(ns xapi.cron
  (:require [mount.core :as mount]

            [xapi.core :as core]
            [xapi.core.db :as db]
            [xapi.log :as log]
            [xapi.ghost :as ghost]
            [xapi.hooks :as hooks]))


(defn scheduled-uuids-q []
  {:from   [:posts]
   :select [:id]
   :where  [:and
            [:= :status "scheduled"]
            [:<= :published_at :%now]]})


(defn -process-scheduled [id]
  (let [dbpost (db/one {:update    :posts
                        :set       {:status "published"}
                        :where     [:= :id id]
                        :returning @ghost/DBPOST-KEYS})
        post   (-> (ghost/dbres->post dbpost)
                   (assoc :status "published"))]
    (hooks/send-webhooks! (:user_id dbpost) post)))


(defn process-scheduled! []
  (when-let [ids (core/report-exc "Error querying DB"
                     (seq (db/q (scheduled-uuids-q))))]
    (log/info "Found scheduled messages" {:total (count ids)})
    (doseq [id ids]
      (core/report-exc "Error processing cron job"
        (-process-scheduled id)))))


(defn -process [*stop]
  (loop []
    (process-scheduled!)
    (Thread/sleep 10000)
    (when-not @*stop
      (recur))))


(defn start-cron []
  (let [*stop (atom false)
        stop-fn #(reset! *stop true)]
    (doto (Thread. #(-process *stop))
      (.setName (str "cron-" (System/currentTimeMillis)))
      (.start))
    {:stop stop-fn}))


(mount/defstate cron
  :start (when db/conn
           (start-cron))
  :stop ((:stop cron)))
