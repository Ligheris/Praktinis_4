package com.mipt.calculator.engine;

import com.mipt.calculator.model.HistoryItem;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Custom "from scratch" calculation engine implementing Windows Standard Calculator behavior.
 */
public class CalculatorEngine {

    public interface CalculatorCallback {
        void onDisplayUpdated(String displayText, String expressionText);
        void onCalculationFinished(HistoryItem item);
        void onMemoryStateChanged(boolean hasMemory);
        void onErrorState(boolean hasError);
    }

    private static final MathContext MATH_CONTEXT = new MathContext(16, RoundingMode.HALF_UP);
    private static final int MAX_INPUT_DIGITS = 16;

    private final CalculatorCallback callback;
    private final String errorDivideByZero;
    private final String errorInvalidInput;

    private String currentInput = "0";
    private String expressionText = "";
    private BigDecimal accumulator = null;
    private String pendingOperator = null;
    private boolean isNewInput = true;
    private boolean hasError = false;
    private boolean isCalculationDone = false;
    private boolean hasPendingOperand = false;

    // For repeated '=' press
    private BigDecimal lastOperand = null;
    private String lastOperator = null;

    // Memory storage
    private BigDecimal memoryValue = BigDecimal.ZERO;
    private boolean isMemorySet = false;

    // To track unary expression inside pending operations (e.g. 12 + 1/(8))
    private String currentUnaryExpression = null;

    public CalculatorEngine(CalculatorCallback callback, String errorDivideByZero, String errorInvalidInput) {
        this.callback = callback;
        this.errorDivideByZero = errorDivideByZero;
        this.errorInvalidInput = errorInvalidInput;
        notifyUpdate();
    }

    public void onDigit(int digit) {
        if (hasError || isCalculationDone) {
            clearAll();
        }

        if (isNewInput) {
            currentInput = String.valueOf(digit);
            isNewInput = false;
        } else {
            if ("0".equals(currentInput)) {
                currentInput = String.valueOf(digit);
            } else if (cleanNumber(currentInput).replace(".", "").replace("-", "").length() < MAX_INPUT_DIGITS) {
                currentInput += digit;
            }
        }
        hasPendingOperand = true;
        currentUnaryExpression = null;
        notifyUpdate();
    }

    public void onDot() {
        if (hasError || isCalculationDone) {
            clearAll();
        }

        if (isNewInput) {
            currentInput = "0.";
            isNewInput = false;
        } else if (!currentInput.contains(".")) {
            currentInput += ".";
        }
        hasPendingOperand = true;
        currentUnaryExpression = null;
        notifyUpdate();
    }

    public void onNegate() {
        if (hasError) return;

        if ("0".equals(currentInput) || "0.".equals(currentInput)) {
            return;
        }

        if (currentInput.startsWith("-")) {
            currentInput = currentInput.substring(1);
        } else {
            currentInput = "-" + currentInput;
        }

        hasPendingOperand = true;
        if (isCalculationDone) {
            isCalculationDone = false;
            accumulator = parseBigDecimal(currentInput);
        }

        if (currentUnaryExpression != null) {
            currentUnaryExpression = "negate(" + currentUnaryExpression + ")";
            updateExpressionForUnary();
        }
        notifyUpdate();
    }

    public void onBackspace() {
        if (hasError || isNewInput) {
            return;
        }

        if (currentInput.length() > 0) {
            currentInput = currentInput.substring(0, currentInput.length() - 1);
            if (currentInput.isEmpty() || "-".equals(currentInput)) {
                currentInput = "0";
                isNewInput = true;
            }
        }
        currentUnaryExpression = null;
        notifyUpdate();
    }

    public void onClear() {
        clearAll();
        notifyUpdate();
    }

    public void onClearEntry() {
        if (hasError) {
            clearAll();
        } else {
            currentInput = "0";
            isNewInput = true;
            currentUnaryExpression = null;
        }
        notifyUpdate();
    }

    public void onOperator(String op) {
        if (hasError) return;

        isCalculationDone = false;
        BigDecimal currentValue = parseBigDecimal(currentInput);

        if (accumulator != null && pendingOperator != null && hasPendingOperand) {
            BigDecimal result = calculate(accumulator, currentValue, pendingOperator);
            if (hasError) {
                notifyUpdate();
                return;
            }
            accumulator = result;
            currentInput = formatNumber(result);
        } else if (accumulator == null || hasPendingOperand) {
            accumulator = currentValue;
        }

        pendingOperator = op;
        isNewInput = true;
        hasPendingOperand = false;
        currentUnaryExpression = null;
        lastOperand = null;
        lastOperator = null;

        expressionText = formatNumber(accumulator) + " " + pendingOperator;
        notifyUpdate();
    }

    public void onEquals() {
        if (hasError) return;

        BigDecimal currentValue = parseBigDecimal(currentInput);

        if (pendingOperator != null) {
            BigDecimal left = accumulator != null ? accumulator : currentValue;
            BigDecimal right = currentValue;
            String op = pendingOperator;

            BigDecimal result = calculate(left, right, op);
            if (hasError) {
                notifyUpdate();
                return;
            }

            String fullExpression;
            if (currentUnaryExpression != null) {
                fullExpression = formatNumber(left) + " " + op + " " + currentUnaryExpression + " =";
            } else {
                fullExpression = formatNumber(left) + " " + op + " " + formatNumber(right) + " =";
            }

            String formattedResult = formatNumber(result);
            expressionText = fullExpression;
            currentInput = formattedResult;
            accumulator = result;
            lastOperand = right;
            lastOperator = op;
            pendingOperator = null;
            isNewInput = true;
            isCalculationDone = true;
            hasPendingOperand = false;
            currentUnaryExpression = null;

            if (callback != null) {
                callback.onCalculationFinished(new HistoryItem(fullExpression, formattedResult));
            }
        } else if (lastOperator != null && lastOperand != null) {
            // Repeated '=' press behavior
            BigDecimal left = currentValue;
            BigDecimal right = lastOperand;
            String op = lastOperator;

            BigDecimal result = calculate(left, right, op);
            if (hasError) {
                notifyUpdate();
                return;
            }

            String fullExpression = formatNumber(left) + " " + op + " " + formatNumber(right) + " =";
            String formattedResult = formatNumber(result);

            expressionText = fullExpression;
            currentInput = formattedResult;
            accumulator = result;
            isNewInput = true;
            isCalculationDone = true;
            hasPendingOperand = false;

            if (callback != null) {
                callback.onCalculationFinished(new HistoryItem(fullExpression, formattedResult));
            }
        } else {
            // Standalone '=' press
            expressionText = formatNumber(currentValue) + " =";
            isNewInput = true;
            isCalculationDone = true;
            hasPendingOperand = false;
        }

        notifyUpdate();
    }

    public void onSquareRoot() {
        if (hasError) return;
        BigDecimal currentValue = parseBigDecimal(currentInput);

        if (currentValue.compareTo(BigDecimal.ZERO) < 0) {
            setError(errorInvalidInput);
            return;
        }

        BigDecimal result = sqrtBigDecimal(currentValue);

        String operandStr = currentUnaryExpression != null ? currentUnaryExpression : formatNumber(currentValue);
        currentUnaryExpression = "√(" + operandStr + ")";

        applyUnaryResult(result, currentUnaryExpression);
    }

    private BigDecimal sqrtBigDecimal(BigDecimal value) {
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        if (value.compareTo(BigDecimal.ONE) == 0) return BigDecimal.ONE;

        BigDecimal x0 = new BigDecimal(Math.sqrt(value.doubleValue()));
        BigDecimal two = new BigDecimal("2");
        for (int i = 0; i < 8; i++) {
            x0 = x0.add(value.divide(x0, MATH_CONTEXT), MATH_CONTEXT).divide(two, MATH_CONTEXT);
        }
        return x0;
    }

    public void onSquare() {
        if (hasError) return;
        BigDecimal currentValue = parseBigDecimal(currentInput);
        BigDecimal result = currentValue.multiply(currentValue, MATH_CONTEXT);

        String operandStr = currentUnaryExpression != null ? currentUnaryExpression : formatNumber(currentValue);
        currentUnaryExpression = "sqr(" + operandStr + ")";

        applyUnaryResult(result, currentUnaryExpression);
    }

    public void onReciprocal() {
        if (hasError) return;
        BigDecimal currentValue = parseBigDecimal(currentInput);

        if (currentValue.compareTo(BigDecimal.ZERO) == 0) {
            setError(errorDivideByZero);
            return;
        }

        BigDecimal result = BigDecimal.ONE.divide(currentValue, MATH_CONTEXT);

        String operandStr = currentUnaryExpression != null ? currentUnaryExpression : formatNumber(currentValue);
        currentUnaryExpression = "1/(" + operandStr + ")";

        applyUnaryResult(result, currentUnaryExpression);
    }

    public void onPercent() {
        if (hasError) return;
        BigDecimal currentValue = parseBigDecimal(currentInput);
        BigDecimal result;

        if (accumulator != null && pendingOperator != null) {
            // If + or -, percent is percentage of accumulator
            // e.g., 200 + 10% => 10% of 200 = 20
            if ("+".equals(pendingOperator) || "−".equals(pendingOperator) || "-".equals(pendingOperator)) {
                result = accumulator.multiply(currentValue, MATH_CONTEXT).divide(new BigDecimal("100"), MATH_CONTEXT);
            } else {
                // If * or /, percent is value / 100
                result = currentValue.divide(new BigDecimal("100"), MATH_CONTEXT);
            }
            currentUnaryExpression = formatNumber(result);
            expressionText = formatNumber(accumulator) + " " + pendingOperator + " " + currentUnaryExpression;
        } else {
            result = BigDecimal.ZERO;
            expressionText = "0";
            currentUnaryExpression = "0";
        }

        currentInput = formatNumber(result);
        isNewInput = true;
        hasPendingOperand = true;
        notifyUpdate();
    }

    // Memory Functions
    public void onMemoryStore() {
        if (hasError) return;
        memoryValue = parseBigDecimal(currentInput);
        isMemorySet = true;
        if (callback != null) {
            callback.onMemoryStateChanged(true);
        }
    }

    public void onMemoryRecall() {
        if (hasError || !isMemorySet) return;
        currentInput = formatNumber(memoryValue);
        isNewInput = true;
        currentUnaryExpression = null;
        notifyUpdate();
    }

    public void onMemoryClear() {
        memoryValue = BigDecimal.ZERO;
        isMemorySet = false;
        if (callback != null) {
            callback.onMemoryStateChanged(false);
        }
    }

    public void onMemoryAdd() {
        if (hasError) return;
        memoryValue = memoryValue.add(parseBigDecimal(currentInput), MATH_CONTEXT);
        isMemorySet = true;
        if (callback != null) {
            callback.onMemoryStateChanged(true);
        }
    }

    public void onMemorySubtract() {
        if (hasError) return;
        memoryValue = memoryValue.subtract(parseBigDecimal(currentInput), MATH_CONTEXT);
        isMemorySet = true;
        if (callback != null) {
            callback.onMemoryStateChanged(true);
        }
    }

    public void setDisplayValue(String value) {
        if (hasError || isCalculationDone) {
            clearAll();
        }
        currentInput = value;
        isNewInput = true;
        hasPendingOperand = true;
        currentUnaryExpression = null;
        notifyUpdate();
    }

    private void applyUnaryResult(BigDecimal result, String unaryExpr) {
        String formattedResult = formatNumber(result);
        currentInput = formattedResult;
        isNewInput = true;
        hasPendingOperand = true;

        if (accumulator != null && pendingOperator != null) {
            expressionText = formatNumber(accumulator) + " " + pendingOperator + " " + unaryExpr;
        } else {
            expressionText = unaryExpr;
            // Record standalone unary calculation into history as seen in PDF screenshot 2 (e.g. 1/(8) = 0.125)
            if (callback != null) {
                callback.onCalculationFinished(new HistoryItem(unaryExpr, formattedResult));
            }
        }
        notifyUpdate();
    }

    private void updateExpressionForUnary() {
        if (accumulator != null && pendingOperator != null) {
            expressionText = formatNumber(accumulator) + " " + pendingOperator + " " + currentUnaryExpression;
        } else {
            expressionText = currentUnaryExpression;
        }
    }

    private BigDecimal calculate(BigDecimal a, BigDecimal b, String op) {
        switch (op) {
            case "+":
                return a.add(b, MATH_CONTEXT);
            case "−":
            case "-":
                return a.subtract(b, MATH_CONTEXT);
            case "×":
            case "*":
                return a.multiply(b, MATH_CONTEXT);
            case "÷":
            case "/":
                if (b.compareTo(BigDecimal.ZERO) == 0) {
                    setError(errorDivideByZero);
                    return BigDecimal.ZERO;
                }
                return a.divide(b, MATH_CONTEXT);
            default:
                return b;
        }
    }

    private void setError(String message) {
        hasError = true;
        currentInput = message;
        expressionText = "";
        accumulator = null;
        pendingOperator = null;
        isNewInput = true;
        currentUnaryExpression = null;
        if (callback != null) {
            callback.onErrorState(true);
        }
        notifyUpdate();
    }

    private void clearAll() {
        currentInput = "0";
        expressionText = "";
        accumulator = null;
        pendingOperator = null;
        lastOperand = null;
        lastOperator = null;
        isNewInput = true;
        hasError = false;
        isCalculationDone = false;
        hasPendingOperand = false;
        currentUnaryExpression = null;
        if (callback != null) {
            callback.onErrorState(false);
        }
    }

    private BigDecimal parseBigDecimal(String str) {
        try {
            return new BigDecimal(cleanNumber(str));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String cleanNumber(String str) {
        if (str == null || str.isEmpty()) return "0";
        String s = str.replace(",", ".");
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s.isEmpty() ? "0" : s;
    }

    private String formatNumber(BigDecimal number) {
        if (number == null || number.compareTo(BigDecimal.ZERO) == 0) return "0";
        BigDecimal stripped = number.stripTrailingZeros();
        // Prevent scientific notation for reasonable length numbers
        return stripped.toPlainString();
    }

    private void notifyUpdate() {
        if (callback != null) {
            callback.onDisplayUpdated(currentInput, expressionText);
        }
    }

    public boolean isMemorySet() {
        return isMemorySet;
    }

    public static class State implements java.io.Serializable {
        public String currentInput;
        public String expressionText;
        public BigDecimal accumulator;
        public String pendingOperator;
        public boolean isNewInput;
        public boolean hasError;
        public boolean isCalculationDone;
        public boolean hasPendingOperand;
        public BigDecimal memoryValue;
        public boolean isMemorySet;
        public String currentUnaryExpression;
        public BigDecimal lastOperand;
        public String lastOperator;
    }

    public State saveState() {
        State s = new State();
        s.currentInput = currentInput;
        s.expressionText = expressionText;
        s.accumulator = accumulator;
        s.pendingOperator = pendingOperator;
        s.isNewInput = isNewInput;
        s.hasError = hasError;
        s.isCalculationDone = isCalculationDone;
        s.hasPendingOperand = hasPendingOperand;
        s.memoryValue = memoryValue;
        s.isMemorySet = isMemorySet;
        s.currentUnaryExpression = currentUnaryExpression;
        s.lastOperand = lastOperand;
        s.lastOperator = lastOperator;
        return s;
    }

    public void restoreState(State s) {
        if (s == null) return;
        currentInput = s.currentInput != null ? s.currentInput : "0";
        expressionText = s.expressionText != null ? s.expressionText : "";
        accumulator = s.accumulator;
        pendingOperator = s.pendingOperator;
        isNewInput = s.isNewInput;
        hasError = s.hasError;
        isCalculationDone = s.isCalculationDone;
        hasPendingOperand = s.hasPendingOperand;
        memoryValue = s.memoryValue != null ? s.memoryValue : BigDecimal.ZERO;
        isMemorySet = s.isMemorySet;
        currentUnaryExpression = s.currentUnaryExpression;
        lastOperand = s.lastOperand;
        lastOperator = s.lastOperator;

        if (callback != null) {
            callback.onMemoryStateChanged(isMemorySet);
            callback.onErrorState(hasError);
            notifyUpdate();
        }
    }
}
