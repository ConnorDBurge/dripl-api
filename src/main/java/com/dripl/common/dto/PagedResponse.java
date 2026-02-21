package com.dripl.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public record PagedResponse<T>(PageInfo page, List<T> content) {

    public record PageInfo(int number, int size, long totalElements, int totalPages) {}

    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                new PageInfo(
                        page.getNumber(),
                        page.getSize(),
                        page.getTotalElements(),
                        page.getTotalPages()
                ),
                page.getContent()

        );
    }
}
