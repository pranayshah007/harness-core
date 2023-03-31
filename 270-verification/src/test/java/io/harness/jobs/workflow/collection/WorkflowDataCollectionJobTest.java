/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jobs.workflow.collection;

import static io.harness.rule.OwnerRule.RAGHU;

import static software.wings.common.VerificationConstants.DELAY_MINUTES;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.VerificationBase;
import io.harness.category.element.UnitTests;
import io.harness.managerclient.VerificationManagerClientHelper;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.service.intfc.ContinuousVerificationService;

import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.sm.StateType;

import com.google.inject.Inject;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class WorkflowDataCollectionJobTest extends VerificationBase {
  @Mock private VerificationManagerClientHelper verificationManagerClientHelper;
  @Mock private ContinuousVerificationService continuousVerificationService;
  @Inject private WorkflowDataCollectionJob workflowDataCollectionJob;

  @Before
  public void setUp() throws Exception {
    FieldUtils.writeField(
        workflowDataCollectionJob, "verificationManagerClientHelper", verificationManagerClientHelper, true);
    FieldUtils.writeField(
        workflowDataCollectionJob, "continuousVerificationService", continuousVerificationService, true);
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testHandle_whenFlagDisabled() {
    when(verificationManagerClientHelper.callManagerWithRetry(any())).thenReturn(new RestResponse<>(false));
    workflowDataCollectionJob.handle(AnalysisContext.builder().build());
    verify(continuousVerificationService, never()).triggerWorkflowDataCollection(any());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testHandle_withoutDelay() {
    when(verificationManagerClientHelper.callManagerWithRetry(any())).thenReturn(new RestResponse<>(true));
    workflowDataCollectionJob.handle(AnalysisContext.builder().createdAt(Instant.now().toEpochMilli()).build());
    verify(continuousVerificationService, never()).triggerWorkflowDataCollection(any());
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testHandle_withDelay() {
    when(verificationManagerClientHelper.callManagerWithRetry(any())).thenReturn(new RestResponse<>(true));
    workflowDataCollectionJob.handle(
        AnalysisContext.builder()
            .stateType(StateType.NEW_RELIC)
            .createdAt(Instant.now().minus(DELAY_MINUTES, ChronoUnit.MINUTES).toEpochMilli())
            .build());
    verify(continuousVerificationService, atLeastOnce()).triggerWorkflowDataCollection(any());
  }
}
