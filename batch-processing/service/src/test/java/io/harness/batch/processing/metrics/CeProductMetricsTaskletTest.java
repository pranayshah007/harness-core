/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.metrics;

import static io.harness.batch.processing.ccm.CCMJobConstants.ACCOUNT_ID;
import static io.harness.rule.OwnerRule.HANTANG;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.category.element.UnitTests;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.event.handler.segment.SegmentConfig;
import io.harness.rule.Owner;

import software.wings.beans.Account;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;

@RunWith(MockitoJUnitRunner.class)
public class CeProductMetricsTaskletTest extends CategoryTest {
  private String accountId = "accountId";
  private Account account = Account.Builder.anAccount()
                                .withUuid(accountId)
                                .withCompanyName("COMPANY_NAME")
                                .withCeLicenseInfo(CeLicenseInfo.builder().build())
                                .build();
  private static final Instant instant = Instant.now();
  private static final long startTime = instant.toEpochMilli();
  private static final long endTime = instant.plus(1, ChronoUnit.DAYS).toEpochMilli();
  private ChunkContext chunkContext;

  private SegmentConfig segmentConfig = SegmentConfig.builder().enabled(true).apiKey("API_KEY").build();
  @Mock private BatchMainConfig batchMainConfig;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;
  @Mock private CeCloudMetricsService ceCloudMetricsService;
  @Mock private ProductMetricsService productMetricsService;
  @Spy @InjectMocks private CeProductMetricsTasklet ceProductMetricsTasklet;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    Map<String, JobParameter> parameters = new HashMap<>();
    parameters.put(CCMJobConstants.JOB_START_DATE, new JobParameter(String.valueOf(startTime), true));
    parameters.put(CCMJobConstants.JOB_END_DATE, new JobParameter(String.valueOf(endTime), true));
    parameters.put(ACCOUNT_ID, new JobParameter(ACCOUNT_ID, true));
    JobParameters jobParameters = new JobParameters(parameters);
    StepExecution stepExecution = new StepExecution("awsBillingDataPipelineStep", new JobExecution(0L, jobParameters));
    chunkContext = new ChunkContext(new StepContext(stepExecution));

    when(batchMainConfig.getSegmentConfig()).thenReturn(segmentConfig);
    when(cloudToHarnessMappingService.getAccountInfoFromId(eq(accountId))).thenReturn(account);
    doNothing().when(ceProductMetricsTasklet).nextGenInstrumentation(ACCOUNT_ID);
  }

  @Test
  @Owner(developers = HANTANG)
  @Category(UnitTests.class)
  public void shouldExecute() throws Exception {
    ceProductMetricsTasklet.execute(null, chunkContext);
    verify(productMetricsService)
        .countTotalEcsTasks(eq(accountId), eq(Instant.ofEpochMilli(startTime).minus(3, ChronoUnit.DAYS)),
            eq(Instant.ofEpochMilli(endTime).minus(3, ChronoUnit.DAYS)));
  }
}
