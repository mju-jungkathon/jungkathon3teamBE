package jungkathon3team.aftergrow.profile.dto;

import jungkathon3team.aftergrow.profile.entity.IntegrationStatus;

/**
 * R7 §7.4 GET /users/me/integrations 응답.
 *
 * <p><b>{@code locationLinked}는 응답에서 뺐다.</b> 값을 true로 만드는 경로가 어디에도 없어서
 * 항상 false만 내려가는 필드였고, 의미상으로도 {@code locationPermission}(브라우저 위치 권한)과
 * 구분되지 않았다. 컬럼은 남겨 뒀으니 쓰임새가 생기면 그때 되살리면 된다.
 */
public record IntegrationResponse(
        boolean cameraPermission,
        boolean locationPermission,
        boolean appleHealthLinked
) {
    public static IntegrationResponse from(IntegrationStatus s) {
        return new IntegrationResponse(
                s.isCameraPermission(),
                s.isLocationPermission(),
                s.isAppleHealthLinked()
        );
    }
}
