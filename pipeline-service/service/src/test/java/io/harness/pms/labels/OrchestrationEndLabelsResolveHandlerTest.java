/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.labels;

import static io.harness.rule.OwnerRule.MOHIT_GARG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.expression.common.ExpressionMode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.pipeline.labels.OrchestrationEndLabelsResolveHandler;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.rule.Owner;
import io.harness.yaml.core.NGLabel;

import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

public class OrchestrationEndLabelsResolveHandlerTest extends CategoryTest {
  @Mock ExecutorService executorService;
  @Mock PmsEngineExpressionService pmsEngineExpressionService;
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @InjectMocks OrchestrationEndLabelsResolveHandler orchestrationEndLabelsResolveHandler;

  String ACC_ID = "accountId";
  String ORG_ID = "orgIdentifier";
  String PRO_ID = "projectIdentifier";
  String PLAN_EXECUTION_ID = "planExecutionId";
  Map<String, String> setupAbstractions =
      Maps.of("accountId", ACC_ID, "projectIdentifier", PRO_ID, "orgIdentifier", ORG_ID);

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testOnEndForV1Yaml() {
    PipelineExecutionSummaryEntity dummyEntity = PipelineExecutionSummaryEntity.builder()
                                                     .label(NGLabel.builder().key("key").value("val").build())
                                                     .pipelineVersion(PipelineVersion.V1)
                                                     .build();
    List<NGLabel> dummyLabels = new ArrayList<>();
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setPlanExecutionId(PLAN_EXECUTION_ID).build();
    doReturn(dummyEntity)
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(PLAN_EXECUTION_ID,
            Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.labels,
                PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.pipelineVersion));
    doReturn(dummyLabels)
        .when(pmsEngineExpressionService)
        .resolve(ambiance, dummyEntity.getLabels(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    orchestrationEndLabelsResolveHandler.onEnd(ambiance);

    verify(pmsEngineExpressionService, times(1))
        .resolve(ambiance, dummyEntity.getLabels(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    verify(pmsExecutionSummaryService)
        .update(PLAN_EXECUTION_ID,
            new Update().set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.labels, dummyLabels));
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testOnEndForV0Yaml() {
    PipelineExecutionSummaryEntity dummyEntity = PipelineExecutionSummaryEntity.builder()
                                                     .label(NGLabel.builder().key("key").value("val").build())
                                                     .pipelineVersion(PipelineVersion.V0)
                                                     .build();
    List<NGLabel> dummyLabels = new ArrayList<>();
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setPlanExecutionId(PLAN_EXECUTION_ID).build();
    doReturn(dummyEntity)
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(PLAN_EXECUTION_ID,
            Sets.newHashSet(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.labels,
                PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.pipelineVersion));
    doReturn(dummyLabels)
        .when(pmsEngineExpressionService)
        .resolve(ambiance, dummyEntity.getLabels(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    orchestrationEndLabelsResolveHandler.onEnd(ambiance);

    verify(pmsEngineExpressionService, times(0))
        .resolve(ambiance, dummyEntity.getLabels(), ExpressionMode.RETURN_NULL_IF_UNRESOLVED);
    verify(pmsExecutionSummaryService, times(0))
        .update(PLAN_EXECUTION_ID,
            new Update().set(PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys.labels, dummyLabels));
  }

  @Test
  @Owner(developers = MOHIT_GARG)
  @Category(UnitTests.class)
  public void testGetInformExecutorService() {
    ExecutorService informExecutorService = orchestrationEndLabelsResolveHandler.getInformExecutorService();
    assertThat(informExecutorService).isNotNull();
  }
}
