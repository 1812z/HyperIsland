package io.github.hyperisland.ui.home

data class HomeUiState(
    val moduleActive: Boolean? = null,
    val lsposedApiVersion: Int = 0,
    val focusProtocolVersion: Int = 0,
    val restarting: Boolean = false,
)
