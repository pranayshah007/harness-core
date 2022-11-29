/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.dao.InstanceDataDao;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.service.intf.InstanceDataService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CE)
@Slf4j
public class InstanceDataServiceImpl implements InstanceDataService {
  @Inject InstanceDataDao instanceDataDao;

  @Override
  public InstanceData get(String instanceId) {
    return instanceDataDao.get(instanceId);
  }

  @Override
  public List<InstanceData> fetchInstanceDataForGivenInstances(List<String> instanceIds) {
    return instanceDataDao.fetchInstanceDataForGivenInstances(instanceIds);
  }

  @Override
  public Map<String, Map<String, String>> fetchLabelsForGivenInstances(String accountId, List<String> instanceIds) {
    try {
      Map<String, Map<String, String>> instanceIdLabelMap = new HashMap<>();

      List<InstanceData> instanceDataList = instanceDataDao.fetchInstanceDataForGivenInstances(instanceIds);
      for (InstanceData instanceData : instanceDataList) {
        if (instanceData.getAccountId().equals(accountId)) {
          instanceIdLabelMap.put(instanceData.getInstanceId(), getLabelMap(instanceData));
        }
      }
      return instanceIdLabelMap;
    } catch (Exception ex) {
      log.error("Exception while fetching labels", ex);
      return Collections.emptyMap();
    }
  }

  /**
   * the precedence of labels is workloadLabels > namespaceLabels > podLabels
   */
  private Map<String, String> getLabelMap(InstanceData instanceData) {
    Map<String, String> labelsMap = new HashMap<>();
    if (instanceData.getLabels() != null) {
      labelsMap.putAll(instanceData.getLabels());
    }
    if (instanceData.getNamespaceLabels() != null) {
      labelsMap.putAll(instanceData.getNamespaceLabels());
    }
    if (instanceData.getTopOwnerLabels() != null) {
      labelsMap.putAll(instanceData.getTopOwnerLabels());
    }
    return labelsMap;
  }

  @Override
  public List<InstanceData> fetchInstanceDataForGivenInstances(
      String accountId, String clusterId, List<String> instanceIds) {
    return instanceDataDao.fetchInstanceDataForGivenInstances(accountId, clusterId, instanceIds);
  }
}
