package com.dripl.transaction.group.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTransactionGroupInput {

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

    public void setCategoryId(UUID categoryId) {
        this.categoryId = categoryId;
        this.categoryIdSpecified = true;
    }

    public void setNotes(String notes) {
        this.notes = notes;
        this.notesSpecified = true;
    }

    public void setTagIds(Set<UUID> tagIds) {
        this.tagIds = tagIds;
        this.tagIdsSpecified = true;
    }
}
