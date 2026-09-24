package com.Pedidos.Estoque.api;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(DataAccessException.class)
    ProblemDetail databaseUnavailable(DataAccessException error) {
        SQLException sql = sqlCause(error);
        if (sql != null && sql.getSQLState() != null) {
            HttpStatus status = switch (sql.getSQLState()) {
                case "23505", "23514", "23503" -> HttpStatus.CONFLICT;
                case "22003", "22007", "22023", "22P02", "23502" -> HttpStatus.BAD_REQUEST;
                case "42501" -> HttpStatus.FORBIDDEN;
                case "P0001" -> HttpStatus.UNPROCESSABLE_ENTITY;
                default -> HttpStatus.SERVICE_UNAVAILABLE;
            };
            if (status != HttpStatus.SERVICE_UNAVAILABLE) {
                ProblemDetail problem = ProblemDetail.forStatus(status);
                problem.setTitle("Operação de estoque não aplicada");
                problem.setDetail(switch (sql.getSQLState()) {
                    case "23505" -> "Referência, código ou lote já existente; confira os dados enviados.";
                    case "23514" -> "Saldo insuficiente ou dados incompatíveis com este estoque.";
                    case "23503" -> "Registro relacionado não encontrado ou ainda em uso.";
                    case "42501" -> "Acesso ao estoque negado.";
                    case "P0001" -> "A operação não atende às regras do estoque.";
                    case "23502" -> "Informe todos os dados obrigatórios.";
                    default -> "Dados inválidos para a operação.";
                });
                return problem;
            }
        }
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Estoque indisponível");
        problem.setDetail("Não foi possível acessar o estoque agora.");
        return problem;
    }

    private static SQLException sqlCause(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql) return sql;
        }
        return null;
    }
}
