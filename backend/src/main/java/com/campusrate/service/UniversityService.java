package com.campusrate.service;

import com.campusrate.dto.UniversityDtos.UniversityDetail;
import com.campusrate.dto.UniversityDtos.UniversitySummary;
import com.campusrate.exception.ApiException;
import com.campusrate.model.University;
import com.campusrate.repository.UniversityRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UniversityService {

    private static final int MAX_SEARCH_RESULTS = 8;

    private final UniversityRepository universityRepository;
    private final SearchService searchService;

    public UniversityService(UniversityRepository universityRepository, SearchService searchService) {
        this.universityRepository = universityRepository;
        this.searchService = searchService;
    }

    /**
     * Autocomplete search. SearchService finds the best matching ids (typos allowed), then the
     * schools are loaded fresh from MySQL so review counts and scores are always current.
     */
    public List<UniversitySummary> search(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        List<Long> ids = searchService.search(query, MAX_SEARCH_RESULTS);
        Map<Long, University> byId = universityRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(University::getId, Function.identity()));
        // Keep the search ranking order (findAllById doesn't).
        return ids.stream()
                .filter(byId::containsKey)
                .map(id -> UniversitySummary.from(byId.get(id)))
                .toList();
    }

    /** Cached; ReviewService clears this cache whenever a review changes the school's numbers. */
    @Cacheable(cacheNames = "universityDetail", key = "#id")
    public UniversityDetail getById(Long id) {
        return UniversityDetail.from(findUniversity(id));
    }

    /** Up to 3 schools side by side, in the order asked for. */
    public List<UniversityDetail> compare(List<Long> ids) {
        List<Long> unique = ids.stream().distinct().limit(3).toList();
        if (unique.size() < 2) {
            throw ApiException.badRequest("Pick at least two universities to compare.");
        }
        Map<Long, University> byId = universityRepository.findAllById(unique).stream()
                .collect(Collectors.toMap(University::getId, Function.identity()));
        return unique.stream()
                .filter(byId::containsKey)
                .map(id -> UniversityDetail.from(byId.get(id)))
                .toList();
    }

    public List<String> countries() {
        return universityRepository.findAllCountries();
    }

    public University findUniversity(Long id) {
        return universityRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("University not found."));
    }

    /** Used by the "worth it" chart and fit matcher: every school that has reviews. */
    public List<University> reviewedUniversities() {
        return universityRepository.findByReviewCountGreaterThan(0).stream()
                .sorted(Comparator.comparing(University::getName))
                .toList();
    }
}
