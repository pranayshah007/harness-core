/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.api.k8s;

import static io.harness.annotations.dev.HarnessModule._957_CG_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.context.ContextElementType;
import io.harness.k8s.model.KubernetesResourceId;

import software.wings.beans.TaskType;
import software.wings.sm.ContextElement;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@TargetModule(_957_CG_BEANS)
@OwnedBy(CDP)
public class K8sContextElement implements ContextElement {
  String releaseName;
  Integer releaseNumber;
  Integer targetInstances;
  TaskType currentTaskType;
  List<String> delegateSelectors;
  List<KubernetesResourceId> prunedResourcesIds;

  @Override
  public ContextElementType getElementType() {
    return ContextElementType.K8S;
  }

  @Override
  public String getUuid() {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public ContextElement cloneMin() {
    return null;
  }
}
