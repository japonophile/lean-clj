(ns mm-app.handler
  (:require
    [clojure.data.int-map :as i]
    [clojure.core.async :refer [go]]
    [clojure.string :as s :refer [includes? split-lines]]
    [reitit.ring :as reitit-ring]
    [mm-app.middleware :refer [middleware]]
    [hiccup.page :refer [include-js include-css html5]]
    [config.core :refer [env]])
  (:use
    [mm-clj.model]))


(def mount-target
  [:div#app
   [:h2 "Welcome to mm-app"]
   [:p "please wait while Figwheel is waking up ..."]
   [:p "(Check the js console for hints if nothing exciting happens.)"]])

(defn head []
  [:head
   [:meta {:charset "utf-8"}]
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))
   (include-css "/css/main.css")
   (include-js "https://polyfill.io/v3/polyfill.min.js?features=es6")
   [:script {:id "MathJax-script" :type "text/javascript"  ; :async "async"
             :src "https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"}]])

(defn loading-page []
  (html5
   (head)
   [:body {:class "body-container"}
    mount-target
    (include-js "/js/app.js")]))

(defn index-handler
  [_request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (loading-page)})

(defn decode-typed-symbols
  [{li :label, ti :typ, sis :syms :as ts} state]
  (let [symbolmap (-> @state :program :symbolmap)
        labelmap (-> @state :program :labelmap)]
    (-> ts
      (assoc :label (get labelmap li))
      (assoc :typ (get symbolmap ti))
      (assoc :syms (vec (map #(get symbolmap %) sis))))))

(defn decode-assertion
  [assertion state]
  (-> assertion
    (decode-typed-symbols state)
    (update-in [:scope :essentials]
               #(into {} (for [[li e] %] [li (decode-typed-symbols e state)])))))

(defn api-axioms-handler
  [request state]
  (let [sec-id (read-string (-> request :path-params :section-id))
        subs-id (read-string (-> request :path-params :subs-id))
        axiom-keys (get-in @state [:program :structure sec-id :subs subs-id :assertions])
        axioms (vec (map (-> @state :program :axioms) axiom-keys))
        axioms (map #(decode-assertion % state) axioms)]
    {:status 200
     :header {"Content-Type" "text/edn"}
     :body (pr-str axioms)}))

(defn create-symbol-map
  [formatting]
  (let [map-re #"\s*latexdef\s+(?:\"([^\"]+)\"|'([^']+)')\s+as\s+(?:\"([^\"]+)\"|'([^']+)')\s*;"
        lines (->> formatting
                  (split-lines)
                  (filter #(includes? % "latexdef")))]
    (into {}
          (map #(if-let [m (re-find map-re %)]
                  (let [[l1 l2 r1 r2] (into [] (rest m))]
                    [(or l1 l2) (or r1 r2)]))
               lines))))

(defonce state (atom {}))

(defn load-state
  [state]
  (go
    (let [_ (println "Start loading state...")
          loaded-state (read-string (slurp "state.edn"))
          _ (println "... State loaded")]
      (reset! state loaded-state))))

(defn global-state
  []
  (when (= 0 (count (keys @state)))
    (load-state state))
  state)

(def app
  (let [state (global-state)]
    (reitit-ring/ring-handler
      (reitit-ring/router
        [["/" {:get {:handler index-handler}}]
         ["/section"
          ["/:section-id" {:get {:handler index-handler
                                 :parameters {:path {:section-id int?}}}}]
          ["/:section-id/:subs-id" {:get {:handler index-handler
                                          :parameters {:path {:section-id int? :subs-id int?}}}}]]
         ["/api/state"
          ["/structure"
           {:get {:handler (fn [_request]
                             {:status 200
                              :header {"Content-Type" "text/edn"}
                              :body (pr-str (-> @state :program :structure))})}}]
          ["/symbolmap"
           {:get {:handler (fn [_request]
                             {:status 200
                              :header {"Content-Type" "text/edn"}
                              :body (pr-str (-> @state :program :formatting create-symbol-map))})}}]
          ["/axioms/:section-id/:subs-id"
           {:get {:handler #(api-axioms-handler % state)
                  :parameters {:path {:section-id int? :subs-id int?}}}}]]
         ["/about" {:get {:handler index-handler}}]])
      (reitit-ring/routes
        (reitit-ring/create-resource-handler {:path "/" :root "/public"})
        (reitit-ring/create-default-handler))
      {:middleware middleware})))
