package com.pocketvm.data.model

/** Lifecycle state of a virtual machine instance. */
enum class VmState {
    STOPPED,
    BOOTING,
    RUNNING,
    PAUSED,
    ERROR
}
