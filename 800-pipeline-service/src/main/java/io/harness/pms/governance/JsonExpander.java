package io.harness.pms.governance;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ModuleType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.UnexpectedException;
import io.harness.pms.contracts.governance.ExpansionRequestBatch;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.contracts.governance.ExpansionRequestProto;
import io.harness.pms.contracts.governance.ExpansionResponseBatch;
import io.harness.pms.contracts.governance.JsonExpansionServiceGrpc.JsonExpansionServiceBlockingStub;
import io.harness.pms.utils.CompletableFutures;
import io.harness.pms.utils.PmsGrpcClientUtils;
import io.harness.pms.yaml.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.protobuf.ByteString;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@OwnedBy(PIPELINE)
public class JsonExpander {
  @Inject Map<ModuleType, JsonExpansionServiceBlockingStub> jsonExpansionServiceBlockingStubMap;
  Executor executor = Executors.newFixedThreadPool(5);

  public Set<ExpansionResponseBatch> fetchExpansionResponses(
      Set<ExpansionRequest> expansionRequests, ExpansionRequestMetadata expansionRequestMetadata) {
    Map<ModuleType, ExpansionRequestBatch> expansionRequestBatches =
        batchExpansionRequests(expansionRequests, expansionRequestMetadata);
    CompletableFutures<ExpansionResponseBatch> completableFutures = new CompletableFutures<>(executor);

    for (ModuleType module : expansionRequestBatches.keySet()) {
      completableFutures.supplyAsync(() -> {
        JsonExpansionServiceBlockingStub blockingStub = jsonExpansionServiceBlockingStubMap.get(module);
        return PmsGrpcClientUtils.retryAndProcessException(blockingStub::expand, expansionRequestBatches.get(module));
      });
    }

    try {
      return new HashSet<>(completableFutures.allOf().get(5, TimeUnit.MINUTES));
    } catch (Exception ex) {
      throw new UnexpectedException("Error fetching JSON expansion responses from services", ex);
    }
  }

  Map<ModuleType, ExpansionRequestBatch> batchExpansionRequests(
      Set<ExpansionRequest> expansionRequests, ExpansionRequestMetadata expansionRequestMetadata) {
    Set<ModuleType> requiredModules =
        expansionRequests.stream().map(ExpansionRequest::getModule).collect(Collectors.toSet());
    Map<ModuleType, ExpansionRequestBatch> expansionRequestBatches = new HashMap<>();
    for (ModuleType module : requiredModules) {
      Set<ExpansionRequest> currModuleRequests =
          expansionRequests.stream()
              .filter(expansionRequest -> expansionRequest.getModule().equals(module))
              .collect(Collectors.toSet());
      List<ExpansionRequestProto> protoRequests = currModuleRequests.stream()
                                                      .map(request
                                                          -> ExpansionRequestProto.newBuilder()
                                                                 .setFqn(request.getFqn())
                                                                 .setValue(convertToByteString(request.getFieldValue()))
                                                                 .build())
                                                      .collect(Collectors.toList());
      ExpansionRequestBatch batch = ExpansionRequestBatch.newBuilder()
                                        .addAllExpansionRequestProto(protoRequests)
                                        .setRequestMetadata(expansionRequestMetadata)
                                        .build();
      expansionRequestBatches.put(module, batch);
    }
    return expansionRequestBatches;
  }

  ByteString convertToByteString(JsonNode fieldValue) {
    String s = YamlUtils.write(fieldValue);
    return ByteString.copyFromUtf8(s);
  }
}
