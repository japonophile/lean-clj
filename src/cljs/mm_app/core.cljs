(ns mm-app.core
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [clerk.core :as clerk]
   [accountant.core :as accountant]
   [cljs-http.client :as http]
   [cljs.core.async :refer [<!]]
   [clojure.string :refer [replace]]
   [cljs.reader :refer [read-string]]
   [mm-clj.formatting :as fmt]
   [mm-app.components.progress-bar :as pb]))


(defn remove-record-refs
  [s]
  (-> s
      (replace "#mm_clj.model.Program" "")
      (replace "#mm_clj.model.Assertion" "")
      (replace "#mm_clj.model.Essential" "")
      (replace "#mm_clj.model.Scope" "")))

;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ["/mm"
     ["/:file-id" :mm-file]
     ["/:file-id/:sec-id" :section]
     ["/:file-id/:sec-id/:subs-id" :subsection]]
    ["/api/mm"
     ["/" :load-mm-files]
     ["/:file-id" :load-mm-file]
     ["/:file-id/:sec-id/:subs-id" :load-mm-subs]]
    ["/about" :about]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))

;; -------------------------
;; State

(defonce state (atom {}))

(defn load-mm-files
  []
  (go (let [response (<! (http/get (path-for :load-mm-files)))]
        (swap! state assoc :mm-files (read-string (:body response))))))

(defn try-loading-mm-file
  [file-id]
  (go (let [response (<! (http/get (path-for :load-mm-file {:file-id file-id})))
            body (read-string (:body response))]
        (if-let [program (:program body)]
          (swap! state assoc :program program)
          (do
            (swap! state assoc :loading (:loading body))
            (js/setTimeout #(try-loading-mm-file file-id) 250))))))

(defn load-mm-file
  [file-id]
  ; only load if not loaded yet
  (if (nil? (get-in @state [:mm-files file-id :program]))
    (try-loading-mm-file file-id)))

(defn load-mm-subs
  [file-id sec-id subs-id]
  (go (let [response (<! (http/get
                           (path-for :load-mm-subs
                                     {:file-id file-id :sec-id sec-id :subs-id subs-id})))]
        (swap! state assoc-in [:program :axioms sec-id subs-id]
               (read-string (remove-record-refs (:body response)))))))

;; -------------------------
;; Page components

(defn home-page []
  (fn []
    [:span.main
     [:h1 "Metamath app"]
     [:div ""]]))

(defn blank-page []
  (fn []
    [:div ""]))

(defn table-of-contents []
  (fn []
    (if-let [structure (-> @state :program :structure)]
      (let [routing-data (session/get :route)
            file-id (int (get-in routing-data [:route-params :file-id]))]
        [:span.main
         [:h1 "Table of Contents"]
         [:ul (map-indexed
                (fn [sec-id section]
                  [:li {:name (str "sec-" sec-id) :key (str "sec-" sec-id)}
                   [:a {:href (path-for :section {:file-id file-id :sec-id sec-id})}
                    (:title section)]])
                structure)]])
      (if-let [progress (:loading @state)]
        [:span.main
         [:h1 "Loading..."]
         [:br ] [:br ]
         (pb/progress-bar (:progress progress) (:total progress))
         [:br ] [:br ]]
        [:span.main ""]))))

(defn mm-file-selection []
  (fn []
    [:span.main
     [:h1 "Metamath files"]
     [:ul (map-indexed
            (fn [file-id mm-file]
              [:li {:name (str "mm-" file-id) :key (str "mm-" file-id)}
               [:a
                {:on-click (fn [_] (load-mm-file file-id))  ; FIXME
                 :href (path-for :mm-file {:file-id file-id})} mm-file]])
            (-> @state :mm-files))]]))

(defn section-page []
  (fn []
    (let [routing-data (session/get :route)
          file-id (int (get-in routing-data [:route-params :file-id]))
          sec-id (int (get-in routing-data [:route-params :sec-id]))
          structure (-> @state :program :structure)
          section (get structure sec-id)
          symbolmap (-> @state :program :symbolmap)]
      [:span.main
       [:p [:a {:href (path-for :index)} "Home"]]
       [:h1 (:title section)]
       [:p (fmt/fmt-text (:description section) symbolmap)]
       [:ul (map-indexed
              (fn [subs-id subsection]
                [:li {:name (str "subs-" subs-id) :key (str "subs-" subs-id)}
                 [:a {:href (path-for :subsection {:file-id file-id :sec-id sec-id :subs-id subs-id})}
                  (:title subsection)]])
              (:subs section))]])))

(defn subsection-page []
  (fn []
    (let [routing-data (session/get :route)
          file-id (int (get-in routing-data [:route-params :file-id]))
          sec-id (int (get-in routing-data [:route-params :sec-id]))
          structure (-> @state :program :structure)
          section (get structure sec-id)
          subs-id (int (get-in routing-data [:route-params :subs-id]))
          subsection (get-in section [:subs subs-id])
          symbolmap (-> @state :program :symbolmap)]
      [:span.main
       [:p [:a {:href (path-for :index)} "Home"] " >> "
        [:a {:href (path-for :section {:file-id file-id :sec-id sec-id})} (:title section)]]
       [:h1 (:title subsection)]
       [:p (fmt/fmt-text (:description subsection) symbolmap)]
       (if-let [axioms (get-in @state [:program :axioms sec-id subs-id])]
         [:div (fmt/format-axioms axioms symbolmap)]
         [:a {:on-click (fn [_] (load-mm-subs file-id sec-id subs-id))}
          (str (count (:assertions subsection)) " assertions.")])])))

(defn about-page []
  (fn [] [:span.main
          [:h1 "About Metamath app"]]))


;; -------------------------
;; Translate routes -> page components

(defn page-for [route]
  (case route
    :index #'home-page
    :mm-file #'home-page
    :section #'section-page
    :subsection #'subsection-page
    :about #'about-page))

(defn left-pane-for [route]
  (case route
    :index #'mm-file-selection
    :mm-file #'table-of-contents
    :section #'table-of-contents
    :subsection #'table-of-contents
    :about #'blank-page))


;; -------------------------
;; Page mounting component

(defn current-page []
  (fn []
    (let [route (session/get :route)
          page (:main-page route)
          left-pane (:left-pane route)]
      [:div
       [:header
        [:p [:a {:href (path-for :index)} "Home"] " | "
         (when (= (count (get-in @state [:program :structure])) 0)
           [:span [:a {:on-click (fn [_] (load-mm-file 0))} "Load"] " | "])
         [:a {:href (path-for :about)} "About Metamath app"]]]
       [:div.mainlayout
        [:div.leftpane [left-pane]]
        [:div.mainpane [page]]
        [:div.rightpane ""]]
       [:footer
        [:p "Metamath app was generated by the "
         [:a {:href "https://github.com/reagent-project/reagent-template"} "Reagent Template"] "."]]])))

(def current-page-mathjs
  (with-meta current-page
    {:component-did-update (fn [_] (. js/MathJax typeset))
     :component-did-mount  (fn [_] (. js/MathJax typeset))}))

;; -------------------------
;; Initialize app

(defn mount-root []
  (load-mm-files)
  (rdom/render [current-page-mathjs] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:left-pane (left-pane-for current-page)
                              :main-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
