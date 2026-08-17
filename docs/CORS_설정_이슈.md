# CORS 미설정으로 프론트 API 호출 전부 실패

- 작성일: 2026-08-17
- 증상 재현: 프론트(`localhost:5173`)에서 로그인 이후 첫 실제 API 호출(`GET /running-sessions/prepare`, `POST /running-sessions` 등) 시 화면에 "네트워크에 연결할 수 없어요"가 표시됨
- 결론: 백엔드는 정상 동작 중이고 네트워크 연결 문제도 아님. **`SecurityConfig`에 CORS 허용 설정이 없어서 브라우저가 요청 자체를 차단**하는 것.

---

## 1. 증상

프론트에서 `POST /running-sessions` 같은 인증 필요 API를 호출하면 콘솔에는 아무 응답도 안 찍히고, 화면에는 `src/api/client.js`의 `raw()`가 던지는 `ApiError('E_NETWORK', '네트워크에 연결할 수 없어요')`만 뜬다. 즉 `fetch()` 자체가 예외를 던진 상태(`raw()`의 `catch` 블록)이지, 서버가 4xx/5xx JSON 에러를 정상적으로 응답한 게 아니다.

```js
// src/api/client.js
try {
  res = await fetch(BASE + path + qs(query), { method, headers, body })
} catch {
  throw new ApiError('E_NETWORK', '네트워크에 연결할 수 없어요', 0)  // ← 여기로 떨어짐
}
```

## 2. 진단 — 서버는 정상, 브라우저만 막힘

**(1) 서버 자체는 살아있고 정상 응답한다** (`curl`은 CORS 정책을 검사하지 않으므로 항상 성공):

```
$ curl -s https://aftergrow.duckdns.org/actuator/health
{"groups":["liveness","readiness"],"status":"UP"}
```

**(2) 브라우저가 실제로 보내는 것과 동일한 preflight(`OPTIONS`) 요청을 재현하면, CORS 응답 헤더가 하나도 없다:**

```
$ curl -s -i -X OPTIONS "https://aftergrow.duckdns.org/running-sessions/prepare?lat=37.5&lng=127" \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization,content-type"

HTTP/1.1 200
Server: nginx/1.24.0 (Ubuntu)
Allow: GET, HEAD, POST, PUT, DELETE, OPTIONS, TRACE, PATCH
...
```

`Access-Control-Allow-Origin`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers` 헤더가 응답에 **전혀 없다.** `200`이 온 건 Spring이 `OPTIONS`를 기본 처리해준 것일 뿐, CORS 허용과는 무관하다. 이 상태에서 브라우저는 실제 요청(`GET`/`POST` 등)을 보내지 않고 바로 `TypeError: Failed to fetch`를 던진다 — 프론트가 보고 있는 "네트워크에 연결할 수 없어요"의 정체.

## 3. 원인

`docs/API_명세서_AfterGrow_3.md`에 `SecurityConfig`가 언급되는 걸로 봐서 Spring Security를 쓰고 있는데, **Spring Security를 쓰는 프로젝트에서 CORS가 막히는 원인은 거의 항상 둘 중 하나(또는 둘 다)다:**

1. `CorsConfigurationSource` 빈 자체가 없거나, 있어도 허용 오리진 목록에 프론트 도메인이 없음
2. `CorsConfigurationSource`는 있는데, `SecurityFilterChain`에서 `.cors()`를 명시적으로 활성화하지 않아서 Spring Security가 CORS 필터보다 먼저(또는 무관하게) 요청을 처리해버림 — `@CrossOrigin` 애너테이션만 컨트롤러에 붙여놓고 Security 설정은 안 건드리는 실수가 흔함

## 4. 해결 방법

### 4.1 CORS 설정 빈 추가

```java
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
            "http://localhost:5173",      // 로컬 개발 서버
            "https://<배포된-프론트-도메인>" // 실제 배포 도메인으로 교체
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true); // Authorization 헤더를 쓰므로 필요
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

### 4.2 SecurityFilterChain에서 CORS 활성화 (빠뜨리기 쉬운 부분)

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .cors(Customizer.withDefaults())   // ← 이 줄이 없으면 위의 CorsConfigurationSource가 있어도 무시됨
        .csrf(csrf -> csrf.disable())
        // ...기존 인증/인가 설정
    ;
    return http.build();
}
```

> `setAllowedOrigins`에 프론트가 `npm run dev -- --host`로 접속할 LAN IP(폰으로 PWA 설치 테스트할 때 쓰는 `http://192.168.x.x:5173` 형태)도 필요하면 추가해야 함. 와일드카드(`*`)는 `allowCredentials(true)`와 같이 못 쓰므로 명시적 목록으로 관리.

## 5. 수정 후 검증 방법

아래 명령이 `Access-Control-Allow-Origin: http://localhost:5173`을 포함해서 응답하면 해결된 것:

```bash
curl -s -i -X OPTIONS "https://aftergrow.duckdns.org/running-sessions/prepare?lat=37.5&lng=127" \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: GET" \
  -H "Access-Control-Request-Headers: authorization,content-type"
```

프론트 쪽은 이미 배선 완료된 상태(`docs/API_명세서_AfterGrow_3.md` 기준 3장·4장·5장 API)라 CORS만 열리면 바로 실제 러닝 세션 흐름을 붙여서 확인 가능.
