(ns mm-clj.core
  (:require
    [clojure.java.io :as io]
    [clojure.math.combinatorics :refer [combinations]]
    [clojure.string :refer [index-of includes?]]
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
    (if (instance? Failure result)
      (throw (ParseException. (str "Parse error: " (:reason result) ", at position " (:index result))))
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
  [text included-files rootdir]
  (let [m (re-find #"(\$\[ (\S+) \$\])" text)]
    (if m
      (let [include-stmt (get m 1)
            filename (get m 2)]
        (apply #(load-includes %1 %2 rootdir)  ; call recursively to load multiple includes in one file
               (load-include-once text include-stmt (str rootdir "/" filename) included-files)))
      [text included-files])))

(defn read-file
  "read metamath file"
  ([filename]
   (first (read-file filename [filename])))
  ([filename included-files]
   (let [program (check-grammar (strip-comments (slurp filename)))
         rootdir (.getParent (io/file filename))]
     (load-includes program included-files rootdir))))

(defrecord Scope [variables floatings essentials disjoints])
(defrecord ParserState [constants variables labels scope axioms provables])

(defn- add-constant
  "add constant to the parser state"
  [c state]
  (if (some #{c} (:constants state))
    (throw (ParseException. (str "Constant " c " was already defined before")))
    (if (contains? (:variables state) c)
      (throw (ParseException. (str "Constant " c " was previously defined as a variable before")))
      (if (some #{c} (:labels state))
        (throw (ParseException. (str "Constant " c " matches an existing label")))
        (assoc state :constants (conj (:constants state) c))))))

(defn- add-variable
  "add variable to the parser state"
  [v state]
  (if (some #{v} (:constants state))
    (throw (ParseException. (str "Variable " v " matches an existing constant")))
    (if (some #{v} (:labels state))
      (throw (ParseException. (str "Variable " v " matches an existing label")))
      (let [active-vars (-> state :scope :variables)]
        (if (some #{v} active-vars)
          (throw (ParseException. (str "Variable " v " was already defined before")))
          (let [state (assoc-in state [:scope :variables] (conj active-vars v))]
            (if (not (contains? (:variables state) v))
              (assoc-in state [:variables v] {:type nil})
              state)))))))

(defn- add-label
  "add label to the parser state"
  [l state]
  (if (some #{l} (:labels state))
    (throw (ParseException. (str "Label " l " was already defined before")))
    (if (some #{l} (:constants state))
      (throw (ParseException. (str "Label " l " matches a constant")))
      (if (contains? (:variables state) l)
        (throw (ParseException. (str "Label " l " matches a variable")))
        (assoc state :labels (conj (:labels state) l))))))

(defn- get-active-variable
  "get a variable, ensuring it is defined and active"
  [variable state]
  (if-let [v (get (:variables state) variable)]
    (if (some #{variable} (-> state :scope :variables))
      v
      (throw (ParseException. (str "Variable " variable " not active"))))
    (throw (ParseException. (str "Variable " variable " not defined")))))

(defn- set-var-type
  "set the type of a variable"
  [variable typecode state]
  (let [v (get-active-variable variable state)]
    (if (and (:type v) (not= typecode (:type v)))
      (throw (ParseException. (str "Variable " variable " was previously assigned type " (:type v))))
      (if (nil? (get (:constants state) typecode))
        (throw (ParseException. (str "Type " typecode " not found in constants")))
        (assoc-in state [:variables variable :type] typecode)))))

(def check-program)

(defn- check-block
  "check a block in the program parse tree"
  [block-stmts state]
  ; save scope
  (let [scope (:scope state)
        ; parse block
        state (reduce #(check-program %2 %1) state block-stmts)]
    ; revert scope
    (assoc state :scope scope)))

(defn- check-floating
  "check a floating hypothesis statement in the program parse tree"
  [[[_ label] [_ [_ typecode]] [_ variable]] state]
  (let [state (add-label label state)
        state (set-var-type variable typecode state)]
    (assoc-in state [:scope :floatings label] {:variable variable :type typecode})))

(defn- check-symbols
  "check all symbols are defined and active"
  [symbols state]
  (doall
    (map (fn [s]
           (if (and (not-any? #{s} (:constants state))
                    (not-any? #{s} (-> state :scope :variables)))
             (throw (ParseException. (str "Variable or constant " s " not defined")))
             :ok))
         symbols)))

(defn- check-variables-have-type
  "check all variables have an active floating statement (i.e. have a type)"
  [symbols state]
  (doall
    (map (fn [s]
           (if (some #{s} (-> state :scope :variables))
             (if (not-any? #(= s (:variable (second %))) (-> state :scope :floatings))
               (throw (ParseException. (str "Variable " s " must be assigned a type")))
               :ok)
             :not-variable))
         symbols)))

(defn- check-essential
  "check an essential hypothesis statement in the program parse tree"
  [[[_ label] [_ [_ typecode]] & symbols] state]
  (let [state (add-label label state)
        _ (check-symbols symbols state)
        _ (check-variables-have-type symbols state)]
    (if (not-any? #{typecode} (:constants state))
      (throw (ParseException. (str "Type " typecode " not found in constants")))
      (assoc-in state [:scope :essentials label] {:type typecode :symbols (vec symbols)}))))

(defn- check-unique
  "check that each variable is unique"
  [variables]
  (doall
    (map #(if (< 1 (second %))
            (throw (ParseException.  (str "Variable " (first %) " appears more than once in a disjoint statement")))
            :ok)
         (frequencies variables))))

(defn- add-disjoint
  "add a disjoint pair to the state"
  [[x y] state]
  (let [disjoints (-> state :scope :disjoints)
        pair (sort [x y])]
    (if (some #{pair} disjoints)
      (throw (ParseException. (str "Disjoint variable restriction " pair " already defined")))
      (assoc-in state [:scope :disjoints] (conj disjoints pair)))))

(defn- check-disjoint
  "check a disjoint statement in the program parse tree"
  [variables state]
  (let [vs (map second variables)
        _ (check-unique vs)
        _ (doall (map #(get-active-variable % state) vs))]
    (reduce #(add-disjoint %2 %1) state (combinations vs 2))))

(defn- check-assertion
  "check an assertion (axiom or provable) statement in the program parse tree"
  [assertion-type [[_ label] [_ [_ typecode]] & symbols] state]
  (let [state (add-label label state)
        _ (check-symbols symbols state)
        _ (check-variables-have-type symbols state)]
    (if (not-any? #{typecode} (:constants state))
      (throw (ParseException. (str "Type " typecode " not found in constants")))
      (assoc-in state [assertion-type label] {:type typecode :symbols (vec symbols) :scope (:scope state)}))))

(defn- check-axiom
  "check an axiom statement in the program parse tree"
  [tree state]
  (check-assertion :axioms tree state))

(defn- check-labels
  "check all labels are defined"
  [labels state]
  (doall (map #(if (not-any? #{%} (:labels state))
                 (throw (ParseException. (str "Label " % " not defined")))
                 :ok)
              labels)))

(defn- check-proof
  "check the proof part of a provable statement in the program parse tree"
  [label [_ [proof-format & proof]] state]
  (case proof-format
    :compressed-proof (throw (ParseException. "Compressed proof not supported (yet)"))
    :uncompressed-proof (let [labels (vec (map second proof))
                              _ (check-labels labels state)]
                          (assoc-in state [:provables label :proof] labels))))

(defn- check-provable
  "check an axiom statement in the program parse tree"
  [tree state]
  (let [state (check-assertion :provables (butlast tree) state)
        [[_ label] & _] tree]
    (check-proof label (last tree) state)))

(defn- check-program
  "check a program parse tree"
  [[node-type & children] state]
  ; (println [node-type children])
  ; (println state)
  (case node-type
    :constant-stmt  (reduce #(add-constant (second %2) %1) state children)
    :variable-stmt  (reduce #(add-variable (second %2) %1) state children)
    :floating-stmt  (check-floating children state)
    :essential-stmt (check-essential children state)
    :disjoint-stmt  (check-disjoint children state)
    :axiom-stmt     (check-axiom children state)
    :provable-stmt  (check-provable children state)
    :block          (check-block children state)
    (if (vector? (first children))
      (reduce #(check-program %2 %1) state children)
      state)))

(defn- mandatory-variables
  "return the set of mandatory variables of an assertion"
  [assertion state]
  #{})

(defn parse-mm-program
  "parse a metamath program"
  [program]
  (let [tree (mm-parser program)]
    (if (instance? Failure tree)
      (throw (ParseException. (str (:reason tree))))
      (check-program tree (ParserState. #{} {} #{} (Scope. #{} {} {} #{}) {} {})))))

(defn parse-mm
  "parse a metamath file"
  [filename]
  (parse-mm-program (read-file filename)))

(defn -main
  "LEAN clojure"
  [filename]
  (println (parse-mm filename)))
