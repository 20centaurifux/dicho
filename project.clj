(defproject de.dixieflatline/dicho "0.1.0-SNAPSHOT"
  :description "Library for creating standardized success and error responses."
  :url "https://github.com/20centaurifux/dicho"
  :license {:name "AGPLv3.0"
            :url "https://www.gnu.org/licenses/agpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.11.1"]
                  [failjure "2.3.0"]]
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})