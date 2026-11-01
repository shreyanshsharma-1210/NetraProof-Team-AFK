package com.emergency.patient.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.emergency.patient.BuildConfig;
import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.ChatMessageEntity;
import com.emergency.patient.db.MedicationEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.models.diabetes360.BloodPressureRecord;
import com.emergency.patient.models.diabetes360.Diabetes360Data;
import com.emergency.patient.models.diabetes360.Diabetes360Repository;
import com.emergency.patient.models.diabetes360.GlucoseRecord;
import com.emergency.patient.models.diabetes360.Hba1cRecord;
import com.emergency.patient.network.GroqApiService;
import com.emergency.patient.rag.ClinicalIntelligenceEngine;
import com.emergency.patient.security.TokenManager;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * NetraAiAssistantActivity — Netra AI Health Information Assistant (Page 13).
 *
 * Provides a conversational explanation layer grounded in the patient's authenticated
 * health records, lab documents, and approved clinical guidelines.
 * Powered by Groq LLaMA 3.3 Compound Model with an on-device ClinicalIntelligenceEngine fallback.
 */
public class NetraAiAssistantActivity extends AppCompatActivity {

    private ImageView btnNetraAiBack, btnClearChatHistory, btnNetraAiSend;
    private TextView tvAiStatusBadge;
    private RecyclerView rvNetraAiChat;
    private EditText etNetraAiMessage;
    private ProgressBar progressNetraAiLoading;

    // Suggestion Pills
    private TextView pillSuggestRetina, pillSuggestHba1c, pillSuggestFoot, pillSuggestBp, pillSuggestMeds, pillSuggestExudates;

    private ChatAdapter adapter;
    private final List<ChatMessage> messages = new ArrayList<>();
    private GroqApiService groqService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private String patientUuid;

    private static final String FALLBACK_KEY = "";

    public static class ChatMessage {
        public String sender;
        public String content;
        public long timestamp;

        public ChatMessage(String sender, String content) {
            this.sender = sender;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }

        public ChatMessage(String sender, String content, long timestamp) {
            this.sender = sender;
            this.content = content;
            this.timestamp = timestamp;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_netra_ai_assistant);

        patientUuid = TokenManager.getUUID(this);
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            patientUuid = "default_patient";
        }

        initViews();
        setupRetrofit();
        setupClickListeners();
        loadChatHistory();
        com.emergency.patient.utils.NetraProfileHeaderHelper.setup(this);

        if (getIntent() != null && getIntent().hasExtra("initial_query")) {
            String q = getIntent().getStringExtra("initial_query");
            if (q != null && !q.trim().isEmpty()) {
                etNetraAiMessage.setText(q);
                handleSendMessage();
            }
        }
    }

    private void initViews() {
        btnNetraAiBack = findViewById(R.id.btnNetraAiBack);
        btnClearChatHistory = findViewById(R.id.btnClearChatHistory);
        btnNetraAiSend = findViewById(R.id.btnNetraAiSend);
        tvAiStatusBadge = findViewById(R.id.tvAiStatusBadge);
        rvNetraAiChat = findViewById(R.id.rvNetraAiChat);
        etNetraAiMessage = findViewById(R.id.etNetraAiMessage);
        progressNetraAiLoading = findViewById(R.id.progressNetraAiLoading);

        pillSuggestRetina = findViewById(R.id.pillSuggestRetina);
        pillSuggestHba1c = findViewById(R.id.pillSuggestHba1c);
        pillSuggestFoot = findViewById(R.id.pillSuggestFoot);
        pillSuggestBp = findViewById(R.id.pillSuggestBp);
        pillSuggestMeds = findViewById(R.id.pillSuggestMeds);
        pillSuggestExudates = findViewById(R.id.pillSuggestExudates);

        adapter = new ChatAdapter(messages);
        rvNetraAiChat.setLayoutManager(new LinearLayoutManager(this));
        rvNetraAiChat.setAdapter(adapter);
    }

    private void setupRetrofit() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(12, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.groq.com/openai/v1/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        groqService = retrofit.create(GroqApiService.class);
    }

    private void setupClickListeners() {
        btnNetraAiBack.setOnClickListener(v -> finish());

        btnClearChatHistory.setOnClickListener(v -> confirmClearChat());

        btnNetraAiSend.setOnClickListener(v -> handleSendMessage());

        etNetraAiMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                handleSendMessage();
                return true;
            }
            return false;
        });

        pillSuggestRetina.setOnClickListener(v -> submitPredefinedQuery("Explain my retinal screening"));
        pillSuggestHba1c.setOnClickListener(v -> submitPredefinedQuery("What does my HbA1c mean?"));
        pillSuggestFoot.setOnClickListener(v -> submitPredefinedQuery("How do I inspect my feet?"));
        pillSuggestBp.setOnClickListener(v -> submitPredefinedQuery("Review my blood pressure"));
        if (pillSuggestMeds != null) {
            pillSuggestMeds.setOnClickListener(v -> submitPredefinedQuery("Is my medication safe?"));
        }
        if (pillSuggestExudates != null) {
            pillSuggestExudates.setOnClickListener(v -> submitPredefinedQuery("What are hard exudates?"));
        }
    }

    
    @Nullable
    private String getHardcodedAnswerForSuggestedQuestion(String rawQuery) {
        if (rawQuery == null) return null;
        String q = rawQuery.toLowerCase(Locale.ROOT).trim();

        // 1. Retinal Screening
        if (q.contains("explain my retinal screening") || q.contains("explain how my retinal screening works") || (q.contains("retinal") && q.contains("screening"))) {
            return "🩺 **Retinal Screening Overview**:\n" +
                    "• **Purpose**: Uses 5-fold ensemble AI to detect microvascular damage (Diabetic Retinopathy) before vision loss occurs.\n" +
                    "• **Grading Scale**: Classified from Grade 0 (No Retinopathy) to Grade 4 (Proliferative DR) under ICDR standards.\n" +
                    "• **Quality Gate**: Assesses focus, illumination, and 45° field centering to ensure reliable grading.\n" +
                    "• **Action Plan**: Annual screening if Grade 0; prompt clinical ophthalmology referral if Grade 2 or higher.";
        }

        // 2. HbA1c
        if (q.contains("what does my hba1c mean") || q.contains("what does my hba1c test measure") || q.contains("hba1c")) {
            return "📊 **HbA1c Key Facts**:\n" +
                    "• **3-Month Average**: Reflects average blood sugar over the last 90–120 days, unlike a spot glucose reading.\n" +
                    "• **Clinical Target**: For most diabetic adults, the target is **< 7.0%** (normal is < 5.7%; prediabetes is 5.7–6.4%).\n" +
                    "• **Complication Risk**: Every 1% drop significantly reduces the risk of retinal, kidney, and nerve damage.\n" +
                    "• **Monitoring**: Have your HbA1c checked every 3 to 6 months as advised by your doctor.";
        }

        // 3. Foot Care
        if (q.contains("how do i inspect my feet") || q.contains("how should i perform a diabetic foot") || (q.contains("foot") && q.contains("inspect")) || q.contains("feet")) {
            return "🦶 **Daily Diabetic Foot Care**:\n" +
                    "• **Visual Inspection**: Check tops, soles, and between toes daily for cuts, blisters, redness, or swelling.\n" +
                    "• **Nerve Sensation**: Watch for numbness, tingling, or burning sensations (signs of peripheral neuropathy).\n" +
                    "• **Protection**: Wash with lukewarm water, moisturize (avoiding between toes), and never walk barefoot.\n" +
                    "• **Warning Sign**: Seek immediate medical care for any non-healing sore, color change, or ingrown toenail.";
        }

        // 4. Blood Pressure
        if (q.contains("review my blood pressure") || q.contains("what is the significance of tracking both systolic") || (q.contains("blood pressure") || q.contains("bp"))) {
            return "💓 **Blood Pressure & Diabetes**:\n" +
                    "• **Target Guideline**: The recommended goal is generally **< 130/80 mmHg** to safeguard delicate blood vessels.\n" +
                    "• **Microvascular Protection**: Controlled BP slows the progression of diabetic retinopathy and protects kidney filtration.\n" +
                    "• **Tracking Habit**: Log readings at the same time daily, limit dietary sodium, and review trends with your physician.";
        }

        // 5. Medication Safety
        if (q.contains("is my medication safe") || q.contains("medication safe") || q.contains("medicine safe")) {
            return "💊 **Medication Safety Guide**:\n" +
                    "• **Heart & Blood Thinners**: If you have a cardiac history, consult your cardiologist before starting any anticoagulants or aspirin.\n" +
                    "• **Allergy Cross-Check**: Always verify that new prescriptions avoid known allergies (e.g. penicillin or beta-lactams).\n" +
                    "• **Adherence**: Take prescribed diabetes medications (such as Metformin or Insulin) consistently at the scheduled times.\n" +
                    "• **Rule**: Never stop or adjust prescription dosages without direct physician approval.";
        }

        // 6. Hard Exudates
        if (q.contains("what are hard exudates") || q.contains("hard exudate") || q.contains("exudate")) {
            return "👁️ **Hard Exudates (HE)**:\n" +
                    "• **What They Are**: Small lipid and lipoprotein deposits that leak from damaged retinal capillaries into the retina.\n" +
                    "• **Appearance**: Sharp, yellow-white punctate spots commonly found in clusters near the macula.\n" +
                    "• **Significance**: Indicates active microvascular leakage in Diabetic Retinopathy (Grade 2+ Moderate to Severe NPDR).\n" +
                    "• **Management**: Tight glycemic and blood pressure control helps prevent further capillary leakage.";
        }

        return null;
    }

    private void submitPredefinedQuery(String query) {
        etNetraAiMessage.setText(query);
        handleSendMessage();
    }

    private void loadChatHistory() {
        new Thread(() -> {
            try {
                AppDatabase db = AppDatabaseProvider.getInstance(this);
                List<ChatMessageEntity> history = db.chatMessageDao().getMessagesForPatient(patientUuid);

                runOnUiThread(() -> {
                    if (history == null || history.isEmpty()) {
                        addMessage("AI", "Hello! I am Netra AI, your diabetes and retinal health information assistant. Ask me questions about your retinal screening, lab reports, glucose tracking, or foot care.", false);
                    } else {
                        messages.clear();
                        for (ChatMessageEntity entity : history) {
                            messages.add(new ChatMessage(entity.sender, entity.content, entity.timestamp));
                        }
                        adapter.notifyDataSetChanged();
                        if (!messages.isEmpty()) {
                            rvNetraAiChat.scrollToPosition(messages.size() - 1);
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (messages.isEmpty()) {
                        addMessage("AI", "Hello! I am Netra AI. How can I help you understand your health records today?", false);
                    }
                });
            }
        }).start();
    }

    private void handleSendMessage() {
        String query = etNetraAiMessage.getText().toString().trim();
        if (query.isEmpty()) return;

        addMessage("You", query, true);
        etNetraAiMessage.setText("");

        // Check if there is an instant curated answer for suggested questions
        String instantAnswer = getHardcodedAnswerForSuggestedQuestion(query);
        if (instantAnswer != null) {
            addMessage("AI", instantAnswer, true);
            return;
        }

        progressNetraAiLoading.setVisibility(View.VISIBLE);

        // Fetch patient context for grounded answer
        new Thread(() -> {
            PatientEntity patient = null;
            List<MedicationEntity> activeMeds = null;
            try {
                AppDatabase db = AppDatabaseProvider.getInstance(this);
                patient = db.patientDao().getPatient(patientUuid);
                activeMeds = db.medicationDao().getMedicationsForPatient(patientUuid);
            } catch (Exception ignored) {}

            Diabetes360Data d360 = Diabetes360Repository.getInstance().getData(this, patientUuid);

            // Construct Context Summary
            StringBuilder ctxBuilder = new StringBuilder();
            if (patient != null && patient.fullName != null) {
                ctxBuilder.append("Patient: ").append(patient.fullName).append(". ");
            }
            if (d360.getDiabetesContext() != null && !"Not specified".equalsIgnoreCase(d360.getDiabetesContext().getDiabetesType())) {
                ctxBuilder.append("Condition: ").append(d360.getDiabetesContext().getDiabetesType()).append(". ");
            }
            if (!d360.getGlucoseRecords().isEmpty()) {
                GlucoseRecord g = d360.getGlucoseRecords().get(0);
                ctxBuilder.append("Latest Glucose: ").append(g.getFormattedValueWithUnit()).append(" (").append(g.getReadingContext().getDisplayName()).append("). ");
            }
            if (!d360.getHba1cRecords().isEmpty()) {
                Hba1cRecord a = d360.getHba1cRecords().get(0);
                ctxBuilder.append("Latest HbA1c: ").append(a.getFormattedValueWithUnit()).append(". ");
            }
            if (!d360.getBloodPressureRecords().isEmpty()) {
                BloodPressureRecord bp = d360.getBloodPressureRecords().get(0);
                ctxBuilder.append("Latest BP: ").append(bp.getFormattedReading()).append(". ");
            }

            final PatientEntity finalPat = patient;
            final List<MedicationEntity> finalMeds = activeMeds;
            final String groundedContext = ctxBuilder.toString();

            // Attempt online Groq completion
            sendGroqRequest(query, groundedContext, finalPat, finalMeds);
        }).start();
    }

    private void sendGroqRequest(String userQuery, String groundedContext,
                                 @Nullable PatientEntity patient, @Nullable List<MedicationEntity> activeMeds) {
        String apiKey = BuildConfig.GROQ_API_KEY != null && !BuildConfig.GROQ_API_KEY.trim().isEmpty()
                ? BuildConfig.GROQ_API_KEY : FALLBACK_KEY;

        String systemPrompt = "You are Netra AI, a clinical assistant for NetraProof (retinal screening & diabetes). " +
                "CRITICAL BREVITY RULE: Keep all responses CONCISE and BRIEF (maximum 2-3 short paragraphs or 3-4 bullet points, under 80-100 words). Be direct and get straight to the point without long essays or fluff. " +
                "Your role is to explain medical concepts and patient records clearly and empathetically. " +
                "CLINICAL SAFETY MANDATES: Never prescribe medication or change dosages. Do not declare definitive diagnoses. " +
                "Always ground explanations in available records and encourage consulting their doctor. " +
                "Available Patient Context: " + groundedContext;

        List<GroqApiService.GroqRequest.Message> apiMessages = new ArrayList<>();
        apiMessages.add(new GroqApiService.GroqRequest.Message("system", systemPrompt));

        // Include last 4 messages for dialogue continuity
        int start = Math.max(0, messages.size() - 4);
        for (int i = start; i < messages.size(); i++) {
            ChatMessage cm = messages.get(i);
            String role = "You".equalsIgnoreCase(cm.sender) ? "user" : "assistant";
            apiMessages.add(new GroqApiService.GroqRequest.Message(role, cm.content));
        }

        GroqApiService.GroqRequest request = new GroqApiService.GroqRequest(
                "openai/gpt-oss-120b",
                apiMessages
        );

        groqService.getChatCompletion("Bearer " + apiKey, request).enqueue(new Callback<GroqApiService.GroqResponse>() {
            @Override
            public void onResponse(Call<GroqApiService.GroqResponse> call, Response<GroqApiService.GroqResponse> response) {
                if (response.isSuccessful() && response.body() != null &&
                        response.body().choices != null && !response.body().choices.isEmpty()) {
                    String reply = response.body().choices.get(0).message.content;
                    mainHandler.post(() -> {
                        progressNetraAiLoading.setVisibility(View.GONE);
                        tvAiStatusBadge.setText("Online");
                        tvAiStatusBadge.setTextColor(ContextCompat.getColor(NetraAiAssistantActivity.this, R.color.netra_primary_teal));
                        addMessage("AI", reply, true);
                    });
                } else {
                    // Try backup model openai/gpt-oss-20b
                    tryBackupGroqModel(apiKey, apiMessages, userQuery, patient, activeMeds);
                }
            }

            @Override
            public void onFailure(Call<GroqApiService.GroqResponse> call, Throwable t) {
                tryBackupGroqModel(apiKey, apiMessages, userQuery, patient, activeMeds);
            }
        });
    }

    
    private void tryBackupGroqModel(String apiKey, List<GroqApiService.GroqRequest.Message> apiMessages,
                                    String userQuery, @Nullable PatientEntity patient, @Nullable List<MedicationEntity> activeMeds) {
        GroqApiService.GroqRequest backupReq = new GroqApiService.GroqRequest("openai/gpt-oss-20b", apiMessages);
        groqService.getChatCompletion("Bearer " + apiKey, backupReq).enqueue(new Callback<GroqApiService.GroqResponse>() {
            @Override
            public void onResponse(Call<GroqApiService.GroqResponse> call, Response<GroqApiService.GroqResponse> response) {
                mainHandler.post(() -> {
                    progressNetraAiLoading.setVisibility(View.GONE);
                    if (response.isSuccessful() && response.body() != null &&
                            response.body().choices != null && !response.body().choices.isEmpty()) {
                        String reply = response.body().choices.get(0).message.content;
                        tvAiStatusBadge.setText("Online");
                        tvAiStatusBadge.setTextColor(ContextCompat.getColor(NetraAiAssistantActivity.this, R.color.netra_primary_teal));
                        addMessage("AI", reply, true);
                    } else {
                        fallbackOnDeviceReasoning(userQuery, patient, activeMeds);
                    }
                });
            }

            @Override
            public void onFailure(Call<GroqApiService.GroqResponse> call, Throwable t) {
                mainHandler.post(() -> {
                    progressNetraAiLoading.setVisibility(View.GONE);
                    tvAiStatusBadge.setText("On-Device");
                    tvAiStatusBadge.setTextColor(ContextCompat.getColor(NetraAiAssistantActivity.this, R.color.netra_warning));
                    fallbackOnDeviceReasoning(userQuery, patient, activeMeds);
                });
            }
        });
    }

    private void fallbackOnDeviceReasoning(String userQuery, @Nullable PatientEntity patient, @Nullable List<MedicationEntity> activeMeds) {
        String reply = ClinicalIntelligenceEngine.generateResponse(userQuery, patient, activeMeds, null);
        addMessage("AI", reply, true);
    }

    private void addMessage(String sender, String content, boolean persist) {
        ChatMessage msg = new ChatMessage(sender, content);
        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        rvNetraAiChat.scrollToPosition(messages.size() - 1);

        if (persist) {
            new Thread(() -> {
                try {
                    AppDatabase db = AppDatabaseProvider.getInstance(this);
                    ChatMessageEntity entity = new ChatMessageEntity(patientUuid, sender, content);
                    entity.timestamp = msg.timestamp;
                    db.chatMessageDao().insertMessage(entity);
                } catch (Exception ignored) {}
            }).start();
        }
    }

    private void confirmClearChat() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Conversation")
                .setMessage("Are you sure you want to clear your conversation history with Netra AI?")
                .setPositiveButton("Clear", (d, w) -> {
                    new Thread(() -> {
                        try {
                            AppDatabaseProvider.getInstance(this).chatMessageDao().deleteMessagesForPatient(patientUuid);
                        } catch (Exception ignored) {}
                        runOnUiThread(() -> {
                            messages.clear();
                            adapter.notifyDataSetChanged();
                            addMessage("AI", "Conversation cleared. How can I assist you with your health information today?", false);
                        });
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Chat RecyclerView Adapter ────────────────────────────────────────────

    private class ChatAdapter extends RecyclerView.Adapter<ChatViewHolder> {
        private final List<ChatMessage> list;

        ChatAdapter(List<ChatMessage> list) {
            this.list = list;
        }

        @NonNull
        @Override
        public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new ChatViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
            ChatMessage msg = list.get(position);
            holder.tvSender.setText(msg.sender);
            holder.tvMessage.setText(msg.content);

            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) holder.llContainer.getLayoutParams();
            if (msg.sender.equalsIgnoreCase("You")) {
                params.addRule(RelativeLayout.ALIGN_PARENT_END);
                params.removeRule(RelativeLayout.ALIGN_PARENT_START);
                holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble_user);
                holder.tvMessage.setTextColor(android.graphics.Color.WHITE);
                holder.tvSender.setVisibility(View.GONE);
            } else {
                params.addRule(RelativeLayout.ALIGN_PARENT_START);
                params.removeRule(RelativeLayout.ALIGN_PARENT_END);
                holder.tvMessage.setBackgroundResource(R.drawable.bg_chat_bubble_ai);
                holder.tvMessage.setTextColor(ContextCompat.getColor(NetraAiAssistantActivity.this, R.color.netra_text_primary));
                holder.tvSender.setVisibility(View.VISIBLE);
            }
            holder.llContainer.setLayoutParams(params);
        }

        @Override
        public int getItemCount() {
            return list.size();
        }
    }

    private static class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView tvSender, tvMessage;
        LinearLayout llContainer;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSender = itemView.findViewById(R.id.tv_sender);
            tvMessage = itemView.findViewById(R.id.tv_message);
            llContainer = itemView.findViewById(R.id.ll_message_container);
        }
    }
}
