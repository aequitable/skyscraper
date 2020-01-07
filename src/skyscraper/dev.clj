(ns skyscraper.dev
  (:require
    [clojure.core.async :refer [chan alts!!]]
    [clojure.java.browse :refer [browse-url]]
    [clojure.java.io :as io]
    [skyscraper.core :as core]
    [skyscraper.traverse :as traverse]
    [taoensso.timbre :as log]))

(defn browse-context [ctx]
  (let [f (java.io.File/createTempFile "skyscraper-" ".html")]
    (with-open [is (io/input-stream (get-in ctx [::core/response :body]))
                os (io/output-stream f)]
      (io/copy is os))
    (browse-url f)))

(defn scrape [seed & {:as options}]
  (let [item-chan (chan)
        options (core/initialize-options (assoc options :item-chan item-chan :parallelism 1))
        seed (core/initialize-seed options seed)
        {:keys [terminate-chan] :as channels} (traverse/launch seed options)]
    (loop []
      (let [alts-res (alts!! [item-chan terminate-chan])
            [val port] alts-res]
        (if (= port terminate-chan)
          nil
          (if-let [unimplemented (first (filter #(::core/unimplemented %) val))]
            unimplemented
            (do (log/infof "Got %s nodes" (count val))
                (recur))))))))
