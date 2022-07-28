/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.event.handlers;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.engine.executions.node.NodeExecutionService;
import io.harness.pms.contracts.execution.events.AddStepDetailsInstanceRequest;
import io.harness.pms.contracts.execution.events.SdkResponseEventProto;
import io.harness.rule.Owner;

import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class HandleProgressRequestProcessorTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks HandleProgressRequestProcessor handleProgressRequestProcessor;
  @Mock NodeExecutionService nodeExecutionService;

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidateHandleEvent() {
    handleProgressRequestProcessor.handleEvent(
        SdkResponseEventProto.newBuilder()
            .setStepDetailsInstanceRequest(
                AddStepDetailsInstanceRequest.newBuilder().setStepDetails("{\"a\":\"b\"}").build())
            .build());
    verify(nodeExecutionService, times(1)).updateV2(any(), any());
  }
}
