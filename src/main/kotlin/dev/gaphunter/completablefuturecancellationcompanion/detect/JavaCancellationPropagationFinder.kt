package dev.gaphunter.completablefuturecancellationcompanion.detect

import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiCodeBlock
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import com.intellij.psi.PsiVariable
import dev.gaphunter.completablefuturecancellationcompanion.model.CancellationGapHit

/**
 * Finds a `.thenCompose(...)` chain whose RESULT future can be
 * cancelled (a `.cancel(true)` call found elsewhere in the same
 * method on the variable the chain is assigned to), where the lambda
 * passed to `.thenCompose` directly constructs and returns another
 * `CompletableFuture` (in the same method) with no evidence that
 * cancellation is ever forwarded to it -- orphaned work keeps running
 * and consuming resources after the caller believes it cancelled the
 * whole operation. A documented, real gap (Reactor Core, Failsafe,
 * OpenTelemetry Java instrumentation issues) since
 * `CompletableFuture.thenCompose` never propagates cancellation into
 * the inner stage on its own.
 *
 * **v0.1 scope, stated honestly:** only the pattern where the lambda's
 * body directly returns another `CompletableFuture` CONSTRUCTED in the
 * same method (`CompletableFuture.supplyAsync(...)`/`.runAsync(...)`/
 * `new CompletableFuture<>()`) -- never follows the cancellation
 * through a call to an external method that returns one. "Forwards
 * cancellation" is recognized by the presence of a `.whenComplete(`
 * call anywhere in the lambda's own text -- a real forwarding pattern
 * is assumed present rather than verified in depth, same "match a
 * known name" discipline used elsewhere in this catalog.
 */
object JavaCancellationPropagationFinder {

    fun findAll(file: PsiFile): List<CancellationGapHit> {
        val hits = mutableListOf<CancellationGapHit>()
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethod(method: PsiMethod) {
                super.visitMethod(method)
                hits += hitsForMethod(method)
            }
        })
        return hits
    }

    private fun hitsForMethod(method: PsiMethod): List<CancellationGapHit> {
        val body = method.body ?: return emptyList()
        val hits = mutableListOf<CancellationGapHit>()

        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                super.visitMethodCallExpression(call)
                if (call.methodExpression.referenceName != "thenCompose") return

                val lambda = call.argumentList.expressions.getOrNull(0) as? PsiLambdaExpression ?: return
                if (!directlyConstructsFuture(lambda)) return // not this v0.1's exact shape
                if (lambda.text.contains(".whenComplete(")) return // assumed to already forward cancellation

                val chainVariable = resolveAssignedVariable(call) ?: return
                if (!hasCancelCall(body, chainVariable)) return

                hits += CancellationGapHit(anchorOf(call.methodExpression))
            }
        })
        return hits
    }

    /** True when [lambda]'s body directly `return`s (expression form or a `return` statement) a `CompletableFuture.supplyAsync(...)`/`.runAsync(...)`/`new CompletableFuture<>()` construction. */
    private fun directlyConstructsFuture(lambda: PsiLambdaExpression): Boolean {
        // A block-bodied lambda's `.body` is a PsiCodeBlock -- neither
        // PsiExpression nor PsiStatement in this platform's PSI hierarchy
        // (confirmed the hard way: an earlier version of this `when` only
        // handled those two, silently treating every block-form lambda as
        // "doesn't construct a future" and never firing on it at all).
        val returnedExpr = when (val bodyElement = lambda.body) {
            is PsiExpression -> bodyElement
            is PsiExpressionStatement -> bodyElement.expression
            is PsiCodeBlock -> {
                var found: PsiExpression? = null
                bodyElement.accept(object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitReturnStatement(statement: PsiReturnStatement) {
                        if (found != null) return
                        super.visitReturnStatement(statement)
                        found = statement.returnValue
                    }
                })
                found
            }
            else -> null
        } ?: return false

        return when (returnedExpr) {
            is PsiMethodCallExpression -> {
                val name = returnedExpr.methodExpression.referenceName
                val qualifierText = returnedExpr.methodExpression.qualifierExpression?.text
                (name == "supplyAsync" || name == "runAsync") && qualifierText == "CompletableFuture"
            }
            is PsiNewExpression -> returnedExpr.classReference?.referenceName == "CompletableFuture"
            else -> false
        }
    }

    /** The variable a `.thenCompose(...)` call's result is assigned to -- either a new local variable's initializer, or the RHS of a plain assignment. */
    private fun resolveAssignedVariable(call: PsiMethodCallExpression): PsiVariable? {
        return when (val parent = call.parent) {
            is PsiVariable -> if (parent.initializer === call) parent else null
            is PsiAssignmentExpression -> {
                if (parent.rExpression !== call) return null
                (parent.lExpression as? PsiReferenceExpression)?.resolve() as? PsiVariable
            }
            else -> null
        }
    }

    private fun hasCancelCall(scope: PsiElement, variable: PsiVariable): Boolean {
        var found = false
        scope.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                if (found) return
                super.visitMethodCallExpression(call)
                if (call.methodExpression.referenceName != "cancel") return
                val qualifier = call.methodExpression.qualifierExpression as? PsiReferenceExpression ?: return
                if (qualifier.resolve() == variable) found = true
            }
        })
        return found
    }

    private fun anchorOf(methodExpr: PsiReferenceExpression): PsiElement = methodExpr.referenceNameElement ?: methodExpr
}
