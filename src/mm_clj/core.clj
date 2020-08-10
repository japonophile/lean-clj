(ns mm-clj.core
  (:require
    [clojure.java.io :as io]
    [clojure.math.combinatorics :refer [combinations cartesian-product]]
    [clojure.string :as s :refer [index-of includes? join]]
    [instaparse.core :as insta]
    [taoensso.tufte :as tufte :refer [defnp p profiled format-pstats]])
  (:import
    instaparse.gll.Failure
    mm-clj.ParseException))


;;; Program parsing stuff

(set! *warn-on-reflection* true)

(defnp strip-comments
  "Strip comments"
  ([text]
   (let [b (StringBuilder.)]
     (loop [from (long 0)]
       (if-let [start (index-of text "$(" from)]
         (let [end (index-of text "$)" from)]
           (if (and end (> end start))
             (if (not (includes? (subs text (+ 2 start) end) "$("))
               (do
                 (.append b (subs text from start))
                 (recur (long (+ 2 end))))
               (throw (ParseException. "Comments may not be nested")))
             (throw (ParseException. "Malformed comment"))))
         (do
           (.append b (subs text from))
           (str b)))))))

(def mm-parser (insta/parser (io/resource "mm_clj/mm.bnf")))

(def mm-top-parser (insta/parser
                     (str (slurp (io/resource "mm_clj/mm-top.bnf"))
                          (slurp (io/resource "mm_clj/mm-core.bnf")))))
(def mm-block-parser (insta/parser
                       (str (slurp (io/resource "mm_clj/mm-block.bnf"))
                            (slurp (io/resource "mm_clj/mm-core.bnf")))))

(def read-file)

(defnp load-include-once
  "Load included file if it has not been included before"
  [text include-stmt filename included-files]
  (if (contains? included-files filename)
    [(s/replace text include-stmt "") included-files]
    (let [[text-to-include updated-included-files]
          (read-file filename (conj included-files filename))]
      [(s/replace text include-stmt text-to-include) updated-included-files])))

(defnp load-includes
  "Load included files"
  [text included-files rootdir]
  (let [m (re-find #"(\$\[ (\S+) \$\])" text)]
    (if m
      (let [include-stmt (get m 1)
            filename (get m 2)]
        (apply #(load-includes %1 %2 rootdir)  ; call recursively to load multiple includes in one file
               (load-include-once text include-stmt (str rootdir "/" filename) included-files)))
      [text included-files])))

(defnp read-file
  "Read metamath file"
  ([filename]
   (first (read-file filename [filename])))
  ([filename included-files]
   (let [program (strip-comments (slurp filename))
         rootdir (.getParent (io/file filename))]
     (load-includes program included-files rootdir))))

(defrecord Scope [variables floatings essentials disjoints])
(defrecord ParserState [constants vartypes labels axioms provables scope])

(defnp add-constant
  "Add constant to the parser state"
  [c state]
  (if (contains? (:constants state) c)
    (throw (ParseException. (str "Constant " c " was already defined before")))
    (if (contains? (:vartypes state) c)
      (throw (ParseException. (str "Constant " c " was previously defined as a variable before")))
      (if (contains? (:labels state) c)
        (throw (ParseException. (str "Constant " c " matches an existing label")))
        (assoc state :constants (conj (:constants state) c))))))

(defnp add-variable
  "Add variable to the parser state"
  [v state]
  (if (contains? (:constants state) v)
    (throw (ParseException. (str "Variable " v " matches an existing constant")))
    (if (contains? (:labels state) v)
      (throw (ParseException. (str "Variable " v " matches an existing label")))
      (let [active-vars (-> state :scope :variables)]
        (if (contains? active-vars v)
          (throw (ParseException. (str "Variable " v " was already defined before")))
          (let [state (assoc-in state [:scope :variables] (conj active-vars v))]
            (if (not (contains? (:vartypes state) v))
              (assoc-in state [:vartypes v] nil)
              state)))))))

(defnp add-label
  "Add label to the parser state"
  [l state]
  (if (contains? (:labels state) l)
    (throw (ParseException. (str "Label " l " was already defined before")))
    (if (contains? (:constants state) l)
      (throw (ParseException. (str "Label " l " matches a constant")))
      (if (contains? (:vartypes state) l)
        (throw (ParseException. (str "Label " l " matches a variable")))
        (update state :labels #(conj % {l (inc (count %))}))))))

(defnp check-variable-active
  "Check that a variable is (defined and) active"
  [v state]
  (when (not (contains? (-> state :scope :variables) v))
    (throw (ParseException. (str "Variable " v " not active")))))

(defnp get-active-variable-type
  "Get the type of a variable, ensuring it is defined and active"
  [v state]
  (if (contains? (:vartypes state) v)
    (do
      (check-variable-active v state)
      (get (:vartypes state) v))
    (throw (ParseException. (str "Variable " v " not defined")))))

(defnp set-active-variable-type
  "Set the type of an active variable"
  [v typecode state]
  (let [t (get-active-variable-type v state)]
    (if (and t (not= typecode t))
      (throw (ParseException. (str "Variable " v " was previously assigned type " t)))
      (if (nil? (get (:constants state) typecode))
        (throw (ParseException. (str "Type " typecode " not found in constants")))
        (assoc-in state [:vartypes v] typecode)))))

(def check-program)

(defnp check-block-stmt
  "Check a block in the program parse tree"
  [block-stmts state]
  ; save scope
  (let [scope (:scope state)
        ; parse block
        state (reduce #(check-program %2 %1) state block-stmts)]
    ; revert scope
    (assoc state :scope scope)))

(defnp check-floating-stmt
  "Check a floating hypothesis statement in the program parse tree"
  [[label [_ [_ typecode]] [_ variable]] state]
  (let [state (add-label label state)
        state (set-active-variable-type variable typecode state)]
    (assoc-in state [:scope :floatings label] {:variable variable :type typecode})))

(defnp check-symbols
  "Check all symbols are defined and active"
  [symbols state]
  (doall
    (map (fn [s]
           (when (and (not (contains? (:constants state) s))
                      (not (contains? (-> state :scope :variables) s)))
             (throw (ParseException. (str "Variable or constant " s " not defined")))))
         symbols)))

(defnp check-variables-have-type
  "Check all variables have an active floating statement (i.e. have a type)"
  [symbols state]
  (doall
    (map (fn [s]
           (when (contains? (-> state :scope :variables) s)
             (when (not-any? #(= s (:variable (second %))) (-> state :scope :floatings))
               (throw (ParseException. (str "Variable " s " must be assigned a type"))))))
         symbols)))

(defnp check-essential-stmt
  "Check an essential hypothesis statement in the program parse tree"
  [[label [_ [_ typecode]] & symbols] state]
  (let [state (add-label label state)
        _ (check-symbols symbols state)
        _ (check-variables-have-type symbols state)]
    (if (not (contains? (:constants state) typecode))
      (throw (ParseException. (str "Type " typecode " not found in constants")))
      (assoc-in state [:scope :essentials label] {:type typecode :symbols (vec symbols)}))))

(defnp check-variables-unique
  "Check that each variable is unique"
  [variables]
  (doall
    (map #(when (< 1 (second %))
            (throw (ParseException.  (str "Variable " (first %) " appears more than once in a disjoint statement"))))
         (frequencies variables))))

(defnp add-disjoint
  "Add a disjoint pair to the state"
  [[x y] state]
  (let [disjoints (-> state :scope :disjoints)
        pair (sort [x y])]
    (if (contains? disjoints pair)
      ; (throw (ParseException. (str "Disjoint variable restriction " pair " already defined")))
      state
      (assoc-in state [:scope :disjoints] (conj disjoints pair)))))

(defnp check-disjoint-stmt
  "Check a disjoint statement in the program parse tree"
  [variables state]
  (let [vs (map second variables)
        _ (check-variables-unique vs)
        _ (doall (map #(check-variable-active % state) vs))]
    (reduce #(add-disjoint %2 %1) state (combinations vs 2))))

(defnp mandatory-variables
  "Return the set of mandatory variables of an assertion"
  [assertion scope]
  (into #{}
        (apply concat
               (conj (map (fn [e]
                            (filter #(contains? (:variables scope) %) (:symbols e)))
                          (vals (:essentials scope)))
                     (filter #(contains? (:variables scope) %) (:symbols assertion))))))

(defnp mandatory-hypotheses
  "Return the list of mandatory hypotheses of an assertion in order of appearance"
  [assertion labels scope]
  (vec (sort-by
    labels
    (into []
          (concat 
            (let [mvars (-> assertion :scope :mvars)]
              (map (fn [v]
                     (first (keep (fn [[label floating]]
                                    (when (= v (:variable floating))
                                      label))
                                  (:floatings scope))))
                   mvars))
            (keys (:essentials scope)))))))

(defnp mandatory-disjoints
  "Return the set of disjoint statements of an assertion"
  [assertion scope]
  (let [mvars (-> assertion :scope :mvars)]
    (into #{}
          (filter (fn [[x y]]
                    (and (contains? mvars x)
                         (contains? mvars y)))
                  (:disjoints scope)))))

(defnp check-assertion-stmt
  "Check an assertion (axiom or provable) statement in the program parse tree"
  [assertion-type [label [_ [_ typecode]] & symbols] state]
  (let [state (add-label label state)
        _ (check-symbols symbols state)
        _ (check-variables-have-type symbols state)
        ; I'd like to get rid of this                           vvvvvvvvvvvvvv
        assertion {:type typecode :symbols (vec symbols) :scope (:scope state)}
        ; however, axiom scope is still used in find-substitutions when applying axiom
        mvars (mandatory-variables assertion (:scope state))
        assertion (assoc-in assertion [:scope :mvars] mvars)
        mdisjs (mandatory-disjoints assertion (:scope state))
        assertion (assoc-in assertion [:scope :mdisjs] mdisjs)
        mhypos (mandatory-hypotheses assertion (:labels state) (:scope state))
        assertion (assoc-in assertion [:scope :mhypos] mhypos)]
    (if (not (contains? (:constants state) typecode))
      (throw (ParseException. (str "Type " typecode " not found in constants")))
      (assoc-in state [assertion-type label] assertion))))

(defnp check-axiom-stmt
  "Check an axiom statement in the program parse tree"
  [tree state]
  (check-assertion-stmt :axioms tree state))

(defnp check-labels
  "Check all labels are defined"
  [labels state]
  (doall (map #(when (not (contains? (:labels state) %))
                 (throw (ParseException. (str "Label " % " not defined"))))
              labels)))

(defnp decode-proof-chars
  ([characters]
   (loop [[c & characters] characters
          nums []
          acc 0]
     (cond
       (nil? c) nums
       (= \? c) (recur characters (conj nums "?") 0)
       (= \Z c) (recur characters (conj nums -1) 0)
       (< (byte \T) (byte c)) (recur characters nums (+ (* 5 acc) (* 20 (- (byte c) 84))))
       :else (recur characters (conj nums (+ acc (- (byte c) 64))) 0)))))

(defnp num-to-label
  [x mhyps labels]
  (let [m (count mhyps)
        n (count labels)]
    (cond
      (= -1 x) :save
      (>= m x) (get mhyps (dec x))
      (>= (+ m n) x) (get labels (- x m 1))
      :else [:load (- x m n 1)])))

(defnp decompress-proof
  "Decompress a compressed proof"
  [characters mhyps labels]
  (let [nums (decode-proof-chars characters)]
    (vec (map #(num-to-label % mhyps labels) nums))))

(defnp check-compressed-proof
  "Check a compressed proof"
  [compressed-proof assertion]
  (let [labels (vec (rest (first compressed-proof)))
        characters (apply str (rest (second compressed-proof)))]
    (decompress-proof characters (-> assertion :scope :mhypos) labels)))

(defnp check-proof
  "Check the proof part of a provable statement in the program parse tree"
  [label [_ [proof-format & proof]] state]
  (case proof-format
    :compressed-proof   (let [labels (check-compressed-proof proof (get (:provables state) label))]
                          (assoc-in state [:provables label :proof] labels))
    :uncompressed-proof (let [labels (vec proof)
                              _ (check-labels labels state)]
                          (assoc-in state [:provables label :proof] labels))))

(defnp check-provable-stmt
  "Check an axiom statement in the program parse tree"
  [tree state]
  (let [state (check-assertion-stmt :provables (butlast tree) state)
        [label & _] tree]
    (check-proof label (last tree) state)))

(defnp check-program
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

; (defn parse-mm-program
;   "Parse a metamath program"
;   [program]
;   ; (let [tree (time (mm-parser program :trace true))]
;   (let [tree (time (mm-parser program :optimize :memory))]
;     (println tree)
;     (if (instance? Failure tree)
;       (throw (ParseException. (str (:reason tree))))
;       (time (check-program tree (ParserState. #{} {} {} {} {} (Scope. #{} {} {} #{})))))))

(defnp find-block
  "Find the next block (including any nested block)"
  [program from]
  (if-let [blockstart (index-of program "${" from)]
    (loop [from (+ 2 blockstart)
           level 0]
      (let [next-start (index-of program "${" from)
            next-end (index-of program "$}" from)]
        (cond
          (and next-end (or (nil? next-start) (< next-end next-start)))
          (if (> level 0)
            (recur (+ 2 next-end) (dec level))
            [blockstart next-end])
          (and next-start (or (nil? next-end) (< next-start next-end)))
          (recur (+ 2 next-start) (inc level))
          :else
          (throw (ParseException. "Malformed block")))))))

(defnp parse-mm-program-by-blocks
  "Parse a metamath program one block at a time"
  [program]
  (loop [from (long 0)
         tree [:database]]
    (if-let [[blockstart blockend] (find-block program from)]
      ; found block
      ; parse top segment before the block
      (let [top-tree (mm-top-parser (subs program from blockstart))]
        (if (instance? Failure top-tree)
          (throw (ParseException. (str (:reason top-tree))))
          ; parse the block
          (let [block-tree (mm-block-parser (subs program (+ 2 blockstart) blockend))]
            (if (instance? Failure block-tree)
              (throw (ParseException. (str (:reason block-tree))))
              (recur (long (+ 2 blockend))
                     (conj (into tree (vec (rest top-tree)))
                           (assoc block-tree 0 :block)))))))
      ; no more block: parse the final top segment
      (let [top-tree (mm-top-parser (subs program from))]
        (if (instance? Failure top-tree)
          (throw (ParseException. (str (:reason top-tree))))
          (into tree (vec (rest top-tree))))))))

(defnp parse-mm-program
  "Parse a metamath program"
  [program]
  (let [tree (parse-mm-program-by-blocks program)
        state (check-program tree (ParserState. #{} {} {} {} {} (Scope. #{} {} {} #{})))]
    state))

;;; Proof verification stuff

(defnp apply-substitutions
  "Apply substitutions to a list of symbols"
  [subst symbols constants]
  (vec (apply concat (map #(if (contains? constants %) [%] (get subst %)) symbols))))

(defn find-substitutions
  "Perform unification"
  [[s & stack] [h & hypos] scope constants subst]
  (if (nil? s)
    subst
    (if-let [f (get (:floatings scope) h)]
      (if (= (:type f) (:type s))
        (if-let [subvar (get subst (:variable f))]
          (if (= subvar (:symbols s))
            (recur stack hypos scope constants subst)
            (throw (ParseException. (str "Proof verification failed (incompatible substitutions for variable " (:variable f) ")"))))
          (recur stack hypos scope constants (assoc subst (:variable f) (:symbols s))))
        (throw (ParseException. (str "Proof verification failed (type mismatch for variable " (:variable f) ")"))))
      (if-let [e (get (:essentials scope) h)]
        ; since all variables in an essential hypothesis need to have associated floating hypothesis
        ; all substitutions should be identified when we unify essential hypotheses, so we just
        ; apply substitutions in the essential hypothesis and check it matches what's on the stack
        (if (= (:type e) (:type s))
          (if (= (apply-substitutions subst (:symbols e) constants) (:symbols s))
            (recur stack hypos scope constants subst)
            (throw (ParseException. (str "Proof verification failed (mismatch in essential hypothesis " h ")"))))
          (throw (ParseException. (str "Proof verification failed (type mismatch for essential hypothesis " h ")"))))))))

(defnp check-expressions-disjoint
  "Check that two expressions are disjoint"
  [exprx expry provable-vars provable-disjs]
  (let [varsx (keep #(get provable-vars %) exprx)
        varsy (keep #(get provable-vars %) expry)
        allpairs (cartesian-product varsx varsy)]
    (doall (map (fn [[x y]]
                  (when (not (contains? provable-disjs (sort [x y])))
                    (throw (ParseException. "Proof verification failed (disjoint restriction violated)"))))
                allpairs))))

(defnp check-disjoint-restriction
  "Check a disjoint restriction for a pair of variable"
  [[x y] assertion-disjoints provable-scope subst]
  (when (and (contains? assertion-disjoints (sort [x y]))
           (contains? subst x) (contains? subst y))
    (let [substx (get subst x)
          substy (get subst y)]
      (check-expressions-disjoint substx substy (:variables provable-scope) (:disjoints provable-scope)))))

(defnp check-disjoint-restrictions
  "Check the validity of disjoint restrictions for a given axiom"
  [assertion provable-scope subst]
  (let [mvars (-> assertion :scope :mvars)]
    (doall (map #(check-disjoint-restriction
                   % (-> assertion :scope :mdisjs) provable-scope subst)
                (combinations mvars 2)))))

(defnp apply-axiom
  "Apply an axiom"
  [axiom provable-scope state stack]
  (let [mhypos (-> axiom :scope :mhypos)
        n (count mhypos)]
    (if (<= n (count stack))
      (let [subst (p ::find-substitutions (find-substitutions (vec (take-last n stack)) mhypos (:scope axiom) (:constants state) {}))
            _ (check-disjoint-restrictions axiom provable-scope subst)]
        (conj (vec (drop-last n stack))
              {:type (:type axiom) :symbols (apply-substitutions subst (:symbols axiom) (:constants state))}))
      (throw (ParseException. (str "Proof verification failed (stack empty)"))))))

(defn pprint-syms
  "Pretty print symbols"
  [symbols]
  (join " " symbols))

(defnp verify-proof
  "Verify proof of a provable statement"
  ([[proof-label {typecode :type symbols :symbols proof :proof scope :scope}] state]
   (print (str "  " proof-label " \"" typecode " " (pprint-syms symbols) "\"... "))
   (verify-proof typecode symbols proof scope state)
   (println "OK!"))
  ([typecode symbols labels scope state]
   (loop [[l & remaining-labels] labels
          stack []
          saved-steps []]
     (if (nil? l)
       (let [{t :type ss :symbols} (peek stack)]
         (if-not (and (= typecode t) (= symbols ss) (empty? (pop stack)))
           (throw (ParseException. (str "Proof verification failed (got " [t (pprint-syms symbols)]
                                        " while expecting " [typecode (pprint-syms symbols)] ")")))))
       (if-let [floating (get (:floatings scope) l)]
         (recur remaining-labels
                (conj stack {:type (:type floating) :symbols [(:variable floating)]})
                saved-steps)
         (if-let [essential (get (:essentials scope) l)]
           (recur remaining-labels (conj stack essential) saved-steps)
           (if-let [axiom (or (get (:axioms state) l)
                              (get (:provables state) l))]
             (recur remaining-labels (apply-axiom axiom scope state stack) saved-steps)
             (if (= :save l)
               (recur remaining-labels stack (conj saved-steps (peek stack)))
               (if (and (seq l) (= :load (first l)))
                 (recur remaining-labels
                        (conj stack (get saved-steps (second l))) saved-steps)
                 (throw (ParseException. (str "Proof verification failed (unrecognized label " l ")"))))))))))))

(defnp verify-proofs
  "Verify proofs of a parsed metamath program"
  [state]
  (doall
    (map #(verify-proof % state) (-> state :provables)))
  state)

(defn parse-mm
  "Parse a metamath file"
  [filename]
  (let [[program pstats]
        (profiled {}
                  (let [_ (print "Reading program from file... ")
                        _ (flush)
                        prg (read-file filename)
                        _ (println "OK!")
                        _ (print "Parsing program... ")
                        _ (flush)
                        program (parse-mm-program prg)
                        _ (println "OK!")]
                    program))]
    (println (format-pstats pstats))
    (if (seq (:provables program))
      (let [[_ pstats]
            (profiled {}
                      (println "Verifying proofs:")
                      (verify-proofs program)
                      (println "Done."))]
        (println (format-pstats pstats))))))

(defn -main
  "A Metamath parser written in Clojure. Fun everywhere!"
  [filename]
  (try
    (parse-mm filename)
    (catch Exception e (println (.getMessage e)))))
