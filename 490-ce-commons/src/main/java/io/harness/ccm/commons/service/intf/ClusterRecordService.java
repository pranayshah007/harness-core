/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.service.intf;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.ClusterRecord;

import java.util.List;

@OwnedBy(CE)
public interface ClusterRecordService {
  ClusterRecord upsert(ClusterRecord clusterRecord);
  ClusterRecord get(String uuid);
  ClusterRecord get(String accountId, String k8sBaseConnectorRefIdentifier);
  boolean delete(String accountId, String ceK8sConnectorIdentifier);
  ClusterRecord getByCEK8sIdentifier(String accountId, String ceK8sConnectorIdentifier);
  List<ClusterRecord> getByCEK8sIdentifierList(String accountId, List<String> ceK8sConnectorIdentifier);
  ClusterRecord attachTask(ClusterRecord clusterRecord, String taskId);
}
