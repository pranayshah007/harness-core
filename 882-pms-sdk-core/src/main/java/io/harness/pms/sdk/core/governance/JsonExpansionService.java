/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.pms.contracts.governance.ExpansionRequestBatch;
import io.harness.pms.contracts.governance.ExpansionRequestProto;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.ExpansionResponseProto;
import io.harness.pms.contracts.governance.JsonExpansionServiceGrpc.JsonExpansionServiceImplBase;
import io.harness.pms.sdk.core.registries.JsonExpansionHandlerRegistry;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PIPELINE)
@Singleton
@Slf4j
public class JsonExpansionService extends JsonExpansionServiceImplBase {
  @Inject JsonExpansionHandlerRegistry expansionHandlerRegistry;
  @Inject ExceptionManager exceptionManager;

  @Override
  public void expand(ExpansionRequestBatch requestsBatch, StreamObserver<ExpansionResponseBatch> responseObserver) {
    ExpansionResponseBatch.Builder expansionResponseBatchBuilder = ExpansionResponseBatch.newBuilder();
    Map<String, ExpansionResponse> jsonNodeMap = new HashMap<>();
    List<ExpansionRequestProto> expansionRequests = requestsBatch.getExpansionRequestProtoList();
    log.info("Initiating expansion request for the following FQNs: "
        + expansionRequests.stream().map(ExpansionRequestProto::getFqn).collect(Collectors.toSet()));
    for (ExpansionRequestProto request : expansionRequests) {
      String fqn = request.getFqn();
      try {
        String key = request.getKey();

        JsonExpansionHandler jsonExpansionHandler = expansionHandlerRegistry.obtain(key);
        String json = request.getValue().toStringUtf8();
        String cacheKey = key + ":" + json;
        if (jsonNodeMap.containsKey(cacheKey)) {
          expansionResponseBatchBuilder.addExpansionResponseProto(
              convertToResponseProto(jsonNodeMap.get(cacheKey), fqn));
          continue;
        }
        JsonNode value = getValueJsonNode(request);
        ExpansionResponse expansionResponse =
            jsonExpansionHandler.expand(value, requestsBatch.getRequestMetadata(), fqn);
        jsonNodeMap.put(cacheKey, expansionResponse);
        expansionResponseBatchBuilder.addExpansionResponseProto(convertToResponseProto(jsonNodeMap.get(cacheKey), fqn));
      } catch (Exception ex) {
        log.error(ExceptionUtils.getMessage(ex), ex);
        WingsException processedException = exceptionManager.processException(ex);
        expansionResponseBatchBuilder.addExpansionResponseProto(
            ExpansionResponseProto.newBuilder()
                .setSuccess(false)
                .setErrorMessage(ExceptionUtils.getMessage(processedException))
                .setFqn(fqn)
                .build());
      }
    }
    responseObserver.onNext(expansionResponseBatchBuilder.build());
    responseObserver.onCompleted();
  }

  private JsonNode getValueJsonNode(ExpansionRequestProto request) {
    String json = request.getValue().toStringUtf8();
    try {
      return YamlUtils.readTree(json).getNode().getCurrJsonNode();
    } catch (IOException e) {
      throw new InvalidRequestException("Could not read the following string" + json);
    }
  }

  ExpansionResponseProto convertToResponseProto(ExpansionResponse expansionResponse, String fqn) {
    if (expansionResponse.isSuccess()) {
      return ExpansionResponseProto.newBuilder()
          .setSuccess(expansionResponse.isSuccess())
          .setFqn(fqn)
          .setKey(expansionResponse.getKey())
          .setValue(expansionResponse.getValue().toJson())
          .setPlacement(expansionResponse.getPlacement())
          .build();
    } else {
      return ExpansionResponseProto.newBuilder()
          .setSuccess(expansionResponse.isSuccess())
          .setErrorMessage(expansionResponse.getErrorMessage())
          .setFqn(fqn)
          .build();
    }
  }
}
