/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.bamboo;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Created by sgurubelli on 8/28/17.
 */
@Data
@Builder
@OwnedBy(HarnessTeam.CDC)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Result {
  private String buildResultKey;
  private String planName;
  private String projectName;
  private String buildState;
  private String buildNumber;
  private String buildStartedTime;
  private String buildCompletedTime;
  private String buildTestSummary;
  private String successfulTestCount;
  private String failedTestCount;
  private String skippedTestCount;
  private String buildReason;
  private String finished;
  private String buildUrl;
  private String lifeCycleState;
  @Builder.Default private Link link = new Link();
  @Builder.Default private Plan plan = new Plan();

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Link {
    private String href;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Plan {
    private String shortName;
    private String name;
    private Link link = new Link();
  }
}
