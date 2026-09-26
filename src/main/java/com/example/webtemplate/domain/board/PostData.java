package com.example.webtemplate.domain.board;

import java.time.LocalDateTime;

public final class PostData {
    private PostData() { }
    public record Post(long id, long boardId, String category, String title, String content,
            String author, LocalDateTime createdAt, boolean pinned) { }
}
