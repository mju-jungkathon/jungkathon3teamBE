package jungkathon3team.aftergrow.profile.dto;

import jungkathon3team.aftergrow.profile.entity.IntegrationStatus;

/**
 * R7 §7.3 GET /users/me/integrations 응답.
 */
public record IntegrationResponse(
        boolean locationLinked,
        boolean cameraPermission,
        boolean locationPermission,
        boolean appleHealthLinked
) {
    public static IntegrationResponse from(IntegrationStatus s) {
        return new IntegrationResponse(
                s.isLocationLinked(),
                s.isCameraPermission(),
                s.isLocationPermission(),
                s.isAppleHealthLinked()
        );
    }
}
