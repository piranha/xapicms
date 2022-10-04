(ns xapi.cron
  (:require [mount.core :as mount]

            [xapi.core :as core]
            [xapi.core.db :as db]
            [xapi.log :as log]
            [xapi.ghost :as ghost]
            [xapi.hooks :as hooks]))


(defn scheduled-q []
  {:from   [:posts]
   :select @ghost/DBPOST-KEYS
   :where  [:and
            [:= :status "scheduled"]
            [:<= :published_at :%now]]})


(defn -process-scheduled [dbpost]
  (let [post (ghost/dbres->post dbpost)]
    (hooks/send-webhooks! (:user_id dbpost) (assoc post :status "published")))
  (db/one {:update :posts
           :set    {:status "published"}
           :where  [:= :uuid (:uuid dbpost)]}))


(defn process-scheduled! []
  (when-let [posts (core/report-exc "Error querying DB"
                     (seq (db/q (scheduled-q))))]
    (log/info "Found scheduled messages" {:total (count posts)})
    (doseq [post posts]
      (core/report-exc "Error processing cron job"
        (-process-scheduled post)))))


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
