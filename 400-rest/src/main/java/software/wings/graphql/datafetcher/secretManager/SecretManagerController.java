/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.secretManager;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SecretManagerConfig;

import io.harness.security.encryption.EncryptedDataParams;
import io.harness.security.encryption.SecretManagerType;
import software.wings.graphql.datafetcher.secrets.UsageScopeController;
import software.wings.graphql.schema.mutation.secretManager.QLEncryptedDataParams;
import software.wings.graphql.schema.type.secretManagers.QLCustomSecretManagerConfig;
import software.wings.graphql.schema.type.secretManagers.QLSecretManager;
import software.wings.graphql.schema.type.secretManagers.QLSecretManager.QLSecretManagerBuilder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;

import java.util.HashSet;
import java.util.Set;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

@Singleton
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class SecretManagerController {
  @Inject UsageScopeController usageScopeController;
  public QLSecretManagerBuilder populateSecretManager(
      SecretManagerConfig secretManager, QLSecretManagerBuilder builder) {
    return builder.id(secretManager.getUuid())
        .name(secretManager.getName())
        .usageScope(usageScopeController.populateUsageScope(secretManager.getUsageRestrictions()));
  }
  public void populateCustomSecretManagerDetails(
          SecretManagerConfig secretManager, QLSecretManagerBuilder builder) {
    CustomSecretsManagerConfig customSecretsManagerConfig = (CustomSecretsManagerConfig) secretManager;
    QLCustomSecretManagerConfig build = QLCustomSecretManagerConfig.builder()
            .templateId(customSecretsManagerConfig.getTemplateId())
            .executeOnDelegate(customSecretsManagerConfig.isExecuteOnDelegate())
            .isConnectorTemplatized(customSecretsManagerConfig.isConnectorTemplatized())
            .host(customSecretsManagerConfig.getHost())
            .commandPath(customSecretsManagerConfig.getCommandPath())
            .connectorId(customSecretsManagerConfig.getConnectorId())
            .isDefault(customSecretsManagerConfig.isDefault())
            .testVariables(obtainQLTestVariables(customSecretsManagerConfig.getTestVariables()))
            .delegateSelectors(customSecretsManagerConfig.getDelegateSelectors())
            .build();
    builder.config(build);
  }

  private Set<QLEncryptedDataParams> obtainQLTestVariables(Set<EncryptedDataParams> testVariables) {
    Set<QLEncryptedDataParams> encryptedDataParams = new HashSet<>();
    if (isNotEmpty(testVariables)) {
      for (EncryptedDataParams testVariable : testVariables) {
        encryptedDataParams.add(
                QLEncryptedDataParams.builder().name(testVariable.getName()).value(testVariable.getValue()).build());
      }
    }
    return encryptedDataParams;
  }

  public QLSecretManager convertToQLSecretManager(SecretManagerConfig secretManager) {
    QLSecretManagerBuilder builder = QLSecretManager.builder();
    if (secretManager.getType() == SecretManagerType.CUSTOM) {
      populateCustomSecretManagerDetails(secretManager,builder);
    }
    populateSecretManager(secretManager, builder);
    return builder.build();
  }

}
