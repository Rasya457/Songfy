/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.wrapped

object WrappedConstants {
    val YEAR: Int
        get() = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    val PLAYLIST_NAME: String
        get() = "Songfy $YEAR"
}
