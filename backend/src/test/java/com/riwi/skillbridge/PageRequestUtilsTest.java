package com.riwi.skillbridge;

import com.riwi.skillbridge.infrastructure.adapter.in.rest.PageRequestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageRequestUtilsTest {

    @Test
    void page_neverNegative() {
        assertEquals(0, PageRequestUtils.clampPage(-5));
        assertEquals(3, PageRequestUtils.clampPage(3));
    }

    @Test
    void size_clampedBetween10And50() {
        assertEquals(10, PageRequestUtils.clampSize(3));
        assertEquals(50, PageRequestUtils.clampSize(999));
        assertEquals(20, PageRequestUtils.clampSize(20));
    }
}
