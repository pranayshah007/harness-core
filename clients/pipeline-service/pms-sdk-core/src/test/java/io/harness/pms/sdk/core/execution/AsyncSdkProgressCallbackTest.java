/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.execution;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SAHIL;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutionMode;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.sdk.core.AmbianceTestUtils;
import io.harness.pms.sdk.core.DummyExecutionStrategy;
import io.harness.pms.sdk.core.PmsSdkCoreTestBase;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class AsyncSdkProgressCallbackTest extends PmsSdkCoreTestBase {
  private Ambiance ambiance;
  public static final String NODE_EXECUTION_ID = generateUuid();
  public static final String NODE_SETUP_ID = generateUuid();
  public static final String NODE_NAME = generateUuid();
  public static final String NODE_IDENTIFIER = "DUMMY";
  public static final StepType DUMMY_STEP_TYPE =
      StepType.newBuilder().setType("DUMMY").setStepCategory(StepCategory.STEP).build();

  @Mock ExecutableProcessorFactory executableProcessorFactory;
  AsyncSdkProgressCallback asyncSdkProgressCallback;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(executableProcessorFactory.obtainProcessor(ExecutionMode.CHILD))
        .thenReturn(new ExecutableProcessor(new DummyExecutionStrategy()));
    asyncSdkProgressCallback =
        AsyncSdkProgressCallback.builder().executableProcessorFactory(executableProcessorFactory).build();
    ambiance = AmbianceTestUtils.buildAmbiance();
    ambiance = ambiance.toBuilder()
                   .addLevels(Level.newBuilder()
                                  .setSetupId(NODE_SETUP_ID)
                                  .setRuntimeId(NODE_EXECUTION_ID)
                                  .setIdentifier(NODE_IDENTIFIER)
                                  .setStepType(DUMMY_STEP_TYPE)
                                  .build())
                   .build();
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testNotify() {
    AsyncSdkProgressCallback progressCallback = AsyncSdkProgressCallback.builder()
                                                    .ambianceBytes(ambiance.toByteArray())
                                                    .stepParameters(new byte[] {})
                                                    .mode(ExecutionMode.CHILD)
                                                    .executableProcessorFactory(executableProcessorFactory)
                                                    .build();
    progressCallback.notify(generateUuid(), CommandUnitsProgress.builder().build());
    Mockito.verify(executableProcessorFactory).obtainProcessor(ExecutionMode.CHILD);
  }
}
