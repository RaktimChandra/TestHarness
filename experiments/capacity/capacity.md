# Capacity experiment

SUT limits: 2.0 CPUs, 1.0 GiB; 60 s per level. A level keeps up when errors < 1.0 %, p95 <= 800 ms and completed flows >= 95 % of the offered rate.

## full flow

Capacity per runner: 25, 30, 20 flows/s (median 25, range 20–30).

| offered flows/s | runners | median p95 ms | median p99 ms | median errors % | median completed/s | median SUT CPU % | kept up |
|---:|---:|---:|---:|---:|---:|---:|---|
| 5 | 3 | 70.0 | 92.0 | 0.0 | 5.0 | 45.0 | 3 of 3 |
| 10 | 3 | 78.0 | 122.0 | 0.0 | 10.0 | 69.4 | 3 of 3 |
| 15 | 3 | 125.0 | 207.0 | 0.0 | 15.0 | 98.9 | 3 of 3 |
| 20 | 3 | 200.0 | 320.0 | 0.0 | 20.0 | 130.7 | 3 of 3 |
| 25 | 3 | 407.0 | 557.0 | 0.0 | 25.0 | 152.0 | 2 of 3 |
| 30 | 3 | 7295.0 | 7617.0 | 0.0 | 30.0 | 165.3 | 1 of 3 |
| 40 | 2 | 13712.0 | 14496.0 | 0.4 | 39.3 | 169.8 | 0 of 2 |
| 50 | 1 | 18773.0 | 19848.0 | 1.6 | 46.3 | 179.4 | 0 of 1 |

## authenticated flow

Capacity per runner: 200, 200, 200 flows/s (median 200, range 200–200).

| offered flows/s | runners | median p95 ms | median p99 ms | median errors % | median completed/s | median SUT CPU % | kept up |
|---:|---:|---:|---:|---:|---:|---:|---|
| 10 | 3 | 6.0 | 10.0 | 0.0 | 10.0 | 24.9 | 3 of 3 |
| 20 | 3 | 4.0 | 5.0 | 0.0 | 20.0 | 28.7 | 3 of 3 |
| 40 | 3 | 3.0 | 5.0 | 0.0 | 40.0 | 26.1 | 3 of 3 |
| 60 | 3 | 4.0 | 6.0 | 0.0 | 60.0 | 31.8 | 3 of 3 |
| 80 | 3 | 5.0 | 7.0 | 0.0 | 80.0 | 34.0 | 3 of 3 |
| 100 | 3 | 4.0 | 8.0 | 0.0 | 100.0 | 42.1 | 3 of 3 |
| 120 | 3 | 4.0 | 6.0 | 0.0 | 120.0 | 45.3 | 3 of 3 |
| 160 | 3 | 5.0 | 36.0 | 0.0 | 160.0 | 57.7 | 3 of 3 |
| 200 | 3 | 13.0 | 68.0 | 0.0 | 200.0 | 71.2 | 3 of 3 |
| 240 | 3 | 60000.0 | 60001.0 | 30.6 | 103.0 | 96.9 | 0 of 3 |

