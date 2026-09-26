package com.example.webtemplate.domain.menu;

import java.net.URI;
import java.util.List;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.webtemplate.domain.menu.MenuData.*;
import com.example.webtemplate.global.exception.ApiExceptionHandler.ApiError;

@RestController
@RequestMapping("/api")
public class MenuController {
    private final MenuService menus;
    public MenuController(MenuService menus) { this.menus = menus; }
    @GetMapping("/menus")
    public ResponseEntity<List<Navigation>> navigation(Authentication auth) {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(menus.navigation(auth));
    }
    @GetMapping("/admin/menus")
    public List<Detail> list() { return menus.list(); }
    @GetMapping("/admin/menus/{menuId}")
    public Detail get(@PathVariable long menuId) { return menus.get(menuId); }
    @PostMapping("/admin/menus")
    public ResponseEntity<Detail> create(@Valid @RequestBody Input input) {
        var result = menus.create(input);
        return ResponseEntity.created(URI.create("/api/admin/menus/" + result.menu().menuId())).body(result);
    }
    @PatchMapping("/admin/menus/{menuId}")
    public Detail patch(@PathVariable long menuId, @RequestBody Map<String, Object> input) { return menus.patch(menuId, input); }
    @PutMapping("/admin/menus/{menuId}/roles")
    public Detail roles(@PathVariable long menuId, @Valid @RequestBody Roles input) { return menus.roles(menuId, input.roleIds()); }
    @DeleteMapping("/admin/menus/{menuId}")
    public ResponseEntity<Void> delete(@PathVariable long menuId) {
        menus.delete(menuId);
        return ResponseEntity.noContent().build();
    }
    @ExceptionHandler({DataIntegrityViolationException.class, TransientDataAccessException.class})
    public ResponseEntity<ApiError> conflict(Exception exception) {
        return ResponseEntity.status(409).body(new ApiError("MENU_CONFLICT", "메뉴 코드 중복 또는 참조·동시 변경 충돌입니다. 상태를 확인하고 다시 시도해 주세요.", List.of()));
    }
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ApiError> unavailable(Exception exception) {
        return ResponseEntity.status(503).body(new ApiError("MENU_STORAGE_UNAVAILABLE",
                "메뉴 저장소에 연결할 수 없습니다. 서버의 DB 연결 설정을 확인해 주세요.", List.of()));
    }
}
