/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.transformer.changeEvent;

import io.harness.cvng.activity.entities.KubernetesClusterActivity;
import io.harness.cvng.beans.change.ChangeEventDTO;
import io.harness.cvng.beans.change.KubernetesChangeEventMetadata;

import java.time.Instant;
import java.util.List;

public class KubernetesClusterChangeEventMetadataTransformer
    extends ChangeEventMetaDataTransformer<KubernetesClusterActivity, KubernetesChangeEventMetadata> {
  @Override
  public KubernetesClusterActivity getEntity(ChangeEventDTO changeEventDTO) {
    KubernetesChangeEventMetadata metadata = (KubernetesChangeEventMetadata) changeEventDTO.getMetadata();
    return KubernetesClusterActivity.builder()
        .accountId(changeEventDTO.getAccountId())
        .activityName("Kubernetes " + metadata.getResourceType() + " event.")
        .orgIdentifier(changeEventDTO.getOrgIdentifier())
        .projectIdentifier(changeEventDTO.getProjectIdentifier())
        .eventTime(Instant.ofEpochMilli(changeEventDTO.getEventTime()))
        .changeSourceIdentifier(changeEventDTO.getChangeSourceIdentifier())
        .monitoredServiceIdentifier(changeEventDTO.getMonitoredServiceIdentifier())
        .type(changeEventDTO.getType().getActivityType())
        .oldYaml(metadata.getOldYaml())
        .newYaml(metadata.getNewYaml())
        .resourceType(metadata.getResourceType())
        .action(metadata.getAction())
        .kind(metadata.getKind())
        .reason(metadata.getReason())
        .namespace(metadata.getNamespace())
        .workload(metadata.getWorkload())
        .activityStartTime(metadata.getTimestamp())
        .build();
  }

  @Override
  protected KubernetesChangeEventMetadata getMetadata(KubernetesClusterActivity activity) {
    List<String> dependentMonitoredServiceIdentifiers = activity.getRealatedAppMonitoredServiceIdentifiers();
    return KubernetesChangeEventMetadata.builder()
        .oldYaml(activity.getOldYaml())
        .newYaml(activity.getNewYaml())
        .resourceType(activity.getResourceType())
        .action(activity.getAction())
        .kind(activity.getKind())
        .reason(activity.getReason())
        .namespace(activity.getNamespace())
        .workload(activity.getWorkload())
        .timestamp(activity.getEventTime())
        .resourceVersion(activity.getResourceVersion())
        .dependentMonitoredService(
            !dependentMonitoredServiceIdentifiers.isEmpty() ? dependentMonitoredServiceIdentifiers.get(0) : null)
        .build();
  }

  @Override
  public List<String> getMonitoredServiceIdentifiers(KubernetesClusterActivity activity) {
    return activity.getRealatedAppMonitoredServiceIdentifiers();
  }
}
