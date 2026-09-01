package dev.gaphunter.completablefuturecancellationcompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import dev.gaphunter.completablefuturecancellationcompanion.detect.JavaCancellationPropagationFinder
import dev.gaphunter.completablefuturecancellationcompanion.review.ReviewPrompt

/**
 * Flags a `.thenCompose(...)` chain whose result future can be
 * cancelled, but whose inner future (constructed directly in the
 * lambda) never forwards that cancellation -- orphaned work keeps
 * running and consuming resources after the caller believes it
 * cancelled the whole operation.
 *
 * Runs via `checkFile` (same shape as every other inspection in this
 * catalog); [JavaCancellationPropagationFinder] does the real PSI
 * walk.
 */
class CancellationPropagationInspection : LocalInspectionTool() {

    companion object {
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        if (file.text.length > MAX_FILE_LENGTH) return null
        if (file !is PsiJavaFile) return null

        val hits = JavaCancellationPropagationFinder.findAll(file)
        if (hits.isEmpty()) return null

        val problems = hits.map { hit ->
            manager.createProblemDescriptor(
                hit.anchor,
                "This future can be cancelled elsewhere, but the inner CompletableFuture constructed in this " +
                    "thenCompose(...) lambda never forwards that cancellation -- the inner work keeps running " +
                    "and consuming resources after the caller believes it cancelled the whole operation",
                isOnTheFly,
                emptyArray(),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }

        val path = file.virtualFile?.path
        if (path != null) {
            for (hit in hits) {
                val lineNumber = file.viewProvider.document?.getLineNumber(hit.anchor.textRange.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber")
            }
        }

        return problems.toTypedArray()
    }
}
