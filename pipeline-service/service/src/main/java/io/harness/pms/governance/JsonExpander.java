/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;
import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.ModuleType;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.ExceptionUtils;
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
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.protobuf.ByteString;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_TEMPLATE_LIBRARY})
@OwnedBy(PIPELINE)
@Slf4j
public class JsonExpander {
  @Inject Map<ModuleType, JsonExpansionServiceBlockingStub> jsonExpansionServiceBlockingStubMap;
  @Inject @Named("jsonExpansionRequestBatchSize") Integer jsonExpansionRequestBatchSize;
  @Inject @Named("JsonExpansionExecutorService") Executor executor;

  @Inject
  JsonExpander(Map<ModuleType, JsonExpansionServiceBlockingStub> jsonExpansionServiceBlockingStubMap,
      @Named("jsonExpansionRequestBatchSize") Integer jsonExpansionRequestBatchSize,
      @Named("JsonExpansionExecutorService") Executor executor) {
    this.jsonExpansionServiceBlockingStubMap = jsonExpansionServiceBlockingStubMap;
    this.jsonExpansionRequestBatchSize = jsonExpansionRequestBatchSize;
    this.executor = executor;
  }

  public Set<ExpansionResponseBatch> fetchExpansionResponses(
      Set<ExpansionRequest> expansionRequests, ExpansionRequestMetadata expansionRequestMetadata) {
    Multimap<ModuleType, ExpansionRequestBatch> expansionRequestBatches =
        batchExpansionRequests(expansionRequests, expansionRequestMetadata);
    CompletableFutures<ExpansionResponseBatch> completableFutures = new CompletableFutures<>(executor);

    for (ModuleType module : expansionRequestBatches.keySet()) {
      for (ExpansionRequestBatch expansionRequestBatch : expansionRequestBatches.get(module)) {
        completableFutures.supplyAsync(() -> {
          JsonExpansionServiceBlockingStub blockingStub = jsonExpansionServiceBlockingStubMap.get(module);
          return PmsGrpcClientUtils.retryAndProcessException(blockingStub::expand, expansionRequestBatch);
        });
      }
    }

    try {
      return new HashSet<>(completableFutures.allOf().get(5, TimeUnit.MINUTES));
    } catch (Exception ex) {
      log.error("Error fetching JSON expansion responses from services: " + ExceptionUtils.getMessage(ex), ex);
      throw new UnexpectedException("Error fetching JSON expansion responses from services", ex);
    }
  }

  Multimap<ModuleType, ExpansionRequestBatch> batchExpansionRequests(
      Set<ExpansionRequest> expansionRequests, ExpansionRequestMetadata expansionRequestMetadata) {
    Set<ModuleType> requiredModules =
        expansionRequests.stream().map(ExpansionRequest::getModule).collect(Collectors.toSet());
    Multimap<ModuleType, ExpansionRequestBatch> expansionRequestBatches = HashMultimap.create();
    for (ModuleType module : requiredModules) {
      List<ExpansionRequestProto> protoRequests =
          expansionRequests.stream()
              .filter(expansionRequest -> expansionRequest.getModule().equals(module))
              .map(request
                  -> ExpansionRequestProto.newBuilder()
                         .setFqn(request.getFqn())
                         .setKey(request.getKey())
                         .setValue(convertToByteString(request.getFieldValue()))
                         .build())
              .sorted(Comparator.comparing(ExpansionRequestProto::getKey))
              .collect(Collectors.toList());
      List<List<ExpansionRequestProto>> protoRequestsBatched =
          Lists.partition(protoRequests, jsonExpansionRequestBatchSize);
      for (List<ExpansionRequestProto> protoRequest : protoRequestsBatched) {
        expansionRequestBatches.put(module,
            ExpansionRequestBatch.newBuilder()
                .addAllExpansionRequestProto(protoRequest)
                .setRequestMetadata(expansionRequestMetadata)
                .build());
      }
    }
    return expansionRequestBatches;
  }

  ByteString convertToByteString(JsonNode fieldValue) {
    String s = YamlUtils.writeYamlString(fieldValue);
    return ByteString.copyFromUtf8(s);
  }
}
