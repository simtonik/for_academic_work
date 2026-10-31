package org.example;

public class new_task {
    public static void main(String[] args) {
        int[] a = {5, -1, 0, 2};

        int positive = 0;
        int negative = 0;
        int zero = 0;

        for (int i = 0; i < a.length; i++) {
            if (a[i] > 0) {
                positive++;
            } else if (a[i] < 0) {
                negative++;
            } else {
                zero++;
            }
        }

        System.out.println(positive);
        System.out.println(negative);
        System.out.println(zero);
    }
}