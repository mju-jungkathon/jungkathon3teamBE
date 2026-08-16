package jungkathon3team.aftergrow.profile.dto;

public class IntegrationUpdateDto {

    /**
     * PATCH /users/me/integrations 요청. 부분 수정이라 두 필드 모두 nullable.
     * <p>브라우저 권한(GPS·카메라)은 사용자가 앱 밖에서 언제든 바꿀 수 있어서, 여기 저장되는 값은
     * <b>"참고용 캐시"일 뿐 권한 검증 수단이 아니다.</b> 기능 진입 시엔 항상
     * {@code navigator.geolocation}/{@code getUserMedia}를 다시 호출해 그 순간의 성공/실패로 판단하고,
     * 그 결과를 이 API로 동기화해 프로필 화면에 "지금 상태"를 보여주는 용도로만 쓴다.
     * <p>{@code appleHealthLinked}는 여기서 다루지 않는다 — R4.3
     * {@code POST /integrations/apple-health/link}가 담당한다.
     */
    public record Request(
            Boolean cameraPermission,
            Boolean locationPermission
    ) {
    }
}
