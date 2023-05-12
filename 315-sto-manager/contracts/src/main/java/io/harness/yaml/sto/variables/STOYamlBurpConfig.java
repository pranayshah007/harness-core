/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.yaml.sto.variables;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("stoYamlBurpConfig")
@RecasterAlias("io.harness.yaml.sto.variables.STOYamlBurpConfig")
public enum STOYamlBurpConfig implements STOYamlConfig {
  @JsonProperty("default") DEFAULT("default"),
  @JsonProperty("never-stop-crawl-due-to-application-errors")
  NEVER_STOP_CRAWL_DUE_TO_APPLICATION_ERRORS("never-stop-crawl-due-to-application-errors"),
  @JsonProperty("never-stop-audit-due-to-application-errors")
  NEVER_STOP_AUDIT_DUE_TO_APPLICATION_ERRORS("never-stop-audit-due-to-application-errors"),
  @JsonProperty("minimize-false-positives") MINIMIZE_FALSE_POSITIVES("minimize-false-positives"),
  @JsonProperty("minimize-false-negatives") MINIMIZE_FALSE_NEGATIVES("minimize-false-negatives"),
  @JsonProperty("crawl-strategy-most-complete") CRAWL_STRATEGY_MOST_COMPLETE("crawl-strategy-most-complete"),
  @JsonProperty("crawl-strategy-more-complete") CRAWL_STRATEGY_MORE_COMPLETE("crawl-strategy-more-complete"),
  @JsonProperty("crawl-strategy-fastest") CRAWL_STRATEGY_FASTEST("crawl-strategy-fastest"),
  @JsonProperty("crawl-strategy-faster") CRAWL_STRATEGY_FASTER("crawl-strategy-faster"),
  @JsonProperty("crawl-limit-60-minutes") CRAWL_LIMIT_60_MINUTES("crawl-limit-60-minutes"),
  @JsonProperty("crawl-limit-30-minutes") CRAWL_LIMIT_30_MINUTES("crawl-limit-30-minutes"),
  @JsonProperty("crawl-limit-10-minutes") CRAWL_LIMIT_10_MINUTES("crawl-limit-10-minutes"),
  @JsonProperty("crawl-and-audit-lightweight") CRAWL_AND_AUDIT_LIGHTWEIGHT("crawl-and-audit-lightweight"),
  @JsonProperty("crawl-and-audit-fast") CRAWL_AND_AUDIT_FAST("crawl-and-audit-fast"),
  @JsonProperty("crawl-and-audit-deep") CRAWL_AND_AUDIT_DEEP("crawl-and-audit-deep"),
  @JsonProperty("crawl-and-audit-balanced") CRAWL_AND_AUDIT_BALANCED("crawl-and-audit-balanced"),
  @JsonProperty("audit-coverage-thorough") AUDIT_COVERAGE_THOROUGH("audit-coverage-thorough"),
  @JsonProperty("audit-coverage-maximum") AUDIT_COVERAGE_MAXIMUM("audit-coverage-maximum"),
  @JsonProperty("audit-checks-passive") AUDIT_CHECKS_PASSIVE("audit-checks-passive"),
  @JsonProperty("audit-checks-medium-active") AUDIT_CHECKS_MEDIUM_ACTIVE("audit-checks-medium-active"),
  @JsonProperty("audit-checks-light-active") AUDIT_CHECKS_LIGHT_ACTIVE("audit-checks-light-active"),
  @JsonProperty("audit-checks-critical-issues-only")
  AUDIT_CHECKS_CRITICAL_ISSUES_ONLY("audit-checks-critical-issues-only"),
  @JsonProperty("audit-checks-all-except-time-based-detection-methods")
  AUDIT_CHECKS_ALL_EXCEPT_TIME_BASED_DETECTION_METHODS("audit-checks-all-except-time-based-detection-methods"),
  @JsonProperty("audit-checks-all-except-java-script-analysis")
  AUDIT_CHECKS_ALL_EXCEPT_JAVA_SCRIPT_ANALYSIS("audit-checks-all-except-java-script-analysis");

  private final String yamlName;

  STOYamlBurpConfig(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static STOYamlBurpConfig getValue(@JsonProperty("config") String yamlName) {
    for (STOYamlBurpConfig value : STOYamlBurpConfig.values()) {
      if (value.yamlName.equalsIgnoreCase(yamlName)) {
        return value;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }

  @Override
  public String toString() {
    return yamlName;
  }

  public static STOYamlBurpConfig fromString(final String s) {
    return STOYamlBurpConfig.getValue(s);
  }
}
