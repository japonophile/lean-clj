(ns mm-app.handler
  (:require
    [clojure.data.int-map :as i]
    [clojure.core.async :refer [go]]
    [clojure.string :as s :refer [includes? split-lines]]
    [clojure.java.io :as io]
    [reitit.ring :as reitit-ring]
    [mm-app.middleware :refer [middleware]]
    [mm-clj.parser :as p]
    [hiccup.page :refer [include-js include-css html5]]
    [config.core :refer [env]]
    [progrock.core :as pr])
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
   [:meta {:name "viewport" :content "width=device-width, initial-scale=1"}]
   (include-css (if (env :dev) "/css/site.css" "/css/site.min.css"))
   (include-css "/css/main.css")
   ; mathjax
   (include-js "https://polyfill.io/v3/polyfill.min.js?features=es6")
   [:script {:id "MathJax-script" :type "text/javascript"  ; :async "async"
             :src "https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"}]
   ; bootstrap
   (include-css "https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap.min.css")
   (include-js "https://ajax.googleapis.com/ajax/libs/jquery/3.5.1/jquery.min.js")
   (include-js "https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/js/bootstrap.min.js")])

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
  [{li :label, ti :typ, sis :syms :as ts} mm]
  (let [symbolmap (-> mm :program :symbolmap)
        labelmap (-> mm :program :labelmap)]
    (-> ts
      (assoc :label (get labelmap li))
      (assoc :typ (get symbolmap ti))
      (assoc :syms (vec (map #(get symbolmap %) sis))))))

(defn decode-assertion
  [assertion mm]
  (-> assertion
    (decode-typed-symbols mm)
    (update-in [:scope :essentials]
               #(into {} (for [[li e] %] [li (decode-typed-symbols e mm)])))))

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

(defn api-mm-files-handler
  [_request state]
  (let [mm-files (map :filename (vals (:mm-files @state)))]
    {:status 200
     :header {"Content-Type" "text/edn"}
     :body (pr-str mm-files)}))

(defn load-program
  [file-id state]
  (let [filename (get-in @state [:mm-files file-id :filename])
        progress (atom (pr/progress-bar 100))]
    (swap! state assoc-in [:mm-files file-id :loading] progress)
    (go
      (println (str "Start loading program \"" filename "\"..."))
      (let [program (p/parse-mm (str "mm/" filename) :progress progress)]
        (println "... program loaded")
        (swap! state assoc-in [:mm-files file-id] (assoc program :filename filename))))
    progress))

(defmacro with-loaded-program
  [mm file-id body]
  `(if-let [program (:program ~mm)]
     ~body
     (if-let [progress (:loading ~mm)]
       {:loading @progress}
       {:loading @(load-program ~file-id state)})))

(defn api-mm-file-handler
  [request state]
  (let [file-id (read-string (-> request :path-params :file-id))
        mm (get-in @state [:mm-files file-id])]
    {:status 200
     :header {"Content-Type" "text/edn"}
     :body (pr-str
             (if-let [program (:program mm)]
               (let [structure (-> program :structure)
                     symbolmap (-> program :formatting create-symbol-map)]
                 {:program {:structure structure :symbolmap symbolmap}})
               (if-let [progress (:loading mm)]
                 {:loading @progress}
                 {:loading @(load-program file-id state)})))}))

(defn api-mm-subs-handler
  [request state]
  (let [path-params (:path-params request)
        file-id (read-string (:file-id path-params))
        mm (get-in @state [:mm-files file-id])]
    {:status 200
     :header {"Content-Type" "text/edn"}
     :body (pr-str
             (if-let [program (:program mm)]
               (let [sec-id (read-string (:sec-id path-params))
                     subs-id (read-string (:subs-id path-params))
                     axiom-keys (get-in program [:structure sec-id :subs subs-id :assertions])
                     axioms (vec (map (:axioms program) axiom-keys))
                     axioms (map #(decode-assertion % mm) axioms)]
                 axioms)
               (if-let [progress (:loading mm)]
                 {:loading @progress}
                 {:loading @(load-program file-id state)})))}))

(defonce state (atom {}))

(defn load-state
  [state]
  (let [mm-filenames (map #(.getName %) (filter #(.isFile %) (file-seq (io/file "mm"))))
        mm-files (into {} (map-indexed #(vec [%1 {:filename %2}]) mm-filenames))]
    (swap! state assoc :mm-files mm-files)))

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
         ["/mm"
          ["/:file-id"
           {:get {:handler index-handler
                  :parameters {:path {:file-id int?}}}}]
          ["/:file-id/:sec-id"
           {:get {:handler index-handler
                  :parameters {:path {:file-id int? :sec-id int?}}}}]
          ["/:file-id/:sec-id/:subs-id"
           {:get {:handler index-handler
                  :parameters {:path {:file-id int? :sec-id int? :subs-id int?}}}}]]
         ["/about" {:get {:handler index-handler}}]
         ["/api"
          ["/mm"
           ["/"
            {:get {:handler #(api-mm-files-handler % state)}}]
           ["/:file-id"
            {:get {:handler #(api-mm-file-handler % state)
                   :parameters {:path {:file-id int?}}}}]
           ["/:file-id/:sec-id/:subs-id"
            {:get {:handler #(api-mm-subs-handler % state)
                   :parameters {:path {:sec-id int? :subs-id int?}}}}]]]])
      (reitit-ring/routes
        (reitit-ring/create-resource-handler {:path "/" :root "/public"})
        (reitit-ring/create-default-handler))
      {:middleware middleware})))
