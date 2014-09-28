(ns lokun-statuspage.routes.home
  (:use compojure.core)
  (:require [lokun-statuspage.views.layout :as layout]
            [lokun-statuspage.util :as util]
            [clj-http.client :as client]
            [clojure.data.json :as json]))

(def api-key (slurp "key.txt"))

(def api {:url "https://api.lokun.is"
          :secret api-key})

(defn api-action [method path & [opts]]
  (let [response (client/request
                  (assoc opts
                    :method method
                    :accept :json
                    :url (str (:url api) path)))]
    (assoc response
      :body (json/read-str (:body response) :key-fn keyword))))

(defn api-call [method path & [opts]]
  (:body (api-action method path opts)))

(defn exp [x n] (reduce * (repeat n x)))

(defn human-readable [node]
  (let [heartbeat-age (:heartbeat_age node)
        kbytes (int (/ (:throughput node) 1000))
        selfcheck (:selfcheck node)]
    (assoc node
      :heartbeat_age (/ heartbeat-age 60)
      :selfcheck (if selfcheck "Good" "Error")
      :throughput (if (> 100 kbytes)
                    (str kbytes " kbytes")
                    (format "%.2f mbytes" (float (/ kbytes 1000)))))))

(defn home-page []
  (let [basic-status (api-call :get "/lokun/status")
        nodelist (:data (api-call :post "/nodes" {:form-params api}))
        all-nodes (map human-readable nodelist)]
    (layout/render
     "home.html" {:nodes (filter :enabled (sort-by :name all-nodes))
                  :disabled (filter #(not (:enabled %)) all-nodes)
                  :response basic-status
                  :usersum (reduce + (map :usercount nodelist))})))

(defn about-page []
  (layout/render "about.html"))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page)))
