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

(def read-file)

(defn load-includes
  "load includes"
  [text include-tree]
  (let [m (re-matches #".*(\$\[ (\S+) \$\]).*" text)]
    (if m
      (let [filename (get m 2)]
        (if (some #{filename} include-tree)
          (throw (Exception. "Include loop detected"))
          (clojure.string/replace text (get m 1) (read-file filename (conj include-tree filename)))))
      text)))

(defn read-file
  "read metamath file"
  ([filename]
   (read-file filename [filename]))
  ([filename include-tree]
   (load-includes (strip-comments (slurp filename)) include-tree)))

(defn parse-mm
  "some help"
  [filename]
  (read-file filename))

(defn -main
  "LEAN clojure"
  [filename]
  (parse-mm filename))
