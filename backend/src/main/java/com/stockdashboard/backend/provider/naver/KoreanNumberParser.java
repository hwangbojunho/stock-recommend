package com.stockdashboard.backend.provider.naver;

import java.math.BigDecimal;

/**
 * 네이버 금융 응답에 포함된 한글 숫자 표기("1,885조 4,249억", "26.07배", "12,372원" 등)를 파싱한다.
 */
public final class KoreanNumberParser {

    private static final BigDecimal EOK_PER_JO = BigDecimal.valueOf(10_000);

    private KoreanNumberParser() {
    }

    /**
     * "1,885조 4,249억" 또는 "4,249억" 형태의 문자열을 억원 단위 숫자로 변환한다.
     */
    public static BigDecimal parseEokWon(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String s = text.replace(",", "").trim();
        BigDecimal result = BigDecimal.ZERO;

        if (s.contains("조")) {
            String[] parts = s.split("조", 2);
            result = result.add(toDecimal(parts[0]).multiply(EOK_PER_JO));
            s = parts.length > 1 ? parts[1] : "";
        }
        s = s.replace("억", "").trim();
        if (!s.isEmpty()) {
            result = result.add(toDecimal(s));
        }
        return result;
    }

    /**
     * "26.07배", "12,372원", "-3,500", "299,000" 등에서 숫자 부분만 추출한다.
     * 값이 없거나("-") 파싱할 수 없으면 null을 반환한다.
     */
    public static BigDecimal parseDecimal(String text) {
        if (text == null) {
            return null;
        }
        String s = text.replace(",", "").trim();
        s = s.replaceAll("[^0-9.\\-]", "");
        if (s.isEmpty() || s.equals("-") || s.equals(".")) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static BigDecimal toDecimal(String s) {
        s = s.trim();
        return s.isEmpty() ? BigDecimal.ZERO : new BigDecimal(s);
    }
}
