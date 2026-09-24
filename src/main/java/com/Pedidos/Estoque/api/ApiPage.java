package com.Pedidos.Estoque.api;

import java.util.List;

/** Pagination shape used by the Vite useProducts/useInventoryRows hooks. */
public record ApiPage<T>(List<T> rows, long total, int page, int size) { }
