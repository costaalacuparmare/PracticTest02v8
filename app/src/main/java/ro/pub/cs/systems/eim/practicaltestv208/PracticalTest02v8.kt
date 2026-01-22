package ro.pub.cs.systems.eim.practicaltestv208

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.pub.cs.systems.eim.practicaltestv208.ui.theme.PracticalTestv208Theme
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URL

class MainActivity : ComponentActivity() {
    private var serverThread: ServerThread? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PracticalTestv208Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onStartServer = { port ->
                            serverThread = ServerThread(port.toInt())
                            serverThread?.start()
                        },
                        onStopServer = {
                            serverThread?.stopServer()
                            serverThread = null
                        },
                        onSendRequest = { addr, port, url, callback ->
                            ClientThread(addr, port.toInt(), url, callback).start()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        serverThread?.stopServer()
        super.onDestroy()
    }
}

// Server Implementation
class ServerThread(private val port: Int) : Thread() {
    private var serverSocket: ServerSocket? = null

    override fun run() {
        try {
            serverSocket = ServerSocket(port)
            Log.i("Server", "Firewall Server started on port $port")
            while (!isInterrupted) {
                val socket = serverSocket?.accept() ?: break
                CommunicationThread(socket).start()
            }
        } catch (e: Exception) {
            Log.e("Server", "Error: ${e.message}")
        }
    }

    fun stopServer() {
        interrupt()
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("Server", "Error closing socket: ${e.message}")
        }
    }
}

class CommunicationThread(private val socket: Socket) : Thread() {
    override fun run() {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(socket.getOutputStream(), true)

            val requestedUrl = reader.readLine()

            if (requestedUrl != null) {
                Log.i("Server", "Processing request for: $requestedUrl")

                val response = if (requestedUrl.contains("bad", ignoreCase = true)) {
                    "URL blocked by firewall"
                } else {
                    // Fetch content directly without caching
                    fetchUrlContent(requestedUrl)
                }

                writer.println(response)
            }
            socket.close()
        } catch (e: Exception) {
            Log.e("CommThread", "Error: ${e.message}")
        }
    }

    private fun fetchUrlContent(urlString: String): String {
        return try {
            val url = URL(urlString)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                "HTTP Error: $responseCode"
            }
        } catch (e: Exception) {
            "Error fetching content: ${e.message}"
        }
    }
}

// Client Implementation
class ClientThread(
    private val address: String,
    private val port: Int,
    private val urlToRequest: String,
    private val onResult: (String) -> Unit
) : Thread() {
    override fun run() {
        try {
            val socket = Socket(address, port)
            val writer = PrintWriter(socket.getOutputStream(), true)
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))

            writer.println(urlToRequest)

            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line).append("\n")
            }
            onResult(response.toString().trim())
            socket.close()
        } catch (e: Exception) {
            onResult("Client Error: ${e.message}")
        }
    }
}

// UI Components
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onStartServer: (String) -> Unit,
    onStopServer: () -> Unit,
    onSendRequest: (String, String, String, (String) -> Unit) -> Unit
) {
    var serverPort by remember { mutableStateOf("8080") }
    var isServerRunning by remember { mutableStateOf(false) }
    var clientAddr by remember { mutableStateOf("localhost") }
    var clientPort by remember { mutableStateOf("8080") }
    var urlInput by remember { mutableStateOf("https://google.com") }
    var resultText by remember { mutableStateOf("Ready...") }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Simple Firewall Server", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = serverPort,
            onValueChange = { serverPort = it },
            label = { Text("Server Port") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (!isServerRunning) onStartServer(serverPort) else onStopServer()
                isServerRunning = !isServerRunning
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isServerRunning) Color.Red else Color.Green
            )
        ) {
            Text(if (isServerRunning) "Stop Firewall Server" else "Start Firewall Server")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Text("Client Request", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = clientAddr,
            onValueChange = { clientAddr = it },
            label = { Text("Server Address") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = clientPort,
            onValueChange = { clientPort = it },
            label = { Text("Server Port") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("URL (contains 'bad' to block)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                resultText = "Requesting via Firewall..."
                onSendRequest(clientAddr, clientPort, urlInput) { res ->
                    scope.launch {
                        withContext(Dispatchers.Main) {
                            resultText = res
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Request")
        }

        Text("Firewall Result:", fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp),
            colors = CardDefaults.cardColors(containerColor = Color.LightGray.copy(alpha = 0.2f))
        ) {
            Text(
                text = resultText,
                modifier = Modifier.padding(8.dp),
                fontSize = 12.sp
            )
        }
    }
}