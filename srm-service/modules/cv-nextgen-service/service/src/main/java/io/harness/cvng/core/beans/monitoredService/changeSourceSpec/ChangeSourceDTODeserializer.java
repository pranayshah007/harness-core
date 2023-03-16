/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.changeSourceSpec;

import io.harness.cvng.beans.change.ChangeSourceType;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO;
import io.harness.cvng.core.beans.monitoredService.ChangeSourceDTO.ChangeSourceDTOKeys;
import io.harness.serializer.JsonUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.ws.rs.BadRequestException;

public class ChangeSourceDTODeserializer extends JsonDeserializer<ChangeSourceDTO> {
  static Map<ChangeSourceType, Class<?>> deserializationMapper = new HashMap<>();
  static {
    deserializationMapper.put(ChangeSourceType.HARNESS_CD_CURRENT_GEN, HarnessCDCurrentGenChangeSourceSpec.class);
    deserializationMapper.put(ChangeSourceType.HARNESS_CD, HarnessCDChangeSourceSpec.class);
    deserializationMapper.put(ChangeSourceType.PAGER_DUTY, PagerDutyChangeSourceSpec.class);
    deserializationMapper.put(ChangeSourceType.KUBERNETES, KubernetesChangeSourceSpec.class);
    deserializationMapper.put(ChangeSourceType.CUSTOM_DEPLOY, CustomChangeSourceSpec.class);
    deserializationMapper.put(ChangeSourceType.CUSTOM_INFRA, CustomChangeSourceSpec.class);
    deserializationMapper.put(ChangeSourceType.CUSTOM_INCIDENT, CustomChangeSourceSpec.class);
    deserializationMapper.put(ChangeSourceType.CUSTOM_FF, CustomChangeSourceSpec.class);
  }

  @Override
  public ChangeSourceDTO deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode tree = jsonParser.readValueAsTree();
    ChangeSourceType type = JsonUtils.treeToValue(tree.get(ChangeSourceDTOKeys.type), ChangeSourceType.class);
    String name = tree.has(ChangeSourceDTOKeys.name) ? tree.get(ChangeSourceDTOKeys.name).asText() : null;
    String identifier =
        tree.has(ChangeSourceDTOKeys.identifier) ? tree.get(ChangeSourceDTOKeys.identifier).asText() : null;
    boolean enabled = tree.has(ChangeSourceDTOKeys.enabled) && tree.get(ChangeSourceDTOKeys.enabled).asBoolean();
    ChangeSourceDTO changeSourceDTO =
        ChangeSourceDTO.builder().name(name).identifier(identifier).type(type).enabled(enabled).build();
    JsonNode spec = tree.get(ChangeSourceDTOKeys.spec);
    if (spec == null) {
      throw new BadRequestException("Spec is not serializable");
    }
    Class<?> deserializationClass = deserializationMapper.get(type);
    if (deserializationClass == null) {
      throw new BadRequestException("Spec is not serializable, it doesn't match any schema.");
    }
    ChangeSourceSpec changeSourceSpec = (ChangeSourceSpec) JsonUtils.treeToValue(spec, deserializationClass);
    changeSourceDTO.setSpec(changeSourceSpec);
    return changeSourceDTO;
  }
}
