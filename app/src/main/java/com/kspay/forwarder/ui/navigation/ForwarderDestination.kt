package com.kspay.forwarder.ui.navigation

sealed class ForwarderDestination(val route: String) {
    data object FareEntry : ForwarderDestination("fare_entry")
    data object History : ForwarderDestination("history")
    data object TidSettings : ForwarderDestination("tid_settings")

    /** Parameterized: the screen observes live Room updates for this specific outTradeNo. */
    data object InProgress : ForwarderDestination("in_progress/{outTradeNo}") {
        fun routeFor(outTradeNo: String) = "in_progress/$outTradeNo"
    }

    data object Result : ForwarderDestination("result/{outTradeNo}") {
        fun routeFor(outTradeNo: String) = "result/$outTradeNo"
    }
}
