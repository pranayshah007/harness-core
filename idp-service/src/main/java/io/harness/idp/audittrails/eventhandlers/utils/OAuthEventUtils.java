/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.audittrails.eventhandlers.utils;

import static io.harness.idp.common.Constants.*;

import static io.serializer.HObjectMapper.NG_DEFAULT_OBJECT_MAPPER;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidArgumentsException;
import io.harness.idp.audittrails.eventhandlers.dtos.GitHubOAuthConfigDTO;
import io.harness.idp.audittrails.eventhandlers.dtos.GoogleOAuthConfigDTO;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.spec.server.idp.v1.model.BackstageEnvConfigVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class OAuthEventUtils {
  private static final ObjectMapper objectMapper = NG_DEFAULT_OBJECT_MAPPER;

  public String getOAuthConfigYamlForAudit(
      String accountIdentifier, List<BackstageEnvVariable> backstageEnvVariables, String auditId) {
    switch (auditId) {
      case GITHUB_AUTH:
        return getGitHubOAuthConfigYamlForAudit(accountIdentifier, backstageEnvVariables, auditId);
      case GOOGLE_AUTH:
        return getGoogleOAuthConfigYamlForAudit(accountIdentifier, backstageEnvVariables, auditId);
      default:
        throw new InvalidArgumentsException(String.format("Not supported Auth Type %s", auditId));
    }
  }

  private String getGitHubOAuthConfigYamlForAudit(
      String accountIdentifier, List<BackstageEnvVariable> envVariables, String authId) {
    Map<String, BackstageEnvVariable> mappedEnvVariables = getMappedEnvVariables(envVariables);
    BackstageEnvConfigVariable clientIdEnvVariable =
        (BackstageEnvConfigVariable) mappedEnvVariables.get(AUTH_GITHUB_CLIENT_ID);
    String clientId = clientIdEnvVariable.getValue();

    BackstageEnvSecretVariable clientSecretEnvVariable =
        (BackstageEnvSecretVariable) mappedEnvVariables.get(AUTH_GITHUB_CLIENT_SECRET);
    String clientSecret = clientSecretEnvVariable.getHarnessSecretIdentifier();

    String enterpriseUrl = null;
    if (mappedEnvVariables.get(AUTH_GITHUB_ENTERPRISE_INSTANCE_URL) != null) {
      BackstageEnvConfigVariable enterpriseUrlEnvVariable =
          (BackstageEnvConfigVariable) mappedEnvVariables.get(AUTH_GITHUB_ENTERPRISE_INSTANCE_URL);
      enterpriseUrl = enterpriseUrlEnvVariable.getValue();
    }

    return NGYamlUtils.getYamlString(GitHubOAuthConfigDTO.builder()
                                         .authIdentifier(authId)
                                         .accountIdentifier(accountIdentifier)
                                         .clientId(clientId)
                                         .clientSecret(clientSecret)
                                         .enterpriseInstanceUrl(enterpriseUrl)
                                         .build(),
        objectMapper);
  }

  private String getGoogleOAuthConfigYamlForAudit(
      String accountIdentifier, List<BackstageEnvVariable> envVariables, String authId) {
    Map<String, BackstageEnvVariable> mappedEnvVariables = getMappedEnvVariables(envVariables);
    BackstageEnvConfigVariable clientIdEnvVariable =
        (BackstageEnvConfigVariable) mappedEnvVariables.get(AUTH_GOOGLE_CLIENT_ID);
    String clientId = clientIdEnvVariable.getValue();

    BackstageEnvSecretVariable clientSecretEnvVariable =
        (BackstageEnvSecretVariable) mappedEnvVariables.get(AUTH_GOOGLE_CLIENT_SECRET);
    String clientSecret = clientSecretEnvVariable.getHarnessSecretIdentifier();

    return NGYamlUtils.getYamlString(GoogleOAuthConfigDTO.builder()
                                         .authIdentifier(authId)
                                         .accountIdentifier(accountIdentifier)
                                         .clientId(clientId)
                                         .clientSecret(clientSecret)
                                         .build(),
        objectMapper);
  }

  private Map<String, BackstageEnvVariable> getMappedEnvVariables(List<BackstageEnvVariable> backstageEnvVariables) {
    return backstageEnvVariables.stream().collect(
        Collectors.toMap(BackstageEnvVariable::getEnvName, Function.identity()));
  }
}
