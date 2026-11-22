package com.sharescreenweb

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors

/**
 * Minimal HTTP server that serves a viewer page and an MJPEG stream
 * (multipart/x-mixed-replace), which every browser can display in an <img>.
 */
class MjpegServer {
    private var serverSocket: ServerSocket? = null
    private val clientPool = Executors.newCachedThreadPool()
    private val frameLock = Object()
    private var latestFrame: ByteArray? = null
    private var frameSeq = 0L
    @Volatile private var running = false

    /** Starts listening on the first free port in [ports]. Returns the port. */
    fun start(ports: IntRange = 8080..8099): Int {
        var lastError: Exception? = null
        for (p in ports) {
            try {
                val ss = ServerSocket(p)
                serverSocket = ss
                running = true
                Thread({ acceptLoop(ss) }, "mjpeg-accept").start()
                return p
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No free port")
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        synchronized(frameLock) { frameLock.notifyAll() }
        clientPool.shutdownNow()
    }

    fun publish(jpeg: ByteArray) {
        synchronized(frameLock) {
            latestFrame = jpeg
            frameSeq++
            frameLock.notifyAll()
        }
    }

    private fun acceptLoop(ss: ServerSocket) {
        while (running) {
            try {
                val socket = ss.accept()
                clientPool.execute { handle(socket) }
            } catch (_: SocketException) {
                break
            } catch (e: Exception) {
                Log.w(TAG, "accept failed", e)
            }
        }
    }

    private fun handle(socket: Socket) {
        socket.use { s ->
            try {
                s.tcpNoDelay = true
                val reader = BufferedReader(InputStreamReader(s.getInputStream(), Charsets.ISO_8859_1))
                val requestLine = reader.readLine() ?: return
                // Drain request headers.
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isEmpty()) break
                }
                val path = requestLine.split(" ").getOrNull(1)?.substringBefore('?') ?: "/"
                val out = s.getOutputStream()
                when (path) {
                    "/", "/index.html" ->
                        sendBytes(out, "200 OK", "text/html; charset=utf-8", VIEWER_HTML.toByteArray())
                    "/stream" -> streamMjpeg(out)
                    "/frame.jpg" -> {
                        val f = synchronized(frameLock) { latestFrame }
                        if (f != null) sendBytes(out, "200 OK", "image/jpeg", f)
                        else sendBytes(out, "503 Service Unavailable", "text/plain", "No frame yet".toByteArray())
                    }
                    else -> sendBytes(out, "404 Not Found", "text/plain", "Not found".toByteArray())
                }
            } catch (_: Exception) {
                // Client went away - normal for streams.
            }
        }
    }

    private fun sendBytes(out: OutputStream, status: String, type: String, body: ByteArray) {
        val header = "HTTP/1.1 $status\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: ${body.size}\r\n" +
            "Cache-Control: no-store\r\n" +
            "Connection: close\r\n\r\n"
        out.write(header.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        out.flush()
    }

    private fun streamMjpeg(out: OutputStream) {
        out.write(
            ("HTTP/1.1 200 OK\r\n" +
                "Content-Type: multipart/x-mixed-replace; boundary=$BOUNDARY\r\n" +
                "Cache-Control: no-store, no-cache, must-revalidate\r\n" +
                "Pragma: no-cache\r\n" +
                "Connection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
        )
        out.flush()
        var lastSent = -1L
        while (running) {
            var frame: ByteArray?
            synchronized(frameLock) {
                if (frameSeq == lastSent) {
                    // Wait for a new frame; resend the old one periodically as keep-alive.
                    frameLock.wait(KEEPALIVE_MS)
                }
                frame = latestFrame
                lastSent = frameSeq
            }
            if (!running) return
            val f = frame ?: continue
            out.write(
                ("--$BOUNDARY\r\n" +
                    "Content-Type: image/jpeg\r\n" +
                    "Content-Length: ${f.size}\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
            )
            out.write(f)
            out.write("\r\n".toByteArray(Charsets.ISO_8859_1))
            out.flush()
        }
    }

    companion object {
        private const val TAG = "MjpegServer"
        private const val BOUNDARY = "screenframe"
        private const val KEEPALIVE_MS = 2000L

        private val VIEWER_HTML = """
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <title>Shared screen</title>
            <style>
              html, body { margin: 0; height: 100%; background: #000; }
              body { display: flex; align-items: center; justify-content: center; overflow: hidden; }
              img { max-width: 100%; max-height: 100%; object-fit: contain; cursor: zoom-in; }
              #status { position: fixed; top: 12px; left: 50%; transform: translateX(-50%);
                        color: #ddd; font: 14px system-ui, sans-serif; background: rgba(0,0,0,.6);
                        padding: 6px 12px; border-radius: 8px; display: none; }
            </style>
            </head>
            <body>
            <div id="status">Connecting&hellip;</div>
            <img id="screen" alt="Shared screen">
            <script>
              const img = document.getElementById('screen');
              const status = document.getElementById('status');
              function connect() { img.src = '/stream?t=' + Date.now(); }
              img.onload = () => { status.style.display = 'none'; };
              img.onerror = () => {
                status.textContent = 'Stream not available, retrying...';
                status.style.display = 'block';
                setTimeout(connect, 1500);
              };
              img.onclick = () => {
                if (document.fullscreenElement) document.exitFullscreen();
                else if (document.documentElement.requestFullscreen) document.documentElement.requestFullscreen();
              };
              connect();
            </script>
            </body>
            </html>
        """.trimIndent()
    }
}
