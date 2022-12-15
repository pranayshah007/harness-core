/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeValidate;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.reflections.Reflections.log;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.api.CloudProviderType;
import software.wings.beans.Account;
import software.wings.beans.GcpConfig;
import software.wings.beans.GcpConfig.GcpConfigKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;
import software.wings.settings.SettingValue.SettingValueKeys;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

public class GcpConfigMultipleDelegateMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;

  private final String DEBUG_LINE = "GCP_CONFIG_MULTIPLE_DELEGATE_MIGRATION: ";

  @Override
  public void migrate() {
    log.info("Running GcpConfigMultipleDelegateMigration");
    List<Account> allAccounts = accountService.getAccountsWithBasicInfo(false);

    for (Account account : allAccounts) {
      String accountId = account.getUuid();
      log.info(StringUtils.join(DEBUG_LINE, "Starting moving variable names for accountId:", accountId));

      try (HIterator<SettingAttribute> settingAttributes = new HIterator<>(
               wingsPersistence.createQuery(SettingAttribute.class, excludeValidate)
                   .filter(SettingAttributeKeys.accountId, accountId)
                   .filter(SettingAttributeKeys.value + "." + SettingValueKeys.type, CloudProviderType.GCP.name())
                   .fetch())) {
        while (settingAttributes.hasNext()) {
          SettingAttribute settingAttribute = settingAttributes.next();

          try {
            if (settingAttribute.getValue() instanceof GcpConfig) {
              GcpConfig clusterConfig = (GcpConfig) settingAttribute.getValue();
              boolean useDelegateSelectors = clusterConfig.isUseDelegate();
              wingsPersistence.updateField(SettingAttribute.class, settingAttribute.getUuid(),
                  new StringBuilder()
                      .append(SettingAttributeKeys.value)
                      .append(".")
                      .append(GcpConfigKeys.useDelegateSelectors)
                      .toString(),
                  useDelegateSelectors);
              log.info(StringUtils.join(DEBUG_LINE,
                  format("useDelegate to useDelegateSelectors migration done for settingAttribute for %s",
                      settingAttribute.getUuid())));
              if (clusterConfig.isUseDelegate() && isNotBlank(clusterConfig.getDelegateSelector())) {
                List<String> delegateSelectors = Collections.singletonList(clusterConfig.getDelegateSelector());
                wingsPersistence.updateField(SettingAttribute.class, settingAttribute.getUuid(),
                    new StringBuilder()
                        .append(SettingAttributeKeys.value)
                        .append(".")
                        .append(GcpConfigKeys.delegateSelectors)
                        .toString(),
                    delegateSelectors);
                log.info(StringUtils.join(DEBUG_LINE,
                    format("DelegateSelector to DelegateSelectors migration done for settingAttribute for %s",
                        settingAttribute.getUuid())));
              } else {
                log.info(StringUtils.join(DEBUG_LINE,
                    format(
                        "isUseDelegate and isUseDelegateSelectors fields are false for settingAttribute %s, skipping",
                        settingAttribute.getUuid())));
              }
            } else {
              log.info(StringUtils.join(DEBUG_LINE,
                  format("setting value is not of type GcpConfig for %s , skipping", settingAttribute.getUuid())));
            }
          } catch (Exception ex) {
            log.error(StringUtils.join(DEBUG_LINE,
                format("Error  moving variable names for settingAttribute %s ", settingAttribute.getUuid()), ex));
          }
        }
      } catch (Exception ex) {
        log.error(StringUtils.join(DEBUG_LINE, format("Error  moving variable names for accountId %s", accountId), ex));
      }
      log.info(StringUtils.join(DEBUG_LINE, format("Successfully moved variable names for accountId %s", accountId)));
    }
    log.info("Completed GcpConfigMultipleDelegateMigration");
  }
}
