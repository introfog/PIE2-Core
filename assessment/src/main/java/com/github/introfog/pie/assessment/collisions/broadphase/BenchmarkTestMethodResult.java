/*
    Copyright 2020 Dmitry Chubrick

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
 */
package com.github.introfog.pie.assessment.collisions.broadphase;

import java.text.NumberFormat;

import java.util.List;
import java.util.Locale;

public class BenchmarkTestMethodResult {
    private static final String OUTPUT_TABLE_LINE = "+---------------------------+----------------+-------------+-------------+-------------+-------------+\n";

    private final String methodName;
    private final double methodTime;
    private final double expectedDifference;
    private final double bottomDifference;
    private final double topDifference;
    private final double actualDifference;

    public static void outputTestMethodResults(List<BenchmarkTestMethodResult> testResults) {
        System.out.println("\nRESULTS");
        System.out.format(OUTPUT_TABLE_LINE);
        System.out.format("| Method name               | Time           | Expected    | Actual      | Bottom      | Top         |\n");
        System.out.format(OUTPUT_TABLE_LINE);
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.CANADA_FRENCH);
        numberFormat.setMaximumFractionDigits(3);
        for (BenchmarkTestMethodResult result : testResults) {
            System.out.format("| %-25s | %-14s | %-11.3f | %-11.3f | %-11.3f | %-11.3f |",
                    result.methodName, numberFormat.format(result.methodTime), result.expectedDifference,
                    result.actualDifference, result.bottomDifference, result.topDifference);
            System.out.println(result.isPassed() ? "" : " - failed");
        }
        System.out.format(OUTPUT_TABLE_LINE);
    }

    public BenchmarkTestMethodResult(String methodName, double methodTime, double comparativeTime, double expectedDifference,
            double allowedDifferencePercent) {
        this.methodName = methodName;
        this.methodTime = methodTime;
        this.expectedDifference = expectedDifference;

        double expectedTime = comparativeTime * expectedDifference;
        actualDifference = methodTime / comparativeTime;
        // The closer the expected difference is to zero, the more additional percentages of the difference will be
        // resolved. This is done due to the fact that when the expected difference is very small (approximately 0.01),
        // the results can be very different.
        if (expectedDifference < 1.0) {
            allowedDifferencePercent += 0.15 * Math.pow(1 - expectedDifference, 5);
        }
        bottomDifference = expectedTime * (1.0 - allowedDifferencePercent) / comparativeTime;
        topDifference = expectedTime * (1.0 + allowedDifferencePercent) / comparativeTime;
    }

    public boolean isPassed() {
        return (bottomDifference < actualDifference) && (actualDifference < topDifference);
    }
}
