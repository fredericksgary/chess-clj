(ns com.gfredericks.chess.rules-test
  (:require [clojure.set :as sets]
            [clojure.test :refer :all]
            [com.gfredericks.chess.board :as board]
            [com.gfredericks.chess.generators :as cgen]
            [com.gfredericks.chess.position :as position]
            [com.gfredericks.chess.moves :as moves]
            [com.gfredericks.chess.rules :refer :all]
            [com.gfredericks.chess.squares :refer :all]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]))

(def fromto (juxt moves/primary-from moves/primary-to))

(defn make-move-like [pos [from to :as pair]]
  (->> (moves pos)
       (filter (comp #{pair} fromto))

       ;; make sure there's exactly one matching move
       (#(doto % (-> (count) (= 1) (assert (str "It's " (count %))))))

       (first)
       (make-move pos)))

(defn moves'
  "Like .rules/moves, but returns [from to] pairs."
  [pos]
  (map fromto (moves pos)))

(deftest starting-position-test
  (is (= (set (moves' position/initial))
         #{[a2 a3] [b2 b3] [c2 c3] [d2 d3]
           [e2 e3] [f2 f3] [g2 g3] [h2 h3]
           [a2 a4] [b2 b4] [c2 c4] [d2 d4]
           [e2 e4] [f2 f4] [g2 g4] [h2 h4]
           [b1 a3] [b1 c3] [g1 f3] [g1 h3]})))

(deftest legal-moves-test
  (let [pos #chess/fen "r2qk2r/pp3ppp/1npbpnb1/8/3P3N/2N1P1P1/PP2BP1P/R1BQ1RK1 b - - 0 1"
        mvs (set (moves' pos))]
    (are [from to] (mvs [from to])
         g6 b1
         h7 h5
         d8 b8
         #_e8 #_g8)
    (are [from to] (not (mvs [from to]))
         g3 g4
         d1 e1
         d4 c5
         e6 f5
         e8 c8
         b7 b5))

  ;; large random position, verification list generated by hand.
  ;; white is in check here
  ;;
  ;;   ---------------------------------
  ;; 8 | N |   |   |   | B |   |   | N |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 7 | k |   | b |   | K |   | q | p |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 6 | q |   | n |   |   |   |   |   |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 5 | b |   |   |   |   | r |   |   |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 4 |   |   |   | P |   | P | p |   |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 3 |   |   | Q | q | P |   | p |   |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 2 |   |   |   |   |   | P |   | B |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 1 |   |   |   |   |   |   | B | r |
  ;;   ---------------------------------
  ;;     a   b   c   d   e   f   g   h
  (let [pos #chess/fen "N3B2N/k1b1K1qp/q1n5/b4r2/3P1Pp1/2QqP1p1/5P1B/6Br w - - 0 1"]
    (is (= (moves' pos)
           [[e7 e6]]))
    (is (= (-> pos (make-move-like [e7 e6]) (moves') (set))
           ;; grouped by the piece doing the moving
           #{[a7 a8] [a7 b8] [a7 b7]

             [c7 b8] [c7 d8] [c7 b6] [c7 d6] [c7 e5] [c7 f4]

             [g7 f8] [g7 g8] [g7 h8] [g7 d7] [g7 e7] [g7 f7]
             [g7 f6] [g7 g6] [g7 h6] [g7 e5] [g7 g5] [g7 d4]

             [h7 h6] [h7 h5]

             [a6 c8] [a6 b7] [a6 b6] [a6 b5] [a6 c4]

             [c6 b8] [c6 d8] [c6 e7] [c6 e5] [c6 b4] [c6 d4]

             [a5 b6] [a5 b4] [a5 c3]

             [f5 f8] [f5 f7] [f5 f6] [f5 b5] [f5 c5] [f5 d5]
             [f5 e5] [f5 g5] [f5 h5] [f5 f4]

             [d3 b5] [d3 c4] [d3 d4] [d3 e4] [d3 c3] [d3 e3]
             [d3 c2] [d3 d2] [d3 e2] [d3 b1] [d3 d1] [d3 f1]

             [g3 f2] [g3 g2] [g3 h2]

             [h1 g1] [h1 h2]}))))


(def castling-pos
  #chess/fen "3k4/8/8/8/8/8/8/4K2R w K - 0 1")

(defn make-moves [pos & moves]
  (reduce make-move-like pos moves))

(deftest castling-test
  (testing "You can castle in this position"
    (is (legal-move? castling-pos [e1 g1])))
  (testing "You can't castle if the position precludes it"
    (is (not (-> castling-pos
                 (assoc-in [:castling :white :king] false)
                 (legal-move? [e1 g1])))))
  (testing "You can't castle if the rook moves"
    (let [current-moves (set (moves' castling-pos))
          new-pos (make-moves castling-pos
                              [h1 h2]
                              [d8 d7]
                              [h2 h1]
                              [d7 d8])
          new-moves (set (moves' new-pos))]
      (is (sets/subset? new-moves current-moves))
      (is (= #{[e1 g1]} (sets/difference current-moves new-moves)))))
  (testing "You can't castle if the king is in check at any point"
    (are [sq piece] (-> (update-in castling-pos [:board] board/set sq piece)
                        (legal-move? [e1 g1])
                        (false?))
         ;; direct checks
         e6 :r, c3 :b, d3 :n, a5 :q, d2 :p, f2 :p
         ;; through checks
         f6 :r, d3 :b, e3 :n, h2 :n, e2 :p, g2 :p
         ;; into checks
         g6 :r, e3 :b, g8 :q, h2 :p))
  (testing "You can't castle if one rook moves into the other's place"
    (testing "opposing piece moves out"
      (let [pos #chess/fen "rn2k2r/8/8/8/8/8/8/R3K3 b kq - 0 1"
            pos1 (make-moves pos [b8 d7] [e1 e2])
            ;; white rook captures black's queen rook, then black's king
            ;; rook moves over to where black's queen rook was.  No
            ;; castling should be legal at this point of course.
            pos2 (make-moves pos
                             [b8 d7] [a1 a8] [d7 b8] [a8 a1]
                             [h8 h1] [e1 e2] [h1 a1] [e2 f2]
                             [a1 a8] [f2 e2] [b8 d7] [e2 f2])]
        (is (legal-move? pos1 [e8 c8]))
        (is (not (legal-move? pos2 [e8 c8])))))
    (testing "opposing piece captured in place"
      (let [pos #chess/fen "rn2k2r/8/8/8/8/8/8/R3K3 b kq - 0 1"
            pos' (make-moves pos
                             [b8 d7] [a1 a8] [d7 b8] [e1 e2]
                             [h8 h1] [e2 f2] [h1 a1] [f2 e2]
                             [a1 a8] [e2 f2] [b8 d7] [f2 e2])]
        (is (not (legal-move? pos' [e8 c8]))))))
  (testing "You can't castle if there are pieces in the way"
    (let [white-pos #chess/fen "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1"
          black-pos (assoc white-pos :turn :black)
          white-kingside [e1 g1]
          white-queenside [e1 c1]
          black-kingside [e8 g8]
          black-queenside [e8 c8]]
      (are [pos mv] (legal-move? pos mv)
           white-pos white-queenside
           white-pos white-kingside
           black-pos black-queenside
           black-pos black-kingside)
      (are [piece sq pos mv] (let [pos' (update-in pos [:board]
                                                   board/set sq piece)]
                               (not (legal-move? pos' mv)))
           :Q b1 white-pos white-queenside
           :N c1 white-pos white-queenside
           :B d1 white-pos white-queenside
           :Q f1 white-pos white-kingside
           :R g1 white-pos white-kingside
           :q b8 black-pos black-queenside
           :n c8 black-pos black-queenside
           :b d8 black-pos black-queenside
           :q f8 black-pos black-kingside
           :r g8 black-pos black-kingside))))

(def en-passant-pos
  #chess/fen "3k4/8/8/8/2p2p1p/8/1P4P1/4K3 w K - 0 1")

(deftest en-passant-test
  (testing "You can do en passant after a jump"
    (let [pos (make-move-like en-passant-pos [b2 b4])
          ep-move [c4 b3]]
      (is (legal-move? pos ep-move))
      (let [pos' (make-move-like pos ep-move)]
        (is (-> pos' :board (board/get b4) (= :_))))))
  (testing "You can't do en passant two moves later"
    (let [pos (make-moves en-passant-pos
                          [b2 b4]
                          [d8 e8]
                          [e1 e2])]
      (is (not (legal-move? pos [c4 b3])))))
  (testing "You can do an en passant from either side"
    (let [pos (make-move-like en-passant-pos [g2 g4])]
      (is (legal-move? pos [h4 g3]))
      (is (legal-move? pos [f4 g3]))))
  (testing "You can't do an en passant if it wasn't a jump"
    (let [pos (make-moves en-passant-pos
                          [b2 b3]
                          [h4 h3]
                          [b3 b4])]
      (is (not (legal-move? pos [c4 b3])))))
  (testing "En-passant works at the edge of the board"
    (let [pos #chess/fen "3k4/8/8/8/1p6/8/P7/4K3 w K - 0 1"
          move-1 [a2 a4]
          move-2 [b4 a3]]
      (is (legal-move? pos move-1))
      (let [pos' (make-move-like pos move-1)]
        (is (legal-move? pos' move-2))
        (is (= :P (board/get (:board pos') a4)))
        (let [pos'' (make-move-like pos' move-2)]
          (is (= :_ (board/get (:board pos'') a4))))))))

(defn rand-nth'
  [^java.util.Random r coll]
  (nth coll
       (.nextInt r (count coll))))

(defn gen-moves
  [pos]
  (gen/elements (moves pos)))

(defn moves-maybe-progressive
  [pos]
  (cond->> (moves pos)
           (= 50 (:half-move pos))
           (filter moves/progressive?)))

(defn gen-game-from
  "Returns a generator for a sequence of moves from the given position."
  [pos]
  (let [moves (moves-maybe-progressive pos)]
    (if (empty? moves)
      (gen/return ())
      (gen/bind (gen/elements moves)
                (fn [move]
                  (let [pos' (make-move pos move)]
                    (gen/fmap (fn [moves]
                                (cons move moves))
                              (gen-game-from pos'))))))))

(def gen-game-prefix
  "Generates a sequence of moves from the initial position."
  (gen/bind gen/pos-int
            (fn f
              ([move-count]
                 (f move-count position/initial))
              ([move-count pos]
                 (if (zero? move-count)
                   (gen/return ())
                   (let [legal-moves (moves pos)]
                     (if (empty? legal-moves)
                       (gen/return ())
                       (gen/bind (gen/elements legal-moves)
                                 (fn [move]
                                   (gen/fmap #(cons move %)
                                    (f (dec move-count) (make-move pos move))))))))))))

(def gen-reachable-pos
  (gen/fmap #(reduce make-move position/initial %) gen-game-prefix))

(defspec play-a-random-game 10
  (prop/for-all [move-list (gen-game-from position/initial)]
    (let [final-position (reduce make-move position/initial move-list)
          status (position-status final-position)]
      (or (#{:checkmate :stalemate} status)
          (= 50 (:half-move final-position))))))


;; Moar tests:
;; - test various kinds of check, and how your move choices in
;;   a complex position become vastly fewer
;; - test promotions


;;
;; Backwards!
;;

(def gen-position-with-move
  (-> (gen/such-that (comp not-empty moves) cgen/position)
      (gen/bind (fn [pos]
                  (gen/fmap #(vector pos %)
                            (gen/elements (moves pos)))))))

(def gen-position-with-unmove
  (-> (gen/such-that (comp not-empty unmoves) cgen/position)
      (gen/bind (fn [pos]
                  (gen/fmap #(vector pos %)
                            (gen/elements (unmoves pos)))))))

(defspec forwards-backwards-roundtrip 100
  (prop/for-all [[pos mv] gen-position-with-move]
    (some #{mv}
          (unmoves (make-move pos mv)))))

(defspec backwards-forwards-roundtrip 100
  (prop/for-all [[pos mv] gen-position-with-unmove]
    (some #{mv}
          (moves (make-unmove pos mv)))))

(defn roundtrips?
  [pos]
  (let [the-moves (moves pos)
        the-unmoves (unmoves pos)]
    (and (every? (fn [move]
                   (some #{move}
                         (unmoves (make-move pos move))))
                 the-moves)
         (every? (fn [unmove]
                   (some #{unmove}
                         (moves (make-unmove pos unmove))))
                 the-unmoves))))

(defspec reachable-pos-roundtrip 10
  (prop/for-all [pos gen-reachable-pos]
    (roundtrips? pos)))

(def special-moves-pos
  #chess/fen "1n2k2r/P6p/8/5pP1/8/8/8/R3K3 w Qk f6 0 1")

(deftest roundtrips-with-special-moves
  (is (roundtrips? special-moves-pos))
  (is (roundtrips? (assoc special-moves-pos :turn :black))))

(defspec rewindable 10
  (prop/for-all [moves gen-game-prefix]
    (let [all-positions (reductions make-move position/initial moves)
          pairs-with-moves (map list (partition-all 2 1 all-positions) moves)
          normalize #(dissoc % :half-move :en-passant :castling)]
      (every? (fn [[[before after] move]]
                (let [before' (make-unmove after move)]
                  (and (some #{move} (unmoves after))
                       (= (normalize before) (normalize before')))))
              pairs-with-moves))))

(defspec backwards-to-legal-positions 100
  (prop/for-all [[pos unmove] gen-position-with-unmove]
    (and (legal-position? pos)
         (legal-position? (make-unmove pos unmove)))))

(defspec reachable-positions-are-legal 100
  (prop/for-all [pos cgen/gen-position-from-random-game]
    (legal-position? pos)))

(def no-backwards-moves-pos
  ;; A position one of my searches found that (mildly curiously)
  ;; has no legal backwards moves (because there's no way for
  ;; that pawn to have moved into the position to check the king).
  ;; ========================================
  ;;   ---------------------------------
  ;; 8 |   |   |   |   |   |   |   |   |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 7 |   |   |   |   |   |   |   |   |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 6 |   |   |   |   |   | Q |   | B |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 5 |   |   | p | k | p |   |   | p |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 4 | P |   |   | p |   |   |   | p |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 3 |   |   | K |   |   |   |   | p |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 2 | p |   | n |   |   |   | r | p |
  ;;   +---+---+---+---+---+---+---+---+
  ;; 1 | r | b |   |   |   |   |   | B |
  ;;   ---------------------------------
  ;;     a   b   c   d   e   f   g   h
  ;; 1: white to move
  ;; (Castling: -)
  ;; half-move: 0
  ;; ========================================
  #chess/fen "8/8/5Q1B/2pkp2p/P2p3p/2K4p/p1n3rp/rb5B w - - 0 1")

(deftest no-way-that-king-could-have-been-checked
  (is (empty? (unmoves no-backwards-moves-pos))))

(deftest this-call-should-not-crash
  (is (false? (legal-position? #chess/fen "P1r5/3B4/1p6/3p4/2RP3K/8/8/k7 w - - 0 0"))))

(deftest bishop-color-test
  (let [nine-light-bishops-and-one-dark
        #chess/fen "k7/8/8/8/B1B5/1B1B4/B1B1B3/1B1BB2K w - - 0 0"

        ten-light-bishops
        #chess/fen "k7/8/8/8/B1B5/1B1B4/B1B1B3/1B1B1B1K w - - 0 0"]
    (is (legal-position? nine-light-bishops-and-one-dark))
    (is (not (legal-position? ten-light-bishops)))))
