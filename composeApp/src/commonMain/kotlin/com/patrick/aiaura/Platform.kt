package com.patrick.aiaura

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform