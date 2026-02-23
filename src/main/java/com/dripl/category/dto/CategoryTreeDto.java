package com.dripl.category.dto;

import com.dripl.category.entity.Category;
import com.dripl.common.dto.BaseDto;
import com.dripl.common.enums.Status;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
public class CategoryTreeDto extends BaseDto {

    private UUID workspaceId;
    private UUID parentId;
    private String name;
    private String description;
    private Status status;
    private boolean income;
    private boolean excludeFromBudget;
    private boolean excludeFromTotals;
    private int displayOrder;
    private boolean group;
    private List<CategoryTreeDto> children;

    public static List<CategoryTreeDto> buildTree(List<Category> categories) {
        Map<UUID, List<Category>> byParent = new HashMap<>();
        for (Category category : categories) {
            byParent.computeIfAbsent(category.getParentId(), k -> new ArrayList<>()).add(category);
        }
        return buildBranch(null, byParent);
    }

    private static List<CategoryTreeDto> buildBranch(UUID parentId, Map<UUID, List<Category>> byParent) {
        return byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparingInt(Category::getDisplayOrder))
                .map(category -> {
                    List<CategoryTreeDto> children = buildBranch(category.getId(), byParent);
                    return fromEntity(category, children);
                })
                .toList();
    }

    private static CategoryTreeDto fromEntity(Category category, List<CategoryTreeDto> children) {
        return CategoryTreeDto.builder()
                .id(category.getId())
                .createdAt(category.getCreatedAt())
                .createdBy(category.getCreatedBy())
                .updatedAt(category.getUpdatedAt())
                .updatedBy(category.getUpdatedBy())
                .workspaceId(category.getWorkspaceId())
                .parentId(category.getParentId())
                .name(category.getName())
                .description(category.getDescription())
                .status(category.getStatus())
                .income(category.isIncome())
                .excludeFromBudget(category.isExcludeFromBudget())
                .excludeFromTotals(category.isExcludeFromTotals())
                .displayOrder(category.getDisplayOrder())
                .group(!children.isEmpty())
                .children(children)
                .build();
    }
}
