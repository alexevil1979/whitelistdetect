package ru.whitelist.pulse.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.whitelist.pulse.R
import ru.whitelist.pulse.notify.ProbeNotifier
import ru.whitelist.pulse.ui.ProbeCoordinator
import ru.whitelist.pulse.ui.titleRes
import javax.inject.Inject

@AndroidEntryPoint
class ProbeTileService : TileService() {

    @Inject
    lateinit var coordinator: ProbeCoordinator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        val kind = coordinator.state.value.verdict?.kind
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && kind != null) {
                subtitle = getString(kind.titleRes())
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        qsTile?.state = Tile.STATE_ACTIVE
        qsTile?.updateTile()
        coordinator.run(scope)
        scope.launch {
            kotlinx.coroutines.delay(400)
            val state = coordinator.state.first { !it.scanning && it.verdict != null }
            val title = getString(state.verdict!!.kind.titleRes())
            Toast.makeText(applicationContext, title, Toast.LENGTH_LONG).show()
            ProbeNotifier.notify(applicationContext, title)
            qsTile?.apply {
                this.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = title
                updateTile()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
