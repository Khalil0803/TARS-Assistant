package com.tars.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class ChatMessage(val fromAssistant: Boolean, val text: String)
private data class TaskLog(val time: String, val command: String, val result: String)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("tars", MODE_PRIVATE)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFF91C7FF),
                background = Color(0xFF101318),
                surface = Color(0xFF191D24)
            )) {
                var apiKey by remember { mutableStateOf(prefs.getString("api_key", "") ?: "") }
                var showSettings by remember { mutableStateOf(apiKey.isBlank()) }
                var showHistory by remember { mutableStateOf(false) }
                var input by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                val messages = remember {
                    mutableStateListOf(ChatMessage(true,
                        "我是 TARS。可以正常聊天，也可以尝试执行明确的简单操作。\\n\\n操作示例：点击：搜索、返回、主页、通知栏。首次使用请先设置 API Key，并开启无障碍服务。"))
                }
                val logs = remember {
                    mutableStateListOf<TaskLog>().apply {
                        val saved = prefs.getString("task_logs", "[]") ?: "[]"
                        runCatching {
                            val array = JSONArray(saved)
                            for (i in 0 until array.length()) {
                                val item = array.getJSONObject(i)
                                add(TaskLog(item.optString("time"), item.optString("command"), item.optString("result")))
                            }
                        }
                    }
                }
                val scope = rememberCoroutineScope()

                fun addLog(command: String, result: String) {
                    val time = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    logs.add(0, TaskLog(time, command, result))
                    while (logs.size > 50) logs.removeAt(logs.lastIndex)
                    val array = JSONArray()
                    logs.forEach { item ->
                        array.put(JSONObject().put("time", item.time).put("command", item.command).put("result", item.result))
                    }
                    prefs.edit().putString("task_logs", array.toString()).apply()
                }

                fun sendMessage() {
                    val prompt = input.trim()
                    if (prompt.isEmpty() || busy) return
                    input = ""
                    messages.add(ChatMessage(false, prompt))

                    val service = TarsAccessibilityService.instance
                    val command = prompt.removePrefix("执行：").removePrefix("执行:").trim()
                    val actionResult: String? = when {
                        command.equals("返回", true) || command.equals("后退", true) ->
                            if (service == null) "未执行：请先开启 TARS 无障碍服务。" else if (service.pressBack()) "执行成功：已触发返回操作。" else "执行失败：系统未接受返回操作。"
                        command.equals("主页", true) || command.equals("回到桌面", true) ->
                            if (service == null) "未执行：请先开启 TARS 无障碍服务。" else if (service.goHome()) "执行成功：已返回桌面。" else "执行失败：系统未接受主页操作。"
                        command.equals("通知栏", true) || command.equals("打开通知栏", true) ->
                            if (service == null) "未执行：请先开启 TARS 无障碍服务。" else if (service.openNotifications()) "执行成功：已打开通知栏。" else "执行失败：系统未接受通知栏操作。"
                        command.startsWith("点击：") || command.startsWith("点击:") -> {
                            val target = command.substringAfter(if (command.startsWith("点击：")) "点击：" else "点击:").trim()
                            when {
                                target.isBlank() -> "未执行：请写明要点击的文字，例如“点击：搜索”。"
                                service == null -> "未执行：请先开启 TARS 无障碍服务。"
                                service.clickText(target) -> "执行成功：已尝试点击“$target”。"
                                else -> "执行失败：当前页面没找到“$target”。请确认目标文字可见，或换成按钮上更准确的文字。"
                            }
                        }
                        else -> null
                    }

                    if (actionResult != null) {
                        messages.add(ChatMessage(true, actionResult))
                        addLog(prompt, actionResult)
                    } else {
                        busy = true
                        scope.launch {
                            val history = messages.dropLast(1).takeLast(12)
                            val answer = withContext(Dispatchers.IO) { askDeepSeek(apiKey, history.map { it.fromAssistant to it.text } + listOf(false to prompt)) }
                            messages.add(ChatMessage(true, answer))
                            if (answer.startsWith("请求失败") || answer.startsWith("连接失败")) addLog("AI 对话", answer)
                            busy = false
                        }
                    }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("TARS 助手") },
                            actions = {
                                IconButton(onClick = { showHistory = !showHistory }) {
                                    Icon(Icons.Default.History, contentDescription = "任务记录")
                                }
                                IconButton(onClick = { showSettings = !showSettings }) {
                                    Icon(Icons.Default.Settings, contentDescription = "设置")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(0xFF101318),
                                titleContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                    },
                    containerColor = Color(0xFF101318),
                    bottomBar = {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = input, onValueChange = { input = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("给 TARS 发消息…") },
                                maxLines = 4, shape = RoundedCornerShape(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(enabled = input.isNotBlank() && !busy, onClick = { sendMessage() }) {
                                Icon(Icons.Default.Send, contentDescription = "发送")
                            }
                        }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (showSettings) {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF191D24))) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("基础设置", style = MaterialTheme.typography.titleMedium)
                                    OutlinedTextField(
                                        value = apiKey, onValueChange = { apiKey = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text("DeepSeek API Key") },
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                        singleLine = true
                                    )
                                    Button(onClick = {
                                        prefs.edit().putString("api_key", apiKey.trim()).apply()
                                        showSettings = false
                                    }, modifier = Modifier.fillMaxWidth()) { Text("保存密钥") }
                                    OutlinedButton(onClick = {
                                        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                    }, modifier = Modifier.fillMaxWidth()) { Text("开启无障碍服务") }
                                    Text("操作命令只在你发送后执行。API Key 只保存在本机设置中，不要提交到公开仓库。",
                                        fontSize = 12.sp, color = Color.LightGray)
                                }
                            }
                        }
                        if (showHistory) {
                            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF191D24))) {
                                Column(Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("任务记录（最近 ${logs.size} 条）", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                        TextButton(onClick = {
                                            logs.clear()
                                            prefs.edit().putString("task_logs", "[]").apply()
                                        }) { Text("清空") }
                                    }
                                    if (logs.isEmpty()) Text("暂无记录", color = Color.LightGray)
                                    else LazyColumn(Modifier.heightIn(max = 200.dp)) {
                                        items(logs) { log ->
                                            Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                                Text("${log.time} · ${log.result}", color = Color.White, fontSize = 13.sp)
                                                Text(log.command, color = Color.LightGray, fontSize = 12.sp)
                                                HorizontalDivider(color = Color(0xFF343B46))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        LazyColumn(
                            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            items(messages) { message ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement =
                                    if (message.fromAssistant) Arrangement.Start else Arrangement.End) {
                                    Surface(
                                        color = if (message.fromAssistant) Color(0xFF202630) else Color(0xFF24476B),
                                        shape = RoundedCornerShape(18.dp),
                                        modifier = Modifier.widthIn(max = 320.dp)
                                    ) {
                                        Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                            color = Color.White, fontSize = 15.sp)
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
            connectTimeout = 15000
            readTimeout = 45000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Authorization", "Bearer $apiKey")
            doOutput = true
        }
        val messages = JSONArray().put(JSONObject().put("role", "system").put(
            "content",
            "你是 TARS 助手。请用中文自然、简洁地回答。你不能声称已经控制手机或执行了操作。若用户要求操作手机，请说明目前支持的明确命令：点击：按钮文字、返回、主页、通知栏。不要输出或索取用户的 API Key。"
        ))
        history.forEach { (assistant, text) ->
            messages.put(JSONObject().put("role", if (assistant) "assistant" else "user").put("content", text))
        }
        val body = JSONObject().put("model", "deepseek-chat").put("messages", messages).put("stream", false).toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") ?: response }
                .getOrDefault(response).take(300)
            when (code) {
                401, 403 -> "API Key 无效或没有权限（HTTP $code）。请检查密钥和账户权限。"
                429 -> "请求过于频繁或额度不足（HTTP 429）。请稍后重试并检查账户余额/限额。"
                else -> "请求失败（HTTP $code）：$detail"
            }
        } else {
            JSONObject(response).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim().ifBlank { "AI 返回了空内容，请重试。" }
        }
    } catch (e: Exception) {
        "连接失败：${e.message ?: "未知错误"}。请检查网络后重试。"
    } finally {
        connection?.disconnect()
    }
}
