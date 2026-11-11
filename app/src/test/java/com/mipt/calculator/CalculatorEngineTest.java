package com.mipt.calculator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.mipt.calculator.engine.CalculatorEngine;
import com.mipt.calculator.model.HistoryItem;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CalculatorEngineTest {

    private CalculatorEngine engine;
    private String currentDisplay;
    private String currentExpression;
    private final List<HistoryItem> history = new ArrayList<>();
    private boolean memoryActive = false;
    private boolean errorActive = false;

    @Before
    public void setUp() {
        history.clear();
        engine = new CalculatorEngine(new CalculatorEngine.CalculatorCallback() {
            @Override
            public void onDisplayUpdated(String displayText, String expressionText) {
                currentDisplay = displayText;
                currentExpression = expressionText;
            }

            @Override
            public void onCalculationFinished(HistoryItem item) {
                history.add(item);
            }

            @Override
            public void onMemoryStateChanged(boolean hasMemory) {
                memoryActive = hasMemory;
            }

            @Override
            public void onErrorState(boolean hasError) {
                errorActive = hasError;
            }
        }, "Cannot divide by zero", "Invalid input");
    }

    @Test
    public void testSequentialEvaluation() {
        // 2 + 3 * 4 = 20 (Windows Standard Calculator logic)
        engine.onDigit(2);
        engine.onOperator("+");
        engine.onDigit(3);
        engine.onOperator("×");
        assertEquals("5", currentDisplay);
        engine.onDigit(4);
        engine.onEquals();
        assertEquals("20", currentDisplay);
        assertEquals(1, history.size());
        assertEquals("5 × 4 =", history.get(0).getExpression());
        assertEquals("20", history.get(0).getResult());
    }

    @Test
    public void testSimpleAddition() {
        // 12 + 8 = 20 (from assignment screenshot)
        engine.onDigit(1);
        engine.onDigit(2);
        engine.onOperator("+");
        engine.onDigit(8);
        engine.onEquals();
        assertEquals("20", currentDisplay);
        assertEquals("12 + 8 =", currentExpression);
    }

    @Test
    public void testDivisionByZero() {
        engine.onDigit(5);
        engine.onOperator("÷");
        engine.onDigit(0);
        engine.onEquals();
        assertEquals("Cannot divide by zero", currentDisplay);
        assertTrue(errorActive);
    }

    @Test
    public void testSquareRoot() {
        engine.onDigit(2);
        engine.onDigit(5);
        engine.onSquareRoot();
        assertEquals("5", currentDisplay);
        assertEquals("√(25)", currentExpression);
    }

    @Test
    public void testSquareRootNegative() {
        engine.onDigit(9);
        engine.onNegate();
        engine.onSquareRoot();
        assertEquals("Invalid input", currentDisplay);
        assertTrue(errorActive);
    }

    @Test
    public void testSquare() {
        engine.onDigit(7);
        engine.onSquare();
        assertEquals("49", currentDisplay);
        assertEquals("sqr(7)", currentExpression);
    }

    @Test
    public void testReciprocal() {
        // 1/(8) = 0.125 (from assignment screenshot)
        engine.onDigit(8);
        engine.onReciprocal();
        assertEquals("0.125", currentDisplay);
        assertEquals("1/(8)", currentExpression);
        assertEquals(1, history.size());
    }

    @Test
    public void testMemoryOperations() {
        assertFalse(memoryActive);
        engine.onDigit(5);
        engine.onDigit(0);
        engine.onMemoryStore();
        assertTrue(memoryActive);

        engine.onClear();
        assertEquals("0", currentDisplay);

        engine.onMemoryRecall();
        assertEquals("50", currentDisplay);

        engine.onDigit(1);
        engine.onDigit(0);
        engine.onMemoryAdd();
        assertTrue(memoryActive);

        engine.onClear();
        engine.onMemoryRecall();
        assertEquals("60", currentDisplay);

        engine.onMemoryClear();
        assertFalse(memoryActive);
    }

    @Test
    public void testBackspaceAndClear() {
        engine.onDigit(1);
        engine.onDigit(2);
        engine.onDigit(3);
        assertEquals("123", currentDisplay);

        engine.onBackspace();
        assertEquals("12", currentDisplay);

        engine.onBackspace();
        assertEquals("1", currentDisplay);

        engine.onBackspace();
        assertEquals("0", currentDisplay);
    }

    @Test
    public void testNewCalculationAfterEquals() {
        // Bug 1 verification:
        // 12 + 8 = 20
        engine.onDigit(1);
        engine.onDigit(2);
        engine.onOperator("+");
        engine.onDigit(8);
        engine.onEquals();
        assertEquals("20", currentDisplay);

        // Typing a new digit must start a fresh calculation, not reuse old 20
        engine.onDigit(5);
        assertEquals("5", currentDisplay);
        engine.onOperator("+");
        assertEquals("5 +", currentExpression);
        engine.onDigit(3);
        engine.onEquals();
        assertEquals("8", currentDisplay);
    }

    @Test
    public void testUnaryChainedWithOperator() {
        // Bug 2 verification:
        // 10 + sqrt(9) + 5 = 18
        engine.onDigit(1);
        engine.onDigit(0);
        engine.onOperator("+");
        engine.onDigit(9);
        engine.onSquareRoot();
        assertEquals("3", currentDisplay);
        assertEquals("10 + √(9)", currentExpression);

        // Chaining operator after unary operation must evaluate intermediate result 10 + 3 = 13
        engine.onOperator("+");
        assertEquals("13", currentDisplay);
        assertEquals("13 +", currentExpression);

        engine.onDigit(5);
        engine.onEquals();
        assertEquals("18", currentDisplay);
    }

    @Test
    public void testNegateAfterEquals() {
        // 10 + 5 = 15
        engine.onDigit(1);
        engine.onDigit(0);
        engine.onOperator("+");
        engine.onDigit(5);
        engine.onEquals();
        assertEquals("15", currentDisplay);

        // Negate the result -> -15
        engine.onNegate();
        assertEquals("-15", currentDisplay);

        // Add 5 -> -10
        engine.onOperator("+");
        engine.onDigit(5);
        engine.onEquals();
        assertEquals("-10", currentDisplay);
    }

    @Test
    public void testSquareRootPrecision() {
        engine.onDigit(1);
        engine.onDigit(4);
        engine.onDigit(4);
        engine.onSquareRoot();
        assertEquals("12", currentDisplay);
    }

    @Test
    public void testSaveAndRestoreState() {
        engine.onDigit(4);
        engine.onDigit(2);
        engine.onOperator("+");
        engine.onDigit(8);
        engine.onMemoryStore();

        CalculatorEngine.State saved = engine.saveState();

        // Clear engine and memory
        engine.onClear();
        engine.onMemoryClear();
        assertEquals("0", currentDisplay);
        assertFalse(memoryActive);

        // Restore engine
        engine.restoreState(saved);
        assertEquals("8", currentDisplay);
        assertEquals("42 +", currentExpression);
        assertTrue(memoryActive);
    }
}
