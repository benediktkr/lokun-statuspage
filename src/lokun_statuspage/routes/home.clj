(ns lokun-statuspage.routes.home
  (:use compojure.core
        [clojure.set :only [difference]])
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

(defn format-heartbeat [secs]
  (/ secs 60))

(defn format-usage [gb]
  (format "%.2f TB" (/ (float gb) 1000)))

(defn format-selfcheck [good]
  (if good "Good" "Error"))

(defn format-bandwidth [bps]
  (let [kbps (int (/ bps 1000))]
    (if (> 1000 kbps)
      (str kbps " kbytes")
      (format "%.2f mbytes" (float (/ kbps 1000))))))

(defn format-node [node]
  (let [heartbeat-age (:heartbeat_age node)
        selfcheck (:selfcheck node)
        total_throughput (:total_throughput node)
        throughput (:throughput node)]
    (assoc node
      :heartbeat_age (format-heartbeat heartbeat-age)
      :selfcheck (format-selfcheck selfcheck)
      :total_throughput (format-usage total_throughput)
      :throughput (format-bandwidth throughput))))

(defn format-nodes [nodes]
  (map format-node (sort-by :name nodes)))

(defn home-page []
  (let [basic-status (api-call :get "/lokun/status")
        nodes (set (:data (api-call :post "/nodes" {:form-params api})))
        enabled-nodes (set (filter :enabled nodes))
        disabled-nodes (difference nodes enabled-nodes)]
    (layout/render
     "home.html" {:nodes (format-nodes enabled-nodes)
                  :disabled (format-nodes disabled-nodes)
                  :response basic-status
                  :usersum (reduce + (map :usercount enabled-nodes))
                  :bandwidthsum (format-bandwidth
                                 (reduce + (map :throughput enabled-nodes)))
                  :totalsum (format-usage (reduce + (map :total_throughput enabled-nodes)))})))

(defn about-page []
  (layout/render "about.html"))

(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/about" [] (about-page)))
