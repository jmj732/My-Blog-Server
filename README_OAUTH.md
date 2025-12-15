# GitHub OAuth 로그인 가이드

## 사전 준비

### 1. PostgreSQL 데이터베이스 시작
```bash
# Docker로 PostgreSQL 실행 (예시)
docker run -d \
  --name demo-postgres \
  -e POSTGRES_PASSWORD=your_secure_password_here \
  -e POSTGRES_DB=demo \
  -p 5432:5432 \
  postgres:17
```

### 2. GitHub OAuth App 생성

1. GitHub 설정으로 이동: https://github.com/settings/developers
2. "New OAuth App" 클릭
3. 다음 정보 입력:
   - **Application name**: demo-api
   - **Homepage URL**: `http://localhost:8080`
   - **Authorization callback URL**: `http://localhost:8080/login/oauth2/code/github`
4. "Register application" 클릭
5. **Client ID**와 **Client Secret** 복사

### 3. 환경변수 설정

`.env` 파일을 열고 GitHub 정보 업데이트:

```bash
GITHUB_CLIENT_ID=your_actual_github_client_id
GITHUB_CLIENT_SECRET=your_actual_github_client_secret
```

## 애플리케이션 실행

### 1. 빌드
```bash
./gradlew clean build
```

### 2. 실행
```bash
./gradlew bootRun
```

또는 IntelliJ에서 `DemoApplication` 메인 클래스 실행

## OAuth 로그인 테스트

### 1. 브라우저에서 로그인 시작

```
http://localhost:8080/oauth2/authorization/github
```

이 URL로 접속하면:
1. GitHub 로그인 페이지로 리다이렉트
2. GitHub 계정으로 로그인
3. 앱 권한 승인
4. 애플리케이션으로 리다이렉트
5. JWT 토큰이 HTTP-Only 쿠키로 설정됨

### 2. 로그인 확인

브라우저 개발자 도구:
- `Application` → `Cookies` → `http://localhost:8080`
- `JWT_TOKEN` 쿠키 확인

### 3. 인증된 API 호출 테스트

로그인 후 쿠키가 자동으로 전송되므로, 인증이 필요한 API를 테스트할 수 있습니다:

```bash
# 댓글 작성 (인증 필요)
curl -X POST http://localhost:8080/api/v1/comments \
  -H "Content-Type: application/json" \
  -b cookies.txt \
  -d '{
    "postId": 123,
    "content": "Test comment"
  }'
```

## 주요 엔드포인트

### OAuth 관련
- **로그인 시작**: `GET /oauth2/authorization/github`
- **OAuth 콜백**: `GET /login/oauth2/code/github` (자동 처리)
- **JWK Set**: `GET /oauth2/jwks` (공개키 확인)

### API 엔드포인트 (기존)
- **게시글 목록**: `GET /api/v1/posts` (인증 불필요)
- **게시글 상세**: `GET /api/v1/posts/{id}` (인증 불필요)
- **댓글 작성**: `POST /api/v1/comments` (인증 필요)
- **댓글 수정**: `PATCH /api/v1/comments/{id}` (인증 필요)
- **게시글 작성**: `POST /api/v1/posts` (ADMIN 권한 필요)

## JWT 토큰 정보

생성된 JWT에는 다음 정보가 포함됩니다:

```json
{
  "iss": "demo-api",
  "sub": "user@example.com",
  "userId": 1234567890,
  "roles": ["USER"],
  "name": "User Name",
  "iat": 1639584000,
  "exp": 1639670400
}
```

## 문제 해결

### 1. "Client ID not found" 오류
- `.env` 파일의 `GITHUB_CLIENT_ID`가 올바른지 확인
- 환경변수가 로드되었는지 확인

### 2. "Redirect URI mismatch" 오류
- GitHub OAuth App 설정에서 Callback URL 확인
- `http://localhost:8080/login/oauth2/code/github`와 정확히 일치해야 함

### 3. 데이터베이스 연결 오류
- PostgreSQL이 실행 중인지 확인
- `.env`의 데이터베이스 설정 확인

### 4. RSA 키 오류
- RSA 키페어 생성:
  ```bash
  ./scripts/generate-rsa-keys.sh
  ```

## 보안 고려사항

### 개발 환경
- HTTP-Only 쿠키 사용 (Secure=false)
- localhost 도메인 사용

### 프로덕션 환경 설정 필요
1. `application-prod.properties` 업데이트:
   ```properties
   jwt.cookie.domain=yourdomain.com
   ```

2. `OAuth2LoginSuccessHandler.java` 수정:
   ```java
   jwtCookie.setSecure(true); // HTTPS 필수
   ```

3. GitHub OAuth App Callback URL 업데이트:
   ```
   https://yourdomain.com/login/oauth2/code/github
   ```

## 추가 기능

### 로그아웃 구현 (선택사항)

```java
@PostMapping("/logout")
public ResponseEntity<?> logout(HttpServletResponse response) {
    Cookie cookie = new Cookie("JWT_TOKEN", null);
    cookie.setHttpOnly(true);
    cookie.setPath("/");
    cookie.setMaxAge(0);
    response.addCookie(cookie);
    return ResponseEntity.ok().body("{\"message\":\"Logged out\"}");
}
```

### 사용자 정보 엔드포인트 (선택사항)

```java
@GetMapping("/api/v1/users/me")
public ResponseEntity<?> getCurrentUser(Authentication authentication) {
    String email = authentication.getName();
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new RuntimeException("User not found"));
    return ResponseEntity.ok(user);
}
```

## Docker/Render 배포 가이드

1. 이미지 빌드/실행 (로컬)
   ```bash
   docker build -t demo-api .
   docker run -p 8080:8080 \
     -e SPRING_PROFILES_ACTIVE=prod \
     -e DB_URL=jdbc:postgresql://host:5432/demo \
     -e DB_USERNAME=postgres \
     -e DB_PASSWORD=*** \
     -e GITHUB_CLIENT_ID=*** \
     -e GITHUB_CLIENT_SECRET=*** \
     -e SYNC_TOKEN=*** \
     demo-api
   ```

2. Render 웹서비스 설정 예시
   - **Start Command**: `java -Dserver.port=$PORT -jar app.jar`
   - **Port**: `$PORT` (Render 기본 제공)
   - **Environment**: Docker
   - **Env Vars**: `SPRING_PROFILES_ACTIVE=prod`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `SYNC_TOKEN`
   - 필요 시 `JAVA_OPTS`에 `-Xmx512m` 등 메모리 옵션 추가
