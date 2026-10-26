package com.example.studentcodex.data.supabase

import android.content.Context

/**
 * Holds the application context so repository-layer helpers (the community
 * image source reading picked photos through the ContentResolver) can reach
 * it without leaking a Context into every call site. Attached once from
 * [com.example.studentcodex.di.AppContainer]'s init.
 */
object AppContextHolder {
    @Volatile private var context: Context? = null

    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    fun get(): Context? = context
}
