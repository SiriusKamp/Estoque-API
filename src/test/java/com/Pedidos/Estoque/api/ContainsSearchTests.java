package com.Pedidos.Estoque.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ContainsSearchTests {
    @Test
    void searchesAnyPartOfTextWithoutInterpretingUserWildcards() {
        assertEquals("%coca!%!_!!%", ContainsSearch.pattern("  CoCa%_!  "));
        assertEquals("%vodka%", ContainsSearch.pattern("Vodka"));
    }

    @Test
    void rejectsUnboundedSearchTerms() {
        assertThrows(ResponseStatusException.class,
            () -> ContainsSearch.pattern("a".repeat(201)));
    }
}
