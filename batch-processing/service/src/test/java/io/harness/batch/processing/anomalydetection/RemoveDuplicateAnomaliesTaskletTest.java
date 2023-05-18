/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.anomalydetection;

import static io.harness.rule.OwnerRule.HITESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.category.element.UnitTests;
import io.harness.ccm.anomaly.entities.AnomalyEntity;
import io.harness.ccm.anomaly.service.itfc.AnomalyService;
import io.harness.rule.Owner;

import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@RunWith(MockitoJUnitRunner.class)
public class RemoveDuplicateAnomaliesTaskletTest extends CategoryTest {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";
  private final Instant NOW = Instant.now();
  private final long START_TIME_MILLIS = NOW.minus(1, ChronoUnit.HOURS).toEpochMilli();
  private final long END_TIME_MILLIS = NOW.toEpochMilli();

  @InjectMocks private RemoveDuplicateAnomaliesTasklet tasklet;
  @Mock private AnomalyService anomalyService;

  private ChunkContext chunkContext;

  @Captor private ArgumentCaptor<List<String>> duplicateAnomalyArgumentCaptor;
  @Captor private ArgumentCaptor<Instant> dateArgumentCaptor;

  @Before
  public void setup() throws SQLException {
    chunkContext = mock(ChunkContext.class);
    StepContext stepContext = mock(StepContext.class);
    StepExecution stepExecution = mock(StepExecution.class);
    JobParameters parameters = mock(JobParameters.class);

    when(chunkContext.getStepContext()).thenReturn(stepContext);
    when(stepContext.getStepExecution()).thenReturn(stepExecution);
    when(stepExecution.getJobParameters()).thenReturn(parameters);

    when(parameters.getString(CCMJobConstants.ACCOUNT_ID)).thenReturn(ACCOUNT_ID);
    when(parameters.getString(CCMJobConstants.JOB_START_DATE)).thenReturn(String.valueOf(START_TIME_MILLIS));
    when(parameters.getString(CCMJobConstants.JOB_END_DATE)).thenReturn(String.valueOf(END_TIME_MILLIS));
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testExecute() throws Exception {
    when(anomalyService.list(ACCOUNT_ID, Instant.ofEpochMilli(START_TIME_MILLIS))).thenReturn(getAnomalyList());
    tasklet.execute(null, chunkContext);
    verify(anomalyService).delete(duplicateAnomalyArgumentCaptor.capture(), dateArgumentCaptor.capture());
    List<String> duplicateAnomalyIds = duplicateAnomalyArgumentCaptor.getAllValues().get(0);
    assertThat(duplicateAnomalyIds).hasSize(3);
    assertThat(duplicateAnomalyIds).containsExactly("i5", "i6", "i1");
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void shouldNotRemoveIfNotParent() throws Exception {
    when(anomalyService.list(ACCOUNT_ID, Instant.ofEpochMilli(START_TIME_MILLIS))).thenReturn(getAnomalyList2());
    tasklet.execute(null, chunkContext);
    verify(anomalyService).delete(duplicateAnomalyArgumentCaptor.capture(), dateArgumentCaptor.capture());
    List<String> duplicateAnomalyIds = duplicateAnomalyArgumentCaptor.getAllValues().get(0);
    assertThat(duplicateAnomalyIds).hasSize(3);
    assertThat(duplicateAnomalyIds).containsExactly("i5", "i6", "i1");
  }

  private List<AnomalyEntity> getAnomalyList2() {
    return Arrays.asList(getClusterAnomaly("i0", "c0", null, null), getClusterAnomaly("i1", "c1", "n1", null),
        getClusterAnomaly("i2", "c1", "n1", "w1"), getClusterAnomaly("i3", "c2", "n1", "w2"),
        getGCPAnomaly("i4", "pr1", "pj2", "g1"), getGCPAnomaly("i5", "pr1", "pj2", null),
        getGCPAnomaly("i6", "pr1", null, null));
  }

  private List<AnomalyEntity> getAnomalyList() {
    return Arrays.asList(getClusterAnomaly("i1", "c1", "n1", null), getClusterAnomaly("i2", "c1", "n1", "w1"),
        getClusterAnomaly("i3", "c2", "n1", "w2"), getGCPAnomaly("i4", "pr1", "pj2", "g1"),
        getGCPAnomaly("i5", "pr1", "pj2", null), getGCPAnomaly("i6", "pr1", null, null));
  }

  private AnomalyEntity getClusterAnomaly(String id, String clusterId, String namespace, String workloadName) {
    return AnomalyEntity.builder()
        .actualCost(10.2)
        .id(id)
        .clusterId(clusterId)
        .namespace(namespace)
        .workloadName(workloadName)
        .build();
  }

  private AnomalyEntity getGCPAnomaly(String id, String gcpProject, String gcpProduct, String gcpSku) {
    return AnomalyEntity.builder()
        .actualCost(10.3)
        .id(id)
        .gcpProject(gcpProject)
        .gcpProduct(gcpProduct)
        .gcpSKUId(gcpSku)
        .build();
  }
}
