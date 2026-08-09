package jungkathon3team.aftergrow.running.external;

public interface UvIndexClient {

    /**
     * 위경도 기준 현재 UV 지수를 조회한다.
     * 실제 구현은 기상청 API 또는 Open-Meteo UV API 등으로 교체 필요.
     */
    UvIndexResult fetchCurrentUvIndex(double lat, double lng);

    record UvIndexResult(int uvIndex, String uvLevel) {
        public static UvIndexResult of(int uvIndex) {
            return new UvIndexResult(uvIndex, levelOf(uvIndex));
        }

        private static String levelOf(int uvIndex) {
            if (uvIndex <= 2) return "낮음";
            if (uvIndex <= 5) return "보통";
            if (uvIndex <= 7) return "높음";
            if (uvIndex <= 10) return "매우 높음";
            return "위험";
        }
    }
}