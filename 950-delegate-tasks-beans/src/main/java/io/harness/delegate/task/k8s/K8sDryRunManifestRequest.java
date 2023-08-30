/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;
import static io.harness.expression.Expression.DISALLOW_SECRETS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.expression.Expression;

import software.wings.beans.ServiceHookDelegateConfig;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(CDP)
public class K8sDryRunManifestRequest implements K8sDeployRequest {
  @Expression(DISALLOW_SECRETS) String releaseName;
  @Expression(ALLOW_SECRETS) List<String> valuesYamlList;
  @Expression(ALLOW_SECRETS) List<String> openshiftParamList;
  @Expression(ALLOW_SECRETS) List<String> kustomizePatchesList;
  String commandName;
  K8sInfraDelegateConfig k8sInfraDelegateConfig;
  ManifestDelegateConfig manifestDelegateConfig;
  boolean inCanaryWorkflow;
  boolean localOverrideFeatureFlag;
  Integer timeoutIntervalInMin;
  String accountId;
  @Builder.Default boolean shouldOpenFetchFilesLogStream = true;
  CommandUnitsProgress commandUnitsProgress;
  boolean useLatestKustomizeVersion;
  boolean useNewKubectlVersion;
  Boolean useDeclarativeRollback;
  @Expression(ALLOW_SECRETS) List<ServiceHookDelegateConfig> serviceHooks;
  boolean disableFabric8;

  @Override
  public K8sTaskType getTaskType() {
    return K8sTaskType.DRY_RUN_MANIFEST;
  }
}
