# Matchmaking Algorithm Simulation

Modelling and simulation of matchmaking algorithms for online 1v1 games.  
The study explores the sensitivity of matchmaking outcomes to policy weight selection, identifying trends and trade-off patterns rather than optimal universal parameters.

---

## Table of Contents

1. [Policy Weights – Value Ranges](#1-policy-weights--value-ranges)
2. [Queue Request Generation](#2-queue-request-generation)
3. [Algorithm-Specific Notes](#3-algorithm-specific-notes)

---

## 1. Policy Weights – Value Ranges

Every matchmaking algorithm shares three tunable weights that must always sum to **100 %**:

| Weight | Symbol | Description |
|---|---|---|
| **Skill** | `weightSkill` | How strictly the algorithm enforces similar skill ratings between opponents. |
| **Latency** | `weightLatency` | How strictly the algorithm enforces low estimated latency for a match. |
| **Wait Time** | `weightWaitTime` | How aggressively the algorithm tries to minimise queue wait time. |

### Allowed Ranges per Algorithm

Each algorithm constrains its weights to a specific interval. The **dominant factor** is the one with the widest upper bound.

| Algorithm | Skill Range | Latency Range | Wait Time Range | Default |
|---|---|---|---|---|
| **Skill-Based (SBMM)** | 50 % – 80 % | 10 % – 30 % | 10 % – 30 % | 70 / 15 / 15 |
| **Latency-Based** | 10 % – 30 % | 50 % – 80 % | 10 % – 30 % | 15 / 70 / 15 |
| **Short Wait-Time** | 10 % – 40 % | 10 % – 40 % | 50 % – 80 % | 20 / 20 / 60 |

### How the Weights Influence the Simulation

The weights drive two mechanisms inside every algorithm:

1. **Expanding tolerance windows** – For skill and latency, the maximum acceptable difference starts tight (proportional to the weight) and widens asymptotically as the player waits longer.

   ```
   tolerance(weight, waitSec) = maxRange × (1 − weight)
                               + maxRange × weight × (1 − e^(−waitSec / τ))
   ```

   | Parameter | Value | Meaning |
      |---|---|---|
   | `maxRange` (skill) | 1 000 | Full Elo-like rating span (0 – 1 000) |
   | `maxRange` (latency) | 300 ms | Beyond 300 ms is considered unplayable |
   | `τ` (decay rate) | 60 s | After ~60 s the window has expanded ≈ 63 %; after ~180 s ≈ 95 % |

   A **high** weight starts with a narrow window (strict matching) and expands slowly.  
   A **low** weight starts nearly fully open (lenient matching).

2. **Composite cost function** – Among candidates that pass the tolerance gates, a weighted score ranks pairs (lower is better):

   ```
   cost = weightSkill    × (skillDiff / 1000)
        + weightLatency  × (estimatedLatency / 300)
        + weightWaitTime × (1 − avgWait / 300)
   ```

### Quality Scores (reported in statistics)

| Metric | Formula | Scale |
|---|---|---|
| **Skill Quality** | `(1 − min(skillDiff / 250, 1)) × 100` | 0 (diff ≥ 250, severe mismatch) – 100 (perfect skill match) |
| **Latency Quality** | Region tier (0–70): penalty 0→70, 1–10ms→50, 11–30ms→20, >30ms→0; plus similarity bonus (0–30): `(1 − min(baseDiff/40, 1)) × 30` | 0 (worst cross-continent + dissimilar latency) – 100 (same-region + identical latency). Measures algorithm effectiveness, not connection quality. |
| **Wait-Time Quality** | `(1 − min(avgWaitTime / 60, 1)) × 100` | 0 (waited ≥ 60 s) – 100 (instant match) |
| **Match Quality** | `(skillQuality + latencyQuality + waitTimeQuality) / 3` | 0 (worst) – 100 (perfect composite) |

---

## 2. Queue Request Generation

Each simulated player is represented as a **`QueueRequest`** record – a snapshot of a player queuing for a game. Queue requests are generated programmatically by `PlayerGenerator` and persisted as JSON in the `data/` directory.

### Player Count (Queue Size per Day)

The `PLAYER_COUNT` constant controls how many queue requests are generated for a single simulated day. This represents the **total number of players entering the matchmaking queue across the entire day**, not the number of concurrent players at any given moment.

| PLAYER_COUNT | Real-world equivalent |
|---|---|
| 500 – 2 000 | Indie / niche competitive title |
| 5 000 – 20 000 | Small popular game (e.g. fighting games, Rocket League off-peak) |
| 20 000 – 100 000 | Mid-tier popular game or single region of a large title (e.g. Valorant, Apex Legends) |
| 100 000 – 500 000 | Blockbuster title per region (e.g. Fortnite, League of Legends) |
| 500 000+ | Peak mega-title (e.g. CS2 global, LoL China) |

The default value is **20 000** (small-to-mid popular game). Larger pools produce better match quality because the algorithm has more candidates to choose from — this is expected and realistic behavior.

### QueueRequest Fields

| Field | Type | Range / Distribution | Description |
|---|---|---|---|
| `id` | `String` (UUID) | Random v4 UUID | Unique identifier for this queue request. |
| `playerTag` | `String` | `"P001"` … `"P20000"` | Sequential player tag / handle. |
| `skillRating` | `int` | 0 – 1 000 | Elo-like rating. Gaussian distribution: μ = 500, σ = 150, clamped to [0, 1 000]. |
| `region` | `String` | 10 regions (see below) | The player's latency region. |
| `latencyMs` | `int` | 20 – 180 ms | Base connection latency (see distribution below). |
| `joinTimeSeconds` | `int` | 0 – 86 399 | Second-of-day when the player enters the queue (see distribution below). |
| `patienceSeconds` | `int` | 30 – 300 s | How long the player will wait before abandoning. Gaussian: μ = 120 s, σ = 60 s, clamped to [30, 300]. |
| `engagementScore` | `double` | 0.0 – 1.0 | Activity / engagement metric. Gaussian: μ = 0.5, σ = 0.2, clamped to [0.0, 1.0]. Reserved for future use (re-queue logic). |
| `queueDate` | `LocalDate` | Date at generation | The calendar date of this queue request. |

### Skill Rating Distribution

A **Gaussian (normal)** distribution centred on 500 with a standard deviation of 150, hard-clamped to the [0, 1 000] range. This mirrors rating systems like Valorant RR (0–800) or Overwatch divisions. Most players cluster around the average; extreme ratings (very low or very high) are rare.

```
skill = clamp(Gaussian(μ=500, σ=150), 0, 1000)
```

#### Skill Difference Buckets

| Gap | Label | Interpretation |
|---|---|---|
| 0 – 25 | Perfect | Same rank / division |
| 26 – 75 | Good | Adjacent division |
| 76 – 150 | Decent | 1–2 divisions apart |
| 151 – 250 | Poor | Noticeable mismatch |
| > 250 | Bad | Severe mismatch |

### Region Distribution

Players are assigned to one of **10 regions** across three continents. Each region has an equal 10 % probability, giving approximate continent shares of EU 40 %, NA 40 %, ASIA 20 %.

| Continent | Regions |
|---|---|
| **EU** (40 %) | EU-NORTH, EU-SOUTH, EU-EAST, EU-WEST |
| **NA** (40 %) | NA-NORTH, NA-SOUTH, NA-EAST, NA-WEST |
| **ASIA** (20 %) | ASIA-EAST, ASIA-WEST |

#### Cross-Region Latency Penalties

When two players are matched from different regions, an additive latency penalty is applied on top of their average base latency:

| Scenario | Penalty |
|---|---|
| Same region | 0 ms |
| Same continent – EU sub-regions | +5 ms |
| Same continent – NA sub-regions | +8 ms |
| Same continent – ASIA sub-regions | +10 ms |
| EU ↔ NA | +25 ms |
| EU ↔ ASIA | +35 ms |
| NA ↔ ASIA | +35 ms |

### Latency Distribution

Base latency (before cross-region penalty) follows a **weighted bucket distribution** designed to mirror realistic internet conditions:

| Quality | Latency Range | Probability | Interpretation |
|---|---------------|-------------|---|
| Excellent | 10 – 30 ms    | 40 %        | Very good internet / same datacenter region |
| Good | 30 – 60 ms    | 30 %        | Typical regional connection (majority) |
| Playable | 60 – 100 ms   | 25 %        | Cross-region but still playable |
| Bad | 100 – 180 ms  | 5 %         | Poor internet or distant routing (minority) |

Within each bucket the value is drawn uniformly.

### Join Time Distribution (Second-of-Day)

Players don't arrive uniformly; they follow a **bimodal Gaussian** distribution with two activity peaks to simulate realistic daily play patterns:

| Peak | Time | Standard Deviation |
|---|---|---|
| Lunch peak | 12:00 (43 200 s) | 3 hours (10 800 s) |
| Evening peak | 20:00 (72 000 s) | 3 hours (10 800 s) |

Each player is randomly assigned to one of the two peaks (50/50), then a Gaussian offset is applied. The result wraps around midnight to stay within [0, 86 399]. This produces high queue density around noon and evening, with low activity in the early morning hours.

### Patience Distribution

A **Gaussian** distribution centred on 120 s with σ = 60 s, clamped to [30, 300]. Most players are willing to wait around two minutes; very impatient players leave after 30 s and the most patient ones endure up to 5 minutes.

```
patience = clamp(Gaussian(μ=120, σ=60), 30, 300)
```

### Reproducibility

The generator uses a **date-based seed** derived from the generation date:

```
seed = date.toEpochDay() × 31 + 17
```

This means:
- **Same date → same seed → identical player pool.** Running the simulation multiple times on the same day (or selecting the same day's file) always produces the exact same players, so different algorithm/weight configurations are compared against identical input.
- **Different date → different seed → different player pool.** Each day produces a unique set of players, allowing the study to verify that results hold across varying populations.

Player pools are saved as `players_yyyy-MM-dd.json` in the `data/` directory (e.g. `players_2026-02-28.json`). At startup the console lists all previously generated pools and offers to generate a new one for any date:

```
╔═══════════════════════════════════════════╗
║         SELECT PLAYER POOL                ║
╠═══════════════════════════════════════════╣
║  1. Generate new pool (enter date)        ║
║  2. players_2026-02-26.json               ║
║  3. players_2026-02-27.json               ║
╚═══════════════════════════════════════════╝
Enter choice (1-3): 1
  Enter date (yyyy-MM-dd): 2026-02-28
```

Selecting an existing file loads it directly; selecting option 1 prompts for a date in `yyyy-MM-dd` format, generates a fresh pool seeded from that date, and writes it to disk. If a file for that date already exists it is overwritten.

### JSON Example

```json
{
  "id": "a3f1c2d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "playerTag": "P001",
  "skillRating": 652,
  "region": "EU-WEST",
  "latencyMs": 30,
  "joinTimeSeconds": 81929,
  "patienceSeconds": 166,
  "engagementScore": 0.45,
  "queueDate": "2026-02-28"
}
```

---

## 3. Algorithm-Specific Notes

### Skill-Based (SBMM) Algorithm — Bracket-and-Rank Matching

The Skill-Based algorithm is a **ranked-mode** matchmaker modelled after real-world ELO / MMR systems. Match quality takes strict priority over speed: players with vastly different skill ratings are **never** matched — they wait longer instead.

#### How it works

1. **Sort by skill** — the queue is sorted by skill rating so similar players are adjacent.
2. **Skill gate** — each player can only match with another whose skill diff is within a tight *search range* controlled by the skill weight. A hard cap prevents extreme mismatches regardless of configuration.
3. **Latency gate** — among skill-eligible candidates, a *latency ceiling* filters out high-latency pairs. The ceiling is controlled by the latency weight.
4. **Weighted scoring** — all pairs passing both gates are scored with a composite formula. Skill diff is normalized against the *search range* (not the full 0–1000 range), giving latency and wait-time weights real influence among similarly-skilled candidates.
5. **Greedy global assignment** — pairs are sorted by score (best first) and greedily assigned.

#### Search range (ranked-mode skill brackets)

The skill weight controls the search range — the maximum skill diff the algorithm will consider:

```
searchRange = TIGHT_RANGE + (LOOSE_RANGE − TIGHT_RANGE)
            × (MAX_SKILL_WEIGHT − skillWeight) / (MAX_SKILL_WEIGHT − MIN_SKILL_WEIGHT)
```

| Skill Weight | Search Range | Interpretation |
|---|---|---|
| 80 % | 75 pts | Same division only — very tight matches |
| 70 % | 100 pts | Adjacent division — default |
| 60 % | 125 pts | 1–2 divisions apart |
| 50 % | 150 pts | Maximum — still within "decent" range |

#### Hard skill cap

No match may **ever** exceed 150 skill diff under normal conditions. This structurally guarantees:

- **Skill > 250 %: always 0 %** — severe mismatches are impossible
- **Skill 151–250 %: always 0 %** — cross-division mismatches are blocked

For players waiting longer than 30 seconds, the cap slowly expands toward 200 to prevent permanent starvation:

```
skillCap(waitSec) = 150 + 50 × (1 − e^(−waitSec / 120))
```

| Wait Time | Effective Cap | Notes |
|---|---|---|
| 0–10 s | 150 | Standard — no 151+ matches |
| 30 s | ≈ 162 | Barely opened |
| 60 s | ≈ 174 | Moderate |
| 120 s | ≈ 187 | Long-waiter relief |

Even at maximum expansion, the cap never reaches 250, so the "> 250" bucket is always 0 %.

#### Latency ceiling

A tight, gaming-realistic ceiling derived linearly from the latency weight:

```
baseCeiling = 150 − (latencyWeight − 0.10) / 0.20 × 100
```

| Latency Weight | Base Ceiling | Effect |
|---|---|---|
| 10 % | 150 ms | Allows cross-region freely |
| 15 % | 125 ms | Blocks worst cross-continent |
| 20 % | 100 ms | Prefers same-continent |
| 30 % | 50 ms | Forces same-region — dramatic latency improvement |

The ceiling expands asymptotically as a player waits (decay rate 120 s), so long-waiters are not permanently stuck by the gate.

#### Within-bracket scoring

Among candidates passing both gates, a weighted composite score ranks pairs (lower = better):

```
score = weightSkill   × (skillDiff / searchRange)
      + weightLatency × (latency / 300)
      − weightWait    × ((waitA + waitB) / 120)
```

The key insight: skill diff is normalized against the **search range** (75–150 pts), not the full 1000-point range. This means a 50-point diff in a 100-point bracket = 0.50 (significant), not 0.05 (negligible). This gives the latency and wait-time terms real leverage in determining which candidate wins within a bracket.

#### Observed behaviour

| Config | AvgSkillDiff | SkillQ | LatQ | MatchQ | Skill >150 |
|---|---|---|---|---|---|
| 80/10/10 | 23 | 90 | 39 | 73 | 0 % |
| 70/15/15 | 28 | 88 | 39 | 73 | 0 % |
| 60/20/20 | 33 | 86 | 41 | 72 | 0 % |
| 50/30/20 | 45 | 81 | **58** | **75** | 0 % |
| 50/20/30 | 37 | 85 | 41 | 72 | 0 % |

- Skill weight clearly differentiates: 80 % → AvgSkillDiff 23 vs 50 % → 45
- Latency weight produces visible jumps: 30 % latency (50/30/20) achieves LatQ 58 vs 39 at 10 %
- Wait-time weight controls speed: 30 % wait (50/20/30) has AvgWait 4.7 s vs 8.3 s for 50/30/20
- Skill > 250 and 151–250 are **always 0 %** — ranked integrity is guaranteed

---

### Latency-Based Algorithm — Inherent Limitation

A latency-based matchmaking algorithm **cannot improve a player's individual connection quality**. Each player enters the queue with a fixed base latency determined by their internet connection and physical distance to the game server. The algorithm can only **avoid making it worse** by minimising the cross-region penalty — i.e. by preferring to match players within the same continent / sub-region.

To capture this distinction, the simulation reports two complementary latency metrics:

| Metric | What it measures |
|---|---|
| **AvgLatencyMs** | The absolute estimated match latency (average of both players' base latency + cross-region penalty). Dominated by the player pool's internet quality — largely the same across algorithms. |
| **AvgLatencyQuality** | How well the algorithm avoided adding cross-region penalty. 100 = same-region match (0 ms added), 0 = worst cross-continent match (≥ 35 ms added). **This is the metric that differentiates algorithms.** |

The latency quality score uses a **hybrid** system with two components:

1. **Region tier (0–70 points)** — based on the cross-region penalty (the avoidable part of the latency):
   - Same region → penalty 0 ms → **70 points**
   - Same continent, different sub-region → penalty 1–10 ms → **50 points**
   - Cross-continent nearby (e.g. EU↔NA) → penalty 11–30 ms → **20 points**
   - Cross-continent far (e.g. EU↔ASIA, NA↔ASIA) → penalty > 30 ms → **0 points**

2. **Latency similarity bonus (0–30 points)** — based on how close the two players' base latencies are:
   - Difference 0 ms → **30 points** (identical connection quality)
   - Difference 40 ms → **15 points**
   - Difference ≥ 80 ms → **0 points**

   This rewards the algorithm for pairing players with similar connection quality (e.g. 30 ms + 35 ms scores higher than 10 ms + 50 ms, even though both are same-region).

The combination of a discrete region tier and a continuous similarity bonus ensures that even small weight changes produce measurable score differences when averaged across thousands of matches.

> **Note on weight sensitivity at large pool sizes:** With very large player pools (100k+), the latency quality score may show minimal variation between different weight configurations for the same algorithm. This is because the pool is large enough that the algorithm can find excellent matches regardless of how strict the weight is — there are always plenty of same-region, similar-latency candidates available. This is a realistic and expected property of matchmaking: **larger populations naturally reduce the impact of tuning**. Weight sensitivity becomes more visible at smaller pool sizes (5k–20k) where the algorithm must make genuine tradeoffs.

### Latency-Based Algorithm — Cross-Continent Hold

To maximise same-continent matching, the latency-based algorithm implements a **cross-continent hold**: players are only eligible for cross-continent matching after waiting at least a minimum number of seconds in the queue. This keeps players in their regional pool longer, giving same-continent candidates more time to arrive before the algorithm falls back to cross-continent pairing.

The hold time scales with the latency weight:

```
crossContinentHoldSeconds = (int)(latencyWeight × 30)
```

| Latency Weight | Hold Time | Effect |
|---|---|---|
| 80 % | ~24 s | Aggressive region-lock — players wait longer for same-continent matches |
| 70 % | ~21 s | Default — good balance between latency quality and wait time |
| 50 % | ~15 s | Loose — falls back to cross-continent matching quickly |

Players who have not yet reached the hold threshold remain in their continent bucket and are retried on subsequent ticks. Once the threshold is crossed, they enter the cross-continent fallback pool where they can be matched with players from any continent.

---

### Short Wait-Time Algorithm — Hold-and-Pick Matching

The Short Wait-Time algorithm uses a **hold-and-pick** strategy with tolerance gates: every player must wait a minimum *hold time* before becoming eligible for matching. Once eligible, only pairs passing through skill and latency tolerance gates are scored, and the globally best pair is matched first.

#### Why this approach

With large player pools (20k–100k), scoring-weight-only approaches produce identical results across different weight configurations because the pool is so dense that perfect matches always exist instantly. The hold timer is a **structural mechanism** — like the Latency-Based algorithm's geographic bucketing — that forces different behaviour by controlling *when* players become matchable, not just *how* they're scored. The tolerance gates provide a second lever: controlling *who* can match, not just when.

#### Tick interval

The algorithm runs at **1-second ticks** (other algorithms default to 5 s). This preserves fine-grained wait-time resolution so that small hold-time differences (e.g. 2.2 s vs 0 s) produce measurably different outcomes.

#### Hold time formula

The wait-time weight directly controls how long players are held before becoming eligible. A **power curve** (`p^0.6`) is used instead of linear interpolation to spread out the high-wait-weight end (70 % vs 80 %) where linear would compress them:

```
p           = (1 − waitTimeWeight) / (1 − MIN_WAIT_WEIGHT)
holdSeconds = MAX_HOLD × p^0.6
```

Where `MAX_HOLD = 4 s` and `MIN_WAIT_WEIGHT = 0.50`.

| Wait Weight | Hold Time | Effect |
|---|---|---|
| 50 % | 4.0 s | Maximum quality accumulation — many candidates in pool before matching |
| 60 % | 3.1 s | Moderate hold — good balance |
| 70 % | 2.2 s | Short hold — biased towards speed |
| 80 % | 0.0 s | No hold — instant matching, lowest quality |

#### Tolerance gates with amplified sensitivity

Before scoring, each candidate pair must pass both a skill and a latency gate. The gates use the **stricter of both players' expanding tolerance windows**. To make even small weight differences (e.g. 10 % vs 15 %) produce noticeably different gate widths, the algorithm uses `sqrt(weight)` instead of linear weight:

```
effectiveWeight = sqrt(weight)
initial         = maxRange × (1 − effectiveWeight)
extra           = maxRange × effectiveWeight × (1 − e^(−waitSec / 30))
tolerance       = initial + extra
```

| Weight | Effective | Initial gate (fraction of max range) |
|---|---|---|
| 10 % | 31.6 % | 68.4 % open |
| 15 % | 38.7 % | 61.3 % open |
| 25 % | 50.0 % | 50.0 % open |
| 40 % | 63.2 % | 36.8 % open |

The decay rate is 30 s (faster than the shared 60 s default) so that gates open quickly and players are not stuck waiting for candidates.

#### Scoring formula

Among candidates passing both gates, a weighted composite score ranks pairs (lower = better):

```
score = weightSkill   × (skillDiff / 1000)
      + weightLatency × (latency / 300)
      − weightWait    × ((waitA + waitB) / 120)
```

Components are normalized against the full fixed ranges (1000 for skill, 300 for latency) so that a weight change always produces a proportional score shift.

#### How it works (summary)

1. **Hold phase:** Players who have waited less than `holdSeconds` are *not eligible* this tick.
2. **Tolerance gates:** Among eligible players, only pairs within both players' skill and latency tolerance windows are considered. The gates use `sqrt(weight)` amplification and the stricter of both players' windows.
3. **Pair scoring:** Surviving pairs are scored with the weighted composite formula.
4. **Global greedy assignment:** Pairs are sorted by score (best first) and greedily assigned.

#### Why the hold timer + gates create weight sensitivity

- At **80 % wait weight** (hold = 0 s): players match the instant they arrive. The pool is tiny each tick (1–2 players), so the algorithm has almost no choice — it pairs whoever is there. Quality is low.
- At **60 % wait weight** (hold = 3.1 s): players accumulate for ~3 seconds. With ~1.2 arrivals/sec (100k pool), ~4 candidates are available. The scoring weights now have multiple candidates to rank, and the tolerance gates filter out poor matches.
- At **50 % wait weight** (hold = 4.0 s): ~5 candidates accumulate. The algorithm has a large enough pool to find genuinely good matches, at the cost of everyone waiting ~4+ seconds.

The scoring weights (skill vs. latency) control *which* quality dimension is prioritised among the accumulated candidates. The tolerance gates (controlled by the same weights) control *who* enters the scoring at all. The wait-time weight controls *how many* candidates accumulate via the hold timer.

