/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.billing.reader;

import io.harness.batch.processing.billing.writer.support.BillingDataGenerationValidator;
import io.harness.batch.processing.dao.intfc.InstanceDataDao;
import io.harness.ccm.cluster.ClusterRecordService;
import io.harness.ccm.cluster.entities.ClusterRecord;
import io.harness.ccm.commons.beans.InstanceType;
import io.harness.ccm.commons.entities.batch.InstanceData;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
  private io.harness.ccm.commons.service.intf.ClusterRecordService eventsClusterRecordService;
  private BillingDataGenerationValidator billingDataGenerationValidator;

  public InstanceDataReader(InstanceDataDao instanceDataDao, ClusterRecordService clusterRecordService,
      io.harness.ccm.commons.service.intf.ClusterRecordService eventsClusterRecordService,
      BillingDataGenerationValidator billingDataGenerationValidator, String accountId, List<InstanceType> instanceTypes,
      Instant activeInstanceIterator, Instant endTime, int batchSize) {
    this.accountId = accountId;
    this.instanceTypes = instanceTypes;
    this.activeInstanceIterator = activeInstanceIterator;
    this.endTime = endTime;
    this.batchSize = batchSize;
    this.instanceDataDao = instanceDataDao;
    this.clusterRecordService = clusterRecordService;
    this.eventsClusterRecordService = eventsClusterRecordService;
    this.billingDataGenerationValidator = billingDataGenerationValidator;
  }

  public List<InstanceData> getNext() {
    Set<String> clusterIds = new HashSet<>();

    List<ClusterRecord> clusterRecords = clusterRecordService.listCeEnabledClusters(accountId);
    List<io.harness.ccm.commons.entities.ClusterRecord> eventsClusterRecords =
        eventsClusterRecordService.getByAccountId(accountId);

    for (ClusterRecord clusterRecord : clusterRecords) {
      if (billingDataGenerationValidator.shouldGenerateBillingData(
              accountId, clusterRecord.getUuid(), activeInstanceIterator)) {
        clusterIds.add(clusterRecord.getUuid());
      }
    }

    for (io.harness.ccm.commons.entities.ClusterRecord eventsClusterRecord : eventsClusterRecords) {
      if (billingDataGenerationValidator.shouldGenerateBillingData(
              accountId, eventsClusterRecord.getUuid(), activeInstanceIterator)) {
        clusterIds.add(eventsClusterRecord.getUuid());
      }
    }

    log.info("Total clusterIds: {} for accountId: {}", clusterIds.size(), accountId);

    List<InstanceData> instanceDataLists =
        clusterIds.stream()
            .map(clusterId
                -> instanceDataDao.getInstanceDataListsOfTypesAndClusterId(
                    accountId, batchSize, activeInstanceIterator, endTime, instanceTypes, clusterId))
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
