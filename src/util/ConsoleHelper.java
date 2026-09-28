package util;

import java.util.Scanner;

/**
 * Utility class for formatted console I/O, bordered headers, and validated user inputs.
 */
public class ConsoleHelper {

    public static void printHeader(String title) {
        int width = 75;
        StringBuilder border = new StringBuilder();
        for (int i = 0; i < width; i++) border.append("=");

        System.out.println();
        System.out.println(border);
        int pad = (width - title.length()) / 2;
        StringBuilder padStr = new StringBuilder();
        for (int i = 0; i < Math.max(0, pad); i++) padStr.append(" ");
        System.out.println(padStr + title);
        System.out.println(border);
    }

    public static void printSubHeader(String title) {
        System.out.println("\n--- " + title + " ---");
    }

    public static void printSuccess(String message) {
        System.out.println("\n[CONFIRMATION] >>> SUCCESS: " + message);
    }

    public static void printError(String message) {
        System.out.println("\n[ERROR] >>> " + message);
    }

    public static void printWarning(String message) {
        System.out.println("\n[WARNING] >>> " + message);
    }

    public static void printInfo(String message) {
        System.out.println("[INFO] " + message);
    }

    public static String readString(Scanner scanner, String prompt) {
        System.out.print(prompt + ": ");
        String input = scanner.nextLine();
        return input != null ? input.trim() : "";
    }

    public static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt + ": ");
            String input = scanner.nextLine().trim();
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                printError("Please enter a valid integer.");
            }
        }
    }

    public static double readDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt + ": ");
            String input = scanner.nextLine().trim();
            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                printError("Please enter a valid decimal number.");
            }
        }
    }

    public static void pressEnterToContinue(Scanner scanner) {
        System.out.println("\nPress [ENTER] to return to the menu...");
        scanner.nextLine();
    }
}
