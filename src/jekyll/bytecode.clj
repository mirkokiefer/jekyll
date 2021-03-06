(ns jekyll.bytecode
  (:import (java.nio ByteBuffer ByteOrder)
           (java.lang Byte)))


;; helper functions

(defn int2bytes
  "Returns length nbytes Byte array representation of i"
  [i nbytes]
  (.array (.putInt (.order (ByteBuffer/allocate nbytes)
                           ByteOrder/LITTLE_ENDIAN)
                   i)))

(defn bits
  "Returns sequence of s bits for an integer n, the bit order being from most
   to least significant. If the last bit is set, n is negative."
  [n s]
  (reverse (take s (map
                    (fn [i] (bit-and 0x01 i))
                    (iterate (fn [i] (bit-shift-right i 1))
                             n)))))

(defn bits2byte
  "Turns a sequence of zeros and ones to a Byte"
  [bit-seq]
  (let [xpos (read-string (apply str (concat "2r" (rest bit-seq))))
        signum (first bit-seq)]
    (byte (if (= signum 1)
            (- xpos 128)
            xpos))))

(defn join-byte-arrays [& byte-arrays]
  (loop [bb (ByteBuffer/allocate (apply + (map count byte-arrays)))
         i 0]
    (if (< i (count byte-arrays))
      (recur (.put bb (nth byte-arrays i))
             (inc i))
      (.array bb) )))

(defn get-bytes [barray]
  (map identity (barray)))

(defn get-bits [barray]
  (map #(bits % 8) barray))


;; byte array representations for integers

(defn i8fle
  "Returns length 1 byte array representation of i"
  [i]
  (byte-array [(byte i)]))

(defn i32fle
  "Returns length 4 byte array representation of i"
  [i]
  (int2bytes i 4))

(defn i64fle
  "Returns length 8 byte array representation of i"
  [i]
  (int2bytes i 8))

(defn i32vle
  "Returns variable length byte array representation of i"
  [i]
  (let [int-bits (get-bits (i32fle i))
        degen-bytes (drop-while
                     (partial every? zero?)
                     (reverse
                      (partition 7 7 [0 0 0 0 0 0 0]
                                 (reverse (flatten (reverse int-bits))))))
        first-bytes (map #(bits2byte (reverse (conj % 1)))
                         (butlast degen-bytes))
        last-byte (bits2byte (reverse (conj (last degen-bytes) 0)))]
    (byte-array (conj (vec first-bytes) last-byte))
    ))

;; byte array representations for floats

(defn f64fle
  "Returns length 8 byte array representation of f"
  [f]
  (.array (.putDouble (.order (ByteBuffer/allocate 8)
                           ByteOrder/LITTLE_ENDIAN)
                      f)))


;;
;; MAIN - type conversion to bytecode
;;

(defn nil2bin []
  (i8fle 0))

(defn true2bin []
  (i8fle 1))

(defn false2bin []
  (i8fle 2))

(defn int2bin [integer]
  (let [type (i8fle 3)
        value (i64fle integer)]
    (join-byte-arrays type value)))

(defn float2bin [float]
  (let [type (i8fle 4)
        value (f64fle float)]
    (join-byte-arrays type value)))

(defn id2bin [identifier]
  (let [str (rest (str identifier))
        type (i8fle 5)
        length (i8fle (count str))
        content (apply join-byte-arrays (map (comp i8fle int) str))]
    (join-byte-arrays type length content)))

(defn str2bin [string]
  (let [type (i8fle 6)
        length (i32vle (count string))
        content (apply join-byte-arrays (map (comp i32vle int) string))]
    (join-byte-arrays type length content)))

(defn rng2bin [start-ind end-ind]
  (let [type (i8fle 7)
        start (i32vle start-ind)
        end (i32vle end-ind)]
    (join-byte-arrays type start end)))

(defn set2bin [set-inds]
  (let [type (i8fle 8)
        length (i32vle (count set-inds))
        content (apply join-byte-arrays (map i32vle set-inds))]
    (join-byte-arrays type length content)))

(defn list2bin [list-inds]
  (let [type (i8fle 9)
        length (i32vle (count list-inds))
        content (apply join-byte-arrays (map i32vle list-inds))]
    (join-byte-arrays type length content)))

(defn map2bin [map-inds]
  (let [type (i8fle 10)
        length (i32vle (count map-inds))
        content (apply join-byte-arrays (map i32vle (reduce into [] map-inds)))]
    (join-byte-arrays type length content)))

(defn module2bin [mod-inds]
  (let [type (i8fle 11)
        length (i32vle (count mod-inds))
        content (apply join-byte-arrays (map i32vle (reduce into [] mod-inds)))]
    (join-byte-arrays type length content)))

(defn do2bin [do-inds]
  (let [type (i8fle 12)
        length (i32vle (count do-inds))
        content (apply join-byte-arrays (map i32vle do-inds))]
    (join-byte-arrays type length content)))

(defn lambda2bin [arity context-inds code]
  (let [type (i8fle 13)
        arity (i8fle arity)
        context-count (i8fle (count context-inds))
        context (apply join-byte-arrays (map i32vle context-inds))]
    (join-byte-arrays type arity context-count context code)))

(defn import2bin [name-ind]
  (join-byte-arrays (i8fle 14) (i32vle name-ind)))

(defn when2bin [cond-inds]
  (let [type (i8fle 15)
        cond-count (i32vle (count cond-inds))
        conditions (apply join-byte-arrays
                          (map i32vle (reduce into [] cond-inds)))]
    (join-byte-arrays type cond-count conditions)))

(defn case2bin [arg-ind match-inds guard-inds value-inds]
  (let [type (i8fle 16)
        arg (i32vle arg-ind)
        case-count (i8fle (count match-inds))
        cases (apply join-byte-arrays
                       (map i32vle
                            (flatten
                             (map vec match-inds guard-inds value-inds))))]
    (join-byte-arrays type arg case-count cases)))

(defn res2bin [lambda-ind arg-inds]
  (let [type (i8fle 17)
        lambda (i32vle lambda-ind)
        arg-count (i8fle (count arg-inds))
        args (apply join-byte-arrays
                       (map i32vle arg-inds))]
    (join-byte-arrays type lambda arg-count args)))

(defn input2bin [inp-ind]
  (join-byte-arrays (i8fle 18) (i8fle inp-ind)))
