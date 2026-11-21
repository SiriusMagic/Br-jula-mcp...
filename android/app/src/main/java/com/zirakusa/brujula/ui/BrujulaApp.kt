package com.zirakusa.brujula.ui

import androidx.compose.runtime.Composable
import com.zirakusa.brujula.ui.screens.CalibrationScreen
import com.zirakusa.brujula.ui.screens.CompassScreen
import com.zirakusa.brujula.ui.screens.McpScreen
import com.zirakusa.brujula.ui.screens.SettingsScreen

/** What the screens ask the phone for (the activity gives the real ones; tests leave them empty). */
class AppActions(val askLocation: () -> Unit = {}, val copy: (String) -> Unit = {}, val goToHub: () -> Unit = {})

/** One page at a time: the compass, «Calibrar la Brújula», Ajustes or Modo MCP. */
@Composable fun BrujulaApp(state: CompassState, actions: AppActions = AppActions()) = when (state.page) {
    Page.COMPASS -> CompassScreen(state, actions.askLocation) { state.page = Page.SETTINGS }
    Page.CALIBRATION -> CalibrationScreen(state::closeCalibration)
    Page.SETTINGS -> SettingsScreen(state, onMcp = { state.page = Page.MCP }, onCalibrate = { state.openCalibration(Page.SETTINGS) }) {
        state.page = Page.COMPASS
    }
    Page.MCP -> McpScreen(actions.copy, actions.goToHub) { state.page = Page.SETTINGS }
}
