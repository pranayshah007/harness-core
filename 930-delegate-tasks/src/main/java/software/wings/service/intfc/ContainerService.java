/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.k8s.model.response.CEK8sDelegatePrerequisite;

import software.wings.beans.TaskType;
import software.wings.beans.infrastructure.instance.info.ContainerInfo;
import software.wings.delegatetasks.DelegateTaskType;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.MasterUrlFetchTaskParameter;

import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(CDP)
public interface ContainerService {
  @DelegateTaskType(TaskType.CONTAINER_ACTIVE_SERVICE_COUNTS)
  Map<String, Integer> getActiveServiceCounts(ContainerServiceParams containerServiceParams);

  @DelegateTaskType(TaskType.CONTAINER_INFO)
  List<ContainerInfo> getContainerInfos(ContainerServiceParams containerServiceParams, boolean isInstanceSync);

  @DelegateTaskType(TaskType.CONTROLLER_NAMES_WITH_LABELS)
  Set<String> getControllerNames(ContainerServiceParams containerServiceParams, Map<String, String> labels);

  @DelegateTaskType(TaskType.CONTAINER_CONNECTION_VALIDATION)
  Boolean validate(ContainerServiceParams containerServiceParams, boolean useNewKubectlVersion);

  @DelegateTaskType(TaskType.CONTAINER_CE_VALIDATION) Boolean validateCE(ContainerServiceParams containerServiceParams);

  @DelegateTaskType(TaskType.CE_DELEGATE_VALIDATION)
  CEK8sDelegatePrerequisite validateCEK8sDelegate(ContainerServiceParams containerServiceParams);

  @DelegateTaskType(TaskType.LIST_CLUSTERS) List<String> listClusters(ContainerServiceParams containerServiceParams);

  @DelegateTaskType(TaskType.FETCH_MASTER_URL)
  String fetchMasterUrl(MasterUrlFetchTaskParameter masterUrlFetchTaskParameter);
}
