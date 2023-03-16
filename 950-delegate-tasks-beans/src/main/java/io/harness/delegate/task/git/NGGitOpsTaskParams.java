/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.git;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.scm.GitCapabilityHelper;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.beans.gitapi.GitApiTaskParams;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@OwnedBy(GITOPS)
@Data
@Builder
public class NGGitOpsTaskParams implements TaskParameters, ExecutionCapabilityDemander {
  private GitFetchFilesConfig gitFetchFilesConfig; // will have ScmConnector
  private Map<String, Map<String, String>> filesToVariablesMap;
  private boolean overrideConfig;
  private String accountId;
  private String activityId;
  ConnectorInfoDTO connectorInfoDTO;
  GitOpsTaskType gitOpsTaskType;
  private String prLink;
  private GitApiTaskParams gitApiTaskParams;
  private String prTitle;
  private boolean isCloseLogStream;

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> capabilities = new ArrayList<>();
    if (GitOpsTaskType.CREATE_PR.equals(gitOpsTaskType) || GitOpsTaskType.UPDATE_RELEASE_REPO.equals(gitOpsTaskType)) {
      GitStoreDelegateConfig gitStoreDelegateConfig = gitFetchFilesConfig.getGitStoreDelegateConfig();
      capabilities.addAll(GitCapabilityHelper.fetchRequiredExecutionCapabilities(
          ScmConnectorMapper.toGitConfigDTO(gitFetchFilesConfig.getGitStoreDelegateConfig().getGitConfigDTO()),
          gitStoreDelegateConfig.getEncryptedDataDetails(), gitStoreDelegateConfig.getSshKeySpecDTO()));
      capabilities.addAll(EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          gitStoreDelegateConfig.getEncryptedDataDetails(), maskingEvaluator));
    }
    return capabilities;
  }
}
