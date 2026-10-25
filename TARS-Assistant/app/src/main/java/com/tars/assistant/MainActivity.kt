package com.tars.assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("tars", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF91C7FF), background = Color(0xFF101318), surface = Color(0xFF191D24))) {
                var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
                var showSettings by remember { mutableStateOf(apiKey.isBlank()) }
                var input by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                val messages = remember { mutableStateListOf<Pair<Boolean, String>>().apply { add(true to "我是 TARS。你可以向我提问，或让我尝试执行简单的界面操作。首次使用请先在设置中填写 DeepSeek API Key。") } }
                val scope = rememberCoroutineScope()
                Scaffold(
                    topBar = { TopAppBar(title = { Text("TARS 助手") }, actions = { IconButton(onClick = { showSettings = !showSettings }) { Icon(Icons.Default.Settings, contentDescription = "设置") } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF101318), titleContentColor = Color.White, actionIconContentColor = Color.White)) },
                    containerColor = Color(0xFF101318),
                    bottomBar = {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("给 TARS 发消息…") }, maxLines = 4, shape = RoundedCornerShape(22.dp))
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(enabled = input.isNotBlank() && !busy, onClick = {
                                val prompt = input.trim(); input = ""; messages.add(false to prompt); busy = true
                                scope.launch {
                                    val answer = withContext(Dispatchers.IO) { askDeepSeek(apiKey, messages.dropLast(1).takeLast(12).map { it.first to it.second } + listOf(false to prompt)) }
                                    messages.add(true to answer); busy = false
                                }
                            }) { Icon(Icons.Default.Send, contentDescription = "发送") }
                        }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (showSettings) {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF191D24))) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("基础设置", style = MaterialTheme.typography.titleMedium)
                                    OutlinedTextField(value = apiKey, onValueChange = { apiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("DeepSeek API Key") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true)
                                    Button(onClick = { prefs.edit().putString("api_key", apiKey.trim()).apply(); showSettings = false }, modifier = Modifier.fillMaxWidth()) { Text("保存密钥") }
                                    OutlinedButton(onClick = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("开启无障碍服务") }
                                    Text("API Key 只保存在本机应用设置中。不要把密钥发给别人或提交到公开仓库。", fontSize = 12.sp, color = Color.LightGray)
                                }
                            }
                        }
                        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                            items(messages) { (assistant, text) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (assistant) Arrangement.Start else Arrangement.End) {
                                    Surface(color = if (assistant) Color(0xFF202630) else Color(0xFF24476B), shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 320.dp)) {
                                        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = Color.White, fontSize = 15.sp)
                                    }
                                }
                            }
                            if (busy) item { Text("TARS 正在思考…", color = Color.LightGray, modifier = Modifier.padding(8.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private fun askDeepSeek(apiKey: String, history: List<Pair<Boolean, String>>): String {
    if (apiKey.isBlank()) return "请先点击右上角设置，填写 DeepSeek API Key 并保存。"
    var connection: HttpURLConnection? = null
    return try {
        connection = (URL("https://api.deepseek.com/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 60000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
        }
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", "你是 TARS 助手。请用中文简洁回答。你目前不能直接控制其他应用；如用户要求操作手机，请说明需要后续接入自动化动作。"))
        history.forEach { (assistant, text) -> msgs.put(JSONObject().put("role", if (assistant) "assistant" else "user").put("content", text)) }
        val body = JSONObject().put("model", "deepseek-chat").put("messages", msgs).put("stream", false).toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream.bufferedReader().use { it.readText() }
        if (code !in 200..299) "请求失败（HTTP $code）：${runCatching { JSONObject(response).optString("error", response) }.getOrDefault(response).take(400)}"
        else JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
    } catch (e: Exception) { "连接失败：${e.message ?: "未知错误"}。请检查网络和 API Key。" }
    finally { connection?.disconnect() }
}
