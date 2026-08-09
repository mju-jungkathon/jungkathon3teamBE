package jungkathon3team.aftergrow.running.external;

public interface LocationLabelResolver {

    /**
     * 위경도를 "서울 성동구" 같은 표시용 라벨로 변환한다.
     * 실제 구현은 카카오/네이버 역지오코딩 API 등으로 교체 필요.
     */
    String resolve(double lat, double lng);
}
