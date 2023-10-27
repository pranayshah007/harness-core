/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.states;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.STAGE_EXECUTION;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.UNIQUE_STEP_IDENTIFIERS;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;
import static io.harness.rule.OwnerRule.SAHITHI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.app.beans.entities.CIResourceCleanup;
import io.harness.beans.execution.PublishedImageArtifact;
import io.harness.beans.stages.IntegrationStageStepParametersPMS;
import io.harness.beans.steps.outcome.CIStepArtifactOutcome;
import io.harness.beans.steps.outcome.IntegrationStageOutcome;
import io.harness.beans.steps.outcome.StepArtifacts;
import io.harness.beans.sweepingoutputs.UniqueStepIdentifiersSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.metrics.CIManagerMetricsService;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.persistence.HPersistence;
import io.harness.plancreator.steps.common.StageElementParameters;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.TriggerType;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.pms.sdk.core.steps.io.StepResponse.StepOutcome;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.yaml.ParameterField;
import io.harness.repositories.StepExecutionParametersRepository;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.tasks.ResponseData;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;

import com.google.common.collect.ImmutableMap;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.SneakyThrows;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.CI)
public class IntegrationStageStepPMSTest extends CIExecutionTestBase {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private OutcomeService outcomeService;

  @Mock private StepExecutionParametersRepository stepExecutionParametersRepository;

  @Mock private KryoSerializer kryoSerializer;
  @Mock HPersistence hPersistence;
  @Mock UpdateOperations<CIResourceCleanup> mockUpdateOperations;
  @Mock Query<CIResourceCleanup> mockQuery;
  @Mock private CIManagerMetricsService ciManagerMetricsService;
  @InjectMocks private IntegrationStageStepPMS integrationStageStepPMS;
  private Ambiance ambiance;
  private IntegrationStageStepParametersPMS integrationStageStepParametersPMS;
  private StageElementParameters stageElementParameters;
  private StepInputPackage inputPackage;

  @Before
  public void setUp() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    setupAbstractions.put("projectIdentifier", "projectId");
    setupAbstractions.put("orgIdentifier", "default");

    Level level =
        Level.newBuilder()
            .setStepType(StepType.newBuilder().setStepCategory(StepCategory.STAGE).setType("STAGES_STEP").build())
            .setRuntimeId("runtimeId")
            .setSetupId("setupId")
            .build();

    ambiance =
        Ambiance.newBuilder()
            .putAllSetupAbstractions(setupAbstractions)
            .addLevels(level)
            .setMetadata(ExecutionMetadata.newBuilder()
                             .setTriggerInfo(
                                 ExecutionTriggerInfo.newBuilder().setTriggerType(TriggerType.WEBHOOK_CUSTOM).build())
                             .build())
            .setPlanExecutionId("planExeId")
            .build();

    ParameterField<Boolean> enable = ParameterField.<Boolean>builder().value(true).build();

    CodeBase codeBase = CodeBase.builder().build();

    Infrastructure infrastructure = new K8sDirectInfraYaml();

    integrationStageStepParametersPMS = IntegrationStageStepParametersPMS.builder().build();
    //            .enableCloneRepo(enable)
    //            .codeBase(codeBase)
    //            .infrastructure(infrastructure)
    //           .build();
    stageElementParameters = StageElementParameters.builder().specConfig(integrationStageStepParametersPMS).build();
    inputPackage = StepInputPackage.builder().build();
  }

  @SneakyThrows
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldHandleSuccessfulTaskResultWithOutcomes() {
    List<String> uniqueIdentifiers = Arrays.asList("id1", "id2");
    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put("id", StepResponseNotifyData.builder().status(Status.SUCCEEDED).build())
            .build();
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(UNIQUE_STEP_IDENTIFIERS)))
        .thenReturn(
            OptionalSweepingOutput.builder()
                .found(true)
                .output(UniqueStepIdentifiersSweepingOutput.builder().uniqueStepIdentifiers(uniqueIdentifiers).build())
                .build());
    when(hPersistence.createUpdateOperations(CIResourceCleanup.class)).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.set(anyString(), any())).thenReturn(mockUpdateOperations);
    when(hPersistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);

    uniqueIdentifiers.forEach(uid
        -> when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifact_" + uid)))
               .thenReturn(OptionalOutcome.builder()
                               .found(true)
                               .outcome(CIStepArtifactOutcome.builder()
                                            .stepArtifacts(StepArtifacts.builder()
                                                               .publishedImageArtifacts(Collections.singletonList(
                                                                   PublishedImageArtifact.builder()
                                                                       .imageName("image_" + uid)
                                                                       .tag("tag_" + uid)
                                                                       .digest("digest_" + uid)
                                                                       .build()))
                                                               .build())
                                            .build())
                               .build()));

    StepResponse stepResponse =
        integrationStageStepPMS.handleChildResponse(ambiance, stageElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);

    StepOutcome stepOutcome = new ArrayList<>(stepResponse.getStepOutcomes()).get(0);
    IntegrationStageOutcome integrationStageOutcome = (IntegrationStageOutcome) stepOutcome.getOutcome();
    assertThat(integrationStageOutcome.getImageArtifacts()).hasSize(2);

    integrationStageOutcome.getImageArtifacts().forEach(artifact -> {
      assertThat(artifact.getImageName()).isIn("image_id1", "image_id2");
      assertThat(artifact.getTag()).isIn("tag_id1", "tag_id2");
      assertThat(artifact.getDigest()).isIn("digest_id1", "digest_id2");
    });
  }

  @SneakyThrows
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void shouldHandleSuccessfulTaskResultWithBackupOutcomes() {
    List<String> uniqueIdentifiers = Arrays.asList("id1", "id2");
    Map<String, ResponseData> responseDataMap =
        ImmutableMap.<String, ResponseData>builder()
            .put("id", StepResponseNotifyData.builder().status(Status.SUCCEEDED).build())
            .build();
    integrationStageStepParametersPMS.setStepIdentifiers(uniqueIdentifiers);
    when(executionSweepingOutputResolver.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject(STAGE_EXECUTION)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getOutcomeRefObject(UNIQUE_STEP_IDENTIFIERS)))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());

    uniqueIdentifiers.forEach(uid
        -> when(outcomeService.resolveOptional(ambiance, RefObjectUtils.getOutcomeRefObject("artifact_" + uid)))
               .thenReturn(OptionalOutcome.builder()
                               .found(true)
                               .outcome(CIStepArtifactOutcome.builder()
                                            .stepArtifacts(StepArtifacts.builder()
                                                               .publishedImageArtifacts(Collections.singletonList(
                                                                   PublishedImageArtifact.builder()
                                                                       .imageName("image_" + uid)
                                                                       .tag("tag_" + uid)
                                                                       .digest("digest_" + uid)
                                                                       .build()))
                                                               .build())
                                            .build())
                               .build()));
    when(hPersistence.createUpdateOperations(CIResourceCleanup.class)).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.set(anyString(), any())).thenReturn(mockUpdateOperations);
    when(hPersistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);

    StepResponse stepResponse =
        integrationStageStepPMS.handleChildResponse(ambiance, stageElementParameters, responseDataMap);
    assertThat(stepResponse.getStatus()).isEqualTo(Status.SUCCEEDED);
    assertThat(stepResponse.getStepOutcomes()).isNotNull();
    assertThat(stepResponse.getStepOutcomes()).hasSize(1);

    StepOutcome stepOutcome = new ArrayList<>(stepResponse.getStepOutcomes()).get(0);
    IntegrationStageOutcome integrationStageOutcome = (IntegrationStageOutcome) stepOutcome.getOutcome();
    assertThat(integrationStageOutcome.getImageArtifacts()).hasSize(2);

    integrationStageOutcome.getImageArtifacts().forEach(artifact -> {
      assertThat(artifact.getImageName()).isIn("image_id1", "image_id2");
      assertThat(artifact.getTag()).isIn("tag_id1", "tag_id2");
      assertThat(artifact.getDigest()).isIn("digest_id1", "digest_id2");
    });
  }
  @Test(expected = CIStageExecutionException.class)
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  @SneakyThrows
  public void shouldThrowErrorWhenCodebaseIsEnabledAndBuildPropertiesAreNull() {
    integrationStageStepParametersPMS = IntegrationStageStepParametersPMS.builder()
                                            .enableCloneRepo(ParameterField.<Boolean>builder().value(true).build())
                                            .codeBase(CodeBase.builder().build())
                                            .infrastructure(new K8sDirectInfraYaml())
                                            .build();
    stageElementParameters = StageElementParameters.builder().specConfig(integrationStageStepParametersPMS).build();
    ChildExecutableResponse childExecutableResponse =
        integrationStageStepPMS.obtainChild(ambiance, stageElementParameters, inputPackage);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void shouldNotThrowErrorWhenCodebaseIsNotEnabledAndBuildPropertiesAreNull() {
    CodeBase codeBase = CodeBase.builder()
                            .connectorRef(ParameterField.<String>builder().value("gitConnector").build())
                            .repoName(ParameterField.<String>builder().value("repoName").build())
                            .build();

    integrationStageStepParametersPMS = IntegrationStageStepParametersPMS.builder()
                                            .enableCloneRepo(ParameterField.<Boolean>builder().value(false).build())
                                            .codeBase(codeBase)
                                            .infrastructure(new K8sDirectInfraYaml())
                                            .cloneManually(true)
                                            .childNodeID("childNodeId")
                                            .build();
    stageElementParameters = StageElementParameters.builder().specConfig(integrationStageStepParametersPMS).build();

    when(hPersistence.createUpdateOperations(CIResourceCleanup.class)).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.setOnInsert(anyString(), any())).thenReturn(mockUpdateOperations);
    when(hPersistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);

    ChildExecutableResponse childExecutableResponse =
        integrationStageStepPMS.obtainChild(ambiance, stageElementParameters, inputPackage);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void shouldNotThrowErrorWhenCodebaseIsEnabledAndBuildPropertiesAreNotNull() {
    Build build = new Build();
    build.setSpec(BranchBuildSpec.builder().branch(ParameterField.createValueField("main")).build());
    build.setType(BuildType.BRANCH);

    CodeBase codeBase = CodeBase.builder()
                            .connectorRef(ParameterField.<String>builder().value("gitConnector").build())
                            .repoName(ParameterField.<String>builder().value("repoName").build())
                            .build(ParameterField.createValueField(build))
                            .build();

    integrationStageStepParametersPMS = IntegrationStageStepParametersPMS.builder()
                                            .enableCloneRepo(ParameterField.<Boolean>builder().value(true).build())
                                            .codeBase(codeBase)
                                            .infrastructure(new K8sDirectInfraYaml())
                                            .cloneManually(true)
                                            .childNodeID("childNodeId")
                                            .build();
    stageElementParameters = StageElementParameters.builder().specConfig(integrationStageStepParametersPMS).build();

    when(hPersistence.createUpdateOperations(CIResourceCleanup.class)).thenReturn(mockUpdateOperations);
    when(mockUpdateOperations.setOnInsert(anyString(), any())).thenReturn(mockUpdateOperations);
    when(hPersistence.createQuery(CIResourceCleanup.class, excludeAuthority)).thenReturn(mockQuery);
    when(mockQuery.filter(anyString(), any())).thenReturn(mockQuery);

    ChildExecutableResponse childExecutableResponse =
        integrationStageStepPMS.obtainChild(ambiance, stageElementParameters, inputPackage);
  }
}
