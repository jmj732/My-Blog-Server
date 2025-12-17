# API 명세서

## 개요

이 문서는 게시판 서비스의 REST API 명세를 정의합니다.

**Base URL**: `/api/v1`

**인증 방식**: OAuth2 JWT (Bearer Token)

**응답 형식**: 모든 응답은 다음 형태로 반환됩니다.

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

## 인증 (Authentication)

### GitHub OAuth 로그인

이 API는 GitHub OAuth 2.0을 사용하여 사용자 인증을 처리합니다.

**로그인 플로우**:

1. **로그인 시작**: 브라우저에서 `/oauth2/authorization/github`로 접속
2. **GitHub 인증**: GitHub 로그인 페이지로 리다이렉트되어 인증
3. **권한 승인**: 애플리케이션 권한 승인
4. **JWT 발급**: 로그인 성공 시 자체 JWT 토큰이 HTTP-Only 쿠키로 설정됨
5. **프론트엔드 리다이렉트**: 백엔드가 콜백 URL(`FRONTEND_CALLBACK_URL`)로 자동 리다이렉트하며 `?token={JWT}` 쿼리 파라미터를 포함해 전달
6. **프론트엔드 처리**: 프론트의 `/api/auth/callback` 등 콜백 페이지에서 `token` 쿼리를 읽어 클라이언트 쿠키/스토리지에 저장 가능 (서버가 이미 HttpOnly 쿠키도 설정)
7. **API 호출**: 이후 모든 API 요청에서 쿠키가 자동으로 전송되어 인증 처리

### 인증 방식

#### 1. 쿠키 인증 (권장)

GitHub OAuth 로그인 후 자동으로 설정되는 `JWT_TOKEN` 쿠키를 사용합니다. 브라우저에서 API를 호출할 때 쿠키가 자동으로 전송됩니다.

- **쿠키 이름**: `JWT_TOKEN`
- **속성**:
  - `HttpOnly`: JavaScript 접근 차단 (보안)
  - `Secure`: HTTPS에서만 전송 (프로덕션)
  - `SameSite=None`: Cross-origin 요청 허용
  - `MaxAge=86400`: 24시간
- **전송**: 브라우저가 자동으로 처리 (단, `credentials: 'include'` 필요)

**⚠️ Cross-Origin 요청 시 주의사항**:

프론트엔드와 백엔드가 다른 도메인에 있는 경우 (예: `vercel.app` ↔ `onrender.com`), API 요청 시 반드시 `credentials: 'include'` 옵션을 사용해야 합니다:

```javascript
fetch('https://gc-board-latest-1.onrender.com/api/v1/posts', {
  credentials: 'include'  // 쿠키 포함 필수
})
```

#### 2. Bearer Token (선택)

프로그래밍 방식으로 API를 호출할 때는 Authorization 헤더를 사용할 수 있습니다:

```
Authorization: Bearer {JWT_TOKEN}
```

### JWT 토큰 정보

JWT 토큰에서 추출되는 정보:
- `iss`: 발급자 ("demo-api")
- `sub`: 사용자 이메일 (String)
- `userId`: 사용자 ID (Long)
- `roles`: 사용자 권한 목록 (예: `["USER"]`, `["ADMIN"]`)
- `scope`: 관리자 토큰에서 `["sync"]` 포함 (동기화 API용)
- `name`: 사용자 이름
- `iat`: 발급 시각 (Unix timestamp)
- `exp`: 만료 시각 (Unix timestamp, 발급 후 24시간)

**주의**: 서버 로직은 `userId` 클레임을 사용해 사용자 식별을 수행합니다. `sub`는 이메일이며 숫자 파싱을 시도하지 않습니다.

## 에러 응답

에러 발생 시 응답 형식:

```json
{
  "success": false,
  "data": null,
  "error": "에러 메시지"
}
```

---

## 0. 인증 API

### 0.1 GitHub OAuth 로그인 시작

**GET** `/oauth2/authorization/github`

GitHub OAuth 로그인 플로우를 시작합니다.

**요청**

브라우저에서 이 URL로 접속하면 자동으로 GitHub 로그인 페이지로 리다이렉트됩니다.

**응답**

GitHub 로그인 성공 후:
- HTTP 상태 코드: 302 Found (리다이렉트)
- `JWT_TOKEN` 쿠키 자동 설정 (HttpOnly)
- **리다이렉트 위치**: 환경 변수 `FRONTEND_CALLBACK_URL`에 설정된 콜백 URL + `token` 쿼리 파라미터
  - 예: `https://my-blog-tau-gilt.vercel.app/api/auth/callback?token=eyJhbGciOi...`
  - 프론트 콜백 페이지는 `token` 쿼리를 읽어 자체 저장/쿠키 설정 가능

**쿠키 상세**:
```
Set-Cookie: JWT_TOKEN={token}; Path=/; Max-Age=86400; HttpOnly; Secure; SameSite=None
```

**OAuth 콜백 URL**: `/login/oauth2/code/github` (Spring Security가 자동 처리)

**환경 설정**:
- `FRONTEND_URL`: CORS 허용을 위한 프론트엔드 오리진 (예: `https://my-blog-tau-gilt.vercel.app`)
- `FRONTEND_CALLBACK_URL`: 로그인 성공 후 리다이렉트할 콜백 URL (예: `https://my-blog-tau-gilt.vercel.app/api/auth/callback`)
- `FRONTEND_CALLBACK_URL`이 쿼리를 포함할 수 있으므로, 서버는 자동으로 `?token={JWT}`를 붙여 전달함
- `JWT_COOKIE_SECURE`: HTTPS 사용 여부 (프로덕션: true, SameSite=None과 함께 설정)
- `JWT_COOKIE_DOMAIN`: 쿠키 도메인 (cross-origin인 경우 빈 문자열 권장)
- `JWT_COOKIE_SAME_SITE`: `none`(기본), `lax`, `strict` 중 선택

---

### 0.2 JWK Set 조회 (공개키)

**GET** `/oauth2/jwks`

JWT 토큰 검증에 사용되는 공개키 세트를 조회합니다.

**인증 불필요**

**응답**

```json
{
  "keys": [
    {
      "kty": "RSA",
      "e": "AQAB",
      "n": "public_key_modulus...",
      "alg": "RS256",
      "use": "sig"
    }
  ]
}
```

**용도**: 외부 시스템에서 JWT 토큰을 독립적으로 검증할 때 사용

---

### 0.3 내 정보 조회 (백엔드 검증)

**GET** `/api/v1/auth/me`

검증된 JWT를 사용해 현재 로그인한 사용자의 정보를 반환합니다.

**인증 필요** (쿠키 또는 Bearer)

**요청 헤더**
- `Cookie: JWT_TOKEN=...` (브라우저: `credentials: 'include'` 필요)
  또는
- `Authorization: Bearer {JWT_TOKEN}`

**응답**

```json
{
  "success": true,
  "data": {
    "id": 1,
    "email": "user@example.com",
    "name": "홍길동",
    "role": "USER",
    "image": "https://example.com/avatar.png"
  },
  "error": null
}
```

**설명**
- 토큰 서명·만료 검증은 백엔드(Spring Security 리소스 서버)에서 수행합니다.
- `userId` 클레임이 있으면 우선 사용하고, 없으면 `sub`(이메일)로 조회합니다.
- 사용자를 찾지 못하면 404를 반환합니다.

---

### 0.4 로그아웃

**POST** `/api/v1/auth/logout`

HttpOnly 쿠키를 삭제하여 로그아웃 처리합니다.

**인증 불필요** (쿠키만 있으면 삭제 가능)

**요청**

요청 본문 없음

**응답**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

**동작 방식**

서버가 `Set-Cookie` 헤더로 쿠키 만료 명령을 내려 브라우저가 자동으로 쿠키를 삭제합니다:

```
Set-Cookie: JWT_TOKEN=; Path=/; Max-Age=0; HttpOnly; Secure
```

**프론트엔드 예시**:
```javascript
// fetch 사용
fetch('https://gc-board-latest-1.onrender.com/api/v1/auth/logout', {
  method: 'POST',
  credentials: 'include'  // 쿠키 포함
})

// axios 사용
axios.post('https://gc-board-latest-1.onrender.com/api/v1/auth/logout', {}, {
  withCredentials: true  // 쿠키 포함
})
```

---

## 1. 게시글 API

### 1.1 피드 조회 (Feed)

**GET** `/posts/feed`

게시글 목록을 커서 기반 페이지네이션으로 조회합니다.

**GET** `/posts/cursor`

`/posts/feed`와 동일한 응답 형식이며, `type` 파라미터로 `admin`/`community` 필터를 지원합니다.
(`admin`: `author_id` 없음, `community`: `author_id` 있음)

**요청 파라미터**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| limit | int | N | 20 | 한 번에 조회할 게시글 수 |
| cursorCreatedAt | string | N | - | 커서: 생성일시 (ISO 8601 형식) |
| cursorId | long | N | - | 커서: 게시글 ID |
| type | string | N | - | (`/posts/cursor` 전용) 게시글 타입: `admin` 또는 `community` |

**제약사항**

- `limit`은 최대 100까지 허용됩니다.

**응답**

```json
{
  "success": true,
  "data": {
    "rows": [
      {
        "id": 123456789,
        "slug": "example-post",
        "title": "게시글 제목",
        "createdAt": "2025-12-14T10:30:00+09:00"
      }
    ],
    "nextCursor": {
      "createdAt": "2025-12-14T09:00:00+09:00",
      "id": 123456780
    }
  },
  "error": null
}
```

**응답 필드**

- `rows`: 게시글 목록
  - `id`: 게시글 ID (Snowflake ID)
  - `slug`: URL 경로용 슬러그
  - `title`: 제목
  - `createdAt`: 생성일시
- `nextCursor`: 다음 페이지 조회를 위한 커서
  - `createdAt`: 다음 조회 시작점의 생성일시
  - `id`: 다음 조회 시작점의 ID

---

### 1.2 게시글 목록 조회 (List)

**GET** `/posts`

게시글 목록을 페이지 번호 기반 페이지네이션으로 조회합니다.

**요청 파라미터**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| page | int | N | 0 | 페이지 번호 (0부터 시작) |
| pageSize | int | N | 10 | 페이지당 게시글 수 |
| type | string | N | - | 게시글 타입 필터 |

**제약사항**

- `pageSize`는 최대 100까지 허용됩니다.
- `type` 값은 `admin`(관리자/시스템 작성) 또는 `community`(일반 사용자 작성)만 지원하며, 미지정 시 전체를 조회합니다.

**응답**

```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 123456789,
        "slug": "example-post",
        "title": "게시글 제목",
        "content": "게시글 내용",
        "authorName": "작성자명",
        "authorRole": "ADMIN",
        "createdAt": "2025-12-14T10:30:00+09:00",
        "version": 1
      }
    ],
    "pageable": {},
    "totalPages": 10,
    "totalElements": 95,
    "last": false,
    "number": 0,
    "size": 10,
    "numberOfElements": 10,
    "first": true,
    "empty": false
  },
  "error": null
}
```

**응답 필드** (Spring Data의 Page 객체)

- `content`: 게시글 목록
  - `id`: 게시글 ID
  - `slug`: URL 슬러그
  - `title`: 제목
  - `content`: 내용
  - `authorName`: 작성자 이름
  - `authorRole`: 작성자 역할
  - `createdAt`: 생성일시
  - `version`: 버전 (낙관적 잠금용)
- `totalPages`: 전체 페이지 수
- `totalElements`: 전체 게시글 수
- `number`: 현재 페이지 번호
- `first`: 첫 페이지 여부
- `last`: 마지막 페이지 여부

---

### 1.3 게시글 상세 조회

**GET** `/posts/{slug}`

특정 게시글의 상세 정보를 조회합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| slug | string | Y | 게시글 슬러그 (URL 인코딩 필요) |

**응답**

```json
{
  "success": true,
  "data": {
    "id": 123456789,
    "slug": "example-post",
    "title": "게시글 제목",
    "content": "게시글 내용",
    "authorName": "작성자명",
    "authorRole": "ADMIN",
    "createdAt": "2025-12-14T10:30:00+09:00",
    "version": 1
  },
  "error": null
}
```

---

### 1.4 관리자 게시글 생성

**POST** `/posts`

관리자 권한으로 게시글을 생성합니다.

**요청 본문**

```json
{
  "title": "게시글 제목",
  "content": "게시글 내용"
}
```

**요청 필드**

| 필드 | 타입 | 필수 | 제약사항 |
|------|------|------|----------|
| title | string | Y | 1~255자 |
| content | string | Y | 1~50000자 |

**응답**

```json
{
  "success": true,
  "data": {
    "slug": "generated-slug"
  },
  "error": null
}
```

---

### 1.5 관리자 게시글 수정

**PATCH** `/posts/{slug}`

관리자 권한으로 게시글을 수정합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| slug | string | Y | 게시글 슬러그 |

**요청 본문**

```json
{
  "title": "수정된 제목",
  "content": "수정된 내용"
}
```

**요청 필드**

| 필드 | 타입 | 필수 | 제약사항 |
|------|------|------|----------|
| title | string | Y | 1~255자 |
| content | string | Y | 1~50000자 |

**응답**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

---

### 1.6 관리자 게시글 삭제

**DELETE** `/posts/{slug}`

관리자 권한으로 게시글을 삭제합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| slug | string | Y | 게시글 슬러그 |

**응답**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

---

### 1.7 커뮤니티 게시글 생성

**POST** `/community/posts`

**인증 필요**: JWT 토큰

일반 사용자가 커뮤니티 게시글을 생성합니다.

**요청 본문**

```json
{
  "title": "게시글 제목",
  "content": "게시글 내용"
}
```

**요청 필드**

| 필드 | 타입 | 필수 | 제약사항 |
|------|------|------|----------|
| title | string | Y | 1~255자 |
| content | string | Y | 1~50000자 |

**응답**

```json
{
  "success": true,
  "data": {
    "slug": "generated-slug"
  },
  "error": null
}
```

---

### 1.8 커뮤니티 게시글 수정

**PATCH** `/community/posts/{slug}`

**인증 필요**: JWT 토큰

작성자(또는 ADMIN)가 커뮤니티 게시글을 수정합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| slug | string | Y | 게시글 슬러그 |

**요청 본문**

```json
{
  "title": "게시글 제목",
  "content": "게시글 내용"
}
```

**응답**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

---

### 1.9 커뮤니티 게시글 삭제

**DELETE** `/community/posts/{slug}`

**인증 필요**: JWT 토큰

작성자(또는 ADMIN)가 커뮤니티 게시글을 삭제합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| slug | string | Y | 게시글 슬러그 |

**응답**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

---

## 2. 댓글 API

### 2.1 댓글 목록 조회

**GET** `/comments`

특정 게시글의 댓글 목록을 조회합니다.

**요청 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| postId | long | Y | 게시글 ID |

**응답**

```json
{
  "success": true,
  "data": [
    {
      "id": 123456789,
      "postId": 987654321,
      "userId": 111222333,
      "userName": "사용자명",
      "deleted": false,
      "content": "댓글 내용",
      "createdAt": "2025-12-14T10:30:00+09:00",
      "updatedAt": "2025-12-14T10:30:00+09:00",
      "parentId": null
    }
  ],
  "error": null
}
```

**응답 필드**

- `id`: 댓글 ID
- `postId`: 게시글 ID
- `userId`: 작성자 ID
- `userName`: 작성자 이름
- `deleted`: 삭제 여부
- `content`: 댓글 내용
- `createdAt`: 생성일시
- `updatedAt`: 수정일시
- `parentId`: 부모 댓글 ID (대댓글인 경우)

---

### 2.2 댓글 생성

**POST** `/comments`

**인증 필요**: JWT 토큰

댓글을 생성합니다.

**요청 본문**

```json
{
  "postId": 987654321,
  "parentId": null,
  "content": "댓글 내용"
}
```

**요청 필드**

| 필드 | 타입 | 필수 | 제약사항 |
|------|------|------|----------|
| postId | long | Y | - |
| parentId | long | N | 대댓글인 경우 부모 댓글 ID |
| content | string | Y | 1~1000자 |

**응답**

```json
{
  "success": true,
  "data": {
    "id": 123456789,
    "postId": 987654321,
    "userId": 111222333,
    "userName": "사용자명",
    "deleted": false,
    "content": "댓글 내용",
    "createdAt": "2025-12-14T10:30:00+09:00",
    "updatedAt": "2025-12-14T10:30:00+09:00",
    "parentId": null
  },
  "error": null
}
```

---

### 2.3 댓글 수정

**PATCH** `/comments/{id}`

**인증 필요**: JWT 토큰

댓글을 수정합니다. 본인 또는 관리자만 수정 가능합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | long | Y | 댓글 ID |

**요청 본문**

```json
{
  "content": "수정된 댓글 내용"
}
```

**요청 필드**

| 필드 | 타입 | 필수 | 제약사항 |
|------|------|------|----------|
| content | string | Y | 1~1000자 |

**응답**

```json
{
  "success": true,
  "data": {
    "id": 123456789,
    "postId": 987654321,
    "userId": 111222333,
    "userName": "사용자명",
    "deleted": false,
    "content": "수정된 댓글 내용",
    "createdAt": "2025-12-14T10:30:00+09:00",
    "updatedAt": "2025-12-14T11:00:00+09:00",
    "parentId": null
  },
  "error": null
}
```

---

### 2.4 댓글 삭제

**DELETE** `/comments/{id}`

**인증 필요**: JWT 토큰

댓글을 소프트 삭제합니다. 본인 또는 관리자만 삭제 가능합니다.

**경로 파라미터**

| 파라미터 | 타입 | 필수 | 설명 |
|----------|------|------|------|
| id | long | Y | 댓글 ID |

**응답**

```json
{
  "success": true,
  "data": null,
  "error": null
}
```

---

## 3. 검색 API

### 3.1 게시글 검색

**GET** `/search` / **POST** `/search`

키워드로 게시글을 검색합니다. 클라이언트 또는 동기화 요청에서 공급된 임베딩을 벡터 유사도 기준으로 우선 검색하며, 임베딩이 없을 경우 기존 제목/내용 기반의 렉시컬 검색(lexical)으로 자동으로 대체합니다. 최대 50건까지 반환합니다.

POST 요청을 쓰면 body에 임베딩이나 더 긴 키워드를 담을 수 있으므로 선호되며, GET은 기존처럼 쿼리파라미터만 사용할 때 유지됩니다.

**요청 (GET query params or POST JSON)**

| 파라미터 | 타입 | 필수 | 기본값 | 설명 |
|----------|------|------|--------|------|
| q | string | Y | - | 검색 키워드 |
| limit | int | N | 10 | 최대 결과 수 |

**POST Body 예시**

```json
{
  "q": "spring tutorials",
  "limit": 20
}
```

**제약사항**

- `limit`은 최대 50까지 허용됩니다.

**응답**

```json
{
  "success": true,
  "data": {
    "results": [
      {
        "slug": "example-post",
        "title": "게시글 제목",
        "description": "게시글 내용 일부...",
        "date": "2025-12-14T10:30:00+09:00",
        "similarity": 0.86
      }
    ],
    "fallback": true,
    "source": "lexical"
  },
  "error": null
}
```

**응답 필드**

- `results`: 검색 결과 목록
  - `slug`: 게시글 슬러그
  - `title`: 제목
  - `description`: 내용 미리보기
  - `date`: 생성일시
  - `similarity`: `source`가 `embeddings`일 때 쿼리와 결과 간의 코사인 유사도(0.0~1.0)를, 그 외에는 `null`을 반환합니다.
- `fallback`: 임베딩 검색 결과가 없어서 `lexical`로 대체되었는지를 나타냅니다.
- `source`: 현재 사용된 검색 방식, `embeddings` 또는 `lexical`

**디버깅 팁**

- 로컬에서 search 흐름을 추적하려면 `application.properties` 또는 실행 시 `logging.level.gc.demo.service.SearchService=DEBUG`를 설정하십시오.
- 그러면 쿼리, limit, 임베딩 사용 여부, 결과 개수, 폴백 여부 등을 로그에서 확인할 수 있습니다. 프론트에서 특정 키워드로 요청 후 로그를 보는 방식으로 임베딩/렉시컬 판단을 검증할 수 있습니다.

---

## 4. 동기화 API

### 4.1 게시글 동기화

**POST** `/posts/sync`

**인증 필요**:

- `Authorization: Bearer <JWT>` (관리자 JWT에 포함된 `SCOPE_sync` 필요)
- `X-Sync-Token` 헤더

외부 시스템에서 게시글 데이터를 동기화합니다.

**요청 헤더**

```
X-Sync-Token: {SYNC_TOKEN}
```

**요청 본문**

```json
{
  "posts": [
    {
      "slug": "example-post",
      "title": "게시글 제목",
      "content": "게시글 내용",
      "createdAt": "2025-12-14T10:30:00+09:00",
      "authorId": 123456789
    }
  ]
}
```

**요청 필드**

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| posts | array | Y | 동기화할 게시글 목록 |
| posts[].slug | string | Y | 게시글 슬러그 |
| posts[].title | string | Y | 제목 |
| posts[].content | string | Y | 내용 |
| posts[].createdAt | string | N | 생성일시 (ISO 8601) |
| posts[].authorId | long | N | 작성자 ID (Snowflake ID) |
| posts[].embedding | float[] | N | 미리 생성된 임베딩 벡터(384차원)를 전송하면 서버에서 그대로 저장하며 없으면 자체 생성 |

**응답**

```json
{
  "success": true,
  "data": {
    "total": 2,
    "inserted": 1,
    "updated": 1,
    "deleted": 0
  },
  "error": null
}
```

**응답 필드**

- `total`: 처리한 총 게시글 수
- `inserted`: 새로 삽입된 게시글 수
- `updated`: 업데이트된 게시글 수
- `deleted`: 삭제된 게시글 수 (현재는 기존 데이터 삭제를 수행하지 않아 항상 0)

**동작 특이사항**

- 요청에 포함되지 않은 기존 게시글은 삭제하지 않습니다.
- `authorId`가 존재하지 않는 경우 404 Not Found 에러가 발생합니다.
- `createdAt` 필드는 현재 저장/갱신에 사용하지 않습니다.
- `posts[].embedding`이 포함되면 384차원 벡터로 그대로 저장되고, 길이가 맞지 않거나 없으면 서버가 OpenAI(OpenAI API 키가 설정된 경우)를 통해 임베딩을 생성합니다.

**에러 응답**

- 401 Unauthorized: 유효하지 않은 동기화 토큰 또는 인증 실패
- 403 Forbidden: JWT에 `SCOPE_sync` 권한이 없는 경우
- 404 Not Found: 요청의 `authorId`가 존재하지 않는 경우

---

## 부록

### A. HTTP 상태 코드

| 코드 | 의미 | 설명 |
|------|------|------|
| 200 | OK | 요청 성공 |
| 400 | Bad Request | 잘못된 요청 (유효성 검증 실패) |
| 401 | Unauthorized | 인증 실패 |
| 403 | Forbidden | 권한 없음 |
| 404 | Not Found | 리소스를 찾을 수 없음 |
| 500 | Internal Server Error | 서버 내부 오류 |

### B. 날짜/시간 형식

모든 날짜/시간은 ISO 8601 형식의 OffsetDateTime을 사용합니다.

예: `2025-12-14T10:30:00+09:00`

### C. ID 형식

- **게시글/댓글/사용자 ID**: Snowflake ID (Long 타입, 64비트 정수)
  - 분산 환경에서 고유성 보장
  - 시간 순서 정렬 가능
  - 64비트 부호 있는 정수

### D. 환경 변수

#### 필수 환경 변수

- `DB_URL`: PostgreSQL 데이터베이스 URL
- `DB_USERNAME`: 데이터베이스 사용자명
- `DB_PASSWORD`: 데이터베이스 비밀번호
- `GITHUB_CLIENT_ID`: GitHub OAuth App Client ID
- `GITHUB_CLIENT_SECRET`: GitHub OAuth App Client Secret
- `SYNC_TOKEN`: 동기화 API 인증 토큰
- `FRONTEND_URL`: 프론트엔드 URL (OAuth 로그인 성공 후 리다이렉트)

#### 선택 환경 변수

- `SPRING_PROFILES_ACTIVE`: 활성 프로필 (dev, prod)
- `JWT_JWK_SET_URI`: 외부 JWK Set URI (기본: http://localhost:8080/oauth2/jwks)
- `JWT_COOKIE_DOMAIN`: 쿠키 도메인 (기본: localhost, cross-origin 시 빈 문자열 권장)
- `JWT_COOKIE_SECURE`: 쿠키 Secure 속성 (기본: false, 프로덕션: true)
- `JWT_COOKIE_MAX_AGE`: 쿠키 유효 시간 초 단위 (기본: 86400 = 24시간)

### E. 보안 고려사항

#### 개발 환경
- HTTP-Only 쿠키 사용 (Secure=false)
- localhost 도메인
- SameSite=Lax (동일 도메인)

#### 프로덕션 환경 (Same-Origin)

프론트엔드와 백엔드가 같은 도메인에 있는 경우:

```env
FRONTEND_URL=https://yourdomain.com
JWT_COOKIE_DOMAIN=yourdomain.com
JWT_COOKIE_SECURE=true
```

#### 프로덕션 환경 (Cross-Origin) ⭐ 권장 설정

**현재 구성**: 프론트엔드(`vercel.app`)와 백엔드(`onrender.com`)가 다른 도메인

**필수 환경 변수**:
```env
FRONTEND_URL=https://my-blog-tau-gilt.vercel.app
JWT_COOKIE_DOMAIN=
JWT_COOKIE_SECURE=true
```

**백엔드 설정**:
- ✅ CORS 허용 origin: `FRONTEND_URL`
- ✅ CORS credentials: `true`
- ✅ 쿠키 `SameSite=None` + `Secure=true`

**프론트엔드 설정**:
```javascript
// 모든 API 요청에 credentials 포함 필수
fetch('https://gc-board-latest-1.onrender.com/api/v1/posts', {
  method: 'GET',
  credentials: 'include'  // 쿠키 전송 활성화
})

// axios 사용 시
axios.get('https://gc-board-latest-1.onrender.com/api/v1/posts', {
  withCredentials: true  // 쿠키 전송 활성화
})
```

**GitHub OAuth App 설정**:
- Homepage URL: `https://gc-board-latest-1.onrender.com`
- Authorization callback URL: `https://gc-board-latest-1.onrender.com/login/oauth2/code/github`

### F. GitHub OAuth App 설정

1. GitHub 설정: https://github.com/settings/developers
2. "New OAuth App" 생성
3. 설정 정보:
   - **Application name**: demo-api
   - **Homepage URL**:
     - 개발: `http://localhost:8080`
     - 프로덕션: `https://gc-board-latest-1.onrender.com` (백엔드 URL)
   - **Authorization callback URL**:
     - 개발: `http://localhost:8080/login/oauth2/code/github`
     - 프로덕션: `https://gc-board-latest-1.onrender.com/login/oauth2/code/github`
4. Client ID와 Client Secret을 환경 변수에 설정

**⚠️ 중요**:
- Callback URL은 **백엔드 도메인**을 사용합니다 (프론트엔드 아님)
- 로그인 성공 후 백엔드가 `FRONTEND_URL`로 자동 리다이렉트합니다
- 프론트엔드 URL(`https://my-blog-tau-gilt.vercel.app`)은 callback URL이 아닙니다
