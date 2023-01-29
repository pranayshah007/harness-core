/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.change;

import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.BadRequestException;

public class ChangeEventDTODeserializer extends JsonDeserializer<ChangeEventDTO> {
  static Map<ChangeSourceType, Class<?>> deserializationMapper = new HashMap<>();
  static {
    deserializationMapper.put(ChangeSourceType.HARNESS_CD_CURRENT_GEN, HarnessCDCurrentGenEventMetadata.class);
    deserializationMapper.put(ChangeSourceType.HARNESS_CD, HarnessCDEventMetadata.class);
    deserializationMapper.put(ChangeSourceType.PAGER_DUTY, PagerDutyEventMetaData.class);
    deserializationMapper.put(ChangeSourceType.KUBERNETES, KubernetesChangeEventMetadata.class);
    deserializationMapper.put(ChangeSourceType.HARNESS_FF, InternalChangeEventMetaData.class);
    deserializationMapper.put(ChangeSourceType.CUSTOM_DEPLOY, CustomChangeEventMetadata.class);
    deserializationMapper.put(ChangeSourceType.CUSTOM_INFRA, CustomChangeEventMetadata.class);
    deserializationMapper.put(ChangeSourceType.CUSTOM_INCIDENT, CustomChangeEventMetadata.class);
    deserializationMapper.put(ChangeSourceType.CUSTOM_FF, CustomChangeEventMetadata.class);
  }

  @Override
  public ChangeEventDTO deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode tree = jsonParser.readValueAsTree();
    ChangeSourceType type = JsonUtils.treeToValue(tree.get(ChangeEventDTO.Fields.type), ChangeSourceType.class);

    String id = tree.has(ChangeEventDTO.Fields.id) ? tree.get(ChangeEventDTO.Fields.id).asText() : null;
    String accountId =
        tree.has(ChangeEventDTO.Fields.accountId) ? tree.get(ChangeEventDTO.Fields.accountId).asText() : null;
    String orgIdentifier = tree.has(ChangeEventDTO.Fields.projectIdentifier)
        ? tree.get(ChangeEventDTO.Fields.projectIdentifier).asText()
        : null;
    String projectIdentifier =
        tree.has(ChangeEventDTO.Fields.orgIdentifier) ? tree.get(ChangeEventDTO.Fields.orgIdentifier).asText() : null;

    String serviceIdentifier = tree.has(ChangeEventDTO.Fields.serviceIdentifier)
        ? tree.get(ChangeEventDTO.Fields.serviceIdentifier).asText()
        : null;
    String serviceName =
        tree.has(ChangeEventDTO.Fields.serviceName) ? tree.get(ChangeEventDTO.Fields.serviceName).asText() : null;

    String envIdentifier =
        tree.has(ChangeEventDTO.Fields.envIdentifier) ? tree.get(ChangeEventDTO.Fields.envIdentifier).asText() : null;
    String envName = tree.has(ChangeEventDTO.Fields.environmentName)
        ? tree.get(ChangeEventDTO.Fields.environmentName).asText()
        : null;

    String name = tree.has(ChangeEventDTO.Fields.name) ? tree.get(ChangeEventDTO.Fields.name).asText() : null;
    String monitoredServiceIdentifier = tree.has(ChangeEventDTO.Fields.monitoredServiceIdentifier)
        ? tree.get(ChangeEventDTO.Fields.monitoredServiceIdentifier).asText()
        : null;
    String changeSourceIdentifier = tree.has(ChangeEventDTO.Fields.changeSourceIdentifier)
        ? tree.get(ChangeEventDTO.Fields.changeSourceIdentifier).asText()
        : null;

    Long eventTime =
        tree.has(ChangeEventDTO.Fields.eventTime) ? tree.get(ChangeEventDTO.Fields.eventTime).asLong() : null;

    ChangeEventDTO changeEventDTO = ChangeEventDTO.builder()
                                        .id(id)
                                        .accountId(accountId)
                                        .projectIdentifier(projectIdentifier)
                                        .orgIdentifier(orgIdentifier)
                                        .serviceIdentifier(serviceIdentifier)
                                        .serviceName(serviceName)
                                        .envIdentifier(envIdentifier)
                                        .environmentName(envName)
                                        .name(name)
                                        .monitoredServiceIdentifier(monitoredServiceIdentifier)
                                        .changeSourceIdentifier(changeSourceIdentifier)
                                        .eventTime(eventTime)
                                        .type(type)
                                        .build();

    JsonNode metadata = tree.get(ChangeEventDTO.Fields.metadata);
    if (metadata == null) {
      throw new BadRequestException("Spec is not serializable.");
    }
    Class<?> deserializationClass = deserializationMapper.get(type);
    if (deserializationClass == null) {
      throw new BadRequestException("Spec is not serializable, it doesn't match any schema.");
    }

    ChangeEventMetadata changeEventMetadata =
        (ChangeEventMetadata) JsonUtils.treeToValue(metadata, deserializationClass);
    changeEventDTO.setMetadata(changeEventMetadata);
    return changeEventDTO;
  }
}
