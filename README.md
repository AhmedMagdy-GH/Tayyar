# Tayyar

Tayyar is a food-delivery marketplace being developed as a modular monolith.

- `backend/`: Maven backend using Java 21, Spring Boot 4.1.1, and Spring Web.
- `frontend/`: reserved for React + TypeScript; not initialized. UI/UX will be designed separately in Figma and Google Stitch.
- `docs/`: reserved for project documentation.

Current status: the first implementation step provides only `GET /api/v1/health`, returning HTTP 200 with `{"status":"UP","service":"tayyar-backend"}`. No database, authentication, or business features are implemented.

## Run the backend

Java 21 is required. The Maven wrapper downloads Maven and dependencies on first use.

```powershell
cd backend
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, use `./mvnw` instead of `.\mvnw.cmd`.

The health endpoint is available at `http://localhost:8080/api/v1/health`.
