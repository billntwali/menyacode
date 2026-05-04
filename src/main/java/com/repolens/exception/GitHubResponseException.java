package com.repolens.exception;

public class GitHubResponseException extends RuntimeException {
    public GitHubResponseException(String message) {
        super(message);
    }
}
