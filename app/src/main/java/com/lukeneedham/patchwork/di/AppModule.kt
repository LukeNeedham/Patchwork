package com.lukeneedham.patchwork.di

import com.lukeneedham.patchwork.CollageEditorViewModel
import com.lukeneedham.patchwork.CollageSaver
import com.lukeneedham.patchwork.PhotoLoader
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    single { PhotoLoader(androidContext()) }
    single { CollageSaver(androidContext()) }
    viewModelOf(::CollageEditorViewModel)
}
