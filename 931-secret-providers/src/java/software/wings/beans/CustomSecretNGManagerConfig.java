/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.beans.SecretManagerCapabilities.CREATE_PARAMETERIZED_SECRET;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.security.encryption.SecretManagerType.CUSTOM;

import io.harness.SecretManagerDescriptionConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SecretManagerCapabilities;
import io.harness.beans.SecretManagerConfig;
import io.harness.delegate.beans.connector.customsecretmanager.TemplateLinkConfigForCustomSecretManager;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.beans.executioncapability.SelectorCapability;
import io.harness.encryption.Encrypted;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ng.core.dto.secrets.SSHKeySpecDTO;
import io.harness.secretmanagerclient.dto.SecretManagerConfigDTO;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.EncryptionType;
import io.harness.security.encryption.SecretManagerType;

import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@OwnedBy(PL)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"customSecretsManagerShellScript", "connectorRef"})
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants(innerTypeName = "CustomSecretsManagerConfigKeys")
public class CustomSecretNGManagerConfig extends SecretManagerConfig {
  Set<String> delegateSelectors;
  private boolean onDelegate;
  private static final String TASK_SELECTORS = "Task Selectors";
  @Encrypted(fieldName = "connectorRef")
  @Schema(description = SecretManagerDescriptionConstants.CUSTOM_AUTH_TOKEN)
  private String connectorRef;
  private SSHKeySpecDTO sshKeySpecDTO;
  private List<EncryptedDataDetail> sshKeyEncryptionDetails;
  private String host;
  private String workingDirectory;
  private TemplateLinkConfigForCustomSecretManager template;
  private String script;

  @Override
  public void maskSecrets() {
    // Nothing to mask
  }

  @Override
  public String getEncryptionServiceUrl() {
    return null;
  }

  @Override
  public String getValidationCriteria() {
    if (onDelegate) {
      return "localhost";
    } else {
      return host;
    }
  }

  @Override
  public SecretManagerType getType() {
    return CUSTOM;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public EncryptionType getEncryptionType() {
    return EncryptionType.CUSTOM_NG;
  }

  @Override
  public List<SecretManagerCapabilities> getSecretManagerCapabilities() {
    return Lists.newArrayList(CREATE_PARAMETERIZED_SECRET);
  }

  @Override
  public SecretManagerConfigDTO toDTO(boolean maskSecrets) {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    List<ExecutionCapability> executionCapabilities = new ArrayList<>();
    if (isNotEmpty(getDelegateSelectors())) {
      executionCapabilities.add(
          SelectorCapability.builder().selectors(getDelegateSelectors()).selectorOrigin(TASK_SELECTORS).build());
    }
    return executionCapabilities;
  }
}