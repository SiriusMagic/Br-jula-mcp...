package com.zirakusa.brujula

import com.zirakusa.brujula.data.Accuracy
import com.zirakusa.brujula.data.Fix
import com.zirakusa.brujula.data.Heading
import com.zirakusa.brujula.mcp.Access
import com.zirakusa.brujula.mcp.CompassData
import com.zirakusa.brujula.mcp.HubLink
import com.zirakusa.brujula.mcp.Kind
import com.zirakusa.brujula.mcp.McpHandler
import com.zirakusa.brujula.mcp.Place
import com.zirakusa.brujula.mcp.Token
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The compass's MCP: tools list, good calls, bad arguments, unknown tool and not found, no permission or fix, tokens, hub link. */
@RunWith(RobolectricTestRunner::class)
class McpTest {
    /** A fake compass: the tests set what the sensors would say. */
    private class Fake : CompassData {
        override var hasCompass = true
        var reading: Heading? = Heading(288.04f, Accuracy.HIGH, declination = -3.5f)
        override var calibrating = false
        var where: Place = Place.At(Fix(-3.606389, -79.085278, 12.5f, 1_763_000_000_000))
        override suspend fun heading() = reading
        override fun place() = where
        override fun now() = 1_763_000_042_000
    }

    private val data = Fake()
    private val reader = Token("r", "Claude", "bru_" + "r".repeat(40), Access.READ, Kind.HUB, "Rumbo")
    private val paused = Token("p", "Viejo", "bru_" + "p".repeat(40), Access.READ, paused = true)
    private val expired = Token("e", "Caducado", "bru_" + "e".repeat(40), Access.READ, expiresAt = 1)
    private val handler = McpHandler({ listOf(reader, paused, expired) }, data, "0.1.0")

    private fun rpc(token: Token, method: String, params: JSONObject = JSONObject()) = runBlocking {
        JSONObject(handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${token.secret}"), emptyMap(),
            JSONObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).put("params", params).toString()).body)
    }
    private fun tool(name: String, args: JSONObject) =
        rpc(reader, "tools/call", JSONObject().put("name", name).put("arguments", args)).getJSONObject("result")
    private fun ok(name: String, args: JSONObject = JSONObject()) =
        tool(name, args).also { assertFalse(it.toString(), it.optBoolean("isError")) }.getJSONObject("structuredContent")
    private fun code(name: String, args: JSONObject = JSONObject()) = tool(name, args).also { assertTrue(it.optBoolean("isError")) }
        .getJSONObject("structuredContent").getJSONObject("error").getString("code")

    @Test fun toolsListIsReadOnly() {
        val tools = rpc(reader, "tools/list").getJSONObject("result").getJSONArray("tools")
        assertEquals(listOf("compass_heading", "compass_location"), (0 until tools.length()).map { tools.getJSONObject(it).getString("name") })
        (0 until tools.length()).forEach { assertTrue(tools.getJSONObject(it).getJSONObject("annotations").getBoolean("readOnlyHint")) }
        val init = rpc(reader, "initialize").getJSONObject("result")
        assertEquals("brujula", init.getJSONObject("serverInfo").getString("name"))
        assertEquals("0.1.0", init.getJSONObject("serverInfo").getString("version"))
        val meta = init.getJSONObject("_meta").getJSONObject(McpHandler.TOKEN_META)
        assertEquals("hub", meta.getString("kind")); assertEquals("read", meta.getString("mode"))
    }

    @Test fun headingAndLocation() {
        val h = ok("compass_heading")
        assertEquals(288.0, h.getDouble("degrees"), 1e-9)
        assertEquals(284.5, h.getDouble("true_degrees"), 1e-9)
        assertEquals("Oeste", h.getString("direction")); assertEquals("W", h.getString("direction_short"))
        assertEquals("high", h.getString("accuracy")); assertFalse(h.getBoolean("calibrating"))
        val h16 = ok("compass_heading", JSONObject().put("points", 16))
        assertEquals("Oestenoroeste", h16.getString("direction")); assertEquals(16, h16.getInt("points"))
        data.reading = Heading(10f, Accuracy.LOW); data.calibrating = true
        val low = ok("compass_heading")
        assertEquals("low", low.getString("accuracy")); assertTrue(low.getBoolean("calibrating")); assertTrue(low.isNull("true_degrees"))

        val p = ok("compass_location")
        assertEquals(-3.606389, p.getDouble("latitude"), 1e-9); assertEquals(-79.085278, p.getDouble("longitude"), 1e-9)
        assertEquals("3°36′23″ S", p.getString("latitude_dms")); assertEquals("79°05′07″ W", p.getString("longitude_dms"))
        assertEquals(12.5, p.getDouble("accuracy_m"), 1e-9); assertEquals(42, p.getInt("age_seconds"))
    }

    @Test fun badArgumentsAreRefused() {
        assertEquals("BAD_ARGUMENT", code("compass_heading", JSONObject().put("units", "rad")))
        assertEquals("BAD_ARGUMENT", code("compass_heading", JSONObject().put("points", 12)))
        assertEquals("BAD_ARGUMENT", code("compass_heading", JSONObject().put("points", "16")))
        assertEquals("BAD_ARGUMENT", code("compass_location", JSONObject().put("precise", true)))
    }

    @Test fun unknownToolAndNotFound() = runBlocking {
        assertEquals("UNKNOWN_TOOL", code("compass_set_north", JSONObject()))
        assertEquals(-32601, rpc(reader, "compass/eval").getJSONObject("error").getInt("code"))
        assertEquals(404, handler.handle("POST", "/other", emptyMap(), emptyMap(), "{}").status)
        data.where = Place.NoPermission
        assertEquals("NO_PERMISSION", code("compass_location"))
        data.where = Place.NoFix
        assertEquals("NO_FIX", code("compass_location"))
        data.reading = null
        assertEquals("NO_READING", code("compass_heading"))
        data.hasCompass = false
        assertEquals("NO_SENSOR", code("compass_heading"))
    }

    @Test fun wrongAndPausedTokensAndHubLink() = runBlocking {
        val body = """{"jsonrpc":"2.0","id":1,"method":"tools/list"}"""
        assertEquals(200, handler.handle("POST", "/mcp", mapOf("X-Api-Key" to reader.secret), emptyMap(), body).status)
        assertEquals(200, handler.handle("POST", "/mcp", emptyMap(), mapOf("token" to reader.secret), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${paused.secret}"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer ${expired.secret}"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", mapOf("Authorization" to "Bearer bru_wrong"), emptyMap(), body).status)
        assertEquals(401, handler.handle("POST", "/mcp", emptyMap(), emptyMap(), body).status)
        assertEquals("NOT_SAME_DEVELOPER", HubLink.answer(false).getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.answer(true).getString("error"))
        // «Conectar todas» and Lucía's tools: only for the same developer, and tools only with «Redireccionar a Hub» on.
        assertEquals("NOT_SAME_DEVELOPER", HubLink.connect(false).getString("error"))
        assertEquals("NOT_SAME_DEVELOPER", HubLink.tool(false, "{}").getString("error"))
        assertEquals("REDIRECT_OFF", HubLink.tool(true, "{}").getString("error"))
        assertTrue(Token.secret().startsWith("bru_"))
    }

    /** Lucía runs a tool on the phone through the same handler, only while «Redireccionar a Hub» is on. */
    @Test fun luciaRunsToolsOnThePhone() {
        com.zirakusa.brujula.mcp.McpMode.relayUrl = ""                                             // no relay in tests
        com.zirakusa.brujula.mcp.McpMode.load(org.robolectric.RuntimeEnvironment.getApplication())
        com.zirakusa.brujula.mcp.McpMode.redirect(false)
        assertEquals(null, com.zirakusa.brujula.mcp.McpMode.local("{}"))
        HubLink.connect(true); assertTrue(com.zirakusa.brujula.mcp.McpMode.hubRedirect)                // «Conectar todas»
        val list = JSONObject(com.zirakusa.brujula.mcp.McpMode.local("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")!!)
        assertTrue(list.getJSONObject("result").getJSONArray("tools").length() > 0)
        val viaProvider = JSONObject(HubLink.tool(true, """{"jsonrpc":"2.0","id":2,"method":"tools/list"}""").getString("response"))
        assertEquals(2, viaProvider.getInt("id"))
        com.zirakusa.brujula.mcp.McpMode.redirect(false)
    }
}
