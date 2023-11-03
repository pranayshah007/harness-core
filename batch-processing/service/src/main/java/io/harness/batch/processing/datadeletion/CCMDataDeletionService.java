/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.datadeletion;

import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStatus.COMPLETE;
import static io.harness.ccm.commons.entities.datadeletion.DataDeletionStatus.INCOMPLETE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.batch.processing.datadeletion.aws.AWSDataDeletionHandler;
import io.harness.batch.processing.datadeletion.azure.AzureDataDeletionHandler;
import io.harness.batch.processing.datadeletion.dkron.DkronDeletionHandler;
import io.harness.batch.processing.datadeletion.gcp.GCPDataDeletionHandler;
import io.harness.batch.processing.datadeletion.lightwing.AutoCudDeletionHandler;
import io.harness.batch.processing.datadeletion.lightwing.AutoStoppingDeletionHandler;
import io.harness.batch.processing.datadeletion.logcontext.DataDeletionBucketLogContext;
import io.harness.batch.processing.datadeletion.mongoeventsdb.MongoEventsDbDeletionHandler;
import io.harness.batch.processing.datadeletion.timescale.TimescaleDbDeletionHandler;
import io.harness.ccm.commons.dao.DataDeletionRecordDao;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionBucket;
import io.harness.ccm.commons.entities.datadeletion.DataDeletionRecord;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import com.google.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Singleton
@Slf4j
public class CCMDataDeletionService {
  private static final Integer MAX_RETRIES = 5;
  private static final List<DataDeletionBucket> DATA_DELETION_BUCKETS_IN_ORDER =
      Arrays.stream(DataDeletionBucket.values())
          .sorted(Comparator.comparing(DataDeletionBucket::getPriorityOrder))
          .collect(Collectors.toList());

  @Autowired private DataDeletionRecordDao dataDeletionRecordDao;
  @Autowired private AWSDataDeletionHandler awsDataDeletionHandler;
  @Autowired private AzureDataDeletionHandler azureDataDeletionHandler;
  @Autowired private GCPDataDeletionHandler gcpDataDeletionHandler;
  @Autowired private DkronDeletionHandler dkronDeletionHandler;
  @Autowired private MongoEventsDbDeletionHandler mongoEventsDbDeletionHandler;
  @Autowired private TimescaleDbDeletionHandler timescaleDbDeletionHandler;
  @Autowired private AutoStoppingDeletionHandler autoStoppingDeletionHandler;
  @Autowired private AutoCudDeletionHandler autoCudDeletionHandler;

  public void processDataDeletionRecords() {
    Long timestampThreshold = Instant.now().minus(5, ChronoUnit.HOURS).toEpochMilli();
    List<DataDeletionRecord> dataDeletionRecords =
        dataDeletionRecordDao.getRecordsToProcess(MAX_RETRIES, timestampThreshold);
    for (DataDeletionRecord dataDeletionRecord : dataDeletionRecords) {
      try (AutoLogContext ignore = new AccountLogContext(dataDeletionRecord.getAccountId(), OVERRIDE_ERROR)) {
        log.info("Processing Data Deletion Record: {}", dataDeletionRecord);
        for (DataDeletionBucket bucket : DATA_DELETION_BUCKETS_IN_ORDER) {
          try (AutoLogContext ignore2 = new DataDeletionBucketLogContext(bucket.name(), OVERRIDE_ERROR)) {
            switch (bucket) {
              case AWS_DELETION:
                awsDataDeletionHandler.executeSteps(dataDeletionRecord);
                break;
              case AZURE_DELETION:
                azureDataDeletionHandler.executeSteps(dataDeletionRecord);
                break;
              case GCP_DELETION:
                gcpDataDeletionHandler.executeSteps(dataDeletionRecord);
                break;
              case DKRON_DELETION:
                dkronDeletionHandler.executeSteps(dataDeletionRecord);
                break;
              case MONGO_EVENTS_DB_DELETION:
                mongoEventsDbDeletionHandler.executeSteps(dataDeletionRecord);
                break;
              case TIMESCALE_DB_DELETION:
                timescaleDbDeletionHandler.executeSteps(dataDeletionRecord);
                break;
              case AUTOSTOPPING_DELETION:
                autoStoppingDeletionHandler.executeSteps(dataDeletionRecord);
                break;
              case AUTOCUD_DELETION:
                autoCudDeletionHandler.executeSteps(dataDeletionRecord);
                break;
              default:
                log.warn("Deletion not implemented for bucket: {}", bucket.name());
                break;
            }
          }
        }
        // Update dataDeletionRecord's status and lastProcessedAt
        if (dataDeletionRecord.getAutoStoppingStatus().equals(COMPLETE)
            && dataDeletionRecord.getAutoCudStatus().equals(COMPLETE)) {
          boolean allComplete = dataDeletionRecord.getRecords().values().stream().allMatch(
              stepRecord -> stepRecord.getStatus() == COMPLETE);
          dataDeletionRecord.setStatus(allComplete ? COMPLETE : INCOMPLETE);
        } else {
          dataDeletionRecord.setStatus(INCOMPLETE);
        }
        dataDeletionRecord.setLastProcessedAt(Instant.now().toEpochMilli());
        dataDeletionRecordDao.save(dataDeletionRecord);
        log.info("Data Deletion completed for account: {}", dataDeletionRecord.getAccountId());
      }
    }
  }
}
