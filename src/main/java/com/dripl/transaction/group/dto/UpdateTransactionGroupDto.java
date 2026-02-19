package com.dripl.transaction.group.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTransactionGroupDto {

    @Size(max = 255, message = "Name must be at most 255 characters")
    private String name;

    private UUID categoryId;
    @Getter
    private boolean categoryIdSpecified;

    @Size(max = 500, message = "Notes must be at most 500 characters")
    private String notes;
    @Getter
    private boolean notesSpecified;

    private Set<UUID> tagIds;
    @Getter
    private boolean tagIdsSpecified;

    private Set<UUID> transactionIds;

    @JsonSetter("categoryId")
    public void assignCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
        this.categoryIdSpecified = true;
    }

    @JsonSetter("notes")
    public void assignNotes(String notes) {
        this.notes = notes;
        this.notesSpecified = true;
    }

    @JsonSetter("tagIds")
    public void assignTagIds(Set<UUID> tagIds) {
        this.tagIds = tagIds;
        this.tagIdsSpecified = true;
    }
}
