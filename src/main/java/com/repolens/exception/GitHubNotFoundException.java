package com.repolens.exception;

public class GitHubNotFoundException extends RuntimeException {
    public GitHubNotFoundException(String message) {
        super(message);
    }
}
