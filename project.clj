(defproject mm-clj "0.1.0-SNAPSHOT"
  :description "A metamath parser written in Clojure"
  :url "https://chopp.in"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/math.combinatorics "0.1.6"]
                 [instaparse "1.4.10"]]
  :main mm-clj.core
  :aot [mm-clj.ParseException]
  :repl-options {:init-ns mm-clj.core})
