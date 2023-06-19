package io.harness.ng.core.entityusageactivity;

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

    EntityUsageType usageType = getType(typeNode);
    EntityUsageData usageData;

    ObjectMapper mapper = (ObjectMapper) jsonParser.getCodec();
    usageData = mapper.readValue(dataNode.toString(), EntityUsageData.class);

    return EntityUsageDetail.builder().usageType(usageType).usageData(usageData).build();
  }

  EntityUsageType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return EntityUsageType.valueOf(typeValue);
  }
}
