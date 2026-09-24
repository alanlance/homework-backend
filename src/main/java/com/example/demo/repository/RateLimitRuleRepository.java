package com.example.demo.repository;

import com.example.demo.domain.RateLimitRule;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class RateLimitRuleRepository {

    private static final RowMapper<RateLimitRule> ROW_MAPPER = RateLimitRuleRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public RateLimitRuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<RateLimitRule> findByApiKey(String apiKey) {
        return jdbcTemplate.query("""
                SELECT id, api_key, request_limit, window_seconds, created_at, updated_at
                FROM rate_limit_rules
                WHERE api_key = ?
                """, ROW_MAPPER, apiKey).stream().findFirst();
    }

    public RateLimitRule insert(String apiKey, int requestLimit, int windowSeconds) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO rate_limit_rules (api_key, request_limit, window_seconds)
                    VALUES (?, ?, ?)
                    """, new String[] {"id"});
            statement.setString(1, apiKey);
            statement.setInt(2, requestLimit);
            statement.setInt(3, windowSeconds);
            return statement;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow();
    }

    public RateLimitRule update(long id, int requestLimit, int windowSeconds) {
        jdbcTemplate.update("""
                UPDATE rate_limit_rules
                SET request_limit = ?, window_seconds = ?
                WHERE id = ?
                """, requestLimit, windowSeconds, id);
        return findById(id).orElseThrow();
    }

    public List<RateLimitRule> findPage(long offset, int size) {
        return jdbcTemplate.query("""
                SELECT id, api_key, request_limit, window_seconds, created_at, updated_at
                FROM rate_limit_rules
                ORDER BY id
                LIMIT ? OFFSET ?
                """, ROW_MAPPER, size, offset);
    }

    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rate_limit_rules", Long.class);
        return count == null ? 0 : count;
    }

    public boolean deleteByApiKey(String apiKey) {
        return jdbcTemplate.update("DELETE FROM rate_limit_rules WHERE api_key = ?", apiKey) > 0;
    }

    private Optional<RateLimitRule> findById(long id) {
        return jdbcTemplate.query("""
                SELECT id, api_key, request_limit, window_seconds, created_at, updated_at
                FROM rate_limit_rules
                WHERE id = ?
                """, ROW_MAPPER, id).stream().findFirst();
    }

    private static RateLimitRule mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RateLimitRule(
                resultSet.getLong("id"),
                resultSet.getString("api_key"),
                resultSet.getInt("request_limit"),
                resultSet.getInt("window_seconds"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }
}
