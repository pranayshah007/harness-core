/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution.invokers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.PRASHANT;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse;
import io.harness.pms.contracts.execution.ChildrenExecutableResponse.Child;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.execution.events.SpawnChildrenRequest;
import io.harness.pms.contracts.steps.io.StepResponseProto;
import io.harness.pms.plan.execution.SetupAbstractionKeys;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.pms.sdk.core.execution.InvokerPackage;
import io.harness.pms.sdk.core.execution.ResumePackage;
import io.harness.pms.sdk.core.execution.SdkNodeExecutionService;
import io.harness.pms.sdk.core.registries.StepRegistry;
import io.harness.pms.sdk.core.steps.io.StepResponseNotifyData;
import io.harness.pms.sdk.core.supporter.children.TestChildrenStep;
import io.harness.pms.sdk.core.supporter.children.TestChildrenStepParameters;
import io.harness.rule.Owner;

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

public class ChildrenStrategyTest extends PmsSdkCoreTestBase {
  @Mock private SdkNodeExecutionService sdkNodeExecutionService;
  @Inject @InjectMocks private ChildrenStrategy childrenStrategy;

  @Inject private StepRegistry stepRegistry;

  @Before
  public void setup() {
    stepRegistry.register(TestChildrenStep.STEP_TYPE, new TestChildrenStep());
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestStart() {
    String childNodeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions())
                            .setPlanId(generateUuid())
                            .setPlanExecutionId(generateUuid())
                            .addLevels(Level.newBuilder()
                                           .setSetupId(generateUuid())
                                           .setRuntimeId(generateUuid())
                                           .setStepType(TestChildrenStep.STEP_TYPE)
                                           .setIdentifier(generateUuid())
                                           .build())
                            .build();
    InvokerPackage invokerPackage =
        InvokerPackage.builder()
            .ambiance(ambiance)
            .executionMode(ExecutionMode.CHILDREN)
            .passThroughData(null)
            .stepParameters(TestChildrenStepParameters.builder().parallelNodeId(childNodeId).build())
            .build();

    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<SpawnChildrenRequest> spawnChildrenRequestArgumentCaptor =
        ArgumentCaptor.forClass(SpawnChildrenRequest.class);

    childrenStrategy.start(invokerPackage);
    Mockito.verify(sdkNodeExecutionService, Mockito.times(1))
        .spawnChildren(ambianceCaptor.capture(), spawnChildrenRequestArgumentCaptor.capture());
    assertThat(ambianceCaptor.getValue()).isEqualTo(ambiance);
    SpawnChildrenRequest spawnChildrenRequest = spawnChildrenRequestArgumentCaptor.getValue();

    ChildrenExecutableResponse children = spawnChildrenRequest.getChildren();
    assertThat(children.getChildrenCount()).isEqualTo(1);
    Child child = children.getChildrenList().get(0);
    assertThat(child.getChildNodeId()).isEqualTo(childNodeId);
  }

  @Test
  @Owner(developers = PRASHANT)
  @Category(UnitTests.class)
  public void shouldTestResume() {
    String childNodeId = generateUuid();
    Ambiance ambiance = Ambiance.newBuilder()
                            .putAllSetupAbstractions(setupAbstractions())
                            .setPlanId(generateUuid())
                            .addLevels(Level.newBuilder()
                                           .setSetupId(generateUuid())
                                           .setRuntimeId(generateUuid())
                                           .setStepType(TestChildrenStep.STEP_TYPE)
                                           .setIdentifier(generateUuid())
                                           .build())
                            .build();
    ResumePackage resumePackage =
        ResumePackage.builder()
            .ambiance(ambiance)
            .stepParameters(TestChildrenStepParameters.builder().parallelNodeId(childNodeId).build())
            .responseDataMap(ImmutableMap.of(generateUuid(),
                StepResponseNotifyData.builder().nodeUuid(childNodeId).status(Status.SUCCEEDED).build()))
            .build();

    childrenStrategy.resume(resumePackage);
    ArgumentCaptor<Ambiance> ambianceCaptor = ArgumentCaptor.forClass(Ambiance.class);
    ArgumentCaptor<StepResponseProto> stepResponseCaptor = ArgumentCaptor.forClass(StepResponseProto.class);

    Mockito.verify(sdkNodeExecutionService, Mockito.times(1))
        .handleStepResponse(ambianceCaptor.capture(), stepResponseCaptor.capture());
    assertThat(ambianceCaptor.getValue()).isEqualTo(ambiance);

    StepResponseProto stepResponseProto = stepResponseCaptor.getValue();
    assertThat(stepResponseProto.getStatus()).isEqualTo(Status.SUCCEEDED);
  }
  private Map<String, String> setupAbstractions() {
    return ImmutableMap.<String, String>builder()
        .put(SetupAbstractionKeys.accountId, generateUuid())
        .put(SetupAbstractionKeys.orgIdentifier, generateUuid())
        .put(SetupAbstractionKeys.projectIdentifier, generateUuid())
        .build();
  }
}
