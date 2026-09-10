import java.util.concurrent.CompletableFuture;

public class OrderService {

    // Flagged: chain.cancel(true) fires directly below, but the inner
    // future constructed inside the thenCompose lambda never hears
    // about it -- the downstream call keeps running and consuming
    // resources.
    public CompletableFuture<String> placeOrderUnsafe(CompletableFuture<String> validated, boolean timedOut) {
        CompletableFuture<String> chain = validated.thenCompose(orderId -> {
            return CompletableFuture.supplyAsync(() -> chargePaymentProvider(orderId));
        });
        if (timedOut) {
            chain.cancel(true);
        }
        return chain;
    }

    // Not flagged: the inner future is captured and cancellation is
    // forwarded to it directly via whenComplete, right inside the
    // thenCompose lambda.
    public CompletableFuture<String> placeOrderSafe(CompletableFuture<String> validated, boolean timedOut) {
        CompletableFuture<String> chain = validated.thenCompose(orderId -> {
            CompletableFuture<String> inner = CompletableFuture.supplyAsync(() -> chargePaymentProvider(orderId));
            inner.whenComplete((result, error) -> { /* no-op, just marks manual forwarding */ });
            return inner;
        });
        if (timedOut) {
            chain.cancel(true);
        }
        return chain;
    }

    private String chargePaymentProvider(String orderId) {
        return "charged:" + orderId;
    }
}
