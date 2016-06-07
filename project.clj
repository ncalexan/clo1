(defproject clo1 "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/java.jdbc "0.6.1"]
                 [datascript "0.15.0"]
                 [org.xerial/sqlite-jdbc "3.8.11.2"]]
  :main ^:skip-aot clo1.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})
