/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.expression.Expression.ALLOW_SECRETS;

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
public class K8sDeleteRequest implements K8sDeployRequest {
  String accountId;
  String releaseName;
  String commandName;
  K8sTaskType taskType;
  Integer timeoutIntervalInMin;
  @Expression(ALLOW_SECRETS) List<String> valuesYamlList;
  @Expression(ALLOW_SECRETS) List<String> kustomizePatchesList;
  K8sInfraDelegateConfig k8sInfraDelegateConfig;
  ManifestDelegateConfig manifestDelegateConfig;
  private String resources;
  private boolean deleteNamespacesForRelease;
  private String filePaths;
  DeleteResourcesType deleteResourcesType;
  @Builder.Default boolean shouldOpenFetchFilesLogStream = true;
  CommandUnitsProgress commandUnitsProgress;
  boolean useLatestKustomizeVersion;
  boolean useNewKubectlVersion;
  boolean useDeclarativeRollback;

  @Override
  public List<String> getOpenshiftParamList() {
    return Collections.emptyList();
  }

  @Override
  public List<ServiceHookDelegateConfig> getServiceHooks() {
    return null;
  }
}
