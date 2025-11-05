package com.atscale.java.executors;

import com.atscale.java.dao.AtScalePostgresDao;
import com.atscale.java.utils.PropertiesManager;
import com.atscale.java.utils.QueryHistoryFileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class InstallerVerQueryExtractExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(InstallerVerQueryExtractExecutor.class);
        // Resolve APP_HOME from system property or environment, default to "."
    private static final String APP_HOME =
            System.getProperty("app.home",
                    System.getenv().getOrDefault("APP_HOME", "."));

    private static final String QUERIES_DIR = APP_HOME + "/queries";

    public static void main(String[] args) {
        InstallerVerQueryExtractExecutor executor = new InstallerVerQueryExtractExecutor();
        executor.execute();
    }

    protected void execute() {
        System.out.println("=== InstallerVerQueryExtractExecutor Starting ===");
        writeDebugFile("=== InstallerVerQueryExtractExecutor Starting ===\n");
        
        // Ensure /app/queries directory exists before starting
        ensureQueriesDirectoryExists();
        
        System.out.println("Properties loaded successfully, checking models...");
        writeDebugFile("Properties loaded successfully, checking models...\n");
        
        try {
            List<String> models = PropertiesManager.getAtScaleModels();
            System.out.println("Models found: " + models);
            writeDebugFile("Models found: " + models + "\n");
            
            if (models == null || models.isEmpty()) {
                System.out.println("ERROR: No models found in configuration!");
                writeDebugFile("ERROR: No models found in configuration!\n");
                return;
            }
            
            for(String model : models) {
                System.out.println("Processing model: " + model);
                writeDebugFile("Processing model: " + model + "\n");
                
                // Test database connectivity for this model
                try {
                    String jdbcUrl = PropertiesManager.getAtScaleJdbcConnection(model);
                    String jdbcUser = PropertiesManager.getAtScaleJdbcUserName(model);
                    System.out.println("  JDBC URL: " + jdbcUrl);
                    System.out.println("  JDBC User: " + jdbcUser);
                    writeDebugFile("  JDBC URL: " + jdbcUrl + "\n");
                    writeDebugFile("  JDBC User: " + jdbcUser + "\n");
                    
                    String xmlaUrl = PropertiesManager.getAtScaleXmlaConnection(model);
                    System.out.println("  XMLA URL: " + xmlaUrl);
                    writeDebugFile("  XMLA URL: " + xmlaUrl + "\n");
                    
                } catch (Exception e) {
                    System.out.println("  ERROR getting connection info for model " + model + ": " + e.getMessage());
                    writeDebugFile("  ERROR getting connection info for model " + model + ": " + e.getMessage() + "\n");
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            System.out.println("FATAL ERROR in setup: " + e.getMessage());
            writeDebugFile("FATAL ERROR in setup: " + e.getMessage() + "\n");
            e.printStackTrace();
            return; // Don't continue if setup failed
        }
        
        LOGGER.info("QueryExtractExecutor started.");
        writeDebugFile("QueryExtractExecutor started.\n");
        List<String> models = PropertiesManager.getAtScaleModels();

        for(String model : models) {
            LOGGER.info("Processing model: {}", model);
            writeDebugFile("Processing model: " + model + "\n");
            
            try {
                System.out.println("Starting JDBC query caching for model: " + model);
                writeDebugFile("Starting JDBC query caching for model: " + model + "\n");
                cacheJdbcQueries(model);
                System.out.println("JDBC query caching completed for model: " + model);
                writeDebugFile("JDBC query caching completed for model: " + model + "\n");
            } catch (Exception e) {
                System.out.println("ERROR in JDBC caching for model " + model + ": " + e.getMessage());
                writeDebugFile("ERROR in JDBC caching for model " + model + ": " + e.getMessage() + "\n");
                LOGGER.error("Error caching JDBC queries for model {}: {}", model, e.getMessage());
                e.printStackTrace();
            }
            
            try {
                System.out.println("Starting XMLA query caching for model: " + model);
                writeDebugFile("Starting XMLA query caching for model: " + model + "\n");
                cacheXmlaQueries(model);
                System.out.println("XMLA query caching completed for model: " + model);
                writeDebugFile("XMLA query caching completed for model: " + model + "\n");
            } catch (Exception e) {
                System.out.println("ERROR in XMLA caching for model " + model + ": " + e.getMessage());
                writeDebugFile("ERROR in XMLA caching for model " + model + ": " + e.getMessage() + "\n");
                LOGGER.error("Error caching XMLA queries for model {}: {}", model, e.getMessage()); 
                e.printStackTrace();
            }
        }
        
        // Final verification of all created files
        verifyAllJsonFiles();
        
        System.out.println("=== InstallerVerQueryExtractExecutor Finished ===");
        writeDebugFile("=== InstallerVerQueryExtractExecutor Finished ===\n");
        LOGGER.info("QueryExtractExecutor finished.");
        org.apache.logging.log4j.LogManager.shutdown();
    }
    
    private void writeDebugFile(String content) {
        String debugFilePath = APP_HOME + "/run_logs/installer_debug.log";
        
        try {
            Path path = Paths.get(debugFilePath);
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }
            
            Files.writeString(path, content, 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.APPEND,
                java.nio.file.StandardOpenOption.WRITE);
                
        } catch (IOException e) {
            System.err.println("Failed to write debug file: " + e.getMessage());
        }
    }

    private void ensureQueriesDirectoryExists() {
        try {
            Path queriesPath = Paths.get(QUERIES_DIR);
            System.out.println("Checking queries directory: " + queriesPath.toAbsolutePath());
            
            if (!Files.exists(queriesPath)) {
                System.out.println("Directory doesn't exist, creating: " + QUERIES_DIR);
                Files.createDirectories(queriesPath);
                System.out.println("Created queries directory: " + QUERIES_DIR);
                setDirectoryPermissions(queriesPath);
            } else {
                System.out.println("Queries directory already exists: " + QUERIES_DIR);
                
                if (!Files.isWritable(queriesPath)) {
                    System.err.println("WARNING: Queries directory is not writable: " + QUERIES_DIR);
                    // Try to fix permissions
                    setDirectoryPermissions(queriesPath);
                } else {
                    System.out.println("Queries directory is writable: " + QUERIES_DIR);
                }
            }
            
            testWritePermission();
            
        } catch (IOException e) {
            System.err.println("FAILED to create queries directory: " + QUERIES_DIR);
            System.err.println("Error: " + e.getMessage());
            throw new RuntimeException("Cannot create queries directory: " + QUERIES_DIR, e);
        }
    }

    private void setDirectoryPermissions(Path path) {
        try {
            java.io.File dir = path.toFile();
            if (!dir.setReadable(true, false) || 
                !dir.setWritable(true, false) || 
                !dir.setExecutable(true, false)) {
                System.err.println("WARNING: Could not set all permissions for directory: " + path);
            } else {
                System.out.println("Set permissions for directory: " + path);
            }
        } catch (SecurityException e) {
            System.err.println("Security exception when setting directory permissions: " + path);
        }
    }

    private void testWritePermission() {
        try {
            Path testFile = Paths.get(QUERIES_DIR, ".write_test");
            Files.writeString(testFile, "test", 
                java.nio.file.StandardOpenOption.CREATE, 
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE);
            Files.delete(testFile);
            System.out.println("✓ Write test successful in: " + QUERIES_DIR);
        } catch (IOException e) {
            System.err.println("✗ Write test FAILED in: " + QUERIES_DIR);
            System.err.println("Error: " + e.getMessage());
            throw new RuntimeException("Cannot write to queries directory: " + QUERIES_DIR, e);
        }
    }

    private void cacheJdbcQueries(String model) {
        System.out.println("=== cacheJdbcQueries called for model: " + model + " ===");
        
        LOGGER.info("Caching JDBC queries...");

        final String query = """
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
                q.query_language = ? AND
                p.planning_started > current_timestamp - interval '60 day'
                and p.cube_name = ?
                AND q.service = 'user-query'
                AND r.succeeded = true
                AND LENGTH(q.query_text) > 100
                AND q.query_text NOT LIKE '/* Virtual query to get the members of a level */%'
                AND q.query_text NOT LIKE '-- statement does not return rows%'
            GROUP BY
                1, 2, 3, 5, 6
            ORDER BY 3
        """;

        System.out.println("Creating DAO instance...");
        AtScalePostgresDao dao = AtScalePostgresDao.getInstance();
        if (dao == null) {
            System.err.println("ERROR: Failed to create AtScalePostgresDao instance");
            return;
        }
        
        System.out.println("DAO instance created, creating QueryHistoryFileUtil...");
        
        // FIX: Use the single-argument constructor that exists
        QueryHistoryFileUtil queryHistoryFileUtil = new QueryHistoryFileUtil(dao);
        
        System.out.println("Starting JDBC query caching...");
        System.out.println("Expected output directory for JSON files: " + QUERIES_DIR);
        
        // List files before operation for debugging
        listFilesInQueriesDir("Before JDBC caching");
        
        queryHistoryFileUtil.cacheJdbcQueries(model, query, AtScalePostgresDao.QueryLanguage.INSTALLER_SQL.getValue(), model);
        System.out.println("JDBC query caching completed.");
        
        // List files after operation for debugging
        listFilesInQueriesDir("After JDBC caching");
        
        verifyJsonFilesCreated(model, "jdbc");
    }

    private void cacheXmlaQueries(String model) {
        System.out.println("=== cacheXmlaQueries called for model: " + model + " ===");
        
        LOGGER.info("Caching XMLA queries...");

        final String query = """
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
                q.query_language = ? AND
                p.planning_started > current_timestamp - interval '60 day'
                and p.cube_name = ?
                AND q.service = 'user-query'
                AND r.succeeded = true
                AND LENGTH(q.query_text) > 100
                AND q.query_text NOT LIKE '/* Virtual query to get the members of a level */%'
                AND q.query_text NOT LIKE '-- statement does not return rows%'
            GROUP BY
                1, 2, 3, 5, 6
            ORDER BY 3
        """;

        System.out.println("Creating DAO instance...");
        AtScalePostgresDao dao = AtScalePostgresDao.getInstance();
        if (dao == null) {
            System.err.println("ERROR: Failed to create AtScalePostgresDao instance");
            return;
        }
        
        System.out.println("DAO instance created, creating QueryHistoryFileUtil...");
        
        // FIX: Use the single-argument constructor that exists
        QueryHistoryFileUtil queryHistoryFileUtil = new QueryHistoryFileUtil(dao);
        
        System.out.println("Starting XMLA query caching...");
        System.out.println("Expected output directory for JSON files: " + QUERIES_DIR);
        
        // List files before operation for debugging
        listFilesInQueriesDir("Before XMLA caching");
        
        queryHistoryFileUtil.cacheXmlaQueries(model, query, AtScalePostgresDao.QueryLanguage.XMLA.getValue(), model);
        System.out.println("XMLA query caching completed.");
        
        // List files after operation for debugging
        listFilesInQueriesDir("After XMLA caching");
        
        verifyJsonFilesCreated(model, "xmla");
    }

    private void listFilesInQueriesDir(String context) {
        try {
            Path queriesDir = Paths.get(QUERIES_DIR);
            if (Files.exists(queriesDir) && Files.isDirectory(queriesDir)) {
                System.out.println("=== " + context + " ===");
                try (var stream = Files.list(queriesDir)) {
                    long fileCount = stream
                        .map(path -> "  - " + path.getFileName())
                        .peek(System.out::println)
                        .count();
                    
                    if (fileCount == 0) {
                        System.out.println("  (empty directory)");
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error listing files in queries directory: " + e.getMessage());
        }
    }

    private void verifyJsonFilesCreated(String model, String queryType) {
        try {
            Path queriesDir = Paths.get(QUERIES_DIR);
            if (Files.exists(queriesDir) && Files.isDirectory(queriesDir)) {
                System.out.println("Checking for JSON files in: " + QUERIES_DIR);
                
                try (var stream = Files.list(queriesDir)) {
                    long jsonFileCount = stream
                        .filter(path -> path.toString().endsWith(".json"))
                        .count();
                    
                    System.out.println("Total JSON files found: " + jsonFileCount);
                    
                    if (jsonFileCount > 0) {
                        // List all JSON files
                        try (var jsonStream = Files.list(queriesDir)) {
                            jsonStream
                                .filter(path -> path.toString().endsWith(".json"))
                                .forEach(path -> System.out.println("  - " + path.getFileName()));
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error verifying JSON files: " + e.getMessage());
        }
    }

    private void verifyAllJsonFiles() {
        System.out.println("=== FINAL VERIFICATION OF ALL JSON FILES ===");
        listFilesInQueriesDir("Final directory contents");
        
        try {
            Path queriesDir = Paths.get(QUERIES_DIR);
            if (Files.exists(queriesDir)) {
                long totalSize;
                try (var stream = Files.walk(queriesDir)) {
                    totalSize = stream
                        .filter(Files::isRegularFile)
                        .count();
                }
                System.out.println("Total files in queries directory: " + totalSize);
            }
        } catch (IOException e) {
            System.err.println("Error in final verification: " + e.getMessage());
        }
    }
}