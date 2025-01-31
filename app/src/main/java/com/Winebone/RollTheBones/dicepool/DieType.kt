package com.Winebone.RollTheBones.dicepool

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.appcompat.content.res.AppCompatResources
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
 * Represents a type of die that can be used in pools. A die produces an integer result selected
 * uniformly at random from a contiguous set of integers (i.e. between a minimum and maximum value)
 * and has a list of drawables, one for each value it can roll. It may also have a drawable to
 * represent an unrolled die/die in the pool configuration activity; if it does not the drawable for
 * the maximum roll will be used for that purpose.
 */
@Serializable(with = DieTypeSerializer::class)
class DieType(val name: String, val minResult: Int, val maxResult: Int,
              val faceDrawables: Array<Drawable>, val configFaceDrawable: Drawable? = null,
              val isPlaceholder: Boolean = false) {
                                                    //isPlaceholder is true if this is not a real type
                                                    //of die, but a placeholder to put in pools that
                                                    //contain die types that don't exist.
    var faceDrawableUris: Array<Uri?>? = null

    /**
     * Returns true if two dice have the same name, minimum result, and maximum result.
     * other: Die type to compare
     */
    override operator fun equals(other: Any?): Boolean {
        if(other is DieType) {
            return name == other.name && minResult == other.minResult && maxResult == other.maxResult
            //Doesn't check drawable IDs
        }
        return false
    }

    /**
     * Randomly generates a result that this type of die could produce.
     * Returns: DieRollResult object storing the numeric face value rolled, and the drawable for
     *          that face.
     */
    fun Roll(): DieRollResult {
        if(isPlaceholder) throw Exception("Can't roll placeholder die")

        val rollNumber = (minResult..maxResult).random()
        val drawable = faceDrawableForResult(rollNumber)
        return DieRollResult(rollNumber, drawable)
    }

    /**
     * Returns the drawable for the face of this die type if a given value were to be rolled. Does
     * not check if the value is within the range of values this die could produce.
     */
    fun faceDrawableForResult(result: Int): Drawable {
        return faceDrawables[result-minResult]
    }

    /**
     * Returns the URI for the face of this die type if a given value were to be rolled. If that
     * face doesn't have a URI (i.e. if it's a built-in type or uses the generic face image). Does
     * not check if the value is within the range of values this die could produce.
     */
    fun faceDrawableUriForResult(result: Int): Uri? {
        return faceDrawableUris?.get(result-minResult)
    }

    /**
     * Generates and returns a DieRollResult object representing how this die should be shown in
     * config activities and menus.
     * Returns: DieRollResult with value equal to the max roll of this die. The drawable is the
     *          config drawable if any is set, otherwise the face drawable for the max result.
     */
    fun GetConfigResult(): DieRollResult {
        if(isPlaceholder) throw Exception("Can't roll placeholder die")

        return DieRollResult(maxResult, configFaceDrawable ?: faceDrawables[maxResult-minResult])
    }
}

/**
 * Class for specifying built-in die types using drawable IDs rather than drawables. Must be turned
 * into a DieType before being used.
 */
class DiePrototype(val name: String, val minResult: Int,
                   val maxResult: Int, val faceDrawableIds: IntArray) {

    /**
     * Loads the face drawables for this die based on the drawable IDs, then creates a DieType based
     * on this prototype that has those face drawables.
     * context: Activity context is required to load drawables
     * Returns: A DieType based on this prototype
     */
    fun CreateDie(context: Context): DieType {
        var faceDrawables = mapFaceDrawableIds(context, faceDrawableIds)
        return DieType(name, minResult, maxResult, faceDrawables)
    }

    companion object {

        /**
         * Generates and returns an array of drawables from an array of drawable IDs.
         * context: Activity context is required to load drawables
         * faceDrawableIds: Array of drawable IDs to load drawables for.
         */
        fun mapFaceDrawableIds(context: Context, faceDrawableIds: IntArray): Array<Drawable> {
            return faceDrawableIds.map(
                { id: Int -> AppCompatResources.getDrawable(context, id)!! }
                ).toTypedArray()
        }
    }
}

/**
 * Object storing the result of rolling a single die. Contains the numeric value of the roll and the
 * drawable to show the roll. Includes operators for adding, subtracting, and multiplying results
 * (which return just the numeric value).
 */
class DieRollResult(val number: Int, val faceDrawable: Drawable) {
    operator fun plus(other: DieRollResult): Int {
        return number + other.number
    }

    operator fun minus(other: DieRollResult): Int {
        return number - other.number
    }

    operator fun times(other: DieRollResult): Int {
        return number * other.number
    }
}

/**
 * Serialize a die type for saving pool configurations. Just saves the die name; when the pool is
 * loaded the DieType object will be retrieved from the DieTypesManager. Don't use this for saving
 * custom die types.
 */
object DieTypeSerializer: KSerializer<DieType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("DieTypeName", PrimitiveKind.STRING)

    /**
     * Encode as the die name
     */
    override fun serialize(encoder: Encoder, value: DieType) {
        if(value.isPlaceholder) throw Exception("Can't serialize placeholder die type")
        encoder.encodeString(value.name)
    }

    /**
     * Retrieve the die with the encoded name from the die type manager
     */
    override fun deserialize(decoder: Decoder): DieType {
        val name = decoder.decodeString()
        return DieTypesManager.GetTypeByName(name)
    }
}