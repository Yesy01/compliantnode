import java.util.*;
import java.util.stream.Collectors;

public class TestHarness {

    // Simple malicious behavior for testing: sends either nothing or random noise txs.
    static class SimpleMaliciousNode implements Node {
        private boolean[] followees;
        private Random rng = new Random(42);
        private int round = 0;

        @Override
        public void setFollowees(boolean[] followees) {
            this.followees = Arrays.copyOf(followees, followees.length);
        }

        @Override
        public void setPendingTransaction(Set<Transaction> pendingTransactions) {
            // ignores pending set
        }

        @Override
        public Set<Transaction> sendToFollowers() {
            // alternate between spamming and silence
            if ((round % 2) == 0) {
                // spam some fresh, likely-unique tx IDs (int range)
                Set<Transaction> out = new HashSet<>();
                for (int i = 0; i < 3; i++) {
                    int id = 1_000_000 + rng.nextInt(1_000_000);
                    out.add(new Transaction(id));
                }
                return out;
            } else {
                return new HashSet<>();
            }
        }

        @Override
        public void receiveFromFollowees(Set<Candidate> candidates) {
            round++;
        }
    }

    public static void main(String[] args) {
        final int N = 30;
        final double pGraph = 0.2;
        final double pMal = 0.30;
        final double pTx = 0.05;
        final int numRounds = 15;

        int maliciousCount = (int) Math.floor(pMal * N);
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            boolean malicious = (i < maliciousCount);
            if (malicious) nodes.add(new SimpleMaliciousNode());
            else nodes.add(new CompliantNode(pGraph, pMal, pTx, numRounds));
        }

        // Build random directed graph: follow[j][i] = true means node i follows node j
        boolean[][] follow = new boolean[N][N];
        Random rng = new Random(7);
        for (int i = 0; i < N; i++) {
            for (int j = 0; j < N; j++) {
                if (i == j) continue;
                if (rng.nextDouble() < pGraph) {
                    follow[j][i] = true; // edge j -> i
                }
            }
        }
        // Provide followee sets to nodes
        for (int i = 0; i < N; i++) {
            boolean[] followees = new boolean[N];
            for (int j = 0; j < N; j++) followees[j] = follow[j][i];
            nodes.get(i).setFollowees(followees);
        }

        // Seed initial valid transactions randomly across nodes
        int seedCount = Math.max(1, (int)Math.round(pTx * 1000));
        List<Transaction> seedTxs = new ArrayList<>();
        for (int k = 0; k < seedCount; k++) seedTxs.add(new Transaction(k+1));
        for (int i = 0; i < N; i++) {
            Set<Transaction> init = new HashSet<>();
            if (rng.nextDouble() < 0.8) { // most nodes get a subset
                int take = 1 + rng.nextInt(Math.max(1, seedCount/10 + 1));
                for (int t = 0; t < take; t++) {
                    init.add(seedTxs.get(rng.nextInt(seedTxs.size())));
                }
            }
            nodes.get(i).setPendingTransaction(init);
        }

        // Run the simulation for numRounds
        for (int r = 0; r < numRounds; r++) {
            // collect proposals from all nodes
            List<Set<Transaction>> proposals = new ArrayList<>(N);
            for (int i = 0; i < N; i++) {
                proposals.add(nodes.get(i).sendToFollowers());
            }
            // deliver to followers
            for (int i = 0; i < N; i++) {
                Set<Candidate> inbox = new HashSet<>();
                for (int j = 0; j < N; j++) {
                    if (follow[j][i]) {
                        for (Transaction tx : proposals.get(j)) {
                            inbox.add(new Candidate(tx, j));
                        }
                    }
                }
                nodes.get(i).receiveFromFollowees(inbox);
            }
        }

        // Final outputs
        List<Set<Transaction>> finalSets = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            finalSets.add(nodes.get(i).sendToFollowers());
        }

        // Evaluate only compliant nodes
        List<Set<Transaction>> compliantSets = new ArrayList<>();
        for (int i = maliciousCount; i < N; i++) compliantSets.add(finalSets.get(i));

        // 1) Check if a majority of compliant nodes agree exactly
        Map<String, Integer> freq = new HashMap<>();
        for (Set<Transaction> s : compliantSets) {
            String key = canonicalKey(s);
            freq.put(key, freq.getOrDefault(key, 0) + 1);
        }
        int bestCluster = freq.values().stream().max(Integer::compareTo).orElse(0);
        boolean majorityAgree = bestCluster >= Math.ceil(0.75 * compliantSets.size());

        // 2) Non-empty agreed set (liveness, given seeds)
        String bestKey = freq.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
        Set<Integer> agreedIds = parseKey(bestKey);
        boolean nonEmpty = !agreedIds.isEmpty();

        // 3) Sanity: agreed tx were part of the original valid seeds or gossiped (valid by construction)
        Set<Integer> seedIds = seedTxs.stream().map(t -> t.id).collect(Collectors.toSet());
        boolean allValid = seedIds.containsAll(agreedIds);

        System.out.println("Compliant nodes: " + (N - maliciousCount));
        System.out.println("Largest agreeing cluster size: " + bestCluster + " / " + compliantSets.size());
        System.out.println("Agreed tx count: " + agreedIds.size());
        System.out.println("Checks: majorityAgree=" + majorityAgree + ", nonEmpty=" + nonEmpty + ", allValid=" + allValid);

        if (majorityAgree && nonEmpty && allValid) {
            System.out.println("[PASS] Requirements satisfied under this scenario.");
            System.exit(0);
        } else {
            System.out.println("[FAIL] One or more requirements not met. Inspect node logic or parameters.");
            System.exit(1);
        }
    }

    private static String canonicalKey(Set<Transaction> s) {
        List<Integer> ids = s.stream().map(t -> t.id).sorted().collect(Collectors.toList());
        return ids.toString();
    }
    private static Set<Integer> parseKey(String k) {
        Set<Integer> out = new HashSet<>();
        if (k == null || k.length() < 2) return out;
        String inner = k.substring(1, k.length()-1).trim();
        if (inner.isEmpty()) return out;
        for (String p : inner.split(",")) {
            out.add(Integer.parseInt(p.trim()));
        }
        return out;
    }
}
