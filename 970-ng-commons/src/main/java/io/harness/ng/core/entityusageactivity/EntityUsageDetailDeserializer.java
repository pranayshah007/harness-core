/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.entityusageactivity;

import static java.util.Objects.isNull;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class EntityUsageDetailDeserializer extends StdDeserializer<EntityUsageDetail> {
  public EntityUsageDetailDeserializer() {
    super(EntityUsageDetailDeserializer.class);
  }

  public EntityUsageDetailDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public EntityUsageDetail deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jsonParser.getCodec().readTree(jsonParser);
    JsonNode typeNode = parentJsonNode.get("usageType");
    JsonNode dataNode = parentJsonNode.get("usageData");

    if (isNull(typeNode)) {
      return EntityUsageDetail.builder().build();
    }
    String usageType = typeNode.toString();

    if (isNull(dataNode)) {
      return EntityUsageDetail.builder().usageType(usageType).build();
    }

    EntityUsageData usageData;
    ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
    usageData = mapper.readValue(dataNode.toString(), EntityUsageData.class);

    return EntityUsageDetail.builder().usageType(usageType).usageData(usageData).build();
  }
}