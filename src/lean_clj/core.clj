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

(defn- load-include-once
  "load included file if it has not been included before"
  [text include-stmt filename included-files]
  (if (some #{filename} included-files)
    [(clojure.string/replace text include-stmt "") included-files]
    (let [[text-to-include updated-included-files]
          (read-file filename (conj included-files filename))]
      [(clojure.string/replace text include-stmt text-to-include) updated-included-files])))

(defn load-includes
  "load included files"
  [text included-files]
  (let [m (re-find #"(\$\[ (\S+) \$\])" text)]
    (if m
      (let [include-stmt (get m 1)
            filename (get m 2)]
        (apply load-includes  ; call recursively to load multiple includes in one file
               (load-include-once text include-stmt filename included-files)))
      [text included-files])))

(defn read-file
  "read metamath file"
  ([filename]
   (first (read-file filename [filename])))
  ([filename included-files]
   (load-includes (strip-comments (slurp filename)) included-files)))

(defn parse-mm
  "some help"
  [filename]
  (read-file filename))

(defn -main
  "LEAN clojure"
  [filename]
  (parse-mm filename))
