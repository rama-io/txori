package com.rama.txori.activities

interface EditModeToggle {
    fun onEditButtonClicked() {}
    fun onCloseButtonClicked() {}
    fun syncTopBarButtons(host: MainActivity) {}
}

