package com.Winebone.RollTheBones.dicepool

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

private const val POOL_LIST_FILE = "savedPools"
private const val DEFAULT_POOL_FILE = "defaultPool"
const val POOL_FIELD_SEPARATOR = "\u001F"

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Singleton which manages saved pool configurations and the files for them. The first time a public
 * function in this manager is called, the pool list file will be loaded. Individual pools will only
 * be loaded from their files when they are needed. Once they are loaded they will be maintained.
 * Public functions in this manager must be passed application or activity context to do file IO.
 *
 * Pools are enumerated in a list file, and the configuration of each pool is saved in its own file.
 * There also may be a file for the configuration of the pool to load when the application is started.
 * The list file is saved as "savedPools" in the app-specific directory, and the startup pool file,
 * if present, as "defaultPool". Individual pool files have names from random UUIDs.
 * Each line of the list file is formatted as pool name POOL_FIELD_SEPARATOR pool file name.
 * The contents of individual pool files (and the startup pool file) are from DicePoolValue
 * serialization. Note that this saves die types as their names only, so when the pool is loaded
 * those will be retrieved from the die types manager, including any changes that have been made to
 * them. The top-level element of each should be a PoolList with the + operation.
 */
object SavedPoolManager {
    private var savedPools = HashMap<String, PoolList?>()
    private var poolFileNames = HashMap<String, String>()
    //Consistent ordering needed for RecyclerView to get pools based on adapter position, which
    //map.keys may not guarantee. Note that ordering may not be maintained when deleting pools, but
    //the recycler should refresh there anyway (notifyDataSetChanged).
    private var poolNames = ArrayList<String>()
    private var poolListLoaded = false

    /**
     * Loads the list file and populates the map from pool names to file names. Does not immediately
     * load individual pools. If the list file does not exist, creates a new file and leaves the
     * pool map empty. Sets a flag that the list has been loaded, so we don't repeat this work.
     * If loading the list fails or the file is malformed, throws an Exception.
     * Throws an IOException if creating the file fails.
     */
    private fun loadPoolList(context: Context) {
        var inputFile = File(context.filesDir, POOL_LIST_FILE)
        if(!inputFile.exists()) {
            if(!inputFile.createNewFile()) throw IOException("Could not create pool list file")
            poolListLoaded = true
            return
        }

        for(line: String in inputFile.readLines()) {
            var segments: List<String> = line.split(POOL_FIELD_SEPARATOR)
            if(segments.size != 2) throw Exception("Pool list is corrupted")
            savedPools[segments[0]] = null
            poolFileNames[segments[0]] = segments[1]
            poolNames.add(segments[0])
        }

        poolListLoaded = true
    }

    /**
     * Loads a saved pool from its file and puts it in the map of pool names to pools. Will throw an
     * Exception if the pool does not exist, its file could not be opened, or the data in the file
     * could not be deserialized.
     * poolName: The name of the pool to load into the manager
     */
    private fun loadPool(context: Context, poolName: String) {
        val filename = poolFileNames[poolName]
        if(filename == null) throw Exception("Pool \"" + poolName + "\" was not found")

        var inputFile = File(context.filesDir, filename)
        if(!inputFile.exists()) throw Exception("File for pool " + poolName + " does not exist")

        var poolText = inputFile.readText()
        var decodedPool = Json.decodeFromString<DicePoolValue>(poolText)
        if(decodedPool !is PoolList) throw Exception("Pool saved in unknown format")
        savedPools[poolName] = decodedPool
    }

    /**
     * Returns an array of the names of all saved pools.
     */
    fun EnnumerateSavedPools(context: Context): Array<String> {
        if(!poolListLoaded) loadPoolList(context)

        //return savedPools.keys.toTypedArray()
        return poolNames.toTypedArray()
    }

    /**
     * Returns the name of the pool based on its index in the consistently-ordered list of pool
     * names. Used by the menu RecyclerView to determine a pool based on adapter position.
     * index: Index of the pool to get the name of
     * Returns: Name of the pool
     */
    fun GetPoolName(context: Context, index: Int): String {
        if(!poolListLoaded) loadPoolList(context)

        return poolNames[index]
    }

    /**
     * Returns the number of saved pools.
     */
    fun GetNumPools(context: Context): Int {
        if(!poolListLoaded) loadPoolList(context)

        return poolNames.size
    }

    /**
     * Gets a saved pool with a given name. If that pool has not already been loaded into memory then
     * it will be here.
     * poolName: The name of the pool to retrieve
     * Returns: (<pool list>, "") if the pool was retrieved successfully. The top-level pool list
     *                should use the + operation.
     *          (null, <message>) if it was not retrieved successfully. This occurs if no pool of
     *                that name is in the list, or there is an exception during file IO. The second
     *                value is a technical error message describing what happened.
     */
    fun GetSavedPool(context: Context, poolName: String): Pair<PoolList?, String> {
        try {
            if (!poolListLoaded) loadPoolList(context)

            //Check that this is a pool that exists
            if (!savedPools.containsKey(poolName))
                return Pair(null, "No pool named \"" + poolName + "\" found")

            //Load the pool if it hasn't been already
            if (savedPools[poolName] == null) loadPool(context, poolName)
            //Return a duplicate to avoid altering or putting display fragments in the pool
            //maintained by the manager.
            var returnPool = savedPools[poolName]!!.Duplicate() as PoolList
            return Pair(returnPool, "")
        }
        catch(exception: Exception) {
            return Pair(null, exception.message ?: "Error loading pool")
        }
    }

    /**
     * Saves a pool configuration. Sets the configuration in the in-memory map of pools and writes/
     * overwrites the file for the pool. If it's a new pool, adds it to the list file.
     * poolName: What name to save this pool as
     * pool: The pool configuration to save. The top-level element should use the + operation.
     * Returns: (true, "") if the pool was successfully saved.
     *          (false, <message>) if it was not. This occurs if there is an exception during
     *                serialization or file IO. The second value is a technical error message.
     */
    fun SavePool(context: Context, poolName: String, pool: PoolList): Pair<Boolean, String> {
        try {
            if (!poolListLoaded) loadPoolList(context)

            //If there's already a file for that pool name, use that. Otherwise generate a filename.
            val filename = poolFileNames[poolName] ?: UUID.randomUUID().toString()
            if (!savedPools.containsKey(poolName)) {
                //Add the pool to the lsit file if necessary
                var listFile = File(context.filesDir, POOL_LIST_FILE)
                var writer = FileWriter(listFile, true)
                writer.appendLine(poolName + POOL_FIELD_SEPARATOR + filename)
                writer.close()

                poolFileNames[poolName] = filename
                poolNames.add(poolName)
            }

            //Duplicate the pool to avoid display fragments and alteration elsewhere, and add it to
            //the pool map.
            savedPools[poolName] = pool.Duplicate() as PoolList
            //Write/rewrite the file for the pool
            var outputFile = File(context.filesDir, filename)
            outputFile.createNewFile()
            var encodedPool = Json.encodeToString(pool as DicePoolValue)
            outputFile.writeText(encodedPool)
            return Pair(true, "")
        }
        catch (exception: Exception) {
            return Pair(false, exception.message ?: "Error saving pool")
        }
    }

    /**
     * Determines whether there is a saved pool with the given name. Does not load that pool if it
     * hasn't been.
     * poolName: Name of the pool to look for.
     * Returns: True if there is a pool by that name in the list, false otherwise.
     */
    fun HasSavedPool(context: Context, poolName: String): Boolean {
        if(!poolListLoaded) loadPoolList(context)

        return savedPools.containsKey(poolName)
    }

    /**
     * Deletes a saved pool by name. Deletes its file, removes it from the in-memory map, and rewrites the
     * list file without it.
     * poolName: Name of the pool to delete
     * Returns: (true, "") if the pool was successfully deleted
     *          (false, <message>) if it wasn't. This occurs if no pool by that name was found or
     *                there was an exception during file IO. The second value is a technical error
     *                message. Note that if an error occurs while deleting the pool file, the pool
     *                will still be in the list file and remain loaded in memory.
     */
    fun DeletePool(context: Context, poolName: String): Pair<Boolean, String> {
        try {
            if (!poolListLoaded) loadPoolList(context)

            //Find the pool file's name
            val filename = poolFileNames[poolName]
            if (filename == null) return Pair(false, "Pool not found")

            //Delete the pool file
            var poolFile = File(context.filesDir, filename)
            if(poolFile.exists() && !poolFile.delete()) {
                return Pair(false, "Pool file could not be deleted")
            }

            //Remove the pool from the map & consistently-ordered list of names.
            poolFileNames.remove(poolName)
            savedPools.remove(poolName)
            poolNames.remove(poolName)

            rewriteList(context)
            return Pair(true, "")
        }
        catch (exception: Exception) {
            return Pair(false, exception.message ?: "Error deleting pool")
        }
    }

    /**
     * Deletes a group of pools at the same time. Deletes their pool files, removes them from the
     * in-memory map & list, and rewrites the list file without them.
     * poolsToRemove: A set of the names of pools to delete.
     * Returns: A map of any errors that occurred while deleting. Key null is used for errors while
     *          writing the list file. Errors deleting individual pools will use that pool's name
     *          as the key. The value is a technical error message. This occurs if a pool of a given
     *          name is not found or there's an exception during file IO.
     */
    fun DeletePools(context: Context, poolsToRemove: Set<String>): HashMap<String?, String> {
        if (!poolListLoaded) loadPoolList(context)
        var errors = HashMap<String?, String>()

        for(poolName in poolsToRemove) {
            try {
                //Find the pool's file name
                val filename = poolFileNames[poolName]
                if (filename == null) {
                    errors[poolName] = "Pool not found"
                    continue
                }

                //Delete the file
                var poolFile = File(context.filesDir, filename)
                if(poolFile.exists() && !poolFile.delete()) {
                    errors[poolName] = "Pool file could not be deleted"
                    continue
                }

                //Remove the pool from the in-memory map and list
                poolFileNames.remove(poolName)
                savedPools.remove(poolName)
                poolNames.remove(poolName)
            }
            catch(exception: Exception) {
                errors[poolName] = exception.message ?: "Error deleting pool"
            }
        }

        try {
            //Rewrite the list file from what's currently in memory. Note that if a pool's file could
            //not be deleted then it will still be in memory, so it won't be removed from the list.
            rewriteList(context)
        }
        catch (exception: Exception) {
            errors[null] = exception.message ?: "Error writing pool list file"
        }

        return errors
    }

    /**
     * Rewrite the the list file from the pools that are currently listed in the in-memory map of
     * file names (the actual pool data does not need to have been loaded).
     */
    private fun rewriteList(context: Context) {
        var listFile = File(context.filesDir, POOL_LIST_FILE)
        var listWriter = FileWriter(listFile)
        for(poolData in poolFileNames) {
            val line = poolData.key + POOL_FIELD_SEPARATOR + poolData.value
            listWriter.appendLine(line)
        }
        listWriter.close()

    }

    /**
     * Get the pool to be shown when the application is started. If a startup pool file has been
     * saved then it will load the pool saved there. If there is no such file, or if there's an
     * error while loading from it, then the default pool (as defined by DicePoolValue) will be used.
     * Returns: (<successful>, <init pool>)
     *                <successful> is false if there is a startup pool file but it could not be
     *                      loaded. True otherwise.
     *                <init pool> is the startup pool to use. The top-level element should use the
     *                      plus operation.
     */
    fun GetInitialPool(context: Context): Pair<Boolean, PoolList> {
        try {
            var inputFile = File(context.filesDir, DEFAULT_POOL_FILE)
            if(!inputFile.exists()) return Pair(true, DicePoolValue.ConstructDefaultList())

            var poolText = inputFile.readText()
            var pool = Json.decodeFromString<DicePoolValue>(poolText)
            if(pool !is PoolList) throw Exception("Pool saved in unknown format")
            return Pair(true, pool)
        }
        catch(exception: Exception) {
            return Pair(false, DicePoolValue.ConstructDefaultList())
        }
    }

    /**
     * Saves a pool to be loaded when the application is started. If no startup pool file has been
     * created, it will be here. Otherwise that file will be overwritten.
     * pool: PoolList to be loaded into the rolling activity the next time the activity starts. The
     *       top-level element should use the plus operation.
     * Returns: (True, "") if the startup pool was successfully saved.
     *          (False, <message>) if there was an issue, which would be an exception during file IO.
     *                The second value is a technical error message.
     */
    fun SetInitialPool(context: Context, pool: PoolList): Pair<Boolean, String> {
        try {
            var outputFile = File(context.filesDir, DEFAULT_POOL_FILE)
            outputFile.createNewFile()

            var poolText = Json.encodeToString(pool as DicePoolValue)
            outputFile.writeText(poolText)
            return Pair(true, "")
        }
        catch (exception: Exception) {
            return Pair(false, exception.message ?: "Error saving default pool")
        }
    }

    /**
     * Called if a die type of a given name has been replaced in the die type manager. Any pool which
     * has already been loaded into memory and that has that die type will be refreshed. Each instance
     * of that die type will be replaced with the new type of the same name. Note that the replaced
     * type and the replacing type may be custom dice or placeholders.
     * dieTypeName: the name of the die type to re-retrieve from the die type manager.
     */
    fun DieTypeUpdated(dieTypeName: String) {
        if(!poolListLoaded) return //No point actually loading it here; updated die type will be found when the pool is loaded

        for(loadedPool in savedPools.values) {
            if(loadedPool == null) continue //Pool hasn't been loaded
            loadedPool.ReplaceDieType(dieTypeName, dieTypeName) //ReplaceDieType gets the replacement from the die type manager
        }
    }
}