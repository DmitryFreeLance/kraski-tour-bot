package ru.kraskitour.bot.util;

public class PhoneUtil {
    public static String normalize(String input) {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;

        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isDigit(ch)) out.append(ch);
            else if (ch == '+' && out.length() == 0) out.append(ch);
        }
        String normalized = out.toString();
        // уберём + для проверки длины
        String digits = normalized.startsWith("+") ? normalized.substring(1) : normalized;
        if (digits.length() < 6 || digits.length() > 16) return null;
        return normalized;
    }
}