(ns xapi.ui.settings
  (:require [malli.core :as m]
            [malli.transform :as mt]
            [malli.error :as me]

            [xapi.auth :as auth]
            [xapi.core :as core]
            [xapi.core.db :as db]
            [xapi.config :as config]
            [xapi.ui.base :as base]))


(defn webhook-row [prefix hook]
  (let [gh? (= (:type hook) "github")]
    [:fieldset.row
     [:input {:name (str prefix ".id") :type "hidden" :value (:id hook)}]
     [:legend "Webhook"]
     [:label.col-1
      "Type"
      [:select {:name (str prefix ".type")}
       [:option {:value    "url"
                 :selected (not gh?)} "URL"]
       [:option {:value    "github"
                 :selected gh?} "Github"]]]
     [:label.col
      (if gh? "Repo" "URL")
      [:input {:name  (str prefix ".url")
               :type  "text"
               :value (:url hook)}]]
     [:div.col-1
      [:a {:href "#" :ts-action "prevent, remove 'parent fieldset'"} "❌"]
      [:br]
      [:input {:name    (str prefix ".enabled")
               :type    "checkbox"
               :value   "true"
               :checked (:enabled hook true)}]]]))


(defn index [_req]
  (let [user  (auth/user)
        hooks (db/q {:from     [:webhook]
                     :select   [:id :enabled :type :url :headers]
                     :where    [:= :user_id (auth/uid)]
                     :order-by [:id]})]
    {:status  200
     :headers {"Content-Type" "text/html"}
     :body
     (if user
       (base/wrap
         [:p
          "You can now post stuff. Connect using those settings:"]
         [:table
          [:tr [:td "Host"] [:td [:code (config/DOMAIN)]]]
          [:tr [:td "Email"] [:td [:code (:email user)]]]
          [:tr [:td "Password"] [:td [:code (:apikey user)]]]]

         [:article
          [:header
           [:h4 "Settings"]]

          [:form {:method "post" :action "/settings"}
           [:label
            [:input {:type "checkbox" :name "apikey-new"}]
            "Regenerate password"]

           [:div.webhooks
            (for [[i hook] (map-indexed vector hooks)]
              (webhook-row (str "webhook." i) hook))]

           [:button {:name "webhook-add"} "Add webhook"]

           [:hr]

           [:button {:name "save"} "Save"]]])
       (base/wrap
         [:a {:href "/oauth"} "Start login"]))}))


(def trans
  (mt/transformer
    (mt/key-transformer {:decode keyword})
    (mt/string-transformer)
    (mt/default-value-transformer)
    {:name     :form-decoders
     :decoders {'int? (fn [x]
                        (cond
                          (not (string? x)) x
                          (not (seq x))     nil
                          :else             (try
                                              (Long/parseLong x)
                                              (catch Exception _ x))))}}))


(def Settings
  (m/schema
    [:map
     [:apikey-new {:optional true} boolean?]
     [:webhook [:vector
                [:map
                 [:id [:maybe int?]]
                 [:type :string]
                 [:url :string]
                 [:enabled {:default false} boolean?]]]]]))


(defn form [{:keys [form-params request-method]}]
  (if (not= request-method :post)
    {:status 405
     :body   "Method Not Allowed"}
    (let [data (m/decode Settings (core/trans-form form-params) trans)]
      (if (m/validate Settings data)
        (do
          ;; NOTE: I feel some serious sorrow looking at this
          (let [webhooks  (db/q {:from   [:webhook]
                                 :where  [:= :user_id (auth/uid)]
                                 :select [:id]})
                known     (into #{} (map :id (:webhook data)))
                to-remove (remove known (map :id webhooks))]
            (db/q {:delete-from :webhook
                   :where       [:in :id to-remove]})
            (doseq [hook (:webhook data)]
              (if (:id hook)
                (db/one {:update :webhook
                         :set    hook
                         :where  [:= :id (:id hook)]})
                (db/one {:insert-into :webhook
                         :values      [(-> hook
                                           (dissoc :id)
                                           (assoc :user_id (auth/uid)))]}))))

          (when (:apikey-new data)
            (db/one {:update :users
                     :where  [:= :id (auth/uid)]
                     :set    {:apikey (core/uuid)}}))

          {:status  302
           :headers {"Location" "/"}
           :body    ""})

        {:status  400
         :headers {"Content-Type" "text/plain"}
         :body    (-> Settings
                      (m/explain data)
                      (me/humanize))}))))
