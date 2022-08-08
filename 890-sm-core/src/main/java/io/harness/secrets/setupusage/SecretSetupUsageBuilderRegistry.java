/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets.setupusage;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.secrets.setupusage.SecretSetupUsageBuilders.CONFIG_FILE_SETUP_USAGE_BUILDER;
import static io.harness.secrets.setupusage.SecretSetupUsageBuilders.SECRET_MANAGER_CONFIG_SETUP_USAGE_BUILDER;
import static io.harness.secrets.setupusage.SecretSetupUsageBuilders.SERVICE_VARIABLE_SETUP_USAGE_BUILDER;
import static io.harness.secrets.setupusage.SecretSetupUsageBuilders.SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER;
import static io.harness.secrets.setupusage.SecretSetupUsageBuilders.TRIGGER_SETUP_USAGE_BUILDER;

import io.harness.annotations.dev.OwnedBy;

import software.wings.settings.SettingVariableTypes;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Singleton;
import com.google.inject.name.Names;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

@OwnedBy(PL)
@Singleton
public class SecretSetupUsageBuilderRegistry {
  @Inject private Injector injector;
  private final Map<SettingVariableTypes, SecretSetupUsageBuilders> registeredSecretSetupUsageBuilders =
      new EnumMap<>(SettingVariableTypes.class);

  public SecretSetupUsageBuilderRegistry() {
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.AWS, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.AZURE, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.GCP, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.SCALYR, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.KUBERNETES, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(
        SettingVariableTypes.KUBERNETES_CLUSTER, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.RANCHER, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.PCF, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.SPOT_INST, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.SMTP, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.JENKINS, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.BAMBOO, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.SPLUNK, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.ELK, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.LOGZ, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.SUMO, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.APP_DYNAMICS, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.INSTANA, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.NEW_RELIC, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.DYNA_TRACE, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.BUG_SNAG, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.DATA_DOG, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.ELB, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.DOCKER, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.ECR, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.NEXUS, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.ARTIFACTORY, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.GIT, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.SMB, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.JIRA, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.SFTP, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.SERVICENOW, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(
        SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(
        SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.HTTP_HELM_REPO, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.OCI_HELM_REPO, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(
        SettingVariableTypes.AZURE_ARTIFACTS_PAT, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(
        SettingVariableTypes.APM_VERIFICATION, SETTING_ATTRIBUTE_SETUP_USAGE_BUILDER);

    registeredSecretSetupUsageBuilders.put(
        SettingVariableTypes.AWS_SECRETS_MANAGER, SECRET_MANAGER_CONFIG_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.AZURE_VAULT, SECRET_MANAGER_CONFIG_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.CYBERARK, SECRET_MANAGER_CONFIG_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.GCP_KMS, SECRET_MANAGER_CONFIG_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.KMS, SECRET_MANAGER_CONFIG_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.VAULT, SECRET_MANAGER_CONFIG_SETUP_USAGE_BUILDER);

    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.SERVICE_VARIABLE, SERVICE_VARIABLE_SETUP_USAGE_BUILDER);

    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.CONFIG_FILE, CONFIG_FILE_SETUP_USAGE_BUILDER);
    registeredSecretSetupUsageBuilders.put(SettingVariableTypes.TRIGGER, TRIGGER_SETUP_USAGE_BUILDER);
  }

  public Optional<SecretSetupUsageBuilder> getSecretSetupUsageBuilder(SettingVariableTypes type) {
    return Optional.ofNullable(registeredSecretSetupUsageBuilders.get(type))
        .flatMap(builder
            -> Optional.of(
                injector.getInstance(Key.get(SecretSetupUsageBuilder.class, Names.named(builder.getName())))));
  }
}
