
;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns
  ^{:author "Stuart Sierra, Chas Emerick, Stuart Halloway",
     :doc "This file defines polymorphic I/O utility functions for Clojure."}
    squeezer.core
    (:refer-clojure :exclude [assert])
    (:require [pjstadig.assertions :refer [assert]]
              [clojure.string :as str]
              [schema.core :as sm])
    (:import
     (java.io Reader InputStream InputStreamReader PushbackReader
              BufferedReader File OutputStream
              OutputStreamWriter BufferedWriter Writer
              FileInputStream FileOutputStream ByteArrayOutputStream
              StringReader ByteArrayInputStream
              BufferedInputStream BufferedOutputStream
              CharArrayReader Closeable)
     (java.net URI URL MalformedURLException Socket URLDecoder URLEncoder)
     (java.util.zip GZIPInputStream  GZIPOutputStream)
     (org.apache.commons.compress.compressors
         bzip2.BZip2CompressorInputStream
         bzip2.BZip2CompressorOutputStream
         xz.XZCompressorInputStream
         xz.XZCompressorOutputStream)))

(defmacro ^:private sassert [ schema form ]
  `(let [ res# (sm/check ~schema ~form) ]
     (assert (nil? res#) (pr-str res#))))

(defmacro ^:private report [ s form ]
  `(do (println ~s) ~form))

(def
    ^{:doc "Type object for a Java primitive byte array."
      :private true
      }
 byte-array-type (class (make-array Byte/TYPE 0)))

(def
    ^{:doc "Type object for a Java primitive char array."
      :private true}
 char-array-type (class (make-array Character/TYPE 0)))

(defprotocol Coercions
  "Coerce between various 'resource-namish' things."
  (^{:tag java.io.File } as-file [x] "Coerce argument to a file.")
  (^{:tag java.net.URL } as-url [x] "Coerce argument to a URL."))

(defn ^:private escaped-utf8-urlstring->str [s]
  (-> (clojure.string/replace s "+" (URLEncoder/encode "+" "UTF-8"))
      (URLDecoder/decode "UTF-8")))

(extend-protocol Coercions
  nil
  (as-file [_] nil)
  (as-url [_] nil)

  String
  (as-file [s] (File. s))
  (as-url [s] (URL. s))

  File
  (as-file [f] f)
  (as-url [f] (.toURL (.toURI f)))

  URL
  (as-url [u] u)
  (as-file [u]
    (if (= "file" (.getProtocol u))
      (as-file (escaped-utf8-urlstring->str
                (.replace (.getFile u) \/ File/separatorChar)))
      (throw (IllegalArgumentException. (str "Not a file: " u)))))

  URI
  (as-url [u] (.toURL u))
  (as-file [u] (as-file (as-url u))))

(defprotocol IORawFactory
  "Factory functions that create ready-to-use, buffered versions of
   the various Java I/O stream types, on top of anything that can
   be unequivocally converted to the requested kind of stream.

   Common options include

     :append    true to open stream in append mode
     :encoding  string name of encoding to use, e.g. \"UTF-8\".

   Callers should generally prefer the higher level API provided by
   reader, writer, input-stream, and output-stream."
  (make-raw-input-stream [x opts]
    "Creates a raw InputStream. See also IORawFactory docs.")
  (make-raw-output-stream [x opts]
    "Creates a raw OutputStream. See also IORawFactory docs."))

(def ^:private default-streams-impl
  { :make-raw-input-stream
      (fn [x opts]
        (throw (IllegalArgumentException.
                 (str "Cannot open <" (pr-str x) "> as an InputStream."))))
    :make-raw-output-stream
      (fn [x opts]
        (throw (IllegalArgumentException.
                 (str "Cannot open <" (pr-str x) "> as an OutputStream."))))})

(extend InputStream
  IORawFactory
  (assoc default-streams-impl
    :make-raw-input-stream
      (fn [x opts] x)))

(extend OutputStream
  IORawFactory
  (assoc default-streams-impl
    :make-raw-output-stream
      (fn [x opts] x)))

(extend File
  IORawFactory
  (assoc default-streams-impl
    :make-raw-input-stream
      (fn [^File x opts] (FileInputStream. x))
    :make-raw-output-stream
      (fn [^File x opts]
         (FileOutputStream. x (:append opts)))))

(extend URL
  IORawFactory
  (assoc default-streams-impl
    :make-raw-input-stream
      (fn [^URL x opts]
         (if (= "file" (.getProtocol x))
           (FileInputStream. (as-file x))
           (.openStream x)))
    :make-raw-output-stream
      (fn [^URL x opts]
         (if (= "file" (.getProtocol x))
           (make-raw-output-stream (as-file x) opts)
             (throw (IllegalArgumentException.
             (str "Can not write to non-file URL <" x ">")))))))

(extend URI
  IORawFactory
  (assoc default-streams-impl
    :make-raw-input-stream
      (fn [^URI x opts] (make-raw-input-stream (.toURL x) opts))
    :make-raw-output-stream
      (fn [^URI x opts] (make-raw-output-stream (.toURL x) opts))))

(extend String
  IORawFactory
  (assoc default-streams-impl
    :make-raw-input-stream
      (fn [^String x opts]
         (try
            (make-raw-input-stream (URL. x) opts)
            (catch MalformedURLException e
              (make-raw-input-stream (File. x) opts))))
    :make-raw-output-stream
      (fn [^String x opts]
         (try
            (make-raw-output-stream (URL. x) opts)
            (catch MalformedURLException err
              (make-raw-output-stream (File. x) opts))))))

(extend Socket
  IORawFactory
  (assoc default-streams-impl
    :make-raw-input-stream
      (fn [^Socket x opts] (.getInputStream x))
    :make-raw-output-stream
      (fn [^Socket x opts] (.getOutputStream x))))

(extend byte-array-type
  IORawFactory
  (assoc default-streams-impl
    :make-raw-input-stream
      (fn [x opts] (ByteArrayInputStream. x))))
;
; (extend char-array-type
;  IORawFactory
;   (assoc default-streams-impl
;     :make-reader
;      (fn [x opts] (CharArrayReader. x))))

(extend Object
  IORawFactory
  default-streams-impl)

(def ^:private BufferSchema
  (sm/either (sm/enum nil false true) sm/Int))

(def ^:private ComprSchema
  (sm/enum "gzip" "bzip2" "xz" "none" false nil))

(def ^:private InputStreamOptsSchema
  { (sm/optional-key :compr)
      ComprSchema
    (sm/optional-key :pre-compr-buffer)
      BufferSchema
    (sm/optional-key :post-compr-buffer)
      BufferSchema
    (sm/optional-key :gzip-buffer)
      BufferSchema})

(def ^:private OutputStreamOptsSchema
  (assoc InputStreamOptsSchema (sm/optional-key :append) sm/Bool))

(def ^:private ReaderOptsSchema
  (assoc InputStreamOptsSchema (sm/optional-key :encoding) sm/Str))

(def ^:private WriterOptsSchema
  (assoc ReaderOptsSchema (sm/optional-key :append) sm/Bool))

(defn ^:private make-buffered-output-stream [^OutputStream x b]
  (sassert BufferSchema b)
  (if b
    (if (= b true)
      (BufferedOutputStream. x)
      (BufferedOutputStream. x b))
    x))

(defn ^:private make-compressed-output-stream
  [^OutputStream is opts]
  (sassert WriterOptsSchema opts)
  (case (:compr opts)
    "gzip"
       (if-let [ bs (:gzip-buffer opts) ]
         (GZIPOutputStream. is bs)
         (GZIPOutputStream. is))
    "bzip2" (BZip2CompressorOutputStream. is) ; true is for enabling decompression of concatenated files
    "xz" (XZCompressorOutputStream. is)
    "none" is
    false is
    nil is
    (throw  (IllegalArgumentException.
              (str "Unknown compression scheme "
                    (:compr opts) " in")))))

(defn ^:private make-writer [^OutputStream x opts]
  (sassert WriterOptsSchema opts)
  (OutputStreamWriter. x (:encoding opts)))

(defn ^:private make-buffered-writer [^Writer x opts ]
  (sassert WriterOptsSchema opts)
  (if-let [ b (:post-compr-buffer opts) ]
    (if (= b true)
      (BufferedWriter. x)
      (BufferedWriter. x b))
    x))

(defn ^Writer writer
  "Attempts to coerce its argument into an open java.io.Writer.
   Default implementations always return a java.io.BufferedWriter.

   Default implementations are provided for Writer, BufferedWriter,
   OutputStream, File, URI, URL, Socket, and String.

   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.

   Should be used inside with-open to ensure the Writer is properly
   closed."
  [x & opts-vec]
  (if (instance? java.io.Writer x)
    x
    (let [
          opts
            (merge
              {:encoding "UTF-8" :pre-compr-buffer true :append false }
              (apply hash-map opts-vec))
          _ (sassert WriterOptsSchema opts)
          ]
      (-> x
        (make-raw-output-stream opts)
        (make-buffered-output-stream (:pre-compr-buff opts))
        (make-compressed-output-stream opts)
        (make-writer opts)
        (make-buffered-writer opts)))))

(defn ^:private get-ext [ ^String s ]
  (last (str/split s #"\.")))

(defn ^:private infer-compr-vec [ x ]
  (case (get-ext (.toString x))
    "gz" [ :compr "gzip" ]
    "bz2" [ :compr "bzip2"]
    "xz"  [ :compr "xz"]
     [ :compr "none"]))

(defn ^Reader writer-compr [ x & opts-vec ]
    (if (and
          (not (contains? (into #{} opts-vec) :compr))
          (contains?
             #{ java.net.URL java.net.URI java.io.File java.lang.String}
             (type x)))
      (apply writer x (concat opts-vec (infer-compr-vec x)))
      (apply writer x opts-vec)))

(defn spit-compr [f content & opts-vec]
  (spit (apply writer-compr f opts-vec) content))

(defn ^OutputStream output-stream
  "Attempts to coerce its argument into an open java.io.OutputStream.
   Default implementations always return a java.io.BufferedOutputStream.

   Default implementations are defined for OutputStream, File, URI, URL,
   Socket, and String arguments.

   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.

   Should be used inside with-open to ensure the OutputStream is
   properly closed."

  [x & opts-vec]
  (let [
         opts
           (merge
             {:pre-compr-buff false :post-compr-buff true :append false }
             (apply hash-map opts-vec))
         _   (sassert OutputStreamOptsSchema opts)
        ]
    (-> x
       (make-raw-output-stream opts)
       (make-buffered-output-stream (:pre-compr-buff opts))
       (make-compressed-output-stream opts)
       (make-buffered-output-stream (:post-compr-buff opts)))))

;; Good notes on buffered stream performance
;;
;; http://stackoverflow.com/questions/1082320/what-order-should-i-use-gzipoutputstream-and-bufferedoutputstream
;; http://java-performance.info/java-io-bufferedinputstream-and-java-util-zip-gzipinputstream/

(defn ^:private make-buffered-input-stream [ ^InputStream x b]
  (sassert BufferSchema b)
  (if b
    (if (= b true)
      (BufferedInputStream. x)
      (BufferedInputStream. x b))
    x))

(defn ^:private make-compressed-input-stream
  [ ^InputStream is opts]
  (sassert ReaderOptsSchema opts)
  (case (:compr opts)
    "gzip"
       (if-let [ bs (:gzip-buffer opts) ]
         (GZIPInputStream. is bs)
         (GZIPInputStream. is))
    "bzip2" (BZip2CompressorInputStream. is true) ; true is for enabling decompression of concatenated files
    "xz" (XZCompressorInputStream. is)
    "none" is
    false  is
    nil is
    (throw  (IllegalArgumentException.
              (str "Unknown compression scheme "
                    (:compr opts) " in")))))

(defn ^:private make-reader [ ^InputStream x opts]
  (sassert ReaderOptsSchema opts)
  (InputStreamReader. x (:encoding opts)))

(defn ^:private make-buffered-reader [ ^Reader x opts]
  (sassert ReaderOptsSchema opts)
  (if-let [ b (:post-compr-buffer opts) ]
    (if (= b true)
      (BufferedReader. x)
      (BufferedReader. x b))
    x))

(defn ^Reader reader
  "Attempts to coerce its argument into an open java.io.Reader.
   Default implementations always return a java.io.BufferedReader.

   Default implementations are provided for Reader, BufferedReader,
   InputStream, File, URI, URL, Socket, byte arrays, character arrays,
   and String.

   If argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.

   Should be used inside with-open to ensure the Reader is properly
   closed.

   Allowed options:
   :encoding
   :append
   :compr
   :pre-compr-buffer
   :gzip-buffer
   :post-compr-buffer"
  [x & opts-vec]
  (if (instance? java.io.Reader x)
    x
    (let [
          opts
            (merge
              {:encoding "UTF-8" :pre-compr-buffer true
               :post-compr-buffer false }
              (apply hash-map opts-vec))
          _ (sassert ReaderOptsSchema opts)
        ]
      (-> x
        (make-raw-input-stream opts)
        (make-buffered-input-stream (:pre-comp-buffer opts))
        (make-compressed-input-stream opts)
        (make-reader opts)
        (make-buffered-reader opts)))))

(defn ^Reader reader-compr [ x & opts-vec ]
    (if (and
          (not (contains? (into #{} opts-vec) :compr))
          (contains?
             #{ java.net.URL java.net.URI java.io.File java.lang.String}
             (type x)))
      (apply reader x (concat opts-vec (infer-compr-vec x)))
      (apply reader x opts-vec)))

(defn slurp-compr [x & opts-vec]
  (slurp (apply reader-compr x opts-vec)))

(defn ^InputStream input-stream
  "Attempts to coerce its argument into an open java.io.InputStream.
   Default implementations always return a java.io.BufferedInputStream.

   Default implementations are defined for OutputStream, File, URI, URL,
   Socket, byte array, and String arguments.

   If the argument is a String, it tries to resolve it first as a URI, then
   as a local file name.  URIs with a 'file' protocol are converted to
   local file names.

   Should be used inside with-open to ensure the InputStream is properly
   closed.

   Allowed options:

   + `:append`
   + `:compr`
   + `:pre-compr-buffer`
   + `:gzip-buffer`
   + `:post-compr-buffer`"
  [x & opts-vec]
  (let [
         opts
           (merge
             {:pre-compr-buff false :post-compr-buff true :append false }
             (apply hash-map opts-vec))
          _ (sassert InputStreamOptsSchema opts)
        ]
      (-> x
       (make-raw-input-stream opts)
       (make-buffered-input-stream (:pre-compr-buffer opts))
       (make-compressed-input-stream opts)
       (make-buffered-input-stream (:post-compr-buffer opts)))))
