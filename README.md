# Compliant Node Consensus –

This project implements a **CompliantNode** for a blockchain-style consensus simulation over a random **trust graph**. Nodes repeatedly share proposed transactions with their followers; compliant nodes must converge on a common set of transactions despite up to ~45% malicious participants.

## Contents

```
CompliantNode.java     # Node implementation (per-round quorum; handles silent followees)
TestHarness.java       # Local simulation to sanity-check agreement & liveness (independent of course sim)
Node.java              # Provided interface (from course)
Candidate.java         # Provided (tx, sender) wrapper (from course)
Transaction.java       # Provided transaction class (from course)
Simulation.java        # Course simulator (may reference additional malicious nodes)
```

## Build & Run

### Local Harness (quick check)
```bash
# Default package example
javac Node.java Candidate.java Transaction.java CompliantNode.java TestHarness.java
java TestHarness
```
**Pass criteria in harness:**
- Majority (ideally all) compliant nodes agree on exactly the same final set.
- Final set is non-empty (given seeds).
- Final set contains only transactions that originated from the seeded pool in this local test.


## Algorithm (CompliantNode)

- **Inputs**: set of followees, initial pending transactions, round count.
- **Per-Round Behavior**:
  1. Gather `(tx, sender)` candidates **only** from configured followees.
  2. Track which followees *spoke* this round; **silence detection** is used to quarantine persistently silent followees so they do not hold up quorum.
  3. Compute a **per-round quorum**: `threshold = ceil(alpha * activeFollowees)` where `alpha` gradually increases from a permissive start (for liveness on sparse graphs) to a safer end value (for agreement under adversaries).
  4. Next proposal set = { tx | support_this_round(tx) ≥ threshold }`. Transactions lacking current support drop out—this drives convergence towards commonly supported items.
  5. Edge case: if nothing is heard in a round, keep existing proposals to avoid an accidental empty result.
- **Final Output**: After the last round, `sendToFollowers()` returns the current proposal set as the node’s final “consensus set”.

### Tuning Knobs

- **Alpha schedule**: `alphaStart ~ 0.40`, `alphaEnd ≥ max(0.55, p_malicious + 0.10)`. Raise `alphaEnd` to prefer safety; lower `alphaStart` *slightly* on extremely sparse graphs for liveness.
- **Silence handling**: basic quarantine of silent followees helps ignore dead/malicious nodes. If your course sim is very sparse, consider requiring *consecutive* silence before quarantine.

## License

For course/educational use.
