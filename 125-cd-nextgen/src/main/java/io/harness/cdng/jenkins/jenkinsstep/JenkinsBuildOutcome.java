/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jenkins.jenkinsstep;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ExecutionStatus;
import io.harness.pms.sdk.core.data.Outcome;

import software.wings.sm.states.FilePathAssertionEntry;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(CDC)
@JsonTypeName("JenkinsBuildOutcome")
@TypeAlias("jenkinsBuildOutcome")
@RecasterAlias("JenkinsBuildOutcome")
public class JenkinsBuildOutcome implements Outcome {
  private ExecutionStatus executionStatus;
  private String jenkinsResult;
  private String errorMessage;
  private String jobUrl;
  private List<FilePathAssertionEntry> filePathAssertionMap = Lists.newArrayList();
  private String buildNumber;
  private Map<String, String> metadata;
  private Map<String, String> jobParameters;
  private Map<String, String> envVars;
  private String description;
  private String buildDisplayName;
  private String buildFullDisplayName;
  private String queuedBuildUrl;
  private String activityId;
  private Long timeElapsed;
}
