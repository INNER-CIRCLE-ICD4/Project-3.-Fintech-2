# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is EasyPay, a fintech application providing payment and money transfer services built with Spring Boot 3.5.3 and Java 21.

## Essential Commands

### Build and Run
```bash
# Clean build (skip tests for faster build)
./gradlew clean build -x test

# Run application (default dev profile)
./gradlew bootRun

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=prod'

# Run built JAR directly
java -jar build/libs/easypay-0.0.1-SNAPSHOT.jar
```

### Testing
```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "fintech2.easypay.auth.service.AuthServiceTest"

# Performance testing with Gatling
./gradlew gatlingRun
```

### Code Quality
```bash
# SpotBugs analysis
./gradlew spotbugsMain

# PMD analysis  
./gradlew pmdMain

# Dependency vulnerability check
./gradlew dependencyCheckAnalyze
```

### Docker Operations
```bash
# Build Docker image
./gradlew dockerBuild

# Start with Docker Compose
docker-compose up -d

# Stop containers
docker-compose down
```

## Architecture Overview

### Domain Structure
The application follows a domain-driven design with clear package separation:

- **account/** - Multi-account management system including UserAccount (primary user accounts), ExternalAccount (linked bank accounts), and VirtualAccount (temporary transaction accounts)
- **auth/** - JWT-based authentication with PIN verification for sensitive operations. Handles user management, login history, and token refresh
- **transfer/** - Money transfer services with secure PIN-verified transfers and external banking API integration
- **payment/** - Payment processing with cancel/refund capabilities and payment gateway integration
- **audit/** - Comprehensive audit logging and notification services for all transactions
- **common/** - Shared utilities, exceptions, DTOs, and enums used across domains
- **config/** - Spring configuration including SecurityConfig (JWT + Spring Security), CacheConfig (Caffeine), and SchedulingConfig
- **external/** - Mock external API services for banking and payment gateway integration

### Key Architectural Patterns

1. **JWT Authentication Flow**: All API endpoints except /api/auth/** require Bearer token authentication via JwtAuthenticationFilter
2. **PIN Security**: Sensitive operations (account creation, deletion, secure transfers) require additional PIN verification
3. **Transaction Management**: Uses Spring's @Transactional with proper isolation levels for financial operations
4. **Caching Strategy**: Caffeine cache for frequently accessed data like account balances and user profiles
5. **Audit Trail**: Every financial transaction creates immutable audit logs with user, action, and result tracking

### Database Configuration

- **Development**: H2 in-memory database with console at /h2-console
- **Production**: PostgreSQL with Flyway migrations
- **JPA Settings**: Show SQL enabled in dev, format_sql for readability

### External Service Integration

The application uses mock services for external APIs:
- **BankingApiService**: Simulates external bank transfers
- **PaymentGatewayService**: Simulates PG payment processing

Real API endpoints configured in application.yml:
```yaml
external:
  banking:
    api:
      base-url: https://api.banking.example.com
      timeout: 30000
```

### Security Configuration

- JWT tokens with configurable expiration (default 24 hours)
- BCrypt password encoding with default strength
- CORS enabled for frontend integration
- Spring Security with stateless session management
- All sensitive data (PINs, passwords) encrypted before storage

### API Endpoint Structure

All REST APIs follow consistent patterns:
- Base path: `/api/{domain}`
- Response wrapper: `ApiResponse<T>` with success flag and data
- Error handling: Global exception handler with proper HTTP status codes
- Validation: Jakarta Bean Validation on all request DTOs

### Testing Approach

- Unit tests for services with mocked dependencies
- Integration tests with @SpringBootTest for end-to-end flows
- Scenario tests for complex business flows (TransferServiceScenarioTest, PaymentServiceScenarioTest)
- Test profile with in-memory H2 database

# MVP Fast Development
## 우선순위
1. 핵심 기능 구현 (70% 노력)
2. 코드 최적화 (20% 노력)
3. 사용자 피드백 수집 도구 (10% 노력)


### Development Workflow

1. Create feature branch from develop: `feat/{name}/{description}`
2. Implement with existing patterns and conventions
3. Ensure all tests pass: `./gradlew test`
4. Create PR to develop branch with review
5. After review, squash merge to develop
6. Develop auto-deploys to test server
7. Release branch created for production deployment
# Enterprise Grade Development
## 보안 요구사항
- 모든 입력값 검증 필수
- SQL 인젝션 방지 조치
- 민감 데이터 암호화 저장
- 정기 보안 감사 대응

## 성능 기준
- 페이지 로딩 시간 2초 이내
- API 응답 시간 500ms 이내
- 데이터베이스 쿼리 최적화 필수
- 캐싱 전략 구현

## 문서화 요구사항
- 모든 API는 OpenAPI 3.0 문서화
- 코드 변경 시 README 업데이트
- 아키텍처 결정 기록(ADR) 작성
- git commit 시 claude 관련 내용은 제거 후 커밋 push
- 기능 단위로 구현이 완료 되면  테스트 후 commit
- 신규 작업을 하는 경우 readme 브랜치 규칙에 따라 신규 브런치를 생성 후 작업
- 작업전 develop 브랜치 기준으로 신규 브랜치를 생성 develop 브랜치는 원격 저장소화 항상 동기화