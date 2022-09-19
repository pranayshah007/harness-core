/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.template;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.FilterCreatorException;
import io.harness.exception.bean.FilterCreatorErrorResponse;
import io.harness.pms.contracts.service.EntityReferenceErrorResponse;
import io.harness.pms.contracts.service.EntityReferenceRequest;
import io.harness.pms.contracts.service.EntityReferenceResponse;
import io.harness.pms.contracts.service.EntityReferenceResponseWrapper;
import io.harness.pms.contracts.service.EntityReferenceServiceGrpc.EntityReferenceServiceImplBase;
import io.harness.pms.contracts.service.ErrorMetadata;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;

@OwnedBy(HarnessTeam.CDC)
public class EntityReferenceGrpcService extends EntityReferenceServiceImplBase {
  @Inject EntityReferenceService entityReferenceService;

  @Override
  public void getReferences(
      EntityReferenceRequest request, StreamObserver<EntityReferenceResponseWrapper> responseObserver) {
    EntityReferenceResponseWrapper entityReferenceResponseWrapper;
    try {
      EntityReferenceResponse entityReferenceResponse = entityReferenceService.getReferences(request);
      entityReferenceResponseWrapper =
          EntityReferenceResponseWrapper.newBuilder().setReferenceResponse(entityReferenceResponse).build();
    } catch (FilterCreatorException ex) {
      FilterCreatorErrorResponse errorResponse = (FilterCreatorErrorResponse) ex.getMetadata();
      EntityReferenceErrorResponse.Builder errorResponseBuilder = EntityReferenceErrorResponse.newBuilder();
      if (errorResponse != null && EmptyPredicate.isNotEmpty(errorResponse.getErrorMetadataList())) {
        errorResponse.getErrorMetadataList().forEach(errorMetadata
            -> errorResponseBuilder.addErrorMetadata(
                ErrorMetadata.newBuilder()
                    .setErrorMessage(errorMetadata.getErrorMessage())
                    .setWingsExceptionErrorCode(String.valueOf(errorMetadata.getErrorCode()))
                    .build()));
      }
      entityReferenceResponseWrapper =
          EntityReferenceResponseWrapper.newBuilder().setErrorResponse(errorResponseBuilder.build()).build();
    }
    responseObserver.onNext(entityReferenceResponseWrapper);
    responseObserver.onCompleted();
  }
}
