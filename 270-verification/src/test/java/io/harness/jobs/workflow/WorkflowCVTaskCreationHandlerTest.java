/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.jobs.workflow;

import static io.harness.rule.OwnerRule.KAMAL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import io.harness.VerificationBase;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.dl.WingsPersistence;
import software.wings.service.impl.analysis.AnalysisContext;
import software.wings.service.impl.splunk.SplunkDataCollectionInfoV2;
import software.wings.service.intfc.verification.CVTaskService;

import com.google.inject.Inject;
import java.util.UUID;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class WorkflowCVTaskCreationHandlerTest extends VerificationBase {
  @Mock CVTaskService cvTaskService;
  @Inject WingsPersistence wingsPersistence;
  @Inject WorkflowCVTaskCreationHandler workflowCVTaskCreationHandler;

  @Before
  public void setupTests() throws IllegalAccessException {
    workflowCVTaskCreationHandler = spy(workflowCVTaskCreationHandler);
    FieldUtils.writeField(workflowCVTaskCreationHandler, "cvTaskService", cvTaskService, true);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandle_withDataCollectionInfoIsNull() {
    AnalysisContext analysisContext = AnalysisContext.builder().stateExecutionId(UUID.randomUUID().toString()).build();
    analysisContext.setCvTasksCreated(false);
    wingsPersistence.save(analysisContext);
    workflowCVTaskCreationHandler.handle(analysisContext);
    AnalysisContext updatedContext = wingsPersistence.get(AnalysisContext.class, analysisContext.getUuid());
    assertThat(updatedContext.isCvTasksCreated()).isTrue();
    verifyZeroInteractions(cvTaskService);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testHandle_withDataCollectionInfoIsNotNull() {
    AnalysisContext analysisContext = AnalysisContext.builder().stateExecutionId(UUID.randomUUID().toString()).build();
    analysisContext.setDataCollectionInfov2(SplunkDataCollectionInfoV2.builder().build());
    analysisContext.setCvTasksCreated(false);
    wingsPersistence.save(analysisContext);
    workflowCVTaskCreationHandler.handle(analysisContext);
    AnalysisContext updatedContext = wingsPersistence.get(AnalysisContext.class, analysisContext.getUuid());
    assertThat(updatedContext.isCvTasksCreated()).isTrue();
    verify(cvTaskService, times(1)).createCVTasks(eq(analysisContext));
  }
}
