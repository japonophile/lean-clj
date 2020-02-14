(ns mm-clj.core
  (:require
    [clojure.string :refer [index-of includes?]]
    [clojure.java.io :as io]
    [instaparse.core :as insta])
  (:import
    instaparse.gll.Failure
    mm-clj.ParseException))

(defn strip-comments
  "strip comments"
  [text]
  (if-let [start (index-of text "$(")]
    (let [end (index-of text "$)")]
      (if (and end (> end start))
        (if (not (includes? (subs text (+ 2 start) end) "$("))
          (str (subs text 0 start) (strip-comments (subs text (+ 2 end))))
          (throw (ParseException. "Comments may not be nested")))
        (throw (ParseException. "Malformed comment"))))
    text))

(def mm-parser (insta/parser (io/resource "mm_clj/mm.bnf")))

(defn- check-grammar
  "parse metamath program"
  [program]
  (let [result (mm-parser program)]
    (if (instance? instaparse.gll.Failure result)
      (throw (ParseException. (str (:reason result))))
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

(defrecord ParserState [constants variables])

(defn- add-constant
  "add constant to the parser state"
  [children state]
  (let [_ (assert (= 1 (count children)))
        c (first children)]
    (if (some #{c} (:constants state))
      (throw (ParseException. (str "Constant " c " was already defined before")))
      (if (contains? (:variables state) c)
        (throw (ParseException. (str "Label " c " was previously defined as a variable before")))
        (assoc state :constants (conj (:constants state) (first children)))))))

(defn- add-variable
  "add variable to the parser state"
  [children state]
  (let [_ (assert (= 1 (count children)))
        v (first children)]
    (let [vv (get (:variables state) v)]
      (if (and vv (:active vv))
        (throw (ParseException. (str "Variable " v " was already defined before")))
        (assoc state :variables (assoc (:variables state) (first children) {:type nil :active true}))))))

(defn active-variables
  "get active variables"
  [state]
  (map #(first %) (filter #(true? (:active %)) (:variables state))))

(defn deactivate-vars
  "deactivate variables that should not be active"
  [variables active-vars]
  (into {} (map (fn [[k v]]
                  (if (not (some #{k} active-vars))
                    [k (assoc v :active false)]
                    [k v]))
                variables)))

(def check-program)

(defn check-block
  "check a block in the program parse tree"
  [block-stmts state]
  (let [active-vars (active-variables state)
        parse-result (reduce #(check-program %2 %1) state block-stmts)]
    (assoc parse-result :variables (deactivate-vars (:variables parse-result) active-vars))))

(defn check-program
  "check a program parse tree"
  [tree state]
  ; (println state)
  (if (instance? instaparse.gll.Failure tree)
    (throw (ParseException. (str (:reason tree))))
    (let [[node-type & children] tree]
      (case node-type
        :constant (add-constant children state)
        :variable (add-variable children state)
        :block    (check-block  children state)
        (reduce #(check-program %2 %1) state children)))))

(defn parse-mm-program
  "parse a metamath program"
  [program]
  (let [tree (mm-parser program)]
    (check-program tree (ParserState. #{} {}))))

(defn parse-mm
  "parse a metamath file"
  [filename]
  (parse-mm-program (read-file filename)))

(defn -main
  "LEAN clojure"
  [filename]
  (parse-mm filename))
