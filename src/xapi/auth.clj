(ns xapi.auth
  (:require [clojure.edn :as edn]
            [ring.util.codec :as codec]
            [oauth.two :as oauth]
            [org.httpkit.client :as http]
            [cheshire.core :as json]

            [xapi.config :as config]
            [xapi.core.db :as db]
            [xapi.core :as core]
            [xapi.auth :as auth]))


(set! *warn-on-reflection* true)


;;; Utils

(defn map->b64 [state]
  (when-let [enc (when (seq state)
                   (pr-str state))]
    (codec/base64-encode
      (.getBytes ^String enc "UTF-8"))))


(defn b64->map [^String s]
  (when s
    (-> ^bytes (codec/base64-decode s)
        (String. "UTF-8")
        edn/read-string)))


;;; Authentication


(defn user-q [uid]
  {:from   [:users]
   :select [:id
            :github
            :email
            :name
            :apikey
            :repo
            :access_token]
   :where  [:= :id uid]})


(def ^:dynamic *uid* nil)
(def ^:dynamic user nil)


(defn wrap-auth [handler]
  (fn [req]
    (let [sess-uid  (-> req :session :user_id)
          ghost-uid (some-> req :cookies (get "ghost-admin-api-session") :value
                      b64->map :user_id)
          uid       (or sess-uid ghost-uid)
          user-fn   (fn [] (when uid (db/one (user-q uid))))
          ;; delay makes only single call to user-fn
          -delay    (delay (user-fn))]
      (binding [*uid* uid
                user  #(deref -delay)]
        (handler req)))))


(defn uid []
  *uid*)


(defn by-email [email password]
  (let [q (-> (user-q nil)
              (assoc :where [:and
                             [:= :email email]
                             [:= :apikey password]]))]
    (db/one q)))


;;; OAuth

(defn get-base-url []
  (str "https://" (config/DOMAIN)))


(def gh-client
  (memoize
    (fn []
      (oauth/make-client
        {:authorize-uri "https://github.com/login/oauth/authorize"
         :access-uri    "https://github.com/login/oauth/access_token"
         :redirect-uri  (str (get-base-url) "/oauth/github")
         :scope         ["repo"]
         :id            (config/GHID)
         :secret        (config/GHSECRET)}))))


(defn get-access-token! [client code]
  (-> @(http/request (oauth/access-token-request client {:code code}))
      :body
      slurp
      codec/form-decode
      (get "access_token")))


;;; Github

(defn gh!
  ([url access-token] (gh! url access-token nil))
  ([url access-token opts]
   (-> @(http/request
          (-> opts
              (assoc :url (str "https://api.github.com" url))
              (update :headers merge
                {"Accept"        "application/vnd.github.v3+json"
                 "Authorization" (str "token " access-token)})))
       :body
       (json/parse-string keyword))))


;;; Logic

(defn store-user-q [info state]
  {:insert-into   :users
   :values        [{:github       (:login info)
                    :name         (:name info)
                    :email        (:email info)
                    :access_token (:access_token state)
                    :updated_at   (db/call :now)
                    :apikey       (core/uuid)}]
   :returning     [:id :github :access_token]
   :on-conflict   [:github]
   :do-update-set [:access_token :updated_at]})


(defn auth-user! [code state]
  (when-let [access-token (get-access-token! (gh-client) code)]
    (let [info (gh! "/user" access-token)]
      (db/one (store-user-q info (assoc state :access_token access-token))))))


;;; HTTP

(defn start [_req]
  {:status  302
   :headers {"Location" (oauth/authorization-url (gh-client)
                          {:state (map->b64 {:nihera "ne nada"})})}})


(defn github-cb [{:keys [params]}]
  (let [code  (:code params)
        state (b64->map (:state params))
        user  (auth-user! code state)]

    (cond
      (nil? code)
      {:status 400
       :body   "No 'code' parameter in query string"}

      (nil? user)
      {:status 400
       :body   "Cannot authenticate you on Github using supplied 'code'"}

      :else
      {:status  302
       :session {:user_id (:id user)}
       :headers {"Location" "/"}
       :body    ""})))


(defn settings [{:keys [form-params request-method] :as req}]
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
