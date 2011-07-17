(ns cheshire.custom
  "Methods used for extending JSON generation to different Java classes.
  Has the same public API as core.clj so they can be swapped in and out."
  (:use [cheshire.factory])
  (:require [cheshire.core :as core])
  (:import (java.io BufferedWriter ByteArrayOutputStream StringWriter)
           (java.util Date SimpleTimeZone)
           (java.text SimpleDateFormat)
           (org.codehaus.jackson.smile SmileFactory)
           (org.codehaus.jackson JsonFactory JsonGenerator JsonParser)))

;;(set! *warn-on-reflection* true)

;; default date format used to JSON-encode Date objects
(def ^{:dynamic true} *date-format* "yyyy-MM-dd'T'HH:mm:ss'Z'")

(defprotocol Jable
  (to-json [t jg]))

(defn ^String encode [obj & [^String date-format]]
  (binding [*date-format* (or date-format *date-format*)]
    (let [sw (StringWriter.)
          generator (.createJsonGenerator ^JsonFactory factory sw)]
      (if obj
        (to-json obj generator)
        (.writeNull generator))
      (.flush generator)
      (.toString sw))))

(defn ^String encode-stream [obj ^BufferedWriter w & [^String date-format]]
  (binding [*date-format* (or date-format *date-format*)]
    (let [generator (.createJsonGenerator factory w)]
      (to-json obj generator)
      (.flush generator)
      w)))

(defn encode-smile
  [obj & [^String date-format]]
  (binding [*date-format* (or date-format *date-format*)]
    (let [baos (ByteArrayOutputStream.)
          generator (.createJsonGenerator smile-factory baos)]
      (to-json obj generator)
      (.flush generator)
      (.toByteArray baos))))

;; there are no differences in parsing, but these are here to make
;; this a self-contained namespace if desired
(def parse core/decode)
(def parse-string core/decode)
(def parse-stream core/decode-stream)
(def parse-smile core/decode-smile)
(def parsed-seq core/parsed-seq)
(def decode core/parse-string)
(def decode-stream parse-stream)
(def decode-smile parse-smile)

;; aliases
(def generate-string encode)
(def generate-stream encode-stream)
(def generate-smile encode-smile)

;; generic encoders
(defn encode-nil [_ ^JsonGenerator jg]
  (.writeNull jg))

(defn encode-str [^String s ^JsonGenerator jg]
  (.writeString jg (str s)))

(defn encode-number [^java.lang.Number n ^JsonGenerator jg]
  (.writeNumber jg n))

(defn encode-seq [s ^JsonGenerator jg]
  (.writeStartArray jg)
  (doseq [i s]
    (to-json i jg))
  (.writeEndArray jg))

(defn encode-date [^Date d ^JsonGenerator jg]
  (let [sdf (SimpleDateFormat. *date-format*)]
    (.setTimeZone sdf (SimpleTimeZone. 0 "UTC"))
    (.writeString jg (.format sdf d))))

(defn encode-bool [^Boolean b ^JsonGenerator jg]
  (.writeBoolean jg b))

(defn encode-named [^clojure.lang.Keyword k ^JsonGenerator jg]
  (.writeString jg (name k)))

(defn encode-map [^clojure.lang.IPersistentMap m ^JsonGenerator jg]
  (.writeStartObject jg)
  (doseq [[k v] m]
    (.writeFieldName jg (if (instance? clojure.lang.Keyword k)
                          (name k)
                          (str k)))
    (to-json v jg))
  (.writeEndObject jg))

(defn encode-symbol [^clojure.lang.Symbol s ^JsonGenerator jg]
  (.writeString jg (str (:ns (meta (resolve s)))
                        "/"
                        (:name (meta (resolve s))))))

;; extended implementations
(extend nil
  Jable
  {:to-json encode-nil})

(extend java.lang.String
  Jable
  {:to-json encode-str})

(extend java.lang.Number
  Jable
  {:to-json encode-number})

(extend clojure.lang.ISeq
  Jable
  {:to-json encode-seq})

(extend clojure.lang.IPersistentVector
  Jable
  {:to-json encode-seq})

(extend clojure.lang.IPersistentSet
  Jable
  {:to-json encode-seq})

(extend java.util.Date
  Jable
  {:to-json encode-date})

(extend java.util.UUID
  Jable
  {:to-json encode-str})

(extend java.lang.Boolean
  Jable
  {:to-json encode-bool})

(extend clojure.lang.Keyword
  Jable
  {:to-json encode-named})

(extend clojure.lang.IPersistentMap
  Jable
  {:to-json encode-map})

(extend clojure.lang.Symbol
  Jable
  {:to-json encode-symbol})


(defn add-encoder
  "Provide an encoder for a type not handled by Cheshire.

   ex. (add-encoder java.net.URL encode-string)

   See encode-str, encode-map, etc, in the cheshire.custom
   namespace for encoder examples."
  [cls encoder]
  (extend cls
    Jable
    {:to-json encoder}))

(defn remove-encoder [cls]
  "Remove encoder for a given type.

   ex. (remove-encoder java.net.URL)"
  (alter-var-root
   #'Jable
   #(assoc % :impls (dissoc (:impls %) cls)))
  (clojure.core/-reset-methods Jable))
