package ru.invest.api.cb.rf.currency.adapter;


import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class BigDecimalAdapter extends XmlAdapter<String, BigDecimal> {
    private final DecimalFormat decimalFormat;

    public BigDecimalAdapter() {
        final DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.GERMANY);
        symbols.setDecimalSeparator(',');
        symbols.setGroupingSeparator(' ');
        decimalFormat = new DecimalFormat("#,##0.0####", symbols);
        decimalFormat.setParseBigDecimal(true);
    }

    @Override
    public BigDecimal unmarshal(final String v) throws Exception {
        if (v == null || v.trim().isEmpty()) {
            return null;
        }

        final String cleaned = v.trim().replace(" ", "");
        return (BigDecimal) decimalFormat.parse(cleaned);
    }

    @Override
    public String marshal(final BigDecimal v) {
        if (v == null) {
            return null;
        }

        return decimalFormat.format(v);
    }
}
