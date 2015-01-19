(in-ns 'clojure.core)

(deftype GenericRangeIterator [^:unsynchronized-mutable i step cmp]
  java.util.Iterator
  (hasNext [_]
    (cmp i))
  (next [this]
    (if (.hasNext this)
      (let [ret i]
        (set! i (+ i step))
        ret)
      (throw (java.util.NoSuchElementException.))))
  (remove [_]
    (throw (UnsupportedOperationException.))))

(deftype GenericRange [start end step]
  clojure.lang.Seqable
  (seq [_]
    (seq (range* start end step)))
  clojure.lang.Counted
  (count [_]
    (int (Math/ceil (/ (- end start) step))))
  clojure.lang.IReduce
  (reduce [_ rf]
    (let [cmp (if (pos? step) #(< % end) #(> % end))]
      (loop [acc start i (+ start step)]
        (if (cmp i)
          (let [ret (rf acc i)]
            (if (reduced? ret)
              @ret
              (recur ret (+ i step))))
          acc))))
  clojure.lang.IReduceInit
  (reduce [_ rf init]
    (let [cmp (if (pos? step) #(< % end) #(> % end))]
      (loop [acc init i start]
        (if (cmp i)
          (let [ret (rf acc i)]
            (if (reduced? ret)
              @ret
              (recur ret (+ i step))))
          acc))))
  java.lang.Iterable
  (iterator [_]
    (let [cmp (if (pos? step) #(< % end) #(> % end))]
      (GenericRangeIterator. start step cmp)))
  clojure.lang.Sequential
  java.io.Serializable)

(deftype LongRangeIterator [^:unsynchronized-mutable ^long i ^long step ^clojure.lang.IFn$LO cmp]
  java.util.Iterator
  (hasNext [_]
    (.invokePrim cmp i))
  (next [this]
    (if (.hasNext this)
      (let [ret i]
        (set! i (+ i step))
        ret)
      (throw (java.util.NoSuchElementException.))))
  (remove [_]
    (throw (UnsupportedOperationException.))))

(deftype LongRange [^long start ^long end ^long step]
  clojure.lang.Seqable
  (seq [_]
    (seq (range* start end step)))
  clojure.lang.Counted
  (count [_]
    (int (Math/ceil (/ (- end start) (float step)))))
  clojure.lang.IReduce
  (reduce [_ rf]
    (let [^clojure.lang.IFn$LO cmp (if (pos? step)
                                     (fn [^long n]
                                       (< n end))
                                     (fn [^long n]
                                       (> n end)))]
      (loop [acc (Long/valueOf start) i (+ start step)]
        (if (.invokePrim cmp i)
          (let [ret (rf acc i)]
            (if (reduced? ret)
              @ret
              (recur ret (+ i step))))
          acc))))
  clojure.lang.IReduceInit
  (reduce [_ rf init]
    (let [^clojure.lang.IFn$LO cmp (if (pos? step)
                                     (fn [^long n]
                                       (< n end))
                                     (fn [^long n]
                                       (> n end)))]
      (loop [acc init i start]
        (if (.invokePrim cmp i)
          (let [ret (rf acc i)]
            (if (reduced? ret)
              @ret
              (recur ret (+ i step))))
          acc))))
  java.lang.Iterable
  (iterator [_]
    (let [cmp (if (pos? step)
                (fn [^long n]
                  (< n end))
                (fn [^long n]
                  (> n end)))]
      (LongRangeIterator. start step cmp)))
  clojure.lang.Sequential
  java.io.Serializable)

(defn- long-range
  ([^long end]
   (if (pos? end)
     (LongRange. 0 end 1)
     ()))
  ([^long start ^long end]
   (if (< start end)
     (LongRange. start end 1)
     ()))
  ([^long start ^long end ^long step]
   (cond
     (pos? step)
     (if (< start end)
       (LongRange. start end step)
       ())
     (neg? step)
     (if (> start end)
       (LongRange. start end step)
       ())
     (= start end)
     ()
     :else
     (repeat start))))

(defn- generic-range
  ([] (iterate inc' 0))
  ([end]
   (if (pos? end)
     (GenericRange. 0 end 1)
     ()))
  ([start end]
   (if (< start end)
     (GenericRange. start end 1)
     ()))
  ([start end step]
   (cond
     (pos? step)
     (if (< start end)
       (GenericRange. start end step)
       ())
     (neg? step)
     (if (> start end)
       (GenericRange. start end step)
       ())
     (= start end)
     ()
     :else
     (repeat start))))

(defmethod print-method LongRange
  [rng ^Writer w]
  (print-method (seq rng) w))

(defmethod print-method GenericRange
  [rng ^Writer w]
  (print-method (seq rng) w))

(ns-unmap 'clojure.core '->GenericRangeIterator)
(ns-unmap 'clojure.core '->LongRangeIterator)
(ns-unmap 'clojure.core '->LongRange)
(ns-unmap 'clojure.core '->GenericRange)
