/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.template;

import static io.harness.rule.OwnerRule.VIVEK_DIXIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.PipelineServiceTestBase;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.exception.FilterCreatorException;
import io.harness.exception.bean.ErrorMetadata;
import io.harness.exception.bean.FilterCreatorErrorResponse;
import io.harness.pms.contracts.service.EntityReferenceRequest;
import io.harness.pms.contracts.service.EntityReferenceResponse;
import io.harness.pms.contracts.service.EntityReferenceResponseWrapper;
import io.harness.rule.Owner;

import io.grpc.stub.StreamObserver;
import java.util.Collections;
import lombok.Getter;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(HarnessTeam.PIPELINE)
public class EntityReferenceGrpcServiceTest extends PipelineServiceTestBase {
  @Mock EntityReferenceService entityReferenceService;
  @Mock StreamObserver<EntityReferenceResponseWrapper> responseObserver = new DummyStreamObserver<>();
  @InjectMocks EntityReferenceGrpcService entityReferenceGrpcService;

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetReferences() {
    EntityReferenceRequest request = EntityReferenceRequest.newBuilder().build();
    EntityReferenceResponse entityReferenceResponse = EntityReferenceResponse.newBuilder().build();
    doReturn(entityReferenceResponse).when(entityReferenceService).getReferences(request);

    entityReferenceGrpcService.getReferences(request, responseObserver);

    ArgumentCaptor<EntityReferenceResponseWrapper> argumentCaptor =
        ArgumentCaptor.forClass(EntityReferenceResponseWrapper.class);
    verify(responseObserver, times(1)).onNext(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getReferenceResponse()).isEqualTo(entityReferenceResponse);
  }

  @Test
  @Owner(developers = VIVEK_DIXIT)
  @Category(UnitTests.class)
  public void testGetReferencesWithException() {
    EntityReferenceRequest request = EntityReferenceRequest.newBuilder().setAccountIdentifier("accId").build();
    EntityReferenceResponse entityReferenceResponse = EntityReferenceResponse.newBuilder().build();
    FilterCreatorException exception = new FilterCreatorException("Exception");
    exception.setMetadata(FilterCreatorErrorResponse.builder()
                              .errorMetadataList(Collections.singletonList(
                                  ErrorMetadata.builder().errorMessage("exception_message").build()))
                              .build());

    doThrow(exception).when(entityReferenceService).getReferences(request);

    entityReferenceGrpcService.getReferences(request, responseObserver);

    ArgumentCaptor<EntityReferenceResponseWrapper> argumentCaptor =
        ArgumentCaptor.forClass(EntityReferenceResponseWrapper.class);
    verify(responseObserver, times(1)).onNext(argumentCaptor.capture());
    assertThat(argumentCaptor.getValue().getErrorResponse().getErrorMetadataList().get(0).getErrorMessage())
        .isEqualTo("exception_message");
  }

  private static class DummyStreamObserver<T> implements StreamObserver<T> {
    @Getter EntityReferenceResponseWrapper entityReferenceResponseWrapper;
    @Override
    public void onNext(Object o) {
      entityReferenceResponseWrapper = (EntityReferenceResponseWrapper) o;
    }
    @Override
    public void onError(Throwable throwable) {}
    @Override
    public void onCompleted() {}
  }
}
