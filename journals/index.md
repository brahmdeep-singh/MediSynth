# MediSynth Team Journals

Group 3C55, UCS503 Software Engineering. Instructor: Ms. Nisha Thakur.

- [Aryan Sharma (1024030787)](aryan/index.md)
- [Brahmdeep Singh Khanuja (1024030792)](brahmdeep/index.md)
- [Keerat Singh Brar (1024030793)](keerat/index.md)

## Division of work

| Area | Aryan | Brahmdeep | Keerat |
|---|---|---|---|
| Requirements and proposal | review | **owner** | review |
| UML and data-flow diagrams | review | **owner** | review |
| Backend API, security, database | integration testing | schema review | **owner** |
| Synthetix generator (code) | review | review | **owner** |
| Synthetix evaluation design and calibration | review | **owner** | code in `DatasetEvaluator` |
| React frontend | **owner** | review | review |
| Frontend to backend integration | **owner** | | API contract |
| Dataset screens and CSV download | **owner** | | endpoints |
| Test-case design and execution | **executes** | documents | automated tests |
| Reports (mid-sem, end-sem) | review | **owner** | technical content |

## Where the work meets

- **API contract:** Keerat's `docs/API.md` is what Aryan's `services/api.js` calls.
- **Synthetix gates:** Brahmdeep sets the thresholds; Keerat implements them in the evaluator; Aryan shows the report in `QualityReport`.
- **Report:** Brahmdeep writes it from Keerat's code and Aryan's screens.
