package com.example.core

import kotlinx.coroutines.sync.Mutex

/** Lock order: sync, then sales, then the Room transaction. */
object LedgerGate {
    val sync = Mutex()
    val sales = Mutex()
}
