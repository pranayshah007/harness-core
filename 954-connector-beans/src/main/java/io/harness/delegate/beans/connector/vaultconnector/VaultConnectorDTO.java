/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.vaultconnector;

import static io.harness.SecretManagerDescriptionConstants.AWS_REGION;
import static io.harness.SecretManagerDescriptionConstants.K8S_AUTH_ENDPOINT;
import static io.harness.SecretManagerDescriptionConstants.RENEW_APPROLE_TOKEN;
import static io.harness.SecretManagerDescriptionConstants.SERVICE_ACCOUNT_TOKEN_PATH;
import static io.harness.SecretManagerDescriptionConstants.SINK_PATH;
import static io.harness.SecretManagerDescriptionConstants.USE_AWS_IAM;
import static io.harness.SecretManagerDescriptionConstants.USE_K8s_AUTH;
import static io.harness.SecretManagerDescriptionConstants.VAULT_AWS_IAM_HEADER;
import static io.harness.SecretManagerDescriptionConstants.VAULT_AWS_IAM_ROLE;
import static io.harness.SecretManagerDescriptionConstants.VAULT_K8S_AUTH_ROLE;
import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_REQUEST;
import static io.harness.exception.WingsException.USER;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.delegate.beans.connector.vaultconnector.outcome.VaultConnectorOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.exception.InvalidRequestException;
import io.harness.secret.SecretReference;
import io.harness.security.encryption.AccessType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@OwnedBy(PL)
@Getter
@Setter
@Builder
@ToString(exclude = {"authToken", "secretId", "sinkPath", "xVaultAwsIamServerId"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
@Schema(name = "VaultConnector", description = "This contains the Vault Connector configuration.")
public class VaultConnectorDTO extends ConnectorConfigDTO implements DelegateSelectable {
  @SecretReference
  @ApiModelProperty(dataType = "string")
  @Schema(description = SecretManagerDescriptionConstants.AUTH_TOKEN)
  private SecretRefData authToken;
  @Schema(description = SecretManagerDescriptionConstants.BASE_PATH) private String basePath;
  @org.hibernate.validator.constraints.URL
  @NotNull
  @Schema(description = SecretManagerDescriptionConstants.VAULT_URL)
  private String vaultUrl;
  @Schema(description = SecretManagerDescriptionConstants.READ_ONLY) private boolean isReadOnly;
  @NotNull
  @Schema(description = SecretManagerDescriptionConstants.RENEWAL_INTERVAL_MINUTES)
  private long renewalIntervalMinutes;
  @Schema(description = SecretManagerDescriptionConstants.ENGINE_ENTERED_MANUALLY)
  private boolean secretEngineManuallyConfigured;
  @Schema(description = SecretManagerDescriptionConstants.SECRET_ENGINE_NAME) private String secretEngineName;
  @Schema(description = SecretManagerDescriptionConstants.APP_ROLE_ID) private String appRoleId;
  @Schema(description = SecretManagerDescriptionConstants.SECRET_ID)
  @SecretReference
  @ApiModelProperty(dataType = "string")
  private SecretRefData secretId;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;
  @Schema(description = SecretManagerDescriptionConstants.SECRET_ENGINE_VERSION) private int secretEngineVersion;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;
  @Schema(description = SecretManagerDescriptionConstants.NAMESPACE) private String namespace;
  @Schema(description = SINK_PATH) private String sinkPath;
  @Schema(description = SecretManagerDescriptionConstants.USE_VAULT_AGENT) private boolean useVaultAgent;
  @Schema(description = USE_AWS_IAM) private boolean useAwsIam;
  @Schema(description = AWS_REGION) private String awsRegion;
  @Schema(description = VAULT_AWS_IAM_ROLE) private String vaultAwsIamRole;
  @SecretReference
  @ApiModelProperty(dataType = "string")
  @Schema(description = VAULT_AWS_IAM_HEADER)
  @JsonProperty(value = "xvaultAwsIamServerId")
  private SecretRefData headerAwsIam;
  @Schema(description = USE_K8s_AUTH) private boolean useK8sAuth;
  @Schema(description = VAULT_K8S_AUTH_ROLE) private String vaultK8sAuthRole;
  @Schema(description = SERVICE_ACCOUNT_TOKEN_PATH) private String serviceAccountTokenPath;
  @Schema(description = K8S_AUTH_ENDPOINT) private String k8sAuthEndpoint;
  @Schema(description = RENEW_APPROLE_TOKEN) private boolean renewAppRoleToken;

  public AccessType getAccessType() {
    if (useVaultAgent) {
      return AccessType.VAULT_AGENT;
    } else if (useAwsIam) {
      return AccessType.AWS_IAM;
    } else if (useK8sAuth) {
      return AccessType.K8s_AUTH;
    } else {
      return isNotEmpty(appRoleId) ? AccessType.APP_ROLE : AccessType.TOKEN;
    }
  }

  @Override
  public List<DecryptableEntity> getDecryptableEntities() {
    return Collections.singletonList(this);
  }

  @Override
  public ConnectorConfigOutcomeDTO toOutcome() {
    return VaultConnectorOutcomeDTO.builder()
        .authToken(authToken)
        .basePath(basePath)
        .vaultUrl(vaultUrl)
        .isReadOnly(isReadOnly)
        .renewalIntervalMinutes(renewalIntervalMinutes)
        .secretEngineManuallyConfigured(secretEngineManuallyConfigured)
        .secretEngineName(secretEngineName)
        .appRoleId(appRoleId)
        .secretId(secretId)
        .isDefault(isDefault)
        .secretEngineVersion(secretEngineVersion)
        .delegateSelectors(delegateSelectors)
        .namespace(namespace)
        .sinkPath(sinkPath)
        .useVaultAgent(useVaultAgent)
        .useAwsIam(useAwsIam)
        .awsRegion(awsRegion)
        .vaultAwsIamRole(vaultAwsIamRole)
        .headerAwsIam(headerAwsIam)
        .useK8sAuth(useK8sAuth)
        .vaultK8sAuthRole(vaultK8sAuthRole)
        .serviceAccountTokenPath(serviceAccountTokenPath)
        .k8sAuthEndpoint(k8sAuthEndpoint)
        .renewAppRoleToken(renewAppRoleToken)
        .build();
  }

  @Override
  public void validate() {
    try {
      new URL(vaultUrl);
    } catch (MalformedURLException malformedURLException) {
      throw new InvalidRequestException("Please check the Vault url and try again.", INVALID_REQUEST, USER);
    }
    if (isBlank(vaultUrl)) {
      throw new InvalidRequestException(String.format("Invalid value for Vault URL"), INVALID_REQUEST, USER);
    }
    if (secretEngineVersion <= 0) {
      throw new InvalidRequestException(
          String.format("Invalid value for secret engine version: %s", secretEngineVersion), INVALID_REQUEST, USER);
    }

    if (getAccessType() == AccessType.APP_ROLE) {
      if (isBlank(appRoleId)) {
        throw new InvalidRequestException(
            "You must provide a App Role Id if you are using AppRole Authentication for Vault.", INVALID_REQUEST, USER);
      }
      if (null == secretId || isEmpty(secretId.getIdentifier())) {
        throw new InvalidRequestException(
            "You must provide the secretId if you are using AppRole Authentication for Vault", INVALID_REQUEST, USER);
      }
    }

    if (getAccessType() == AccessType.TOKEN) {
      if (authToken == null) {
        throw new InvalidRequestException(
            "You must provide a Auth Token if you are using Token Authentication for Vault", INVALID_REQUEST, USER);
      }
    }

    if (renewalIntervalMinutes < 0) {
      throw new InvalidRequestException(
          "Invalid value for renewal interval. It cannot be negative number", INVALID_REQUEST, USER);
    }
    if (isUseVaultAgent() && isUseAwsIam()) {
      throw new InvalidRequestException(
          "You must use either Vault Agent or Aws Iam Auth method to authenticate. Both can not be used together",
          INVALID_REQUEST, USER);
    }
    if (isUseVaultAgent() && isUseK8sAuth()) {
      throw new InvalidRequestException(
          "You must use either Vault Agent or K8s Auth method to authenticate. Both can not be used together",
          INVALID_REQUEST, USER);
    }
    if (isUseK8sAuth() && isUseAwsIam()) {
      throw new InvalidRequestException(
          "You must use either K8s or Aws Iam Auth method to authenticate. Both can not be used together",
          INVALID_REQUEST, USER);
    }
    if (isUseVaultAgent()) {
      if (isBlank(getSinkPath())) {
        throw new InvalidRequestException(
            "You must provide a sink path to read token if you are using VaultAgent", INVALID_REQUEST, USER);
      }
      if (isEmpty(getDelegateSelectors())) {
        throw new InvalidRequestException(
            "You must provide a delegate selector to read token if you are using VaultAgent", INVALID_REQUEST, USER);
      }
    }
    if (isUseAwsIam()) {
      if (isBlank(getVaultAwsIamRole())) {
        throw new InvalidRequestException(
            "You must provide a vault role if you are using Vault with Aws Iam Auth method", INVALID_REQUEST, USER);
      }
      if (isBlank(getAwsRegion())) {
        throw new InvalidRequestException(
            "You must provide a aws region if you are using Vault with Aws Iam Auth method", INVALID_REQUEST, USER);
      }
      if (isEmpty(getDelegateSelectors())) {
        throw new InvalidRequestException(
            "You must provide a delegate selector which can connect to vault using Aws IAM auth method",
            INVALID_REQUEST, USER);
      }
    }
    if (isUseK8sAuth()) {
      if (isBlank(getVaultK8sAuthRole())) {
        throw new InvalidRequestException(
            "You must provide a vault role if you are using Vault with K8s Auth method", INVALID_REQUEST, USER);
      }
      if (isBlank(getServiceAccountTokenPath())) {
        throw new InvalidRequestException(
            "You must provide the Service Account token path if you are using Vault with K8s Auth method",
            INVALID_REQUEST, USER);
      }
      if (isEmpty(getDelegateSelectors())) {
        throw new InvalidRequestException(
            "You must provide a delegate selector which can connect to vault using K8s auth method", INVALID_REQUEST,
            USER);
      }
    }
  }
}
