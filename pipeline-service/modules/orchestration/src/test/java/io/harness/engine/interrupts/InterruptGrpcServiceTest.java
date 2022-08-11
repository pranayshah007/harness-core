package io.harness.engine.interrupts;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.OrchestrationTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.contracts.service.InterruptRequest;
import io.harness.pms.contracts.service.InterruptResponse;
import io.harness.rule.Owner;
import io.harness.waiter.WaitNotifyEngine;

import io.grpc.stub.StreamObserver;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class InterruptGrpcServiceTest extends OrchestrationTestBase {
  @Mock WaitNotifyEngine waitNotifyEngine;
  @InjectMocks InterruptGrpcService interruptGrpcService;

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleAbort() {
    InterruptRequest interruptRequest = InterruptRequest.newBuilder().build();
    StreamObserver<InterruptResponse> responseObserver = spy(StreamObserver.class);

    doReturn("Invoked").when(waitNotifyEngine).doneWith(any(), any());
    doNothing().when(responseObserver).onNext(any());
    doNothing().when(responseObserver).onCompleted();
    interruptGrpcService.handleAbort(interruptRequest, responseObserver);

    verify(waitNotifyEngine, times(1)).doneWith(any(), any());
    verify(responseObserver, times(1)).onNext(any());
    verify(responseObserver, times(1)).onCompleted();
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testHandleFailure() {
    InterruptRequest interruptRequest = InterruptRequest.newBuilder().build();
    StreamObserver<InterruptResponse> responseObserver = spy(StreamObserver.class);

    doReturn("Invoked").when(waitNotifyEngine).doneWith(any(), any());
    doNothing().when(responseObserver).onNext(any());
    doNothing().when(responseObserver).onCompleted();
    interruptGrpcService.handleAbort(interruptRequest, responseObserver);

    verify(waitNotifyEngine, times(1)).doneWith(any(), any());
    verify(responseObserver, times(1)).onNext(any());
    verify(responseObserver, times(1)).onCompleted();
  }
}
