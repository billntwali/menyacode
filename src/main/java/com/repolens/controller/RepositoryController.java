package com.repolens.controller;

import com.repolens.dto.ExploreRequest;
import com.repolens.dto.ExploreResponse;
import com.repolens.dto.SummaryRequest;
import com.repolens.dto.SummaryResponse;
import com.repolens.service.RepositoryService;
import com.repolens.service.SummaryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/repository")
public class RepositoryController {

    private final RepositoryService repositoryService;
    private final SummaryService    summaryService;

    public RepositoryController(RepositoryService repositoryService,
                                SummaryService summaryService) {
        this.repositoryService = repositoryService;
        this.summaryService    = summaryService;
    }

    @PostMapping("/explore")
    public ExploreResponse explore(@Valid @RequestBody ExploreRequest request) {
        return repositoryService.explore(request);
    }

    @PostMapping("/summary")
    public SummaryResponse summary(@Valid @RequestBody SummaryRequest request) {
        return summaryService.summarize(request);
    }
}
