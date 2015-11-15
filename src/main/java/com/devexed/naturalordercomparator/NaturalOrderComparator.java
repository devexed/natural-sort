package com.devexed.naturalordercomparator;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.Collator;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * <p>Comparator for ordering strings in <a href="https://en.wikipedia.org/wiki/Natural_sort_order">natural order</a>.
 * Compares strings case-insensitively and compares any numbers found in the string according to their magnitude, rather
 * than digit by digit. Additionally merges whitespace in text and ignores any whitespace around numbers for even more
 * human friendliness.</p>
 */
public final class NaturalOrderComparator implements Comparator<String> {

    private static Collator createDefaultCollator(Locale locale) {
        // Secondary strength collator which typically compares case-insensitively.
        Collator textCollator = Collator.getInstance(locale);
        textCollator.setStrength(Collator.SECONDARY);
        textCollator.setDecomposition(Collator.NO_DECOMPOSITION);
        return textCollator;
    }

    private static byte[] normalizeBigDecimal(BigDecimal number) {
        long doubleBits = Double.doubleToLongBits(number.doubleValue());

        return new byte[] {
                (byte) (doubleBits >> 56),
                (byte) (doubleBits >> 48),
                (byte) (doubleBits >> 40),
                (byte) (doubleBits >> 32),
                (byte) (doubleBits >> 24),
                (byte) (doubleBits >> 16),
                (byte) (doubleBits >> 8),
                (byte) doubleBits
        };
    }

    // Unicode whitespace and digit matching.
    private static final String digitPatternString = "\\p{Nd}";
    private static final String whitespacePatternString = "[\\u0009-\\u000D\\u0020\\u0085\\u00A0\\u1680\\u180E\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000]+";
    private static final Pattern whitespacePattern = Pattern.compile(whitespacePatternString);
    private static final char whitespaceReplacement = ' ';

    private final Collator textCollator;
    private final Pattern decimalChunkPatten;
    private final DecimalFormat decimalFormat;

    public NaturalOrderComparator() {
        this(Locale.getDefault());
    }

    public NaturalOrderComparator(Locale locale) {
        this(createDefaultCollator(locale), new DecimalFormatSymbols(locale));
    }

    public NaturalOrderComparator(Collator textCollator, DecimalFormatSymbols symbols) {
        this.textCollator = textCollator;

        // Relevant number symbols coerced to non-null strings.
        String minusSign = "" + symbols.getMinusSign();
        String groupingSeparator = "" + symbols.getGroupingSeparator();
        String decimalSeparator = "" + symbols.getDecimalSeparator();

        // Pattern to match numbers in the string.
        StringBuilder patternBuilder = new StringBuilder();
        patternBuilder.append("(.*?)").append(whitespaceReplacement).append("*(");

        // Include negative numbers...
        if (!minusSign.isEmpty()) patternBuilder.append(Pattern.quote(minusSign)).append("?");

        // Include grouped number part (e.g. 1,000,000,000)...
        if (!groupingSeparator.isEmpty())
            patternBuilder.append("(").append(digitPatternString).append("+")
                    .append(Pattern.quote(groupingSeparator)).append(")*");

        // Include integer number part...
        patternBuilder.append(digitPatternString).append("+");

        // Include decimal part...
        if (!decimalSeparator.isEmpty())
            patternBuilder.append("(").append(Pattern.quote(decimalSeparator)).append(digitPatternString).append("+)?");

        patternBuilder.append(")");
        decimalChunkPatten = Pattern.compile(patternBuilder.toString());

        // Format to parse the numbers with three digits per group and a decimal part.
        decimalFormat = new DecimalFormat("#,##0.#", symbols);
        decimalFormat.setParseBigDecimal(true);
    }

    private String trimText(String text) {
        // Collapse whitespace.
        text = whitespacePattern.matcher(text).replaceAll("" + whitespaceReplacement);

        // Trim start whitespace.
        if (!text.isEmpty() && text.charAt(0) == whitespaceReplacement) text = text.substring(1);

        // Trim end whitespace.
        if (!text.isEmpty() && text.charAt(text.length() - 1) == whitespaceReplacement)
            text = text.substring(0, text.length() - 1);

        return text;
    }

    private int compareText(String lhs, String rhs) {
        return textCollator.compare(trimText(lhs), trimText(rhs));
    }

    /**
     * <p>Normalize a string according to this comparators rules. Will create a key from the string that can be used for
     * first pass lookups, restricting the set of values that need to be compared for equality by the {@link #compare}
     * method.</p>
     * <p>If <code>compare(a, b)</code> is <code>0</code> then <code>normalizeForLookup(a)</code> equals
     * <code>normalizeForLookup(b)</code>.
     * <p>Note that unlike {@link Collator#getCollationKey(String)} <strong>the key generated by this method can not be
     * used for ordering of strings</strong>, only for comparing their equality.</p>
     *
     * @param text The text to normalize.
     * @return The key in byte array form.
     */
    public byte[] normalizeForLookup(String text) {
        // Normalize tet and number parts separately and append to byte array.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Matcher m = decimalChunkPatten.matcher(text);
        int e = 0;

        while (m.find()) {
            String t = m.group(1);
            String td = m.group(2);

            byte[] tb = textCollator.getCollationKey(t).toByteArray();
            bytes.write(tb, 0, tb.length);

            try {
                // Parse numbers as big decimal and compare.
                BigDecimal d = (BigDecimal) decimalFormat.parse(td);
                byte[] db = normalizeBigDecimal(d);
                bytes.write(db, 0, db.length);
            } catch (ParseException ex) {
                // Ignorable barring some bug in the Java implementation.
                throw new RuntimeException(ex);
            }

            e = m.end();
        }

        // Add final text segment.
        byte[] tb = textCollator.getCollationKey(text.substring(e)).toByteArray();
        bytes.write(tb, 0, tb.length);

        return bytes.toByteArray();
    }

    @Override
    public int compare(String lhs, String rhs) {
        // Match strings against number pattern and compare the segments in each string one by one.
        Matcher lm = decimalChunkPatten.matcher(lhs);
        Matcher rm = decimalChunkPatten.matcher(rhs);

        // Start indexes of final non-number segment. For comparing the last text part of the strings.
        int le = 0;
        int re = 0;

        while (lm.find() && rm.find()) {
            String lt = lm.group(1);
            String rt = rm.group(1);
            String ltd = lm.group(2);
            String rtd = rm.group(2);

            // Compare prefix text part of match.
            int textCompare = compareText(lt, rt);
            if (textCompare != 0) return textCompare;

            try {
                // Parse numbers as big decimal and compare.
                BigDecimal ld = (BigDecimal) decimalFormat.parse(ltd);
                BigDecimal rd = (BigDecimal) decimalFormat.parse(rtd);

                int numberCompare = ld.compareTo(rd);
                if (numberCompare != 0) return numberCompare;
            } catch (ParseException ex) {
                // Ignorable barring some bug in the Java implementation.
                throw new RuntimeException(ex);
            }

            le = lm.end();
            re = rm.end();
        }

        // Compare final text segment.
        return compareText(lhs.substring(le), rhs.substring(re));
    }

}
