(ns xapi.core-test
  (:require [xapi.core :as core]
            [clojure.test :as t]))


(t/deftest trans-form-test
  (t/is (= {"webhook" [{"type" "github", "url" "solovyov.net"}
                       {"type" "url", "url" "https://example.com", "enabled" "on"}]}
           (core/trans-form
             {"webhook.0.type"    "github"
              "webhook.0.url"     "solovyov.net"
              "webhook.1.type"    "url"
              "webhook.1.url"     "https://example.com"
              "webhook.1.enabled" "on"}))))
