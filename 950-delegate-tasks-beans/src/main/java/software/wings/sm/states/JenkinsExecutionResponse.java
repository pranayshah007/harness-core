/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.sm.states;

import io.harness.beans.ExecutionStatus;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.delegate.beans.DelegateTaskNotifyResponseData;

import software.wings.beans.JenkinsSubTaskType;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public final class JenkinsExecutionResponse implements DelegateTaskNotifyResponseData {
  private DelegateMetaInfo delegateMetaInfo;
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
  private JenkinsSubTaskType subTaskType;
  private String activityId;
  private Long timeElapsed; // time taken for task completion
  private boolean isTimeoutError;
}
