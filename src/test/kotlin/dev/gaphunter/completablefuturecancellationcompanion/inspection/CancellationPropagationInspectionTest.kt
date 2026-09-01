package dev.gaphunter.completablefuturecancellationcompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class CancellationPropagationInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(CancellationPropagationInspection::class.java)
    }

    fun `test thenCompose with a directly-constructed inner future and a later cancel is flagged`() {
        myFixture.configureByText(
            "Service.java",
            """
            import java.util.concurrent.CompletableFuture;

            class Service {
                void run(CompletableFuture<String> outer) {
                    CompletableFuture<String> chain = outer.thenCompose(x -> {
                        return CompletableFuture.supplyAsync(() -> x + "!");
                    });
                    chain.cancel(true);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("never forwards that cancellation") == true })
    }

    fun `test expression-form lambda directly returning supplyAsync is also flagged`() {
        myFixture.configureByText(
            "Service2.java",
            """
            import java.util.concurrent.CompletableFuture;

            class Service2 {
                void run(CompletableFuture<String> outer) {
                    CompletableFuture<String> chain = outer.thenCompose(x -> CompletableFuture.supplyAsync(() -> x + "!"));
                    chain.cancel(true);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("never forwards that cancellation") == true })
    }

    fun `test a lambda that forwards cancellation via whenComplete is not flagged`() {
        myFixture.configureByText(
            "Service3.java",
            """
            import java.util.concurrent.CompletableFuture;

            class Service3 {
                void run(CompletableFuture<String> outer) {
                    CompletableFuture<String> chain = outer.thenCompose(x -> {
                        CompletableFuture<String> inner = CompletableFuture.supplyAsync(() -> x + "!");
                        inner.whenComplete((r, e) -> {});
                        return inner;
                    });
                    chain.cancel(true);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("never forwards that cancellation") == true })
    }

    fun `test no cancel call anywhere means no warning`() {
        myFixture.configureByText(
            "Service4.java",
            """
            import java.util.concurrent.CompletableFuture;

            class Service4 {
                void run(CompletableFuture<String> outer) {
                    CompletableFuture<String> chain = outer.thenCompose(x -> {
                        return CompletableFuture.supplyAsync(() -> x + "!");
                    });
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("never forwards that cancellation") == true })
    }

    fun `test a lambda that does not construct a new future at all is not flagged`() {
        myFixture.configureByText(
            "Service5.java",
            """
            import java.util.concurrent.CompletableFuture;

            class Service5 {
                CompletableFuture<String> helper(String x) { return null; }

                void run(CompletableFuture<String> outer) {
                    CompletableFuture<String> chain = outer.thenCompose(x -> helper(x));
                    chain.cancel(true);
                }
            }
            """.trimIndent(),
        )
        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("never forwards that cancellation") == true })
    }
}
