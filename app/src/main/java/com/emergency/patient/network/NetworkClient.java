package com.emergency.patient.network;

import android.util.Log;

import com.emergency.patient.network.models.HealthResponse;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * NetworkClient — Configured Retrofit client connected to the NetraProof Render backend.
 *
 * Implements 60-second timeouts to reliably accommodate Render free-tier cold starts.
 */
public final class NetworkClient {

    private static final String TAG = "NetworkClient";
    public static final String BASE_URL = "https://netraproof-backend.onrender.com/";

    private static NetraProofApi api;
    private static OkHttpClient okHttpClient;

    private NetworkClient() {}

    public static synchronized OkHttpClient getOkHttpClient() {
        if (okHttpClient == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            okHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(60, TimeUnit.SECONDS) // Important for Render cold starts
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(logging)
                    .build();
        }
        return okHttpClient;
    }

    public static synchronized NetraProofApi getApi() {
        if (api == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(getOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
            api = retrofit.create(NetraProofApi.class);
        }
        return api;
    }

    /**
     * Pro Tip: Background ping to awaken Render free-tier cold starts.
     * Safe to call from MainActivity.onCreate() or DashboardActivity.onCreate().
     */
    public static void pingHealth() {
        new Thread(() -> {
            try {
                getApi().getHealth().enqueue(new Callback<HealthResponse>() {
                    @Override
                    public void onResponse(Call<HealthResponse> call, Response<HealthResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.i(TAG, "NetraProof backend awake & healthy: " + response.body().getService());
                        } else {
                            Log.w(TAG, "Health ping responded with code: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(Call<HealthResponse> call, Throwable t) {
                        Log.w(TAG, "Health ping in progress or cold starting: " + t.getMessage());
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "Health ping exception: " + e.getMessage());
            }
        }).start();
    }
}

