package com.openzeekr.app.wear

/**
 * Message paths for the phone ⇄ watch digital-key clone over the Wear Data Layer.
 * Shared by :app (phone responder) and :wear (watch requester). Plain constants — no
 * Play Services dependency here.
 */
object WearKeyProtocol {
    /** Watch → phone: "send me your digital key". */
    const val PATH_REQUEST = "/openzeekr/request-key"

    /** Phone → watch: the DK credential blob (JSON of [com.openzeekr.app.ble.DkIdentity.exportCredentialBlob]). */
    const val PATH_KEY = "/openzeekr/key"

    /** Phone → watch: "the key was removed — purge your cached copy." (Sent on Remove key / sign-out.) */
    const val PATH_PURGE = "/openzeekr/purge-key"

    /**
     * Phone → watch DataItem: the phone's CURRENT key id ([KEY_STATE_DKID], "" = no key). Unlike the
     * one-shot [PATH_PURGE] message, a DataItem is durable and synced whenever the watch reconnects, so
     * a watch that was off or out of range at Remove key / sign-out still drops its copy ([watchMustPurge]).
     * Carries only the id, never key material.
     */
    const val PATH_KEY_STATE = "/openzeekr/key-state"
    const val KEY_STATE_DKID = "dkId"

    /** True when the watch holds a key ([watchDkId]) that is not the phone's current one ([phoneDkId]). */
    fun watchMustPurge(watchDkId: String?, phoneDkId: String): Boolean =
        watchDkId != null && watchDkId != phoneDkId

    // ---- BLE-link arbitration (the car allows only ONE peer, so watch and phone can't both
    // hold the DK session; the watch checks with the phone before touching the car) ----

    /** Watch → phone: "are you present / holding the car link / is proximity on?" */
    const val PATH_STATUS_QUERY = "/openzeekr/status-query"

    /** Phone → watch: reply to a status query. JSON: {"connected":"0|1","prox":"0|1"}. */
    const val PATH_STATUS = "/openzeekr/status"

    /** Watch → phone: "release the car link and stand your keep-alive down, I need it." */
    const val PATH_PAUSE = "/openzeekr/pause-link"

    /** Phone → watch: "released — the car is free, go ahead." */
    const val PATH_PAUSED = "/openzeekr/link-paused"

    /** Watch → phone: "I'm done — take the link back." */
    const val PATH_RESUME = "/openzeekr/resume-link"
}
