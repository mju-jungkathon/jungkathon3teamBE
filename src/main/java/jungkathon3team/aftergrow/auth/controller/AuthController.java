package jungkathon3team.aftergrow.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jungkathon3team.aftergrow.auth.dto.LoginRequest;
import jungkathon3team.aftergrow.auth.dto.LoginResponse;
import jungkathon3team.aftergrow.auth.dto.SignupRequest;
import jungkathon3team.aftergrow.auth.dto.SignupResponse;
import jungkathon3team.aftergrow.auth.service.AuthService;
import jungkathon3team.aftergrow.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "회원가입 / 로그인 / 토큰")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "회원가입", description = "이메일 중복 시 409(E4091)를 반환합니다.")
    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(authService.signup(request));
    }

    @Operation(summary = "로그인",
            description = "이메일 또는 비밀번호가 틀리면 401(E4011)을 반환합니다. 둘을 구분하지 않습니다.")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
