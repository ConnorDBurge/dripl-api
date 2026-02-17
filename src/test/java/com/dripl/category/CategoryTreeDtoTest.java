package com.dripl.category;

import com.dripl.category.dto.CategoryTreeDto;
import com.dripl.category.entity.Category;
import com.dripl.common.enums.Status;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTreeDtoTest {

    private final UUID workspaceId = UUID.randomUUID();

    private Category buildCategory(UUID id, String name, UUID parentId) {
        return Category.builder()
                .id(id)
                .workspaceId(workspaceId)
                .parentId(parentId)
                .name(name)
                .status(Status.ACTIVE)
                .build();
    }

    @Test
    void buildTree_emptyList() {
        List<CategoryTreeDto> tree = CategoryTreeDto.buildTree(List.of());

        assertThat(tree).isEmpty();
    }

    @Test
    void buildTree_rootsOnly() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<CategoryTreeDto> tree = CategoryTreeDto.buildTree(List.of(
                buildCategory(id1, "Food", null),
                buildCategory(id2, "Bills", null)
        ));

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getName()).isEqualTo("Bills");
        assertThat(tree.get(1).getName()).isEqualTo("Food");
        assertThat(tree.get(0).isGroup()).isFalse();
        assertThat(tree.get(0).getChildren()).isEmpty();
    }

    @Test
    void buildTree_parentWithChildren() {
        UUID parentId = UUID.randomUUID();
        UUID child1Id = UUID.randomUUID();
        UUID child2Id = UUID.randomUUID();
        List<CategoryTreeDto> tree = CategoryTreeDto.buildTree(List.of(
                buildCategory(parentId, "Food", null),
                buildCategory(child1Id, "Groceries", parentId),
                buildCategory(child2Id, "Dining Out", parentId)
        ));

        assertThat(tree).hasSize(1);
        CategoryTreeDto food = tree.get(0);
        assertThat(food.getName()).isEqualTo("Food");
        assertThat(food.isGroup()).isTrue();
        assertThat(food.getChildren()).hasSize(2);
        assertThat(food.getChildren().get(0).getName()).isEqualTo("Dining Out");
        assertThat(food.getChildren().get(1).getName()).isEqualTo("Groceries");
    }

    @Test
    void buildTree_mixedRootsAndChildren() {
        UUID foodId = UUID.randomUUID();
        UUID groceriesId = UUID.randomUUID();
        UUID billsId = UUID.randomUUID();
        List<CategoryTreeDto> tree = CategoryTreeDto.buildTree(List.of(
                buildCategory(foodId, "Food", null),
                buildCategory(groceriesId, "Groceries", foodId),
                buildCategory(billsId, "Bills", null)
        ));

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getName()).isEqualTo("Bills");
        assertThat(tree.get(0).isGroup()).isFalse();
        assertThat(tree.get(1).getName()).isEqualTo("Food");
        assertThat(tree.get(1).isGroup()).isTrue();
        assertThat(tree.get(1).getChildren()).hasSize(1);
    }

    @Test
    void buildTree_preservesFields() {
        UUID id = UUID.randomUUID();
        Category category = Category.builder()
                .id(id)
                .workspaceId(workspaceId)
                .name("Income")
                .description("All income sources")
                .status(Status.ACTIVE)
                .income(true)
                .excludeFromBudget(true)
                .excludeFromTotals(false)
                .build();

        List<CategoryTreeDto> tree = CategoryTreeDto.buildTree(List.of(category));

        CategoryTreeDto dto = tree.get(0);
        assertThat(dto.getId()).isEqualTo(id);
        assertThat(dto.getWorkspaceId()).isEqualTo(workspaceId);
        assertThat(dto.getName()).isEqualTo("Income");
        assertThat(dto.getDescription()).isEqualTo("All income sources");
        assertThat(dto.isIncome()).isTrue();
        assertThat(dto.isExcludeFromBudget()).isTrue();
        assertThat(dto.isExcludeFromTotals()).isFalse();
    }
}
