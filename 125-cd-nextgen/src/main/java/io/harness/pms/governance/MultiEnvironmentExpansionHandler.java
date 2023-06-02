/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.governance;

import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.pms.governance.EnvironmentExpansionUtils.getEnvRefFromEnvYamlV2Node;
import static io.harness.pms.governance.ExpansionConstants.ENVIRONMENTS_PARALLEL_KEY;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.environment.yaml.EnvironmentsMetadata;
import io.harness.cdng.environment.yaml.EnvironmentsYaml;
import io.harness.connector.services.ConnectorService;
import io.harness.exception.ExceptionUtils;
import io.harness.pms.contracts.governance.ExpansionPlacementStrategy;
import io.harness.pms.contracts.governance.ExpansionRequestMetadata;
import io.harness.pms.sdk.core.governance.ExpansionResponse;
import io.harness.pms.sdk.core.governance.JsonExpansionHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDC)
@Singleton
@Slf4j
public class MultiEnvironmentExpansionHandler implements JsonExpansionHandler {
  @Inject private EnvironmentExpansionUtils utils;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  private final ObjectMapper objectMapper = new ObjectMapper();
  @Override
  public ExpansionResponse expand(JsonNode fieldValue, ExpansionRequestMetadata metadata, String fqn) {
    try {
      JsonNode environments = fieldValue.get(EnvironmentsYaml.keys.values);
      if (!environments.isArray()) {
        return ExpansionResponse.builder().success(false).errorMessage("field is not an array").build();
      }

      List<SingleEnvironmentExpandedValue> values = new ArrayList<>();
      environments.forEach(environmentNode -> {
        final Optional<String> environmentRefOpt = getEnvRefFromEnvYamlV2Node(environmentNode);
        if (environmentRefOpt.isPresent()) {
          values.add(utils.toSingleEnvironmentExpandedValue(metadata, environmentNode, environmentRefOpt.get()));
        } else {
          values.add(SingleEnvironmentExpandedValue.builder().build());
        }
      });

      JsonNode metaDataNode = fieldValue.get(EnvironmentsYaml.METADATA);

      final Map<String, Object> metaData = new HashMap<>();
      if (metaDataNode != null && metaDataNode.isObject()) {
        EnvironmentsMetadata environmentsMetadata = objectMapper.treeToValue(metaDataNode, EnvironmentsMetadata.class);
        if (environmentsMetadata != null) {
          metaData.put(ENVIRONMENTS_PARALLEL_KEY, environmentsMetadata.getParallel());
        }
      }

      MultiEnvExpandedValue value = MultiEnvExpandedValue.builder().environments(values).metadata(metaData).build();
      return ExpansionResponse.builder()
          .success(true)
          .placement(ExpansionPlacementStrategy.REPLACE)
          .key(value.getKey())
          .value(value)
          .build();
    } catch (Exception ex) {
      log.error("Exception in multi environment expansion", ex);
      return ExpansionResponse.builder().success(false).errorMessage(ExceptionUtils.getMessage(ex)).build();
    }
  }
}
