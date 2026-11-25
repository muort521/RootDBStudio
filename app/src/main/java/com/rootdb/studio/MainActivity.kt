package com.rootdb.studio

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rootdb.studio.ui.BrowserScreen
import com.rootdb.studio.ui.CryptoScreen
import com.rootdb.studio.ui.DbScreen
import com.rootdb.studio.ui.HomeScreen
import com.rootdb.studio.ui.PasswordDialog
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingOpenPath.value = intent?.getStringExtra(EXTRA_OPEN_PATH)
        // 只在首次创建时消费外部打开请求，避免重建（如旋转）时重复打开
        if (savedInstanceState == null) pendingOpenUri.value = openUriOf(intent)
        setContent { RootDbApp() }
    }

    override fun onResume() {
        super.onResume()
        appResumeTick.value = appResumeTick.value + 1
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpenPath.value = intent.getStringExtra(EXTRA_OPEN_PATH)
        pendingOpenUri.value = openUriOf(intent)
    }

    companion object {
        const val EXTRA_OPEN_PATH = "openPath"

        /** 从 VIEW / SEND intent 里取出要打开的数据库 Uri */
        fun openUriOf(intent: android.content.Intent?): Uri? {
            if (intent == null) return null
            return when (intent.action) {
                android.content.Intent.ACTION_VIEW -> intent.data
                android.content.Intent.ACTION_SEND -> {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Uri>(android.content.Intent.EXTRA_STREAM)
                }
                else -> null
            }
        }
    }
}

/** 由 intent 传入、待 UI 消费一次的目标数据库路径 */
private val pendingOpenPath = mutableStateOf<String?>(null)

/** 由其他应用「打开方式」传入、待 UI 消费一次的目标 Uri */
private val pendingOpenUri = mutableStateOf<Uri?>(null)

/** 每次 onResume 自增，供 Compose 侧感知「回到应用」 */
private val appResumeTick = mutableStateOf(0)

@Composable
private fun RootDbApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val c = remember { AppController(ctx.applicationContext, scope) }

    // 明暗跟随系统 + Material You 动态取色（壁纸取色）
    val themeController = remember { ThemeController(colorSchemeMode = ColorSchemeMode.MonetSystem) }

    val openTarget = pendingOpenPath.value
    LaunchedEffect(openTarget) {
        val target = openTarget?.trim().orEmpty()
        if (target.isNotEmpty()) {
            pendingOpenPath.value = null
            c.openDb(target, null)
        }
    }

    // 其他应用「打开方式 / 分享」送来的数据库文件
    val openUri = pendingOpenUri.value
    LaunchedEffect(openUri) {
        if (openUri != null) {
            pendingOpenUri.value = null
            c.openFromUri(openUri)
        }
    }

    // 从「所有文件访问权限」等系统设置页返回时，重新读取授权状态，避免仍显示未授权
    val resumeTick = appResumeTick.value
    LaunchedEffect(resumeTick) {
        if (resumeTick > 0) c.onAppResume()
    }

    LaunchedEffect(c.message) {
        if (c.message != null) {
            delay(2800)
            c.message = null
        }
    }

    MiuixTheme(controller = themeController) {
        BackHandler(enabled = c.screen != Screen.Home) {
            when (c.screen) {
                is Screen.Table -> c.backToDbHome()
                is Screen.Crypto -> c.closeCrypto()
                Screen.Browser -> c.backFromBrowser()
                else -> c.closeDb()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
        ) {
            when (c.screen) {
                Screen.Home -> HomeScreen(c)
                Screen.Browser -> BrowserScreen(c)
                is Screen.Crypto -> CryptoScreen(c, (c.screen as Screen.Crypto).tab)
                else -> DbScreen(c)
            }

            c.message?.let { msg ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    insideMargin = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 20.dp,
                        vertical = 14.dp,
                    ),
                    onClick = null,
                ) {
                    Text(msg, fontSize = 13.sp)
                }
            }
        }
    }
}
