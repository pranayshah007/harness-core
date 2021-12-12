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
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.List;
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

    List<ExpansionRequestProto> expansionRequests = requestsBatch.getExpansionRequestProtoList();
    for (ExpansionRequestProto request : expansionRequests) {
      String fqn = request.getFqn();
      try {
        String key = YamlNode.getLastKeyInPath(fqn);
        JsonExpansionHandler jsonExpansionHandler = expansionHandlerRegistry.obtain(key);
        JsonNode value = getValueJsonNode(request);
        ExpansionResponse expansionResponse = jsonExpansionHandler.expand(value, requestsBatch.getRequestMetadata());
        ExpansionResponseProto expansionResponseProto = convertToResponseProto(expansionResponse, fqn);
        expansionResponseBatchBuilder.addExpansionResponseProto(expansionResponseProto);

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
