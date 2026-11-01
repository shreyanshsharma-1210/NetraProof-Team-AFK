package com.emergency.patient.network;

import com.emergency.patient.network.models.HealthResponse;
import com.emergency.patient.network.models.LesionEvidenceResponse;
import com.emergency.patient.network.models.ScreenResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

/**
 * NetraProofApi — Retrofit interface for the NetraProof Clinical DR Screening API on Render.
 */
public interface NetraProofApi {

    /**
     * Proactive Health Ping — Awakens Render cold starts.
     */
    @GET("health")
    Call<HealthResponse> getHealth();

    /**
     * Primary Screening Endpoint:
     * Executes optical Quality Gate, 5-Fold ONNX EfficientNet-B0 Grading Ensemble,
     * and Clinical Action Guidance.
     */
    @Multipart
    @POST("api/v1/screen")
    Call<ScreenResponse> screenFundus(
            @Part MultipartBody.Part file,
            @Part("patient_id") RequestBody patientId,
            @Part("eye_laterality") RequestBody eyeLaterality,
            @Part("include_experimental_segmentation") RequestBody includeSegmentation
    );

    /**
     * Screening Endpoint overload with defaults.
     */
    @Multipart
    @POST("api/v1/screen")
    Call<ScreenResponse> screenFundus(
            @Part MultipartBody.Part file,
            @Part("patient_id") RequestBody patientId,
            @Part("eye_laterality") RequestBody eyeLaterality
    );

    /**
     * On-Demand Lesion Segmentation Evidence Endpoint.
     * Returns candidate transparent lesion mask PNGs for cotton-wool spots, hard exudates, hemorrhages, and microaneurysms.
     */
    @Multipart
    @POST("api/v1/evidence/lesions")
    Call<LesionEvidenceResponse> getLesionEvidence(
            @Part MultipartBody.Part file,
            @Part("sample_id") RequestBody sampleId,
            @Part("threshold") RequestBody threshold,
            @Part("include_masks") RequestBody includeMasks
    );

    /**
     * Fast-evaluation sample screening endpoint.
     */
    @FormUrlEncoded
    @POST("api/v1/screen-sample")
    Call<ScreenResponse> screenSample(
            @Field("sample_id") String sampleId,
            @Field("patient_id") String patientId,
            @Field("include_experimental_segmentation") boolean includeSegmentation
    );
}

