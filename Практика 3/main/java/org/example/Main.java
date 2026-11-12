package org.example;

import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.function.IntUnaryOperator;

public class Main {
    private static final int[] SIZES = {
            10, 100, 1_000, 2_000, 4_000, 8_000, 16_000, 32_000, 64_000, 128_000
    };
    private static final int WARMUP_ROUNDS = 5;
    private static final int MEASUREMENT_ROUNDS = 5;
    private static final int FAST_BATCH_SIZE = 100_000;
    private static final int LINEAR_WORK_PER_BATCH = 4_000_000;
    private static final int RANDOM_SEED = 42;

    private static volatile long resultSink;

    public static void main(String[] args) {
        Random random = new Random(RANDOM_SEED);
        System.out.println("task,algorithm,scenario,n,calls_per_sample,"
                + "sample_1_ns,sample_2_ns,sample_3_ns,sample_4_ns,sample_5_ns,mean_ns");

        for (int n : SIZES) {
            int[] array = prepare(n);
            int linearBatchSize = Math.min(FAST_BATCH_SIZE, LINEAR_WORK_PER_BATCH / n);
            int[] randomIndices = random.ints(FAST_BATCH_SIZE, 0, n).toArray();
            int[] randomTargets = new int[FAST_BATCH_SIZE];
            int[] absentTargets = new int[FAST_BATCH_SIZE];
            for (int i = 0; i < FAST_BATCH_SIZE; i++) {
                randomTargets[i] = array[randomIndices[i]];
                absentTargets[i] = randomTargets[i] - 1;
            }

            measure(1, "array_access", "random_index", n, randomIndices, index -> array[index]);

            int[] firstTargets = repeatedInput(array[0], FAST_BATCH_SIZE);
            measure(2, "binary_search", "first", n, firstTargets, target -> binarySearch(array, target));
            measure(2, "binary_search", "random", n, randomTargets, target -> binarySearch(array, target));
            measure(2, "binary_search", "absent", n, absentTargets, target -> binarySearch(array, target));

            int[] linearRandomTargets = Arrays.copyOf(randomTargets, linearBatchSize);
            int[] linearAbsentTargets = Arrays.copyOf(absentTargets, linearBatchSize);
            measure(3, "linear_search", "first", n, firstTargets, target -> linearSearch(array, target));
            measure(3, "linear_search", "random", n, linearRandomTargets, target -> linearSearch(array, target));
            measure(3, "linear_search", "absent", n, linearAbsentTargets, target -> linearSearch(array, target));

            measure(4, "count_greater", "all_match", n, repeatedInput(0, linearBatchSize),
                    target -> countGreater(array, target));
            measure(4, "count_greater", "random_matches", n, linearRandomTargets,
                    target -> countGreater(array, target));
            measure(4, "count_greater", "none_match", n, repeatedInput(array[n - 1], linearBatchSize),
                    target -> countGreater(array, target));

            // Swap adjacent elements to introduce exactly one break in ascending order.
            int[] earlyBreak = withInversion(array, 0);
            int[] middleBreak = withInversion(array, (n - 1) / 2);
            measure(5, "check_sorted", "best", n, repeatedInput(0, FAST_BATCH_SIZE),
                    ignored -> isSorted(earlyBreak) ? 1 : 0);
            int[] sortednessInputs = repeatedInput(0, linearBatchSize);
            measure(5, "check_sorted", "average", n, sortednessInputs,
                    ignored -> isSorted(middleBreak) ? 1 : 0);
            measure(5, "check_sorted", "worst", n, sortednessInputs,
                    ignored -> isSorted(array) ? 1 : 0);
        }
    }

    static int[] prepare(int n) {
        int[] array = new int[n];
        for (int i = 0; i < n; i++) {
            array[i] = (i + 1) * 2;
        }
        return array;
    }

    static int binarySearch(int[] array, int target) {
        int left = 0;
        int right = array.length - 1;
        while (left <= right) {
            int middle = left + (right - left) / 2;
            int value = array[middle];
            if (value == target) {
                return middle;
            }
            if (value < target) {
                left = middle + 1;
            } else {
                right = middle - 1;
            }
        }
        return -1;
    }

    static int linearSearch(int[] array, int target) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == target) {
                return i;
            }
        }
        return -1;
    }

    static int countGreater(int[] array, int target) {
        int count = 0;
        for (int value : array) {
            if (value > target) {
                count++;
            }
        }
        return count;
    }

    static boolean isSorted(int[] array) {
        for (int i = 1; i < array.length; i++) {
            if (array[i - 1] > array[i]) {
                return false;
            }
        }
        return true;
    }

    private static int[] withInversion(int[] array, int index) {
        int[] copy = array.clone();
        copy[index] = array[index + 1];
        copy[index + 1] = array[index];
        return copy;
    }

    private static int[] repeatedInput(int value, int count) {
        int[] inputs = new int[count];
        Arrays.fill(inputs, value);
        return inputs;
    }

    private static void measure(int task, String algorithm, String scenario, int n,
                                int[] inputs, IntUnaryOperator operation) {
        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            timeBatch(inputs, operation);
        }

        // Each sample is the elapsed batch time divided by the number of calls.
        double[] samples = new double[MEASUREMENT_ROUNDS];
        double total = 0;
        for (int round = 0; round < MEASUREMENT_ROUNDS; round++) {
            samples[round] = (double) timeBatch(inputs, operation) / inputs.length;
            total += samples[round];
        }

        System.out.printf(Locale.ROOT, "%d,%s,%s,%d,%d", task, algorithm, scenario, n, inputs.length);
        for (double sample : samples) {
            System.out.printf(Locale.ROOT, ",%.3f", sample);
        }
        System.out.printf(Locale.ROOT, ",%.3f%n", total / MEASUREMENT_ROUNDS);
    }

    private static long timeBatch(int[] inputs, IntUnaryOperator operation) {
        long checksum = 0;
        long start = System.nanoTime();
        for (int input : inputs) {
            checksum += operation.applyAsInt(input);
        }
        long elapsed = System.nanoTime() - start;
        // Make the computed results observable outside the timed interval.
        resultSink = checksum;
        return elapsed;
    }
}
