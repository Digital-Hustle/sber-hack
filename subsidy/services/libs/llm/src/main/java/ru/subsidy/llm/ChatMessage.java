package ru.subsidy.llm;

import java.util.List;

/** Сообщение диалога; images — base64 PNG страниц для vision-модели. */
public record ChatMessage(Role role, String content, List<String> images) {

    public enum Role { SYSTEM, USER, ASSISTANT }

    public static ChatMessage system(String text) {
        return new ChatMessage(Role.SYSTEM, text, List.of());
    }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, text, List.of());
    }
}
