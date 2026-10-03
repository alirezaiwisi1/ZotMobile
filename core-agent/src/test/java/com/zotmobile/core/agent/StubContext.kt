package com.zotmobile.core.agent

/**
 * Marker for the seam used in tests. AgentToolsTest does not exercise command
 * execution (which requires Termux), so no runner stub is needed here; the
 * command path is covered by instrumentation tests on device.
 */
object TestMarkers {
    const val COMMAND_TESTS_RUN_ON_DEVICE = true
}
