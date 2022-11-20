/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.dao.ClusterRecordDao;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.commons.service.intf.ClusterRecordService;

import com.google.inject.Inject;
import java.util.List;

@OwnedBy(CE)
public class ClusterRecordServiceImpl implements ClusterRecordService {
  @Inject private ClusterRecordDao clusterRecordDao;

  @Override
  public ClusterRecord upsert(ClusterRecord clusterRecord) {
    return clusterRecordDao.upsert(clusterRecord);
  }

  @Override
  public ClusterRecord get(String uuid) {
    return clusterRecordDao.get(uuid);
  }

  @Override
  public ClusterRecord get(String accountId, String k8sBaseConnectorRefIdentifier) {
    return clusterRecordDao.get(accountId, k8sBaseConnectorRefIdentifier);
  }

  @Override
  public boolean delete(String accountId, String ceK8sConnectorIdentifier) {
    return clusterRecordDao.delete(accountId, ceK8sConnectorIdentifier);
  }

  @Override
  public ClusterRecord getByCEK8sIdentifier(String accountId, String ceK8sConnectorIdentifier) {
    return clusterRecordDao.getByCEK8sIdentifier(accountId, ceK8sConnectorIdentifier);
  }

  @Override
  public List<ClusterRecord> getByCEK8sIdentifierList(String accountId, List<String> ceK8sConnectorIdentifier) {
    return clusterRecordDao.getByCEK8sIdentifierList(accountId, ceK8sConnectorIdentifier);
  }

  @Override
  public ClusterRecord attachTask(ClusterRecord clusterRecord, String taskId) {
    return clusterRecordDao.insertTask(clusterRecord, taskId);
  }
}
