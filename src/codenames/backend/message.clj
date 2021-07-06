(ns codenames.backend.message
  (:require [cognitect.transit :as transit]))

(defn decode
  [message]
  (let [stream (java.io.ByteArrayInputStream. (.getBytes message))
        reader (transit/reader stream :json)]
    (transit/read reader)))

(defn encode
  [message]
  (let [stream (java.io.ByteArrayOutputStream.)
        writer (transit/writer stream :json)]
    (transit/write writer message)
    (let [out (.toString stream)]
      (.reset stream)
      out)))
