package com.pocketvm.data.db

import androidx.room.TypeConverter
import com.pocketvm.data.model.VmState

/** Room type converters for enum and complex types. */
class Converters {
    @TypeConverter
    fun fromVmState(state: VmState): String = state.name

    @TypeConverter
    fun toVmState(value: String): VmState = VmState.valueOf(value)
}
