/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.trigger.request;

import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.GitConfig;
import software.wings.beans.trigger.TriggerCommand.TriggerCommandType;
import software.wings.delegatetasks.delegatecapability.CapabilityHelper;

import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class TriggerDeploymentNeededRequest extends TriggerRequest implements ExecutionCapabilityDemander {
  private GitConfig gitConfig;
  private String gitConnectorId;
  private String currentCommitId;
  private String oldCommitId;
  private String branch;
  private String repoName;
  private List<String> filePaths;
  private List<EncryptedDataDetail> encryptionDetails;

  public TriggerDeploymentNeededRequest() {
    super(TriggerCommandType.DEPLOYMENT_NEEDED_CHECK);
  }

  @Builder
  public TriggerDeploymentNeededRequest(String accountId, String appId, GitConfig gitConfig, String gitConnectorId,
      String currentCommitId, String oldCommitId, String repoName, String branch, List<String> filePaths,
      List<EncryptedDataDetail> encryptionDetails) {
    super(TriggerCommandType.DEPLOYMENT_NEEDED_CHECK, accountId, appId);
    this.gitConfig = gitConfig;
    this.gitConnectorId = gitConnectorId;
    this.currentCommitId = currentCommitId;
    this.oldCommitId = oldCommitId;
    this.repoName = repoName;
    this.branch = branch;
    this.filePaths = filePaths;
    this.encryptionDetails = encryptionDetails;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return CapabilityHelper.generateDelegateCapabilities(gitConfig, encryptionDetails, maskingEvaluator);
  }
}
