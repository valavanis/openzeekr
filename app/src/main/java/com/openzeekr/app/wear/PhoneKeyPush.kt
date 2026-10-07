package com.openzeekr.app.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.openzeekr.app.ble.DkIdentity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Proactively pushes this phone's provisioned DK key to any paired watch, so the watch is
 * armed the moment provisioning finishes — without the user having to open the watch app.
 *
 * The watch's [KeySyncService] (a WearableListenerService) receives it even if the watch app
 * isn't running (the Data Layer starts the service to deliver the message) and caches it in
 * the watch's encrypted store, so it survives the phone later being out of range.
 *
 * This is the push half; the watch also PULLs on open (KeySyncClient.requestKey) as a fallback.
 */
object PhoneKeyPush {
    private const val TAG = "OZWearKey"

    /** Tell any paired watch to purge its cached key (on Remove key / sign-out): an immediate message
     *  to the watches connected now, plus the durable key state for those that aren't. */
    fun purgeWatches(context: Context) {
        val ctx = context.applicationContext
        publishKeyState(ctx)
        Wearable.getNodeClient(ctx).connectedNodes
            .addOnSuccessListener { nodes ->
                val mc = Wearable.getMessageClient(ctx)
                nodes.forEach { node ->
                    mc.sendMessage(node.id, WearKeyProtocol.PATH_PURGE, ByteArray(0))
                        .addOnSuccessListener { Log.i(TAG, "purge sent to ${node.displayName}") }
                        .addOnFailureListener { e -> Log.w(TAG, "purge to ${node.displayName} failed", e) }
                }
            }
            .addOnFailureListener { e -> Log.w(TAG, "purge: connectedNodes failed", e) }
    }

    /**
     * Publish this phone's current key id as a durable DataItem ([WearKeyProtocol.PATH_KEY_STATE]); the
     * Data Layer syncs it to every paired watch, including one that is off or out of range right now.
     * A watch holding any other key purges it. Carries the id only, never key material.
     */
    fun publishKeyState(context: Context) {
        val ctx = context.applicationContext
        val id = DkIdentity.get(ctx)
        val enabled = com.openzeekr.app.config.ConfigStore.get(ctx).current().wearKeyEnabled
        // "" (no key) ONLY when the phone really has none or sharing is off: a failed read of a key that
        // exists must not tell the watch to wipe its valid copy.
        val phoneDkId = if (!enabled || !id.isProvisioned) "" else runCatching { id.credential()?.dkId }.getOrNull()
            ?: run { Log.w(TAG, "key state not published: couldn't read the current key"); return }
        val dkId = WearKeyProtocol.publishedKeyId(enabled, phoneDkId)
        val req = PutDataMapRequest.create(WearKeyProtocol.PATH_KEY_STATE).apply {
            dataMap.putString(WearKeyProtocol.KEY_STATE_DKID, dkId)
            dataMap.putLong("ts", System.currentTimeMillis()) // always a change, so it always syncs
        }.asPutDataRequest().setUrgent()
        Wearable.getDataClient(ctx).putDataItem(req)
            .addOnSuccessListener { Log.i(TAG, "key state published (${if (dkId.isEmpty()) "no key" else "key"})") }
            .addOnFailureListener { e -> Log.w(TAG, "key state publish failed", e) }
    }

    fun pushToWatches(context: Context) {
        val ctx = context.applicationContext
        if (!com.openzeekr.app.config.ConfigStore.get(ctx).current().wearKeyEnabled) {
            Log.i(TAG, "push skipped — Watch key is off"); return
        }
        val blob = DkIdentity.get(ctx).exportCredentialBlob() ?: run {
            Log.i(TAG, "push skipped — phone not provisioned yet"); return
        }
        publishKeyState(ctx)
        val json = Json.encodeToString(blob).toByteArray(Charsets.UTF_8)
        Wearable.getNodeClient(ctx).connectedNodes
            .addOnSuccessListener { nodes ->
                if (nodes.isEmpty()) { Log.i(TAG, "push: no watch connected"); return@addOnSuccessListener }
                val mc = Wearable.getMessageClient(ctx)
                nodes.forEach { node ->
                    mc.sendMessage(node.id, WearKeyProtocol.PATH_KEY, json)
                        .addOnSuccessListener { Log.i(TAG, "pushed key to ${node.displayName} (${json.size}B)") }
                        .addOnFailureListener { e -> Log.w(TAG, "push to ${node.displayName} failed", e) }
                }
            }
            .addOnFailureListener { e -> Log.w(TAG, "push: connectedNodes failed", e) }
    }
}
