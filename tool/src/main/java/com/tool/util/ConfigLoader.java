package com.tool.util;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.json.JSONArray;
import org.json.JSONObject;

import com.tool.domain.Severity;
import com.tool.domain.Threshold;
import com.tool.domain.Category;
import com.tool.metrics.Metric;
import com.tool.metrics.maintainability.CyclomaticComplexityMetric;
import com.tool.metrics.security.DependencyVulnerabilityRiskMetric;

/**
 * Utility class for resolving metric types and thresholds from the configuration file.
 */
public class ConfigLoader {
    public static final String CONFIG_FILE_PATH = "default_config.json";
    public static final List<String> THRESHOLD_REGISTRY = Arrays.asList("critical", "major", "minor", "info");

    // Add new metrics here
    private static final Map<String, Function<ArrayList<Threshold>, Metric>> METRIC_REGISTRY =
        Map.of(
            "cyclomatic_complexity",
            CyclomaticComplexityMetric::new,
            "dependency_vulnerability_risk", 
            DependencyVulnerabilityRiskMetric::new
        
        );

    /**
     * Resolve the configuration file path. If the provided path is null, use the default config file.
     * If the provided path does not exist, throw an exception.
     * @param configArg the provided configuration file path
     * @return the resolved configuration file path
     * @throws RuntimeException if the default config cannot be loaded or if the provided config path is invalid
     */
    public static Path resolveConfigPath(String configArg) {
        if (configArg != null) {
            Path providedPath = Paths.get(configArg);
            if (!Files.exists(providedPath)) {
                throw new RuntimeException("Provided config file does not exist: " + configArg);
            }
            return Paths.get(configArg);
        }

        // Load default config from resources and copy to a temp file so Path works
        try (InputStream is = ConfigLoader.class
                .getClassLoader()
                .getResourceAsStream("default_config.json")) {

            if (is == null) {
                throw new RuntimeException("Default config not found in resources");
            }

            // Create temp file because Path cannot directly represent jar resources
            Path tempFile = Files.createTempFile("default_config", ".json");
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);

            return tempFile;

        } catch (Exception e) {
            throw new RuntimeException("Default config loading failed", e);
        }
    }

    /**
     * Load categories from a JSON configuration file
     * @param configPath the path to the JSON configuration file
     * @return a list of loaded Category objects
     */
    public static ArrayList<Category> loadCategories(Path configPath) {
        try {
            String content = Files.readString(configPath);
            
            JSONArray categoriesArray = new JSONArray(content);
            ArrayList<Category> loadedCategories = new ArrayList<>();
            for (int i = 0; i < categoriesArray.length(); i++) {
                JSONObject categoryObj = categoriesArray.getJSONObject(i);
                Category category = parseCategory(categoryObj);
                loadedCategories.add(category);
            }

            return loadedCategories;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load categories from config: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a category from a JSON object
     * @param categoryObj the JSON object representing the category
     * @return the parsed Category object
     */
    private static Category parseCategory(JSONObject categoryObj) {
        String name = categoryObj.getString("category");
        String description = categoryObj.getString("description");
        JSONArray metricArray = categoryObj.getJSONArray("metrics");
        ArrayList<Metric> metrics = new ArrayList<>();

        for (int j = 0; j < metricArray.length(); j++) {
            JSONObject metricObj = metricArray.getJSONObject(j);
            Metric metric = parseMetric(metricObj);
            metrics.add(metric);
        }

        return new Category(name, description, metrics);
    }

    /**
     * Parse a metric from a JSON object
     * @param metricObj the JSON object representing the metric
     * @return the parsed Metric object
     */
    private static Metric parseMetric(JSONObject metricObj) {
        String type = metricObj.getString("type");
        JSONObject thresholdObject = metricObj.getJSONObject("thresholds");

        if(type == null || type.isEmpty()) {
            throw new IllegalArgumentException("Metric type cannot be null or empty: " + metricObj.toString());
        }
        ArrayList<Threshold> thresholds = resolveThresholds(thresholdObject);

        if(thresholds.isEmpty()) {
            throw new RuntimeException("Metric must have at least one threshold: " + metricObj.toString());
        }

        return resolveMetric(type, thresholds);
    }

    /**
     * Resolve thresholds from a JSON object. 
     * The object should have keys corresponding to severity levels and values as the threshold values.
     * @param thresholdObject the JSON object representing the thresholds
     * @return a list of Threshold objects sorted by severity (highest severity first)
     * @throws IllegalArgumentException if the threshold object is null or contains unsupported severity levels
     */
    public static ArrayList<Threshold> resolveThresholds(JSONObject thresholdObject) {
        if(thresholdObject == null) {
            throw new IllegalArgumentException("Threshold object cannot be null");
        }

        ArrayList<Threshold> thresholds = new ArrayList<>();

        for (String severityName : THRESHOLD_REGISTRY) {
            String key = severityName.trim().toLowerCase();
            if(thresholdObject.has(key)) {
                double value = thresholdObject.getDouble(key);
                Severity severity = Severity.valueOf(key.toUpperCase());
                thresholds.add(new Threshold(severity, value));
            } 
        }

        // Sort by threshold severity, so the highest severity is first.
        thresholds.sort((a, b) -> Integer.compare(b.severity().rank(), a.severity().rank()));
        
        // Ensure it follows the expected order of severity (blocker > critical > major > minor > info)
        for (int i = 0; i < thresholds.size() - 1; i++) {
            if (thresholds.get(i).threshold() >= thresholds.get(i + 1).threshold()) {
                throw new IllegalArgumentException(String.format(
                    "Threshold values must be in non-decreasing order of severity. Found %s=%.2f and %s=%.2f",
                    thresholds.get(i).severity(), thresholds.get(i).threshold(),
                    thresholds.get(i + 1).severity(), thresholds.get(i + 1).threshold()
                ));
            }
        }
        return thresholds;
    }

    /**
     * Resolve a metric type to a Metric object using the METRIC_REGISTRY.
     * @param type the metric type as a string (e.g., "cyclomatic_complexity")
     * @param thresholds the list of thresholds associated with the metric
     * @return the resolved Metric object
     * @throws IllegalArgumentException if the metric type is null, empty, or not supported by the registry
     */
    public static Metric resolveMetric(String type, ArrayList<Threshold> thresholds) {
        if (type == null || type.isEmpty())
            throw new IllegalArgumentException("Metric type cannot be null or empty");

        if (!METRIC_REGISTRY.containsKey(type))
            throw new IllegalArgumentException("Unsupported metric type: " + type + ". Supported types are: " + METRIC_REGISTRY.keySet().toString());

        return METRIC_REGISTRY.get(type).apply(thresholds);
    }
}
