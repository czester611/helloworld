package com.fitlab.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fitlab.app.AppContainer
import com.fitlab.app.FitLabApp

/** Dostęp do kontenera zależności z Composable. */
@Composable
fun rememberContainer(): AppContainer = (LocalContext.current.applicationContext as FitLabApp).container

/** Tworzy ViewModel przez fabrykę z kontenerem (ręczne DI). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline create: (AppContainer) -> VM): VM {
    val container = rememberContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container) } })
}
