package com.bblvn.composebaseproject.features.uninstall

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.bbl.ads.modules.AdsManager
import com.bblvn.composebaseproject.FadeSlideUp
import com.bblvn.composebaseproject.MainActivity
import com.bblvn.composebaseproject.R
import com.bblvn.composebaseproject.ui.theme.AppColors
import com.bblvn.composebaseproject.ui.theme.ComposeBaseProjectTheme

/**
 * Đăng ký shortcut động "Uninstall" vào menu giữ icon ở launcher.
 *
 * Android KHÔNG cho app biết lúc nào nó sắp bị gỡ, nên không thể chặn đúng thời điểm đó. Cách
 * duy nhất còn lại là tự đặt một lối vào ngay cạnh nút gỡ của hệ thống: user giữ icon app sẽ
 * thấy mục "Uninstall" này. Gọi lại mỗi lần mở app đều an toàn (push đè shortcut cũ).
 */
fun createUninstallShortcut(context: Context) {
    runCatching {
        val intent = Intent(context, UninstallActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            // Thiếu 2 cờ này thì màn gỡ chồng lên chính task của app: thoát khỏi nó là rơi
            // thẳng vào app đang mở phía dưới, luồng giữ chân mất hết ý nghĩa.
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val shortcut = ShortcutInfoCompat.Builder(context, "shortcut_uninstall")
            .setShortLabel(context.getString(R.string.uninstall_shortcut_label))
            .setLongLabel(context.getString(R.string.uninstall_shortcut_label))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_trash))
            .setIntent(intent)
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}

private object UninstallRoutes {
    const val SPLASH = "uninstall_splash"
    const val NOTICE = "uninstall_notice"
    const val SURVEY = "uninstall_survey"
}

/**
 * MỘT Activity duy nhất cho cả luồng gỡ; 3 bước bên trong là 3 route của NavHost.
 *
 * Cố ý KHÔNG kế thừa BaseActivity: không muốn màn Welcome Back hay overlay chờ inter chen vào
 * luồng giữ chân này. Đổi lại phải tự gán [AdsManager.currentActivity] ở onResume.
 */
class UninstallActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ComposeBaseProjectTheme {
                UninstallFlow(onFinish = { finish() })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        AdsManager.getInstance().currentActivity = this
    }
}

@Composable
private fun UninstallFlow(onFinish: () -> Unit) {
    val context = LocalContext.current
    val nav = rememberNavController()

    /** Người dùng đổi ý: đưa thẳng về app, KHÔNG chen thêm quảng cáo nào nữa. */
    fun keepApp() {
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
            )
        }
        onFinish()
    }

    /** Bàn giao cho hộp thoại gỡ của hệ thống; máy nào chặn thì lui về trang thông tin ứng dụng. */
    fun uninstallAnyway() {
        val uri = Uri.fromParts("package", context.packageName, null)
        val delete = Intent(Intent.ACTION_DELETE, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(delete)
        } catch (_: ActivityNotFoundException) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
        onFinish()
    }

    NavHost(
        navController = nav,
        modifier = Modifier.background(AppColors.bgRoot),
        startDestination = UninstallRoutes.SPLASH,
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
    ) {
        composable(UninstallRoutes.SPLASH) {
            UninstallSplashScreen(
                onKeepApp = ::keepApp,
                onNext = {
                    // Splash chỉ là cổng chờ: bỏ nó khỏi back stack để Back không quay lại.
                    nav.navigate(UninstallRoutes.NOTICE) {
                        popUpTo(UninstallRoutes.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(
            UninstallRoutes.NOTICE,
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second },
        ) {
            UninstallNoticeScreen(
                onKeepApp = ::keepApp,
                onContinue = { nav.navigate(UninstallRoutes.SURVEY) },
            )
        }
        composable(
            UninstallRoutes.SURVEY,
            enterTransition = { FadeSlideUp.first },
            exitTransition = { FadeSlideUp.second },
        ) {
            UninstallSurveyScreen(
                onKeepApp = ::keepApp,
                onUninstall = ::uninstallAnyway,
            )
        }
    }
}
