# RepoLens

GitHub Repository Structure Explorer — Java/Spring Boot edition.

Explore any public GitHub repository as an interactive tree with DFS or BFS traversal, inspect node metadata, and generate AI-powered file summaries.

---

## Architecture

```
repolens/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/repolens/
    │   │   ├── RepoLensApplication.java       entry point
    │   │   ├── controller/
    │   │   │   ├── HealthController.java       GET /api/health
    │   │   │   ├── RepositoryController.java   POST /api/repository/{explore,summary}
    │   │   │   └── WebController.java          GET / → Thymeleaf
    │   │   ├── service/
    │   │   │   ├── RepositoryService.java      orchestrates explore flow
    │   │   │   ├── TreeBuilderService.java     flat items → recursive TreeNode
    │   │   │   ├── TraversalService.java       DFS / BFS
    │   │   │   └── SummaryService.java         fetch + validate + summarize
    │   │   ├── client/
    │   │   │   ├── GitHubClient.java           GitHub REST API (RestClient)
    │   │   │   └── OpenAiClient.java           OpenAI chat completions (RestClient)
    │   │   ├── model/
    │   │   │   ├── NodeType.java               DIRECTORY | FILE enum
    │   │   │   ├── TreeNode.java               recursive tree record
    │   │   │   ├── TraversalEntry.java         path, type, depth, order
    │   │   │   └── RepositoryInfo.java         owner, name, branch, url
    │   │   ├── dto/
    │   │   │   ├── ExploreRequest / Response
    │   │   │   ├── SummaryRequest / Response
    │   │   │   └── ErrorResponse
    │   │   └── exception/
    │   │       ├── GitHubNotFoundException / RateLimitException / ResponseException
    │   │       ├── OpenAiException
    │   │       ├── DirectorySelectedException / FileTooLargeException / BinaryFileException
    │   │       └── GlobalExceptionHandler.java
    │   └── resources/
    │       ├── templates/index.html            Thymeleaf shell
    │       ├── static/app.css                  styling
    │       ├── static/app.js                   vanilla JS interactivity
    │       └── application.properties
    └── test/
        └── java/com/repolens/
            ├── service/TraversalServiceTest.java
            ├── service/TreeBuilderServiceTest.java
            └── controller/RepositoryControllerTest.java
```

---

## Requirements

| Tool  | Version  |
|-------|----------|
| Java  | 21       |
| Maven | 3.9+     |

---

## Environment Variables

| Variable              | Required | Default        | Description                                         |
|-----------------------|----------|----------------|-----------------------------------------------------|
| `GITHUB_TOKEN`        | No       | *(empty)*      | GitHub PAT for higher rate limits (5 000 req/hr vs 60) |
| `OPENAI_API_KEY`      | **Yes*** | *(empty)*      | OpenAI API key — required to use the summary feature |
| `OPENAI_MODEL`        | No       | `gpt-4o-mini`  | OpenAI model for summaries                          |
| `OPENAI_MAX_TOKENS`   | No       | `500`          | Max tokens in the AI response                       |
| `MAX_FILE_SIZE_BYTES` | No       | `100000`       | Files larger than this are rejected for summarization |

*The app starts and explore works without `OPENAI_API_KEY`; only the summary endpoint requires it.

---

## Setup and Run

### 1. Clone and enter the directory

```bash
cd repolens
```

### 2. Configure environment (create `.env` or export variables)

```bash
export GITHUB_TOKEN=ghp_your_token_here
export OPENAI_API_KEY=sk-your_openai_key_here
```

### 3. Build

```bash
mvn clean package -DskipTests
```

### 4. Run

```bash
mvn spring-boot:run
# or with the fat jar:
java -jar target/repolens-1.0.0.jar
```

The application starts at **http://localhost:8080**.

### 5. Run tests

```bash
mvn test
```

---

## API Reference

All responses use `snake_case` JSON keys (identical contract to the original Python API).

### `GET /api/health`

```json
{ "status": "ok" }
```

---

### `POST /api/repository/explore`

Fetch and traverse a public GitHub repository tree.

**Request**
```json
{
  "repo_url": "https://github.com/vercel/next.js",
  "traversal_mode": "dfs"
}
```

| Field           | Type             | Required | Default | Description                   |
|-----------------|------------------|----------|---------|-------------------------------|
| `repo_url`      | string           | Yes      | —       | Public GitHub repository URL  |
| `traversal_mode`| `"dfs"` \| `"bfs"` | No     | `"dfs"` | Tree traversal algorithm      |

**Response `200`**
```json
{
  "repository": {
    "owner": "vercel",
    "name": "next.js",
    "default_branch": "canary",
    "html_url": "https://github.com/vercel/next.js"
  },
  "tree": {
    "name": "next.js",
    "path": "",
    "type": "directory",
    "size": null,
    "children": [ ... ]
  },
  "traversal_mode": "dfs",
  "traversal": [
    { "path": "", "type": "directory", "depth": 0, "order": 1 },
    { "path": "packages", "type": "directory", "depth": 1, "order": 2 },
    ...
  ]
}
```

**Error responses**

| HTTP | Condition |
|------|-----------|
| 400  | Invalid URL, bad traversal mode |
| 404  | Repository not found or not public |
| 429  | GitHub API rate limit exceeded |
| 502  | GitHub API upstream failure |

---

### `POST /api/repository/summary`

Generate an AI summary for a specific file in the repository.

**Request**
```json
{
  "repo_url": "https://github.com/vercel/next.js",
  "file_path": "packages/next/src/server/app-render/app-render.tsx"
}
```

| Field       | Type   | Required | Description                          |
|-------------|--------|----------|--------------------------------------|
| `repo_url`  | string | Yes      | Public GitHub repository URL         |
| `file_path` | string | Yes      | Path to the file (relative to root)  |

**Response `200`**
```json
{
  "file_path": "packages/next/src/server/app-render/app-render.tsx",
  "summary": "This file implements the core server-side rendering logic for Next.js App Router..."
}
```

**Error responses**

| HTTP | Condition |
|------|-----------|
| 400  | Invalid URL, directory path provided, binary file, or file exceeds size limit |
| 404  | File not found in repository |
| 429  | GitHub API rate limit exceeded |
| 502  | GitHub API or OpenAI API failure |

---

## API Contract Changes (Python → Java)

No breaking changes. The JSON contract is identical:

| Concern | Python | Java |
|---------|--------|------|
| Naming | snake_case (Pydantic) | snake_case (Jackson `SNAKE_CASE` strategy) |
| `type` values | `"directory"` / `"file"` | `"directory"` / `"file"` (enum `@JsonValue`) |
| Error body | `{"detail": "..."}` | `{"detail": "..."}` |
| HTTP status codes | 400/404/429/502 | 400/404/429/502 |
| Summary endpoint | Not implemented | **New — fully implemented** |

---

## Known Gaps / Risks

- **Large repositories**: GitHub's Git Trees API returns at most ~100 000 items; the API is called with `recursive=1`. Very large monorepos that exceed GitHub's server-side limit will receive a 502 with a descriptive message.
- **Rate limits without auth**: Without `GITHUB_TOKEN`, the GitHub API allows only 60 unauthenticated requests per hour. Set the token for production use.
- **OpenAI costs**: Each summary call uses OpenAI tokens. The default model is `gpt-4o-mini` (low cost), and content is hard-truncated to 80 000 characters before sending.
- **Binary detection**: Binary files are detected by the presence of null bytes after base64 decoding. Edge cases (e.g., valid UTF-8 with no null bytes but still non-text) may pass through, resulting in a noisy summary.
