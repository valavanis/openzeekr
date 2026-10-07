package com.openzeekr.wear

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import com.openzeekr.app.ble.DkIdentity
import com.openzeekr.app.wear.WearKeyProtocol
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

private const val TAG = "OZWearKey"

/**
 * Receives the phone's cloned DK credential over the Wear Data Layer and stores it locally,
 * then arms the BLE session ([WearKeyState.refresh]). Runs in the app process, so the
 * [WearKeyState] flow it updates is the same one the UI observes.
 */
class KeySyncService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            WearKeyProtocol.PATH_KEY -> importKey(event)
            WearKeyProtocol.PATH_PURGE -> purge("phone removed the key / signed out")
            WearKeyProtocol.PATH_STATUS -> {
                val m = runCatching { Json.decodeFromString<Map<String, String>>(String(event.data, Charsets.UTF_8)) }.getOrNull()
                PhoneLink.onStatus(connected = m?.get("connected") == "1", prox = m?.get("prox") == "1")
            }
            WearKeyProtocol.PATH_PAUSED -> PhoneLink.onPaused()
        }
    }

    /**
     * The phone's durable key state, synced whenever the watch reconnects: drop our copy if it is not the
     * phone's current key. This is what reaches a watch that was off or out of range at Remove key /
     * sign-out, when the one-shot purge message could not.
     */
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { e ->
            if (e.type != DataEvent.TYPE_CHANGED || e.dataItem.uri.path != WearKeyProtocol.PATH_KEY_STATE) return@forEach
            val phoneDkId = DataMapItem.fromDataItem(e.dataItem).dataMap.getString(WearKeyProtocol.KEY_STATE_DKID, "")
            val watchDkId = runCatching { DkIdentity.get(this).credential()?.dkId }.getOrNull()
            if (WearKeyProtocol.watchMustPurge(watchDkId, phoneDkId)) purge("not the phone's current key")
        }
    }

    private fun purge(reason: String) {
        Log.i(TAG, "watch purging cached key ($reason)")
        DkIdentity.get(this).wipeAll()
        runCatching { com.openzeekr.app.ble.DkBleManager.get(this).disconnect() }
        WearKeyState.status.value = "Key removed on phone"
        WearKeyState.refresh(this)
    }

    private fun importKey(event: MessageEvent) {
        val raw = String(event.data, Charsets.UTF_8)
        Log.i(TAG, "watch received key reply, ${event.data.size} bytes")
        val blob = runCatching {
            Json.decodeFromString<Map<String, String>>(raw)
        }.getOrNull()
        if (blob != null && WearKeyProtocol.replyError(blob) == WearKeyProtocol.ERR_WATCH_KEY_OFF) {
            Log.i(TAG, "phone refused: Watch key is off")
            WearKeyState.status.value = "Turn on \"Watch key\" in the phone app (Key tab)"
            return
        }
        if (blob.isNullOrEmpty()) {
            Log.w(TAG, "reply had no key — phone is not provisioned")
            WearKeyState.status.value = "Phone has no key yet — provision it first"
            return
        }
        // Anyone holding an unlocked watch can open the car: never keep the key on a watch without a lock.
        if (!WearKeyState.deviceSecure(this)) {
            Log.w(TAG, "refusing the key: no screen lock on this watch")
            WearKeyState.status.value = WearKeyState.NEEDS_LOCK
            return
        }
        DkIdentity.get(this).importCredentialBlob(blob)
        WearKeyState.status.value = null
        WearKeyState.refresh(this)
        Log.i(TAG, "watch imported key (${blob.size} fields), provisioned=${WearKeyState.provisioned.value}")
    }
}

/** Asks the paired phone (running OpenZeekr) to send its digital key. */
object KeySyncClient {
    fun requestKey(context: Context) {
        val ctx = context.applicationContext
        Wearable.getNodeClient(ctx).connectedNodes
            .addOnSuccessListener { nodes ->
                Log.i(TAG, "connectedNodes=${nodes.size} ${nodes.joinToString { it.displayName }}")
                if (nodes.isEmpty()) {
                    WearKeyState.status.value = "Phone not connected — open Galaxy Wearable"
                    return@addOnSuccessListener
                }
                WearKeyState.status.value = "Asking phone for the key…"
                val mc = Wearable.getMessageClient(ctx)
                nodes.forEach { node ->
                    mc.sendMessage(node.id, WearKeyProtocol.PATH_REQUEST, ByteArray(0))
                        .addOnSuccessListener { Log.i(TAG, "request sent to ${node.displayName}") }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "send to ${node.displayName} failed", e)
                            WearKeyState.status.value = "Couldn't reach phone app"
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "connectedNodes failed", e)
                WearKeyState.status.value = "Can't reach phone (Play Services?)"
            }
    }
}
