(ns klangmeister.instruments)

(defn plug [param input at context]
  "Plug an input into an audio parameter,
  accepting both numbers and ugens."
  (if (fn? input)
    (.connect (input at context) param)
    (.setValueAtTime param input at)))

(defn gain [level]
  (fn [at context]
    (doto (.createGain context)
      (-> .-gain (plug level at context)))))

(defn line
  "Build a line out of [dx y] coordinates, starting at [0 0]."
  [& corners]
  (fn [at context]
    (let [node (.createGain context)
          gain (.-gain node)]
      (.setValueAtTime gain 0 at)
      (reduce
        (fn [x [dx y]]
          (.linearRampToValueAtTime gain y (+ x dx))
          (+ dx x))
        at
        corners)
      node)))

(defn adshr [attack decay sustain hold release]
  (line [attack 1.0] [decay sustain] [hold sustain] [release 0]))

(defn ashr [attack hold release]
  (line [attack 1.0] [hold 1.0] [release 0]))

(defn percuss [attack decay]
  (line [attack 1.0] [decay 0.0]))

(defn connect
  [ugen1 ugen2]
  (fn [at context]
    (let [sink (ugen2 at context)]
      (.connect (ugen1 at context) sink)
      sink)))

(defn >> [& nodes]
  (reduce connect nodes))

(defn noise [bit duration]
  (fn [at context]
    (let [sample-rate 44100
          frame-count (* sample-rate duration)
          buffer (.createBuffer context 1 frame-count sample-rate)
          data (.getChannelData buffer 0)]
      (doseq [i (range sample-rate)]
        (aset data i (bit)))
      (doto (.createBufferSource context)
        (-> .-buffer (set! buffer))
        (.start at)))))

(def white-noise (partial noise #(-> (js/Math.random) (* 2) dec)))

(defn oscillator [type freq duration]
  (fn [at context]
    (doto (.createOscillator context)
      (-> .-frequency (plug freq at context))
      (-> .-type (set! type))
      (.start at)
      (.stop (+ at duration)))))

(defn rise [freq duration]
  (fn [at context]
    (let [modulator (.createOscillator context)
          width (.createGain context)
          wave (.createOscillator context)]
      (doto width
        (-> .-gain .-value (set! 400))
        (.connect (.-frequency wave)))
      (doto modulator
        (-> .-frequency .-value (set! 9))
        (-> .-type (set! "sine"))
        (.connect width)
        (.start at)
        (.stop (+ at duration)))
      (doto wave
        (-> .-frequency (plug freq at context))
        (-> .-type (set! "square"))
        (.start at)
        (.stop (+ at duration))))))

(def sin-osc (partial oscillator "sine"))
(def saw (partial oscillator "sawtooth"))
(def square (partial oscillator "square"))

(defn biquad-filter [type freq]
  (fn [at context]
    (doto (.createBiquadFilter context)
      (-> .-frequency (plug freq at context))
      (-> .-type (set! type)))))

(def lpf (partial biquad-filter "lowpass"))
(def hpf (partial biquad-filter "highpass"))

(defn destination [at context]
  (.-destination context))

(defn add [& ugens]
  (fn [at context]
    (reduce
      (fn [sink input]
        (doto sink
          (-> .-gain (plug input at context))))
      ((gain 1.0) at context)
      ugens)))

(defn bell! [{:keys [time duration pitch]}]
  (let [harmonic (fn [n proportion]
                   (>> (sin-osc (* n pitch) 1.5)
                       (percuss 0.01 proportion)
                       (gain 0.01)))]
    (apply add
           (map
             harmonic
             [1.0 2.0 3.0 4.1 5.2]
             [1.0 0.6 0.4 0.3 0.2]))))
