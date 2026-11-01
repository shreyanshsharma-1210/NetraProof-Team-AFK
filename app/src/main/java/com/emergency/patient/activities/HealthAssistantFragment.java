package com.emergency.patient.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.emergency.patient.BuildConfig;
import com.emergency.patient.R;
import com.emergency.patient.db.AppDatabase;
import com.emergency.patient.db.AppDatabaseProvider;
import com.emergency.patient.db.ChatMessageEntity;
import com.emergency.patient.db.PatientEntity;
import com.emergency.patient.network.GroqApiService;
import com.emergency.patient.rag.ClinicalIntelligenceEngine;
import com.emergency.patient.rag.HealthKnowledgeBase;
import com.emergency.patient.rag.RagManager;
import com.emergency.patient.security.TokenManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * HealthAssistantFragment — Direct, to-the-point AI Clinical Assistant powered by Groq Compound LLM.
 * Rebranded for NetraProof as Netra AI Clinical Assistant.
 */
public class HealthAssistantFragment extends Fragment {

    private RecyclerView rvChat;
    private EditText etMessage;
    private ImageButton btnSend;
    private ProgressBar progressLoading;
    private ChatAdapter adapter;
    private List<ChatMessage> messages = new ArrayList<>();
    private GroqApiService groqService;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final String FALLBACK_KEY = "";

    private String getApiKey() {
        if (BuildConfig.GROQ_API_KEY != null && !BuildConfig.GROQ_API_KEY.trim().isEmpty()) {
            return BuildConfig.GROQ_API_KEY;
        }
        return FALLBACK_KEY;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_health_assistant, container, false);

        rvChat = view.findViewById(R.id.rv_chat);
        etMessage = view.findViewById(R.id.et_message);
        btnSend = view.findViewById(R.id.btn_send);
        progressLoading = view.findViewById(R.id.progress_loading);

        adapter = new ChatAdapter(messages);
        rvChat.setLayoutManager(new LinearLayoutManager(getContext()));
        rvChat.setAdapter(adapter);

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.groq.com/openai/v1/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        groqService = retrofit.create(GroqApiService.class);

        btnSend.setOnClickListener(v -> sendMessage());

        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendMessage();
                return true;
            }
            return false;
        });

        View btnBack = view.findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getActivity() instanceof DashboardActivity) {
                    DashboardActivity act = (DashboardActivity) getActivity();
                    act.setFloatingNavbarVisible(true);
                    if (getActivity().getSupportFragmentManager().getBackStackEntryCount() > 0) {
                        getActivity().getSupportFragmentManager().popBackStack();
                    } else {
                        act.selectFloatingTab(0);
                    }
                } else if (getActivity() != null) {
                    getActivity().onBackPressed();
                }
            });
        }

        loadChatHistory();

        // Check if pre-populated initial query was provided
        if (getArguments() != null && getArguments().containsKey("initial_query")) {
            String initialQuery = getArguments().getString("initial_query");
            if (initialQuery != null && !initialQuery.isEmpty()) {
                etMessage.setText(initialQuery);
                etMessage.setSelection(initialQuery.length());
            }
        }

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() instanceof DashboardActivity) {
            ((DashboardActivity) getActivity()).setFloatingNavbarVisible(false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() instanceof DashboardActivity) {
            ((DashboardActivity) getActivity()).setFloatingNavbarVisible(true);
        }
    }

    private void loadChatHistory() {
        if (getContext() == null) return;
        String patientUuid = TokenManager.getUUID(getContext());
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            patientUuid = "default_patient";
        }
        final String effectiveUuid = patientUuid;

        new Thread(() -> {
            try {
                if (getContext() == null) return;
                AppDatabase db = AppDatabaseProvider.getInstance(getContext());
                List<ChatMessageEntity> history = db.chatMessageDao().getMessagesForPatient(effectiveUuid);

                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (history == null || history.isEmpty()) {
                        addMessage("AI", "Hello! I am Netra AI Clinical Assistant. Ask me any question about your retinal screening, diabetic care, medications, or lab reports.", false);
                    } else {
                        messages.clear();
                        for (ChatMessageEntity msg : history) {
                            messages.add(new ChatMessage(msg.sender, msg.content));
                        }
                        adapter.notifyDataSetChanged();
                        if (!messages.isEmpty()) {
                            rvChat.scrollToPosition(messages.size() - 1);
                        }
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    if (!isAdded()) return;
                    if (messages.isEmpty()) {
                        addMessage("AI", "Hello! I am Netra AI Clinical Assistant. How can I assist you with your health today?", false);
                    }
                });
            }
        }).start();
    }

    private void sendMessage() {
        String userQuery = etMessage.getText().toString().trim();
        if (userQuery.isEmpty()) return;

        addMessage("You", userQuery, true);
        etMessage.setText("");

        if (getContext() == null) return;
        String patientUuid = TokenManager.getUUID(getContext());
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            patientUuid = "default_patient";
        }
        final String effectiveUuid = patientUuid;

        new Thread(() -> {
            if (getContext() == null) return;
            PatientEntity patient = null;
            List<com.emergency.patient.db.MedicationEntity> activeMeds = null;
            try {
                AppDatabase db = AppDatabaseProvider.getInstance(getContext());
                patient = db.patientDao().getPatient(effectiveUuid);
                activeMeds = db.medicationDao().getMedicationsForPatient(effectiveUuid);
            } catch (Exception ignored) {}

            List<String> docContext = new ArrayList<>();
            try {
                docContext = RagManager.getRelevantContext(getContext(), effectiveUuid, userQuery);
            } catch (Exception ignored) {}

            final PatientEntity finalPatient = patient;
            final List<com.emergency.patient.db.MedicationEntity> finalActiveMeds = activeMeds;
            final List<String> finalDocContext = docContext;

            mainHandler.post(() -> {
                if (!isAdded()) return;

                StringBuilder contextBuilder = new StringBuilder();
                if (finalPatient != null) {
                    contextBuilder.append("Patient EHR Reference (Only use if relevant to question):\n");
                    if (finalPatient.fullName != null) contextBuilder.append("- Name: ").append(finalPatient.fullName).append("\n");
                    if (finalPatient.bloodGroup != null) contextBuilder.append("- Blood Group: ").append(finalPatient.bloodGroup).append("\n");
                    if (finalPatient.clinicalAllergies != null && !finalPatient.clinicalAllergies.isEmpty()) {
                        contextBuilder.append("- Allergies: ").append(finalPatient.clinicalAllergies).append("\n");
                    }
                    if (finalPatient.pastMedicalDiagnosis != null && !finalPatient.pastMedicalDiagnosis.isEmpty()) {
                        contextBuilder.append("- Conditions: ").append(finalPatient.pastMedicalDiagnosis).append("\n");
                    }
                }

                if (finalActiveMeds != null && !finalActiveMeds.isEmpty()) {
                    contextBuilder.append("- Active Medications: ");
                    for (int i = 0; i < finalActiveMeds.size(); i++) {
                        com.emergency.patient.db.MedicationEntity m = finalActiveMeds.get(i);
                        contextBuilder.append(m.name != null ? m.name : "Med");
                        if (m.dosage != null) contextBuilder.append(" (").append(m.dosage).append(")");
                        if (i < finalActiveMeds.size() - 1) contextBuilder.append(", ");
                    }
                    contextBuilder.append("\n");
                }

                if (finalDocContext != null && !finalDocContext.isEmpty()) {
                    contextBuilder.append("\nUploaded Document Context:\n");
                    for (String doc : finalDocContext) {
                        contextBuilder.append("• ").append(doc).append("\n");
                    }
                }

                String systemPrompt = "You are Netra AI, an intelligent, empathetic, and clinically precise Health Assistant specialized in Diabetes Care and Retinal Health.\n\n" +
                        "MANDATORY BREVITY RULE: Keep all answers VERY CONCISE and BRIEF (maximum 2-3 short bullet points, under 80-100 words total). Never generate long essays or repeated disclaimers. Answer directly.\n\n" +
                        "CRITICAL PATIENT EHR & MANDATORY CONTRAINDICATIONS:\n" +
                        "1. HEART PATIENT & BLOOD THINNER CONTRAINDICATION:\n" +
                        "   - The patient is a HEART PATIENT and CANNOT take blood thinning pills / anticoagulants / antiplatelets (e.g. Aspirin, Ecosprin, Clopidogrel, Warfarin, Heparin, Apixaban, Rivaroxaban, Dabigatran, etc.).\n" +
                        "   - Taking blood thinning pills will affect the patient drastically due to dangerous bleeding risks and cardiac complications.\n" +
                        "   - If the user asks ANY question related to taking blood thinners, blood-thinning pills, Aspirin, or anticoagulants, you MUST strictly tell them NOT to take the medicine due to their heart condition history, and explicitly advise them to consult their cardiologist/doctor.\n" +
                        "2. BETA-LACTAM ANTIBIOTIC ALLERGY:\n" +
                        "   - Patient has a severe allergy to Penicillin, Amoxicillin, and Beta-Lactams. Strictly warn against taking them and advise safe alternatives (e.g. Azithromycin) and doctor consultation.\n\n" +
                        (contextBuilder.length() > 0 ? "ADDITIONAL EHR CONTEXT:\n" + contextBuilder.toString() : "") +
                        "\nCLINICAL RESPONSE GUIDELINES:\n" +
                        "1. DIRECT & HELPFUL ADVICE: Answer the user's health query clearly, providing relevant clinical context, explanations, and actionable lifestyle/home care advice.\n" +
                        "2. RETINAL INTELLIGENCE & DIABETES: Explain diabetic retinopathy stages (Grades 0–4 ICDR), microaneurysms, macular edema, and preventative glycemic control in patient-friendly terms.\n" +
                        "3. CONTEXT INTEGRATION: Use the patient's EHR information (heart condition, allergies, medications, chronic conditions, test reports) to personalize recommendations safely.\n" +
                        "4. DRUG & ALLERGY SAFETY: Always check if any medication asked about conflicts with patient allergies (e.g., penicillin / beta-lactam allergy) or active conditions.\n" +
                        "5. DOCTOR CONSULTATION FOR SERIOUS CONDITIONS: If the query or symptoms indicate anything serious, potentially dangerous (e.g. sudden vision loss, dark spots/floaters, chest pain, breathing difficulty, severe bleeding), explicitly advise the user to consult an ophthalmologist/doctor immediately.\n" +
                        "6. FORMATTING: Use clean markdown, bullet points, and concise formatting for readability.\n";

                performGroqInference(systemPrompt, userQuery, finalPatient, finalActiveMeds, finalDocContext);
            });
        }).start();
    }

    private void performGroqInference(
            String systemPrompt,
            String userQuery,
            @Nullable PatientEntity patient,
            @Nullable List<com.emergency.patient.db.MedicationEntity> activeMeds,
            @Nullable List<String> docContext
    ) {
        if (progressLoading != null) progressLoading.setVisibility(View.VISIBLE);
        if (btnSend != null) btnSend.setEnabled(false);

        List<GroqApiService.GroqRequest.Message> msgList = new ArrayList<>();
        msgList.add(new GroqApiService.GroqRequest.Message("system", systemPrompt));

        // Include recent conversation turns (up to last 6 messages) for multi-turn context
        int startIdx = Math.max(0, messages.size() - 7);
        for (int i = startIdx; i < messages.size() - 1; i++) {
            ChatMessage m = messages.get(i);
            String role = m.sender.equalsIgnoreCase("You") ? "user" : "assistant";
            msgList.add(new GroqApiService.GroqRequest.Message(role, m.content));
        }

        // Add current user query
        msgList.add(new GroqApiService.GroqRequest.Message("user", userQuery));

        // 1. Primary Model: llama-3.3-70b-versatile
        GroqApiService.GroqRequest primaryRequest = new GroqApiService.GroqRequest("openai/gpt-oss-120b", msgList);

        groqService.getChatCompletion("Bearer " + getApiKey(), primaryRequest).enqueue(new Callback<GroqApiService.GroqResponse>() {
            @Override
            public void onResponse(Call<GroqApiService.GroqResponse> call, Response<GroqApiService.GroqResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty()) {
                    if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                    if (btnSend != null) btnSend.setEnabled(true);
                    String answer = response.body().choices.get(0).message.content;
                    addMessage("AI", answer, true);
                } else {
                    // Fallback to fast model: llama-3.1-8b-instant
                    tryFallbackModel(msgList, userQuery, patient, activeMeds, docContext);
                }
            }

            @Override
            public void onFailure(Call<GroqApiService.GroqResponse> call, Throwable t) {
                if (!isAdded()) return;
                tryFallbackModel(msgList, userQuery, patient, activeMeds, docContext);
            }
        });
    }

    private void tryFallbackModel(
            List<GroqApiService.GroqRequest.Message> msgList,
            String originalQuery,
            @Nullable PatientEntity patient,
            @Nullable List<com.emergency.patient.db.MedicationEntity> activeMeds,
            @Nullable List<String> docContext
    ) {
        GroqApiService.GroqRequest fallbackRequest = new GroqApiService.GroqRequest("openai/gpt-oss-20b", msgList);
        groqService.getChatCompletion("Bearer " + getApiKey(), fallbackRequest).enqueue(new Callback<GroqApiService.GroqResponse>() {
            @Override
            public void onResponse(Call<GroqApiService.GroqResponse> call, Response<GroqApiService.GroqResponse> response) {
                if (!isAdded()) return;
                if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                if (btnSend != null) btnSend.setEnabled(true);

                if (response.isSuccessful() && response.body() != null && response.body().choices != null && !response.body().choices.isEmpty()) {
                    String answer = response.body().choices.get(0).message.content;
                    addMessage("AI", answer, true);
                } else {
                    provideDirectOfflineResponse(originalQuery, patient, activeMeds, docContext);
                }
            }

            @Override
            public void onFailure(Call<GroqApiService.GroqResponse> call, Throwable t) {
                if (!isAdded()) return;
                if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                if (btnSend != null) btnSend.setEnabled(true);
                provideDirectOfflineResponse(originalQuery, patient, activeMeds, docContext);
            }
        });
    }

    private void provideDirectOfflineResponse(
            String query,
            @Nullable PatientEntity patient,
            @Nullable List<com.emergency.patient.db.MedicationEntity> activeMeds,
            @Nullable List<String> docContext
    ) {
        String answer = ClinicalIntelligenceEngine.generateResponse(query, patient, activeMeds, docContext);
        addMessage("AI", answer, true);
    }

    private void addMessage(String sender, String content, boolean saveToDb) {
        messages.add(new ChatMessage(sender, content));
        adapter.notifyItemInserted(messages.size() - 1);
        rvChat.scrollToPosition(messages.size() - 1);

        if (saveToDb) {
            saveMessageToDb(sender, content);
        }
    }

    private void saveMessageToDb(String sender, String content) {
        if (getContext() == null) return;
        String patientUuid = TokenManager.getUUID(getContext());
        if (patientUuid == null || patientUuid.trim().isEmpty()) {
            patientUuid = "default_patient";
        }
        final String effectiveUuid = patientUuid;

        new Thread(() -> {
            try {
                if (getContext() == null) return;
                AppDatabase db = AppDatabaseProvider.getInstance(getContext());
                db.chatMessageDao().insertMessage(new ChatMessageEntity(effectiveUuid, sender, content));
            } catch (Exception ignored) {}
        }).start();
    }

    private static class ChatMessage {
        String sender;
        String content;

        ChatMessage(String sender, String content) {
            this.sender = sender;
            this.content = content;
        }
    }

    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
        private List<ChatMessage> chatMessages;

        ChatAdapter(List<ChatMessage> chatMessages) {
            this.chatMessages = chatMessages;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_message, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = chatMessages.get(position);
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
                holder.tvMessage.setTextColor(getResources().getColor(R.color.color_text_primary));
                holder.tvSender.setVisibility(View.VISIBLE);
            }
            holder.llContainer.setLayoutParams(params);
        }

        @Override
        public int getItemCount() {
            return chatMessages.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvSender, tvMessage;
            LinearLayout llContainer;

            ViewHolder(View itemView) {
                super(itemView);
                tvSender = itemView.findViewById(R.id.tv_sender);
                tvMessage = itemView.findViewById(R.id.tv_message);
                llContainer = itemView.findViewById(R.id.ll_message_container);
            }
        }
    }
}
