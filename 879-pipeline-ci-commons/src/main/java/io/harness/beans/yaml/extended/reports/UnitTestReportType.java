/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.yaml.extended.reports;

import io.harness.annotation.RecasterAlias;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModelProperty;
import org.springframework.data.annotation.TypeAlias;

@TypeAlias("unit_test_report_type")
@RecasterAlias("io.harness.beans.yaml.extended.reports.UnitTestReportType")
public enum UnitTestReportType {
  @JsonProperty("JUnit") JUNIT("JUnit");

  private final String yamlName;

  @ApiModelProperty(hidden = true) String uuid;

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static UnitTestReportType getUnitTestReportType(@JsonProperty("type") String yamlName) {
    for (UnitTestReportType unitTestReportType : UnitTestReportType.values()) {
      if (unitTestReportType.yamlName.equalsIgnoreCase(yamlName)) {
        return unitTestReportType;
      }
    }
    throw new IllegalArgumentException("Invalid value: " + yamlName);
  }

  UnitTestReportType(String yamlName) {
    this.yamlName = yamlName;
  }

  @JsonValue
  public String getYamlName() {
    return yamlName;
  }
}
