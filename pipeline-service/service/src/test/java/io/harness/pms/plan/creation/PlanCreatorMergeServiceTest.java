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
import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.engine.pms.commons.events.PmsEventSender;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.PlanExecutionMetadata;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.PlanCreationBlobResponse;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.triggers.ParsedPayload;
import io.harness.pms.contracts.triggers.SourceType;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.plan.creation.validator.PlanCreationValidator;
import io.harness.pms.sdk.PmsSdkHelper;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;
import io.harness.serializer.KryoSerializer;
import io.harness.serializer.kryo.NGCommonsKryoRegistrar;
import io.harness.serializer.kryo.YamlKryoRegistrar;
import io.harness.utils.PmsFeatureFlagService;
import io.harness.waiter.WaitNotifyEngine;
import io.harness.yaml.clone.Ref;
import io.harness.yaml.clone.RefType;
import io.harness.yaml.options.Options;
import io.harness.yaml.registry.Registry;
import io.harness.yaml.registry.RegistryCredential;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(PIPELINE)
public class PlanCreatorMergeServiceTest extends CategoryTest {
  private KryoSerializer kryoSerializer;
  private final PmsFeatureFlagService pmsFeatureFlagService = new NoOpPmsFeatureFlagService();
  @Mock private PmsFeatureFlagService pmsFeatureFlagServiceMock;
  @Mock private PmsEventSender pmsEventSender;
  @Mock private WaitNotifyEngine waitNotifyEngine;
  @Mock private PmsSdkHelper pmsSdkHelper;
  @Mock private PlanCreationValidator planCreationValidator;
  @Spy @InjectMocks PlanCreatorMergeService planCreatorMergeServiceMock;
  private final String accountId = "acc";
  private final String orgId = "org";
  private final String projId = "proj";
  private ExecutionMetadata executionMetadata;
  private String pipelineYamlV1;

  private String processedYaml =
      "{\"pipeline\":{\"identifier\":\"p1_inline\",\"name\":\"p1_inline\",\"projectIdentifier\":\"proj1\",\"orgIdentifier\":"
      + "\"default\",\"tags\":{\"__uuid\":\"9Sd2vBJyTreQuN50aBigOw\"},\"stages\":[{\"stage\":{\"identifier\":\"ap1\",\"type\":"
      + "\"Approval\",\"name\":\"ap1\",\"description\":\"\",\"spec\":{\"execution\":{\"steps\":[{\"step\":{\"identifier\":\"ap1\",\"type\":"
      + "\"HarnessApproval\",\"name\":\"ap1\",\"timeout\":\"1d\",\"spec\":{\"approvalMessage\":"
      + "\"Please review the following information\\nand approve the pipeline progression\",\"includePipelineExecutionHistory\":"
      + "true,\"approvers\":{\"minimumCount\":1,\"disallowPipelineExecutor\":false,\"userGroups\":"
      + "[\"account._account_all_users\"],\"__uuid\":\"huEePX9KTxO2bxiJScw3iQ\"},\"isAutoRejectEnabled\":false,\"approverInputs\":[],\"__uuid\":"
      + "\"Q8HzGsYWSZGjvjmf5Jlszw\"},\"__uuid\":\"tCucaMc3THa3qh6MXcMVkw\"},\"__uuid\":\"IN87E8WJT4-YCnhHkIpV2A\"}],\"__uuid\":"
      + "\"sEDjjPPhQNe8wJgvpz6GsA\"},\"__uuid\":\"SlBHSpltS2ePxjN4LUnEGw\"},\"tags\":{\"__uuid\":\"j6V5id3tQ6S9fkED430CXw\"},\"__uuid\":"
      + "\"JwAB6BpnTF-PGO6FgrLXlw\"},\"__uuid\":\"GMi_oA0MQYm6oGAJXR9Dig\"}],\"__uuid\":\"R1f_rPxfSUCrc5_MvfFHSw\"},\"__uuid\":\"fSqM9u1jQsK8cJngoac2FA\"}";

  @Before
  public void before() {
    MockitoAnnotations.initMocks(this);
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
    assertThat(globalDependency.getMetadataMap()).containsKey(YAMLFieldNameConstants.OPTIONS);
    byte[] bytes = globalDependency.getMetadataMap().get(YAMLFieldNameConstants.OPTIONS).toByteArray();
    Options options = (Options) kryoSerializer.asObject(bytes);
    assertThat(options).isNotNull();
    assertThat(options.getRepository()).isNotNull();
    assertThat(options.getRepository().getConnector().fetchFinalValue()).isEqualTo("connector");
    assertThat(options.getRepository().getName().fetchFinalValue()).isEqualTo("harness-core");

    assertThat(options.getRegistry()).isNotNull();
    Registry registry = options.getRegistry();
    assertThat(registry).isNotNull();
    List<RegistryCredential> credentials = registry.getCredentials();
    assertThat(credentials).hasSize(4);
    assertThat(credentials.get(0).getName().isExpression()).isFalse();
    assertThat(credentials.get(0).getName().fetchFinalValue()).isEqualTo("account.docker");
    assertThat(credentials.get(0).getMatch().fetchFinalValue()).isNull();

    assertThat(credentials.get(1).getName().isExpression()).isTrue();
    assertThat(credentials.get(1).getName().fetchFinalValue()).isEqualTo("<+expression>");
    assertThat(credentials.get(1).getMatch().fetchFinalValue()).isNull();

    assertThat(credentials.get(2).getName().fetchFinalValue()).isEqualTo("account.dockerhub");
    assertThat(credentials.get(2).getMatch().fetchFinalValue()).isEqualTo("docker.io");

    assertThat(credentials.get(3).getName().isExpression()).isTrue();
    assertThat(credentials.get(3).getName().fetchFinalValue()).isEqualTo("<+expression>");
    assertThat(credentials.get(3).getMatch().isExpression()).isTrue();
    assertThat(credentials.get(3).getMatch().fetchFinalValue()).isEqualTo("<+expression>");
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testCreateInitialPlanCreationContextForV1YamlWithStaticReference() {
    ExecutionMetadata executionMetadataLocal =
        executionMetadata.toBuilder().setHarnessVersion(PipelineVersion.V1).build();
    String pipelineYaml = readFile("pipeline-v1-with-static-reference.yaml");
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder().processedYaml(pipelineYaml).build();
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
    assertThat(globalDependency.getMetadataMap()).containsKey(YAMLFieldNameConstants.OPTIONS);
    byte[] bytes = globalDependency.getMetadataMap().get(YAMLFieldNameConstants.OPTIONS).toByteArray();
    Options options = (Options) kryoSerializer.asObject(bytes);
    assertThat(options).isNotNull();
    assertThat(options.getRepository()).isNotNull();
    assertThat(options.getRepository().getConnector().fetchFinalValue()).isEqualTo("connector");
    assertThat(options.getRepository().getName().fetchFinalValue()).isEqualTo("harness-core");
    assertThat(options.getClone()).isNotNull();
    assertThat(options.getClone().getRef().fetchFinalValue()).isNotNull();
    Ref ref = options.getClone().getRef().getValue();
    assertThat(ref.getName()).isEqualTo("v1");
    assertThat(ref.getType()).isEqualTo(RefType.TAG);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testCreatePlanV2() throws IOException {
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .processedYaml(processedYaml)
                                                      .triggerPayload(TriggerPayload.newBuilder().build())
                                                      .build();
    doReturn("").when(pmsEventSender).sendEvent(any(), any(), any(), any());
    doReturn(Collections.emptyMap())
        .when(planCreatorMergeServiceMock)
        .createInitialPlanCreationContext(any(), any(), any(), any(), any());
    doReturn("").when(waitNotifyEngine).waitForAllOnInList(any(), any(), any(), any());
    planCreatorMergeServiceMock.createPlanV2(
        accountId, orgId, projId, "planUuid", executionMetadata, planExecutionMetadata);
    verify(pmsEventSender, times(1)).sendEvent(any(), any(), any(), any());
    verify(waitNotifyEngine, times(1)).waitForAllOnInList(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testCreatePlanVersioned() throws IOException {
    PlanExecutionMetadata planExecutionMetadata = PlanExecutionMetadata.builder()
                                                      .processedYaml(processedYaml)
                                                      .triggerPayload(TriggerPayload.newBuilder().build())
                                                      .build();
    Set<String> supportedTypesForPipeline = new HashSet<>();
    supportedTypesForPipeline.add("[__any__]");
    Map<String, Set<String>> supportedTypes = new HashMap<>();
    supportedTypes.put("pipeline", supportedTypesForPipeline);
    PlanCreatorServiceInfo planCreatorServiceInfo = new PlanCreatorServiceInfo(supportedTypes, null);

    Map<String, PlanCreatorServiceInfo> services = new HashMap<>();
    services.put("pms", planCreatorServiceInfo);

    doReturn(services).when(pmsSdkHelper).getServices();
    doReturn(true).when(pmsFeatureFlagServiceMock).isEnabled(any(), (FeatureName) any());
    PlanCreationBlobResponse finalResponse = PlanCreationBlobResponse.newBuilder().build();
    doReturn(finalResponse)
        .when(planCreatorMergeServiceMock)
        .createPlanForDependenciesRecursive(any(), any(), any(), any(), any(), any(), any());
    doNothing().when(planCreationValidator).validate(any(), any());
    planCreatorMergeServiceMock.createPlanVersioned(
        accountId, orgId, projId, PipelineVersion.V1, executionMetadata, planExecutionMetadata);
    verify(planCreationValidator, times(1)).validate(any(), any());
    verify(planCreatorMergeServiceMock, times(1))
        .createPlanForDependenciesRecursive(any(), any(), any(), any(), any(), any(), any());
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
