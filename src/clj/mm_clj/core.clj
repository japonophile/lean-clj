(ns mm-clj.core
  (:require
    [clojure.core.async :refer [go]]
    ; [mm-clj.insta.insta :as p]
    [mm-clj.parser :as p]
    [progrock.core :as pr]))


(def bar (atom (pr/progress-bar 100)))

(defn print-truncated
  [txt]
  (let [n (count txt)]
    (when (< 0 n)
      (if (< n 50)
        (println (str "      " txt))
        (println (str "      " (subs txt 0 50) "..."))))))

(defn print-structure
  [structure]
  (println (or (:title structure) "Program structure"))
  (loop [[section & othersections] (:sections structure)
         i 1]
    (when section
      (println (str "  " i ". " (:title section)))
      (print-truncated (:description section))
      (let [nasserts (count (:assertions section))]
        (when (< 0 nasserts)
          (println (str "    " nasserts " assertions"))))
      (loop [[subsection & othersubsections] (:subs section)
             j 1]
        (when subsection
          (println (str "    " i "." j " " (:title subsection)))
          (print-truncated (:description subsection))
          (let [nasserts (count (:assertions subsection))]
            (when (< 0 nasserts)
              (println (str "      " nasserts " assertions"))))
          (recur othersubsections (inc j))))
      (recur othersections (inc i)))))


(defn parse-mm
  "A Metamath parser written in Clojure. Fun everywhere!"
  [filename print-stats]
  (go
    (try
      (let [state (p/parse-mm filename :print-stats print-stats :progress bar)
            program (:program state)]
        (print-structure (:structure program))
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
