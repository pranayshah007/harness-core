/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.expression.Expression;

import software.wings.beans.ServiceHookDelegateConfig;

import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
public class K8sBlueGreenStageScaleDownRequest implements K8sDeployRequest {
  @Expression(DISALLOW_SECRETS) String releaseName;
  String commandName;
  K8sTaskType taskType;
  Integer timeoutIntervalInMin;
  K8sInfraDelegateConfig k8sInfraDelegateConfig;
  ManifestDelegateConfig manifestDelegateConfig;
  boolean skipSteadyStateCheck;
  CommandUnitsProgress commandUnitsProgress;
  boolean useLatestKustomizeVersion;
  boolean useNewKubectlVersion;
  boolean useK8sApiForSteadyStateCheck;

  @Override
  public List<String> getValuesYamlList() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getKustomizePatchesList() {
    return Collections.emptyList();
  }

  @Override
  public List<String> getOpenshiftParamList() {
    return Collections.emptyList();
  }

  @Override
  public List<ServiceHookDelegateConfig> getServiceHooks() {
    return null;
  }

  @Override
  public K8sTaskType getTaskType() {
    return K8sTaskType.BLUE_GREEN_STAGE_SCALE_DOWN;
  }
}
