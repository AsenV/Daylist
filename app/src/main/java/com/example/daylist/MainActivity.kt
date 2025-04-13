package com.example.daylist

import java.nio.charset.Charset
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.BufferedReader
import java.io.InputStreamReader
import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.util.Xml
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.TimePicker
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.daylist.ui.theme.DaylistTheme
import com.example.daylist.ui.theme.LocalExtraColors
import com.jakewharton.threetenabp.AndroidThreeTen // Unresolved reference 'AndroidThreeTen'.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

// Lang.get(0, context)
object Lang {
    private val keys: Map<Int, Int> by lazy {
        val fields = R.string::class.java.fields
        fields
            .filter { it.name.startsWith("lang_") }
            .associate { it.name.removePrefix("lang_").toInt() to it.getInt(null) }
    }

    fun get(key: Int, context: Context): String {
        return keys[key]?.let { context.getString(it) } ?: "Empty"
    }
}

data class Task(val name: String, val afterHour: String, val enabled: Boolean)
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidThreeTen.init(this) // Inicializa a biblioteca ThreeTenABP

        WindowCompat.setDecorFitsSystemWindows(window, false) // Permite desenhar sob a status bar
        setStatusBarAppearance()

        setContent {
            DaylistTheme {
                AppNavigation()
            }
        }

        requestPermissions()
        readOrCreateFile()
    }

    // Função para solicitar permissões
    private fun requestPermissions() {
        // Se já estiver em uma versão Android 13 ou superior, você pode pedir permissões de notificação
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1
                )
            }
        }
    }

    private fun setStatusBarAppearance() {
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false // Ícones brancos na status bar
    }

    private fun readOrCreateFile() {
        val daylistDir = File(filesDir, "daylist").apply {
            if (!exists()) mkdir()
        }

        val filePath = File(daylistDir, "daylist.xml")

        if (!filePath.exists()) {
            Log.w("Daylist", "daylist.xml não existe! Criando novo arquivo.")
            try {
                filePath.writeText(
                    "<daylist><settings></settings><agendas></agendas></daylist>"
                )
                Log.d("Daylist", "daylist.xml foi criado")
            } catch (e: Exception) {
                Log.e("Daylist", "Erro ao criar o arquivo: ${e.message}")
            }
        }
    }
}

fun saveDaylistToXML(
    settings: Map<String, String>,
    agendasWithTasks: List<Pair<String, List<Task>>>, // Usa a nova classe Task
    context: Context
) {
    val xml = Xml.newSerializer()
    val daylistDir = File(context.filesDir, "daylist").apply { if (!exists()) mkdir() }
    val outputStream = FileOutputStream(File(daylistDir, "daylist.xml"))

    xml.setOutput(outputStream, "UTF-8")
    xml.startDocument("UTF-8", true)
    xml.startTag("", "Daylist")

    // Salva as configurações
    xml.startTag("", "Settings")
    for ((key, value) in settings) {
        xml.startTag("", "Setting")
        xml.attribute("", "Key", key)
        xml.attribute("", "Value", value)
        xml.endTag("", "Setting")
    }
    xml.endTag("", "Settings")

    // Salva as agendas com suas tasks
    xml.startTag("", "Agendas")
    for ((agendaName, tasks) in agendasWithTasks) {
        xml.startTag("", "Agenda")
        xml.attribute("", "Name", agendaName)

        for (task in tasks) {
            xml.startTag("", "Task")
            xml.attribute("", "Name", task.name)
            xml.attribute("", "Afterhour", task.afterHour)
            xml.attribute(
                "",
                "Enabled",
                task.enabled.toString()
            ) // Salva como string ("true" ou "false")
            xml.endTag("", "Task")
        }

        xml.endTag("", "Agenda")
    }
    xml.endTag("", "Agendas")

    xml.endTag("", "Daylist")
    xml.endDocument()
    outputStream.close()
}

fun loadDaylistFromXML(context: Context): List<Pair<String, List<Task>>> {
    val agendas = mutableListOf<Pair<String, List<Task>>>()
    try {
        val daylistDir = File(context.filesDir, "daylist")
        val inputStream: InputStream = FileInputStream(File(daylistDir, "daylist.xml"))
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(inputStream, "UTF-8")

        var eventType = parser.eventType
        var currentAgenda: String? = null
        var currentTasks = mutableListOf<Task>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "Agenda" -> {
                            currentAgenda = parser.getAttributeValue(null, "Name")
                            currentTasks = mutableListOf()
                        }

                        "Task" -> {
                            val taskName = parser.getAttributeValue(null, "Name") ?: ""
                            val afterHour = parser.getAttributeValue(null, "Afterhour") ?: ""
                            val enabled =
                                parser.getAttributeValue(null, "Enabled")?.toBoolean() ?: false
                            currentTasks.add(Task(taskName, afterHour, enabled))
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (parser.name == "Agenda" && currentAgenda != null) {
                        agendas.add(currentAgenda to currentTasks)
                    }
                }
            }
            eventType = parser.next()
        }
        inputStream.close()
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return agendas
}


fun salvarBackup(context: Context, uri: Uri) {
    try {
        val inputFile = File(context.filesDir, "daylist/daylist.xml")
        if (!inputFile.exists()) {
            Toast.makeText(context, Lang.get(1, context), Toast.LENGTH_SHORT).show()
            return
        }

        val formatoSimples = xmlParaFormatoSimples(inputFile) // Converte XML para Formato Simples

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(formatoSimples.toByteArray(Charset.forName("UTF-8")))
        }

        Toast.makeText(context, Lang.get(2, context), Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(context, Lang.get(3, context), Toast.LENGTH_SHORT).show()
    }
}

fun importarBackup(context: Context, uri: Uri) {
    try {
        val daylistDir = File(context.filesDir, "daylist")
        if (!daylistDir.exists()) {
            daylistDir.mkdir()
        }

        val tempFile = File(daylistDir, "daylist_temp.txt") // Arquivo temporário para formato simples
        val outputFile = File(daylistDir, "daylist.xml")

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(tempFile).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        // Lê o arquivo salvo como formato simples e converte para XML
        val texto = tempFile.readText(Charset.forName("UTF-8"))
        val xmlConvertido = formatoSimplesParaXml(texto)

        outputFile.writeText(xmlConvertido, Charset.forName("UTF-8"))

        Toast.makeText(context, Lang.get(4, context), Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(context, Lang.get(6, context), Toast.LENGTH_SHORT).show()
    }
}


fun xmlParaFormatoSimples(xmlFile: File): String {
    val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    val doc = builder.parse(xmlFile)
    doc.documentElement.normalize()

    val agendas = doc.getElementsByTagName("Agenda")
    val sb = StringBuilder()

    for (i in 0 until agendas.length) {
        val agendaNode = agendas.item(i)
        if (agendaNode.nodeType == Node.ELEMENT_NODE) {
            val agendaElement = agendaNode as Element
            val agendaNome = agendaElement.getAttribute("Name")
            sb.append("[ $agendaNome ]\n") // Inicia a agenda

            val tasks = agendaElement.getElementsByTagName("Task")
            for (j in 0 until tasks.length) {
                val taskElement = tasks.item(j) as Element
                // Converte &#10; para quebra de linha
                val rawTaskNome = taskElement.getAttribute("Name").replace("&#10;", "\n")
                val taskHora = taskElement.getAttribute("Afterhour")

                // Adiciona a hora da tarefa
                sb.append("[ $taskHora ]\n")

                // Separa as linhas do nome da task
                val taskLines = rawTaskNome.split("\n").toMutableList()

                // Remove as linhas em branco do final (trailing)
                while (taskLines.isNotEmpty() && taskLines.last().isBlank()) {
                    taskLines.removeAt(taskLines.size - 1)
                }

                // Adiciona cada linha da task
                for (line in taskLines) {
                    sb.append("$line\n")
                }
                // Garante que haverá apenas UMA linha em branco no final da task
                sb.append("\n")
            }
            // Adiciona uma linha em branco entre agendas somente se não houver uma já
            if (sb.length >= 2) {
                if (!(sb[sb.length - 1] == '\n' && sb[sb.length - 2] == '\n')) {
                    sb.append("\n")
                }
            } else {
                sb.append("\n")
            }
        }
    }
    sb.append("\n")
    return sb.toString()
}


fun formatoSimplesParaXml(texto: String): String {
    val sb = StringBuilder()
    sb.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
    sb.append("<Daylist>\n")
    sb.append("  <Settings />\n")
    sb.append("  <Agendas>\n")

    val lines = texto.lines()
    var currentAgenda: String? = null
    var currentTaskHour: String? = null
    val currentTaskNameLines = mutableListOf<String>()

    // Função auxiliar para finalizar a task acumulada
    fun finalizeTask() {
        if (currentTaskHour != null && currentTaskNameLines.isNotEmpty()) {
            // Remove linhas vazias no final (trailing)
            while (currentTaskNameLines.isNotEmpty() && currentTaskNameLines.last().isBlank()) {
                currentTaskNameLines.removeAt(currentTaskNameLines.size - 1)
            }
            val taskName = currentTaskNameLines.joinToString("&#10;")
            sb.append("      <Task Name=\"$taskName\" Afterhour=\"$currentTaskHour\" Enabled=\"true\" />\n")
            currentTaskHour = null
            currentTaskNameLines.clear()
        }
    }

    // Regex para identificar um horário, ex: "00:01"
    val timeRegex = Regex("^\\d{2}:\\d{2}$")

    for (rawLine in lines) {
        // Preserve a linha original para identificar linhas vazias
        val line = rawLine
        if (line.isBlank()) {
            // Se estamos no meio de uma task, adiciona a linha em branco
            if (currentTaskHour != null) {
                currentTaskNameLines.add("")
            }
            // Se não estiver numa task, ignora a linha em branco
            continue
        }
        if (line.startsWith("[") && line.endsWith("]")) {
            val content = line.substring(1, line.length - 1).trim() // Remove os colchetes e espaços
            if (timeRegex.matches(content)) {
                // É a hora de uma task: finaliza qualquer task anterior
                finalizeTask()
                currentTaskHour = content
            } else {
                // É o título de uma nova agenda
                finalizeTask() // Finaliza task pendente, se houver
                // Se já havia uma agenda aberta, fecha-a
                if (currentAgenda != null) {
                    sb.append("    </Agenda>\n")
                }
                currentAgenda = content
                sb.append("    <Agenda Name=\"$currentAgenda\">\n")
            }
        } else {
            // Linha que não está entre colchetes: parte do nome da task
            if (currentTaskHour != null) {
                currentTaskNameLines.add(line.trim())
            }
        }
    }
    // Finaliza qualquer task ou agenda que ainda esteja pendente
    finalizeTask()
    if (currentAgenda != null) {
        sb.append("    </Agenda>\n")
    }

    sb.append("  </Agendas>\n")
    sb.append("</Daylist>\n")
    return sb.toString()
}



fun getLastPage(context: Context) =
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getString("last_page", "Home") ?: "Home"

fun saveLastPage(context: Context, page: String) =
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
        putString("last_page", page)
    }

fun getLastSelectedAgenda(context: Context) =
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        .getString("selected_agenda", null) ?: ""

fun saveLastSelectedAgenda(context: Context, agenda: String) {
    context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit {
        putString("selected_agenda", agenda)
    }
}

fun getAppVersion(context: Context): String {
    return try {
        val packageInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
            ?: Lang.get(7, context) // Caso versionName seja nulo, retorna "Desconhecida"
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        Lang.get(7, context) // Caso não consiga encontrar a versão
    }
}

fun getAppName(context: Context): String {
    val packageManager = context.packageManager
    val applicationInfo = context.applicationInfo
    return packageManager.getApplicationLabel(applicationInfo).toString()
}

@Composable
fun Header(
    title: String,
    onSettingsClick: () -> Unit,
    headerBackgroundColor: Color
) {
    val foreColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(headerBackgroundColor), // Usando a cor passada como parâmetro
        contentAlignment = Alignment.Center
    ) {
        // Row para garantir que o título ocupe toda a largura disponível e o botão de configurações fique à direita
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Título clicável
            Text(
                text = title,
                color = foreColor,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f) // Garante que o título ocupe a largura disponível
                    .padding(vertical = 15.dp)
                    .padding(start = 50.dp)
            )

            // Botão de configurações clicável na área inteira
            Box(
                modifier = Modifier
                    .clickable { onSettingsClick() }
                    .align(Alignment.CenterVertically) // Alinha verticalmente no centro
                    .padding(14.dp) // Espaçamento para a área clicável
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = foreColor
                )
            }
        }
    }
}

@Composable
fun Footer(
    title: String,
    onClick: () -> Unit,
    footerBackgroundColor: Color
) {
    val foreColor = MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(footerBackgroundColor.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        // Row para garantir que o título ocupe toda a largura disponível e o botão de configurações fique à direita
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Título clicável
            Text(
                text = title,
                color = foreColor,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f) // Garante que o título ocupe a largura disponível
                    .clickable { onClick() }
                    .padding(vertical = 15.dp)
                //.padding(start = 50.dp)
            )
        }
    }
}


fun saveActivationTimestamp(context: Context, agenda: String, timestamp: Long) {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    prefs.edit().putLong("activation_$agenda", timestamp).apply()
}

fun getActivationTimestamp(context: Context, agenda: String): Long {
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    return prefs.getLong("activation_$agenda", 0L)
}
// Função para gerar o nome do arquivo com a data atual
fun getBackupFileName(context: Context): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val date = dateFormat.format(Date())
    return "${getAppName(context).lowercase(Locale.getDefault())} $date"
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    var currentPage by remember { mutableStateOf("Home") }
    var selectedAgenda by remember { mutableStateOf(getLastSelectedAgenda(context) ?: "") }
    var agendaToList by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(emptyMap<String, String>()) }
    var agendas by remember { mutableStateOf(emptyList<Pair<String, List<Task>>>()) }
    var tasks by remember { mutableStateOf(emptyList<Task>()) }
    var agendaActivationId by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        val loadedAgendas = loadDaylistFromXML(context)
        if (loadedAgendas.isNotEmpty()) {
            agendas = loadedAgendas.map { it.first to it.second }
            tasks = agendas.flatMap { it.second }
            Log.d("AgendaData", "Agendas carregadas: $agendas")

            // Se houver uma agenda selecionada, verifique se ela já terminou
            if (selectedAgenda.isNotEmpty()) {
                val agendaTasks = agendas.find { it.first == selectedAgenda }?.second ?: emptyList()
                if (agendaTasks.isNotEmpty()) {
                    // Recupera o timestamp de ativação
                    val activationTimestamp = getActivationTimestamp(context, selectedAgenda)
                    // Calcula o tempo máximo previsto (em milissegundos) para a agenda
                    val maxFinishTime = agendaTasks.maxOfOrNull { task ->
                        // Converte afterHour (HH:mm) para milissegundos
                        val parts = task.afterHour.split(":")
                        val taskDelay = ((parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)) * 60 * 1000L
                        activationTimestamp + taskDelay
                    } ?: 0L

                    // Se o tempo atual é maior ou igual ao tempo máximo, limpa a agenda selecionada
                    if (System.currentTimeMillis() >= maxFinishTime) {
                        selectedAgenda = ""
                        saveLastSelectedAgenda(context, "")
                        Log.d("AppNavigation", "Agenda concluída, limpeza da seleção")
                    }
                }
            }
        }
    }



    // Função para atualizar e salvar os dados
    fun updateData(
        newSettings: Map<String, String> = settings,
        newAgendas: List<Pair<String, List<Task>>> = agendas
    ) {
        settings = newSettings
        agendas = newAgendas
        tasks = newAgendas.flatMap { it.second }
        saveDaylistToXML(settings, agendas, context)
        Log.d("updateData", "Tarefas atualizadas: $tasks")
    }

    // Função para resetar dados
    fun resetData() {
        settings = emptyMap() // Limpa as configurações
        agendas = emptyList() // Limpa as agendas
        tasks = emptyList() // Limpa as tarefas
        saveDaylistToXML(settings, agendas, context) // Salva a configuração resetada
        Log.d("ResetData", "Dados resetados com sucesso")
    }

    // Tratamento do botão de voltar de navegação swipe switch deslizar
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    DisposableEffect(backDispatcher) {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                currentPage = when (currentPage) {
                    "Home" -> {
                        (context as Activity).finish()
                        "Home"
                    }

                    "TaskPage" -> "Home"
                    else -> if (agendas.none { it.first == agendaToList }) {"Home"} else {getLastPage(context)}
                }
                saveLastPage(context, currentPage)
            }
        }
        backDispatcher?.addCallback(callback)
        onDispose { callback.remove() }
    }

    // Navegação entre as páginas
    Box(modifier = Modifier.fillMaxSize()) {
        when (currentPage) {
            "Home" -> AgendaPage(
                settings = settings.toMutableMap(),
                agendas = agendas.map { it.first },
                selectedAgenda = selectedAgenda,
                onSettingsClick = { currentPage = "Settings" },
                onStart = { agenda ->
                    agendaToList = agenda
                    currentPage = "TaskPage"
                    saveLastPage(context, "TaskPage")
                },
                onAddAgenda = { name ->
                    val updatedAgendas = agendas + (name to emptyList<Task>())
                    updateData(newAgendas = updatedAgendas)
                },
                onEditAgenda = { index, name ->
                    val updatedAgendas = agendas.toMutableList().apply {
                        this[index] = name to agendas[index].second
                    }
                    updateData(newAgendas = updatedAgendas)
                },
                onDeleteAgenda = { agendaName ->
                    // Remove da lista original usando o nome
                    val updatedAgendas = agendas.filter { it.first != agendaName }
                    updateData(newAgendas = updatedAgendas)
                },
                onSelectAgenda = onSelect@ { agenda ->
                    val agendaTasks = agendas.find { it.first == agenda }?.second ?: emptyList()

                    // Se a agenda não possui tasks, mostra "Agenda vazia"
                    if (agendaTasks.isEmpty()) {
                        Toast.makeText(context, Lang.get(8, context), Toast.LENGTH_SHORT).show()
                        return@onSelect
                    }

                    if (selectedAgenda == agenda) {
                        // Agenda já selecionada; cancela
                        cancelAlarmsForSelectedAgenda(context, agendaTasks)
                        selectedAgenda = ""
                        saveLastSelectedAgenda(context, "")
                        Toast.makeText(context, Lang.get(9, context), Toast.LENGTH_SHORT).show()
                    } else {
                        // Se houver uma agenda ativa, cancela seus alarmes
                        if (selectedAgenda.isNotEmpty()) {
                            val previousTasks = agendas.find { it.first == selectedAgenda }?.second ?: emptyList()
                            cancelAlarmsForSelectedAgenda(context, previousTasks)
                        }
                        // Atualiza a agenda selecionada
                        selectedAgenda = agenda
                        saveLastSelectedAgenda(context, agenda)
                        // Salva o timestamp de ativação para essa agenda
                        saveActivationTimestamp(context, agenda, System.currentTimeMillis())
                        agendaActivationId++  // Incrementa para indicar nova ativação
                        val currentActivationId = agendaActivationId

                        Log.d("AgendaData", "Tarefas para a agenda '$agenda': $agendaTasks")
                        scheduleAlarmsForSelectedAgenda(context, agenda, agendaTasks) {
                            if (agendaActivationId == currentActivationId) {
                                if (selectedAgenda != "") {
                                    Log.d("Alarm", "oldSelectedAgenda: $selectedAgenda")
                                    selectedAgenda = ""
                                    saveLastSelectedAgenda(context, "")
                                    Log.d("Alarm", "selectedAgenda: $selectedAgenda ($agendaActivationId) currentActivationId: $currentActivationId")
                                }
                            } else {
                                Log.d("Alarm", "Token alterado; callback não executado.")
                            }
                        }
                        Toast.makeText(context, Lang.get(10, context), Toast.LENGTH_SHORT).show()
                    }
                    Log.d("onSelectAgenda", "selectedAgenda: $selectedAgenda")
                }

            )

            "TaskPage" -> TaskPage(
                settings = settings.toMutableMap(),
                tasks = agendas.find { it.first == agendaToList }?.second ?: emptyList(),
                agendaToList = agendaToList,
                onSettingsClick = { currentPage = "Settings" },
                onBack = {
                    currentPage = "Home"
                    saveLastPage(context, "Home")
                },
                onToggleTask = { updatedTask ->
                    Log.d(
                        "TaskToggle",
                        "Tarefa ${updatedTask.name} agora está: ${updatedTask.enabled}"
                    )
                    val updatedAgendas = agendas.map { agenda ->
                        if (agenda.first == agendaToList) {
                            val updatedTasks = agenda.second.map { task ->
                                if (task.name == updatedTask.name) updatedTask else task
                            }
                            agenda.first to updatedTasks
                        } else agenda
                    }
                    updateData(newAgendas = updatedAgendas)
                },
                onAddTask = { task ->
                    val updatedAgendas = agendas.map { agenda ->
                        if (agenda.first == agendaToList) {
                            agenda.first to (agenda.second + task)
                        } else agenda
                    }
                    updateData(newAgendas = updatedAgendas)
                },
                onEditTask = { index, task ->
                    val updatedAgendas = agendas.map { agenda ->
                        if (agenda.first == agendaToList) {
                            val updatedTasks = agenda.second.toMutableList().apply {
                                this[index] = task
                            }
                            agenda.first to updatedTasks
                        } else agenda
                    }
                    updateData(newAgendas = updatedAgendas)
                },
                onDeleteTask = { taskToDelete ->
                    val updatedAgendas = agendas.map { agenda ->
                        if (agenda.first == agendaToList) {
                            val updatedTasks = agenda.second.toMutableList().apply {
                                remove(taskToDelete) // Remover a tarefa diretamente
                            }
                            agenda.first to updatedTasks
                        } else agenda
                    }
                    updateData(newAgendas = updatedAgendas)
                }
            )

            "Settings" -> SettingsPage(
                settings = settings.toMutableMap(),
                onBack = {
                    currentPage = if (agendas.none { it.first == agendaToList }) {"Home"} else {getLastPage(context)}
                },
                onSave = { newSettings ->
                    settings = newSettings
                    updateData(newSettings = newSettings)
                },
                onBackupImported = { newSettings, newAgendas ->
                    settings = newSettings
                    agendas = newAgendas
                    updateData(newSettings = newSettings, newAgendas = newAgendas)
                },
                onReset = { resetData() } // Chamando a função resetData
            )
        }
    }
}

@Composable
fun SettingsPage(
    settings: MutableMap<String, String>,
    onBack: () -> Unit,
    onSave: (MutableMap<String, String>) -> Unit,
    onBackupImported: (Map<String, String>, List<Pair<String, List<Task>>>) -> Unit,
    onReset: () -> Unit // Adicionando a função de reset
) {
    val backColor = MaterialTheme.colorScheme.background
    val foreColor = MaterialTheme.colorScheme.onBackground
    val lightBackColor = LocalExtraColors.current.lightBackground

    val context = LocalContext.current
    val appVersion = getAppVersion(context)

    val saveBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let {
                salvarBackup(context, it)
            }
        }

    val importBackupLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                importarBackup(context, it)
                val agendasWithTasks =
                    loadDaylistFromXML(context) // Load the agendas and tasks from the XML
                onBackupImported(
                    settings,
                    agendasWithTasks
                ) // Pass the settings and tasks to the callback
            }
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backColor)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Header(
            title = Lang.get(11, context),
            onSettingsClick = {},
            headerBackgroundColor = lightBackColor
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            // Backup
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(lightBackColor, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        Text(Lang.get(12, context), color = foreColor, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { saveBackupLauncher.launch(getBackupFileName(context)) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = Lang.get(13, context),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { importBackupLauncher.launch(arrayOf("*/*")) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = Lang.get(14, context),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Button(
                                onClick = { onReset() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.White
                                )
                            ) {
                                Text(
                                    text = Lang.get(15, context),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }


            // Versão do app
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(lightBackColor, RoundedCornerShape(8.dp))
                        .padding(16.dp)
                ) {
                    Column {
                        val appName = getAppName(context)
                        Text(
                            appName,
                            color = foreColor,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("${Lang.get(16, context)} $appVersion", color = foreColor, fontSize = 18.sp)
                        Text(
                            "© ${Calendar.getInstance().get(Calendar.YEAR)} Asen Lab Corporation\n${Lang.get(17, context)}",
                            color = foreColor,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        Footer(
            title = Lang.get(18, context),
            onClick = onBack,
            footerBackgroundColor = LocalExtraColors.current.lightBackground
        )
    }
}


@Composable
fun AgendaScreen(
    agendaToEdit: String? = null,
    existingAgendas: List<String>,  // Lista de agendas existentes para validar duplicidade
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val foreColor = MaterialTheme.colorScheme.onBackground
    val fieldBackColor = MaterialTheme.colorScheme.surface
    val lightBackColor = LocalExtraColors.current.lightBackground

    val context = LocalContext.current
    var name by remember { mutableStateOf(agendaToEdit ?: "") }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDismiss() }
        )
        Box(
            modifier = Modifier
                .width(350.dp)
                .background(lightBackColor, RoundedCornerShape(8.dp))
                .zIndex(1f)
                .clickable(enabled = false, onClick = {})
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                BasicTextField(
                    value = name,
                    onValueChange = { if (it.length <= 24) name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .background(fieldBackColor, RoundedCornerShape(4.dp))
                        .padding(16.dp),
                    textStyle = TextStyle(
                        color = foreColor,
                        fontSize = 26.sp
                    ),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            // Verifica se o nome já existe (exceto o nome atual da agenda sendo editada)
                            if (existingAgendas.contains(name) && name != agendaToEdit) {
                                Toast.makeText(
                                    context,
                                    Lang.get(19, context),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onSave(name)  // Salva a agenda com o novo nome
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = foreColor
                    )
                ) {
                    Text(
                        text = if (agendaToEdit != null) Lang.get(20, context) else Lang.get(21, context),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp)
                    )
                }
            }
        }
    }
}



fun cancelAlarmsForSelectedAgenda(context: Context, tasks: List<Task>) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    tasks.forEach { task ->
        if (task.enabled) {  // Verifica se a tarefa está habilitada antes de cancelar o alarme
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("task_name", task.name)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                task.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            Log.d("Alarm", "Cancelando alarme para tarefa: ${task.name}")

            alarmManager.cancel(pendingIntent)
        }
    }
}

fun scheduleAlarmsForSelectedAgenda(
    context: Context,
    agenda: String,  // Nome da agenda
    tasks: List<Task>,  // Lista de tasks
    onLastTaskCompleted: () -> Unit // Callback para limpar selectedAgenda após o último alarme ter disparado
) {
    val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    val isAtLeastApi23 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

    val enabledTasks = tasks.filter { it.enabled }
    if (enabledTasks.isEmpty()) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (!alarmManager.canScheduleExactAlarms()) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            context.startActivity(intent)
            Log.d("Alarm", "Permissão para alarmes exatos não concedida.")
            return
        }
    }

    var lastTriggerTime: Long = 0L
    enabledTasks.forEach { task ->
        val parts = task.afterHour.split(":")
        val hours = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val minutes = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val delayMs = ((hours * 60) + minutes) * 60 * 1000L
        val currentTime = System.currentTimeMillis()
        val triggerTime = currentTime + delayMs

        lastTriggerTime = max(lastTriggerTime, triggerTime)
        val formattedTime = String.format("%02d:%02d", hours, minutes)

        Log.d(
            "Alarm",
            "Task: ${task.name} | Current time: $currentTime | Delay: $delayMs ms | Trigger time: $triggerTime"
        )

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("task_name", task.name)
            putExtra("trigger_time", formattedTime)
            putExtra("agenda_name", agenda)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (isAtLeastApi23) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    val delayDuration = lastTriggerTime - System.currentTimeMillis() + 1000L
    if (delayDuration > 0) {
        GlobalScope.launch(Dispatchers.Main) {
            delay(delayDuration)
            Log.d("Alarm", "Todos os alarmes dispararam (ou já deveriam ter disparado).")
            onLastTaskCompleted() // O callback fará a verificação do token
        }
    } else {
        onLastTaskCompleted()
    }
}

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskName = intent.getStringExtra("task_name")
        val triggerTime = intent.getStringExtra("trigger_time")
        val agendaName = intent.getStringExtra("agenda_name")
        Log.d(
            "NotificationReceiver",
            "Alarme disparado para: $taskName às $triggerTime da agenda: $agendaName"
        )

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "task_channel",
                "Alarm Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for task notifications"
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(
                    soundUri, AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            notificationManager.createNotificationChannel(channel)
        }

        // A cor definida aqui é a cor de acento (tint) e não o fundo completo
        val accentColor = ContextCompat.getColor(context, R.color.bluesky)

        val notification = NotificationCompat.Builder(context, "task_channel")
            .setContentTitle("$agendaName")
            .setContentText("$triggerTime • $taskName")
            .setSmallIcon(R.drawable.daylist_notify) // Substitua pelo seu ícone real
            .setColor(accentColor)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
            .setVibrate(longArrayOf(0, 500, 1000))
            .setAutoCancel(true)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$triggerTime • $taskName")
            )
            .build()

        notificationManager.notify(taskName.hashCode(), notification)
    }
}
@Composable
fun AgendaPage(
    settings: MutableMap<String, String>,
    agendas: List<String>,
    selectedAgenda: String?,
    onSettingsClick: () -> Unit,
    onStart: (String) -> Unit,
    onAddAgenda: (String) -> Unit,
    onEditAgenda: (Int, String) -> Unit,
    onDeleteAgenda: (String) -> Unit,
    onSelectAgenda: (String) -> Unit
) {
    val isPremium = true

    val backColor = MaterialTheme.colorScheme.background
    val lightBackColor = LocalExtraColors.current.lightBackground

    var isAgendaScreenVisible by remember { mutableStateOf(false) }
    var agendaToEdit by remember { mutableStateOf<String?>(null) }
    var expandedAgendaKey by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    LaunchedEffect(true) {
        saveLastPage(context, "Home")
    }

    val sortedAgendas = agendas.sorted()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backColor)
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                title = "Daylist",
                onSettingsClick = onSettingsClick,
                headerBackgroundColor = lightBackColor
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                itemsIndexed(sortedAgendas) { _, agenda ->
                    AgendaRow(
                        agenda = agenda,
                        expandedAgendaKey = expandedAgendaKey,
                        setExpandedAgendaKey = { key ->
                            expandedAgendaKey = if (expandedAgendaKey == key) null else key
                        },
                        isSelected = (selectedAgenda == agenda),
                        onSelectAgenda = { onSelectAgenda(agenda) },
                        onStartEditAgenda = {
                            agendaToEdit = agenda
                            isAgendaScreenVisible = true
                        },
                        onDeleteAgenda = { onDeleteAgenda(agenda) },
                        onEditTaskPage = { onStart(agenda) }
                    )
                }
                item { Spacer(modifier = Modifier.height(138.dp)) }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = {
                    if (agendas.size >= 1 && !isPremium) {
                        Toast.makeText(context, Lang.get(30, context), Toast.LENGTH_SHORT).show()
                    } else {
                        expandedAgendaKey = null
                        isAgendaScreenVisible = true
                        agendaToEdit = null
                    }
                },
                modifier = Modifier.size(80.dp),
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                Text(
                    "+",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 30.sp),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (isAgendaScreenVisible) {
        AgendaScreen(
            agendaToEdit = agendaToEdit,
            existingAgendas = agendas,
            onDismiss = {
                isAgendaScreenVisible = false
                agendaToEdit = null
                expandedAgendaKey = null
            },
            onSave = { newName ->
                if (agendaToEdit == null) {
                    onAddAgenda(newName)
                } else {
                    val index = agendas.indexOf(agendaToEdit)
                    if (index >= 0) {
                        onEditAgenda(index, newName)
                        if (selectedAgenda == agendaToEdit) {
                            onSelectAgenda(newName)
                        }
                    }
                }
                isAgendaScreenVisible = false
                agendaToEdit = null
                expandedAgendaKey = null
            }
        )
    }
}



@Composable
fun AgendaRow(
    agenda: String,
    expandedAgendaKey: String?,
    setExpandedAgendaKey: (String) -> Unit,
    isSelected: Boolean,
    onSelectAgenda: () -> Unit,
    onStartEditAgenda: () -> Unit,
    onDeleteAgenda: () -> Unit,
    onEditTaskPage: () -> Unit
) {
    val context = LocalContext.current
    val roundMax = 30
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { setExpandedAgendaKey(agenda) } // Clique para expandir ou recolher
            .padding(4.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface, // Muda cor para 'tertiary' quando selecionado
                RoundedCornerShape(roundMax.dp)
            )
            .border(
                2.dp,
                if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface, // A borda também muda com o estado de seleção
                RoundedCornerShape(roundMax.dp)
            ),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .background(
                    LocalExtraColors.current.lightBackground,
                    RoundedCornerShape(roundMax.dp)
                )
                .height(62.dp)
                .padding(12.dp)
        ) {
            Text(
                text = if (agenda.length > 18) "${agenda.take(18)}.." else agenda,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .clickable { onSelectAgenda() } // Ativa/Desativa a Agenda
                .background(
                    color = if (isSelected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(roundMax.dp)
                )
                .height(62.dp)
                .padding(12.dp)
        ) {
            Text(
                text = "       ",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
// Dropdown expandido para editar ou excluir a agenda
    if (expandedAgendaKey == agenda) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(LocalExtraColors.current.lightBackground, RoundedCornerShape(8.dp))
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onEditTaskPage,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Text(
                        text = Lang.get(22, context),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                    )
                }

                Text(
                    text = "|",
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.surface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp)
                )

                Button(
                    onClick = onStartEditAgenda,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Text(
                        text = Lang.get(23, context),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                    )
                }

                Text(
                    text = "|",
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.surface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp)
                )

                Button(
                    onClick = onDeleteAgenda,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Text(
                        text = Lang.get(24, context),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                    )
                }
            }
        }
    }
}


@Composable
fun TaskScreen(
    taskToEdit: Task? = null,
    existingTasks: List<Task>,
    onDismiss: () -> Unit,
    onSave: (Task) -> Unit
) {
    val foreColor = MaterialTheme.colorScheme.onBackground
    val fieldBackColor = MaterialTheme.colorScheme.surface
    val lightBackColor = LocalExtraColors.current.lightBackground

    val context = LocalContext.current
    var name by remember { mutableStateOf(taskToEdit?.name ?: "") }
    var afterhour by remember { mutableStateOf(taskToEdit?.afterHour ?: "00:00") }
    var showTimePicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() }
            .windowInsetsPadding(WindowInsets.ime) // Move tudo para cima quando teclado aparece
            .consumeWindowInsets(WindowInsets.ime),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(350.dp)
                .zIndex(1f)
                .background(lightBackColor, RoundedCornerShape(8.dp))
                .clickable(enabled = false, onClick = {})
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    Lang.get(25, context),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = foreColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicTextField(
                    value = name,
                    onValueChange = { if (it.length <= 264) name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .background(fieldBackColor, RoundedCornerShape(4.dp))
                        .padding(16.dp),
                    textStyle = TextStyle(color = foreColor, fontSize = 26.sp)
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    Lang.get(32, context), // "Após quanto tempo"
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                    color = foreColor
                )
                Spacer(modifier = Modifier.height(8.dp))

                val (hour, minute) = afterhour.split(":").map { it.toIntOrNull() ?: 0 }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .background(lightBackColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AndroidView(
                        factory = { context ->
                            NumberPicker(context).apply {
                                minValue = 0
                                maxValue = 99
                                value = hour
                                setOnValueChangedListener { _, _, newVal ->
                                    afterhour = "%02d:%02d".format(newVal, afterhour.split(":").getOrNull(1)?.toIntOrNull() ?: 0)
                                }
                            }
                        },
                        modifier = Modifier.width(100.dp)
                    )

                    Text(":", color = foreColor, fontSize = 24.sp, modifier = Modifier.padding(horizontal = 8.dp))

                    AndroidView(
                        factory = { context ->
                            NumberPicker(context).apply {
                                minValue = 0
                                maxValue = 59
                                value = minute
                                setOnValueChangedListener { _, _, newVal ->
                                    afterhour = "%02d:%02d".format(afterhour.split(":").getOrNull(0)?.toIntOrNull() ?: 0, newVal)
                                }
                            }
                        },
                        modifier = Modifier.width(100.dp)
                    )
                }


                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (name.isNotBlank() && afterhour.matches(Regex("^\\d{2}:\\d{2}\$"))) {
                            val newTask = Task(name, afterhour, taskToEdit?.enabled ?: true)
                            val isDuplicate = existingTasks.any {
                                it.name.trim().equals(name.trim(), ignoreCase = true) &&
                                        (taskToEdit == null || it != taskToEdit)
                            }
                            if (isDuplicate) {
                                Toast.makeText(
                                    context,
                                    Lang.get(27, context),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                onSave(newTask)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = foreColor
                    )
                ) {
                    Text(
                        text = if (taskToEdit != null) Lang.get(22, context) else Lang.get(21, context),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 26.sp)
                    )
                }
            }
        }
    }
}

@Composable
fun TaskPage(
    settings: MutableMap<String, String>,
    tasks: List<Task>,
    agendaToList: String,
    onSettingsClick: () -> Unit,
    onBack: () -> Unit,
    onToggleTask: (Task) -> Unit,
    onAddTask: (Task) -> Unit,
    onEditTask: (Int, Task) -> Unit,
    onDeleteTask: (Task) -> Unit,  // Alterado para receber a Task diretamente
) {
    val backColor = MaterialTheme.colorScheme.background
    val foreColor = MaterialTheme.colorScheme.onBackground
    val lightBackColor = LocalExtraColors.current.lightBackground

    var isTaskScreenVisible by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }
    var expandedTaskKey by remember { mutableStateOf<String?>(null) } // Chave para controle do dropdown
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    LaunchedEffect(true) { saveLastPage(context, "TaskPage") }

    // Ordenando as tarefas pelo valor de afterHour, como números
    val sortedTasks = tasks.sortedBy { task ->
        val (hours, minutes) = task.afterHour.split(":").map { it.toIntOrNull() ?: 0 }
        hours * 60 + minutes
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backColor)
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Header(
                title = agendaToList, // Mostra o nome da agenda selecionada
                onSettingsClick = onSettingsClick,
                headerBackgroundColor = lightBackColor
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                itemsIndexed(sortedTasks) { index, task ->  // Usando sortedTasks aqui
                    TaskRow(
                        task = task,
                        expandedTaskKey = expandedTaskKey,
                        setExpandedTaskKey = { key ->
                            expandedTaskKey = if (expandedTaskKey == key) null else key
                        },
                        onToggleTask = { updatedTask -> onToggleTask(updatedTask) },
                        onStartEditTask = {
                            taskToEdit = task
                            isTaskScreenVisible = true
                        },
                        onDeleteTask = { onDeleteTask(task) }  // Alterado para passar a Task diretamente
                    )
                }
                item { Spacer(modifier = Modifier.height(146.dp)) }
            }

            Footer(
                title = Lang.get(18, context),
                onClick = onBack,
                footerBackgroundColor = LocalExtraColors.current.lightBackground
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 64.dp)
                .padding(32.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            FloatingActionButton(
                onClick = {
                    expandedTaskKey = null
                    isTaskScreenVisible = true
                    taskToEdit = null
                },
                modifier = Modifier.size(80.dp),
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = foreColor
            ) {
                Text(
                    "+",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 30.sp),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }

    if (isTaskScreenVisible) {
        TaskScreen(
            taskToEdit = taskToEdit,
            existingTasks = tasks,
            onDismiss = {
                isTaskScreenVisible = false
                taskToEdit = null
                expandedTaskKey = null
            },
            onSave = { newTask ->
                if (taskToEdit == null) {
                    onAddTask(newTask)
                } else {
                    val index = tasks.indexOf(taskToEdit)
                    if (index >= 0) onEditTask(index, newTask)
                }
                isTaskScreenVisible = false
                taskToEdit = null
                expandedTaskKey = null
            }
        )
    }
}

@Composable
fun TaskRow(
    task: Task,
    expandedTaskKey: String?,
    setExpandedTaskKey: (String) -> Unit,
    onToggleTask: (Task) -> Unit,
    onStartEditTask: () -> Unit,
    onDeleteTask: (Task) -> Unit  // Alterado para receber a Task diretamente
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { setExpandedTaskKey(task.name) },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val roundMax = 30
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .background(
                    color = if (task.enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(roundMax.dp)
                )
                .border(
                    width = 2.dp,
                    color = if (task.enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(roundMax.dp)
                )
        ) {
            // Nome da task
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        LocalExtraColors.current.lightBackground,
                        RoundedCornerShape(roundMax.dp)
                    )
                    .height(76.dp)
                    .padding(12.dp)
            ) {
                Text(
                    text = if (task.name.length > 33) "${task.name.take(33)}.." else task.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            // Box para alternar o estado 'enabled'
            Box(
                modifier = Modifier
                    .wrapContentWidth()
                    .clickable {
                        val updatedTask = task.copy(enabled = !task.enabled)
                        Log.d(
                            "TaskToggle",
                            "Antes: ${task.enabled}, Depois: ${updatedTask.enabled}"
                        )
                        onToggleTask(updatedTask)
                        Toast.makeText(
                            context,
                            if (updatedTask.enabled) Lang.get(28, context) else Lang.get(29, context),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .background(
                        color = if (task.enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(roundMax.dp)
                    )
                    .height(76.dp)
                    .padding(12.dp)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = task.afterHour,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp),
                    modifier = Modifier.align(Alignment.Center)
                )
            }

        }
    }
    // Dropdown expandido para editar ou excluir a task
    if (expandedTaskKey == task.name) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .background(LocalExtraColors.current.lightBackground, RoundedCornerShape(8.dp))
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Botão "Editar"
                Button(
                    onClick = onStartEditTask,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Text(
                        Lang.get(22, context),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                    )
                }
                // Separador "|"
                Text(
                    text = "|",
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.surface,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 28.sp)
                )
                // Botão "Excluir"
                Button(
                    onClick = { onDeleteTask(task) },  // Passando a Task diretamente
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Transparent
                    )
                ) {
                    Text(
                        Lang.get(24, context),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                    )
                }
            }
        }
    }
}
