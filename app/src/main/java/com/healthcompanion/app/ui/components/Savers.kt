package com.healthcompanion.app.ui.components

import androidx.compose.runtime.saveable.Saver
import java.time.LocalDate

val LocalDateSaver: Saver<LocalDate, String> = Saver(
    save = { it.toString() },
    restore = { LocalDate.parse(it) }
)

fun <T : Enum<T>> enumSaver(enumClass: Class<T>): Saver<T, String> = Saver(
    save = { it.name },
    restore = { java.lang.Enum.valueOf(enumClass, it) }
)

inline fun <reified T : Enum<T>> enumSaver(): Saver<T, String> = enumSaver(T::class.java)
