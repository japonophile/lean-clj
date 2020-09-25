(ns mm-clj.core
  (:require
    [clojure.core.async :refer [go]]
    ; [mm-clj.insta.insta :as p]
    [mm-clj.parser :as p]
    [progrock.core :as pr]))


(def bar (atom (pr/progress-bar 100)))

(defn parse-mm
  "A Metamath parser written in Clojure. Fun everywhere!"
  [filename]
  (go
    (try
      (let [state (p/parse-mm filename :print-stats true :progress bar)
            program (:program state)]
        (println (str (count (:symbols program)) " symbols"))
        (println (str (count (:constants program)) " constants"))
        (println (str (count (:variables program)) " variables"))
        (println (str (count (:axioms program)) " axioms"))
        (println (str (count (:provables program)) " provables")))
      (catch Exception e (println (.getMessage e)))))
  (loop []
    (if (= (:progress @bar) (:total @bar))
      (pr/print (pr/done @bar))
      (do (Thread/sleep 200)
          (pr/print @bar)
          (recur)))))
