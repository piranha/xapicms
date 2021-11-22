(ns xapi.ui.settings
  (:require [malli.core :as m]

            [xapi.auth :as auth]
            [xapi.core :as core]
            [xapi.core.db :as db]
            [xapi.config :as config]
            [xapi.ui.base :as base]))


(defn webhook-row [hook]
  (let [gh? (= (:type hook) "github")]
    [:fieldset.row
     [:legend "Webhook"]
     [:label.col-1
      "Type"
      [:select {:name "type"}
       [:option {:value    "url"
                 :selected (not gh?)} "Generic URL"]
       [:option {:value    "github"
                 :selected gh?} "Github"]]]
     [:label.col
      (if gh? "Repo" "URL")
      [:input {:name  "url"
               :type  "text"
               :value (:url hook)}]]
     [:div.col-1
      [:a {:href "#" :ts-action "prevent, remove 'parent fieldset'"} "❌"]
      [:br]
      [:input {:name    "enabled"
               :type    "checkbox"
               :checked (:enabled hook true)}]]]))


(defn index [_req]
  (let [user  (auth/user)
        hooks (db/q {:from   [:webhook]
                     :select [:id :enabled :type :url :headers]
                     :where  [:= :user_id (auth/uid)]})]
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
            (for [hook hooks]
              (webhook-row hook))]

           [:script#webhook-row-tpl {:type "text/template"}
            (webhook-row nil)]

           [:a {:ts-action "target .webhooks,
                            template append #webhook-row-tpl"}
            "Add webhook"]

           [:hr]

           [:button "Save"]]])
       (base/wrap
         [:a {:href "/oauth"} "Start login"]))}))


(def Settings
  [:map
   [:apikey-new boolean?]
   [:webhook [:vector
              [:map
               [:type :string]
               [:url :string]
               [:enabled boolean?]]]]])


(defn form [{:keys [form-params request-method]}]
  (if (not= request-method :post)
    {:status 405
     :body "Method Not Allowed"}
    (do
      (db/one {:update :users
               :where  [:= :id (auth/uid)]
               :set    (cond-> {:repo (get form-params "repo")}
                         (get form-params "apikey-new")
                         (assoc :apikey (core/uuid)))})
      {:status  302
       :headers {"Location" "/"}
       :body    ""})))
