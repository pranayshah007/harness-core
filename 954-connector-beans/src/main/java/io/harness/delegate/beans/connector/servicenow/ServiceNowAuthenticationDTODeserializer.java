/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.servicenow;
import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.exception.InvalidRequestException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_APPROVALS})
@OwnedBy(CDC)
public class ServiceNowAuthenticationDTODeserializer extends StdDeserializer<ServiceNowAuthenticationDTO> {
  public ServiceNowAuthenticationDTODeserializer() {
    super(io.harness.delegate.beans.connector.servicenow.ServiceNowAuthenticationDTODeserializer.class);
  }

  public ServiceNowAuthenticationDTODeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public ServiceNowAuthenticationDTO deserialize(JsonParser jp, DeserializationContext deserializationContext)
      throws IOException {
    JsonNode parentJsonNode = jp.getCodec().readTree(jp);
    JsonNode typeNode = parentJsonNode.get("type");
    JsonNode authSpec = parentJsonNode.get("spec");

    ServiceNowAuthType type = getType(typeNode);
    ServiceNowAuthCredentialsDTO servicenowAuthCredentials = null;

    ObjectMapper mapper = (ObjectMapper) jp.getCodec();
    if (authSpec == null || authSpec.isNull()) {
      throw new InvalidRequestException(
          String.format("Missing spec for %s service now auth type", ServiceNowAuthType.USER_PASSWORD));
    }
    if (type == ServiceNowAuthType.USER_PASSWORD) {
      servicenowAuthCredentials = mapper.readValue(authSpec.toString(), ServiceNowUserNamePasswordDTO.class);
    }
    if (type == ServiceNowAuthType.ADFS) {
      servicenowAuthCredentials = mapper.readValue(authSpec.toString(), ServiceNowADFSDTO.class);
    }
    if (type == ServiceNowAuthType.REFRESH_TOKEN) {
      servicenowAuthCredentials = mapper.readValue(authSpec.toString(), ServiceNowRefreshTokenDTO.class);
    }
    // add condition for future types here
    return ServiceNowAuthenticationDTO.builder().authType(type).credentials(servicenowAuthCredentials).build();
  }

  ServiceNowAuthType getType(JsonNode typeNode) {
    String typeValue = typeNode.asText();
    return ServiceNowAuthType.fromString(typeValue);
  }
}
