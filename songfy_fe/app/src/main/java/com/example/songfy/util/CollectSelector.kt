package com.lagu.songfy.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Collect satu field dari StateFlow; recompose hanya saat field itu berubah.
 * collectAsStateWithLifecycle stdlib tidak punya overload selector, jadi disusun
 * dari map + distinctUntilChanged (semantik identik: emit hanya saat nilai beda).
 * ponytail: hapus helper ini kalau lifecycle-runtime-compose menambah selector resmi.
 */
@Composable
fun <T, R> StateFlow<T>.collectSelector(
    selector: (T) -> R,
    lifecycle: Lifecycle = LocalLifecycleOwner.current.lifecycle,
    minActiveState: Lifecycle.State = Lifecycle.State.STARTED
): State<R> {
    // remember(this) SAJA: selector di call-site selalu pure field-read tanpa capture,
    // jadi capture pertama valid selamanya. Pakai selector sebagai key justru bikin
    // mapped flow recreate tiap recompose (lambda baru tiap komposisi) = re-collect terus.
    val mapped = remember(this) { map(selector).distinctUntilChanged() }
    return mapped.collectAsStateWithLifecycle(
        initialValue = selector(value),
        lifecycle = lifecycle,
        minActiveState = minActiveState
    )
}
