/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.dependency;

import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceDependencyDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO.ServiceDependencyDTO.ServiceDependencyDTOKeys;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ServiceDependencyDeserializer extends JsonDeserializer<ServiceDependencyDTO> {
  static Map<DependencyMetadataType, Class<?>> deserializationMapper = new HashMap<>();
  static {
    deserializationMapper.put(DependencyMetadataType.KUBERNETES, KubernetesDependencyMetadata.class);
  }

  @Override
  public ServiceDependencyDTO deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode tree = jsonParser.readValueAsTree();
    DependencyMetadataType type =
        JsonUtils.treeToValue(tree.get(ServiceDependencyDTOKeys.type), DependencyMetadataType.class);
    String monitoredServiceIdentifier = tree.has(ServiceDependencyDTOKeys.monitoredServiceIdentifier)
        ? tree.get(ServiceDependencyDTOKeys.monitoredServiceIdentifier).asText()
        : null;
    JsonNode dependencyMetadataNode = tree.get(ServiceDependencyDTOKeys.dependencyMetadata);

    if (type == null && !dependencyMetadataNode.isNull()) {
      type = JsonUtils.treeToValue(
          dependencyMetadataNode.get(ServiceDependencyDTOKeys.type), DependencyMetadataType.class);
    }

    Class<?> deserializationClass = deserializationMapper.get(type);
    ServiceDependencyMetadata serviceDependencyMetadata = null;
    if (!dependencyMetadataNode.isNull()) {
      serviceDependencyMetadata =
          (ServiceDependencyMetadata) JsonUtils.treeToValue(dependencyMetadataNode, deserializationClass);
    }
    return ServiceDependencyDTO.builder()
        .monitoredServiceIdentifier(monitoredServiceIdentifier)
        .type(type)
        .dependencyMetadata(serviceDependencyMetadata)
        .build();
  }
}
