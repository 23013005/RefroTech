// FirestoreNormalizer.kt
package com.example.refrotech

import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot

object FirestoreNormalizer {

    /**
     * Normalize technicians representation read from a document.
     * Returns Pair(namesList, idsList)
     */
    fun normalizeTechnicians(doc: DocumentSnapshot): Pair<List<String>, List<String>> {
        // Prefer arrays if present
        val namesFromList = doc.get(FirestoreFields.FIELD_TECHNICIANS) as? List<*>
        val idsFromList = doc.get(FirestoreFields.FIELD_TECHNICIAN_IDS) as? List<*>

        val names = namesFromList?.mapNotNull { it?.toString() }?.toList()
            ?: run {
                // fall back to legacy single-string fields
                val single = doc.getString("technician") ?: doc.getString("technicians") ?: ""
                if (single.isBlank()) emptyList() else single.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }

        val ids = idsFromList?.mapNotNull { it?.toString() }?.toList()
            ?: run {
                // fallback to some legacy field maybe 'technicianIds' missing => empty
                emptyList()
            }

        return Pair(names, ids)
    }

    /**
     * Write normalized technician arrays back into the document (simple migration helper).
     */
    fun writeTechnicians(docRef: DocumentReference, names: List<String>, ids: List<String>, onComplete: (Boolean, String) -> Unit) {
        val updates = hashMapOf<String, Any>(
            FirestoreFields.FIELD_TECHNICIANS to names,
            FirestoreFields.FIELD_TECHNICIAN_IDS to ids,
            FirestoreFields.FIELD_ASSIGNED_TECHNICIAN_IDS to ids
        )

        docRef.update(updates)
            .addOnSuccessListener { onComplete(true, "Normalized technicians") }
            .addOnFailureListener { e -> onComplete(false, e.message ?: "Failed") }
    }
}
