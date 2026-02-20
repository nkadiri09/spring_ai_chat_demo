package com.naren.kadiri.chatdemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(excludeName = {
        "org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration",
        "org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration"
})
public class ChatDemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatDemoApplication.class, args);
    }
}

