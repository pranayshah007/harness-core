/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.NAMAN;
import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.kryo.NGCommonsKryoRegistrar;
import io.harness.serializer.kryo.YamlKryoRegistrar;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.yaml.repository.Reference;
import io.harness.yaml.repository.ReferenceType;
import io.harness.yaml.repository.Repository;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class PlanCreatorMergeServiceTest extends CategoryTest {
  private KryoSerializer kryoSerializer;
  private final PmsFeatureFlagService pmsFeatureFlagService = new NoOpPmsFeatureFlagService();
  private final String accountId = "acc";
  private final String orgId = "org";
  private final String projId = "proj";
  private ExecutionMetadata executionMetadata;
  private String pipelineYamlV1;

  @Before
  public void before() {
    kryoSerializer =
        new KryoSerializer(new HashSet<>(Arrays.asList(NGCommonsKryoRegistrar.class, YamlKryoRegistrar.class)));
    executionMetadata = ExecutionMetadata.newBuilder()
                            .setExecutionUuid("execId")
                            .setRunSequence(3)
                            .setModuleType("cd")
                            .setPipelineIdentifier("pipelineId")
                            .build();
    pipelineYamlV1 = readFile("pipeline-v1.yaml");
  }

  private String readFile(String filename) {
    ClassLoader classLoader = getClass().getClassLoader();
    try {
      return Resources.toString(Objects.requireNonNull(classLoader.getResource(filename)), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read resource file: " + filename);
    }
  }

  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testCreateInitialPlanCreationContext() {
    PlanCreatorMergeService planCreatorMergeService = new PlanCreatorMergeService(
        null, null, null, null, Executors.newSingleThreadExecutor(), 20, pmsFeatureFlagService, null);
    Map<String, PlanCreationContextValue> initialPlanCreationContext =
        planCreatorMergeService.createInitialPlanCreationContext(accountId, orgId, projId, executionMetadata, null);
    assertThat(initialPlanCreationContext).hasSize(1);
    assertThat(initialPlanCreationContext.containsKey("metadata")).isTrue();
    PlanCreationContextValue planCreationContextValue = initialPlanCreationContext.get("metadata");
    assertThat(planCreationContextValue.getAccountIdentifier()).isEqualTo(accountId);
    assertTrue(planCreationContextValue.getIsExecutionInputEnabled());
    assertThat(planCreationContextValue.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(planCreationContextValue.getProjectIdentifier()).isEqualTo(projId);
    assertThat(planCreationContextValue.getMetadata()).isEqualTo(executionMetadata);
    assertThat(planCreationContextValue.getTriggerPayload()).isEqualTo(TriggerPayload.newBuilder().build());

    TriggerPayload triggerPayload = TriggerPayload.newBuilder()
                                        .setParsedPayload(ParsedPayload.newBuilder().build())
                                        .setSourceType(SourceType.GITHUB_REPO)
                                        .build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().triggerPayload(triggerPayload).build();
    initialPlanCreationContext = planCreatorMergeService.createInitialPlanCreationContext(
        accountId, orgId, projId, executionMetadata, planExecutionMetadata);
    assertThat(initialPlanCreationContext).hasSize(1);
    assertThat(initialPlanCreationContext.containsKey("metadata")).isTrue();
    planCreationContextValue = initialPlanCreationContext.get("metadata");
    assertThat(planCreationContextValue.getAccountIdentifier()).isEqualTo(accountId);
    assertThat(planCreationContextValue.getOrgIdentifier()).isEqualTo(orgId);
    assertThat(planCreationContextValue.getProjectIdentifier()).isEqualTo(projId);
    assertThat(planCreationContextValue.getMetadata()).isEqualTo(executionMetadata);
    assertThat(planCreationContextValue.getTriggerPayload()).isEqualTo(triggerPayload);
    assertThat(planCreationContextValue.getIsExecutionInputEnabled()).isTrue();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateInitialPlanCreationContextForV1Yaml() {
    ExecutionMetadata executionMetadataLocal =
        executionMetadata.toBuilder().setHarnessVersion(PipelineVersion.V1).build();
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().processedYaml(pipelineYamlV1).build();
    PlanCreatorMergeService planCreatorMergeService = new PlanCreatorMergeService(
        null, null, null, null, Executors.newSingleThreadExecutor(), 20, pmsFeatureFlagService, kryoSerializer);
    Map<String, PlanCreationContextValue> initialPlanCreationContext =
        planCreatorMergeService.createInitialPlanCreationContext(
            accountId, orgId, projId, executionMetadataLocal, planExecutionMetadata);
    assertThat(initialPlanCreationContext).containsKey("metadata");
    PlanCreationContextValue planCreationContextValue = initialPlanCreationContext.get("metadata");
    assertThat(planCreationContextValue.getGlobalDependency()).isNotNull();
    assertThat(planCreationContextValue.getIsExecutionInputEnabled()).isFalse();
    Dependency globalDependency = planCreationContextValue.getGlobalDependency();
    assertThat(globalDependency.getMetadataMap()).containsKey(YAMLFieldNameConstants.REPOSITORY);
    byte[] bytes = globalDependency.getMetadataMap().get(YAMLFieldNameConstants.REPOSITORY).toByteArray();
    Repository repository = (Repository) kryoSerializer.asObject(bytes);
    assertThat(repository).isNotNull();
    assertThat(repository.getConnector().fetchFinalValue()).isEqualTo("connector");
    assertThat(repository.getName().fetchFinalValue()).isEqualTo("harness-core");
    assertThat(repository.getReference().fetchFinalValue()).isNull();
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateInitialPlanCreationContextWithInputsPayloadForV1Yaml() {
    String inputsPayload = readFile("inputs-payload.json");
    ExecutionMetadata executionMetadataLocal =
        executionMetadata.toBuilder().setHarnessVersion(PipelineVersion.V1).build();
    PlanExecutionMetadata planExecutionMetadata =
        PlanExecutionMetadata.builder().processedYaml(pipelineYamlV1).inputSetYaml(inputsPayload).build();
    PlanCreatorMergeService planCreatorMergeService = new PlanCreatorMergeService(
        null, null, null, null, Executors.newSingleThreadExecutor(), 20, pmsFeatureFlagService, kryoSerializer);
    Map<String, PlanCreationContextValue> initialPlanCreationContext =
        planCreatorMergeService.createInitialPlanCreationContext(
            accountId, orgId, projId, executionMetadataLocal, planExecutionMetadata);
    assertThat(initialPlanCreationContext).containsKey("metadata");
    PlanCreationContextValue planCreationContextValue = initialPlanCreationContext.get("metadata");
    assertThat(planCreationContextValue.getGlobalDependency()).isNotNull();
    assertThat(planCreationContextValue.getIsExecutionInputEnabled()).isFalse();
    Dependency globalDependency = planCreationContextValue.getGlobalDependency();
    assertThat(globalDependency.getMetadataMap()).containsKey(YAMLFieldNameConstants.REPOSITORY);
    byte[] bytes = globalDependency.getMetadataMap().get(YAMLFieldNameConstants.REPOSITORY).toByteArray();
    Repository repository = (Repository) kryoSerializer.asObject(bytes);
    assertThat(repository).isNotNull();
    assertThat(repository.getConnector().fetchFinalValue()).isEqualTo("connector");
    assertThat(repository.getName().fetchFinalValue()).isEqualTo("harness-core");
    assertThat(repository.getReference().fetchFinalValue()).isNotNull();
    Reference reference = repository.getReference().getValue();
    assertThat(reference.getValue()).isEqualTo("main");
    assertThat(reference.getType()).isEqualTo(ReferenceType.BRANCH);
  }

  private class NoOpPmsFeatureFlagService implements PmsFeatureFlagService {
    @Override
    public boolean isEnabled(String accountId, FeatureName featureName) {
      return true;
    }

    @Override
    public boolean isEnabled(String accountId, String featureName) {
      return false;
    }
  }
}
