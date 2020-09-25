(ns mm-app.server
  (:require
    [mm-app.handler :refer [app]]
    [config.core :refer [env]]
    [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))

(defn start-server []
  (let [port (or (env :port) 3000)]
    (run-jetty #'app {:port port :join? false})))
