(ns mm-app.core
  (:require
    [mm-clj.core :refer [parse-mm]]
    [mm-app.server :refer [start-server]]))

(defn -main [& args]
  (if-let [filename (first args)]
    (parse-mm filename)
    (start-server)))
