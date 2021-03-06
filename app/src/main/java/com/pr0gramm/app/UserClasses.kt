package com.pr0gramm.app

import android.graphics.Color
import androidx.annotation.ColorInt
import com.pr0gramm.app.model.config.Config
import com.pr0gramm.app.services.config.ConfigService
import com.pr0gramm.app.util.doInBackground
import kotlinx.coroutines.flow.*
import java.util.*

class UserClassesService(configObservable: Flow<Config>) {
    data class UserClass(val name: String, val symbol: String, @get:ColorInt val color: Int)

    private var userClasses: List<UserClass> = listOf()

    private val mutableChanges = MutableStateFlow(0)

    val onChange: Flow<Unit> = mutableChanges.map { Unit }

    init {
        doInBackground {
            configObservable
                    .map { config -> config.userClasses.map { parseClass(it) } }
                    .distinctUntilChanged()
                    .collect {
                        userClasses = it

                        // publish changes
                        mutableChanges.value++
                    }
        }
    }

    private fun parseClass(inputValue: Config.UserClass): UserClass {
        val color = try {
            Color.parseColor(inputValue.color)
        } catch (_: Exception) {
            Color.WHITE
        }

        return UserClass(inputValue.name.toUpperCase(Locale.GERMANY), inputValue.symbol, color)
    }

    fun get(mark: Int): UserClass {
        return userClasses.getOrNull(mark) ?: UserClass("User", "?", Color.WHITE)
    }

    companion object {
        operator fun invoke(configService: ConfigService) = UserClassesService(configService.observeConfig())
    }
}
