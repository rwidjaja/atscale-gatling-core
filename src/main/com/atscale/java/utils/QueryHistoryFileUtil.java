package com.atscale.java.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import com.atscale.java.dao.QueryHistoryDto;
import com.atscale.java.dao.AtScalePostgresDao;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Utility class to write and read a list of QueryHistoryDto objects to and from a file.
 */
@SuppressWarnings({"Convert2Diamond", "FieldCanBeLocal", "unused"})
public class QueryHistoryFileUtil {
    private final Logger LOGGER = LoggerFactory.getLogger(QueryHistoryFileUtil.class);
    private final AtScalePostgresDao dao;

    public QueryHistoryFileUtil(AtScalePostgresDao dao) {
        this.dao = dao;
    }

    protected void writeQueryHistoryToFile(List<QueryHistoryDto> dtos, String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(dtos);
        Files.write(Paths.get(filePath), json.getBytes());
    }

    public static List<QueryHistoryDto> readQueryHistoryFromFile(String filePath) throws IOException {
        String json = new String(Files.readAllBytes(Paths.get(filePath)));
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(json, new TypeReference<List<QueryHistoryDto>>() {});
    }

    private void createQueryDirectory() {
        String path = Paths.get(System.getProperty("user.dir"),"queries").toString();
        java.io.File directory = new java.io.File(path);
        if (!directory.exists()) {
            if (directory.mkdir()) {
                LOGGER.info("Directory created: {}", directory.getAbsolutePath());
            } else {
                LOGGER.error("Failed to create directory: {}", directory.getAbsolutePath());
                throw new RuntimeException(String.format("Failed to create directory: %s", directory.getAbsolutePath()));
            }
        } else {
            LOGGER.debug("Directory already exists: {}", directory.getAbsolutePath());
        }
    }

    public void cacheQueries(String model){
        createQueryDirectory();
        try {
            cacheJdbcQueries(model);
        } catch (Exception e) {
            LOGGER.error("Error caching JDBC queries for model {}: {}", model, e.getMessage());
        }
        try {
            cacheXmlaQueries(model);
        } catch (Exception e) {
            LOGGER.error("Error caching XMLA queries for model {}: {}", model, e.getMessage()); 
        }
    }

    public void cacheJdbcQueries(String model, String jdbcQuery, String... jdbcParams) {
        createQueryDirectory();
        String filePath = getJdbcFilePath(model);
        LOGGER.info("Caching JDBC queries for model {} to file: {}", model, filePath);  
        try {
            List<QueryHistoryDto> queryHistoryList = AtScalePostgresDao.getInstance()
                    .getQueryHistory(jdbcQuery, jdbcParams);
            validate(queryHistoryList, jdbcParams[0], model);
            writeQueryHistoryToFile(queryHistoryList, filePath);
        } catch (IOException e) {
            throw new RuntimeException("Error caching queries to file: " + filePath, e);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Validation error {}", e.getMessage(), e);
        }
    }

    public void cacheXmlaQueries(String model, String xmlaQuery, String... xmlaParams) {
        createQueryDirectory();
        String filePath = getXmlaFilePath(model);
        LOGGER.info("Caching XMLA queries for model {} to file: {}", model, filePath);  
        try {
            List<QueryHistoryDto> queryHistoryList = AtScalePostgresDao.getInstance()
                    .getQueryHistory(xmlaQuery, xmlaParams);
            validate(queryHistoryList, AtScalePostgresDao.QueryLanguage.XMLA.getValue(), model);
            writeQueryHistoryToFile(queryHistoryList, filePath);
        } catch (IOException e) {
            throw new RuntimeException("Error caching queries to file: " + filePath, e);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Validation error {}", e.getMessage());
        }
    }

    private void validate(List<QueryHistoryDto> queries, String queryLanguage, String model) {
        if((queries == null) || queries.isEmpty()) {
            throw new IllegalArgumentException("No results returned for the user provided query.");
        }

        for(QueryHistoryDto query: queries) {
            if(! queryLanguage.equalsIgnoreCase(query.getQueryLanguage())) {
                throw new IllegalArgumentException(String.format("User provided query returned results that do not match the expected query language: %s" , queryLanguage));
            }
            if(query.getQueryName() == null) {
                throw new IllegalArgumentException("Query name is required but not found.");
            }
            if(query.getCubeName() == null || !query.getCubeName().equalsIgnoreCase(model)) {
                throw new IllegalArgumentException(String.format("User provided query returned results that do not match the expected model: %s", model));
            }
        }
    }

    protected void cacheJdbcQueries(String model) {
        String filePath = getJdbcFilePath(model);
        try {
            List<QueryHistoryDto> queryHistoryList = AtScalePostgresDao.getInstance()
                    .getQueryHistory(AtScalePostgresDao.QueryLanguage.SQL, model);
            writeQueryHistoryToFile(queryHistoryList, filePath);
        } catch (IOException e) {
            throw new RuntimeException("Error caching queries to file: " + filePath, e);
        }
    }

    protected void cacheXmlaQueries(String model) {
        String filePath = getXmlaFilePath(model);
        try {
            List<QueryHistoryDto> queryHistoryList = AtScalePostgresDao.getInstance()
                    .getQueryHistory(AtScalePostgresDao.QueryLanguage.XMLA, model);
            writeQueryHistoryToFile(queryHistoryList, filePath);
        } catch (IOException e) {
            throw new RuntimeException("Error caching queries to file: " + filePath, e);
        }
    }

    public static String getXmlaFilePath(String model) {
        model = StringUtil.stripQuotes(model);
        return Paths.get(System.getProperty("user.dir"), "queries", model + "_xmla_queries.json").toString();
    }

    public static String getJdbcFilePath(String model) {
        model = StringUtil.stripQuotes(model);
        return Paths.get(System.getProperty("user.dir"), "queries", model + "_jdbc_queries.json").toString();
    }
}
