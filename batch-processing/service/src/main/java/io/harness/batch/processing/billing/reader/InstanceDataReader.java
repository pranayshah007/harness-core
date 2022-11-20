/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.reader;

import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.entities.ClusterRecord;
import io.harness.ccm.commons.entities.batch.InstanceData;
import io.harness.ccm.commons.service.intf.ClusterRecordService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InstanceDataReader {
  private String accountId;
  private List<InstanceType> instanceTypes;
  private Instant activeInstanceIterator;
  private Instant endTime;
  private int batchSize;
  private InstanceDataDao instanceDataDao;
  private ClusterRecordService clusterRecordService;

  public InstanceDataReader(InstanceDataDao instanceDataDao, ClusterRecordService clusterRecordService,
      String accountId, List<InstanceType> instanceTypes, Instant activeInstanceIterator, Instant endTime,
      int batchSize) {
    this.accountId = accountId;
    this.instanceTypes = instanceTypes;
    this.activeInstanceIterator = activeInstanceIterator;
    this.endTime = endTime;
    this.batchSize = batchSize;
    this.instanceDataDao = instanceDataDao;
    this.clusterRecordService = clusterRecordService;
  }

  public List<InstanceData> getNext() {
    List<ClusterRecord> clusterRecords = clusterRecordService.getByAccountId(accountId);
    List<InstanceData> instanceDataLists =
        clusterRecords.stream()
            .map(clusterRecord
                -> instanceDataDao.getInstanceDataListsOfTypesAndClusterId(
                    accountId, batchSize, activeInstanceIterator, endTime, instanceTypes, clusterRecord.getUuid()))
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .collect(Collectors.toList());
    if (!instanceDataLists.isEmpty()) {
      activeInstanceIterator = instanceDataLists.get(instanceDataLists.size() - 1).getActiveInstanceIterator();
      if (accountId.equals("SFDfOzL_Qq-SH3AuAN4yWQ")) {
        Instant batchStartTime = instanceDataLists.get(0).getActiveInstanceIterator();
        log.info("Instance details reader sec {} : {} : {}", batchSize, activeInstanceIterator, batchStartTime);
        int value = batchStartTime.compareTo(activeInstanceIterator);
        if (value > 0) {
          activeInstanceIterator = batchStartTime.plus(1, ChronoUnit.MILLIS);
          log.info(
              "Instance details reader val is greater {} : {} : {}", batchSize, activeInstanceIterator, batchStartTime);
        }
      }
      if (instanceDataLists.get(0).getActiveInstanceIterator().equals(activeInstanceIterator)) {
        log.info("Incrementing lastActiveInstanceIterator by 1ms {} {} {} {}", instanceDataLists.size(),
            activeInstanceIterator, endTime, accountId);
        activeInstanceIterator = activeInstanceIterator.plus(1, ChronoUnit.MILLIS);
      }
    }
    return instanceDataLists;
  }
}
