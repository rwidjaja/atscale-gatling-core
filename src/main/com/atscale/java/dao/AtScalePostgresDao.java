package com.atscale.java.dao;

import java.sql.*;
import java.util.Properties;

import com.atscale.java.utils.PropertiesManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;

@SuppressWarnings("unused")
public class AtScalePostgresDao {
    private static final Logger LOGGER = LoggerFactory.getLogger(AtScalePostgresDao.class);
    
    public enum QueryLanguage {
        SQL("pgsql"),
        XMLA("analysis"),
        INSTALLER_SQL("sql");

        private final String value;

        QueryLanguage(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    private final String url;
    private final String user;
    private final String password;
    private static AtScalePostgresDao INSTANCE;
    
    // FIX: Use consistent schema - choose one based on your environment
    private static final String INSTALLER_QUERY = """
            SELECT
                        q.service,
                        q.query_language,
                        q.query_text as inbound_text,
                        MAX(s.subquery_text) as outbound_text,
                        p.cube_name,
                        p.project_id,
                        case when MAX(s.subquery_text) like '%as_agg_%' then true else false end as used_agg,
                        COUNT(*)                             AS num_times,
                        AVG(r.finished - p.planning_started) AS elasped_time_in_seconds,
                        AVG(r.result_size)                   AS avg_result_size
                    FROM
                        atscale.queries q
                    INNER JOIN
                        atscale.query_results r
                    ON
                        q.query_id=r.query_id
                    INNER JOIN
                        atscale.queries_planned p
                    ON
                        q.query_id=p.query_id
                    INNER JOIN
                        atscale.subqueries s
                    ON
                        q.query_id=s.query_id
                    WHERE
                        q.query_language = ?
                    AND p.planning_started > current_timestamp - interval '60 day'
                    and p.cube_name = ?
                    AND q.service = 'user-query'
                    AND r.succeeded = true
                    AND LENGTH(q.query_text) > 100
                    AND q.query_text NOT LIKE '/* Virtual query to get the members of a level */%'
                    AND q.query_text NOT LIKE '-- statement does not return rows%'
                    GROUP BY
                        1, 2, 3, 5, 6
                    HAVING COUNT(*) >= 1
                    ORDER BY 3
    """;

    private static final String CONTAINER_QUERY = """
            SELECT
                        q.service,
                        q.query_language,
                        q.query_text as inbound_text,
                        MAX(s.subquery_text) as outbound_text,
                        p.cube_name,
                        p.project_id,
                        case when MAX(s.subquery_text) like '%as_agg_%' then true else false end as used_agg,
                        COUNT(*)                             AS num_times,
                        AVG(r.finished - p.planning_started) AS elasped_time_in_seconds,
                        AVG(r.result_size)                   AS avg_result_size
                    FROM
                        atscale.engine.queries q
                    INNER JOIN
                        atscale.engine.query_results r
                    ON
                        q.query_id=r.query_id
                    INNER JOIN
                        atscale.engine.queries_planned p
                    ON
                        q.query_id=p.query_id
                    INNER JOIN
                        atscale.engine.subqueries s
                    ON
                        q.query_id=s.query_id
                    WHERE
                        q.query_language = ?
                    AND p.planning_started > current_timestamp - interval '60 day'
                    and p.cube_name = ?
                    AND q.service = 'user-query'
                    AND r.succeeded = true
                    AND LENGTH(q.query_text) > 100
                    AND q.query_text NOT LIKE '/* Virtual query to get the members of a level */%'
                    AND q.query_text NOT LIKE '-- statement does not return rows%'
                    GROUP BY
                        1, 2, 3, 5, 6
                    HAVING COUNT(*) >= 1
                    ORDER BY 3
    """;

    private AtScalePostgresDao() {
        this.url = PropertiesManager.getAtScalePostgresURL();
        this.user = PropertiesManager.getAtScalePostgresUser();
        this.password = PropertiesManager.getAtScalePostgresPassword();
        LOGGER.info("Initializing AtScalePostgresDao with URL: {}", this.url);
        LOGGER.info("Database user: {}", this.user);
        
        // Test connection on initialization
        testConnection();
    }

    public static AtScalePostgresDao getInstance() {
        if (INSTANCE == null) {
            synchronized (AtScalePostgresDao.class) {
                if (INSTANCE == null) {
                    INSTANCE = new AtScalePostgresDao();
                }
            }
        }
        return INSTANCE;
    }

    private Connection getConnection() throws SQLException {
        try {
            // Ensure PostgreSQL driver is loaded
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("PostgreSQL JDBC Driver not found", e);
        }
        
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        props.setProperty("currentSchema", "engine");
        props.setProperty("connectTimeout", "30");
        props.setProperty("socketTimeout", "60");
        props.setProperty("loginTimeout", "30");
        
        LOGGER.debug("Attempting database connection to: {}", url);
        Connection connection = DriverManager.getConnection(url, props);
        
        // Test the connection
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SELECT 1");
        }
        
        LOGGER.debug("Database connection established successfully");
        return connection;
    }

    private void testConnection() {
        try (Connection conn = getConnection()) {
            LOGGER.info("✅ Database connection test successful");
            
            // Detect which schema we should use
            detectSchema();
            
        } catch (SQLException e) {
            LOGGER.error("❌ Database connection test FAILED: {}", e.getMessage());
            throw new RuntimeException("Cannot connect to AtScale PostgreSQL database", e);
        }
    }

    private void detectSchema() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            // Try to detect which schema exists
            boolean hasEngineSchema = false;
            boolean hasPublicSchema = false;
            
            try (ResultSet rs = stmt.executeQuery(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'engine' AND table_name = 'queries')")) {
                if (rs.next()) {
                    hasEngineSchema = rs.getBoolean(1);
                }
            }
            
            try (ResultSet rs = stmt.executeQuery(
                "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'queries')")) {
                if (rs.next()) {
                    hasPublicSchema = rs.getBoolean(1);
                }
            }
            
            LOGGER.info("Schema detection - Engine schema: {}, Public schema: {}", hasEngineSchema, hasPublicSchema);
            
        } catch (SQLException e) {
            LOGGER.warn("Could not detect schema automatically: {}", e.getMessage());
        }
    }

    public List<QueryHistoryDto> getQueryHistory(QueryLanguage queryLanguage, String cubeName) {
        LOGGER.info("Getting query history for language: {}, cube: {}", queryLanguage, cubeName);
        
        // FIX: Choose the appropriate query based on environment
        String effectiveQuery = chooseQuery();
        List<QueryHistoryDto> queryHistory;
        
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(effectiveQuery)) {
            
            stmt.setString(1, queryLanguage.getValue());
            stmt.setString(2, cubeName);

            LOGGER.debug("Executing query with params: language={}, cube={}", queryLanguage.getValue(), cubeName);
            
            try (ResultSet resultSet = stmt.executeQuery()) {
                queryHistory = extractResults(resultSet);
            }
            
            LOGGER.info("Retrieved {} query history records", queryHistory.size());
            
        } catch (SQLException e) {
            LOGGER.error("Error executing query history extraction", e);
            throw new RuntimeException("Error fetching query history from the AtScale Postgres database", e);
        }
        
        if (queryHistory.isEmpty()) {
            String debugSql = buildLoggingSql(effectiveQuery, new String[]{queryLanguage.getValue(), cubeName});
            LOGGER.warn("No results returned from the query. QueryExtract SQL: \n{}", debugSql);
            logModelConfiguration(effectiveQuery);
        }
        
        return queryHistory;
    }

    public List<QueryHistoryDto> getQueryHistory(String userQuery, String... params) {
        LOGGER.info("Getting query history with custom query, params: {}", (Object) params);
        
        List<QueryHistoryDto> queryHistory;
        try (Connection conn = getConnection();
            PreparedStatement stmt = conn.prepareStatement(userQuery)) {
            
            for(int i = 1; i <= params.length; i++) {
                String param = params[i - 1];
                stmt.setString(i, param);
                LOGGER.debug("Set parameter {}: {}", i, param);
            }

            try (ResultSet resultSet = stmt.executeQuery()) {
                queryHistory = extractResults(resultSet);
            }
            
            LOGGER.info("Retrieved {} query history records with custom query", queryHistory.size());
            
        } catch (SQLException e) {
            LOGGER.error("Error executing custom query history extraction", e);
            throw new RuntimeException("Error fetching query history from the AtScale Postgres database.", e);
        }
        
        if (queryHistory.isEmpty()) {
            String debugSql = buildLoggingSql(userQuery, params);
            LOGGER.warn("No results returned from custom query. QueryExtract SQL: \n{}", debugSql);
            logModelConfiguration(userQuery);
        }
        
        return queryHistory;
    }

    private String chooseQuery() {
        // FIX: Determine which query to use based on environment or configuration
        // You can make this configurable via properties
        String queryType = System.getProperty("atscale.schema.type", "installer");
        
        if ("container".equalsIgnoreCase(queryType)) {
            LOGGER.info("Using CONTAINER schema query (atscale.engine.* tables)");
            return CONTAINER_QUERY;
        } else {
            LOGGER.info("Using INSTALLER schema query (atscale.* tables)");
            return INSTALLER_QUERY;
        }
    }

    private String buildLoggingSql(String sql, String[] params) {
        if (params == null || params.length == 0) return sql;
        StringBuilder sb = new StringBuilder();
        int paramIndex = 0;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '?' && paramIndex < params.length) {
                sb.append('\'').append(params[paramIndex].replace("'", "''")).append('\'');
                paramIndex++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private List<QueryHistoryDto> extractResults(ResultSet resultSet) throws SQLException {
        List<QueryHistoryDto> queryHistory = new ArrayList<>();
        int queryNameIndex = 0;
        
        while (resultSet.next()) {
            QueryHistoryDto dto = new QueryHistoryDto();
            dto.setQueryName(String.format("Query %s", ++queryNameIndex));
            dto.setService(resultSet.getString("service"));
            dto.setQueryLanguage(resultSet.getString("query_language"));
            dto.setInboundText(resultSet.getString("inbound_text"));
            dto.setOutboundText(resultSet.getString("outbound_text"));
            dto.setCubeName(resultSet.getString("cube_name"));
            dto.setProjectId(resultSet.getString("project_id"));
            dto.setAggregateUsed(resultSet.getBoolean("used_agg"));
            dto.setNumTimes(resultSet.getInt("num_times"));
            
            // FIX: Handle timestamp conversion properly
            Timestamp elapsedTime = resultSet.getTimestamp("elasped_time_in_seconds");
            dto.setElapsedTimeInSeconds(elapsedTime);
            
            dto.setAvgResultSetSize(resultSet.getInt("avg_result_size"));
            queryHistory.add(dto);
        }
        
        return queryHistory;
    }

    private void logModelConfiguration(String query) {
        String domainQuery;
        if (query != null && query.contains("atscale.engine.queries")) {
            domainQuery = """
                select DISTINCT q.query_language, p.cube_name
                FROM
                    atscale.engine.queries q
                INNER JOIN
                    atscale.engine.queries_planned p
                ON
                    q.query_id=p.query_id
                where cube_name is not null
                and query_language is not null
                and q.query_language != 'none'
                """;
        } else if (query != null && query.contains("atscale.queries")) {
            domainQuery = """
                select DISTINCT q.query_language, p.cube_name
                FROM
                    atscale.queries q
                INNER JOIN
                    atscale.queries_planned p
                ON
                    q.query_id=p.query_id
                where cube_name is not null
                and query_language is not null
                and q.query_language != 'none'
                """;
        } else {
            LOGGER.warn("Unknown schema in query, cannot provide model configuration details");
            return;
        }

        LOGGER.info("Checking available models and query languages...");
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(domainQuery);
             ResultSet resultSet = stmt.executeQuery()) {
            
            LOGGER.info("Available Model and Query Language combinations:");
            boolean foundAny = false;
            while (resultSet.next()) {
                String queryLanguage = resultSet.getString("query_language");
                String model = resultSet.getString("cube_name");
                LOGGER.info("  - Model: {}, Query Language: {}", model, queryLanguage);
                foundAny = true;
            }
            
            if (!foundAny) {
                LOGGER.warn("No models found in the database!");
            }
            
        } catch (SQLException e) {
            LOGGER.error("Error fetching model configuration", e);
        }
    }
}