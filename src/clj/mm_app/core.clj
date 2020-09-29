(ns mm-app.core
  (:require
    [mm-clj.core :refer [parse-mm]]
    [mm-app.server :refer [start-server]]))

(defn -main [& args]
  (if (first args)
    (if (= "--print-stats" (first args))
      (parse-mm (second args) true)
      (parse-mm (first args) false))
    (start-server)))
