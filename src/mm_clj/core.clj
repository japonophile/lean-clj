(ns mm-clj.core
  (:require
    ; [mm-clj.insta :refer [parse-mm]]))
    [mm-clj.handmade :refer [parse-mm]]))

(defn -main
  "A Metamath parser written in Clojure. Fun everywhere!"
  [filename]
  (try
    (parse-mm filename)
    (catch Exception e (println (.getMessage e)))))
