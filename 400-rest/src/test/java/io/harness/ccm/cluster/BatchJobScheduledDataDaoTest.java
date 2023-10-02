/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.cluster;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.dao.BatchJobScheduledDataDao;
import io.harness.ccm.commons.entities.batch.BatchJobScheduledData;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

@TargetModule(HarnessModule._490_CE_COMMONS)
@OwnedBy(CE)
public class BatchJobScheduledDataDaoTest extends WingsBaseTest {
  @InjectMocks @Inject private BatchJobScheduledDataDao batchJobScheduledDataDao;
  @Inject private HPersistence hPersistence;
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private static final String BATCH_JOB_TYPE = "CLUSTER_DATA_TO_BIG_QUERY";
  private final Instant NOW = Instant.now().truncatedTo(ChronoUnit.DAYS);
  private final Instant LAST_PROCESSED_DATA_START_TIME = NOW.minus(2, ChronoUnit.DAYS);
  private final Instant LAST_PROCESSED_DATA_END_TIME = NOW.minus(1, ChronoUnit.DAYS);

  @Before
  public void setUp() {
    hPersistence.save(getBatchJobScheduledData());
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testFetchLastBatchJobScheduledData() {
    BatchJobScheduledData batchJobScheduledData =
        batchJobScheduledDataDao.fetchLastBatchJobScheduledData(ACCOUNT_ID, BATCH_JOB_TYPE);
    assertThat(batchJobScheduledData.getEndAt()).isEqualTo(LAST_PROCESSED_DATA_END_TIME);
    assertThat(batchJobScheduledData.getStartAt()).isEqualTo(LAST_PROCESSED_DATA_START_TIME);
    assertThat(batchJobScheduledData.getBatchJobType()).isEqualTo(BATCH_JOB_TYPE);
  }

  private BatchJobScheduledData getBatchJobScheduledData() {
    return new BatchJobScheduledData(
        ACCOUNT_ID, BATCH_JOB_TYPE, 1200, LAST_PROCESSED_DATA_START_TIME, LAST_PROCESSED_DATA_END_TIME, 111111L);
  }
}
