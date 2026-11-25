package com.zirakusa.brujula.mcp

import java.security.SecureRandom
import java.util.Base64

/** The compass only reads (heading and position): every token is READ. Kept as an enum so the hub reads the same "mode". */
enum class Access(val key: String) { READ("read"), WRITE("write") }

/**
 * A token the handler accepts. The app serves only Arka's own (WRITE): Arka turns the Modo MCP on or off, and the hub
 * decides what each AI may do. READ exists so the rules for read-only tokens stay tested.
 */
class Token(val secret: String, val access: Access = Access.WRITE, val name: String = "Arka") {
    companion object {
        /** "bru_" + 256 random bits: the prefix tells the hub (and secret scanners) which app it belongs to. */
        fun secret(): String = "bru_" + Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))
        fun id(): String = ByteArray(6).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
    }
}
