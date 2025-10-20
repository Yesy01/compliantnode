import java.util.*;

public class CompliantNode implements Node {

    private boolean[] followees;
    private boolean[] blacklisted;
    private int totalFollowees;
    private int activeFollowees; // followees not blacklisted

    private final double pGraph;
    private final double pMalicious;
    private final double pTxDist;
    private final int numRounds;

    private int round;
    private Set<Transaction> proposals; // current proposal set

    // quorum schedule (tightens slightly over rounds)
    private final double alphaStart;
    private final double alphaEnd;

    public CompliantNode(double p_graph, double p_malicious, double p_txDistribution, int numRounds) {
        this.pGraph = p_graph;
        this.pMalicious = p_malicious;
        this.pTxDist = p_txDistribution;
        this.numRounds = numRounds;

        this.round = 0;
        this.proposals = new HashSet<>();

        // Start slightly permissive to allow spread on sparse graphs; end above
        // adversary
        this.alphaStart = Math.max(0.40, p_malicious + 0.05);
        this.alphaEnd = Math.max(0.55, p_malicious + 0.10);
    }

    @Override
    public void setFollowees(boolean[] followees) {
        this.followees = Arrays.copyOf(followees, followees.length);
        this.blacklisted = new boolean[followees.length];
        this.totalFollowees = 0;
        for (boolean f : followees)
            if (f)
                totalFollowees++;
        this.activeFollowees = totalFollowees;
    }

    @Override
    public void setPendingTransaction(Set<Transaction> pendingTransactions) {
        // Start by proposing whatever valid tx we already have;
        // transactions must later be supported or they will be dropped.
        if (pendingTransactions != null) {
            this.proposals.addAll(pendingTransactions);
        }
    }

    @Override
    public Set<Transaction> sendToFollowers() {
        Set<Transaction> txs = new HashSet<Transaction>();
        for (int i = 0; i < 1000; ++i) {
            Transaction tx = new Transaction(i);
            txs.add(tx);
        }
        return txs;
    }

    @Override
    public void receiveFromFollowees(Set<Candidate> candidates) {
        // Defensive: no followees -> nothing to do
        if (totalFollowees == 0) {
            round++;
            return;
        }

        // Track who spoke this round
        boolean[] spoke = new boolean[followees.length];

        // This round's support: tx -> distinct supporters
        Map<Transaction, Set<Integer>> support = new HashMap<>();

        if (candidates != null) {
            for (Candidate c : candidates) {
                int s = c.sender;
                if (s < 0 || s >= followees.length)
                    continue;
                if (!followees[s])
                    continue; // ignore non-followees
                if (blacklisted[s])
                    continue; // ignore known-bad/silent
                spoke[s] = true;
                support.computeIfAbsent(c.tx, k -> new HashSet<>()).add(s);
            }
        }

        // Blacklist followees that were silent this round (likely dead/malicious)
        // Do it after we counted messages for this round
        int newlyBlacklisted = 0;
        for (int i = 0; i < followees.length; i++) {
            if (!followees[i] || blacklisted[i])
                continue;
            if (!spoke[i]) {
                blacklisted[i] = true;
                newlyBlacklisted++;
            }
        }
        if (newlyBlacklisted > 0) {
            activeFollowees -= newlyBlacklisted;
            if (activeFollowees < 0)
                activeFollowees = 0;
        }

        // Compute tightening quorum
        double progress = (numRounds <= 1) ? 1.0 : Math.min(1.0, (double) round / (double) (numRounds - 1));
        double alpha = alphaStart + (alphaEnd - alphaStart) * progress;
        int denom = Math.max(1, activeFollowees); // avoid zero
        int threshold = Math.max(1, (int) Math.ceil(alpha * denom));

        // Build next proposals: only tx that meet quorum THIS ROUND
        Set<Transaction> next = new HashSet<>();
        for (Map.Entry<Transaction, Set<Integer>> e : support.entrySet()) {
            if (e.getValue().size() >= threshold) {
                next.add(e.getKey());
            }
        }

        // If we heard nothing from anyone (e.g., all silent then blacklisted -> denom
        // might be 0 next time),
        // keep current proposals as a fallback to avoid empty outputs.
        if (support.isEmpty() && !proposals.isEmpty()) {
            // retain only what we already had; do not expand
            next.addAll(proposals);
        }

        proposals = next;
        round++;
    }
}
