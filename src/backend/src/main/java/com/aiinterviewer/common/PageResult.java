package com.aiinterviewer.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private java.util.List<T> list;
    private long total;
    private int page;
    private int pageSize;

    public static <T> PageResult<T> of(java.util.List<T> list, long total, int page, int pageSize) {
        return new PageResult<>(list, total, page, pageSize);
    }
}
