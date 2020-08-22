(ns mm-clj.handmade
  (:require
    [clojure.java.io :as io]
    [clojure.string :refer [includes? join split split-lines trim]]
    [taoensso.tufte :as tufte :refer [defnp profiled format-pstats]])
  (:import
    java.util.Arrays))


(set! *warn-on-reflection* true)

;;; Program parsing stuff

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

(defmacro end-stmt?
  [program i]
  `(and (= (getchr ~program ~i) \$)
        (= (getchr ~program (inc ~i)) \.)))

(defnp skip-whitespaces
  [program start]
  (let [n (alength ^bytes program)]
    (loop [i start]
      (if (< i n)
        (let [c (getchr program i)]
          (if (or (= c \space) (= c \tab) (= c \newline))
            (recur (inc i))
            i))
        i))))

(defnp parse-comment
  [program start state]
  (loop [i start]
    (if (= (getchr program i) \$)
      (case (getchr program (inc i))
        \( (throw (Exception. "Comments may not be nested"))
        \) (let [text (substr program start i)]
             (swap! state update-in [:comments] inc)
             (swap! state assoc :last-comment text)
             (when (:in-formatting @state)
               (swap! state assoc :formatting text)
               (swap! state dissoc :in-formatting))
             (+ i 2))
        \t (do (swap! state assoc :in-formatting true)
               (recur (inc i)))
        (recur (inc i)))
      (recur (inc i)))))

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

(defnp parse-floating-stmt
  [program start label state]
  (loop [i start]
    (if (end-stmt? program i)
      (+ i 2)
      (recur (inc i)))))

(defnp parse-essential-stmt
  [program start label state]
  (loop [i start]
    (if (end-stmt? program i)
      (+ i 2)
      (recur (inc i)))))

(defnp parse-assertion-stmt
  [assertion-type program start label state]
  (loop [i start]
    (if (= (getchr program i) \$)
      (let [c (getchr program (inc i))]
        (if (= c \.)  ; (or (= c \.) (= c \=))
          (let [assertion (substr program start i)]
            (swap! state update-in [assertion-type] conj assertion)
            (+ i 2))
          (recur (inc i))))
      (recur (inc i)))))

(defnp parse-variable-stmt
  [program start state]
  (loop [i start]
    (if (end-stmt? program i)
      (+ i 2)
      (recur (inc i)))))

(defnp parse-disjoint-stmt
  [program start state]
  (loop [i start]
    (if (end-stmt? program i)
      (+ i 2)
      (recur (inc i)))))

(defnp parse-labeled-stmt
  [program start state]
  (loop [i start]
    (let [[i label] (parse-label program i)
          i (skip-whitespaces program i)]
      (if (= (getchr program i) \$)
        (case (getchr program (inc i))
          \( (recur (long (parse-comment program (+ i 2) state)))
          \f (parse-floating-stmt program (+ i 2) label state)
          \e (parse-essential-stmt program (+ i 2) label state)
          \a (parse-assertion-stmt :axioms program (+ i 2) label state)
          \p (parse-assertion-stmt :provables program (+ i 2) label state)
          (throw (Exception. (str i ": Unexpected token $" (getchr program (inc i))))))
        (throw (Exception. (str i ": Unexpected token " (getchr program  i))))))))

(def parse-stmt)

(defnp parse-block
  [program start state]
  (loop [i start]
    (let [i (skip-whitespaces program i)]
      (if (= (getchr program i) \$)
        (case (getchr program (inc i))
          \( (recur (long (parse-comment program (+ i 2) state)))
          \} (+ i 2)
          (recur (long (parse-stmt program i state))))
        (recur (long (parse-stmt program i state)))))))

(defnp parse-stmt
  [program start state]
  (loop [i start]
    (let [i (skip-whitespaces program i)]
      (if (= (getchr program i) \$)
        (case (getchr program (inc i))
          \( (recur (long (parse-comment program (+ i 2) state)))
          \{ (parse-block program (+ i 2) state)
          \v (parse-variable-stmt program (+ i 2) state)
          \d (parse-disjoint-stmt program (+ i 2) state)
          (throw (Exception. (str i ": Unexpected token $" (getchr program (inc i))))))
        (parse-labeled-stmt program i state)))))

(defnp parse-constant-stmt
  [program start state]
  (loop [i start]
    (if (end-stmt? program i)
      (+ i 2)
      (recur (inc i)))))

(defnp parse-top-level
  [program state]
  (let [n (alength ^bytes program)]
    (println n)
    (loop [i 0]
      (let [i (skip-whitespaces program i)]
        (if (< i n)
          (if (= (getchr program i) \$)
            (case (getchr program (inc i))
              \( (recur (long (parse-comment program (+ i 2) state)))
              \c (recur (long (parse-constant-stmt program (+ i 2) state)))
              (recur (long (parse-stmt program i state))))
            (recur (long (parse-stmt program i state))))
          @state)))))

(defnp parse-mm-program
  "Parse a metamath program"
  [program]
  (let [state (atom {:comments 0 :axioms [] :provables []})]
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

(defn parse-mm
  "Parse a metamath file"
  [filename]
  (let [[program pstats]
        (profiled {}
                  (let [_ (print "Reading program from file... ")
                        _ (flush)
                        bs (file->bytes filename)
                        _ (println "OK!")
                        _ (print "Parsing program... ")
                        _ (flush)
                        program (parse-mm-program bs)
                        _ (println "OK!")]
                    program))]
    (println (str (:comments program) " comments"))
    (let [formatting (:formatting program)
          symbolmap (create-symbol-map formatting)
          axioms (map #(split (trim %) #"\s+") (:axioms program))
          axioms (take-while #(not (= "CondEq" (get % 1))) axioms)
          axioms (filter #(not (= "wff" (first %))) axioms)
          output (join "\n" (map (comp wrap-p #(assertion->tex % symbolmap)) axioms))]
      ; (println (str "Formatting:\n" formatting))
      ; (println symbolmap))
      ; (println axioms)
      ; (spit "sample.html" (str header output footer))
      (println (format-pstats pstats))
    )))
