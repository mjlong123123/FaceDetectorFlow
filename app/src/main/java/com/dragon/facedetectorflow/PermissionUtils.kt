package com.dragon.facedetectorflow

import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat

/**
 * @author dragon
 */
class PermissionUtils(private val activity: Activity, private val resultCaller: ActivityResultCaller) {

    private val cachedPermissions = mutableListOf<String>()

    private var cacheBlock: ((Map<String, Boolean>) -> Unit)? = null

    private val contract: ActivityResultContracts.RequestMultiplePermissions = ActivityResultContracts.RequestMultiplePermissions()

    private val launcher: ActivityResultLauncher<Array<String>> = resultCaller.registerForActivityResult(contract) { permissionMap ->
        cacheBlock?.invoke(cachedPermissions.associateWith { permissionMap[it] ?: true })
    }

    fun requestPermission(permission: String, block: (Boolean) -> Unit) {
        requestPermission(listOf(permission)) {
            block.invoke(it[permission] ?: false)
        }
    }

    fun requestPermission(permissions: List<String>, block: (Map<String, Boolean>) -> Unit) {
        val requestPermissions = permissions.asSequence()
            .filter { ActivityCompat.checkSelfPermission(activity, it) != PackageManager.PERMISSION_GRANTED }
            .toList()
        if (requestPermissions.isEmpty()) {
            cacheBlock = null
            cachedPermissions.clear()
            block.invoke(permissions.associateWith { true })
        } else {
            cacheBlock = block
            cachedPermissions.clear()
            cachedPermissions.addAll(permissions)
            launcher.launch(requestPermissions.toTypedArray())
        }
    }
}