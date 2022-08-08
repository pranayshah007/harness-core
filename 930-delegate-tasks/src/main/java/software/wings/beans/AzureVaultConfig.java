/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.azure.AzureEnvironmentType.AZURE;
import static io.harness.beans.SecretManagerCapabilities.CAN_BE_DEFAULT_SM;
import static io.harness.beans.SecretManagerCapabilities.CREATE_FILE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_INLINE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.CREATE_REFERENCE_SECRET;
import static io.harness.beans.SecretManagerCapabilities.TRANSITION_SECRET_FROM_SM;
import static io.harness.beans.SecretManagerCapabilities.TRANSITION_SECRET_TO_SM;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.security.encryption.SecretManagerType.VAULT;

import io.harness.annotations.dev.OwnedBy;
import io.harness.azure.AzureEnvironmentType;
import io.harness.beans.SecretManagerCapabilities;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.delegate.task.mixin.HttpConnectionExecutionCapabilityGenerator;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;
import io.harness.mappers.SecretManagerConfigMapper;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.secretmanagerclient.dto.azurekeyvault.AzureKeyVaultConfigDTO;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"secretKey"})
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "AzureVaultConfigKeys")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AzureVaultConfig extends SecretManagerConfig {
  private static final String TASK_SELECTORS = "Task Selectors";
  public static final String AZURE_VAULT_VALIDATION_URL = "harnessAzureVaultValidation";
  @Attributes(title = "Name", required = true) private String name;

  @Attributes(title = "Azure Client Id", required = true) @NotEmpty private String clientId;

  @Attributes(title = "Azure Secret Id", required = true)
  @NotEmpty
  @Encrypted(fieldName = "azure_secret_id")
  private String secretKey;

  @Attributes(title = "Azure Tenant Id", required = true) @NotEmpty private String tenantId;

  @Attributes(title = "Azure Vault Name", required = true) private String vaultName;

  @Attributes(title = "Subscription") private String subscription;

  @Attributes(title = "delegateSelectors") private Set<String> delegateSelectors;

  @Builder.Default private AzureEnvironmentType azureEnvironmentType = AZURE;

  @Override
  public void maskSecrets() {
    this.secretKey = SECRET_MASK;
  }

  @JsonIgnore
  @SchemaIgnore
  @Override
  public String getValidationCriteria() {
    return obtainEncryptionServiceUrl();
  }

  @Override
  public SecretManagerType getType() {
    return VAULT;
  }

  @JsonIgnore
  @SchemaIgnore
  @Override
  public String getEncryptionServiceUrl() {
    return obtainEncryptionServiceUrl();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities =
        new ArrayList<>(Arrays.asList(HttpConnectionExecutionCapabilityGenerator.buildHttpConnectionExecutionCapability(
            getEncryptionServiceUrl(), maskingEvaluator)));
    if (isNotEmpty(getDelegateSelectors())) {
      executionCapabilities.add(
          SelectorCapability.builder().selectors(getDelegateSelectors()).selectorOrigin(TASK_SELECTORS).build());
    }
    return executionCapabilities;
  }

  @Override
  public EncryptionType getEncryptionType() {
    return EncryptionType.AZURE_VAULT;
  }

  private String obtainEncryptionServiceUrl() {
    if (this.azureEnvironmentType == null) {
      return String.format("https://%s.vault.azure.net", getVaultName());
    }

    switch (this.azureEnvironmentType) {
      case AZURE_US_GOVERNMENT:
        return String.format("https://%s.vault.usgovcloudapi.net", getVaultName());

      case AZURE:
      default:
        return String.format("https://%s.vault.azure.net", getVaultName());
    }
  }

  @Override
  public List<SecretManagerCapabilities> getSecretManagerCapabilities() {
    List<SecretManagerCapabilities> secretManagerCapabilities =
        Lists.newArrayList(CREATE_INLINE_SECRET, CREATE_REFERENCE_SECRET, CREATE_FILE_SECRET, CAN_BE_DEFAULT_SM);
    if (!isTemplatized()) {
      secretManagerCapabilities.add(TRANSITION_SECRET_FROM_SM);
      secretManagerCapabilities.add(TRANSITION_SECRET_TO_SM);
    }
    return secretManagerCapabilities;
  }

  @Override
  public SecretManagerConfigDTO toDTO(boolean maskSecrets) {
    AzureKeyVaultConfigDTO ngAzureKeyVaultConfigDTO = AzureKeyVaultConfigDTO.builder()
                                                          .encryptionType(getEncryptionType())
                                                          .name(getName())
                                                          .isDefault(isDefault())
                                                          .clientId(getClientId())
                                                          .tenantId(getTenantId())
                                                          .vaultName(getVaultName())
                                                          .subscription(getSubscription())
                                                          .azureEnvironmentType(getAzureEnvironmentType())
                                                          .delegateSelectors(getDelegateSelectors())
                                                          .build();
    SecretManagerConfigMapper.updateNGSecretManagerMetadata(getNgMetadata(), ngAzureKeyVaultConfigDTO);
    if (!maskSecrets) {
      ngAzureKeyVaultConfigDTO.setSecretKey(getSecretKey());
    }
    return ngAzureKeyVaultConfigDTO;
  }
}
