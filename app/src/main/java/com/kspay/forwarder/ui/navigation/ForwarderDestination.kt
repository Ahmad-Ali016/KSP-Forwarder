package com.kspay.forwarder.ui.navigation

sealed class ForwarderDestination(val route: String) {
    data object FareEntry : ForwarderDestination("fare_entry")
    data object InProgress : ForwarderDestination("in_progress")
    data object Result : ForwarderDestination("result")
    data object History : ForwarderDestination("history")
}
