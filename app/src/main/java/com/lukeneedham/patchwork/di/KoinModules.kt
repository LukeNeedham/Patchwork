package com.lukeneedham.patchwork.di

import com.lukeneedham.patchwork.CollageEditorViewModel
import com.lukeneedham.patchwork.CollageSaver
import com.lukeneedham.patchwork.PhotoLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

object KoinModules {
    private val data = module {
        single { PhotoLoader(androidContext()) }
        single { CollageSaver(androidContext()) }
    }

    private val viewModels = module {
        viewModelOf(::CollageEditorViewModel)
    }

    val modules: List<Module> = listOf(data, viewModels)
}
