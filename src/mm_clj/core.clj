(ns mm-clj.core
  (:require
    [clojure.java.io :as io]
    [clojure.math.combinatorics :refer [combinations cartesian-product]]
    [clojure.string :refer [index-of includes? join]]
    [instaparse.core :as insta])
  (:import
    instaparse.gll.Failure
    mm-clj.ParseException))

;;; Program parsing stuff

(defn strip-comments
  "Strip comments"
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
  "Parse metamath program"
  [program]
  (let [result (mm-parser program)]
    (if (instance? Failure result)
      (throw (ParseException. (str "Parse error: " (:reason result) ", at position " (:index result))))
      program)))

(def read-file)

(defn- load-include-once
  "Load included file if it has not been included before"
  [text include-stmt filename included-files]
  (if (some #{filename} included-files)
    [(clojure.string/replace text include-stmt "") included-files]
    (let [[text-to-include updated-included-files]
          (read-file filename (conj included-files filename))]
      [(clojure.string/replace text include-stmt text-to-include) updated-included-files])))

(defn load-includes
  "Load included files"
  [text included-files rootdir]
  (let [m (re-find #"(\$\[ (\S+) \$\])" text)]
    (if m
      (let [include-stmt (get m 1)
            filename (get m 2)]
        (apply #(load-includes %1 %2 rootdir)  ; call recursively to load multiple includes in one file
               (load-include-once text include-stmt (str rootdir "/" filename) included-files)))
      [text included-files])))

(defn read-file
  "Read metamath file"
  ([filename]
   (first (read-file filename [filename])))
  ([filename included-files]
   (let [program (check-grammar (strip-comments (slurp filename)))
         rootdir (.getParent (io/file filename))]
     (load-includes program included-files rootdir))))

(defrecord Scope [variables floatings essentials disjoints])
(defrecord ParserState [constants vartypes labels axioms provables scope])

(defn- add-constant
  "Add constant to the parser state"
  [c state]
  (if (some #{c} (:constants state))
    (throw (ParseException. (str "Constant " c " was already defined before")))
    (if (contains? (:vartypes state) c)
      (throw (ParseException. (str "Constant " c " was previously defined as a variable before")))
      (if (some #{c} (:labels state))
        (throw (ParseException. (str "Constant " c " matches an existing label")))
        (assoc state :constants (conj (:constants state) c))))))

(defn- add-variable
  "Add variable to the parser state"
  [v state]
  (if (some #{v} (:constants state))
    (throw (ParseException. (str "Variable " v " matches an existing constant")))
    (if (some #{v} (:labels state))
      (throw (ParseException. (str "Variable " v " matches an existing label")))
      (let [active-vars (-> state :scope :variables)]
        (if (some #{v} active-vars)
          (throw (ParseException. (str "Variable " v " was already defined before")))
          (let [state (assoc-in state [:scope :variables] (conj active-vars v))]
            (if (not (contains? (:vartypes state) v))
              (assoc-in state [:vartypes v] nil)
              state)))))))

(defn- add-label
  "Add label to the parser state"
  [l state]
  (if (some #{l} (:labels state))
    (throw (ParseException. (str "Label " l " was already defined before")))
    (if (some #{l} (:constants state))
      (throw (ParseException. (str "Label " l " matches a constant")))
      (if (contains? (:vartypes state) l)
        (throw (ParseException. (str "Label " l " matches a variable")))
        (assoc state :labels (conj (:labels state) l))))))

(defn- check-variable-active
  "Check that a variable is (defined and) active"
  [v state]
  (when (not-any? #{v} (-> state :scope :variables))
    (throw (ParseException. (str "Variable " v " not active")))))

(defn- get-active-variable-type
  "Get the type of a variable, ensuring it is defined and active"
  [v state]
  (if (contains? (:vartypes state) v)
    (do
      (check-variable-active v state)
      (get (:vartypes state) v))
    (throw (ParseException. (str "Variable " v " not defined")))))

(defn- set-active-variable-type
  "Set the type of an active variable"
  [v typecode state]
  (let [t (get-active-variable-type v state)]
    (if (and t (not= typecode t))
      (throw (ParseException. (str "Variable " v " was previously assigned type " t)))
      (if (nil? (get (:constants state) typecode))
        (throw (ParseException. (str "Type " typecode " not found in constants")))
        (assoc-in state [:vartypes v] typecode)))))

(def check-program)

(defn- check-block-stmt
  "Check a block in the program parse tree"
  [block-stmts state]
  ; save scope
  (let [scope (:scope state)
        ; parse block
        state (reduce #(check-program %2 %1) state block-stmts)]
    ; revert scope
    (assoc state :scope scope)))

(defn- check-floating-stmt
  "Check a floating hypothesis statement in the program parse tree"
  [[[_ label] [_ [_ typecode]] [_ variable]] state]
  (let [state (add-label label state)
        state (set-active-variable-type variable typecode state)]
    (assoc-in state [:scope :floatings label] {:variable variable :type typecode})))

(defn- check-symbols
  "Check all symbols are defined and active"
  [symbols state]
  (doall
    (map (fn [s]
           (when (and (not-any? #{s} (:constants state))
                      (not-any? #{s} (-> state :scope :variables)))
             (throw (ParseException. (str "Variable or constant " s " not defined")))))
         symbols)))

(defn- check-variables-have-type
  "Check all variables have an active floating statement (i.e. have a type)"
  [symbols state]
  (doall
    (map (fn [s]
           (when (some #{s} (-> state :scope :variables))
             (when (not-any? #(= s (:variable (second %))) (-> state :scope :floatings))
               (throw (ParseException. (str "Variable " s " must be assigned a type"))))))
         symbols)))

(defn- check-essential-stmt
  "Check an essential hypothesis statement in the program parse tree"
  [[[_ label] [_ [_ typecode]] & symbols] state]
  (let [state (add-label label state)
        _ (check-symbols symbols state)
        _ (check-variables-have-type symbols state)]
    (if (not-any? #{typecode} (:constants state))
      (throw (ParseException. (str "Type " typecode " not found in constants")))
      (assoc-in state [:scope :essentials label] {:type typecode :symbols (vec symbols)}))))

(defn- check-variables-unique
  "Check that each variable is unique"
  [variables]
  (doall
    (map #(when (< 1 (second %))
            (throw (ParseException.  (str "Variable " (first %) " appears more than once in a disjoint statement"))))
         (frequencies variables))))

(defn- add-disjoint
  "Add a disjoint pair to the state"
  [[x y] state]
  (let [disjoints (-> state :scope :disjoints)
        pair (sort [x y])]
    (if (some #{pair} disjoints)
      (throw (ParseException. (str "Disjoint variable restriction " pair " already defined")))
      (assoc-in state [:scope :disjoints] (conj disjoints pair)))))

(defn- check-disjoint-stmt
  "Check a disjoint statement in the program parse tree"
  [variables state]
  (let [vs (map second variables)
        _ (check-variables-unique vs)
        _ (doall (map #(check-variable-active % state) vs))]
    (reduce #(add-disjoint %2 %1) state (combinations vs 2))))

(defn- check-assertion-stmt
  "Check an assertion (axiom or provable) statement in the program parse tree"
  [assertion-type [[_ label] [_ [_ typecode]] & symbols] state]
  (let [state (add-label label state)
        _ (check-symbols symbols state)
        _ (check-variables-have-type symbols state)]
    (if (not-any? #{typecode} (:constants state))
      (throw (ParseException. (str "Type " typecode " not found in constants")))
      (assoc-in state [assertion-type label] {:type typecode :symbols (vec symbols) :scope (:scope state)}))))

(defn- check-axiom-stmt
  "Check an axiom statement in the program parse tree"
  [tree state]
  (check-assertion-stmt :axioms tree state))

(defn- check-labels
  "Check all labels are defined"
  [labels state]
  (doall (map #(when (not-any? #{%} (:labels state))
                 (throw (ParseException. (str "Label " % " not defined"))))
              labels)))

(defn- decode-proof-chars
  ([characters]
   (decode-proof-chars characters [] 0))
  ([[c & characters] nums acc]
   (cond
     (nil? c) nums
     (= \? c) (decode-proof-chars characters (conj nums "?") 0)
     (= \Z c) (decode-proof-chars characters (conj nums -1) 0)
     (< (byte \T) (byte c)) (decode-proof-chars characters nums (+ (* 5 acc) (* 20 (- (byte c) 84))))
     :else (decode-proof-chars characters (conj nums (+ acc (- (byte c) 64))) 0))))

(defn- num-to-label
  [x mhyps labels]
  (let [m (count mhyps)
        n (count labels)]
    (cond
      (= -1 x) :save
      (>= m x) (get mhyps (dec x))
      (>= (+ m n) x) (get labels (- x m 1))
      :else [:load (- x m n 1)])))

(defn- decompress-proof
  "Decompress a compressed proof"
  [characters mhyps labels]
  (let [nums (decode-proof-chars characters)]
    (vec (map #(num-to-label % mhyps labels) nums))))

(def mandatory-hypotheses)

(defn- check-compressed-proof
  "Check a compressed proof"
  [compressed-proof assertion state]
  (let [labels (vec (map second (filter #(= :LABEL (first %)) compressed-proof)))
        characters (apply str (map second (filter #(= :COMPRESSED-PROOF-BLOCK (first %)) compressed-proof)))]
    (decompress-proof characters (mandatory-hypotheses assertion (:labels state)) labels)))

(defn- check-proof
  "Check the proof part of a provable statement in the program parse tree"
  [label [_ [proof-format & proof]] state]
  (case proof-format
    :compressed-proof   (let [labels (check-compressed-proof proof (get (:provables state) label) state)]
                          (assoc-in state [:provables label :proof] labels))
    :uncompressed-proof (let [labels (vec (map second proof))
                              _ (check-labels labels state)]
                          (assoc-in state [:provables label :proof] labels))))

(defn- check-provable-stmt
  "Check an axiom statement in the program parse tree"
  [tree state]
  (let [state (check-assertion-stmt :provables (butlast tree) state)
        [[_ label] & _] tree]
    (check-proof label (last tree) state)))

(defn- check-program
  "Check a program parse tree"
  [[node-type & children] state]
  (case node-type
    :constant-stmt  (reduce #(add-constant (second %2) %1) state children)
    :variable-stmt  (reduce #(add-variable (second %2) %1) state children)
    :floating-stmt  (check-floating-stmt children state)
    :essential-stmt (check-essential-stmt children state)
    :disjoint-stmt  (check-disjoint-stmt children state)
    :axiom-stmt     (check-axiom-stmt children state)
    :provable-stmt  (check-provable-stmt children state)
    :block          (check-block-stmt children state)
    (if (vector? (first children))
      (reduce #(check-program %2 %1) state children)
      state)))

(defn parse-mm-program
  "Parse a metamath program"
  [program]
  (let [tree (mm-parser program)]
    (if (instance? Failure tree)
      (throw (ParseException. (str (:reason tree))))
      (check-program tree (ParserState. #{} {} [] {} {} (Scope. #{} {} {} #{}))))))

;;; Proof verification stuff

(defn mandatory-variables
  "Return the set of mandatory variables of an assertion"
  [assertion]
  (into #{}
        (apply concat
               (conj (map (fn [e]
                            (filter #(some #{%} (-> assertion :scope :variables)) (:symbols e)))
                          (vals (-> assertion :scope :essentials)))
                     (filter #(some #{%} (-> assertion :scope :variables)) (:symbols assertion))))))

(defn mandatory-hypotheses
  "Return the list of mandatory hypothese of an assertion in order of appearance"
  [assertion labels]
  (vec (sort-by
    #(.indexOf labels %)
    (into []
          (concat 
            (let [mvars (mandatory-variables assertion)]
              (map (fn [v]
                     (first (keep (fn [[label floating]]
                                    (when (= v (:variable floating))
                                      label))
                                  (-> assertion :scope :floatings))))
                   mvars))
            (keys (-> assertion :scope :essentials)))))))

(defn mandatory-disjoints
  "Return the set of disjoint statements of an assertion"
  [assertion]
  (let [mvars (mandatory-variables assertion)]
    (into #{}
          (filter (fn [[x y]]
                    (and (some #{x} mvars)
                         (some #{y} mvars)))
                  (-> assertion :scope :disjoints)))))

(defn- apply-substitutions
  "Apply substitutions to a list of symbols"
  [subst symbols constants]
  (vec (apply concat (map #(if (some #{%} constants) [%] (get subst %)) symbols))))

(defn- find-substitutions
  "Perform unification"
  [[s & stack] [h & hypos] scope constants subst]
  (if (nil? s)
    subst
    (if-let [f (get (:floatings scope) h)]
      (if (= (:type f) (:type s))
        (if-let [subvar (get subst (:variable f))]
          (if (= subvar (:symbols s))
            (find-substitutions stack hypos scope constants subst)
            (throw (ParseException. (str "Proof verification failed (incompatible substitutions for variable " (:variable f) ")"))))
          (find-substitutions stack hypos scope constants (assoc subst (:variable f) (:symbols s))))
        (throw (ParseException. (str "Proof verification failed (type mismatch for variable " (:variable f) ")"))))
      (if-let [e (get (:essentials scope) h)]
        ; since all variables in an essential hypothesis need to have associated floating hypothesis
        ; all substitutions should be identified when we unify essential hypotheses, so we just
        ; apply substitutions in the essential hypothesis and check it matches what's on the stack
        (if (= (:type e) (:type s))
          (if (= (apply-substitutions subst (:symbols e) constants) (:symbols s))
            (find-substitutions stack hypos scope constants subst)
            (throw (ParseException. (str "Proof verification failed (mismatch in essential hypothesis " h ")"))))
          (throw (ParseException. (str "Proof verification failed (type mismatch for essential hypothesis " h ")"))))))))

(defn- check-expressions-disjoint
  "Check that two expressions are disjoint"
  [exprx expry provable-vars provable-disjs]
  (let [varsx (keep #(get provable-vars %) exprx)
        varsy (keep #(get provable-vars %) expry)
        allpairs (cartesian-product varsx varsy)]
    (doall (map (fn [[x y]]
                  (when (not-any? #{(sort [x y])} provable-disjs)
                    (throw (ParseException. "Proof verification failed (disjoint restriction violated)"))))
                allpairs))))

(defn- check-disjoint-restriction
  "Check a disjoint restriction for a pair of variable"
  [[x y] assertion-disjoints provable-scope subst]
  (when (and (some #{(sort [x y])} assertion-disjoints)
           (contains? subst x) (contains? subst y))
    (let [substx (get subst x)
          substy (get subst y)]
      (check-expressions-disjoint substx substy (:variables provable-scope) (:disjoints provable-scope)))))

(defn- check-disjoint-restrictions
  "Check the validity of disjoint restrictions for a given axiom"
  [assertion provable-scope subst]
  (let [mvars (mandatory-variables assertion)]
    (doall (map #(check-disjoint-restriction
                   % (mandatory-disjoints assertion) provable-scope subst)
                (combinations mvars 2)))))

(defn- apply-axiom
  "Apply an axiom"
  [axiom provable-scope state stack done-fn]
  (let [mhypos (mandatory-hypotheses axiom (:labels state))
        n (count mhypos)]
    (if (<= n (count stack))
      (let [subst (find-substitutions (vec (take-last n stack)) mhypos (:scope axiom) (:constants state) {})
            _ (check-disjoint-restrictions axiom provable-scope subst)]
        (done-fn
          (conj (vec (drop-last n stack))
                {:type (:type axiom) :symbols (apply-substitutions subst (:symbols axiom) (:constants state))})))
      (throw (ParseException. (str "Proof verification failed (stack empty)"))))))

(defn pprint-syms
  "Pretty print symbols"
  [symbols]
  (join " " symbols))

(defn verify-proof
  "Verify proof of a provable statement"
  ([[proof-label {typecode :type symbols :symbols proof :proof scope :scope}] state]
   (print (str "  " proof-label " \"" typecode " " (pprint-syms symbols) "\"... "))
   (verify-proof typecode symbols proof scope state [] [])
   (println "OK!"))
  ([typecode symbols [l & remaining-labels] scope state stack saved-steps]
   (if (nil? l)
     (let [{t :type ss :symbols} (peek stack)]
       (if-not (and (= typecode t) (= symbols ss) (empty? (pop stack)))
         (throw (ParseException. (str "Proof verification failed (got " [t (pprint-syms symbols)]
                                      " while expecting " [typecode (pprint-syms symbols)] ")")))))
     (if-let [floating (get (:floatings scope) l)]
       (verify-proof typecode symbols remaining-labels scope state
                     (conj stack {:type (:type floating) :symbols [(:variable floating)]})
                     saved-steps)
       (if-let [essential (get (:essentials scope) l)]
         (verify-proof typecode symbols remaining-labels scope state (conj stack essential) saved-steps)
         (if-let [axiom (or (get (:axioms state) l)
                            (get (:provables state) l))]
           (apply-axiom axiom scope state stack
                        (fn [stack]
                          (verify-proof typecode symbols remaining-labels scope state stack saved-steps)))
           (if (= :save l)
             (verify-proof typecode symbols remaining-labels scope state stack (conj saved-steps (peek stack)))
             (if (and (seq l) (= :load (first l)))
               (verify-proof typecode symbols remaining-labels scope state
                             (conj stack (get saved-steps (second l))) saved-steps)
               (throw (ParseException. (str "Proof verification failed (unrecognized label " l ")")))))))))))


(defn verify-proofs
  "Verify proofs of a parsed metamath program"
  [state]
  (doall
    (map #(verify-proof % state) (-> state :provables)))
  state)

(defn parse-mm
  "Parse a metamath file"
  [filename]
  (let [_ (print "Parsing program... ")
        program (parse-mm-program (read-file filename))
        _ (println "OK!")
        result (if (seq (:provables program))
                 (do
                   (println "Verifying proofs:")
                   (verify-proofs program)
                   (println "Done."))
                 program)]
    result))

(defn -main
  "A Metamath parser written in Clojure. Fun everywhere!"
  [filename]
  (try
    (parse-mm filename)
    (catch Exception e (println (.getMessage e)))))
