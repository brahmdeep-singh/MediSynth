# Brahmdeep's Journal

Roll No. 1024030792

Name: Brahmdeep Singh Khanuja

Design and documentation track: requirements, UML and data-flow diagrams, Synthetix evaluation, and the reports.

---

## Week 1 — Phase 1: Requirements and project proposal

**Status:** Complete

### What I did
- Wrote the project proposal: problem statement, objectives, actors (patient, doctor, lab technician, researcher, administrator) and the main functional requirements.
- Agreed the split of work with Aryan and Keerat (see `docs/journals/index.md`): I take requirements, diagrams, the Synthetix evaluation design and the reports.

### Key decisions & reasoning
- **Decision:** State measurable objectives (a dataset of 10,000 records that passes a quality check) rather than "build a good system".
  **Why:** The evaluators can check a number; they cannot check an adjective.

### Next steps
- Use case and activity diagrams.

---

## Week 2 — Phase 2: Use case, class and activity diagrams

**Status:** Complete

### What I did
- Drew the use case diagram, the class diagram, and two activity diagrams in draw.io: the patient / hospital workflow and the Synthetix workflow.
- Drew two swimlane diagrams for the same two workflows.

### Key decisions & reasoning
- **Decision:** Two workflows (hospital and Synthetix), each shown as an activity diagram and a swimlane diagram.
  **Why:** The project has two separate stories, and mixing them made the first draft unreadable.

### Next steps
- Data-flow and interaction diagrams.

---

## Week 3 — Phase 3: Data-flow and interaction diagrams

**Status:** Complete

### What I did
- Level 0 and Level 1 data-flow diagrams; sequence diagrams for booking an appointment and generating a synthetic dataset; the matching collaboration diagrams.
- State diagrams for the appointment, the bill and payment, and the synthetic dataset.
- Component diagram, and later a deployment diagram.

### Challenges & how I solved them
- Several draw.io exports had cut-off labels in the report. I re-exported them as PDF and sized each one in the report by page orientation.

### Next steps
- Mid-semester report.

---

## Week 5 — Phase 4: Mid-semester report (LaTeX)

**Status:** Complete

### What I did
- Wrote the introduction, objectives, architecture, stack and Module 1 sections on the TIET report template.
- Put all diagrams into one chapter, with wide ones on landscape pages.

### Key decisions & reasoning
- **Decision:** Keep the bibliography block commented out until there are real references.
  **Why:** An empty bibliography looks worse than none.

### Challenges & how I solved them
- The mid-semester report still mentioned a mobile app in three places, which is no longer planned. Corrected in the final report.

### Next steps
- Define how Synthetix data will be judged.

---

## Week 9 — Phase 5: Synthetix evaluation design and calibration

**Status:** Complete

### What I did
- Defined the scoring: overall = 0.35 marginal fidelity + 0.20 clinical relationships + 0.15 validity + 0.30 privacy.
- Defined the release gates: overall at least 0.90, validity at least 0.999, at least 95% of records in k-anonymous groups (k=5 on age band, gender, diagnosis), at most 1% unique, no duplicate rows, and at least 6 of 8 relationship checks.
- Ran the engine standalone on 500, 2,000, 5,000 and 10,000 records with three seeds each to calibrate the gates. 500 and 2,000 fail privacy; 5,000 (about 0.976) and 10,000 (about 0.983) pass.

### Key decisions & reasoning
- **Decision:** Privacy has the second-largest weight and its own hard gates.
  **Why:** An average score must not be able to hide a privacy failure. At 2,000 records the overall score is 0.951 and the dataset is still rejected.
- **Decision:** Relaxed the unique-share gate from 0.5% to 1%.
  **Why:** 5,000 records sat at 0.58%; the stricter value rejected a dataset that was otherwise sound, and 1% is still a tight limit.

### Next steps
- Prepare the test-case documents.

---

## Week 10 — Phase 6: Test-case documentation

**Status:** Complete

### What I did
- Prepared the four test cases in the course template, with a description, preconditions, steps, expected results and comments, and one PDF per test case.

### Next steps
- End-semester report.

---

## Week 11 — Phase 7: End-semester report

**Status:** Complete

### What I did
- Extended the report to the End-Semester Evaluation: scope and status table, security design, database design, all modules, the Synthetix method, the results chapter with the measured evaluator tables, analysis, limitations and conclusion.
- Drew an as-built deployment diagram and added a note under the component diagram, because it still shows the earlier Python engine while the final engine is Java inside the backend.
- Added the test-case sheets as an appendix and fixed a blank page that came from the note pushing the component diagram off its page.

### Key decisions & reasoning
- **Decision:** State plainly that the generator is rule-based and not a model of real patients, and that prescriptions, pharmacy and billing are demo-only.
  **Why:** An honest limitations section is safer in a viva than an overstated claim.

### Next steps
- Final proofread of the PDF built with the real template class.
