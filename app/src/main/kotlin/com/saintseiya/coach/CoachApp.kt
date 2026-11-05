package com.saintseiya.coach

import android.app.Application
import com.saintseiya.coach.data.CoachDb
import com.saintseiya.coach.data.Prefs
import com.saintseiya.coach.data.Repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CoachApp : Application() {
    lateinit var repo: Repository
    lateinit var prefs: Prefs
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        repo = Repository(CoachDb.get(this))
        appScope.launch {
            // sessões que ficaram abertas por morte do processo viram "INTERRUPTED" (nunca "sucesso").
            repo.dao().closeDanglingSessions(System.currentTimeMillis())
            val id = repo.ensureAccount()
            if (prefs.activeAccountId == 0L) prefs.activeAccountId = id
        }
    }

    companion object { lateinit var instance: CoachApp }
}
