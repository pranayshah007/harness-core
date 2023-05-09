/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.vaultconnector.outcome;

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
import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.connector.DelegateSelectable;
import io.harness.delegate.beans.connector.ConnectorConfigOutcomeDTO;
import io.harness.encryption.SecretRefData;
import io.harness.secret.SecretReference;
import io.harness.security.encryption.AccessType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@OwnedBy(CDP)
@Data
@Builder
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultConnectorOutcomeDTO extends ConnectorConfigOutcomeDTO implements DelegateSelectable {
  @SecretReference @Schema(description = SecretManagerDescriptionConstants.AUTH_TOKEN) private SecretRefData authToken;
  @Schema(description = SecretManagerDescriptionConstants.BASE_PATH) private String basePath;
  @org.hibernate.validator.constraints.URL
  @NotNull
  @Schema(description = SecretManagerDescriptionConstants.VAULT_URL)
  private String vaultUrl;
  @Schema(description = SecretManagerDescriptionConstants.READ_ONLY) private boolean readOnly;
  @NotNull
  @Schema(description = SecretManagerDescriptionConstants.RENEWAL_INTERVAL_MINUTES)
  private long renewalIntervalMinutes;
  @Schema(description = SecretManagerDescriptionConstants.ENGINE_ENTERED_MANUALLY)
  private boolean secretEngineManuallyConfigured;
  @Schema(description = SecretManagerDescriptionConstants.SECRET_ENGINE_NAME) private String secretEngineName;
  @Schema(description = SecretManagerDescriptionConstants.APP_ROLE_ID) private String appRoleId;
  @Schema(description = SecretManagerDescriptionConstants.SECRET_ID) @SecretReference private SecretRefData secretId;
  @Schema(description = SecretManagerDescriptionConstants.DEFAULT) private boolean isDefault;
  @Schema(description = SecretManagerDescriptionConstants.SECRET_ENGINE_VERSION) private int secretEngineVersion;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;
  @Schema(description = SecretManagerDescriptionConstants.NAMESPACE) private String namespace;
  @Schema(description = SINK_PATH) private String sinkPath;
  @Schema(description = SecretManagerDescriptionConstants.USE_VAULT_AGENT) private boolean useVaultAgent;
  @Schema(description = USE_AWS_IAM) private boolean useAwsIam;
  @Schema(description = AWS_REGION) private String awsRegion;
  @Schema(description = VAULT_AWS_IAM_ROLE) private String vaultAwsIamRole;
  @SecretReference @Schema(description = VAULT_AWS_IAM_HEADER) private SecretRefData xvaultAwsIamServerId;
  @Schema(description = USE_K8s_AUTH) private boolean useK8sAuth;
  @Schema(description = VAULT_K8S_AUTH_ROLE) private String vaultK8sAuthRole;
  @Schema(description = SERVICE_ACCOUNT_TOKEN_PATH) private String serviceAccountTokenPath;
  @Schema(description = K8S_AUTH_ENDPOINT) private String k8sAuthEndpoint;
  @Schema(description = RENEW_APPROLE_TOKEN) private boolean renewAppRoleToken;
  private AccessType accessType;
}
