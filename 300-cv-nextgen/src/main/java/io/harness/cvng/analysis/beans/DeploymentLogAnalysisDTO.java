/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Value;
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeploymentLogAnalysisDTO {
  List<Cluster> clusters;
  List<ClusterCoordinates> clusterCoordinates;
  ResultSummary resultSummary;
  List<HostSummary> hostSummaries;

  public enum ClusterType {
    BASELINE("Baseline", 3),
    KNOWN_EVENT("Known", 2),
    UNEXPECTED_FREQUENCY("Unexpected Frequency", 1),
    UNKNOWN_EVENT("Unknown", 0);
    private final String displayName;
    private final int sortOrder;
    ClusterType(String displayName, int sortOrder) {
      this.displayName = displayName;
      this.sortOrder = sortOrder;
    }

    public String getDisplayName() {
      return displayName;
    }

    public static List<ClusterType> getNonBaselineValues() {
      return Arrays.stream(ClusterType.values())
          .filter(key -> key != ClusterType.BASELINE)
          .collect(Collectors.toList());
    }

    public static List<ClusterType> getValues() {
      return Arrays.stream(ClusterType.values()).collect(Collectors.toList());
    }

    private int getSortOrder() {
      return sortOrder;
    }
    public static Comparator<ClusterType> clusterTypeRiskComparator =
        Comparator.comparingInt(ClusterType::getSortOrder);
  }

  public List<Cluster> getClusters() {
    if (this.clusters == null) {
      return Collections.emptyList();
    }
    return clusters;
  }

  public List<HostSummary> getHostSummaries() {
    if (this.hostSummaries == null) {
      return Collections.emptyList();
    }
    return hostSummaries;
  }

  @Value
  @Builder
  public static class Cluster {
    String text;
    int label;
  }

  @Value
  @Builder
  public static class ClusterCoordinates {
    double x;
    double y;
    int label;
    String host;
  }

  @Value
  @Builder
  public static class ClusterSummary {
    int label;
    ClusterType clusterType;
    int risk;
    public Risk getRiskLevel() {
      return Risk.valueOfRiskForDeploymentLogAnalysis(risk);
    }
    double score;
    int count;
    List<Double> testFrequencyData;

    public List<Double> getTestFrequencyData() {
      if (this.testFrequencyData == null) {
        return Collections.emptyList();
      }
      return testFrequencyData;
    }
  }

  @Value
  @Builder
  public static class ControlClusterSummary {
    int label;
    List<Double> controlFrequencyData;
  }

  @Value
  @Builder
  public static class ResultSummary {
    int risk;
    public Risk getRiskLevel() {
      return Risk.valueOfRiskForDeploymentLogAnalysis(risk);
    }
    double score;
    List<ControlClusterSummary> controlClusterSummaries;
    List<ClusterSummary> testClusterSummaries;

    @JsonIgnore @Builder.Default private static Map<Integer, List<Double>> labelToControlDataMap = new HashMap<>();

    public void setLabelToControlDataMap() {
      if (controlClusterSummaries != null) {
        labelToControlDataMap.putAll(controlClusterSummaries.stream().collect(
            Collectors.toMap(ControlClusterSummary::getLabel, ControlClusterSummary::getControlFrequencyData)));
      }
    }

    public List<ControlClusterSummary> getControlClusterSummaries() {
      if (controlClusterSummaries == null) {
        return Collections.emptyList();
      }
      return controlClusterSummaries;
    }

    public List<Double> getControlData(int label) {
      if (isEmpty(labelToControlDataMap) || !labelToControlDataMap.containsKey(label)) {
        return new ArrayList<>();
      }
      return labelToControlDataMap.get(label);
    }

    public List<ClusterSummary> getTestClusterSummaries() {
      if (testClusterSummaries == null) {
        return Collections.emptyList();
      }
      return testClusterSummaries;
    }
  }

  @Value
  @Builder
  public static class HostSummary {
    String host;
    ResultSummary resultSummary;
  }
}
