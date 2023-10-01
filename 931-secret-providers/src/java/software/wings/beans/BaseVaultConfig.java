/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.SecretString.SECRET_MASK;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;
import io.harness.mongo.index.FdIndex;
import io.harness.security.encryption.AccessType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@OwnedBy(PL)
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"authToken", "secretId"})
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "BaseVaultConfigKeys")
public abstract class BaseVaultConfig extends SecretManagerConfig {
  private static final String TASK_SELECTORS = "Task Selectors";
  @Attributes(title = "Name", required = true) public String name;
  @Attributes(title = "Vault Url", required = true) @FdIndex public String vaultUrl;
  @Attributes(title = "Auth token") @Encrypted(fieldName = "auth_token") public String authToken;
  @Attributes(title = "AppRole Id") public String appRoleId;
  @Attributes(title = "Secret Id") @Encrypted(fieldName = "secret_id") public String secretId;
  @Attributes(title = "Secret Engine Name") public String secretEngineName;
  @Attributes(title = "Token Renewal Interval in minutes", required = true) private long renewalInterval;
  @Attributes(title = "Is Secret Engine Manually Entered") private boolean engineManuallyEntered;
  @Attributes(title = "Namespace") private String namespace;
  @JsonIgnore @SchemaIgnore boolean isCertValidationRequired;
  private long renewedAt;

  @Attributes(title = "useVaultAgent") private boolean useVaultAgent;
  @Attributes(title = "delegateSelectors") private Set<String> delegateSelectors;
  @Attributes(title = "sinkPath") private String sinkPath;

  // added for Aws Iam Auth Method
  @Attributes(title = "useAwsIam") private boolean useAwsIam;
  @Attributes(title = "AwsIAM Auth Role") public String vaultAwsIamRole;
  @Attributes(title = "AWS region") private String awsRegion;
  @Attributes(title = "X_VAULT_AWS_IAM_SERVER_ID") private String xVaultAwsIamServerId;

  // added for K8s Auth Method
  @Attributes(title = "useK8sAuth") private boolean useK8sAuth;
  @Attributes(title = "K8s Auth Role") private String vaultK8sAuthRole;
  @Attributes(title = "serviceAccountTokenPath") private String serviceAccountTokenPath;
  @Attributes(title = "k8sAuthEndpoint") private String k8sAuthEndpoint;

  @Attributes(title = "renewAppRoleToken") @Builder.Default private Boolean renewAppRoleToken = Boolean.TRUE;
  @Attributes(title = "enableCache") private Boolean enableCache;

  public boolean isCertValidationRequired() {
    return isCertValidationRequired;
  }

  @Override
  public void maskSecrets() {
    if (isNotEmpty(this.authToken)) {
      this.authToken = SECRET_MASK;
    }
    if (isNotEmpty(this.secretId)) {
      this.secretId = SECRET_MASK;
    }
  }

  @JsonIgnore
  @SchemaIgnore
  @Override
  public String getValidationCriteria() {
    return getEncryptionServiceUrl();
  }

  @JsonIgnore
  @SchemaIgnore
  @Override
  public String getEncryptionServiceUrl() {
    return vaultUrl;
  }

  @JsonIgnore
  @SchemaIgnore
  public AccessType getAccessType() {
    return isNotEmpty(appRoleId) ? AccessType.APP_ROLE : AccessType.TOKEN;
  }

  @JsonIgnore
  @SchemaIgnore
  public boolean getRenewAppRoleToken() {
    return renewAppRoleToken == null ? Boolean.TRUE : renewAppRoleToken;
  }

  @JsonIgnore
  @SchemaIgnore
  public boolean getEnableCache() {
    return enableCache == null ? Boolean.FALSE : enableCache;
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>(Arrays.asList(
        HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(vaultUrl, maskingEvaluator)));
    if (isNotEmpty(delegateSelectors)) {
      executionCapabilities.add(
          SelectorCapability.builder().selectors(delegateSelectors).selectorOrigin(TASK_SELECTORS).build());
    }
    return executionCapabilities;
  }
}
