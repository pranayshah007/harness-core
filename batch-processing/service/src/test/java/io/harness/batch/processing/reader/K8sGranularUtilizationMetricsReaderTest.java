/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.reader;

import static io.harness.rule.OwnerRule.ROHIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.batch.processing.BatchProcessingTestBase;
import io.harness.batch.processing.billing.timeseries.service.impl.K8sUtilizationGranularDataServiceImpl;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;

@RunWith(MockitoJUnitRunner.class)
public class K8sGranularUtilizationMetricsReaderTest extends BatchProcessingTestBase {
  @Inject @InjectMocks private K8sGranularUtilizationMetricsReader k8sGranularUtilizationMetricsReader;
  @Mock private K8sUtilizationGranularDataServiceImpl k8sUtilizationGranularDataService;
  @Mock private JobParameters parameters;
  @Mock private AtomicBoolean runOnlyOnce;

  private final Instant NOW = Instant.now();
  private final long START_TIME_MILLIS = NOW.minus(1, ChronoUnit.HOURS).toEpochMilli();
  private final long END_TIME_MILLIS = NOW.toEpochMilli();
  private final String INSTANCE_ID = "instanceId";
  private final String ACCOUNT_ID = "accountId";

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(k8sUtilizationGranularDataService.getDistinctInstantIds(ACCOUNT_ID, START_TIME_MILLIS, END_TIME_MILLIS))
        .thenReturn(Collections.singletonList(INSTANCE_ID));
    when(parameters.getString(CCMJobConstants.ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
    when(parameters.getString(CCMJobConstants.JOB_START_DATE)).thenReturn(String.valueOf(START_TIME_MILLIS));
    when(parameters.getString(CCMJobConstants.JOB_END_DATE)).thenReturn(String.valueOf(END_TIME_MILLIS));
    runOnlyOnce = new AtomicBoolean(false);
  }

  @Test
  @Owner(developers = ROHIT)
  @Category(UnitTests.class)
  public void testK8sGranularUtilizationMetricsReader() {
    new K8sGranularUtilizationMetricsReader();
    List<String> list = k8sGranularUtilizationMetricsReader.read();
    assertThat(list.get(0)).isEqualTo(INSTANCE_ID);
    assertThat(k8sGranularUtilizationMetricsReader.read()).isNull();
  }
}
