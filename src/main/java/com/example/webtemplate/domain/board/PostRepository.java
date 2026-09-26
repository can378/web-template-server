package com.example.webtemplate.domain.board;

import java.util.List;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import com.example.webtemplate.domain.board.PostData.Post;

@Repository
public class PostRepository {
    private final JdbcClient jdbc;

    public PostRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public List<Post> list(String boardCode) {
        return jdbc.sql("""
                SELECT p.post_id, p.board_id, b.board_name, p.title, p.content, p.is_pinned, p.created_at,
                       u.name AS author_name
                FROM web_posts p
                JOIN web_boards b ON b.board_id = p.board_id
                LEFT JOIN web_users u ON u.user_id = p.author_id
                WHERE p.deleted_at IS NULL AND b.is_active = 1 AND b.board_code = ?
                ORDER BY p.is_pinned DESC, p.created_at DESC, p.post_id DESC
                """).param(boardCode).query((rs, row) -> {
            long boardId = rs.getLong("board_id");
            return new Post(rs.getLong("post_id"), boardId, rs.getString("board_name"),
                    rs.getString("title"), rs.getString("content"), rs.getString("author_name"),
                    rs.getTimestamp("created_at").toLocalDateTime(), rs.getBoolean("is_pinned"));
        }).list();
    }
}
