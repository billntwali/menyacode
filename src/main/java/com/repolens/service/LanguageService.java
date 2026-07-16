package com.repolens.service;

import org.springframework.stereotype.Service;

@Service
public class LanguageService {
    public String detect(String path) {
        String name = path.toLowerCase();
        if (name.endsWith(".java")) return "Java";
        if (name.endsWith(".py")) return "Python";
        if (name.endsWith(".ts") || name.endsWith(".tsx")) return "TypeScript";
        if (name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".mjs")) return "JavaScript";
        if (name.endsWith(".go")) return "Go";
        if (name.endsWith(".rs")) return "Rust";
        if (name.endsWith(".rb")) return "Ruby";
        if (name.endsWith(".cs")) return "C#";
        if (name.endsWith(".c") || name.endsWith(".h") || name.endsWith(".cpp")) return "C/C++";
        if (name.endsWith(".swift")) return "Swift";
        if (name.endsWith(".kt") || name.endsWith(".kts")) return "Kotlin";
        if (name.endsWith(".html")) return "HTML";
        if (name.endsWith(".css") || name.endsWith(".scss")) return "CSS";
        if (name.endsWith(".json")) return "JSON";
        if (name.endsWith(".xml")) return "XML";
        if (name.endsWith(".yaml") || name.endsWith(".yml")) return "YAML";
        if (name.endsWith(".md")) return "Markdown";
        if (name.endsWith(".sh")) return "Shell";
        if (name.endsWith(".sql")) return "SQL";
        return "Text";
    }
}
