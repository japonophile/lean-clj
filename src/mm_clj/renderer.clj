(ns mm-clj.renderer
  (:require
    [clojure.string :as s :refer [includes? join split split-lines starts-with? trim]]
    [clojure.data.int-map :as i]
    [hiccup.core :as h]))


(set! *warn-on-reflection* true)

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

(defn map-symbols
  [symbolmap symbols]
  (map (fn [s]
         (let [sym (symbolmap s)]
           (if (= " & " sym) " \\& " sym)))
       symbols))

(defn assertion->tex
  [assertion-symbols symbolmap start end]
  (if (> (count assertion-symbols) 0)
    (str start (join " " (map-symbols symbolmap assertion-symbols)) end)
    ""))

(defn text->tex
  [text symbolmap]
  (reduce (fn [buffer [txt & [syms]]]
            (str buffer txt
                 (if (nil? syms)
                   ""
                   (assertion->tex (split (trim syms) #" ") symbolmap "\\(" "\\)"))))
          "" (partition-all 2 (split text #"`"))))

(defn subst-refs
  [txt]
  (let [[beginning & tokens] (split txt #"~ ")]
    (reduce (fn [buffer token]
              (let [[label & otherwords] (split token #" ")
                    link (if (starts-with? label "http") label (str "#" label))]
                (str buffer " " (h/html [:a {:href link} label])
                     " " (join " " otherwords))))
            beginning tokens)))

(defn apply-emphasis
  [txt]
  (s/replace txt #"_([^_ ][^_]*[^_ ])_" (h/html [:em "$1"])))

(defn fmt-text
  [txt symbolmap]
  (-> txt
    (text->tex symbolmap)
    (subst-refs)
    (apply-emphasis)))

(defn fmt-title-desc
  [title desc symbolmap]
  (if title
    [:p [:span.title (fmt-text title symbolmap)] (fmt-text desc symbolmap)]
    [:p (fmt-text desc symbolmap)]))

(defn fmt-hypothese
  [hypo symbolmap]
  [:p (assertion->tex (into [(:typ hypo)] (:syms hypo)) symbolmap "\\(" "\\)")])

(defn decode-typed-symbols
  [{li :label, ti :typ, sis :syms :as ts} state]
  (let [symbolmap (-> state :program :symbolmap)
        labelmap (-> state :program :labelmap)]
    (-> ts
      (assoc :label (get labelmap li))
      (assoc :typ (get symbolmap ti))
      (assoc :syms (vec (map #(get symbolmap %) sis))))))

(defn decode-assertion
  [assertion state]
  (-> assertion
    (decode-typed-symbols state)
    (update-in [:scope :essentials]
               #(into (i/int-map) (for [[li e] %] [li (decode-typed-symbols e state)])))))

(defn assertion-categ
  [assertion]
  (condp = (:category assertion)
    :axiom "Axiom"
    :theorem "Theorem"
    :definition "Definition"
    :declaration "Declaration"
    ""))

(defn fmt-axiom
  [axiom symbolmap]
  (let [l (:label axiom)]
    (h/html [:div.theorem {:id l}
             [:p [:span {:class (str "title " (name (:category axiom)))} (assertion-categ axiom)]]
             (fmt-title-desc (:title axiom) (:description axiom) symbolmap)
             (when-let [essentials (vals (-> axiom :scope :essentials))]
               [:div
                [:p "If"]
                [:div (map #(fmt-hypothese % symbolmap) essentials)]
                [:p "Then"]])
             [:p (assertion->tex (into [(:typ axiom)] (:syms axiom)) symbolmap "\\(" "\\)")]])))

(def header "<!DOCTYPE html>
<html>
<head>
  <meta charset=\"utf-8\">
  <meta name=\"viewport\" content=\"width=device-width\">
  <link rel=\"stylesheet\" href=\"style/main.css\">
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

(defn print-truncated
  [txt]
  (let [n (count txt)]
    (when (< 0 n)
      (if (< n 50)
        (println (str "      " txt))
        (println (str "      " (subs txt 0 50) "..."))))))

(defn print-structure
  [structure]
  (println "Program structure")
  (loop [[section & othersections] structure
         i 1]
    (when section
      (println (str "  " i ". " (:title section)))
      (print-truncated (:description section))
      (loop [[subsection & othersubsections] (:subs section)
             j 1]
        (when subsection
          (println (str "    " i "." j " " (:title subsection)))
          (print-truncated (:description subsection))
          (println (str "      " (count (:assertions subsection)) " assertions"))
          (recur othersubsections (inc j))))
      (recur othersections (inc i)))))

(defn render
  [state]
  (let [program (:program state)]
    (print-structure (:structure program))
    (let [axioms (map #(decode-assertion % state) (vals (:axioms program)))
          axioms (take-while #(not (= "CondEq" (get (:syms %) 0))) axioms)
          axioms (filter #(not (= "wff" (:typ %))) axioms)
          formatting (:formatting program)
          symbolmap (create-symbol-map formatting)
          output (join "\n" (map #(fmt-axiom % symbolmap) axioms))]
      (spit "sample.html" (str header output footer)))))
