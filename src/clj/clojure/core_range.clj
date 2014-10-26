(in-ns 'clojure.core)

(deftype RangeIterator_L [^long ^:unsynchronized-mutable i
                          ^long end
                          ^long step]
  java.util.Iterator
  (hasNext [_]
    (if (pos? step)
      (< i end)
      (> i end)))
  (next [_]
    (let [ret i]
      (set! i (+ i step))
      ret)))

(declare range-seq*)

(deftype Range_L [^long start
                  ^long end
                  ^long step
                  _meta
                  ^int ^:unsynchronized-mutable ^:transient _hash
                  ^int ^:unsynchronized-mutable ^:transient _hasheq]
  clojure.lang.IPersistentCollection
  (cons [this o]
    (clojure.lang.Cons. o this))
  (empty [_]
    (with-meta () _meta))
  (equiv [this o]
    (.equals this o))
  clojure.lang.Seqable
  (seq [_]
    (seq (range-seq* start end step)))
  clojure.lang.ISeq
  (first [this]
    (clojure.core/first (seq this)))
  (more [this]
    (clojure.core/rest (seq this)))
  (next [this]
    (clojure.core/next (seq this)))
  java.util.List
  (add [_ o]
    (throw (UnsupportedOperationException.)))
  (add [_ i o]
    (throw (UnsupportedOperationException.)))
  (addAll [_ c]
    (throw (UnsupportedOperationException.)))
  (addAll [_ i c]
    (throw (UnsupportedOperationException.)))
  (clear [_]
    (throw (UnsupportedOperationException.)))
  (contains [this o]
    (let [iter (.iterator this)]
      (loop []
        (if (.hasNext iter)
          (if (clojure.lang.Util/equiv (.next iter) o)
            true
            (recur))
          false))))
  (containsAll [this c]
    (let [iter (.iterator c)]
      (loop []
        (if (.hasNext iter)
          (if (.contains this (.next iter))
            (recur)
            false)
          true))))
  (equals [this obj]
    (if (or (instance? clojure.lang.Sequential obj)
            (instance? java.util.List obj))
      (= (seq this) (seq obj))
      false))

  (get [this i]
    (.nth this i))
  (indexOf [_ o]) ;; TODO
  (lastIndexOf [_ o]) ;; TODO
  (isEmpty [this]
    (boolean (seq this)))

  (listIterator [this idx]
    (.listIterator (java.util.Collections/unmodifiableList this) idx))
  (listIterator [this]
    (.listIterator (java.util.Collections/unmodifiableList this)))
  (remove [_ ^int i]
    (throw (UnsupportedOperationException.)))
  (^boolean remove [_ o]
    (throw (UnsupportedOperationException.)))
  (removeAll [_ c]
    (throw (UnsupportedOperationException.)))
  (retainAll [_ c]
    (throw (UnsupportedOperationException.)))
  (set [_ i o]
    (throw (UnsupportedOperationException.)))
  (size [this] (count this))
  (subList [this from to]
    (.subList (java.util.Collections/unmodifiableList this) from to))
  (toArray [this]
    (clojure.lang.RT/seqToArray this)) ;; seqToArray counts length inefficiently
  (toArray [this arr]
    (clojure.lang.RT/seqToPassedArray this arr))

  Iterable
  (iterator [_]
    (RangeIterator_L. start end step))
  clojure.lang.IHashEq
  (hasheq [this]
    (when (= _hasheq -1)
      (set! _hasheq (clojure.lang.Murmur3/hashOrdered this)))
    _hasheq)
  clojure.lang.Counted
  (count [this]
    (if-not (seq this)
      0
      (Math/ceil (/ (- end start) step))))
  clojure.lang.Indexed
  (nth [this i]
    (if (< i (count this))
      (+ start (* step i))
      (if (and (zero? step)
               (> start end))
        start
        (throw (IndexOutOfBoundsException.)))))
  (nth [this i not-found]
    (if (< i (count this))
      (+ start (* step i))
      (if (and (zero? step)
               (> start end))
        start
        not-found)))
  clojure.core.protocols/CollReduce
  (coll-reduce [this f]
    (.reduce this f))
  (coll-reduce [this f init]
    (.reduce this f init))
  clojure.lang.IReduce
  (reduce [this f]
    (if (pos? step)  ;; handle zero step?
      (loop [acc (Long/valueOf start) i (+ start step)]
        (if (< i end)
          (let [ret (f acc i)]
            (if (reduced? ret)
              @ret
              (recur ret (+ i step))))
          acc))
      (loop [acc (Long/valueOf start) i (+ start step)]
        (if (> i end)
          (let [ret (f acc i)]
            (if (reduced? ret)
              @ret
              (recur ret (+ i step))))
          acc))))
  clojure.lang.IReduceInit
  (reduce [_ f init]
    (if (pos? step)
      (loop [acc init i start]
        (if (< i end)
          (let [ret (f acc i)]
            (if (reduced? ret)
              @ret
              (recur ret (+ i step))))
          acc))
      (loop [acc init i start]
        (if (> i end)
          (let [ret (f acc i)]
            (if (reduced? ret)
              @ret
              (recur ret (+ i step))))
          acc))))
  clojure.lang.IObj
  (withMeta [this m]
    (if (identical? _meta m)
      this
      (Range_L. start end step m _hash _hasheq)))
  clojure.lang.IMeta
  (meta [_] _meta)
  Object
  (hashCode [this]
    (when (== _hash -1)
      (let [iter (.iterator this)
            hc (loop [hc 1]
                 (if (.hasNext iter)
                   (recur (unchecked-add-int (unchecked-multiply-int 31 hc)
                                             (.hashCode (.next iter))))
                   hc))]
        (set! _hash (int hc))))  ;; TODO why must cast?
    _hash)
  java.io.Serializable
  clojure.lang.Sequential)

(defn ^:private range-seq*
  [start end step]
  (lazy-seq
   (let [b (chunk-buffer 32)
         comp (cond (or (zero? step) (= start end)) not=
                    (pos? step) <
                    (neg? step) >)]
     (loop [i start]
       (if (and (< (count b) 32)
                (comp i end))
         (do
           (chunk-append b i)
           (recur (+ i step)))
         (chunk-cons (chunk b)
                     (when (comp i end)
                       (range-seq* i end step))))))))

(defn range
  "Returns a lazy seq of nums from start (inclusive) to end
  (exclusive), by step, where start defaults to 0, step to 1, and end to
  infinity. When step is equal to 0, returns an infinite sequence of
  start. When start is equal to end, returns empty list."
  {:added "1.0"
   "static" true}
  ([] (range 0 Long/MAX_VALUE 1))
  ([end] (range 0 end 1))
  ([start end] (range start end 1))
  ([start end step]
     (if (and (instance? Long start)
              (instance? Long end)
              (instance? Long step))
       (Range_L. start end step nil -1 -1)
       (range-seq* start end step))))
