package com.emergency.patient.network;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.emergency.patient.db.AdherenceLogEntity;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.GuardianLinkEntity;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.security.TokenManager;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirebaseRepository — Centralizes all Cloud Firebase operations (Firestore, Storage, Auth).
 * 
 * NOTE: Emergency Contacts are intentionally left out of this repository 
 * to adhere to the local-only privacy constraint.
 */
public class FirebaseRepository {

    private static final String TAG = "FirebaseRepository";
    private static FirebaseRepository instance;

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    // TODO: USER MUST CONFIGURE THESE IN THE FIREBASE CONSOLE / CLOUDINARY DASHBOARD
    private static final String CLOUDINARY_CLOUD_NAME = "dthqisgz9";
    private static final String CLOUDINARY_UPLOAD_PRESET = "Emergency";

    private FirebaseRepository(Context context) {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        
        // Initialize Cloudinary
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", CLOUDINARY_CLOUD_NAME);
        try {
            MediaManager.init(context, config);
        } catch (IllegalStateException e) {
            // Already initialized
        }
    }

    public static synchronized FirebaseRepository getInstance(Context context) {
        if (instance == null) {
            instance = new FirebaseRepository(context.getApplicationContext());
        }
        return instance;
    }

    // ─── Authentication ───────────────────────────────────────────────────────

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public void signOut() {
        auth.signOut();
    }

    // ─── Profile Sync (Room <-> Firestore) ───────────────────────────────────

    /**
     * Uploads the local Room PatientEntity to Firestore.
     */
    public void uploadProfile(Context context, @NonNull PatientEntity patient, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("fullName", patient.fullName);
        profile.put("dobMillis", patient.dobMillis);
        profile.put("gender", patient.gender);
        profile.put("bloodGroup", patient.bloodGroup);
        profile.put("pastMedicalDiagnosis", patient.pastMedicalDiagnosis);
        profile.put("pharmacologicalStatus", patient.pharmacologicalStatus);
        profile.put("clinicalAllergies", patient.clinicalAllergies);
        profile.put("hereditaryConditions", patient.hereditaryConditions);
        profile.put("lifestyleFactor", patient.lifestyleFactor);
        profile.put("isOnboardingComplete", patient.isOnboardingComplete);
        profile.put("updatedAt", System.currentTimeMillis());

        db.collection("patients").document(user.getUid())
                .set(profile)
                .addOnSuccessListener(aVoid -> {
                    if (callback != null) callback.onSuccess();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    /**
     * Downloads the profile from Firestore and updates the local Room database.
     */
    public void downloadProfile(Context context, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        db.collection("patients").document(user.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Update Room DB on background thread
                        new Thread(() -> {
                            AppDatabase roomDb = AppDatabaseProvider.getInstance(context);
                            String uuid = TokenManager.getUUID(context); // Legacy sync
                            PatientEntity patient = roomDb.patientDao().getPatient(uuid);
                            
                            if (patient == null) {
                                patient = new PatientEntity(uuid);
                            }

                            patient.fullName = documentSnapshot.getString("fullName");
                            patient.dobMillis = documentSnapshot.getLong("dobMillis") != null ? documentSnapshot.getLong("dobMillis") : 0;
                            patient.gender = documentSnapshot.getString("gender");
                            patient.bloodGroup = documentSnapshot.getString("bloodGroup");
                            patient.pastMedicalDiagnosis = documentSnapshot.getString("pastMedicalDiagnosis");
                            patient.pharmacologicalStatus = documentSnapshot.getString("pharmacologicalStatus");
                            patient.clinicalAllergies = documentSnapshot.getString("clinicalAllergies");
                            patient.hereditaryConditions = documentSnapshot.getString("hereditaryConditions");
                            patient.lifestyleFactor = documentSnapshot.getString("lifestyleFactor");
                            patient.isOnboardingComplete = documentSnapshot.getBoolean("isOnboardingComplete") != null && documentSnapshot.getBoolean("isOnboardingComplete");

                            roomDb.patientDao().insertPatient(patient);
                            
                            if (callback != null) callback.onSuccess();
                        }).start();
                    } else {
                        if (callback != null) callback.onFailure(new Exception("Profile not found in cloud"));
                    }
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    // ─── Document Storage (Room <-> Firebase Storage) ───────────────────────

    /**
     * Uploads a local file to Cloudinary and returns the URL.
     */
    public void uploadDocument(String localPath, String fileName, final DocumentUploadCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        if (CLOUDINARY_CLOUD_NAME.equals("YOUR_CLOUD_NAME")) {
            if (callback != null) callback.onFailure(new Exception("Cloudinary not configured. Please set cloud_name in FirebaseRepository.java"));
            return;
        }

        MediaManager.get().upload(localPath)
                .unsigned(CLOUDINARY_UPLOAD_PRESET)
                .option("public_id", "patients/" + user.getUid() + "/" + fileName)
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String requestId) {
                        Log.d(TAG, "Cloudinary upload started: " + requestId);
                    }

                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) {
                    }

                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String downloadUrl = (String) resultData.get("secure_url");
                        String publicId = (String) resultData.get("public_id");
                        Log.d(TAG, "Cloudinary upload success: " + downloadUrl + " (public_id: " + publicId + ")");
                        registerDocumentInFirestore(user.getUid(), fileName, downloadUrl, publicId, callback);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        Log.e(TAG, "Cloudinary upload error: " + error.getDescription());
                        if (callback != null) callback.onFailure(new Exception(error.getDescription()));
                    }

                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) {
                    }
                }).dispatch();
    }

    public void deleteCloudProfile(final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        String uid = user.getUid();

        // 1. Delete all documents in the 'documents' sub-collection
        db.collection("patients").document(uid).collection("documents")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    for (com.google.firebase.firestore.DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        doc.getReference().delete();
                    }

                    // 2. Delete the main patient document
                    db.collection("patients").document(uid)
                            .delete()
                            .addOnSuccessListener(aVoid -> {
                                if (callback != null) callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                if (callback != null) callback.onFailure(e);
                            });
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onFailure(e);
                });
    }

    private void registerDocumentInFirestore(String uid, String fileName, String downloadUrl, String publicId, final DocumentUploadCallback callback) {
        Map<String, Object> docData = new HashMap<>();
        docData.put("fileName", fileName);
        docData.put("downloadUrl", downloadUrl);
        docData.put("publicId", publicId); // Store for future deletion
        docData.put("timestamp", System.currentTimeMillis());

        db.collection("patients").document(uid)
                .collection("documents").add(docData)
                .addOnSuccessListener(documentReference -> {
                    if (callback != null) callback.onSuccess(downloadUrl, publicId, documentReference.getId());
                })
                .addOnFailureListener(e -> {
                    // Even if metadata sync fails, Storage upload was successful
                    if (callback != null) callback.onSuccess(downloadUrl, publicId, null); 
                });
    }

    public void deleteDocumentFromCloud(String publicId, String firestoreId, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        // 1. Delete from Firestore if we have ID
        if (firestoreId != null && !firestoreId.isEmpty()) {
            db.collection("patients").document(user.getUid())
                    .collection("documents").document(firestoreId)
                    .delete()
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to delete Firestore record: " + e.getMessage()));
        }

        // 2. Delete from Cloudinary
        // Note: For public Cloudinary setups, simple deletion without secret isn't supported via SDK.
        // We trigger the callback immediately after attempting firestore cleanup.
        if (callback != null) callback.onSuccess();
    }

    // ─── Medication Cloud Sync ────────────────────────────────────────────────

    /**
     * Writes a local MedicationEntity up to Firestore at
     * users/{uid}/medications/{firestoreId}.
     *
     * On success, stamps the local Room record with the firestoreId.
     */
    public void uploadMedication(Context context, MedicationEntity entity, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        Map<String, Object> med = new HashMap<>();
        med.put("name",          entity.name);
        med.put("brandName",     entity.brandName);
        med.put("dosage",        entity.dosage);
        med.put("expiry",        entity.expiry);
        med.put("syncSource",    "patient");
        med.put("updatedAt",     System.currentTimeMillis());

        String uid = user.getUid();

        // If already has a firestoreId, update that doc; otherwise create new
        if (entity.firestoreId != null && !entity.firestoreId.isEmpty()) {
            db.collection("users").document(uid)
              .collection("medications").document(entity.firestoreId)
              .set(med)
              .addOnSuccessListener(aVoid -> {
                  stampFirestoreId(context, entity.id, entity.firestoreId);
                  if (callback != null) callback.onSuccess();
              })
              .addOnFailureListener(e -> {
                  if (callback != null) callback.onFailure(e);
              });
        } else {
            db.collection("users").document(uid)
              .collection("medications")
              .add(med)
              .addOnSuccessListener(docRef -> {
                  stampFirestoreId(context, entity.id, docRef.getId());
                  if (callback != null) callback.onSuccess();
              })
              .addOnFailureListener(e -> {
                  if (callback != null) callback.onFailure(e);
              });
        }
    }

    /**
     * Removes a medication document from Firestore.
     * The local MedicationSyncService listener will pick up the REMOVED event
     * and delete from Room DB automatically.
     */
    public void deleteMedicationFromCloud(String firestoreId, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }
        if (firestoreId == null || firestoreId.isEmpty()) {
            if (callback != null) callback.onFailure(new Exception("No Firestore ID provided"));
            return;
        }

        db.collection("users").document(user.getUid())
          .collection("medications").document(firestoreId)
          .delete()
          .addOnSuccessListener(aVoid -> {
              if (callback != null) callback.onSuccess();
          })
          .addOnFailureListener(e -> {
              if (callback != null) callback.onFailure(e);
          });
    }

    private void stampFirestoreId(Context context, int localId, String firestoreId) {
        new Thread(() -> {
            AppDatabaseProvider.getInstance(context)
                .medicationDao()
                .markSynced(localId, firestoreId, System.currentTimeMillis());
        }).start();
    }

    // ─── Adherence Log Sync ───────────────────────────────────────────────────

    /**
     * Pushes a single AdherenceLogEntity to Firestore at medication_logs/{auto-id}.
     * On success, marks the local entry as synced.
     */
    public void syncAdherenceLog(Context context, AdherenceLogEntity log, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        Map<String, Object> entry = new HashMap<>();
        entry.put("patientUid",             log.patientUid);
        entry.put("medicationId",           log.medicationId);
        entry.put("medicationFirestoreId",  log.medicationFirestoreId);
        entry.put("medicationName",         log.medicationName);
        entry.put("scheduledTime",          log.scheduledTimeMillis);
        entry.put("actionType",             log.actionType);
        entry.put("actedAt",                log.actedAt);
        entry.put("snoozeCount",            log.snoozeCount);
        entry.put("deviceTimestamp",        log.deviceTimestamp);

        db.collection("medication_logs")
          .add(entry)
          .addOnSuccessListener(docRef -> {
              new Thread(() -> {
                  AppDatabaseProvider.getInstance(context)
                      .adherenceLogDao()
                      .markSynced(log.id, docRef.getId());
              }).start();
              if (callback != null) callback.onSuccess();
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Adherence log sync failed: " + e.getMessage());
              if (callback != null) callback.onFailure(e);
          });
    }

    /**
     * Updates an EXISTING Firestore medication_logs document in-place.
     * Used when an adherence state changes (e.g. MISSED → PENDING) and the
     * record was already pushed to Firestore — avoids creating a duplicate doc.
     *
     * If the log's firestoreLogId is empty this falls back to syncAdherenceLog
     * (insert new).
     */
    public void updateAdherenceLog(Context context, AdherenceLogEntity log, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        if (log.firestoreLogId == null || log.firestoreLogId.isEmpty()) {
            // No existing Firestore doc — delegate to insert path
            syncAdherenceLog(context, log, callback);
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("actionType",        log.actionType);
        update.put("actedAt",           log.actedAt);
        update.put("scheduledTime",     log.scheduledTimeMillis);
        update.put("snoozeCount",       log.snoozeCount);
        update.put("deviceTimestamp",   System.currentTimeMillis());

        db.collection("medication_logs")
          .document(log.firestoreLogId)
          .update(update)
          .addOnSuccessListener(aVoid -> {
              new Thread(() ->
                  AppDatabaseProvider.getInstance(context)
                      .adherenceLogDao().markSynced(log.id, log.firestoreLogId)
              ).start();
              if (callback != null) callback.onSuccess();
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "updateAdherenceLog failed: " + e.getMessage());
              if (callback != null) callback.onFailure(e);
          });
    }

    /**
     * Pushes all unsynced AdherenceLogEntities to Firestore in a batch.
     * Safe to call on reconnect.
     */
    public void syncPendingAdherenceLogs(Context context) {
        new Thread(() -> {
            List<AdherenceLogEntity> unsynced =
                AppDatabaseProvider.getInstance(context).adherenceLogDao().getUnsyncedLogs();
            for (AdherenceLogEntity log : unsynced) {
                syncAdherenceLog(context, log, null);
            }
        }).start();
    }

    // ─── Guardian Link Sync ───────────────────────────────────────────────────

    /**
     * Fetches guardian links for the current patient from Firestore and
     * stores them in the local Room DB.
     */
    public void syncGuardianLinks(Context context, final SyncCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onFailure(new Exception("User not authenticated"));
            return;
        }

        String uid = user.getUid();
        db.collection("guardian_links")
          .whereEqualTo("patientUid", uid)
          .whereEqualTo("active", true)
          .get()
          .addOnSuccessListener(querySnapshot -> {
              new Thread(() -> {
                  for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                      GuardianLinkEntity link = new GuardianLinkEntity();
                      link.linkId          = doc.getId();
                      link.guardianUid     = doc.getString("guardianUid") != null ? doc.getString("guardianUid") : "";
                      link.patientUid      = uid;
                      link.permissionsJson = doc.getString("permissionsJson");
                      link.active          = true;
                      Long created = doc.getLong("createdAt");
                      link.createdAt       = (created != null) ? created : System.currentTimeMillis();
                      AppDatabaseProvider.getInstance(context).guardianLinkDao().insertOrUpdate(link);
                  }
                  if (callback != null) callback.onSuccess();
              }).start();
          })
          .addOnFailureListener(e -> {
              Log.e(TAG, "Guardian link sync failed: " + e.getMessage());
              if (callback != null) callback.onFailure(e);
          });
    }

    // ─── Interfaces ──────────────────────────────────────────────────────────

    public interface SyncCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    public interface DocumentUploadCallback {
        void onSuccess(String cloudUrl, String publicId, String firestoreId);
        void onFailure(Exception e);
    }
}
