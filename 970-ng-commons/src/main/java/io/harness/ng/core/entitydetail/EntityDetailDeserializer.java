/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.entitydetail;

import io.harness.EntityType;
import io.harness.beans.EntityReference;
import io.harness.beans.IdentifierRef;
import io.harness.beans.InputSetReference;
import io.harness.beans.NGTemplateReference;
import io.harness.ng.core.EntityDetail;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class EntityDetailDeserializer extends StdDeserializer<EntityDetail> {
  public EntityDetailDeserializer() {
    super(EntityDetailDeserializer.class);
  }

  public EntityDetailDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public EntityDetail deserialize(JsonParser jp, DeserializationContext deserializationContext) throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode entityRefNode = parentJsonNode.get("entityRef");
    JsonNode nameNode = parentJsonNode.get("name");

    EntityType type = getType(typeNode);
    EntityReference reference;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (type == EntityType.INPUT_SETS) {
      reference = mapper.readValue(entityRefNode.toString(), InputSetReference.class);
    } else if (type == EntityType.TEMPLATE) {
      reference = mapper.readValue(entityRefNode.toString(), NGTemplateReference.class);
    } else {
      reference = mapper.readValue(entityRefNode.toString(), IdentifierRef.class);
    }

    String name = nameNode != null ? nameNode.asText() : null;

    return EntityDetail.builder().type(type).entityRef(reference).name(name).build();
  }

  EntityType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return EntityType.fromString(typeValue);
  }
}
