package com.mipt.calculator.model;

import java.io.Serializable;

public class HistoryItem implements Serializable {
    private final String expression;
    private final String result;

    public HistoryItem(String expression, String result) {
        this.expression = expression;
        this.result = result;
    }

    public String getExpression() {
        return expression;
    }

    public String getResult() {
        return result;
    }
}
