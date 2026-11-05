package com.saintseiya.coach

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.saintseiya.coach.capture.CaptureService
import com.saintseiya.coach.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()
    private var pendingStart = false

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val data = r.data
        if (r.resultCode == RESULT_OK && data != null) CaptureService.start(this, r.resultCode, data)
        else CoachApp.instance.prefs.lastError = "Autorização de captura negada ou cancelada."
    }
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        // mesmo sem permissão de notificação o serviço funciona; só não mostra o aviso.
        if (pendingStart) { pendingStart = false; launchProjection() }
    }

    private fun launchProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    fun startCapture() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingStart = true; notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else launchProjection()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoachTheme {
                val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris -> vm.importImages(uris) }
                val actions = remember {
                    Actions(
                        onStart = { startCapture() },
                        onStop = { CaptureService.stop(this) },
                        onPickImages = { pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                }
                AppRoot(vm, actions)
            }
        }
    }
}
