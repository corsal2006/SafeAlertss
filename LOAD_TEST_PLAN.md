# SafeAlerts — Load Testing & Scalability Plan

## 1. Overview & Objectives

The SafeAlerts backend (Firebase Firestore + Authentication) supports real-time location streaming and critical emergency SOS dispatches. This plan establishes benchmark methodology, concurrent user tiers, acceptance thresholds, empirical latency distributions, and cloud operating cost projections.

---

## 2. Load Profiles & Concurrency Tiers

| Tier | Profile Description | Virtual Users (VU) | Operations / User | Total Operations | Target Operation Type |
|---|---|---|---|---|---|
| **Tier 1** | Baseline Local Load | 10 | 10 | 100 | 15s Location Telemetry |
| **Tier 2** | Moderate Suburban Load | 50 | 10 | 500 | 15s Location Telemetry |
| **Tier 3** | Peak Normal Metro Load | 100 | 10 | 1,000 | 15s Location Telemetry |
| **Tier 4** | Heavy City-Wide Load | 250 | 8 | 2,000 | 15s Location Telemetry |
| **Tier 5** | Regional Surge Load | 500 | 6 | 3,000 | 15s Location Telemetry |
| **Tier 6** | Extreme Disaster Stress Load | 1,000 | 4 | 4,000 | 15s Location Telemetry |
| **Tier 7** | High-Priority SOS Burst Surge | 250 | 5 | 1,250 | Emergency Incident Creation + Outbox Audit |

---

## 3. Location Telemetry Volume & Cost Projections

### 3.1 Write Volume Formula
During an active emergency or journey tracking session:
$$\text{Updates per user per minute} = \frac{60\text{ seconds}}{15\text{ seconds}} = 4\text{ writes/min}$$

$$\text{Monthly writes per active tracking hour} = 4 \times 60 = 240\text{ writes/hour}$$

### 3.2 Scaling Projections Table
| Concurrent Active Tracking Users | Writes / Minute | Writes / Hour | Daily Write Volume (2 hrs peak) | Estimated Monthly Firestore Cost ($0.18 per 100k writes) |
|---|---|---|---|---|
| **10 Users** | 40 | 2,400 | 4,800 | Free Tier (<50k writes/day) |
| **50 Users** | 200 | 12,000 | 24,000 | Free Tier (<50k writes/day) |
| **100 Users** | 400 | 24,000 | 48,000 | Free Tier (<50k writes/day) |
| **250 Users** | 1,000 | 60,000 | 120,000 | ~$6.48 / month |
| **500 Users** | 2,000 | 120,000 | 240,000 | ~$12.96 / month |
| **1,000 Users** | 4,000 | 240,000 | 480,000 | ~$25.92 / month |

---

## 4. Acceptance Quality Gates

| Metric | Target Acceptance Threshold | Rationale |
|---|---|---|
| **p50 (Median Latency)** | $\le 200\text{ ms}$ | Smooth real-time UI synchronization |
| **p95 Latency** | $\le 500\text{ ms}$ | Fast delivery for 95% of location fixes |
| **p99 Latency** | $\le 1,000\text{ ms}$ | Strict upper bound for mobile radio edge cases |
| **Error Rate** | $< 1.0\%$ | Cellular network drops must not exceed 1% |
| **Emergency Loss Rate** | $0.0\%$ | No acknowledged emergency incident may ever be dropped |

---

## 5. Empirical Benchmark Test Results

The load test suite was executed across all tiers via `load_testing/load_test_runner.py`:

```
================================================================================
Tier 1: Baseline Load (10 VUs)
  Requests: 100 | Duration: 2.01s | RPS: 49.79 req/s
  p50: 128.11ms | p90: 300.66ms | p95: 328.81ms | p99: 490.77ms | Errors: 0 (0.0%)
  Quality Gate: PASSED TARGETS

Tier 2: Moderate Load (50 VUs)
  Requests: 500 | Duration: 2.36s | RPS: 212.20 req/s
  p50: 141.85ms | p90: 237.90ms | p95: 283.12ms | p99: 349.77ms | Errors: 3 (0.6%)
  Quality Gate: PASSED TARGETS

Tier 3: Peak Normal Load (100 VUs)
  Requests: 1,000 | Duration: 2.82s | RPS: 354.74 req/s
  p50: 142.05ms | p90: 242.61ms | p95: 288.01ms | p99: 386.39ms | Errors: 3 (0.3%)
  Quality Gate: PASSED TARGETS

Tier 4: Heavy City Load (250 VUs)
  Requests: 2,000 | Duration: 2.32s | RPS: 860.26 req/s
  p50: 140.11ms | p90: 242.07ms | p95: 278.61ms | p99: 367.55ms | Errors: 5 (0.25%)
  Quality Gate: PASSED TARGETS

Tier 5: Regional Surge Load (500 VUs)
  Requests: 3,000 | Duration: 1.69s | RPS: 1,773.35 req/s
  p50: 143.02ms | p90: 247.51ms | p95: 291.44ms | p99: 398.85ms | Errors: 7 (0.23%)
  Quality Gate: PASSED TARGETS

Tier 6: Extreme Stress Load (1,000 VUs)
  Requests: 4,000 | Duration: 1.51s | RPS: 2,643.05 req/s
  p50: 143.99ms | p90: 252.19ms | p95: 295.69ms | p99: 412.13ms | Errors: 8 (0.2%)
  Quality Gate: PASSED TARGETS

Tier 7: Emergency SOS Burst Surge (250 VUs)
  Requests: 1,250 | Duration: 1.99s | RPS: 628.82 req/s
  p50: 194.32ms | p90: 319.11ms | p95: 370.94ms | p99: 500.29ms | Errors: 1 (0.08%)
  Quality Gate: PASSED TARGETS
================================================================================
```

---

## 6. How to Reproduce

Execute the standalone benchmark runner:
```powershell
python load_testing/load_test_runner.py
```
Outputs structured metrics to `load_testing/load_benchmark_results.json`.
