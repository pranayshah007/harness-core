/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static software.wings.common.Constants.ACCOUNT_ID_KEY;

import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.EncryptedDataParent;
import io.harness.data.validator.EntityNameValidator;
import io.harness.encryption.EncryptionReflectUtils;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.GitConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.SettingAttribute.SettingCategory;
import software.wings.dl.WingsPersistence;
import software.wings.security.UsageRestrictions;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.yaml.gitSync.beans.YamlGitConfig;

import com.google.inject.Inject;
import java.lang.reflect.Field;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YamlGitConfigRefactoringMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  private String jsonUsageRestrictionString = "{\"appEnvRestrictions\":"
      + "[{\"appFilter\":{\"type\":\"GenericEntityFilter\",\"ids\":null,\"filterType\":\"ALL\"},"
      + "\"envFilter\":{\"type\":\"EnvFilter\",\"ids\":null,\"filterTypes\":[\"NON_PROD\"]}},"
      + "{\"appFilter\":{\"type\":\"GenericEntityFilter\",\"ids\":null,\"filterType\":\"ALL\"},"
      + "\"envFilter\":{\"type\":\"EnvFilter\",\"ids\":null,\"filterTypes\":[\"PROD\"]}}]}";

  @Override
  public void migrate() {
    log.info("Retrieving all YamlGitConfigs");
    UsageRestrictions defaultUsageRestrictions =
        usageRestrictionsService.getUsageRestrictionsFromJson(jsonUsageRestrictionString);

    try (HIterator<YamlGitConfig> yamlGitConfigHIterator =
             new HIterator<>(wingsPersistence.createQuery(YamlGitConfig.class).fetch())) {
      for (YamlGitConfig yamlGitConfig : yamlGitConfigHIterator) {
        EncryptedData encryptedData = null;
        if (yamlGitConfig.getEncryptedPassword() != null) {
          encryptedData = wingsPersistence.createQuery(EncryptedData.class)
                              .filter(EncryptedDataKeys.accountId, yamlGitConfig.getAccountId())
                              .filter("_id", yamlGitConfig.getEncryptedPassword())
                              .get();
        }

        Account account = accountService.get(yamlGitConfig.getAccountId());

        String settingAttributeForGitName = account.getAccountName() + "_default_Git_Connector_For_Yaml";
        SettingAttribute savedSettingAttribute = wingsPersistence.createQuery(SettingAttribute.class)
                                                     .filter(ACCOUNT_ID_KEY, yamlGitConfig.getAccountId())
                                                     .filter(SettingAttributeKeys.name, settingAttributeForGitName)
                                                     .filter(SettingAttributeKeys.category, SettingCategory.CONNECTOR)
                                                     .get();

        if (savedSettingAttribute == null) {
          log.info("Creating GitConnector for yaml from YamlGitConfig {}", yamlGitConfig.getUuid());
          GitConfig gitConfig = getGitConfig(yamlGitConfig, encryptedData);

          SettingAttribute settingAttributeForGit =
              SettingAttribute.Builder.aSettingAttribute()
                  .withAccountId(yamlGitConfig.getAccountId())
                  .withValue(gitConfig)
                  .withCategory(SettingCategory.CONNECTOR)
                  .withName(EntityNameValidator.getMappedString(settingAttributeForGitName))
                  .withUsageRestrictions(defaultUsageRestrictions)
                  .build();
          wingsPersistence.save(settingAttributeForGit);

          log.info("Created GitConnector from YamlGitConfig {}", yamlGitConfig.getUuid());
          log.info("Updating YamlGitConfig with newly created GitConnector");

          if (encryptedData != null) {
            List<Field> encryptedFields = EncryptionReflectUtils.getEncryptedFields(gitConfig.getClass());
            String fieldKey = EncryptionReflectUtils.getEncryptedFieldTag(encryptedFields.get(0));
            EncryptedDataParent encryptedDataParent =
                new EncryptedDataParent(settingAttributeForGit.getUuid(), gitConfig.getSettingType(), fieldKey);
            encryptedData.addParent(encryptedDataParent);
            wingsPersistence.save(encryptedData);
          }

          /* Not calling yamlGitServiceImpl.save() as it will initiate validate call and gitFullSync.
           * We currently do not have yaml representation of gitConnector, so it wont have any need to perform gitSync.
           * */
          wingsPersistence.updateField(
              YamlGitConfig.class, yamlGitConfig.getUuid(), "gitConnectorId", settingAttributeForGit.getUuid());
          log.info("Updated YamlGitConfig with newly created GitConnector {}", settingAttributeForGit.getUuid());
        }
      }
    }
  }

  public GitConfig getGitConfig(YamlGitConfig yamlGitConfig, EncryptedData encryptedData) {
    GitConfig gitConfig = GitConfig.builder()
                              .accountId(yamlGitConfig.getAccountId())
                              .repoUrl(yamlGitConfig.getUrl())
                              .username(yamlGitConfig.getUsername())
                              .sshSettingId(yamlGitConfig.getSshSettingId())
                              .keyAuth(yamlGitConfig.isKeyAuth())
                              .branch(yamlGitConfig.getBranchName().trim())
                              .webhookToken(yamlGitConfig.getWebhookToken())
                              .description("Default Yaml git ConnectorDisconnectHandler for account")
                              .build();

    if (encryptedData != null) {
      String encryptedPassword = new StringBuilder()
                                     .append(encryptedData.getEncryptionType().getYamlName())
                                     .append(":")
                                     .append(yamlGitConfig.getEncryptedPassword())
                                     .toString();
      gitConfig.setEncryptedPassword(encryptedPassword);
    }

    return gitConfig;
  }
}
