(ns lean-clj.core
  (:require
    [clojure.string :refer [index-of includes?]]
    [clojure.java.io :as io]
    [instaparse.core :as insta])
  (:import
    instaparse.gll.Failure
    ; lean-clj.ParseException
    ))

(defn strip-comments
  "strip comments"
  [text]
  (if-let [start (index-of text "$(")]
    (let [end (index-of text "$)")]
      (if (> end start)
        (if (not (includes? (subs text (+ 2 start) end) "$("))
          (str (subs text 0 start) (strip-comments (subs text (+ 2 end))))
          (throw (Exception. "Comments may not be nested")))
        (throw (Exception. "Malformed comment"))))
    text))

(defn- check-grammar
  "parse metamath program"
  [program]
  (let [result ((insta/parser (io/resource "lean_clj/mm.bnf")) program)]
    (if (instance? instaparse.gll.Failure result)
      (throw (Exception. (str (:reason result))))
      program)))

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
   (let [program (check-grammar (strip-comments (slurp filename)))]
     (load-includes program included-files))))

(defn parse-mm
  "some help"
  [filename]
  (read-file filename))

(defn -main
  "LEAN clojure"
  [filename]
  (parse-mm filename))
