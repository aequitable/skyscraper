# SQLite integration

## Introduction

Skyscraper can automatically emit the result of scraping in the form of a SQLite database.  One table will be created for each database-enabled processor, with each row corresponding to an output context created by that processor. Each row will be automatically assigned an ID, and parent-child relationships will be modelled as foreign keys.

To specify that a table should be generated for a given processor, add a `:db-columns` option to `defprocessor`. For example:

```clojure
(defprocessor :users
  :process-fn (fn [res ctx]
                [{:name "John", :surname "Doe"}])
  :db-columns [:name :surname])
```

Now, when you invoke Skyscraper like this:

```clojure
(scrape! [{:url "http://example.com", :processor :users}]
         :db-file "/tmp/demo.sqlite")
```

Skyscraper will create a SQLite database in the given file, containing one table named `users` with four columns: two textual ones that you have specified, and two additional integer ones named `id` and `parent`. That is, it will conform to the following schema:

```sql
CREATE TABLE users (id integer primary key, parent integer, name text, surname text);
```

The `id` will be an internal, autogenerated primary key. It is not guaranteed to be stable – it is possible for two identical invocations of Skyscraper to generate different tables.

The `parent` column will be described below.

## Tree structure

Let us expand our working example. Consider the following processor definitions:

```clojure
(defprocessor :users
  :process-fn (fn [res ctx]
                [{:name "John", :surname "Doe", :url "/", :processor :accounts}])
  :db-columns [:name :surname])

(defprocessor :accounts
  :process-fn (fn [res ctx]
                [{:bank-account "0123-4567"}
                 {:bank-account "8888-9999"}])
  :db-columns [:bank-account])
```

Running `scrape!` as above will now generate the following database:

```sql
sqlite> select * from users;
     id = 1
 parent =
   name = John
surname = Doe

sqlite> select * from accounts;
          id = 1
      parent = 1
bank_account = 0123-4567

          id = 2
      parent = 1
bank_account = 8888-9999
```

Because, in the scrape tree, nodes corresponding to the `:accounts` processor are children of those of `:users`, the `parent` column in the `account` user table references the `id` in `users`.

Note that this database doesn’t contain redundant data, but you can still easily obtain user data for each accounts by simply `JOIN`ing the tables together.

## Persistent identifiers

There’s a gotcha: if you re-run Skyscraper with the above settings, it will duplicate the already existing records in the database. This is because, normally, there is no way tell whether the newly-scraped records correspond to data we already have or not. For example, the records may differ in some details (e.g., timestamps), but still refer to the same entity.

Therefore, you have to be explicit about which fields constitute the “persistent ID” for a given DB-enabled processor. For instance:

```clojure
(defprocessor :users
  :process-fn (fn [res ctx]
                [{:name "John", :surname "Doe", :url "/", :processor :accounts}])
  :db-columns [:name :surname]
  :id [:name :surname])
```

In this case, rather than bluntly executing an `INSERT` for each encountered row, Skyscraper will only insert the row when it already exists in the DB.

## Tips and caveats