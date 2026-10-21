package com.campusrate.service;

import com.campusrate.dto.CommunityDtos.CostRequest;
import com.campusrate.dto.CommunityDtos.CostSummary;
import com.campusrate.dto.CommunityDtos.CountryCount;
import com.campusrate.dto.CommunityDtos.InternationalSummary;
import com.campusrate.dto.CommunityDtos.LinkRequest;
import com.campusrate.dto.CommunityDtos.LinkResponse;
import com.campusrate.dto.UniversityDtos;
import com.campusrate.exception.ApiException;
import com.campusrate.model.University;
import com.campusrate.model.User;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** Everything on the "International" tab: international ratings, home countries, living costs and community links. */
@Service
public class InternationalService {

    private static final RowMapper<LinkResponse> LINK_ROW = (rs, n) -> new LinkResponse(
            rs.getLong("id"), rs.getLong("university_id"), rs.getString("kind"), rs.getString("name"),
            rs.getString("url"), rs.getString("description"), rs.getString("status"));

    /** One student's cost report, as stored. */
    record CostRow(int rent, int groceries, int transport, int phone, Integer tuitionPerYear) {
        int monthlyTotal() {
            return rent + groceries + transport + phone;
        }
    }

    private final JdbcClient jdbc;
    private final UniversityService universityService;
    private final AuthService authService;

    public InternationalService(JdbcClient jdbc, UniversityService universityService, AuthService authService) {
        this.jdbc = jdbc;
        this.universityService = universityService;
        this.authService = authService;
    }

    @Transactional(readOnly = true)
    public InternationalSummary summary(Long universityId) {
        University university = universityService.findUniversity(universityId);

        Map<String, Object> averages = jdbc.sql("""
                        select coalesce(avg(intl_support), 0) as intl_support, coalesce(avg(visa_help), 0) as visa_help,
                               coalesce(avg(job_availability), 0) as job_availability, coalesce(avg(welcoming), 0) as welcoming
                        from reviews
                        where university_id = :id and status = 'PUBLISHED' and intl_support is not null
                        """)
                .param("id", universityId).query().singleRow();

        // Only students who chose to show their country are counted.
        List<CountryCount> countries = jdbc.sql("""
                        select u.home_country, count(*) as review_count
                        from reviews r join users u on u.id = r.user_id
                        where r.university_id = :id and r.status = 'PUBLISHED'
                          and u.show_country = true and u.home_country is not null
                        group by u.home_country
                        order by review_count desc, u.home_country
                        """)
                .param("id", universityId)
                .query((rs, n) -> new CountryCount(rs.getString("home_country"), rs.getLong("review_count")))
                .list();

        List<LinkResponse> links = jdbc.sql("""
                        select * from community_links where university_id = :id and status = 'APPROVED'
                        order by kind, name
                        """)
                .param("id", universityId).query(LINK_ROW).list();

        return new InternationalSummary(
                university.getIntlReviewCount(), UniversityDtos.round(university.getAvgInternational()),
                number(averages, "intl_support"), number(averages, "visa_help"),
                number(averages, "job_availability"), number(averages, "welcoming"),
                countries, costSummary(universityId), links);
    }

    public CostSummary costSummary(Long universityId) {
        List<CostRow> rows = jdbc.sql("""
                        select rent, groceries, transport, phone, tuition_per_year
                        from cost_reports where university_id = :id
                        """)
                .param("id", universityId)
                .query((rs, n) -> new CostRow(rs.getInt("rent"), rs.getInt("groceries"), rs.getInt("transport"),
                        rs.getInt("phone"), (Integer) rs.getObject("tuition_per_year", Integer.class)))
                .list();
        return summarize(rows);
    }

    /** Medians of each cost. A median ignores a few extreme answers, unlike an average. */
    static CostSummary summarize(List<CostRow> rows) {
        if (rows.isEmpty()) {
            return new CostSummary(0, null, null, null, null, null, null, null);
        }
        Integer monthly = median(rows, CostRow::monthlyTotal);
        List<CostRow> withTuition = rows.stream().filter(r -> r.tuitionPerYear() != null).toList();
        Integer tuition = withTuition.isEmpty() ? null : median(withTuition, CostRow::tuitionPerYear);
        Integer yearly = monthly * 12 + (tuition == null ? 0 : tuition);
        return new CostSummary(rows.size(),
                median(rows, CostRow::rent), median(rows, CostRow::groceries),
                median(rows, CostRow::transport), median(rows, CostRow::phone),
                monthly, tuition, yearly);
    }

    static Integer median(List<CostRow> rows, ToIntFunction<CostRow> field) {
        List<Integer> values = new ArrayList<>(rows.stream().map(field::applyAsInt).toList());
        Collections.sort(values);
        int middle = values.size() / 2;
        return values.size() % 2 == 1
                ? values.get(middle)
                : Math.round((values.get(middle - 1) + values.get(middle)) / 2.0f);
    }

    /** Each student can report costs once per school; reporting again replaces their old numbers. */
    @Transactional
    public CostSummary reportCosts(Long userId, Long universityId, CostRequest request) {
        User user = authService.findUser(userId);
        universityService.findUniversity(universityId);
        jdbc.sql("""
                        insert into cost_reports (university_id, user_id, rent, groceries, transport, phone, tuition_per_year)
                        values (:universityId, :userId, :rent, :groceries, :transport, :phone, :tuition)
                        on duplicate key update rent = values(rent), groceries = values(groceries),
                            transport = values(transport), phone = values(phone), tuition_per_year = values(tuition_per_year)
                        """)
                .param("universityId", universityId).param("userId", user.getId())
                .param("rent", request.rent()).param("groceries", request.groceries())
                .param("transport", request.transport()).param("phone", request.phone())
                .param("tuition", request.tuitionPerYear())
                .update();
        return costSummary(universityId);
    }

    /** Suggest a cultural food spot or student association. Shown after an admin approves it. */
    @Transactional
    public LinkResponse suggestLink(Long userId, Long universityId, LinkRequest request) {
        User user = authService.findUser(userId);
        universityService.findUniversity(universityId);
        String status = user.isAdmin() ? "APPROVED" : "PENDING";
        String url = request.url() == null || request.url().isBlank() ? null : request.url().trim();
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();

        jdbc.sql("""
                        insert into community_links (university_id, kind, name, url, description, status, created_by)
                        values (:universityId, :kind, :name, :url, :description, :status, :userId)
                        """)
                .param("universityId", universityId).param("kind", request.kind()).param("name", request.name().trim())
                .param("url", url).param("description", description).param("status", status)
                .param("userId", user.getId())
                .update();

        return jdbc.sql("select * from community_links where created_by = :userId order by id desc limit 1")
                .param("userId", user.getId()).query(LINK_ROW).single();
    }

    public List<LinkResponse> pendingLinks() {
        return jdbc.sql("select * from community_links where status = 'PENDING' order by created_at").query(LINK_ROW).list();
    }

    @Transactional
    public void setLinkStatus(Long linkId, String status) {
        int updated = jdbc.sql("update community_links set status = :status where id = :id")
                .param("status", status).param("id", linkId).update();
        if (updated == 0) {
            throw ApiException.notFound("Link not found.");
        }
    }

    private static double number(Map<String, Object> row, String column) {
        Object value = row.get(column);
        return value == null ? 0 : UniversityDtos.round(((Number) value).doubleValue());
    }
}
