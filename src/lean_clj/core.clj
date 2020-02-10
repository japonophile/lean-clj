(ns lean-clj.core
  (:require
    [clojure.string :refer [index-of]]))

(defn strip-comments
  "strip comments"
  [text]
  (let [start (index-of text "$(")]
    (if start
      (let [end (index-of text "$)")]
        (if (> end start)
          (str (subs text 0 start) (strip-comments (subs text (+ 2 end))))
          (throw (Exception. "Malformed comment"))))
      text)))

(defn parse-mm
  "some help"
  [text]
  (strip-comments text))

(defn -main
  "LEAN clojure"
  []
  (parse-mm ""))
