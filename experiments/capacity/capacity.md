# Capacity experiment

SUT limits: 2.0 CPUs, 1.0 GiB; 60 s per level. A level keeps up when errors < 1.0 %, p95 <= 800 ms and completed flows >= 95 % of the offered rate.

## full flow

Capacity per runner: 0, 0, 0 flows/s (median 0, range 0–0).

| offered flows/s | runners | median p95 ms | median p99 ms | median errors % | median completed/s | median SUT CPU % | kept up |
|---:|---:|---:|---:|---:|---:|---:|---|
| 5 | 3 | 83.0 | 120.0 | None | 5.0 | 60.6 | 0 of 3 |
| 10 | 3 | 102.0 | 199.0 | None | 10.0 | 79.2 | 0 of 3 |

## authenticated flow

Capacity per runner: 0, 0, 0 flows/s (median 0, range 0–0).

| offered flows/s | runners | median p95 ms | median p99 ms | median errors % | median completed/s | median SUT CPU % | kept up |
|---:|---:|---:|---:|---:|---:|---:|---|
| 10 | 3 | 7.0 | 11.0 | None | 10.0 | 25.8 | 0 of 3 |
| 20 | 3 | 4.0 | 6.0 | None | 0.0 | 26.4 | 0 of 3 |

