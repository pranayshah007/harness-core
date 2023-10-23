/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.opa.entities.secret;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.governance.GovernanceMetadata;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
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
public class OpaSecretServiceImpl implements OpaSecretService {
  private OpaService opaService;
  private AccountClient accountClient;

  private SecretOpaEvaluationContext createEvaluationContext(String yaml, String key) throws IOException {
    return SecretOpaEvaluationContext.builder().secret(OpaUtils.extractObjectFromYamlString(yaml, key)).build();
  }

  public GovernanceMetadata evaluatePoliciesWithEntity(String accountId, SecretDTOV2 secretDTO, String orgIdentifier,
      String projectIdentifier, String action, String identifier) {
    OpaEvaluationContext context;

    try {
      String expandedYaml = getSecretYaml(secretDTO);
      context = createEvaluationContext(expandedYaml, OpaConstants.OPA_EVALUATION_TYPE_SECRET);
      return opaService.evaluate(context, accountId, orgIdentifier, projectIdentifier, identifier, action,
          OpaConstants.OPA_EVALUATION_TYPE_SECRET);
    } catch (IOException ex) {
      return GovernanceMetadata.newBuilder()
          .setDeny(true)
          .setMessage(String.format("Could not create OPA context: [%s]", ex.getMessage()))
          .build();
    }
  }

  private String getSecretYaml(SecretDTOV2 secretDTO) {
    String connectorYaml = null;
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory()
                                                     .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                                                     .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                                                     .disable(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID));
    objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    try {
      connectorYaml = objectMapper.writeValueAsString(secretDTO);
    } catch (Exception ex) {
      log.error("Failed while converting to ConnectorDisconnectHandler yaml format", ex);
    }
    return connectorYaml;
  }
}
