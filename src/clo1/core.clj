(ns clo1.core
  (:require
    [clojure.java.jdbc :refer :all]
    [datascript.core :as d])
  (:gen-class))

(defn origin [url]
  (try
    (let [u (new java.net.URI url)]
      (str (.getScheme u) "://" (.getAuthority u) "/"))
    (catch Exception e nil)))

(defn visits
  [sqldb]
  (let [rows (query sqldb
                    [(clojure.string/join
                       " "
                       ["SELECT DISTINCT v.visit_date, p.url"
                        "FROM moz_historyvisits v INNER JOIN moz_places p"
                        "WHERE v.place_id == p.id AND p.hidden == 0"
                        "ORDER BY v.visit_date ASC"
                        ;; "LIMIT 2000"
                        ])
                     ])]
    rows))

(def schema
  {
   :page/url    {
                 :db/cardinality :db.cardinality/one
                 :db/unique      :db.unique/identity
                 :db/doc         "A page's URL."
                 }
   :page/title  {
                 :db/cardinality :db.cardinality/one      ; We supersede as we see new titles.
                 :db/doc         "A page's title."
                 }

   :event/visit {
                 :db/valueType   :db.type/ref
                 :db/cardinality :db.cardinality/many
                 :db/doc         "A visit to the page."
                 }
   :visit/instant {
                   :db/cardinality :db.cardinality/one
                   :db/doc         "Instant of the visit to the page."
                   }
   })

(defn import-from-places
  [sqldb conn]
  (let [
        ;; Visit rows with origins.
        vs (filter (comp some? origin :url) (visits sqldb))

        ;; Map URL -> entity offset.
        us (into {} (map-indexed #(vector %2 %1) (map :url vs)))
        ]

    ;; Insert places.
    (doseq [[u _] us]
      (d/transact! conn
                   [{:db/id -1
                     :page/url u
                     }]))

    ;; Insert visits.
    (doseq [v vs]
      (d/transact! conn
                   [{:db/id         -1
                     :visit/instant (:visit_date v) ;; Long!
                     }
                    {
                     :db/id [:page/url (:url v)]
                     :event/visit -1
                     }]))))

(defn select-visits
  [db url]
  (d/q '[:find ?u ?v
         :in $ ?matches ?url
         :where
         [?ep :page/url ?u]
         [(?matches ?u ?url)]
         [?ep :event/visit ?ev]
         [?ev :visit/instant ?v]
         ]
       db
       clojure.string/starts-with?
       url))

(defn count-visits
  [db url]
  (d/q '[:find (count ?ev)
         :in $ ?matches ?url
         :where
         [?ep :page/url ?u]
         [(?matches ?u ?url)]
         [?ep :event/visit ?ev]
         ]
       db
       clojure.string/starts-with?
       url))

(defn -main
  ([path-to-places]
   (System/gc)
   (prn "Before doing anything, have Mb used"
        (/ (- (.totalMemory (Runtime/getRuntime)) (.freeMemory (Runtime/getRuntime))) (* 1024 1024.0)))
   (let [sqldb {:classname   "org.sqlite.JDBC"
                :subprotocol "sqlite"
                :subname     path-to-places
                }
         conn (d/create-conn schema)
         ]
     (time (import-from-places sqldb conn))
     (prn "Inserted # of datoms:"
          (count (d/datoms @conn :eavt)))

     (System/gc)
     (prn "After inserting places and visits, have Mb used"
          (/ (- (.totalMemory (Runtime/getRuntime)) (.freeMemory (Runtime/getRuntime))) (* 1024 1024.0)))

     (prn "Returned # visits to treeherder: "
          (count (time (select-visits @conn "https://treeherder.mozilla.org/"))))

     (prn "Counted # visits to gmail: "
          (first (time (count-visits @conn "https://mail.google.com/"))))))

  ([]
   (println "First argument should be places SQLite database.")
   (System/exit 1))

  ([first &rest]
   (println "First argument should be places SQLite database.")
   (System/exit 1)))
