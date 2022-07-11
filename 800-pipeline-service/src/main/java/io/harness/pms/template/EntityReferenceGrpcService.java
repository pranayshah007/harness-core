/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.template;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.contracts.service.EntityReferenceRequest;
import io.harness.pms.contracts.service.EntityReferenceResponse;
import io.harness.pms.contracts.service.EntityReferenceServiceGrpc.EntityReferenceServiceImplBase;

import com.google.inject.Inject;
import io.grpc.stub.StreamObserver;

@OwnedBy(HarnessTeam.CDC)
public class EntityReferenceGrpcService extends EntityReferenceServiceImplBase {
  @Inject EntityReferenceService entityReferenceService;

  @Override
  public void getReferences(EntityReferenceRequest request, StreamObserver<EntityReferenceResponse> responseObserver) {
    EntityReferenceResponse entityReferenceResponse =
        EntityReferenceResponse.newBuilder()
            .addAllReferredEntities(entityReferenceService.getReferences(request).getReferredEntitiesList())
            .build();
    responseObserver.onNext(entityReferenceResponse);
    responseObserver.onCompleted();
  }
}
