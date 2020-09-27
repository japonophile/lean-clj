(ns mm-clj.formatting
  (:require
    [clojure.string :as s :refer [join split starts-with? trim]]))


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
                (conj buffer [:a {:href link} label] (join " " otherwords))))
            [:span beginning] tokens)))

(defn subst-newlines
  [tokens]
  (reduce (fn [buffer token]
            (if (string? token)
              (let [paragraphs (split token #"\n\n")]
                ; (into buffer (map #(vec [:p %]) paragraphs)))
                (into buffer (interpose [:br] paragraphs)))
              (conj buffer token)))
          [(first tokens)] (rest tokens)))

(defn apply-emphasis
  [tokens]
  tokens)
  ; (reduce (fn [buffer token]
  ;           (if (string? token)
  ;             (into buffer (s/replace txt #"_([^_ ][^_]*[^_ ])_" [:em "$1"]))
  ;             (let [paragraphs (split token #"\n\n")]
  ;               (into buffer (interpose [:br] paragraphs)))
  ;             (conj buffer token)))
  ;         (first tokens) (rest tokens)))
  ; (s/replace txt #"_([^_ ][^_]*[^_ ])_" [:em "$1"]))

(defn fmt-text
  [txt symbolmap]
  (-> txt
    (text->tex symbolmap)
    (subst-refs)
    (subst-newlines)
    (apply-emphasis)))

(defn fmt-title-desc
  [title desc symbolmap]
  (if title
    [:p [:span.title (fmt-text title symbolmap)] (fmt-text desc symbolmap)]
    [:p (fmt-text desc symbolmap)]))

(defn fmt-hypothese
  [hypo symbolmap]
  [:p (assertion->tex (into [(:typ hypo)] (:syms hypo)) symbolmap "\\(" "\\)")])

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
  (if-let [l (:label axiom)]
    [:div.theorem {:id l}
     [:p [:span {:class (str "title " (if-let [c (:category axiom)] (name c) ""))}
          (assertion-categ axiom)]]
     (fmt-title-desc (:title axiom) (:description axiom) symbolmap)
     (when-let [essentials (vals (-> axiom :scope :essentials))]
       [:div
        [:p "If"]
        [:div (map #(fmt-hypothese % symbolmap) essentials)]
        [:p "Then"]])
     [:p (assertion->tex (into [(:typ axiom)] (:syms axiom)) symbolmap "\\(" "\\)")]]))

(defn format-axioms
  [axioms symbolmap]
  (let [axioms (filter #(not (= "wff" (:typ %))) axioms)]
    (map #(fmt-axiom % symbolmap) axioms)))
