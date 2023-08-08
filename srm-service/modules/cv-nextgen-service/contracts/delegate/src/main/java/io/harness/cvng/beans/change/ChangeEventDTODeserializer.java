/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.beans.change;

import io.harness.cvng.beans.change.ChangeEventDTO.ChangeEventDTOKeys;
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
    deserializationMapper.put(ChangeSourceType.SRM_STEP_ANALYSIS, HarnessSRMAnalysisEventMetadata.class);
  }

  @Override
  public ChangeEventDTO deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode tree = jsonParser.readValueAsTree();
    ChangeSourceType type = JsonUtils.treeToValue(tree.get(ChangeEventDTOKeys.type), ChangeSourceType.class);

    String id = tree.has(ChangeEventDTOKeys.id) ? tree.get(ChangeEventDTOKeys.id).asText() : null;
    String accountId = tree.has(ChangeEventDTOKeys.accountId) ? tree.get(ChangeEventDTOKeys.accountId).asText() : null;
    String orgIdentifier =
        tree.has(ChangeEventDTOKeys.orgIdentifier) ? tree.get(ChangeEventDTOKeys.orgIdentifier).asText() : null;
    String projectIdentifier =
        tree.has(ChangeEventDTOKeys.projectIdentifier) ? tree.get(ChangeEventDTOKeys.projectIdentifier).asText() : null;

    String serviceIdentifier =
        tree.has(ChangeEventDTOKeys.serviceIdentifier) ? tree.get(ChangeEventDTOKeys.serviceIdentifier).asText() : null;
    String serviceName =
        tree.has(ChangeEventDTOKeys.serviceName) ? tree.get(ChangeEventDTOKeys.serviceName).asText() : null;

    String envIdentifier =
        tree.has(ChangeEventDTOKeys.envIdentifier) ? tree.get(ChangeEventDTOKeys.envIdentifier).asText() : null;
    String envName =
        tree.has(ChangeEventDTOKeys.environmentName) ? tree.get(ChangeEventDTOKeys.environmentName).asText() : null;

    String name = tree.has(ChangeEventDTOKeys.name) ? tree.get(ChangeEventDTOKeys.name).asText() : null;
    String monitoredServiceIdentifier = tree.has(ChangeEventDTOKeys.monitoredServiceIdentifier)
        ? tree.get(ChangeEventDTOKeys.monitoredServiceIdentifier).asText()
        : null;
    String changeSourceIdentifier = tree.has(ChangeEventDTOKeys.changeSourceIdentifier)
        ? tree.get(ChangeEventDTOKeys.changeSourceIdentifier).asText()
        : null;

    Long eventTime = tree.has(ChangeEventDTOKeys.eventTime) ? tree.get(ChangeEventDTOKeys.eventTime).asLong() : null;

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

    JsonNode metadata = tree.get(ChangeEventDTOKeys.metadata);
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
