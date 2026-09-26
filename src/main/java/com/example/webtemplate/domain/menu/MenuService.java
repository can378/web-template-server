package com.example.webtemplate.domain.menu;

import java.util.*;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.example.webtemplate.domain.menu.MenuData.*;

@Service
@Transactional(readOnly = true)
public class MenuService {
    private final MenuRepository menus;
    private final Validator validator;
    public MenuService(MenuRepository menus, Validator validator) { this.menus = menus; this.validator = validator; }
    public List<Navigation> navigation(Authentication auth) {
        var rows = menus.list();
        Set<Long> roles = auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                ? Set.of() : menus.userRoles(auth.getName());
        var permitted = new HashSet<Long>();
        var restricted = new HashSet<Long>();
        for (var link : menus.roles()) {
            restricted.add(link.menuId());
            if (roles.contains(link.roleId())) permitted.add(link.menuId());
        }
        var byId = new HashMap<Long, Row>();
        rows.forEach(row -> byId.put(row.menuId(), row));
        var nodes = new LinkedHashMap<Long, Navigation>();
        for (var row : rows) {
            Row current = row;
            var visited = new HashSet<Long>();
            boolean allowed = true;
            while (current != null) {
                if (!visited.add(current.menuId()) || !current.isActive() || !current.isVisible()
                        || (restricted.contains(current.menuId()) && !permitted.contains(current.menuId()))) { allowed = false; break; }
                if (current.parentMenuId() == null) break;
                current = byId.get(current.parentMenuId());
                if (current == null) allowed = false;
            }
            if (allowed) nodes.put(row.menuId(), new Navigation(row.menuId(), row.menuName(), row.menuPath(), row.icon(), new ArrayList<>()));
        }
        var result = new ArrayList<Navigation>();
        for (var row : rows) {
            var node = nodes.get(row.menuId());
            if (node == null) continue;
            if (row.parentMenuId() == null) result.add(node);
            else nodes.get(row.parentMenuId()).children().add(node);
        }
        return result;
    }
    public List<Detail> list() {
        var roles = menus.roles();
        return menus.list().stream().map(row -> new Detail(row, roles.stream()
                .filter(link -> link.menuId() == row.menuId()).map(MenuRole::roleId).toList())).toList();
    }
    public Detail get(long id) {
        return list().stream().filter(d -> d.menu().menuId() == id).findFirst()
                .orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."));
    }
    @Transactional
    public Detail create(Input input) {
        validate(input, null, menus.lockHierarchy());
        return get(menus.insert(input));
    }
    @Transactional
    public Detail patch(long id, Map<String, Object> patch) {
        var rows = menus.lockHierarchy();
        var old = require(rows, id);
        var allowed = Set.of("parentMenuId", "menuCode", "menuName", "menuPath", "icon", "sortOrder", "isVisible", "isActive");
        if (!allowed.containsAll(patch.keySet())) throw fail(HttpStatus.BAD_REQUEST, "알 수 없는 수정 필드입니다.");
        var input = new Input(value(patch, "parentMenuId", old.parentMenuId(), Long.class),
                value(patch, "menuCode", old.menuCode(), String.class), value(patch, "menuName", old.menuName(), String.class),
                value(patch, "menuPath", old.menuPath(), String.class), value(patch, "icon", old.icon(), String.class),
                value(patch, "sortOrder", old.sortOrder(), Integer.class), value(patch, "isVisible", old.isVisible(), Boolean.class),
                value(patch, "isActive", old.isActive(), Boolean.class));
        validate(input, id, rows);
        menus.update(id, input);
        return get(id);
    }
    private <T> T value(Map<String, Object> patch, String key, T fallback, Class<T> type) {
        if (!patch.containsKey(key)) return fallback;
        Object value = patch.get(key);
        if (value == null) return null;
        if (type == Long.class && value instanceof Integer number) return type.cast(number.longValue());
        if (!type.isInstance(value)) throw fail(HttpStatus.BAD_REQUEST, key + "의 자료형을 확인해 주세요.");
        return type.cast(value);
    }
    private void validate(Input input, Long id, List<Row> rows) {
        if (!validator.validate(input).isEmpty()) throw fail(HttpStatus.BAD_REQUEST, "메뉴 입력값을 확인해 주세요.");
        if (rows.stream().anyMatch(row -> !Objects.equals(row.menuId(), id) && row.menuCode().equalsIgnoreCase(input.menuCode())))
            throw fail(HttpStatus.CONFLICT, "이미 사용 중인 메뉴 코드입니다.");
        Long parent = input.parentMenuId();
        var visited = new HashSet<Long>();
        while (parent != null) {
            if (Objects.equals(parent, id) || !visited.add(parent)) throw fail(HttpStatus.BAD_REQUEST, "메뉴 순환 관계는 허용되지 않습니다.");
            long parentId = parent;
            var row = rows.stream().filter(r -> r.menuId() == parentId).findFirst()
                    .orElseThrow(() -> fail(HttpStatus.BAD_REQUEST, "상위 메뉴가 존재하지 않습니다."));
            parent = row.parentMenuId();
        }
    }
    @Transactional
    public Detail roles(long id, List<Long> roleIds) {
        require(menus.lockHierarchy(), id);
        var unique = new LinkedHashSet<>(roleIds);
        for (long role : unique) if (!menus.roleExists(role)) throw fail(HttpStatus.BAD_REQUEST, "존재하지 않는 역할입니다.");
        menus.replaceRoles(id, unique);
        return get(id);
    }
    @Transactional
    public void delete(long id) {
        var rows = menus.lockHierarchy();
        require(rows, id);
        if (rows.stream().anyMatch(row -> Objects.equals(row.parentMenuId(), id)))
            throw fail(HttpStatus.CONFLICT, "하위 메뉴를 먼저 삭제하거나 이동해 주세요.");
        menus.delete(id);
    }
    private Row require(List<Row> rows, long id) {
        return rows.stream().filter(row -> row.menuId() == id).findFirst()
                .orElseThrow(() -> fail(HttpStatus.NOT_FOUND, "메뉴를 찾을 수 없습니다."));
    }
    private ResponseStatusException fail(HttpStatus status, String reason) { return new ResponseStatusException(status, reason); }
}
