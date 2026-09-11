package com.example.SWP391_G2_SE2055_JV.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PaginationUtils {

    public static final int DEFAULT_PAGE      = 0;
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE     = 100;

    private PaginationUtils() {}

    public static Pageable of(Integer page, Integer size) {
        int p = (page != null && page >= 0) ? page : DEFAULT_PAGE;
        int s = (size != null && size > 0) ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        return PageRequest.of(p, s);
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = (page != null && page >= 0) ? page : DEFAULT_PAGE;
        int s = (size != null && size > 0) ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        return PageRequest.of(p, s, sort);
    }
}