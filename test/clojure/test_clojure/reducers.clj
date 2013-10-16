;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

;; Author: Tassilo Horn

(ns clojure.test-clojure.reducers
  (:require [clojure.core.reducers :as r])
  (:use clojure.test))

(defmacro defequivtest
  ;; f is the core fn, r is the reducers equivalent, rt is the reducible ->
  ;; coll transformer
  [name [f r rt] fns]
  `(deftest ~name
     (let [c# (range -100 1000)]
       (doseq [fn# ~fns]
         (is (= (~f fn# c#)
                (~rt (~r fn# c#))))))))

(defequivtest test-map
  [map r/map #(into [] %)]
  [inc dec #(Math/sqrt (Math/abs %))])

(defequivtest test-mapcat
  [mapcat r/mapcat #(into [] %)]
  [(fn [x] [x])
   (fn [x] [x (inc x)])
   (fn [x] [x (inc x) x])])

(deftest test-mapcat-obeys-reduced
  (is (= [1 "0" 2 "1" 3]
        (->> (concat (range 100) (lazy-seq (throw (Exception. "Too eager"))))
          (r/mapcat (juxt inc str))
          (r/take 5)
          (into [])))))

(defequivtest test-reduce
  [reduce r/reduce identity]
  [+' *'])

(defequivtest test-filter
  [filter r/filter #(into [] %)]
  [even? odd? #(< 200 %) identity])


(deftest test-sorted-maps
  (let [m (into (sorted-map)
                '{1 a, 2 b, 3 c, 4 d})]
    (is (= "1a2b3c4d" (reduce-kv str "" m))
        "Sorted maps should reduce-kv in sorted order")
    (is (= 1 (reduce-kv (fn [acc k v]
                          (reduced (+ acc k)))
                        0 m))
        "Sorted maps should stop reduction when asked")))

(deftest test-nil
  (is (= {:k :v} (reduce-kv assoc {:k :v} nil)))
  (is (= 0 (r/fold + nil))))

(def simple-map {:a 1 :b 2 :c 3 :d 4 :e 5 :f 6 :g 7 :h 8 :i 9 :j 10
                 :k 11 :l 12 :m 13 :n 14 :o 15 :p 16 :q 17 :r 18
                 :s 19 :t 20 :u 21 :v 22 :w 23 :x 24 :y 25 :z 26})

(declare k-fail)

(defn reducef
  ([])
  ([ret [k v]] (reducef ret k v))
  ([ret k v] (when (= k k-fail)
               (throw (IndexOutOfBoundsException.)))))

;; This test isn't very interesting. It demonstrates that exceptions
;; thrown from within a reduce propagate up out of the reduce.
(deftest test-reduce-exception
  (doseq [k (keys simple-map)]
    (def k-fail k)
    (is (thrown? IndexOutOfBoundsException
                 (reduce reducef nil simple-map)))))

;; Using a checked exception, such as IllegalAccessException, causes
;; test-fold-exception to fail. The checked exception get wrapped in
;; a few levels of RuntimeException. Note that test-reduce-exception
;; does not see this behavior; the base exception is not wrapped.
(deftest test-fold-exception
  (doseq [k (keys simple-map)]
    (def k-fail k)
    (is (thrown? IndexOutOfBoundsException
                 (r/fold reducef simple-map)))))
