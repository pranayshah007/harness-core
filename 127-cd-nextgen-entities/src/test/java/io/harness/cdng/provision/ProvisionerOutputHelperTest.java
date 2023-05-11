/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.executions.steps.ExecutionNodeType.INFRASTRUCTURE_PROVISIONER_STEP;
import static io.harness.rule.OwnerRule.IVAN;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.serializer.recaster.RecastOrchestrationUtils;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDC)
public class ProvisionerOutputHelperTest extends CategoryTest {
  private static final String PROVISIONER_IDENTIFIER = "provisionerIdentifier";
  private static final String OUTPUT = "output";
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;

  @InjectMocks private ProvisionerOutputHelper provisionerOutputHelper;

  @Before
  public void setup() {
    initMocks(this);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testSaveProvisionerOutputByIdentifier() {
    doReturn("success").when(executionSweepingOutputService).consume(any(), any(), any(), any());
    ArgumentCaptor<String> provisionerOutputRefObjectName = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<TestExecutionSweepingOutput> executionSweepingOutput =
        ArgumentCaptor.forClass(TestExecutionSweepingOutput.class);

    provisionerOutputHelper.saveProvisionerOutputByStepIdentifier(
        getAmbiance(), TestExecutionSweepingOutput.builder().output(OUTPUT).build());

    verify(executionSweepingOutputService, times(1))
        .consume(any(), provisionerOutputRefObjectName.capture(), executionSweepingOutput.capture(), any());

    assertThat(provisionerOutputRefObjectName.getValue()).isEqualTo(format("provisioner_%s", PROVISIONER_IDENTIFIER));
    assertThat(executionSweepingOutput.getValue().getOutput()).isEqualTo(OUTPUT);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetProvisionerOutputAsMap() {
    String provisionerOutputWithoutHosts = "{\n"
        + "   \"region\":\"us-east-1\",\n"
        + "   \"subscriptionId\": \"12d2db62-5aa9-471d-84bb-faa489b3e319\",\n"
        + "   \"resourceGroup\": \"testProvisionersRG\",\n"
        + "   \"tags\": {\n"
        + "     \"team\": \"CDP\",\n"
        + "     \"data_center\": \"west\"\n"
        + "  }\n"
        + "}";
    Map<String, Object> provisionerOutputMap = RecastOrchestrationUtils.fromJson(provisionerOutputWithoutHosts);
    doReturn(Optional.of(provisionerOutputMap)).when(executionSweepingOutputService).resolveFromJsonAsMap(any(), any());

    Map<String, Object> provisionerOutputAsMap = provisionerOutputHelper.getProvisionerOutputAsMap(
        getAmbiance(), format("provisioner_%s", PROVISIONER_IDENTIFIER));

    assertThat(provisionerOutputAsMap.size()).isEqualTo(4);
    assertThat(provisionerOutputAsMap.get("region")).isEqualTo("us-east-1");
    assertThat(provisionerOutputAsMap.get("subscriptionId")).isEqualTo("12d2db62-5aa9-471d-84bb-faa489b3e319");
  }

  private Ambiance getAmbiance() {
    return Ambiance.newBuilder()
        .putAllSetupAbstractions(ImmutableMap.<String, String>builder()
                                     .put(SetupAbstractionKeys.accountId, "ACCOUNT_ID")
                                     .put(SetupAbstractionKeys.orgIdentifier, "ORG_ID")
                                     .put(SetupAbstractionKeys.projectIdentifier, "PROJECT_ID")
                                     .build())
        .setPlanId(generateUuid())
        .addLevels(Level.newBuilder()
                       .setSetupId(generateUuid())
                       .setRuntimeId(generateUuid())
                       .setStepType(StepType.newBuilder()
                                        .setType(INFRASTRUCTURE_PROVISIONER_STEP.getName())
                                        .setStepCategory(StepCategory.STAGE)
                                        .build())
                       .setIdentifier(generateUuid())
                       .build())
        .addLevels(
            Level.newBuilder()
                .setSetupId(generateUuid())
                .setRuntimeId(generateUuid())
                .setStepType(StepType.newBuilder().setType("PROVISIONER").setStepCategory(StepCategory.STAGE).build())
                .setIdentifier(PROVISIONER_IDENTIFIER)
                .build())
        .build();
  }

  @Getter
  @Builder
  private static class TestExecutionSweepingOutput implements ExecutionSweepingOutput {
    private String output;
  }
}
