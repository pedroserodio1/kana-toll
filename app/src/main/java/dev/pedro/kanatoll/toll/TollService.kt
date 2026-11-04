package dev.pedro.kanatoll.toll

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.pedro.kanatoll.KanaApp
import dev.pedro.kanatoll.data.Card
import dev.pedro.kanatoll.ui.KanaTheme
import dev.pedro.kanatoll.ui.TollScreen
import dev.pedro.kanatoll.widget.KanaWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Pedágio: quando um app da sua lista abre, mostra uma rodada de 3 cartões por cima dele
 * (overlay de acessibilidade, que recebe foco e teclado). "Agora não" fica sempre visível.
 * Pagar dá uma carência por app; pular só dá uma pausa curta, para a tela não voltar na hora.
 */
class TollService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var overlay: View? = null
    private var owner: OverlayOwner? = null
    private var tollPackage: String? = null
    private var loading = false

    private val container get() = (application as KanaApp).container

    override fun onServiceConnected() {
        instance = this
        TollLog.add("serviço conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // O próprio app (inclui o overlay), a barra do sistema e o teclado não contam como "abrir um app".
        if (pkg == packageName || pkg == "com.android.systemui" || pkg == imePackage()) return

        val showing = tollPackage
        if (showing != null) {
            if (pkg == showing) return
            TollLog.add("saiu de $showing para $pkg: overlay fechado")
            dismiss() // trocou de app (ou foi para a tela inicial) com o overlay aberto
        } else if (loading) {
            return
        }

        val settings = container.settings
        if (pkg !in settings.tollApps) return
        if (container.clock.millis() < settings.tollQuietUntil(pkg)) return

        loading = true
        scope.launch {
            val cards = container.reviews.tollCards(ROUND_SIZE)
            if (cards.isEmpty()) {
                loading = false
                TollLog.add("sem cartões para cobrar em $pkg (nenhum kana estudado ainda)")
                return@launch
            }
            show(pkg, cards)
        }
    }

    override fun onInterrupt() {
        TollLog.add("onInterrupt")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        TollLog.add("SERVIÇO DESCONECTADO")
        dismiss()
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun imePackage(): String =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.let { ComponentName.unflattenFromString(it)?.packageName }.orEmpty()

    private fun show(pkg: String, cards: List<Card>) {
        val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() }
            .getOrDefault(pkg)
        val overlayOwner = OverlayOwner()
        val controller = TollController(container, scope, cards, onFinished = { finish(pkg, paid = true) })
        val compose = ComposeView(this).apply {
            setContent {
                KanaTheme(container.themeMode.value) {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.systemBarsPadding()) { TollScreen(controller, label) { finish(pkg, paid = false) } }
                    }
                }
            }
        }
        // O botão Voltar equivale a "agora não" (com o teclado aberto, o primeiro Voltar só fecha o teclado).
        val root = OverlayRoot(this) { finish(pkg, paid = false) }.apply {
            // Os donos vão na RAIZ da janela: o Compose procura o ciclo de vida a partir dela, não do ComposeView.
            setViewTreeLifecycleOwner(overlayOwner)
            setViewTreeSavedStateRegistryOwner(overlayOwner)
            setViewTreeViewModelStoreOwner(overlayOwner)
            addView(compose)
        }
        // Focável (sem FLAG_NOT_FOCUSABLE): é o que deixa o teclado funcionar, como o spike confirmou.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE,
        ).apply { softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE }
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).addView(root, params) }
            .onSuccess {
                overlay = root
                owner = overlayOwner
                tollPackage = pkg
                TollLog.add("pedágio mostrado em $pkg (${cards.size} cartões)")
            }
            .onFailure {
                overlayOwner.destroy()
                loading = false
                TollLog.add("overlay FALHOU em $pkg: ${it.message}")
            }
    }

    /** Fim da rodada: pagou (respondeu todos) ou pulou. Registra, define a pausa do app e fecha. */
    private fun finish(pkg: String, paid: Boolean) {
        if (overlay == null) return // já fechado (botão e tecla Voltar quase juntos)
        val settings = container.settings
        val now = container.clock.millis()
        if (paid) {
            settings.tollPaidCount += 1
            settings.setTollQuietUntil(pkg, now + settings.tollGraceMinutes * 60_000L)
            TollLog.add("pago: $pkg (carência de ${settings.tollGraceMinutes} min)")
        } else {
            settings.tollSkipCount += 1
            settings.setTollQuietUntil(pkg, now + SKIP_PAUSE_MS)
            TollLog.add("agora não: $pkg")
        }
        dismiss()
        scope.launch { KanaWidget().updateAll(this@TollService) }
    }

    private fun dismiss() {
        overlay?.let { runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) } }
        overlay = null
        owner?.destroy()
        owner = null
        tollPackage = null
        loading = false
    }

    companion object {
        private const val ROUND_SIZE = 3
        /** Depois de "agora não", o mesmo app fica em paz por 2 min (cada tela do app gera um evento de janela). */
        private const val SKIP_PAUSE_MS = 2 * 60_000L

        @Volatile var instance: TollService? = null

        /** O serviço está marcado como ligado nas configurações de acessibilidade? */
        fun isEnabled(context: Context): Boolean {
            val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            val me = ComponentName(context, TollService::class.java)
            return list.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

/** Raiz do overlay: transforma o Voltar em "agora não". */
private class OverlayRoot(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

/** O Compose precisa de ciclo de vida, estado salvo e ViewModelStore mesmo fora de uma Activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}
