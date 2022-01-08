(ns xapi.cron
  (:require [sentry-clj.core :as sentry]

            [xapi.core.db :as db]
            [xapi.log :as log]
            [xapi.ghost :as ghost]
            [mount.core :as mount]))


(defn scheduled-q []
  {:from   [:posts]
   :select @ghost/DBPOST-KEYS
   :where  [:and
            [:= :status "scheduled"]
            [:<= :published_at :%now]]})


(defn process-scheduled [dbpost]
  (db/one {:update :posts
           :set    {:status "published"}
           :where  [:= :uuid (:uuid dbpost)]})
  (let [post (ghost/dbres->post dbpost)]
    (ghost/send-webhooks! (assoc post :status "published"))))


(defn -process [*stop]
  (loop []
    (when-let [posts (try (seq (db/q (scheduled-q)))
                          (catch Exception e
                            (sentry/send-event {:message   "Error querying DB"
                                                :throwable e})))]
      (log/info "Found %s scheduled messages" (count posts))
      (doseq [post posts]
        (try
          (process-scheduled post)
          (catch Exception e
            (sentry/send-event {:message   "error in cron"
                                :throwable e})))))
    (Thread/sleep 1000)
    (when-not @*stop
      (recur))))


(defn start-cron []
  (let [*stop (atom false)
        stop-fn #(reset! *stop true)]
    (.start (Thread. #(-process *stop)))
    {:stop stop-fn}))


(mount/defstate cron
  :start (when db/conn
           (start-cron))
  :stop ((:stop cron)))
