package com.Winebone.RollTheBones.dicepool

import com.Winebone.RollTheBones.R
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Represents the operation of a PoolList, i.e. the operation by which the sub-pool results are
 * combined into the list's result.
 */
@Serializable(with = OperationSerializer::class)
class Operation(val operatorText: String,
                val operatorDrawableId: Int,
                val opFunction: (Int, Int) -> Int,
                val operatorName: String) {
    fun apply(x: Int, y: Int): Int {
        return opFunction(x, y)
    }

    companion object {
        //Available operations are add, subtract, and multiply
        val SelectableOperations = arrayOf<Operation>(
            Operation("+", R.drawable.ic_plus_sign, {x, y -> x + y}, "plus"),
            Operation("-", R.drawable.ic_minus_sign, {x, y -> x - y}, "minus"),
            Operation("*", R.drawable.ic_multiply_sign, {x, y -> x * y}, "multiply")
        )

        val DefaultOperation = SelectableOperations[0]
    }
}

/**
 * Serialize an operation for saving a PoolList in a pool configuration. Saved as the operator text;
 * when the pool is loaded the relevant Operation object will be retrieved from the Operation
 * companion object.
 */
object OperationSerializer: KSerializer<Operation> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("operatorText", PrimitiveKind.STRING)

    /**
     * Encode as the operator text
     */
    override fun serialize(encoder: Encoder, value: Operation) {
        encoder.encodeString(value.operatorText)
    }

    /**
     * Find the operator with the encoded text from the Operator companion object.
     */
    override fun deserialize(decoder: Decoder): Operation {
        val encodedOpText = decoder.decodeString()
        for(op: Operation in Operation.SelectableOperations) {
            if(op.operatorText == encodedOpText) return op
        }
        throw Exception("Encoded operation not found")
    }
}