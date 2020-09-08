(ns mm-clj.handmade
  (:require
    [clojure.java.io :as io]
    [clojure.string :refer [includes? join split split-lines trim]]
    [clojure.data.int-map :as i]
    [taoensso.tufte :as tufte :refer [defnp profiled format-pstats]])
  (:import
    java.util.Arrays))


(set! *warn-on-reflection* true)

;;; Program parsing stuff

(defrecord Program [constants variables
                    symbols symbolmap labels labelmap
                    vartypes axioms provables
                    comments formatting])
(defrecord Assertion [label typ syms proof scope description])
(defrecord Scope [variables vartypes
                  floatings essentials disjoints
                  mvars mhypos mdisjs])

(defn file->bytes [filename]
  (with-open [xin (io/input-stream filename)
              xout (java.io.ByteArrayOutputStream.)]
    (io/copy xin xout)
    (.toByteArray xout)))

(defmacro substr
  [program start end]
  (let [tagged-program (vary-meta program assoc :tag `"[B")
        tagged-start (vary-meta start assoc :tag `Long)
        tagged-end (vary-meta end assoc :tag `Long)]
    `(apply str (map char (Arrays/copyOfRange ~tagged-program ~tagged-start ~tagged-end)))))

(defmacro getchr
  [program i]
  (let [tagged-program (vary-meta program assoc :tag `"[B")]
    `(char (aget ~tagged-program ~i))))

(defmacro next-chars-match?
  [program i c1 c2]
  `(and (= (getchr ~program ~i) ~c1)
        (= (getchr ~program (inc ~i)) ~c2)))

(defmacro end-stmt?
  [program i]
  `(next-chars-match? ~program ~i \$ \.))

(defnp parse-comment
  [program start state]
  (loop [i start]
    (if (= (getchr program i) \$)
      (case (getchr program (inc i))
        \( (throw (Exception. "Comments may not be nested"))
        \) (let [text (substr program start i)]
             (swap! state update-in [:program :comments] inc)
             (swap! state assoc :last-comment text)
             (when (:in-formatting @state)
               (swap! state assoc-in [:program :formatting] text)
               (swap! state dissoc :in-formatting))
             (+ i 2))
        \t (do (swap! state assoc :in-formatting true)
               (recur (inc i)))
        (recur (inc i)))
      (recur (inc i)))))

(defnp skip-spaces
  [program start state]
  (let [n (alength ^bytes program)]
    (loop [i start]
      (if (< i n)
        (let [c (getchr program i)]
          (if (or (= c \space) (= c \tab) (= c \newline))
            (recur (inc i))
            (if (and (= c \$) (= (getchr program (inc i)) \())
              (recur (long (parse-comment program (+ i 2) state)))
              i)))
        i))))

(defnp parse-label
  [program start]
  (loop [i start]
    (let [b (aget ^bytes program i)]
      (if (or (<= (byte \A) b (byte \Z))
              (<= (byte \a) b (byte \z))
              (<= (byte \0) b (byte \9))
              (= b (byte \-)) (= b (byte \_)) (= b (byte \.)))
        (recur (inc i))
        [i (substr program start i)]))))

(defnp parse-symbol
  [program start]
  (loop [i start]
    (let [b (aget ^bytes program i)]
      ; ASCII printable characters, except '$' and whitespaces
      (if (or (<= 0x21 b 0x23) (<= 0x25 b 0x7e))
        (recur (inc i))
        (if (= start i)
          (throw (Exception. (str i ": empty symbol found")))
          [i (substr program start i)])))))

(defnp add-floating
  [li typecode variable state]
  (swap! state
    (fn [s]
      (let [p (:program s)
            ti (get (:symbols p) typecode)
            vi (get (:symbols p) variable)]
          (if (or (nil? ti) (nil? vi))
            (throw (Exception. (str "Typecode " typecode " or variable " variable " not defined")))
            (if (contains? (-> :scope :vartypes) vi)
              (throw (Exception. (str "Variable " variable " already has a type")))
              (if (and (contains? (:vartypes p) vi) (not= ti (get (:vartypes p) vi)))
                (throw (Exception. (str "Variable " variable " was previously assigned a different type")))
                (-> s
                    (update-in [:program :vartypes] assoc vi ti)
                    (update-in [:scope :vartypes] assoc vi ti)
                    (update-in [:scope :floatings] assoc li [ti vi])))))))))

(defnp parse-floating-stmt
  [program start li state]
  (let [i (skip-spaces program start state)
        [i typecode] (parse-symbol program i)
        i (skip-spaces program i state)
        [i variable] (parse-symbol program i)
        i (skip-spaces program i state)]
    (add-floating li typecode variable state)
    (if (end-stmt? program i)
      (+ i 2)
      (throw (Exception. (str i ": unexpected token " (getchr program i)))))))

(defnp parse-symbols
  [program start state stop-marker]
  (loop [i start
         symbols []]
    (let [i (skip-spaces program i state)]
      (if (next-chars-match? program i \$ (char stop-marker))
        [(+ i 2) symbols]
        (let [[i sym] (parse-symbol program i)]
          (recur i (conj symbols sym)))))))

(defnp parse-typed-symbols
  ([program start state]
   (parse-typed-symbols program start state \.))
  ([program start state stop-marker]
   (let [i (skip-spaces program start state)
         [i typecode] (parse-symbol program i)
         i (skip-spaces program i state)
         [i symbols] (parse-symbols program i state stop-marker)]
     [i [typecode symbols]])))

(defnp encode-typed-symbols
  [typ syms state]
  (let [symbols (-> state :program :symbols)
        constants (-> state :program :constants)]
    (if-let [ti (get symbols typ)]
      (if (contains? constants ti)
        (let [sis (vec (map
                         (fn [sym]
                           (if-let [si (get symbols sym)]
                             si
                             (throw (Exception. (str "Variable or constant " sym " not defined")))))
                         syms))]
          [ti sis])
        (throw (Exception. (str "Type " typ " not found in constants"))))
      (throw (Exception. (str "Type " typ " not found in constants"))))))

(defnp decode-typed-symbols
  [ti sis state]
  (let [symbolmap (-> state :program :symbolmap)]
    (into [(get symbolmap ti)] (vec (map symbolmap sis)))))

(defnp add-essential
  [li typ syms state]
  (swap! state
    (fn [s]
      (let [ts (encode-typed-symbols typ syms s)]
        (update-in s [:scope :essentials] assoc li ts)))))

(defnp parse-essential-stmt
  [program start li state]
  (let [[i [typ syms]] (parse-typed-symbols program start state)]
    (add-essential li typ syms state)
    i))

(defnp add-assertion
  [assertion-type li typ syms proof state]
  (swap! state
    (fn [s]
      (let [[ti sis] (encode-typed-symbols typ syms s)
            scope (:scope s)
            desc (:last-comment s)
            a (Assertion. li ti sis proof scope desc)]
        (update-in s [:program assertion-type] assoc li a)))))

(defnp parse-proof
  [program start state]
  (loop [i start]
    (if (= (getchr program i) \$)
      (let [c (getchr program (inc i))]
        (if (= c \.)  ; (or (= c \.) (= c \=))
          (let [proof (substr program start i)]
            [(+ i 2) proof])
          (recur (inc i))))
      (recur (inc i)))))

(defnp parse-assertion-stmt
  [assertion-type program start li state]
  (let [stop-marker (if (= :axioms assertion-type) \. \=)
        [i [typ syms]] (parse-typed-symbols program start state stop-marker)
        [i proof] (if (= :axioms assertion-type) [i nil] (parse-proof program i state))]
    (add-assertion assertion-type li typ syms proof state)
    i))

(defnp parse-disjoint-stmt
  [program start state]
  (loop [i start]
    (if (end-stmt? program i)
      (+ i 2)
      (recur (inc i)))))

(defnp add-label
  [l state]
  (let [p (:program @state)
        li (count (:labels p))]
    (swap! state
           (fn [s]
             (if (or (contains? (:symbols p) l)
                     (contains? (:labels p) l))
               (throw (Exception. (str "Label " l " was already defined before")))
               (-> s
                 (update-in [:program :labels] assoc l li)
                 (update-in [:program :labelmap] assoc li l)))))
    li))

(defnp parse-labeled-stmt
  [program start state]
  (loop [i start]
    (let [[i label] (parse-label program i)
          i (skip-spaces program i state)
          li (add-label label state)]
      (if (= (getchr program i) \$)
        (case (getchr program (inc i))
          \f (parse-floating-stmt program (+ i 2) li state)
          \e (parse-essential-stmt program (+ i 2) li state)
          \a (parse-assertion-stmt :axioms program (+ i 2) li state)
          \p (parse-assertion-stmt :provables program (+ i 2) li state)
          (throw (Exception. (str i ": unexpected token $" (getchr program (inc i))))))
        (throw (Exception. (str i ": unexpected token " (getchr program i))))))))

(def parse-stmt)

(defnp parse-block
  [program start state]
  (let [saved-scope (:scope @state)]
    (loop [i start]
      (let [i (skip-spaces program i state)]
        (if (next-chars-match? program i \$ \})
          (do (swap! state assoc :scope saved-scope)
              (+ i 2))
          (recur (long (parse-stmt program i state))))))))

(defnp add-constant
  [c state]
  (swap! state
    (fn [s]
      (let [p (:program s)]
        (if (or (contains? (:symbols p) c)
                (contains? (:labels p) c))
          (throw (Exception. (str "Constant " c " was already defined before")))
          (let [ci (count (:symbols p))]
            (-> s
              (update-in [:program :symbols] assoc c ci)
              (update-in [:program :symbolmap] assoc ci c)
              (update-in [:program :constants] conj ci))))))))

(defnp add-variable
  [v state]
  (swap! state
    (fn [s]
      (let [p (:program s)]
        (if (contains? (:labels p) v)
          (throw (Exception. (str "Variable " v " matches an existing label")))
          (if-let [vi ((:symbols p) v)]
            (if (contains? (:constants p) vi)
              (throw (Exception. (str "Variable " v " matches an existing constant")))
              (if (contains? (-> s :scope :variables) vi)
                (throw (Exception. (str "Variable " v " was already defined before")))
                (update-in s [:scope :variables] conj vi)))
            (let [vi (count (:symbols p))]
              (-> s
                  (update-in [:program :symbols] assoc v vi)
                  (update-in [:program :symbolmap] assoc vi v)
                  (update-in [:program :variables] conj vi)
                  (update-in [:scope :variables] conj vi)))))))))

(defnp parse-const-var-stmt
  [program start add-symbol state]
  (loop [i start]
    (let [i (skip-spaces program i state)]
      (if (end-stmt? program i)
        (+ i 2)
        (let [[i sym] (parse-symbol program i)]
          (add-symbol sym state)
          (recur i))))))

(defnp parse-stmt
  [program start state]
  (loop [i start]
    (let [i (skip-spaces program i state)]
      (if (= (getchr program i) \$)
        (case (getchr program (inc i))
          \{ (parse-block program (+ i 2) state)
          \v (parse-const-var-stmt program (+ i 2) add-variable state)
          \d (parse-disjoint-stmt program (+ i 2) state)
          (throw (Exception. (str i ": unexpected token $" (getchr program (inc i))))))
        (parse-labeled-stmt program i state)))))

(defnp parse-top-level
  [program state]
  (let [n (alength ^bytes program)]
    (loop [i 0]
      (let [i (skip-spaces program i state)]
        (if (< i n)
          (if (next-chars-match? program i \$ \c)
            (recur (long (parse-const-var-stmt program (+ i 2) add-constant state)))
            (recur (long (parse-stmt program i state))))
          @state)))))

(defnp parse-mm-program
  "Parse a metamath program"
  [program]
  (let [state (atom {:program (Program. #{} #{}
                                        {} (i/int-map) {} (i/int-map)
                                        (i/int-map) (i/int-map) (i/int-map)
                                        0 "")
                     :scope (Scope. #{} (i/int-map)
                                    (i/int-map) (i/int-map) #{}
                                    #{} [] [])
                     :last-comment ""})]
    (parse-top-level program state)))

(defn create-symbol-map
  [formatting]
  (let [lines (->> formatting
                  (split-lines)
                  (filter #(includes? % "latexdef")))]
    (into {}
          (map #(if-let [m (re-find #"\s*latexdef\s+(?:\"([^\"]+)\"|'([^']+)')\s+as\s+(?:\"([^\"]+)\"|'([^']+)')\s*;" %)]
                  (let [[l1 l2 r1 r2] (into [] (rest m))]
                    [(or l1 l2) (or r1 r2)]))
               lines))))

(defn assertion->tex
  [assertion-symbols symbolmap]
  (str "\\[" (join " " (map symbolmap assertion-symbols)) "\\]"))

(defn wrap-p
  [text]
  (str "<p>" text "</p>"))

(def header "<!DOCTYPE html>
<html>
<head>
  <meta charset=\"utf-8\">
  <meta name=\"viewport\" content=\"width=device-width\">
  <title>Metamath sample</title>
  <script src=\"https://polyfill.io/v3/polyfill.min.js?features=es6\"></script>
  <script id=\"MathJax-script\" async
          src=\"https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js\">
  </script>
</head>
<body>
<h1>Metamath sample</h1>
")

(def footer "</body>
</html>")

(defnp fmt-axiom
  [axiom symbolmap]
  (str
    (wrap-p (second axiom))
    (wrap-p (assertion->tex (first axiom) symbolmap))))

(defn parse-mm
  "Parse a metamath file"
  [filename]
  (let [[[program state] pstats]
        (profiled {}
                  (let [_ (print "Reading program from file... ")
                        _ (flush)
                        bs (file->bytes filename)
                        _ (println "OK!")
                        _ (print "Parsing program... ")
                        _ (flush)
                        state (parse-mm-program bs)
                        _ (println "OK!")
                        program (:program state)]
                    [program state]))]
    ; (println (str (:comments program) " comments"))
    (println (str (count (:symbols program)) " symbols"))
    (println (str (count (:constants program)) " constants"))
    (println (str (count (:variables program)) " variables"))
    (println (str (count (-> state :scope :essentials)) " essentials"))
    (println (str (count (:axioms program)) " axioms"))
    (println (str (count (:provables program)) " provables"))
    (let [axioms (map (fn [[_ {ti :typ, sis :syms, desc :description}]]
                        [(decode-typed-symbols ti sis state) desc])
                      (:axioms program))
          axioms (take-while #(not (= "CondEq" (get (first %) 1))) axioms)
          axioms (filter #(not (= "wff" (first (first %)))) axioms)
          formatting (:formatting program)
          symbolmap (create-symbol-map formatting)
          output (join "\n" (map #(fmt-axiom % symbolmap) axioms))]
          ; ]
      ; (println (str "Formatting:\n" formatting))
      ; (println symbolmap))
      ; (println axioms)
      (spit "sample.html" (str header output footer))
      (println (format-pstats pstats))
    )))
