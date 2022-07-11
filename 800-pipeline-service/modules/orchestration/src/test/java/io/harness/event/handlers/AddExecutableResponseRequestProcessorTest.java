/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.SAHIL;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.ExecutableResponse;
import io.harness.pms.contracts.execution.TaskExecutableResponse;
import io.harness.pms.contracts.execution.events.AddExecutableResponseRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.pms.contracts.execution.events.SdkResponseEventType;
import io.harness.pms.contracts.execution.tasks.TaskCategory;
import io.harness.rule.Owner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.PIPELINE)
public class AddExecutableResponseRequestProcessorTest extends CategoryTest {
  @Mock private NodeExecutionService nodeExecutionService;
  @InjectMocks AddExecutableResponseRequestProcessor addExecutableResponseEventHandler;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void verifyMocks() {
    verifyNoMoreInteractions(nodeExecutionService);
  }

  @Test
  @Owner(developers = SAHIL)
  @Category(UnitTests.class)
  public void testHandleEventWithStatus() {
    String nodeExecutionId = generateUuid();
    AddExecutableResponseRequest request =
        AddExecutableResponseRequest.newBuilder()
            .setExecutableResponse(ExecutableResponse.newBuilder()
                                       .setTask(TaskExecutableResponse.newBuilder()
                                                    .setTaskId(generateUuid())
                                                    .setTaskCategory(TaskCategory.UNKNOWN_CATEGORY)
                                                    .build())
                                       .build())
            .build();
    addExecutableResponseEventHandler.handleEvent(
        SdkResponseEventProto.newBuilder()
            .setAmbiance(
                Ambiance.newBuilder().addLevels(Level.newBuilder().setRuntimeId(nodeExecutionId).build()).build())
            .setAddExecutableResponseRequest(request)
            .setSdkResponseEventType(SdkResponseEventType.ADD_EXECUTABLE_RESPONSE)
            .build());
    verify(nodeExecutionService).updateV2(eq(nodeExecutionId), any());
  }
}
