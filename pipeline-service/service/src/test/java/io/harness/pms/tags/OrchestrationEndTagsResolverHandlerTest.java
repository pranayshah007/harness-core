/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.tags;

import static io.harness.rule.OwnerRule.PRASHANTSHARMA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.data.PmsEngineExpressionService;
import io.harness.ng.core.common.beans.NGTag;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity.PlanExecutionSummaryKeys;
import io.harness.pms.plan.execution.service.PmsExecutionSummaryService;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.rule.Owner;

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

@OwnedBy(HarnessTeam.PIPELINE)
public class OrchestrationEndTagsResolverHandlerTest extends CategoryTest {
  @Mock ExecutorService executorService;
  @Mock PmsEngineExpressionService pmsEngineExpressionService;
  @Mock PmsExecutionSummaryService pmsExecutionSummaryService;
  @InjectMocks OrchestrationEndTagsResolveHandler orchestrationEndTagsResolveHandler;

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
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testOnEnd() {
    PipelineExecutionSummaryEntity dummyEntity = PipelineExecutionSummaryEntity.builder()
                                                     .tag(NGTag.builder().key("key1").value("value1").build())
                                                     .pipelineVersion(PipelineVersion.V0)
                                                     .build();
    List<NGTag> dummyTags = new ArrayList<>();
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setPlanExecutionId(PLAN_EXECUTION_ID).build();
    doReturn(dummyEntity)
        .when(pmsExecutionSummaryService)
        .getPipelineExecutionSummaryWithProjections(PLAN_EXECUTION_ID,
            Sets.newHashSet(PlanExecutionSummaryKeys.tags, PlanExecutionSummaryKeys.pipelineVersion));
    doReturn(dummyTags).when(pmsEngineExpressionService).resolve(ambiance, dummyEntity.getTags(), true);
    orchestrationEndTagsResolveHandler.onEnd(ambiance);

    verify(pmsEngineExpressionService, times(1)).resolve(ambiance, dummyEntity.getTags(), true);
    verify(pmsExecutionSummaryService)
        .update(PLAN_EXECUTION_ID, new Update().set(PlanExecutionSummaryKeys.tags, dummyTags));
  }

  @Test
  @Owner(developers = PRASHANTSHARMA)
  @Category(UnitTests.class)
  public void testGetInformExecutorService() {
    ExecutorService informExecutorService = orchestrationEndTagsResolveHandler.getInformExecutorService();
    assertThat(informExecutorService).isNotNull();
  }
}