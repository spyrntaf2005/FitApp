package com.example.fitapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Η κύρια δραστηριότητα της εφαρμογής FitApp.
 * Διαχειρίζεται την εισαγωγή στοιχείων χρήστη (onboarding), τη συνομιλία με το AI
 * και την προβολή του ιστορικού των συνομιλιών.
 */
public class MainActivity extends AppCompatActivity {

    //Κλειδιά SharedPreferences για αποθήκευση
    private static final String PREFS_NAME     = "fitai_prefs";
    private static final String KEY_HISTORY    = "conversation_history";
    private static final int    MAX_SAVED      = 30; // Μέγιστος αριθμός αποθηκευμένων συνομιλιών

    //Views για την οθόνη εισαγωγής (Onboarding)
    private ScrollView onboardingLayout;
    private EditText etName;
    private TextView chipBeginner, chipIntermediate, chipAdvanced;
    private TextView chipMuscle, chipLoss, chipEndurance, chipWellness;
    private TextView btnStart, btnHistory;

    // Views για την οθόνη συνομιλίας (Chat)
    private LinearLayout chatMainLayout;
    private LinearLayout chatLayout;
    private ScrollView scrollView;
    private EditText etMessage;
    private TextView btnSend, btnBack;
    private TextView tvHeaderSubtitle, tvUserBadge;
    private LinearLayout typingIndicator;
    private TextView tvTyping;

    //Views για την οθόνη ιστορικού (History)
    private LinearLayout historyLayout;
    private LinearLayout historyList;
    private LinearLayout historyEmpty;
    private TextView btnHistoryBack;

    // Κατάσταση επιλογών χρήστη
    private String selectedLevel = "Αρχάριος";
    private String selectedGoal  = "Μυϊκή Ανάπτυξη";

    //  Δεδομένα συνομιλίας
    private JSONArray conversationHistory = new JSONArray();
    private String systemPrompt  = "";
    private String currentConvId = ""; // Μοναδικό ID συνομιλίας βάσει χρόνου
    private boolean isLoading    = false; // Ένδειξη αν περιμένουμε απάντηση από το API

    // Animation για την ένδειξη πληκτρολόγησης του AI
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private int typingDotCount = 0;
    private final Runnable typingRunnable = new Runnable() {
        @Override public void run() {
            typingDotCount = (typingDotCount + 1) % 4;
            String dots = ".".repeat(typingDotCount == 0 ? 1 : typingDotCount);
            tvTyping.setText("Σκέφτομαι" + dots);
            typingHandler.postDelayed(this, 400);
        }
    };

    // Χρήση του μοντέλου gemini-2.5-flash για τη δημιουργία περιεχομένου
    private final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
            + BuildConfig.GEMINI_API_KEY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Αρχικοποίηση των views και των λειτουργιών της εφαρμογής
        bindOnboardingViews();
        bindChatViews();
        bindHistoryViews();
        setupChips();
        setupOnboarding();
        setupChat();
        setupHistory();
        loadHistory();
    }

    // Σύνδεση των Views με τον κώδικα (Binding)
    private void bindOnboardingViews() {
        onboardingLayout = findViewById(R.id.onboardingLayout);
        etName           = findViewById(R.id.etName);
        chipBeginner     = findViewById(R.id.chipBeginner);
        chipIntermediate = findViewById(R.id.chipIntermediate);
        chipAdvanced     = findViewById(R.id.chipAdvanced);
        chipMuscle       = findViewById(R.id.chipMuscle);
        chipLoss         = findViewById(R.id.chipLoss);
        chipEndurance    = findViewById(R.id.chipEndurance);
        chipWellness     = findViewById(R.id.chipWellness);
        btnStart         = findViewById(R.id.btnStart);
        btnHistory       = findViewById(R.id.btnHistory);
    }

    private void bindChatViews() {
        chatMainLayout   = findViewById(R.id.chatMainLayout);
        chatLayout       = findViewById(R.id.chatLayout);
        scrollView       = findViewById(R.id.scrollView);
        etMessage        = findViewById(R.id.etMessage);
        btnSend          = findViewById(R.id.btnSend);
        btnBack          = findViewById(R.id.btnBack);
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle);
        tvUserBadge      = findViewById(R.id.tvUserBadge);
        typingIndicator  = findViewById(R.id.typingIndicator);
        tvTyping         = findViewById(R.id.tvTyping);
    }

    private void bindHistoryViews() {
        historyLayout   = findViewById(R.id.historyLayout);
        historyList     = findViewById(R.id.historyList);
        historyEmpty    = findViewById(R.id.historyEmpty);
        btnHistoryBack  = findViewById(R.id.btnHistoryBack);
    }

    // Λειτουργία επιλογής επιπέδου και στόχου (Chips)
    private void setupChips() {
        chipBeginner.setOnClickListener(v    -> selectLevelChip(chipBeginner,     "Αρχάριος"));
        chipIntermediate.setOnClickListener(v-> selectLevelChip(chipIntermediate, "Μεσαίος"));
        chipAdvanced.setOnClickListener(v    -> selectLevelChip(chipAdvanced,     "Προχωρημένος"));
        chipMuscle.setOnClickListener(v      -> selectGoalChip(chipMuscle,    "Μυϊκή Ανάπτυξη"));
        chipLoss.setOnClickListener(v        -> selectGoalChip(chipLoss,      "Απώλεια Βάρους"));
        chipEndurance.setOnClickListener(v   -> selectGoalChip(chipEndurance, "Αντοχή"));
        chipWellness.setOnClickListener(v    -> selectGoalChip(chipWellness,  "Γενική Ευεξία"));
    }

    /**
     * Ενημερώνει την εμφάνιση των chips επιπέδου κατά την επιλογή.
     */
    private void selectLevelChip(TextView selected, String value) {
        selectedLevel = value;
        for (TextView chip : new TextView[]{chipBeginner, chipIntermediate, chipAdvanced}) {
            boolean on = chip == selected;
            chip.setBackgroundResource(on ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(getColor(on ? R.color.text_primary : R.color.text_secondary));
        }
    }

    /**
     * Ενημερώνει την εμφάνιση των chips στόχου κατά την επιλογή.
     */
    private void selectGoalChip(TextView selected, String value) {
        selectedGoal = value;
        for (TextView chip : new TextView[]{chipMuscle, chipLoss, chipEndurance, chipWellness}) {
            boolean on = chip == selected;
            chip.setBackgroundResource(on ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
            chip.setTextColor(getColor(on ? R.color.text_primary : R.color.text_secondary));
        }
    }

    // Λειτουργίες Onboarding
    private void setupOnboarding() {
        btnStart.setOnClickListener(v -> startNewSession());
        btnHistory.setOnClickListener(v -> showHistoryScreen());
    }


    private void startNewSession() {
        String name = etName.getText().toString().trim();
        if (name.isEmpty()) name = "Αθλητή";

        // Κλείσιμο πληκτρολογίου
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(etName.getWindowToken(), 0);

        currentConvId = String.valueOf(System.currentTimeMillis());

        // Καθορισμός της προσωπικότητας του AI Personal Trainer
        systemPrompt = "Είσαι ένας κορυφαίος AI Personal Trainer, Sports Nutritionist και Wellness Coach. "
                + "Το όνομα του χρήστη είναι " + name + ". "
                + "Επίπεδο: " + selectedLevel + ". "
                + "Στόχος: " + selectedGoal + ". "
                + "Μίλα πάντα στα Ελληνικά. Χρησιμοποίησε φιλικό αλλά επαγγελματικό τόνο. "
                + "Δίνε συγκεκριμένα προγράμματα, ασκήσεις και διατροφικές συμβουλές. "
                + "Χρησιμοποίησε emoji για να κάνεις τις απαντήσεις πιο ζωντανές. "
                + "Απάντησε πάντα σύντομα και καθαρά, χωρίς markdown (χωρίς ** ή #).";

        // Εικονίδιο στόχου
        String[] goalEmoji = {"💪", "🔥", "🏃", "🧘"};
        String[] goals     = {"Μυϊκή Ανάπτυξη", "Απώλεια Βάρους", "Αντοχή", "Γενική Ευεξία"};
        String emoji = "💪";
        for (int i = 0; i < goals.length; i++) {
            if (goals[i].equals(selectedGoal)) { emoji = goalEmoji[i]; break; }
        }
        tvUserBadge.setText(emoji + " " + name);
        tvHeaderSubtitle.setText("● Online");

        chatLayout.removeAllViews();
        conversationHistory = new JSONArray();

        // Προσθήκη μηνύματος καλωσορίσματος
        String welcome = "Γεια σου " + name + "! 👋\n\n"
                + "Είμαι ο AI προπονητής σου. Έχω δει το προφίλ σου:\n"
                + "• Επίπεδο: " + selectedLevel + "\n"
                + "• Στόχος: " + selectedGoal + "\n\n"
                + "Είμαι έτοιμος να σε βοηθήσω! Τι θέλεις να ξεκινήσουμε σήμερα; 🚀";
        addBubble(welcome, false);

        onboardingLayout.setVisibility(View.GONE);
        chatMainLayout.setVisibility(View.VISIBLE);
    }

    //  Λειτουργίες Chat
    private void setupChat() {
        btnBack.setOnClickListener(v -> {
            typingHandler.removeCallbacks(typingRunnable);
            chatMainLayout.setVisibility(View.GONE);
            onboardingLayout.setVisibility(View.VISIBLE);
        });

        btnSend.setOnClickListener(v -> {
            if (isLoading) return;
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                etMessage.setText("");
                sendMessage(text);
            }
        });
    }

    /**
     * Στέλνει το μήνυμα του χρήστη στο API του Gemini και λαμβάνει την απάντηση.
     */
    private void sendMessage(String userMessage) {
        addBubble(userMessage, true);
        setLoadingState(true);

        new Thread(() -> {
            try {
                // Προετοιμασία JSON payload για το API
                JSONObject userObj = new JSONObject();
                userObj.put("role", "user");
                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", userMessage);
                parts.put(part);
                userObj.put("parts", parts);
                conversationHistory.put(userObj);

                JSONObject systemInst = new JSONObject();
                JSONObject sysText = new JSONObject();
                sysText.put("text", systemPrompt);
                JSONArray sysParts = new JSONArray();
                sysParts.put(sysText);
                systemInst.put("parts", sysParts);

                JSONObject payload = new JSONObject();
                payload.put("system_instruction", systemInst);
                payload.put("contents", conversationHistory);

                byte[] body = payload.toString().getBytes("utf-8");

                // Εκτέλεση HTTP POST αιτήματος
                HttpURLConnection conn = (HttpURLConnection) new URL(GEMINI_URL).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Accept", "application/json");
                conn.setConnectTimeout(12000);
                conn.setReadTimeout(20000);
                conn.setDoOutput(true);
                conn.setFixedLengthStreamingMode(body.length);

                try (OutputStream os = conn.getOutputStream()) { os.write(body); }

                int code = conn.getResponseCode();
                StringBuilder sb = new StringBuilder();

                if (code == 200 || code == 201) {
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "utf-8"))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line.trim());
                    }

                    JSONObject json = new JSONObject(sb.toString());
                    JSONObject cont = json.getJSONArray("candidates")
                                         .getJSONObject(0).getJSONObject("content");
                    final String aiText = cont.getJSONArray("parts")
                                             .getJSONObject(0).getString("text");

                    // Αποθήκευση της απάντησης του AI στο ιστορικό
                    JSONObject modelObj = new JSONObject();
                    modelObj.put("role", "model");
                    JSONArray mParts = new JSONArray();
                    JSONObject mText = new JSONObject();
                    mText.put("text", aiText);
                    mParts.put(mText);
                    modelObj.put("parts", mParts);
                    conversationHistory.put(modelObj);

                    saveCurrentConversation();

                    runOnUiThread(() -> {
                        addBubble(aiText, false);
                        setLoadingState(false);
                    });

                } else {
                    // Διαχείριση σφαλμάτων API
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getErrorStream(), "utf-8"))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line.trim());
                    }
                    Log.e("FitAI", "API Error " + code + ": " + sb);
                    String errMsg;
                    try {
                        errMsg = new JSONObject(sb.toString()).getJSONObject("error").getString("message");
                    } catch (Exception ignored) {
                        errMsg = sb.toString().isEmpty() ? "Άγνωστο σφάλμα" : sb.toString();
                    }
                    final String err = "❌ Σφάλμα API (" + code + "):\n" + errMsg;
                    runOnUiThread(() -> { addBubble(err, false); setLoadingState(false); });
                }

            } catch (Exception e) {
                Log.e("FitAI", "Network Error", e);
                runOnUiThread(() -> {
                    addBubble("❌ Σφάλμα σύνδεσης. Ελέγξτε το internet σας.", false);
                    setLoadingState(false);
                });
            }
        }).start();
    }

    /**
     * Προσθέτει ένα "συννεφάκι" μηνύματος στο chat layout.
     */
    private void addBubble(String text, boolean isUser) {
        int layoutRes = isUser ? R.layout.item_chat_user : R.layout.item_chat_ai;
        View view = getLayoutInflater().inflate(layoutRes, (ViewGroup) null);
        TextView tv = view.findViewById(R.id.tvMessage);
        tv.setText(text);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = isUser ? Gravity.END : Gravity.START;
        params.topMargin = 16;
        view.setLayoutParams(params);

        chatLayout.addView(view);
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN)); // Αυτόματο scroll προς τα κάτω
    }

    /**
     * Ενημερώνει το UI κατά τη διάρκεια αναμονής απάντησης από το API.
     */
    private void setLoadingState(boolean loading) {
        isLoading = loading;
        runOnUiThread(() -> {
            typingIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
            if (loading) typingHandler.post(typingRunnable);
            else typingHandler.removeCallbacks(typingRunnable);
            btnSend.setAlpha(loading ? 0.5f : 1.0f);
        });
    }

    // Διαχείριση Ιστορικού
    private void setupHistory() {
        btnHistoryBack.setOnClickListener(v -> {
            historyLayout.setVisibility(View.GONE);
            onboardingLayout.setVisibility(View.VISIBLE);
        });
    }

    private void showHistoryScreen() {
        onboardingLayout.setVisibility(View.GONE);
        historyLayout.setVisibility(View.VISIBLE);
        renderHistoryList();
    }

    /**
     * Εμφανίζει τη λίστα με τις αποθηκευμένες συνομιλίες από τα SharedPreferences.
     */
    private void renderHistoryList() {
        historyList.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String historyStr = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray all = new JSONArray(historyStr);
            if (all.length() == 0) {
                historyEmpty.setVisibility(View.VISIBLE);
                return;
            }
            historyEmpty.setVisibility(View.GONE);

            for (int i = all.length() - 1; i >= 0; i--) {
                JSONObject obj = all.getJSONObject(i);
                String id = obj.getString("id");
                String date = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
                        .format(new Date(Long.parseLong(id)));
                
                JSONArray history = obj.getJSONArray("history");
                String lastMsg = "Κενή συνομιλία";
                if (history.length() > 0) {
                    JSONObject last = history.getJSONObject(history.length()-1);
                    lastMsg = last.getJSONArray("parts").getJSONObject(0).getString("text");
                }

                View item = getLayoutInflater().inflate(R.layout.item_history, (ViewGroup) null);
                ((TextView)item.findViewById(R.id.tvHistoryDate)).setText(date);
                ((TextView)item.findViewById(R.id.tvHistorySnippet)).setText(lastMsg);
                
                item.setOnClickListener(v -> loadConversation(obj));
                historyList.addView(item);
            }
        } catch (Exception e) { Log.e("FitAI", "History Render Error", e); }
    }

    /**
     * Φορτώνει μια επιλεγμένη παλιά συνομιλία στην οθόνη του chat.
     */
    private void loadConversation(JSONObject obj) {
        try {
            currentConvId = obj.getString("id");
            conversationHistory = obj.getJSONArray("history");
            systemPrompt = obj.optString("systemPrompt", "");

            chatLayout.removeAllViews();
            for (int i = 0; i < conversationHistory.length(); i++) {
                JSONObject m = conversationHistory.getJSONObject(i);
                String role = m.getString("role");
                String text = m.getJSONArray("parts").getJSONObject(0).getString("text");
                addBubble(text, role.equals("user"));
            }

            historyLayout.setVisibility(View.GONE);
            chatMainLayout.setVisibility(View.VISIBLE);
        } catch (Exception e) { Log.e("FitAI", "Load Conv Error", e); }
    }

    /**
     * Αποθηκεύει την τρέχουσα συνομιλία στα SharedPreferences.
     */
    private void saveCurrentConversation() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            JSONArray all = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            
            int index = -1;
            for (int i = 0; i < all.length(); i++) {
                if (all.getJSONObject(i).getString("id").equals(currentConvId)) {
                    index = i; break;
                }
            }

            JSONObject current = new JSONObject();
            current.put("id", currentConvId);
            current.put("history", conversationHistory);
            current.put("systemPrompt", systemPrompt);

            if (index != -1) all.put(index, current);
            else all.put(current);

            // Διατήρηση μόνο των MAX_SAVED συνομιλιών
            if (all.length() > MAX_SAVED) {
                JSONArray newAll = new JSONArray();
                for (int i = 1; i < all.length(); i++) newAll.put(all.get(i));
                all = newAll;
            }

            prefs.edit().putString(KEY_HISTORY, all.toString()).apply();
        } catch (Exception e) { Log.e("FitAI", "Save Error", e); }
    }

    private void loadHistory() {
        // Η μέθοδος αυτή μπορεί να χρησιμοποιηθεί για μελλοντική προ-φόρτωση
    }
}
