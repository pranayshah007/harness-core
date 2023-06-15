/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.rancher;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.ExecutionCapabilityDemander;
import io.harness.delegate.task.ActivityAccess;
import io.harness.delegate.task.Cd1ApplicationAccess;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.ExpressionEvaluator;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.ClusterSelectionCriteriaEntry;
import software.wings.beans.RancherConfig;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@TargetModule(HarnessModule._950_DELEGATE_TASKS_BEANS)
@OwnedBy(CDP)
public class RancherResolveClustersTaskParameters
    implements TaskParameters, ActivityAccess, ExecutionCapabilityDemander, Cd1ApplicationAccess {
  private RancherConfig rancherConfig;
  private List<ClusterSelectionCriteriaEntry> clusterSelectionCriteria;
  private String activityId;
  private String appId;
  private List<EncryptedDataDetail> encryptedDataDetails;
  private int timeout;
  private boolean timeoutSupported;

  @Builder
  public RancherResolveClustersTaskParameters(RancherConfig rancherConfig,
      List<ClusterSelectionCriteriaEntry> clusterSelectionCriteria, List<EncryptedDataDetail> encryptedDataDetails,
      String activityId, String appId, boolean timeoutSupported, int timeout) {
    this.rancherConfig = rancherConfig;
    this.clusterSelectionCriteria = clusterSelectionCriteria;
    this.encryptedDataDetails = encryptedDataDetails;
    this.activityId = activityId;
    this.appId = appId;
    this.timeoutSupported = timeoutSupported;
    this.timeout = timeout;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    return this.rancherConfig.fetchRequiredExecutionCapabilities(maskingEvaluator);
  }
}
