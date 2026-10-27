package dev.jcode.mobile

import android.app.Application
import dev.jcode.mobile.data.JCodeRepository

class JCodeApp : Application() {
    lateinit var repository: JCodeRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = JCodeRepository(this)
    }
}
