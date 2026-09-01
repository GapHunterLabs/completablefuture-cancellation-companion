import java.util.concurrent.CompletableFuture;

public class OrderService {

    // Flagged: chain.cancel(true) can fire, but the inner future
    // constructed inside the thenCompose lambda never hears about it --
    // the downstream call keeps running and consuming resources.
    public CompletableFuture<String> placeOrderUnsafe(CompletableFuture<String> validated) {
        CompletableFuture<String> chain = validated.thenCompose(orderId -> {
            return CompletableFuture.supplyAsync(() -> chargePaymentProvider(orderId));
        });
        registerTimeout(chain);
        return chain;
    }

    // Not flagged: the inner future is captured and cancellation is
    // forwarded to it by hand via whenComplete.
    public CompletableFuture<String> placeOrderSafe(CompletableFuture<String> validated) {
        CompletableFuture<String> chain = validated.thenCompose(orderId -> {
            CompletableFuture<String> inner = CompletableFuture.supplyAsync(() -> chargePaymentProvider(orderId));
            chain(inner);
            return inner;
        });
        registerTimeout(chain);
        return chain;
    }

    private void chain(CompletableFuture<String> inner) {
        inner.whenComplete((result, error) -> { /* no-op, just marks manual forwarding */ });
    }

    private void registerTimeout(CompletableFuture<String> future) {
        // Elsewhere in the real codebase: a timeout scheduler calls
        // future.cancel(true) if the order takes too long.
        future.cancel(true);
    }

    private String chargePaymentProvider(String orderId) {
        return "charged:" + orderId;
    }
}
