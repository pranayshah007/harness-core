/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.execution.expansion;

import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

import io.harness.CategoryTest;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.plan.PlanExecutionMetadataService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.data.PmsOutcome;
import io.harness.pms.data.stepparameters.PmsStepParameters;
import io.harness.repositories.planExecutionJson.PlanExecutionExpansionRepository;
import io.harness.rule.Owner;
import io.harness.utils.PmsFeatureFlagService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.assertj.core.util.Maps;
import org.bson.Document;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.data.mongodb.core.query.Update;

public class PlanExpansionServiceImplTest extends CategoryTest {
  private static final String PLAN_EXECUTION_ID = "planExecutionId";
  private static final String PIPELINE = "pipeline";
  private static final String SPEC = "spec";

  @Mock PlanExecutionExpansionRepository planExecutionExpansionRepository;
  @Mock PlanExecutionMetadataService planExecutionMetadataService;
  @Mock PmsFeatureFlagService pmsFeatureFlagService;

  @InjectMocks PlanExpansionServiceImpl planExpansionService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testGetExpansionPathUsingLevels() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .addLevels(Level.newBuilder().setIdentifier(PIPELINE).setSkipExpressionChain(false).build())
                            .addLevels(Level.newBuilder().setIdentifier("spec").setSkipExpressionChain(true).build())
                            .build();

    assertThat(planExpansionService.getExpansionPathUsingLevels(ambiance))
        .isEqualTo(String.format("%s.%s", PlanExpansionConstants.EXPANDED_JSON, PIPELINE));

    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .addLevels(Level.newBuilder().setIdentifier(PIPELINE).setSkipExpressionChain(false).build())
                   .addLevels(Level.newBuilder().setIdentifier(SPEC).setSkipExpressionChain(false).build())
                   .build();

    assertThat(planExpansionService.getExpansionPathUsingLevels(ambiance))
        .isEqualTo(String.format("%s.%s.%s", PlanExpansionConstants.EXPANDED_JSON, PIPELINE, SPEC));
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testUpdateStatus() {
    Mockito.when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .putSetupAbstractions("accountId", "accountId")
                            .addLevels(Level.newBuilder().setIdentifier(PIPELINE).setSkipExpressionChain(false).build())
                            .addLevels(Level.newBuilder().setIdentifier("spec").setSkipExpressionChain(true).build())
                            .build();

    planExpansionService.updateStatus(ambiance, Status.SUCCEEDED);
    Mockito.verifyNoInteractions(planExecutionExpansionRepository);

    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .putSetupAbstractions("accountId", "accountId")
                   .addLevels(Level.newBuilder().setIdentifier(PIPELINE).setSkipExpressionChain(false).build())
                   .addLevels(Level.newBuilder().setIdentifier(SPEC).setSkipExpressionChain(false).build())
                   .build();

    Mockito.when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    planExpansionService.updateStatus(ambiance, Status.SUCCEEDED);
    ArgumentCaptor<String> planExecutionCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(planExecutionExpansionRepository).update(planExecutionCaptor.capture(), updateCaptor.capture());
    Update update = updateCaptor.getValue();
    Set<String> fieldsUpdated = new HashSet<>();
    if (update.getUpdateObject().containsKey("$set")) {
      fieldsUpdated.addAll(((Document) update.getUpdateObject().get("$set")).keySet());
    }
    assertThat(fieldsUpdated.size()).isEqualTo(1);
    assertThat(fieldsUpdated.iterator().next()).isEqualTo("expandedJson.pipeline.spec.status");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddOutcomes() {
    Mockito.when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .putSetupAbstractions("accountId", "accountId")
                            .addLevels(Level.newBuilder().setIdentifier(PIPELINE).setSkipExpressionChain(false).build())
                            .addLevels(Level.newBuilder().setIdentifier("spec").setSkipExpressionChain(true).build())
                            .build();

    planExpansionService.addOutcomes(ambiance, "name", PmsOutcome.parse(new HashMap<>()));
    Mockito.verifyNoInteractions(planExecutionExpansionRepository);

    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .putSetupAbstractions("accountId", "accountId")
                   .addLevels(Level.newBuilder().setIdentifier(PIPELINE).setSkipExpressionChain(false).build())
                   .addLevels(Level.newBuilder().setIdentifier(SPEC).setSkipExpressionChain(false).build())
                   .build();

    Mockito.when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    planExpansionService.addOutcomes(ambiance, "name", PmsOutcome.parse(new HashMap<>()));
    ArgumentCaptor<String> planExecutionCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(planExecutionExpansionRepository).update(planExecutionCaptor.capture(), updateCaptor.capture());
    Update update = updateCaptor.getValue();
    Set<String> fieldsUpdated = new HashSet<>();
    if (update.getUpdateObject().containsKey("$set")) {
      fieldsUpdated.addAll(((Document) update.getUpdateObject().get("$set")).keySet());
    }
    assertThat(fieldsUpdated.size()).isEqualTo(1);
    assertThat(fieldsUpdated.iterator().next()).isEqualTo("expandedJson.pipeline.spec.outcome.name");
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testAddStepInputs() {
    Mockito.when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    Ambiance ambiance = Ambiance.newBuilder()
                            .setPlanExecutionId(PLAN_EXECUTION_ID)
                            .putSetupAbstractions("accountId", "accountId")
                            .addLevels(Level.newBuilder().setIdentifier(PIPELINE).setSkipExpressionChain(false).build())
                            .addLevels(Level.newBuilder().setIdentifier("spec").setSkipExpressionChain(true).build())
                            .build();

    planExpansionService.addStepInputs(ambiance, PmsStepParameters.parse(Maps.newHashMap("a", "b")));
    Mockito.verifyNoInteractions(planExecutionExpansionRepository);

    ambiance = Ambiance.newBuilder()
                   .setPlanExecutionId(PLAN_EXECUTION_ID)
                   .putSetupAbstractions("accountId", "accountId")
                   .addLevels(Level.newBuilder().setIdentifier(PIPELINE).setSkipExpressionChain(false).build())
                   .addLevels(Level.newBuilder().setIdentifier(SPEC).setSkipExpressionChain(false).build())
                   .build();

    Mockito.when(pmsFeatureFlagService.isEnabled(anyString(), any(FeatureName.class))).thenReturn(true);
    ArgumentCaptor<Update> updateCaptor = ArgumentCaptor.forClass(Update.class);
    planExpansionService.addStepInputs(ambiance, PmsStepParameters.parse(Maps.newHashMap("a", "b")));
    ArgumentCaptor<String> planExecutionCaptor = ArgumentCaptor.forClass(String.class);
    Mockito.verify(planExecutionExpansionRepository).update(planExecutionCaptor.capture(), updateCaptor.capture());
    Update update = updateCaptor.getValue();
    Set<String> fieldsUpdated = new HashSet<>();
    if (update.getUpdateObject().containsKey("$set")) {
      fieldsUpdated.addAll(((Document) update.getUpdateObject().get("$set")).keySet());
    }
    assertThat(fieldsUpdated.size()).isEqualTo(1);
    assertThat(fieldsUpdated.iterator().next()).isEqualTo("expandedJson.pipeline.spec.stepInputs");
  }
}