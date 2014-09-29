(ns squeezer.core-check
  (:require [squeezer.core :refer :all]
            [clojure.test.check.clojure-test :refer (defspec)]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.java.io :as io])
  (:import [java.io File]))

(defn make-round-trip-prop [compr]
  (prop/for-all
     [
       doc (gen/fmap #(apply str %) (gen/vector gen/string-ascii 1000))
     ]
       (let [
              test-fname "round_trip.test"
              _
                (spit
                  (writer test-fname :compr compr)
                  doc)
              doc*
                (slurp
                  (reader test-fname :compr compr))
              _ (io/delete-file test-fname true)
            ]
         (= doc doc*))))

(defspec round-trip-prop-no-comp-check 100 (make-round-trip-prop false))

(defspec round-trip-prop-gzip-check 100 (make-round-trip-prop "gzip"))

(defspec round-trip-prop-bzip2-check 100 (make-round-trip-prop "bzip2"))

(defspec round-trip-prop-xz-check 100 (make-round-trip-prop "xz"))

(defn make-compress-prop [compr]
  (prop/for-all
     [
       doc (gen/fmap #(apply str %)
               (gen/vector
                 gen/char-ascii
                 1000))
     ]
       (let [
              test-fname-plain "compress_plain.test"
              test-fname-compressed "compress_compressed.test"
              _
                (spit (writer test-fname-plain :compr false) doc)
              _ (spit
                  (writer test-fname-compressed :compr compr)
                  doc)
              size-plain (.length (File. test-fname-plain))
              size-compressed (.length (File. test-fname-compressed))
              _
                 (do
                   (io/delete-file test-fname-plain true)
                   (io/delete-file test-fname-compressed true))
            ]
         (< size-compressed size-plain))))

(defspec compress-prop-gzip-check 50 (make-compress-prop "gzip"))

(defspec compress-prop-bzip2-check 50 (make-compress-prop "bzip2"))

(defspec compress-prop-xz-check 50 (make-compress-prop "xz"))