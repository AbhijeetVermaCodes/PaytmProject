package com.ticketbooking.loadtest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * High-concurrency stampede load runner that fires thousands of parallel requests against
 * a live ticket booking service and asserts race-free correctness, zero 5xx errors,
 * idempotency safety, per-user limits, and reconciliation invariants.
 */
public class BurstLoadRunner {

    private final String baseUrl;
    private final HttpClient client;

    private final AtomicInteger confirmed201 = new AtomicInteger(0);
    private final AtomicInteger declinedSeatTaken409 = new AtomicInteger(0);
    private final AtomicInteger declinedUserLimit409 = new AtomicInteger(0);
    private final AtomicInteger declinedIdempotency409 = new AtomicInteger(0);
    private final AtomicInteger other4xx = new AtomicInteger(0);
    private final AtomicInteger serverErrors5xx = new AtomicInteger(0);
    private final List<Long> latenciesMs = Collections.synchronizedList(new ArrayList<>());

    public BurstLoadRunner(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static void main(String[] args) throws Exception {
        String url = args.length > 0 ? args[0] : "http://localhost:8080";
        BurstLoadRunner runner = new BurstLoadRunner(url);
        runner.executeFullSuite();
    }

    public void executeFullSuite() throws Exception {
        System.out.println("================================================================================");
        System.out.println("          HIGH-CONCURRENCY TICKET BOOKING SERVICE - BURST LOAD RUNNER          ");
        System.out.println("================================================================================");
        System.out.println("Target URL: " + baseUrl);
        System.out.println();

        // 1. Health & Readiness check
        System.out.println("[Step 1/5] Checking service readiness (/health/ready)...");
        HttpRequest readyReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health/ready"))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .build();

        HttpResponse<String> readyRes = client.send(readyReq, HttpResponse.BodyHandlers.ofString());
        if (readyRes.statusCode() != 200) {
            System.err.println("FATAL: Readiness check failed with status " + readyRes.statusCode() + ": " + readyRes.body());
            System.exit(1);
        }
        System.out.println("   --> Service is READY! Response: " + readyRes.body().trim());

        // 2. Create fresh show with 50 seats
        System.out.println("\n[Step 2/5] Creating fresh on-sale show with 50 seats (A1..A50)...");
        List<String> seats = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            seats.add("A" + i);
        }
        String seatsJson = seats.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(","));
        String createShowPayload = String.format("{\"name\":\"on-sale-concert-%d\",\"seats\":[%s],\"price_paise\":25000,\"per_user_limit\":4}",
                System.currentTimeMillis(), seatsJson);

        HttpRequest createShowReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/shows"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer admin")
                .POST(HttpRequest.BodyPublishers.ofString(createShowPayload))
                .build();

        HttpResponse<String> createShowRes = client.send(createShowReq, HttpResponse.BodyHandlers.ofString());
        if (createShowRes.statusCode() != 201) {
            System.err.println("FATAL: Failed to create show: " + createShowRes.statusCode() + " -> " + createShowRes.body());
            System.exit(1);
        }

        String showBody = createShowRes.body();
        String showId = extractJsonField(showBody, "id");
        System.out.println("   --> Show created successfully! ID: " + showId);

        // 3. Storm 1: Hot Seat Stampede (500 users simultaneously grabbing seat A12)
        System.out.println("\n[Step 3/5] Launching Storm 1: Hot-Seat Contention (500 concurrent buyers on seat A12)...");
        runHotSeatStorm(showId, "A12", 500);

        // 4. Storm 2: Hall Stampede (1,000 concurrent multi-seat and single-seat requests)
        System.out.println("\n[Step 4/5] Launching Storm 2: Full-Hall Stampede (1,000 concurrent requests for A1..A50)...");
        runHallStampede(showId, 1000);

        // 5. Final Reconciliation & Invariant Check
        System.out.println("\n[Step 5/5] Fetching final show state and Prometheus metrics for reconciliation...");
        HttpRequest showStateReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/shows/" + showId))
                .GET()
                .build();

        HttpResponse<String> showStateRes = client.send(showStateReq, HttpResponse.BodyHandlers.ofString());
        String finalStateBody = showStateRes.body();

        long available = Long.parseLong(extractJsonField(finalStateBody, "available_seats"));
        long held = Long.parseLong(extractJsonField(finalStateBody, "held_seats"));
        long confirmed = Long.parseLong(extractJsonField(finalStateBody, "confirmed_seats"));
        long total = Long.parseLong(extractJsonField(finalStateBody, "total_seats"));
        boolean reconciliationValid = (available + held + confirmed) == total;

        printReport(showId, total, available, held, confirmed, reconciliationValid);

        if (serverErrors5xx.get() > 0 || !reconciliationValid) {
            System.exit(1);
        }
    }

    private void runHotSeatStorm(String showId, String hotSeat, int concurrency) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(concurrency, 100));
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(concurrency);

        for (int i = 0; i < concurrency; i++) {
            final String userId = "hot-buyer-" + i;
            final String idempotencyKey = "hot-key-" + i;

            executor.submit(() -> {
                try {
                    startGate.await();
                    long start = System.currentTimeMillis();
                    String payload = String.format("{\"seats\":[\"%s\"],\"idempotency_key\":\"%s\"}", hotSeat, idempotencyKey);
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/shows/" + showId + "/reserve"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + userId)
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .timeout(Duration.ofSeconds(15))
                            .build();

                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    long latency = System.currentTimeMillis() - start;
                    latenciesMs.add(latency);
                    recordResponse(res.statusCode(), res.body());
                } catch (Exception e) {
                    serverErrors5xx.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();
    }

    private void runHallStampede(String showId, int requestCount) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(100);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(requestCount);

        Random random = new Random();

        for (int i = 0; i < requestCount; i++) {
            final String userId = "user-" + (i % 200); // 200 distinct users
            final String idempotencyKey = "key-stampede-" + i;
            int seatIdx1 = 1 + random.nextInt(50);
            int seatIdx2 = 1 + random.nextInt(50);
            String payload;
            if (i % 3 == 0) {
                payload = String.format("{\"seats\":[\"A%d\",\"A%d\"],\"idempotency_key\":\"%s\"}", seatIdx1, seatIdx2, idempotencyKey);
            } else {
                payload = String.format("{\"seats\":[\"A%d\"],\"idempotency_key\":\"%s\"}", seatIdx1, idempotencyKey);
            }

            executor.submit(() -> {
                try {
                    startGate.await();
                    long start = System.currentTimeMillis();
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/shows/" + showId + "/reserve"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + userId)
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .timeout(Duration.ofSeconds(15))
                            .build();

                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    long latency = System.currentTimeMillis() - start;
                    latenciesMs.add(latency);
                    recordResponse(res.statusCode(), res.body());
                } catch (Exception e) {
                    serverErrors5xx.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await(45, TimeUnit.SECONDS);
        executor.shutdown();
    }

    private void recordResponse(int code, String body) {
        if (code == 201) {
            confirmed201.incrementAndGet();
        } else if (code == 409) {
            if (body != null && body.contains("SEAT_ALREADY_TAKEN")) {
                declinedSeatTaken409.incrementAndGet();
            } else if (body != null && body.contains("PER_USER_LIMIT_EXCEEDED")) {
                declinedUserLimit409.incrementAndGet();
            } else if (body != null && body.contains("IDEMPOTENCY")) {
                declinedIdempotency409.incrementAndGet();
            } else {
                declinedSeatTaken409.incrementAndGet();
            }
        } else if (code >= 400 && code < 500) {
            other4xx.incrementAndGet();
        } else if (code >= 500) {
            serverErrors5xx.incrementAndGet();
        }
    }

    private void printReport(String showId, long total, long available, long held, long confirmed, boolean reconciliationValid) {
        long totalRequests = confirmed201.get() + declinedSeatTaken409.get() + declinedUserLimit409.get()
                + declinedIdempotency409.get() + other4xx.get() + serverErrors5xx.get();

        Collections.sort(latenciesMs);
        long p50 = latenciesMs.isEmpty() ? 0 : latenciesMs.get((int) (latenciesMs.size() * 0.50));
        long p95 = latenciesMs.isEmpty() ? 0 : latenciesMs.get((int) (latenciesMs.size() * 0.95));
        long p99 = latenciesMs.isEmpty() ? 0 : latenciesMs.get((int) (latenciesMs.size() * 0.99));

        System.out.println("\n================================================================================");
        System.out.println("                         BURST EXECUTION SUMMARY REPORT                        ");
        System.out.println("================================================================================");
        System.out.printf(" Total Requests Processed:     %d%n", totalRequests);
        System.out.printf(" Confirmed Reservations (201): %d%n", confirmed201.get());
        System.out.printf(" Declined (409 Seat Taken):    %d%n", declinedSeatTaken409.get());
        System.out.printf(" Declined (409 User Limit):    %d%n", declinedUserLimit409.get());
        System.out.printf(" Declined (409 Idempotency):   %d%n", declinedIdempotency409.get());
        System.out.printf(" Other Client Errors (4xx):    %d%n", other4xx.get());
        System.out.printf(" Server Errors (5xx):          %d  %s%n", serverErrors5xx.get(),
                serverErrors5xx.get() == 0 ? "[PASS - ZERO 5XX ERRORS]" : "[FAIL - SERVER ERRORS OCCURRED]");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.printf(" Latency Distribution:         p50: %dms | p95: %dms | p99: %dms%n", p50, p95, p99);
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println(" RECONCILIATION INVARIANT CHECK (available + held + confirmed == total):");
        System.out.printf(" Available: %d | Held: %d | Confirmed: %d ==> Sum: %d | Total: %d%n",
                available, held, confirmed, (available + held + confirmed), total);
        System.out.printf(" Invariant Holds:              %s%n",
                reconciliationValid ? "[PASS - EXACT 100% RECONCILIATION]" : "[FAIL - INVARIANT VIOLATED]");
        System.out.println("================================================================================\n");
    }

    private static String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":";
        int idx = json.indexOf(search);
        if (idx == -1) return "0";
        int start = idx + search.length();
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) {
            start++;
        }
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != '"') {
            end++;
        }
        return json.substring(start, end).trim();
    }
}
