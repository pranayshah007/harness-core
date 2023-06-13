/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.command;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.JenkinsConfig;
import software.wings.beans.JenkinsSubTaskType;

import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@OwnedBy(CDC)
@Data
@Builder
public class JenkinsTaskParams implements ExecutionCapabilityDemander {
  private JenkinsConfig jenkinsConfig;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private String jobName;
  private Map<String, String> parameters;
  private Map<String, String> filePathsForAssertion;
  private String activityId;
  private String unitName;
  private boolean unstableSuccess;
  private boolean injectEnvVars;
  private JenkinsSubTaskType subTaskType;
  private String queuedBuildUrl;
  private long timeout;
  private long startTs;
  private String appId;
  private List<String> artifactPaths;
  private Map<String, String> metaData;
  private boolean timeoutSupported;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    // Ideally we should check for capability for getting encryption details
    // but the original validation task does not do that
    return jenkinsConfig.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}
