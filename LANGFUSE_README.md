# Langfuse Integration for Spring AI (Without OpenTelemetry)

This project includes Langfuse integration using the official `langfuse-java` SDK (v0.1.2) for Spring AI. It does **not** require OpenTelemetry.

## Setup

### 1. Dependency

The Langfuse Java SDK is already added to `build.gradle`:

```groovy
implementation 'com.langfuse:langfuse-java:0.1.2'
```

### 2. Get Langfuse Credentials

1. Sign up at [Langfuse Cloud](https://cloud.langfuse.com) or self-host Langfuse
2. Create a new project and get your API keys from the project settings

### 3. Configure Environment Variables

Set the following environment variables before running the application:

```bash
export GROQ_API_KEY=your-groq-api-key
export LANGFUSE_PUBLIC_KEY=pk-lf-...
export LANGFUSE_SECRET_KEY=sk-lf-...
# Optional: for self-hosted Langfuse
export LANGFUSE_HOST=https://your-langfuse-instance.com
# Optional: trust all SSL certificates (for self-signed certs in dev/test)
export LANGFUSE_TRUST_ALL_CERTS=true
```

Or add them to your `application.properties`:

```properties
langfuse.enabled=true
langfuse.public-key=pk-lf-...
langfuse.secret-key=sk-lf-...
langfuse.host=https://cloud.langfuse.com
# WARNING: Only enable for development/testing with self-signed certificates!
langfuse.trust-all-certificates=false
```

### 4. Self-Signed SSL Certificates

If you're running a self-hosted Langfuse instance with self-signed SSL certificates, you can configure the client to trust all certificates:

```properties
langfuse.trust-all-certificates=true
```

Or via environment variable:

```bash
export LANGFUSE_TRUST_ALL_CERTS=true
```

**⚠️ WARNING:** This disables SSL certificate verification and should **ONLY** be used for development/testing environments. Never use this in production!

The implementation creates a custom OkHttpClient with:
- A TrustManager that accepts all certificates
- A HostnameVerifier that accepts all hostnames

### 5. Run the Application

```bash
./gradlew bootRun
```

## How It Works

The integration consists of three components:

1. **LangfuseProperties** - Configuration properties for Langfuse connection
2. **LangfuseService** - Service that wraps the official Langfuse Java SDK
3. **LangfuseAdvisor** - Spring AI Advisor that automatically intercepts all chat calls

The `LangfuseAdvisor` is wired into the `ChatClient` and intercepts:
- Synchronous chat calls (`adviseCall`)
- Streaming chat calls (`adviseStream`)

## What Gets Traced

For each LLM call, the following information is sent to Langfuse:

- **Trace**: High-level request tracking
  - Name: `chat-completion` or `chat-completion-stream`
  - User ID: Extracted from context or "anonymous"
  - Metadata: Message count, prompt info

- **Generation**: LLM call details
  - Model name
  - Input messages (system, user, assistant)
  - Output response
  - Token usage (prompt, completion, total)
  - Latency (start/end time)
  - Error status if applicable

## Customizing User ID

To track user IDs in Langfuse, you can pass them in the chat context:

```java
// In your controller or service
chatClient.prompt()
    .user("Hello")
    .advisors(spec -> spec.param("userId", "user-123"))
    .call()
    .content();
```

## Disabling Langfuse

Set `langfuse.enabled=false` in your properties to disable tracing without removing the advisor.

## Architecture

```
┌─────────────────┐
│  ChatController │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│   ChatClient    │
└────────┬────────┘
         │
         ▼
┌─────────────────┐     ┌─────────────────┐
│ LangfuseAdvisor │────▶│ LangfuseService │
└────────┬────────┘     └────────┬────────┘
         │                       │
         ▼                       ▼
┌─────────────────┐     ┌─────────────────┐
│   Groq/OpenAI   │     │ Langfuse Java   │
│      LLM        │     │     SDK         │
└─────────────────┘     └─────────────────┘
```

## Files

- `src/main/java/com/naren/kadiri/chatdemo/langfuse/`
  - `LangfuseProperties.java` - Configuration properties
  - `LangfuseService.java` - Wrapper for Langfuse Java SDK
  - `LangfuseAdvisor.java` - Spring AI Advisor for automatic tracing

## API Endpoints

Test the integration with these endpoints:

```bash
# Simple chat
curl "http://localhost:8080/api/chat/simple?message=Hello"

# POST chat
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Hello!"}'

# Streaming chat
curl "http://localhost:8080/api/chat/stream?message=Tell+me+a+joke"
```

After making requests, check your Langfuse dashboard to see the traces!
