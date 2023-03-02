/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.instancesync;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.DelegateMetaInfo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.ng.core.k8s.ServiceSpecType;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class AwsLambdaInstanceSyncPerpetualTaskResponse implements InstanceSyncPerpetualTaskResponse {
  private DelegateMetaInfo delegateMetaInfo;
  private List<ServerInstanceInfo> serverInstanceDetails;
  private String errorMessage;
  private CommandExecutionStatus commandExecutionStatus;

  @Override
  public String getDeploymentType() {
    return ServiceSpecType.AWS_LAMBDA;
  }
}
