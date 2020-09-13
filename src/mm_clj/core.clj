(ns mm-clj.core
  (:require
    ; [mm-clj.insta :as p]
    [mm-clj.parser :as p]
    [mm-clj.renderer :as r]))


(defn -main
  "A Metamath parser written in Clojure. Fun everywhere!"
  [filename]
  (try
    (let [state (p/parse-mm filename :print-stats true)
          program (:program state)]
      (println (str (count (:symbols program)) " symbols"))
      (println (str (count (:constants program)) " constants"))
      (println (str (count (:variables program)) " variables"))
      (println (str (count (:axioms program)) " axioms"))
      (println (str (count (:provables program)) " provables"))
      (r/render state))
    (catch Exception e (println (.getMessage e)))))
