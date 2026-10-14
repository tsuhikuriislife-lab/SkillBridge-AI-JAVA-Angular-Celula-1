package com.riwi.skillbridge.infrastructure.adapter.in.rest;

/**
 * Límites globales de paginación (plan §13): tamaño entre 10 y 50, página >= 0.
 * Uso: PageRequestUtils.clampPage(page) / clampSize(size) en todo controller.
 */
public final class PageRequestUtils {

    private static final int MIN_SIZE = 10;
    private static final int MAX_SIZE = 50;

    private PageRequestUtils() {}

    public static int clampPage(int page) { return Math.max(page, 0); }

    public static int clampSize(int size) { return Math.min(Math.max(size, MIN_SIZE), MAX_SIZE); }
}
