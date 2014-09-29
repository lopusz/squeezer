(defproject squeezer "0.1.0-SNAPSHOT"

  ; GENERAL OPTIONS

  :description "description"
  :url "url"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot :all
  :omit-source true

  :main squeezer.core
  ;; Options used by Java
  ;;; run with assertions enabled
  :jvm-opts ["-ea"]
 
  ; DEPENDENCIES

  :dependencies [
    [org.clojure/clojure "1.6.0"]
    [org.clojure/test.check "0.5.9"]

    ;; Runtime assertions
    [pjstadig/assertions "0.1.0"]
    [prismatic/schema "0.3.0"]
    [org.apache.commons/commons-compress "1.8.1"] 
    [org.tukaani/xz "1.5"]]
  
  ; PLUGINS + CONFIGURATION

  :plugins [[codox "0.8.10"] 
            [lein-ancient "0.5.5"]]

  ;; codox configuration

  :codox {
          :output-dir "target/apidoc"
          :sources [ "src/"]
          :defaults {:doc/format :markdown}
          ;; Uncomment this to get github links in sources
          ;; :src-dir-uri "githubrepo/blob/master/"
          ;; :src-linenum-anchor-prefix "L"
          })
