#!/usr/bin/env python3
"""
Simple Python program to send traces to Langfuse with SSL disabled.

Install dependencies:
    pip install langfuse

Usage:
    export LANGFUSE_PUBLIC_KEY=pk-lf-...
    export LANGFUSE_SECRET_KEY=sk-lf-...
    export LANGFUSE_HOST=https://your-langfuse-instance.com
    python langfuse_example.py
"""

import os
import ssl
import urllib3
from datetime import datetime

# Disable SSL warnings when using self-signed certificates
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# Disable SSL verification globally for httpx/requests
os.environ["CURL_CA_BUNDLE"] = ""
os.environ["REQUESTS_CA_BUNDLE"] = ""

from langfuse import Langfuse


def create_langfuse_client():
    """
    Create a Langfuse client with SSL verification disabled.
    """
    # Get credentials from environment variables
    public_key = os.getenv("LANGFUSE_PUBLIC_KEY", "pk-lf-your-public-key")
    secret_key = os.getenv("LANGFUSE_SECRET_KEY", "sk-lf-your-secret-key")
    host = os.getenv("LANGFUSE_HOST", "https://cloud.langfuse.com")

    # Create Langfuse client
    # Note: httpx_client parameter can be used to pass a custom client with SSL disabled
    langfuse = Langfuse(
        public_key=public_key,
        secret_key=secret_key,
        host=host,
        # Disable SSL verification - WARNING: Only use in development!
        httpx_client_options={
            "verify": False  # Disable SSL certificate verification
        }
    )

    return langfuse


def send_simple_trace():
    """
    Send a simple trace to Langfuse.
    """
    langfuse = create_langfuse_client()

    # Create a trace
    trace = langfuse.trace(
        name="example-trace",
        user_id="test-user",
        metadata={"environment": "development"},
        tags=["python", "example", "ssl-disabled"]
    )

    print(f"Created trace with ID: {trace.id}")

    # Create a span within the trace
    span = trace.span(
        name="processing-step",
        metadata={"step": 1}
    )

    # Simulate some work
    span.end(
        output={"result": "success"},
        metadata={"duration_ms": 100}
    )

    print("Created span within trace")

    # Create a generation (for LLM calls)
    generation = trace.generation(
        name="llm-call",
        model="gpt-3.5-turbo",
        model_parameters={"temperature": 0.7, "max_tokens": 100},
        input=[
            {"role": "system", "content": "You are a helpful assistant."},
            {"role": "user", "content": "Hello, how are you?"}
        ],
        output="I'm doing well, thank you for asking! How can I help you today?",
        usage={
            "prompt_tokens": 25,
            "completion_tokens": 15,
            "total_tokens": 40
        }
    )

    print(f"Created generation with ID: {generation.id}")

    # Flush to ensure all data is sent
    langfuse.flush()
    print("Flushed all traces to Langfuse")

    return trace.id


def send_trace_with_decorator():
    """
    Example using the @observe decorator for automatic tracing.
    """
    from langfuse.decorators import observe, langfuse_context

    # Set Langfuse context options to disable SSL
    langfuse_context.configure(
        public_key=os.getenv("LANGFUSE_PUBLIC_KEY", "pk-lf-your-public-key"),
        secret_key=os.getenv("LANGFUSE_SECRET_KEY", "sk-lf-your-secret-key"),
        host=os.getenv("LANGFUSE_HOST", "https://cloud.langfuse.com"),
        httpx_client_options={"verify": False}  # Disable SSL
    )

    @observe()
    def my_function(input_text: str) -> str:
        """A traced function."""
        # Simulate processing
        return f"Processed: {input_text}"

    @observe(as_type="generation")
    def my_llm_call(prompt: str) -> str:
        """A traced LLM call."""
        # Update observation with model details
        langfuse_context.update_current_observation(
            model="gpt-4",
            input=prompt,
            usage={"prompt_tokens": 10, "completion_tokens": 20}
        )
        return "This is the LLM response"

    # Call the decorated functions
    result = my_function("Hello, World!")
    print(f"Function result: {result}")

    llm_result = my_llm_call("What is the meaning of life?")
    print(f"LLM result: {llm_result}")

    # Flush to ensure all data is sent
    langfuse_context.flush()
    print("Flushed decorator traces to Langfuse")


def main():
    print("=" * 50)
    print("Langfuse Python Example (SSL Disabled)")
    print("=" * 50)
    print()

    print("⚠️  WARNING: SSL verification is disabled!")
    print("   Only use this in development/testing environments.")
    print()

    # Check if credentials are set
    if not os.getenv("LANGFUSE_PUBLIC_KEY"):
        print("Note: LANGFUSE_PUBLIC_KEY not set, using placeholder")
    if not os.getenv("LANGFUSE_SECRET_KEY"):
        print("Note: LANGFUSE_SECRET_KEY not set, using placeholder")

    print()
    print("-" * 50)
    print("Sending simple trace...")
    print("-" * 50)

    try:
        trace_id = send_simple_trace()
        print(f"\n✅ Successfully sent trace: {trace_id}")
    except Exception as e:
        print(f"\n❌ Error sending trace: {e}")

    print()
    print("-" * 50)
    print("Sending trace with decorator...")
    print("-" * 50)

    try:
        send_trace_with_decorator()
        print("\n✅ Successfully sent decorator traces")
    except Exception as e:
        print(f"\n❌ Error sending decorator traces: {e}")

    print()
    print("=" * 50)
    print("Done!")
    print("=" * 50)


if __name__ == "__main__":
    main()
