package com.p2p.meshify.core.data.repository

interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
