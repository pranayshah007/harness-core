/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.newrelic;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.exception.WingsException;
import io.harness.serializer.YamlUtils;

import software.wings.common.VerificationConstants;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.sm.states.NewRelicState;
import software.wings.sm.states.NewRelicState.Metric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Vaibhav Tulsyan
 * 26/Sep/2018
 */
@Slf4j
public class MetricUtilHelper {
  public Map<String, TimeSeriesMetricDefinition> metricDefinitions(Collection<Metric> metrics) {
    Map<String, TimeSeriesMetricDefinition> metricDefinitionByName = new HashMap<>();
    for (Metric metric : metrics) {
      metricDefinitionByName.put(metric.getMetricName(),
          TimeSeriesMetricDefinition.builder()
              .metricName(metric.getMetricName())
              .metricType(metric.getMlMetricType())
              .build());
    }
    return metricDefinitionByName;
  }

  /**
   * Get a mapping from metric name to {@link Metric} for the list of metric names
   * provided as input.
   * This method is meant to be called before saving a metric template.
   * The output of this method shall be consumed by metricDefinitions(...)
   * @param metricNames - List[String] containing metric names
   * @return - Map[String, Metric], a mapping from metric name to {@link Metric}
   */
  public Map<String, Metric> getMetricsCorrespondingToMetricNames(List<String> metricNames) {
    Map<String, Metric> metricMap = new HashMap<>();
    try {
      Map<String, List<Metric>> metrics = getMetricsFromYaml(VerificationConstants.getNewRelicMetricsYamlUrl());
      if (metrics == null) {
        return metricMap;
      }

      Set<String> metricNamesSet = metricNames == null ? new HashSet<>() : Sets.newHashSet(metricNames);

      // Iterate over the metrics present in the YAML file
      for (Map.Entry<String, List<Metric>> entry : metrics.entrySet()) {
        if (entry == null) {
          log.error("Found a null entry in the NewRelic Metrics YAML file.");
        } else {
          entry.getValue().forEach(metric -> {
            /*
            We consider 2 cases:
            1. metricNames is empty - we add all metrics present in the YAML to the metricMap in this case
            2. metricNames is non-empty - we only add metrics which are present in the list
             */
            if (metric != null && (isEmpty(metricNames) || metricNamesSet.contains(metric.getMetricName()))) {
              if (metric.getTags() == null) {
                metric.setTags(new HashSet<>());
              }
              // Add top-level key of the YAML as a tag
              metric.getTags().add(entry.getKey());
              metricMap.put(metric.getMetricName(), metric);
            }
          });
        }
      }

      /*
      If metricNames is non-empty but metricMap is, it means that all
      metric names were spelt incorrectly.
       */
      if (!isEmpty(metricNames) && metricMap.isEmpty()) {
        log.warn("Incorrect set of metric names received. Maybe the UI is sending incorrect metric names.");
        throw new WingsException("Incorrect Metric Names received.");
      }

      return metricMap;
    } catch (WingsException wex) {
      // Return empty metricMap
      return metricMap;
    }
  }

  /**
   *
   * @param yamlPath - String containing path from rest/src/main/java/resources
   *                   e.g. Path to new relic metrics yaml => /apm/newrelic_metrics.yml
   * @return Mapping of name of the group of metrics (e.g. WebTransactions) to List of Metric Objects
   */
  private Map<String, List<Metric>> getMetricsFromYaml(String yamlPath) {
    YamlUtils yamlUtils = new YamlUtils();
    URL url = NewRelicState.class.getResource(yamlPath);
    try {
      String yaml = Resources.toString(url, Charsets.UTF_8);
      return yamlUtils.read(yaml, new TypeReference<Map<String, List<Metric>>>() {});
    } catch (IOException ioex) {
      log.error("Could not read " + yamlPath);
      throw new WingsException("Unable to load New Relic metrics", ioex);
    }
  }
}
