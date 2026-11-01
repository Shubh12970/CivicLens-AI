package com.civiclens.common;

/** Validates page/size query parameters so clients cannot request unbounded pages. */
public final class PageParams {

    public static final int MAX_SIZE = 100;

    private PageParams() {
    }

    public static int page(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0 || page > 10_000) {
            throw ApiException.validation("page", "page must be between 0 and 10000.");
        }
        return page;
    }

    public static int size(Integer size, int defaultSize) {
        if (size == null) {
            return defaultSize;
        }
        if (size < 1 || size > MAX_SIZE) {
            throw ApiException.validation("size", "size must be between 1 and " + MAX_SIZE + ".");
        }
        return size;
    }
}
