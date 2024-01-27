package com.devexed.naturalsort;

import java.math.BigDecimal;
import java.text.Collator;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Arrays;
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
public final class NaturalOrderComparator<T extends CharSequence> implements Comparator<T> {

    private static Collator createDefaultCollator(Locale locale) {
        // Secondary strength collator which typically compares case-insensitively.
        Collator textCollator = Collator.getInstance(locale);
        textCollator.setStrength(Collator.SECONDARY);
        textCollator.setDecomposition(Collator.NO_DECOMPOSITION);
        return textCollator;
    }

    private static String quoteAndGroup(String regex) {
        return "(?:" + Pattern.quote(regex) + ")";
    }

    private static boolean isWhitespace(char c) {
        return Character.isWhitespace(c) || c == 160;
    }

    // Unicode whitespace and digit matching.
    private static final String digitPatternString = "\\p{Nd}";
    private static final String whitespacePatternString = "[\\s\\u00A0]";

    private final Collator textCollator;
    private final Pattern decimalChunkPatten;
    private final Pattern minusCharPatten;
    private final Pattern groupingCharPatten;
    private final Pattern deicmalCharPatten;

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
        char minusChar = symbols.getMinusSign();
        char groupingChar = symbols.getGroupingSeparator();
        char decimalChar = symbols.getDecimalSeparator();

        // Relax regex to include more types of characters as separators
        String minusSign =
                Character.getType(minusChar) == Character.DASH_PUNCTUATION ||
                minusChar == 0x2212 // math minus
                        ? "\\p{Pd}"
                        : quoteAndGroup("" + minusChar);
        String groupingSeparator =
                isWhitespace(groupingChar)
                        ? whitespacePatternString
                        : quoteAndGroup("" + groupingChar);
        String decimalSeparator = quoteAndGroup("" + decimalChar);

        minusCharPatten = Pattern.compile(minusSign);
        groupingCharPatten = Pattern.compile(groupingSeparator);
        deicmalCharPatten = Pattern.compile(decimalSeparator);

        // Pattern to match numbers in the string.
        String patternBuilder =
                // Match first non-number part
                "(" + whitespacePatternString + "*.*?" + whitespacePatternString + "*)" +

                "(" +
                // Include negative numbers...
                minusSign + "?" +

                // Include grouped whole part (e.g. 1,000,000,000)...
                "(?:" + digitPatternString + "+" + groupingSeparator + ")*" +
                digitPatternString + "+" +

                // Include decimal part...
                "(?:" + decimalSeparator + digitPatternString + "+)?" +

                ")";
        decimalChunkPatten = Pattern.compile(patternBuilder);

        // Format to parse the numbers with three digits per group and a decimal part.
        decimalFormat = new DecimalFormat("0.#", new DecimalFormatSymbols(Locale.ENGLISH));
        decimalFormat.setParseBigDecimal(true);
    }

    public String normalize(T text) {
        StringBuilder normalizedText = new StringBuilder();
        Matcher matcher = decimalChunkPatten.matcher(text);
        int end = 0;

        while (matcher.find()) {
            String textPart = matcher.group(1);
            String numberPart = matcher.group(2);
            numberPart = minusCharPatten.matcher(numberPart).replaceAll("-");
            numberPart = groupingCharPatten.matcher(numberPart).replaceAll("");
            numberPart = deicmalCharPatten.matcher(numberPart).replaceAll(".");
            String numberNormalized;

            try {
                numberNormalized = decimalFormat.parse(numberPart).toString();
            } catch (NumberFormatException | ParseException ex) {
                // Should be ignorable barring some mismatch between DecimalFormat and the number regex.
                // In which case we use the text value instead of the number value.
                numberNormalized = numberPart;
            }

            normalizedText.append(textPart);
            normalizedText.append(numberNormalized);
            end = matcher.end();
        }

        return normalizedText.append(text.subSequence(end, text.length())).toString();
    }

    public int normalizedKey(T text) {
        return Arrays.hashCode(textCollator.getCollationKey(normalize(text)).toByteArray());
    }

    @Override
    public int compare(T lhs, T rhs) {
        // Match strings against number pattern and compare the segments in each string one by one.
        Matcher lhsMatcher = decimalChunkPatten.matcher(lhs);
        Matcher rhsMatcher = decimalChunkPatten.matcher(rhs);

        // Start indexes of final non-number segment. For comparing the last text part of the strings.
        int lhsEnd = 0;
        int rhsEnd = 0;

        while (lhsMatcher.find() && rhsMatcher.find()) {
            String lhsTextPart = lhsMatcher.group(1);
            String rhsTextPart = rhsMatcher.group(1);
            String lhsNumberPart = lhsMatcher.group(2);
            lhsNumberPart = minusCharPatten.matcher(lhsNumberPart).replaceAll("-");
            lhsNumberPart = groupingCharPatten.matcher(lhsNumberPart).replaceAll("");
            lhsNumberPart = deicmalCharPatten.matcher(lhsNumberPart).replaceAll(".");
            String rhsNumberPart = rhsMatcher.group(2);
            rhsNumberPart = minusCharPatten.matcher(rhsNumberPart).replaceAll("-");
            rhsNumberPart = groupingCharPatten.matcher(rhsNumberPart).replaceAll("");
            rhsNumberPart = deicmalCharPatten.matcher(rhsNumberPart).replaceAll(".");

            // Compare prefix text part of match.
            int textCompare = textCollator.compare(lhsTextPart, rhsTextPart);
            if (textCompare != 0) return textCompare;

            try {
                // Parse numbers as big decimal and compare.
                BigDecimal lhsDecimal = (BigDecimal) decimalFormat.parse(lhsNumberPart);
                BigDecimal rhsDecimal = (BigDecimal) decimalFormat.parse(rhsNumberPart);

                int numberCompare = lhsDecimal.compareTo(rhsDecimal);
                if (numberCompare != 0) return numberCompare;
            } catch (NumberFormatException | ParseException ex) {
                // Should be ignorable barring some mismatch between DecimalFormat and the number regex.
                // In which case we compare the numbers as text.
                int numberCompare = textCollator.compare(lhsNumberPart, rhsNumberPart);
                if (numberCompare != 0) return numberCompare;
            }

            lhsEnd = lhsMatcher.end();
            rhsEnd = rhsMatcher.end();
        }

        // Ignore whitespace on last compare
        while (lhsEnd < lhs.length() && isWhitespace(lhs.charAt(lhsEnd))) {
            lhsEnd++;
        }

        while (rhsEnd < rhs.length() && isWhitespace(rhs.charAt(rhsEnd))) {
            rhsEnd++;
        }

        // Compare final segment where one or both of lhs or rhs have no number part.
        return textCollator.compare(lhs.subSequence(lhsEnd, lhs.length()), rhs.subSequence(rhsEnd, rhs.length()));
    }

}
