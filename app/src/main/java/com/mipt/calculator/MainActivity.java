package com.mipt.calculator;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mipt.calculator.adapter.HistoryAdapter;
import com.mipt.calculator.engine.CalculatorEngine;
import com.mipt.calculator.model.HistoryItem;
import com.mipt.calculator.R;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity implements CalculatorEngine.CalculatorCallback {

    private TextView tvDisplay;
    private TextView tvExpression;
    private TextView tvHistoryEmpty;
    private LinearLayout historyPanel;

    private Button btnMc;
    private Button btnMr;
    private Button btnMPlus;
    private Button btnMMinus;
    private Button btnMs;
    private Button btnMView;

    private HistoryAdapter historyAdapter;
    private CalculatorEngine engine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupHistory();
        setupEngine();
        setupClickListeners();

        if (savedInstanceState != null) {
            CalculatorEngine.State engineState;
            ArrayList<HistoryItem> savedHistory;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                engineState = savedInstanceState.getSerializable("engine_state", CalculatorEngine.State.class);
                savedHistory = savedInstanceState.getSerializable("history_items", (Class<ArrayList<HistoryItem>>) (Class<?>) ArrayList.class);
            } else {
                engineState = (CalculatorEngine.State) savedInstanceState.getSerializable("engine_state");
                savedHistory = (ArrayList<HistoryItem>) savedInstanceState.getSerializable("history_items");
            }
            if (engineState != null) {
                engine.restoreState(engineState);
            }
            if (savedHistory != null) {
                historyAdapter.setItems(savedHistory);
                updateHistoryEmptyState();
            }
            boolean historyVisible = savedInstanceState.getBoolean("history_visible", false);
            toggleHistory(historyVisible);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (historyPanel.getVisibility() == View.VISIBLE) {
                    toggleHistory(false);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void initViews() {
        tvDisplay = findViewById(R.id.tv_display);
        tvExpression = findViewById(R.id.tv_expression);
        tvHistoryEmpty = findViewById(R.id.tv_history_empty);
        historyPanel = findViewById(R.id.history_panel);

        btnMc = findViewById(R.id.btn_mc);
        btnMr = findViewById(R.id.btn_mr);
        btnMPlus = findViewById(R.id.btn_m_plus);
        btnMMinus = findViewById(R.id.btn_m_minus);
        btnMs = findViewById(R.id.btn_ms);
        btnMView = findViewById(R.id.btn_m_view);
    }

    private void setupHistory() {
        RecyclerView recyclerHistory = findViewById(R.id.recycler_history);
        recyclerHistory.setLayoutManager(new LinearLayoutManager(this));

        historyAdapter = new HistoryAdapter(item -> {
            engine.setDisplayValue(item.getResult());
            toggleHistory(false);
        });
        recyclerHistory.setAdapter(historyAdapter);

        ImageButton btnClearHistory = findViewById(R.id.btn_clear_history);
        btnClearHistory.setOnClickListener(v -> {
            historyAdapter.clear();
            updateHistoryEmptyState();
        });

        ImageButton btnHistoryToggle = findViewById(R.id.btn_history_toggle);
        btnHistoryToggle.setOnClickListener(v -> toggleHistory(historyPanel.getVisibility() != View.VISIBLE));

        updateHistoryEmptyState();
    }

    private void toggleHistory(boolean show) {
        historyPanel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void updateHistoryEmptyState() {
        tvHistoryEmpty.setVisibility(historyAdapter.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void setupEngine() {
        String errDivideZero = getString(R.string.error_divide_by_zero);
        String errInvalid = getString(R.string.error_invalid_input);
        engine = new CalculatorEngine(this, errDivideZero, errInvalid);
        onMemoryStateChanged(engine.isMemorySet());
    }

    private void setupClickListeners() {
        // Digits
        findViewById(R.id.btn_0).setOnClickListener(v -> engine.onDigit(0));
        findViewById(R.id.btn_1).setOnClickListener(v -> engine.onDigit(1));
        findViewById(R.id.btn_2).setOnClickListener(v -> engine.onDigit(2));
        findViewById(R.id.btn_3).setOnClickListener(v -> engine.onDigit(3));
        findViewById(R.id.btn_4).setOnClickListener(v -> engine.onDigit(4));
        findViewById(R.id.btn_5).setOnClickListener(v -> engine.onDigit(5));
        findViewById(R.id.btn_6).setOnClickListener(v -> engine.onDigit(6));
        findViewById(R.id.btn_7).setOnClickListener(v -> engine.onDigit(7));
        findViewById(R.id.btn_8).setOnClickListener(v -> engine.onDigit(8));
        findViewById(R.id.btn_9).setOnClickListener(v -> engine.onDigit(9));

        // Decimal and negation
        findViewById(R.id.btn_dot).setOnClickListener(v -> engine.onDot());
        findViewById(R.id.btn_negate).setOnClickListener(v -> engine.onNegate());

        // Arithmetic Operators
        findViewById(R.id.btn_add).setOnClickListener(v -> engine.onOperator("+"));
        findViewById(R.id.btn_subtract).setOnClickListener(v -> engine.onOperator("−"));
        findViewById(R.id.btn_multiply).setOnClickListener(v -> engine.onOperator("×"));
        findViewById(R.id.btn_divide).setOnClickListener(v -> engine.onOperator("÷"));
        findViewById(R.id.btn_equals).setOnClickListener(v -> engine.onEquals());

        // Unary and special operations
        findViewById(R.id.btn_percent).setOnClickListener(v -> engine.onPercent());
        findViewById(R.id.btn_sqr).setOnClickListener(v -> engine.onSquare());
        findViewById(R.id.btn_sqrt).setOnClickListener(v -> engine.onSquareRoot());
        findViewById(R.id.btn_one_over_x).setOnClickListener(v -> engine.onReciprocal());

        // Edit operations
        findViewById(R.id.btn_backspace).setOnClickListener(v -> engine.onBackspace());
        findViewById(R.id.btn_c).setOnClickListener(v -> engine.onClear());
        findViewById(R.id.btn_ce).setOnClickListener(v -> engine.onClearEntry());

        // Memory operations
        btnMc.setOnClickListener(v -> engine.onMemoryClear());
        btnMr.setOnClickListener(v -> engine.onMemoryRecall());
        btnMPlus.setOnClickListener(v -> engine.onMemoryAdd());
        btnMMinus.setOnClickListener(v -> engine.onMemorySubtract());
        btnMs.setOnClickListener(v -> engine.onMemoryStore());
        btnMView.setOnClickListener(v -> {
            if (engine.isMemorySet()) {
                engine.onMemoryRecall();
            }
        });

        // Top bar buttons
        findViewById(R.id.btn_menu).setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.mode_standard), Toast.LENGTH_SHORT).show()
        );
        findViewById(R.id.btn_keep_on_top).setOnClickListener(v ->
                Toast.makeText(this, getString(R.string.content_desc_keep_on_top), Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public void onDisplayUpdated(String displayText, String expressionText) {
        tvDisplay.setText(displayText);
        tvExpression.setText(expressionText);
    }

    @Override
    public void onCalculationFinished(HistoryItem item) {
        historyAdapter.addItem(item);
        updateHistoryEmptyState();
    }

    @Override
    public void onMemoryStateChanged(boolean hasMemory) {
        btnMc.setEnabled(hasMemory);
        btnMr.setEnabled(hasMemory);
        btnMView.setEnabled(hasMemory);
        float alpha = hasMemory ? 1.0f : 0.4f;
        btnMc.setAlpha(alpha);
        btnMr.setAlpha(alpha);
        btnMView.setAlpha(alpha);
    }

    @Override
    public void onErrorState(boolean hasError) {
        // Handled through display text and engine state
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (engine != null) {
            outState.putSerializable("engine_state", engine.saveState());
        }
        if (historyAdapter != null) {
            outState.putSerializable("history_items", historyAdapter.getItems());
        }
        if (historyPanel != null) {
            outState.putBoolean("history_visible", historyPanel.getVisibility() == View.VISIBLE);
        }
    }
}
