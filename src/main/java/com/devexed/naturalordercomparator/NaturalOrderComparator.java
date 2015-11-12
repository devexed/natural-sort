package com.devexed.naturalordercomparator;

import java.math.BigDecimal;
import java.text.Collator;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.Normalizer;
import java.text.ParseException;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * <p>Comparator for ordering strings in
 * <a href="https://en.wikipedia.org/wiki/Natural_sort_order">natural order</a>. Compares strings
 * case-insensitively and compares any numbers found in the string according to their magnitude,
 * rather than digit by digit. Additionally merges whitespace in text and ignores any whitespace
 * around numbers for even more human friendliness.</p>
 */
public final class NaturalOrderComparator implements Comparator<String> {

    private static Collator createDefaultCollator(Locale locale) {
        // Secondary strength collator which typically compares case-insensitively.
        Collator textCollator = Collator.getInstance(locale);
        textCollator.setStrength(Collator.SECONDARY);
        textCollator.setDecomposition(Collator.NO_DECOMPOSITION);
        return textCollator;
    }

    private static final Pattern whitespaceTrimmer = Pattern.compile("\\s+");

    private final Collator textCollator;
    private final Pattern decimalPatten;
    private final Pattern decimalChunkPatten;
    private final DecimalFormat decimalFormat;

    public NaturalOrderComparator() {
        this(Locale.getDefault());
    }

    public NaturalOrderComparator(Locale locale) {
        this(locale, createDefaultCollator(locale));
    }

    public NaturalOrderComparator(Locale locale, Collator textCollator) {
        this.textCollator = textCollator;

        // Relevant number symbols coerced to non-null strings.
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(locale);
        String groupingSeparator = "" + symbols.getGroupingSeparator();
        String decimalSeparator = "" + symbols.getDecimalSeparator();
        String minus = "" + symbols.getMinusSign();

        // Matches digits in unicode.
        String digit = "\\p{Nd}";

        // Pattern to match the numbers.
        StringBuilder patternBuilder = new StringBuilder();
        patternBuilder.append(Pattern.quote(minus)).append("?");

        if (!groupingSeparator.isEmpty()) {
            // Create pattern that includes grouped numbers e.g. 1,000,000,000
            patternBuilder.append("(").append(digit).append("+")
                    .append(Pattern.quote(groupingSeparator))
                    .append(")*");
        }

        patternBuilder.append(digit).append("+");

        // Include decimal part if the separator exists.
        if (!decimalSeparator.isEmpty())
            patternBuilder.append("(").append(Pattern.quote(decimalSeparator)).append(digit).append("+)?");

        String decimalPatternString = patternBuilder.toString();
        decimalPatten = Pattern.compile(decimalPatternString);
        decimalChunkPatten = Pattern.compile("(.*?)\\s*(" + decimalPatternString + ")");

        // Format to parse the numbers.
        decimalFormat = new DecimalFormat("#,#00.#", symbols);
        decimalFormat.setParseBigDecimal(true);
    }

    /**
     * <p>Normalize a string according to this comparators rules. Will create key from the string that can be used for
     * first pass lookups, restricting the set of values that need to be compared for equality by the {@link #compare}
     * method.</p>
     * <p>If <code>compare(a, b) == 0</code> then <code>normalizeForLookup(a).equals(normalizeForLookup(b)) ==
     * true</code></p>
     *
     * @param text The text to normalize.
     * @return The key in byte array form.
     */
    public byte[] normalizeForLookup(String text) {
        // Remove numbers.
        text = decimalPatten.matcher(text).replaceAll("");

        // Strip whitespace.
        text = whitespaceTrimmer.matcher(text).replaceAll(" ").trim();

        // Return the bytes of the collation key.
        return textCollator.getCollationKey(text).toByteArray();
    }

    private int compareText(String lhs, String rhs) {
        return textCollator.compare(lhs, rhs);
    }

    @Override
    public int compare(String lhs, String rhs) {
        // Trim whitespace.
        lhs = whitespaceTrimmer.matcher(lhs).replaceAll(" ").trim();
        rhs = whitespaceTrimmer.matcher(rhs).replaceAll(" ").trim();

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
            } catch (ParseException e) {
                // Should be ignorable, but in case of a parse error (possibly due to a weird
                // locale) defer to comparing as strings.
                int numberAsTextCompare = compareText(ltd, rtd);
                if (numberAsTextCompare != 0) return numberAsTextCompare;
            }

            le = lm.end();
            re = rm.end();
        }

        // Compare final non-number segment.
        return compareText(lhs.substring(le), rhs.substring(re));
    }

}
