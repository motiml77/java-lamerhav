package examples;

import unit4.collectionsLib.*;

/** דוגמאות מערכים: מקום המקסימום, היפוך במקום, סכומי שורות במערך דו-ממדי, מיון בועות */
public class ArrayExamples {

    /** האינדקס של האיבר הגדול ביותר */
    public static int maxIndex(int[] arr) {
        int max = 0;
        for (int i = 1; i < arr.length; i++) {
            if (arr[i] > arr[max]) max = i;
        }
        return max;
    }

    /** היפוך המערך במקום — שני אינדקסים שמתקרבים זה לזה */
    public static void reverse(int[] arr) {
        int left = 0, right = arr.length - 1;
        while (left < right) {
            int tmp = arr[left];
            arr[left] = arr[right];
            arr[right] = tmp;
            left++;
            right--;
        }
    }

    /** מערך עם הסכום של כל שורה */
    public static int[] rowSums(int[][] mat) {
        int[] sums = new int[mat.length];
        for (int r = 0; r < mat.length; r++) {
            for (int c = 0; c < mat[r].length; c++) {
                sums[r] += mat[r][c];
            }
        }
        return sums;
    }

    /** מיון בועות */
    public static void bubbleSort(int[] a) {
        for (int i = 0; i < a.length - 1; i++) {
            for (int j = 0; j < a.length - 1 - i; j++) {
                if (a[j] > a[j + 1]) {
                    int tmp = a[j];
                    a[j] = a[j + 1];
                    a[j + 1] = tmp;
                }
            }
        }
    }

    // מומלץ להריץ "שורה אחר שורה" — רואים את i, j, left, right זזים על המערך
    public static void main(String[] args) {
        int[] arr = Build.array(7, 3, 9, 1, 5);
        System.out.println("המקסימום במקום " + maxIndex(arr));
        reverse(arr);
        bubbleSort(arr);

        int[][] mat = Build.matrix(new int[][] {
                { 1, 2, 3 },
                { 4, 5, 6 }
        });
        System.out.println("סכומי שורות: " + Viz.text(rowSums(mat)));
    }
}
