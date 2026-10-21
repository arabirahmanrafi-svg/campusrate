package com.campusrate.controller;

import com.campusrate.dto.CommunityDtos.AnswerRequest;
import com.campusrate.dto.CommunityDtos.AnswerResponse;
import com.campusrate.dto.CommunityDtos.AnswerVoteResponse;
import com.campusrate.dto.CommunityDtos.QuestionDetail;
import com.campusrate.dto.CommunityDtos.QuestionRequest;
import com.campusrate.dto.CommunityDtos.QuestionSummary;
import com.campusrate.service.QuestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Q&A threads on each university. */
@RestController
@RequestMapping("/api")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    @GetMapping("/universities/{id}/questions")
    public List<QuestionSummary> list(@PathVariable Long id) {
        return questionService.list(id);
    }

    @PostMapping("/universities/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionSummary ask(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                               @Valid @RequestBody QuestionRequest request) {
        return questionService.ask(userId, id, request);
    }

    @GetMapping("/questions/{id}")
    public QuestionDetail detail(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        return questionService.detail(id, userId);
    }

    @PostMapping("/questions/{id}/answers")
    @ResponseStatus(HttpStatus.CREATED)
    public AnswerResponse answer(@AuthenticationPrincipal Long userId, @PathVariable Long id,
                                 @Valid @RequestBody AnswerRequest request) {
        return questionService.answer(userId, id, request);
    }

    @PostMapping("/answers/{id}/vote")
    public AnswerVoteResponse vote(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return questionService.toggleVote(userId, id);
    }
}
