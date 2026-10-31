package org.example;

public class Second_task {
    public static void main(String[] args) {
        int[] a = {101, 203, 305};
        int[] b = {102, 203, 305};

        int count = 0;

        for (int i = 0; i < a.length; i++) {
            for (int j = 0; j < b.length; j++) {
                if (a[i] == b[j]) {
                    count++;
                    break;
                }
            }
        }

        System.out.println(count);
    }
}