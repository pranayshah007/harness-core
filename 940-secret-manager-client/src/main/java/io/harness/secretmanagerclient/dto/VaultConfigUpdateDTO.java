/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secretmanagerclient.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@ToString(exclude = {"authToken", "secretId", "sinkPath", "xVaultAwsIamServerId"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class VaultConfigUpdateDTO extends SecretManagerConfigUpdateDTO {
  private String authToken;
  private String basePath;
  private String namespace;
  private String sinkPath;
  private boolean useVaultAgent;
  private String vaultUrl;
  private boolean isReadOnly;
  private long renewalIntervalMinutes;
  private String secretEngineName;
  private int secretEngineVersion;
  private String appRoleId;
  private String secretId;
  private boolean useAwsIam;
  private String awsRegion;
  private String vaultAwsIamRole;
  private String xVaultAwsIamServerId;
  private boolean useK8sAuth;
  private String vaultK8sAuthRole;
  private String serviceAccountTokenPath;
  private String k8sAuthEndpoint;
  private boolean renewAppRoleToken;
  private boolean enableCache;
}
