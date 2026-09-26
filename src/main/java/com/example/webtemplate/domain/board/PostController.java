package com.example.webtemplate.domain.board;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.webtemplate.domain.board.PostData.Post;

@RestController
@RequestMapping("/api/boards/posts")
@Tag(name = "게시판", description = "게시글 조회 API")
public class PostController {
    private final PostRepository posts;

    public PostController(PostRepository posts) { this.posts = posts; }

    @GetMapping
    @Operation(summary = "게시글 목록 조회", description = "게시판 코드로 삭제되지 않은 게시글을 고정글과 작성일 순으로 조회합니다.")
    public ResponseEntity<List<Post>> list(@RequestParam String boardCode) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(posts.list(boardCode));
    }
}
