package com.medhome.nepal.data

import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A Firestore snapshot listener as a flow, registered with [listeners] so sign-out can stop it.
 * [attach] builds the query or reference and adds the listener; it runs when the flow is
 * collected, so it uses the Firestore instance of that moment. If it throws (a terminated
 * instance after sign-out throws IllegalStateException, from building the query or from adding
 * the listener), the flow fails with an [com.medhome.nepal.domain.AuthException], like any other
 * failure. [onEvent] handles each answer, in the flow's producer scope.
 */
internal fun <S, T> snapshotFlow(
    listeners: ListenerRegistry,
    attach: (EventListener<S>) -> ListenerRegistration,
    onEvent: ProducerScope<T>.(snapshot: S?, error: FirebaseFirestoreException?) -> Unit,
): Flow<T> = callbackFlow {
    val registration = mapErrors { attach(EventListener { snapshot, error -> onEvent(snapshot, error) }) }
    val stop = listeners.register {
        registration.remove()
        channel.close()
    }
    awaitClose {
        stop.release()
        registration.remove()
    }
}

/**
 * A live query (including metadata changes, so cache and server answers both arrive): each
 * answer mapped by [map]; an error ends the flow with an AuthException. [query] is built when the
 * flow is collected.
 */
internal fun <T> queryFlow(listeners: ListenerRegistry, query: () -> Query, map: (QuerySnapshot) -> T): Flow<T> =
    snapshotFlow(
        listeners = listeners,
        attach = { listener -> query().addSnapshotListener(MetadataChanges.INCLUDE, listener) },
    ) { snapshot, error ->
        if (error != null) {
            close(AuthErrorMapper.toException(error))
        } else if (snapshot != null) {
            trySend(map(snapshot))
        }
    }
