package com.example.webapp.catalog;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read-only employee field, derived from current source records without a stale stored copy. */
@Component
public class EmployeeAssessmentScores {
    private final NamedParameterJdbcTemplate jdbc;

    public EmployeeAssessmentScores(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void populate(List<Map<String, Object>> employees) {
        if (employees.isEmpty()) return;
        var ids = employees.stream().map(employee -> ((Number) employee.get("id")).longValue()).toList();
        var totals = new HashMap<Long, BigDecimal>();
        // All formula inputs are read in one statement, so they share a database snapshot.
        // Batch retrieval also avoids one query per employee or per training record.
        var inputs = jdbc.query("""
            SELECT latest.employee_id, latest.score AS base_score,
                   training.score AS training_score, training.impact_coefficient
            FROM (
                SELECT employee_id, score,
                       ROW_NUMBER() OVER (PARTITION BY employee_id ORDER BY assessed_on DESC, id DESC) AS recency
                FROM employee_scores WHERE employee_id IN (:ids)
            ) latest
            LEFT JOIN employee_trainings training ON training.employee_id = latest.employee_id
                 AND training.completion_date <= CURRENT_DATE
            WHERE latest.recency = 1
            """, Map.of("ids", ids), (rs, n) -> new Input(rs.getLong("employee_id"),
                rs.getBigDecimal("base_score"), rs.getBigDecimal("training_score"), rs.getBigDecimal("impact_coefficient")));
        for (var input : inputs) {
            var total = totals.getOrDefault(input.employeeId(), input.baseScore());
            // Both inputs are stored on a 0..100 scale. Missing optional values contribute zero.
            if (input.trainingScore() != null && input.impactCoefficient() != null) {
                var impact = input.trainingScore().movePointLeft(2).multiply(input.impactCoefficient().movePointLeft(2));
                total = total.multiply(BigDecimal.ONE.add(impact));
            }
            totals.put(input.employeeId(), total);
        }
        for (var employee : employees) {
            var total = totals.get(((Number) employee.get("id")).longValue());
            // Round only the final result. No assessment is distinct from an assessment of zero.
            employee.put("assessmentScore", total == null ? null : total.setScale(2, RoundingMode.HALF_UP));
        }
    }

    private record Input(long employeeId, BigDecimal baseScore, BigDecimal trainingScore, BigDecimal impactCoefficient) {}
}
