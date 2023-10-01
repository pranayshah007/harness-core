/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.dto;

import static io.harness.SecretManagerDescriptionConstants.AWS_REGION;
import static io.harness.SecretManagerDescriptionConstants.ENABLE_CACHE;
import static io.harness.SecretManagerDescriptionConstants.K8S_AUTH_ENDPOINT;
import static io.harness.SecretManagerDescriptionConstants.RENEW_APPROLE_TOKEN;
import static io.harness.SecretManagerDescriptionConstants.SERVICE_ACCOUNT_TOKEN_PATH;
import static io.harness.SecretManagerDescriptionConstants.USE_AWS_IAM;
import static io.harness.SecretManagerDescriptionConstants.USE_K8s_AUTH;
import static io.harness.SecretManagerDescriptionConstants.VAULT_AWS_IAM_HEADER;
import static io.harness.SecretManagerDescriptionConstants.VAULT_AWS_IAM_ROLE;
import static io.harness.SecretManagerDescriptionConstants.VAULT_K8S_AUTH_ROLE;
import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@OwnedBy(PL)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@ToString(exclude = {"authToken", "secretId", "sinkPath", "xVaultAwsIamServerId"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "VaultConfig", description = "This contains the information for the Vault Secret Manager.")
public class VaultConfigDTO extends SecretManagerConfigDTO {
  @Schema(description = SecretManagerDescriptionConstants.AUTH_TOKEN) private String authToken;
  @Schema(description = SecretManagerDescriptionConstants.BASE_PATH) private String basePath;
  @Schema(description = SecretManagerDescriptionConstants.NAMESPACE) private String namespace;
  private String sinkPath;
  @Schema(description = SecretManagerDescriptionConstants.USE_VAULT_AGENT) private boolean useVaultAgent;
  @Schema(description = SecretManagerDescriptionConstants.VAULT_URL) private String vaultUrl;
  @Schema(description = SecretManagerDescriptionConstants.READ_ONLY)
  @JsonProperty("readOnly")
  private boolean isReadOnly;
  @Schema(description = SecretManagerDescriptionConstants.RENEWAL_INTERVAL_MINUTES) private long renewalIntervalMinutes;
  @Schema(description = SecretManagerDescriptionConstants.SECRET_ENGINE_NAME) private String secretEngineName;
  @Schema(description = SecretManagerDescriptionConstants.APP_ROLE_ID) private String appRoleId;
  @Schema(description = SecretManagerDescriptionConstants.SECRET_ID) private String secretId;
  @Schema(description = SecretManagerDescriptionConstants.SECRET_ENGINE_VERSION) private int secretEngineVersion;
  @Schema(description = SecretManagerDescriptionConstants.ENGINE_ENTERED_MANUALLY)
  private boolean engineManuallyEntered;
  @Schema(description = SecretManagerDescriptionConstants.DELEGATE_SELECTORS) private Set<String> delegateSelectors;

  @Schema(description = USE_AWS_IAM) private boolean useAwsIam;
  @Schema(description = AWS_REGION) private String awsRegion;
  @Schema(description = VAULT_AWS_IAM_ROLE) private String vaultAwsIamRole;
  @Schema(description = VAULT_AWS_IAM_HEADER) private String xVaultAwsIamServerId;

  @Schema(description = USE_K8s_AUTH) private boolean useK8sAuth;
  @Schema(description = VAULT_K8S_AUTH_ROLE) private String vaultK8sAuthRole;
  @Schema(description = SERVICE_ACCOUNT_TOKEN_PATH) private String serviceAccountTokenPath;
  @Schema(description = K8S_AUTH_ENDPOINT) private String k8sAuthEndpoint;

  @Schema(description = RENEW_APPROLE_TOKEN) private boolean renewAppRoleToken;
  @Schema(description = ENABLE_CACHE) private Boolean enableCache;
}
