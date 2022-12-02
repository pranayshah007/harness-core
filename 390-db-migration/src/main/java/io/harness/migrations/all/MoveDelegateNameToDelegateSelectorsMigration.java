/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.reflections.Reflections.log;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.KubernetesClusterConfig;
import software.wings.beans.KubernetesClusterConfig.KubernetesClusterConfigKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.AccountService;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;

public class MoveDelegateNameToDelegateSelectorsMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private AccountService accountService;

  private final String DEBUG_LINE = "MOVE_DELEGATE_NAME_TO_DELEGATE_SELECTORS_MIGRATION: ";

  @Override
  public void migrate() {
    log.info("Running MoveDelegateNameToDelegateSelectorsMigration");
    List<Account> allAccounts = accountService.getAccountsWithBasicInfo(false);
    for (Account account : allAccounts) {
      String accountId = account.getUuid();
      log.info(
          StringUtils.join(DEBUG_LINE, "Starting moving DelegateName to DelegateSelectors for accountId:", accountId));

      try (HIterator<SettingAttribute> settingAttributes =
               new HIterator<>(wingsPersistence.createQuery(SettingAttribute.class)
                                   .filter(SettingAttributeKeys.accountId, accountId)
                                   .fetch())) {
        while (settingAttributes.hasNext()) {
          SettingAttribute settingAttribute = settingAttributes.next();

          try {
            if (settingAttribute.getValue() instanceof KubernetesClusterConfig) {
              KubernetesClusterConfig clusterConfig = (KubernetesClusterConfig) settingAttribute.getValue();
              if (clusterConfig.isUseKubernetesDelegate() && isNotBlank(clusterConfig.getDelegateName())) {
                Set<String> delegateSelectors =
                    new HashSet<>(Collections.singletonList(clusterConfig.getDelegateName()));
                wingsPersistence.updateField(SettingAttribute.class, settingAttribute.getUuid(),
                    new StringBuilder()
                        .append(SettingAttributeKeys.value)
                        .append(".")
                        .append(KubernetesClusterConfigKeys.delegateSelectors)
                        .toString(),
                    delegateSelectors);
                log.info(StringUtils.join(DEBUG_LINE,
                    format("DelegateName to DelegateSelector migration done for settingAttribute for %s",
                        settingAttribute.getUuid())));
              } else {
                log.info(StringUtils.join(DEBUG_LINE,
                    format("isUseKubernetesDelegate field is false for settingAttribute %s, skipping",
                        settingAttribute.getUuid())));
              }
            } else {
              log.info(StringUtils.join(DEBUG_LINE,
                  format("setting value is not of type KubernetesClusterConfig for %s , skipping",
                      settingAttribute.getUuid())));
            }
          } catch (Exception ex) {
            log.error(StringUtils.join(DEBUG_LINE,
                format("Error  moving DelegateName to DelegateSelectors for settingAttribute %s ",
                    settingAttribute.getUuid()),
                ex));
          }
        }
      } catch (Exception ex) {
        log.error(StringUtils.join(
            DEBUG_LINE, format("Error  moving DelegateName to DelegateSelectors accountId %s", accountId), ex));
      }
      log.info(StringUtils.join(
          DEBUG_LINE, format("Successfully moved DelegateName to DelegateSelectors for accountId %s", accountId)));
    }
    log.info("Completed MoveDelegateNameToDelegateSelectorsMigration");
  }
}
