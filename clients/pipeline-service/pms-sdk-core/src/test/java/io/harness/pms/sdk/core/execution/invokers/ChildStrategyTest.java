/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildExecutableResponse;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.events.SpawnChildRequest;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.InvokerPackage;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.supporter.async.TestChildStep;
import io.harness.pms.sdk.core.supporter.async.TestStepParameters;
import io.harness.pms.sdk.core.waiter.AsyncWaitEngine;
import io.harness.rule.Owner;
import io.harness.waiter.StringNotifyResponseData;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.PIPELINE)
public class ChildStrategyTest extends PmsSdkCoreTestBase {
  @Mock private SdkNodeExecutionService sdkNodeExecutionService;
  @Mock private AsyncWaitEngine asyncWaitEngine;
  @Inject @InjectMocks private ChildStrategy childStrategy;

  @Inject private StepRegistry stepRegistry;

  @Before
  public void setup() {
    stepRegistry.register(TestChildStep.CHILD_STEP_TYPE, new TestChildStep());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void shouldTestStart() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions())
                            .setPlanId(generateUuid())
                            .addLevels(Level.newBuilder()
                                           .setSetupId(generateUuid())
                                           .setRuntimeId(generateUuid())
                                           .setStepType(TestChildStep.CHILD_STEP_TYPE)
                                           .setIdentifier(generateUuid())
                                           .build())
                            .build();
    InvokerPackage invokerPackage = InvokerPackage.builder()
                                        .ambiance(ambiance)
                                        .executionMode(ExecutionMode.CHILD)
                                        .passThroughData(null)
                                        .stepParameters(TestStepParameters.builder().param("TEST_PARAM").build())
                                        .build();
    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<SpawnChildRequest> spawnChildRequest = ArgumentCaptor.forClass(SpawnChildRequest.class);

    childStrategy.start(invokerPackage);
    verify(sdkNodeExecutionService).spawnChild(ambianceCaptor.capture(), spawnChildRequest.capture());

    assertThat(ambianceCaptor.getValue()).isEqualTo(ambiance);
    SpawnChildRequest request = spawnChildRequest.getValue();
    assertThat(request.getChild()).isEqualTo(ChildExecutableResponse.newBuilder().build());
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void shouldTestResume() {
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions())
                            .setPlanId(generateUuid())
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder()
                                           .setSetupId(generateUuid())
                                           .setRuntimeId(generateUuid())
                                           .setStepType(TestChildStep.CHILD_STEP_TYPE)
                                           .setIdentifier(generateUuid())
                                           .build())
                            .build();
    ResumePackage resumePackage = ResumePackage.builder()
                                      .ambiance(ambiance)
                                      .stepParameters(TestStepParameters.builder().param("TEST_PARAM").build())
                                      .responseDataMap(ImmutableMap.of(generateUuid(),
                                          StringNotifyResponseData.builder().data("someString").build()))
                                      .build();

    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<StepResponseProto> stepResponseCaptor = ArgumentCaptor.forClass(StepResponseProto.class);
    childStrategy.resume(resumePackage);
    Mockito.verify(sdkNodeExecutionService, Mockito.times(1))
        .handleStepResponse(ambianceCaptor.capture(), stepResponseCaptor.capture());

    assertThat(ambianceCaptor.getValue()).isEqualTo(ambiance);
  }

  private Map<String, String> setupAbstractions() {
    return ImmutableMap.<String, String>builder()
        .put(SetupAbstractionKeys.accountId, generateUuid())
        .put(SetupAbstractionKeys.orgIdentifier, generateUuid())
        .put(SetupAbstractionKeys.projectIdentifier, generateUuid())
        .build();
  }
}
