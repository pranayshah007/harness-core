/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.persistence.HQuery.excludeAuthority;

import static java.util.Arrays.asList;

import io.harness.beans.EncryptedData;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.SettingAttribute;
import software.wings.dl.WingsPersistence;
import software.wings.security.EnvFilter;
import software.wings.security.EnvFilter.FilterType;
import software.wings.security.GenericEntityFilter;
import software.wings.security.UsageRestrictions;
import software.wings.security.UsageRestrictions.AppEnvRestriction;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * Migration script to transform usage restrictions with no restrictions to all app restrictions for setting attributes,
 * secrets
 * @author rktummala on 05/03/18
 */
@Slf4j
public class UsageRestrictionsMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private UsageRestrictionsService usageRestrictionsService;

  @Override
  public void migrate() {
    UsageRestrictions allAppEnvUsageRestrictions = getAllAppEnvUsageRestrictions();
    log.info("Start - Updating usage restrictions for setting attributes");
    Query<SettingAttribute> settingAttributeQuery =
        wingsPersistence.createQuery(SettingAttribute.class, excludeAuthority);
    try (HIterator<SettingAttribute> records = new HIterator<>(settingAttributeQuery.fetch())) {
      for (SettingAttribute settingAttribute : records) {
        try {
          settingAttribute = records.next();

          if (usageRestrictionsService.hasNoRestrictions(settingAttribute.getUsageRestrictions())) {
            settingAttribute.setUsageRestrictions(allAppEnvUsageRestrictions);
            final UpdateOperations<SettingAttribute> updateOperations =
                wingsPersistence.createUpdateOperations(SettingAttribute.class);
            updateOperations.set("usageRestrictions", allAppEnvUsageRestrictions);
            wingsPersistence.update(settingAttribute, updateOperations);
          }
        } catch (Exception ex) {
          log.warn("Error while updating setting attribute: {}",
              settingAttribute != null ? settingAttribute.getName() : "", ex);
        }
      }

      log.info("Done - Updating usage restrictions for setting attributes");
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception ex) {
      log.error("Failed - Updating usage restrictions for setting attributes", ex);
    }

    log.info("Start - Updating usage restrictions for secrets");
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class, excludeAuthority);
    query.field("type").in(asList(SettingVariableTypes.SECRET_TEXT, SettingVariableTypes.CONFIG_FILE));
    try (HIterator<EncryptedData> records = new HIterator<>(query.fetch())) {
      for (EncryptedData encryptedData : records) {
        try {
          encryptedData = records.next();

          if (usageRestrictionsService.hasNoRestrictions(encryptedData.getUsageRestrictions())) {
            encryptedData.setUsageRestrictions(allAppEnvUsageRestrictions);
            final UpdateOperations<EncryptedData> updateOperations =
                wingsPersistence.createUpdateOperations(EncryptedData.class);
            updateOperations.set("usageRestrictions", allAppEnvUsageRestrictions);
            wingsPersistence.update(encryptedData, updateOperations);
          }
        } catch (Exception ex) {
          log.warn("Error while updating secret: {}", encryptedData != null ? encryptedData.getName() : "", ex);
        }
      }

      log.info("Done - Updating usage restrictions for secrets");
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
    } catch (Exception ex) {
      log.error("Failed - Updating usage restrictions for secrets", ex);
    }
  }

  private UsageRestrictions getAllAppEnvUsageRestrictions() {
    Set<AppEnvRestriction> appEnvRestrictions = new HashSet<>(2);
    appEnvRestrictions.add(
        AppEnvRestriction.builder()
            .appFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
            .envFilter(EnvFilter.builder().filterTypes(Sets.newHashSet(FilterType.PROD)).build())
            .build());
    appEnvRestrictions.add(
        AppEnvRestriction.builder()
            .appFilter(GenericEntityFilter.builder().filterType(GenericEntityFilter.FilterType.ALL).build())
            .envFilter(EnvFilter.builder().filterTypes(Sets.newHashSet(FilterType.NON_PROD)).build())
            .build());
    return UsageRestrictions.builder().appEnvRestrictions(appEnvRestrictions).build();
  }
}
