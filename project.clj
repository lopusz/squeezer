(defproject squeezer "0.4.0"

  ; GENERAL OPTIONS

  :description "description"
  :url "url"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :aot :all
  :omit-source true

  ;; Options used by Java
  ;;; run with assertions enabled
  :jvm-opts ["-ea"]

  ; DEPENDENCIES

  :dependencies [
    [org.clojure/clojure "1.8.0"]
    [org.clojure/test.check "0.9.0"]

    ;; Runtime assertions
    [pjstadig/assertions "0.1.0"]
    [prismatic/schema "1.1.0"]
    [org.apache.commons/commons-compress "1.10"]
    [org.tukaani/xz "1.5"]]

  ; PLUGINS + CONFIGURATION

  :plugins [[codox "0.8.10"]]

  ;; codox configuration

  :codox {
          :output-dir "target/apidoc"
          :sources [ "src/"]
          :defaults {:doc/format :markdown}
          ;; Uncomment this to get github links in sources
          ;; :src-dir-uri "githubrepo/blob/master/"
          ;; :src-linenum-anchor-prefix "L"
          })
