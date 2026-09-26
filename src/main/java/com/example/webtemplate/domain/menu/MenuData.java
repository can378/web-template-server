package com.example.webtemplate.domain.menu;

import java.util.List;
import jakarta.validation.constraints.*;

public final class MenuData {
    private MenuData() { }
    public record Input(@Positive Long parentMenuId,
            @NotBlank @Size(max = 100) String menuCode,
            @NotBlank @Size(max = 100) String menuName,
            @Size(max = 500) @Pattern(regexp = "^/(?!/).*") String menuPath,
            @Size(max = 100) String icon, @NotNull Integer sortOrder,
            @NotNull Boolean isVisible, @NotNull Boolean isActive) { }
    public record Roles(@NotNull @Size(max = 100) List<@NotNull @Positive Long> roleIds) { }
    public record Row(long menuId, Long parentMenuId, String menuCode, String menuName,
            String menuPath, String icon, int sortOrder, boolean isVisible, boolean isActive) { }
    public record Detail(Row menu, List<Long> roleIds) { }
    public record Navigation(long id, String label, String path, String icon, List<Navigation> children) { }
    public record MenuRole(long menuId, long roleId) { }
}
