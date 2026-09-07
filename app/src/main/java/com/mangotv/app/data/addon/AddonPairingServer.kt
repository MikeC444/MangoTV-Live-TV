package com.mangotv.app.data.addon

import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.ResponseException
import java.io.IOException

/**
 * A tiny local-network-only HTTP server that lets a phone on the same Wi-Fi
 * add an addon without anyone having to type a URL with a TV remote: the
 * Add Addon screen shows a QR code pointing at this server, the phone opens
 * a one-field form, and submitting it delivers the manifest URL straight
 * back into the running app via [onUrlSubmitted].
 */
class AddonPairingServer(
    port: Int,
    private val onUrlSubmitted: (String) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return when {
            session.method == Method.GET && (session.uri == "/" || session.uri == "/add") ->
                NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html", FORM_HTML)

            session.method == Method.POST && session.uri == "/submit" -> handleSubmit(session)

            else -> NanoHTTPD.newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun handleSubmit(session: IHTTPSession): Response {
        return try {
            session.parseBody(HashMap<String, String>())
            val url = session.parms["url"]?.trim().orEmpty()
            if (url.isBlank()) {
                NanoHTTPD.newFixedLengthResponse(Response.Status.BAD_REQUEST, "text/html", ERROR_HTML)
            } else {
                onUrlSubmitted(url)
                NanoHTTPD.newFixedLengthResponse(Response.Status.OK, "text/html", SUCCESS_HTML)
            }
        } catch (e: IOException) {
            NanoHTTPD.newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "Error: ${e.message}")
        } catch (e: ResponseException) {
            NanoHTTPD.newFixedLengthResponse(e.status, "text/plain", "Error: ${e.message}")
        }
    }

    companion object {
        private const val PAGE_STYLE = """
            body{font-family:-apple-system,Roboto,sans-serif;background:#08080a;color:#f6f6f8;
                 padding:32px 24px;margin:0;text-align:center;}
            h2{font-weight:800;letter-spacing:0.5px;}
            p{color:#afafb8;line-height:1.5;}
            input{width:100%;padding:16px;font-size:16px;border-radius:10px;border:1px solid #333;
                  margin-top:16px;box-sizing:border-box;background:#1c1c20;color:#fff;}
            button{width:100%;padding:16px;font-size:16px;font-weight:700;border-radius:10px;border:none;
                   margin-top:16px;background:linear-gradient(90deg,#ffb020,#ff3d68);color:#08080a;}
        """

        private val FORM_HTML = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Add Addon to Mango TV</title><style>$PAGE_STYLE</style></head><body>
            <h2>Add Addon to Mango TV</h2>
            <p>Paste the addon's manifest URL below, then send it to your TV.</p>
            <form method="POST" action="/submit">
              <input type="url" name="url" placeholder="https://example.com/manifest.json" required autofocus />
              <button type="submit">Send to Mango TV</button>
            </form>
            </body></html>
        """.trimIndent()

        private val SUCCESS_HTML = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Sent</title><style>$PAGE_STYLE</style></head><body>
            <h2>Sent ✓</h2><p>Check your TV screen to finish installing the addon.</p>
            </body></html>
        """.trimIndent()

        private val ERROR_HTML = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Missing URL</title><style>$PAGE_STYLE</style></head><body>
            <h2>Missing URL</h2><p>Go back and paste the addon's manifest URL.</p>
            </body></html>
        """.trimIndent()
    }
}
