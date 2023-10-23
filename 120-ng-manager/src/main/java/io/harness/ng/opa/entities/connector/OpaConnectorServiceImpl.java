/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.entities.connector;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.ConnectorDTO;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.opa.OpaEvaluationContext;
import io.harness.ng.opa.OpaService;
import io.harness.opaclient.OpaUtils;
import io.harness.opaclient.model.OpaConstants;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.google.inject.Inject;
import java.io.IOException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class OpaConnectorServiceImpl implements OpaConnectorService {
  private OpaService opaService;
  private AccountClient accountClient;

  public ConnectorOpaEvaluationContext createEvaluationContext(String yaml, String key) throws IOException {
    return ConnectorOpaEvaluationContext.builder().entity(OpaUtils.extractObjectFromYamlString(yaml, key)).build();
  }

  public GovernanceMetadata evaluatePoliciesWithEntity(String accountId, ConnectorDTO connectorDTO,
      String orgIdentifier, String projectIdentifier, String action, String identifier) {
    OpaEvaluationContext context;

    try {
      String expandedYaml = getConnectorYaml(connectorDTO);
      context = createEvaluationContext(expandedYaml, OpaConstants.OPA_EVALUATION_TYPE_CONNECTOR);
      return opaService.evaluate(context, accountId, orgIdentifier, projectIdentifier, identifier, action,
          OpaConstants.OPA_EVALUATION_TYPE_CONNECTOR);
    } catch (IOException ex) {
      return GovernanceMetadata.newBuilder()
          .setDeny(true)
          .setMessage(String.format("Could not create OPA context: [%s]", ex.getMessage()))
          .build();
    }
  }

  private String getConnectorYaml(ConnectorDTO connectorDTO) {
    String connectorYaml = null;
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      connectorYaml = objectMapper.writeValueAsString(connectorDTO);
    } catch (Exception ex) {
      log.error("Failed while converting to ConnectorDisconnectHandler yaml format", ex);
    }
    return connectorYaml;
  }

  public void evaluatePoliciesWithJson(String accountId, String expandedJson, String orgIdentifier,
      String projectIdentifier, String action, String identifier) {
    OpaEvaluationContext context;
    try {
      context = createEvaluationContext(expandedJson, OpaConstants.OPA_EVALUATION_TYPE_CONNECTOR);
      opaService.evaluate(context, accountId, orgIdentifier, projectIdentifier, identifier, action,
          OpaConstants.OPA_EVALUATION_TYPE_CONNECTOR);
    } catch (IOException ex) {
      log.error("Could not create OPA evaluation context", ex);
    }
  }
}
