package com.Pedidos.Estoque.api;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Same literal substring semantics as monolith src/lib/inventorySearch.ts#containsPattern. */
final class ContainsSearch {
    private ContainsSearch() { }

    static String pattern(String input) {
        String term = input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
        if (term.length() > 200) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A busca deve ter até 200 caracteres.");
        }
        return "%" + term.replace("!", "!!").replace("%", "!%")
            .replace("_", "!_") + "%";
    }
}
