# MenyaCode

GitHub Repository Structure Explorer — Java/Spring Boot edition.

Explore any public GitHub repository as an interactive tree with DFS or BFS traversal, inspect node metadata, and generate AI-powered file summaries.

**Live demo:** https://menyacode-production.up.railway.app

---

## Features

- **Explore any public GitHub repo** — paste a URL, get the full file tree fetched from the GitHub Git Trees API.
- **DFS or BFS traversal** — switch traversal strategies and see the visit order/depth on each node.
- **Interactive tree UI** — expand/collapse folders, click any node to view metadata (path, size, type, traversal order/depth).
- **AI file summaries** — generate a plain-English explanation of any individual file via OpenAI, with automatic language detection.
- **Binary + size guards** — files over 100 KB or with null bytes are rejected with a clear error before they reach OpenAI.
- **Snake_case JSON contract** — REST endpoints return snake_case keys so the API is drop-in compatible with the original Python service.
- **Deployable with one Dockerfile** — runs locally on port 8080 or on Railway with the platform-assigned port.

---

## Technologies

| Layer | Stack |
|-------|-------|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.4 (Web, Thymeleaf, Validation) |
| HTTP clients | Spring `RestClient` (GitHub + OpenAI) |
| Templating | Thymeleaf (server-rendered shell) |
| Frontend | Vanilla HTML/CSS/JS — no build step |
| Testing | JUnit 5, Mockito, `@WebMvcTest` |
| Build | Maven 3.9 |
| Container | Multi-stage Dockerfile (Maven build → JRE runtime) |
| Hosting | Railway (Docker deploy, healthcheck on `/api/health`) |
| External APIs | GitHub REST API, OpenAI Chat Completions |

---

## Architecture

```
repolens/
├── pom.xml
├── Dockerfile
├── railway.toml
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
    │   │   ├── model/                          TreeNode, TraversalEntry, NodeType, RepositoryInfo
    │   │   ├── dto/                            Request/Response/Error DTOs
    │   │   └── exception/                      Typed exceptions + GlobalExceptionHandler
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

The flow is straightforward: `WebController` serves the Thymeleaf shell, the browser hits `RepositoryController`, the controller delegates to `RepositoryService` or `SummaryService`, which call out to GitHub (and OpenAI for summaries). Typed exceptions bubble up to `GlobalExceptionHandler`, which maps them to clean `{"detail": "..."}` JSON with the right HTTP status.

---

## The Process

The original RepoLens was a Python/FastAPI project. MenyaCode is a Java/Spring Boot rewrite that keeps the same JSON contract (snake_case keys, same status codes, same error body shape) so any existing client keeps working.

Rough order of work:

1. **Map the contract first.** Before writing Java, I locked in the exact request/response shape from the Python version. Jackson's `SNAKE_CASE` strategy plus `@JsonValue` on the `NodeType` enum kept the wire format identical without polluting field names.
2. **Model + tree builder.** Built `TreeNode` as a Java `record` and wrote `TreeBuilderService` to turn the flat list GitHub returns (`{path, type, size}`) into a nested tree.
3. **Iterative traversal.** Implemented DFS and BFS in [TraversalService.java](src/main/java/com/repolens/service/TraversalService.java) with explicit `ArrayDeque` instead of recursion — avoids stack-overflow risk on deeply nested repos and makes order/depth tracking trivial.
4. **Typed exception hierarchy.** Instead of returning ad-hoc error strings, each failure mode (rate limit, not found, binary file, file too large, OpenAI failure) is its own exception class, all funneled through one `GlobalExceptionHandler`.
5. **Summary pipeline.** Pulled file contents from GitHub (base64), stripped wrapping whitespace, rejected binary files (null-byte check) and oversized files, truncated to 80 000 chars, then sent to OpenAI. Detected language from file extension so the UI can show it.
6. **Tests.** Service-level unit tests (traversal + tree builder), plus `@WebMvcTest` controller tests that exercise the full error mapping.
7. **Frontend.** A single Thymeleaf page with vanilla JS — no React, no build step. The JS handles tree rendering, lazy-expansion of folders, node selection, and the summary fetch.
8. **Containerize + deploy.** Wrote a multi-stage Dockerfile (Maven layer → JRE layer), changed `server.port` to read `${PORT:8080}` so the same image runs locally and on Railway, and added `railway.toml` with a healthcheck on `/api/health`.

---

## How to Run

### Requirements

| Tool  | Version |
|-------|---------|
| Java  | 21      |
| Maven | 3.9+    |

### 1. Configure environment variables

```bash
export GITHUB_TOKEN=ghp_your_token_here
export OPENAI_API_KEY=sk-your_openai_key_here
```

### 2. Build

```bash
mvn clean package -DskipTests
```

### 3. Run

```bash
mvn spring-boot:run
# or with the fat jar:
java -jar target/repolens-1.0.0.jar
```

The application starts at **http://localhost:8080**.

### 4. Run tests

```bash
mvn test
```

### 5. Run with Docker (optional)

```bash
docker build -t menyacode .
docker run -p 8080:8080 \
  -e GITHUB_TOKEN=$GITHUB_TOKEN \
  -e OPENAI_API_KEY=$OPENAI_API_KEY \
  menyacode
```

---

## Environment Variables

| Variable              | Required | Default        | Description                                            |
|-----------------------|----------|----------------|--------------------------------------------------------|
| `GITHUB_TOKEN`        | No       | *(empty)*      | GitHub PAT for higher rate limits (5 000 req/hr vs 60) |
| `OPENAI_API_KEY`      | **Yes*** | *(empty)*      | OpenAI API key — required to use the summary feature   |
| `OPENAI_MODEL`        | No       | `gpt-4o-mini`  | OpenAI model for summaries                             |
| `OPENAI_MAX_TOKENS`   | No       | `500`          | Max tokens in the AI response                          |
| `MAX_FILE_SIZE_BYTES` | No       | `100000`       | Files larger than this are rejected for summarization  |
| `PORT`                | No       | `8080`         | Server port (Railway injects this automatically)       |

*The app starts and explore works without `OPENAI_API_KEY`; only the summary endpoint requires it.

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
    { "path": "packages", "type": "directory", "depth": 1, "order": 2 }
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

## Deployment (Railway)

The app is deployed on [Railway](https://railway.app) using the included [Dockerfile](Dockerfile) and [railway.toml](railway.toml). Railway builds the multi-stage image (Maven build → JRE runtime), injects `$PORT`, and routes traffic to the container.

Steps to redeploy from scratch:

1. Push the repo to GitHub.
2. In Railway: **New Project → Deploy from GitHub repo** → select this repo.
3. Under **Variables**, set:
   - `GITHUB_TOKEN` — GitHub PAT
   - `OPENAI_API_KEY` — OpenAI key
4. Under **Settings → Networking**, click **Generate Domain**.

`server.port` reads `${PORT:8080}`, so the same image runs locally on 8080 and on Railway with whatever port it assigns. The healthcheck path is `/api/health`.

---

## What I Learned

- **Preserving an API contract during a rewrite is its own design problem.** Jackson defaults to camelCase; keeping snake_case on the wire while writing idiomatic Java internally took deliberate config (`SNAKE_CASE` strategy + `@JsonValue` on enums) and disciplined DTO design.
- **Iterative DFS/BFS beats recursive when input is untrusted.** GitHub trees can be deeply nested. Using `ArrayDeque` instead of recursion in [TraversalService.java](src/main/java/com/repolens/service/TraversalService.java) eliminates a whole class of stack-overflow bugs and makes depth/order tracking explicit.
- **Typed exceptions clean up controller code.** Instead of try/catch chains in the controller, each failure mode is its own exception and `GlobalExceptionHandler` maps it once. Adding a new error type means one new class plus one handler method — controllers stay untouched.
- **Base64 + binary detection is fiddly.** GitHub's API returns file contents base64-encoded with newlines every 60 chars. You have to strip whitespace before decoding, then scan decoded bytes for null bytes to flag binaries. A `try`/`catch IllegalArgumentException` around the decode catches the rest.
- **Truncate before you call the LLM, not after.** Hard-capping content at 80 000 chars before sending keeps OpenAI cost and latency predictable, and a 100 KB file-size guard avoids ever loading huge blobs into memory.
- **`server.port=${PORT:8080}` is the one trick that makes a Spring Boot Docker image portable.** Same image, same Dockerfile, works locally and on Railway/Render/Fly/Cloud Run without modification.
- **Vanilla JS is enough for a small UI.** No build step, no bundler config to maintain, instant page loads. For a single-page tool like this, reaching for React would have been overkill.

---

## How to Improve

- **Cache GitHub tree responses.** A given `(owner, repo)` rarely changes between page loads. Adding an in-memory or Redis cache with a short TTL would cut latency and rate-limit pressure dramatically.
- **Stream long trees instead of returning JSON in one shot.** For monorepos approaching GitHub's ~100 000-item limit, the response can be megabytes. Server-sent events or chunked NDJSON would let the UI render progressively.
- **Smarter binary detection.** The current null-byte heuristic passes some non-text formats (e.g., minified bundles). Switching to a content-type sniff (magic bytes / `Tika`) would be more accurate.
- **Diff/summary across commits.** Right now you summarize a single file at HEAD. A "summarize what changed in this PR" mode would be more useful.
- **Auth for private repos.** Today only public repos work. A "Sign in with GitHub" flow + per-user tokens would unlock private repo exploration.
- **Move secrets out of `application.properties` entirely.** They're env-driven today, but a proper secret manager (Railway's built-in, or HashiCorp Vault for production) is cleaner than relying on platform env vars.
- **Frontend polish.** The vanilla JS works but is starting to push the limits — a small framework (Alpine, htmx, or Lit) would help if more interactivity (keyboard navigation, search-in-tree, breadcrumbs) gets added.
- **CI/CD.** Add a GitHub Actions workflow that runs `mvn test` on every PR and auto-deploys to Railway on merge to `main`.
- **Observability.** Add structured logging (already partially via Spring) and a metrics endpoint (`spring-boot-starter-actuator`) so production traffic patterns and OpenAI cost are visible.

---

## Known Gaps / Risks

- **Large repositories**: GitHub's Git Trees API returns at most ~100 000 items; the API is called with `recursive=1`. Very large monorepos that exceed GitHub's server-side limit will receive a 502 with a descriptive message.
- **Rate limits without auth**: Without `GITHUB_TOKEN`, the GitHub API allows only 60 unauthenticated requests per hour. Set the token for production use.
- **OpenAI costs**: Each summary call uses OpenAI tokens. The default model is `gpt-4o-mini` (low cost), and content is hard-truncated to 80 000 characters before sending.
- **Binary detection**: Binary files are detected by the presence of null bytes after base64 decoding. Edge cases (e.g., valid UTF-8 with no null bytes but still non-text) may pass through, resulting in a noisy summary.
