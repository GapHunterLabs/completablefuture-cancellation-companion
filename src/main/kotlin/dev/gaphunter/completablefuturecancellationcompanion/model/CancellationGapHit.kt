package dev.gaphunter.completablefuturecancellationcompanion.model

import com.intellij.psi.PsiElement

/** One `.thenCompose(...)` chain whose result future can be cancelled, but whose inner future (constructed directly in the lambda) never forwards that cancellation. */
data class CancellationGapHit(val anchor: PsiElement)
