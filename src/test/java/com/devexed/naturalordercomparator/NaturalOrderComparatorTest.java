package com.devexed.naturalordercomparator;

import junit.framework.TestCase;
import org.junit.Test;

import java.text.Collator;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Locale;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.number.OrderingComparison.comparesEqualTo;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.hamcrest.number.OrderingComparison.lessThan;

public class NaturalOrderComparatorTest extends TestCase {

    private static String formatDouble(Locale locale, double number) {
        DecimalFormat format = new DecimalFormat();
        format.setDecimalFormatSymbols(new DecimalFormatSymbols(locale));
        format.applyPattern("#,#00.#####");
        format.setParseBigDecimal(true);

        /*try {
            System.out.println(locale + ": " + number + ": " + format.parse(format.format(number)));
        } catch (ParseException e) {
            e.printStackTrace();
        }*/

        return format.format(number);
    }

    private static void compareNumbers(Locale locale, double a, double b) {
        NaturalOrderComparator comp = new NaturalOrderComparator(locale);
        int expectedResult = Double.compare(a, b);
        int compareResult = comp.compare(
                "abc " + formatDouble(locale, a),
                "abc " + formatDouble(locale, b));

        if (expectedResult == 0) assertThat(compareResult, comparesEqualTo(0));
        else if (expectedResult < 0) assertThat(compareResult, lessThan(0));
        else if (expectedResult > 0) assertThat(compareResult, greaterThan(0));
    }

    public void testSortsDecimalNumbers() {
        for (Locale locale: Locale.getAvailableLocales()) {
            double a = Math.random();
            double b = Math.random();
            compareNumbers(locale, a, b);
        }
    }

    public void testSortsGroupedNumbers() {
        for (Locale locale: Locale.getAvailableLocales()) {
            double a = Math.random() * 1000000;
            double b = Math.random() * 1000000000;
            compareNumbers(locale, a, b);
        }
    }

    public void testComparesNormalizedKeys() {
        // Only test locales with basic collation for now.
        for (Locale locale: new Locale[] { Locale.ENGLISH, Locale.GERMAN, Locale.FRANCE, Locale.CHINESE, Locale.JAPAN }) {
            double a = Math.random() * 1000000;
            double b = Math.random() * 1000000000;

            NaturalOrderComparator comp = new NaturalOrderComparator(locale);
            byte[] keyA = comp.normalizeForLookup("abc ".toUpperCase(locale) + formatDouble(locale, a) + " hsd");
            byte[] keyB = comp.normalizeForLookup("abc ".toLowerCase(locale) + formatDouble(locale, b) + " hsd");

            assertThat(keyA, is(keyB));
        }
    }

}
