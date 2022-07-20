package io.harness.event.handlers;

import static io.harness.rule.OwnerRule.SOUMYAJIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.graph.stepDetail.service.PmsGraphStepDetailsService;
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

public class AddStepDetailsInstanceRequestProcessorTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @InjectMocks AddStepDetailsInstanceRequestProcessor addStepDetailsInstanceRequestProcessor;

  @Mock PmsGraphStepDetailsService graphStepDetailsService;

  @Test
  @Owner(developers = SOUMYAJIT)
  @Category(UnitTests.class)
  public void shouldValidatehandleEvent() {
    addStepDetailsInstanceRequestProcessor.handleEvent(
        SdkResponseEventProto.newBuilder()
            .setStepDetailsInstanceRequest(
                AddStepDetailsInstanceRequest.newBuilder().setStepDetails("{\"a\":\"b\"}").build())
            .build());
    verify(graphStepDetailsService, times(1)).addStepDetail(any(), any(), any(), any());
  }
}
