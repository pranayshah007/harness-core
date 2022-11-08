package io.harness.delegate.beans.connector.pcfconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(HarnessTeam.CDP)
public class PcfCredentialDTODeserializer extends StdDeserializer<PcfCredentialDTO> {
  public PcfCredentialDTODeserializer() {
    super(PcfCredentialDTO.class);
  }
  protected PcfCredentialDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public PcfCredentialDTO deserialize(JsonParser jp, DeserializationContext deserializationContext) throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    PcfCredentialType type = getType(typeNode);
    PcfCredentialSpecDTO pcfCredentialSpecDTO = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (type == PcfCredentialType.MANUAL_CREDENTIALS) {
      pcfCredentialSpecDTO = mapper.readValue(authSpec.toString(), PcfManualDetailsDTO.class);
    }
    return PcfCredentialDTO.builder().type(type).spec(pcfCredentialSpecDTO).build();
  }

  PcfCredentialType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return PcfCredentialType.fromString(typeValue);
  }
}
