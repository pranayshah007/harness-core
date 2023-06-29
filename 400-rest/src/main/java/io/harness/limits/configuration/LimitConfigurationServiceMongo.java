/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.limits.configuration;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.ConfiguredLimit.ConfiguredLimitKeys;
import io.harness.limits.defaults.service.DefaultLimitsService;
import io.harness.limits.impl.model.RateLimit;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.limits.lib.Limit;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.beans.Account;
import software.wings.beans.AccountType;
import software.wings.dl.WingsPersistence;
import software.wings.exception.AccountNotFoundException;
import software.wings.service.intfc.AccountService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.Datastore;
import dev.morphia.UpdateOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@Singleton
@ParametersAreNonnullByDefault
@Slf4j
public class LimitConfigurationServiceMongo implements LimitConfigurationService {
  @Inject private WingsPersistence dao;
  @Inject private AccountService accountService;
  @Inject private DefaultLimitsService defaultLimits;

  @Override
  @Nullable
  public ConfiguredLimit getOrDefault(String accountId, ActionType actionType) {
    ConfiguredLimit limit = get(accountId, actionType);
    if (null == limit) {
      limit = getDefaultLimit(actionType, accountId);
    }
    return limit;
  }

  @Override
  @Nullable
  public ConfiguredLimit getOrDefaultToGlobal(String accountId, String globalAccountId, ActionType actionType) {
    ConfiguredLimit limit = get(accountId, actionType);
    if (null == limit) {
      limit = getOrDefault(globalAccountId, actionType);
    }
    return limit;
  }

  @Override
  @VisibleForTesting
  @Nullable
  public ConfiguredLimit get(String accountId, ActionType actionType) {
    return dao.createQuery(ConfiguredLimit.class)
        .filter(ConfiguredLimitKeys.accountId, accountId)
        .filter(ConfiguredLimitKeys.key, actionType.toString())
        .get();
  }

  @Override
  public List<List<ConfiguredLimit>> getAllLimitsConfiguredForAccounts(List<String> accountIds) {
    List<ConfiguredLimit> configuredLimitList =
        dao.createQuery(ConfiguredLimit.class).field(ConfiguredLimitKeys.accountId).hasAnyOf(accountIds).asList();
    Map<String, List<ConfiguredLimit>> limitsPerAccount = new HashMap<>();

    accountIds.forEach(accountId -> { limitsPerAccount.put(accountId, new ArrayList<>()); });

    configuredLimitList.forEach(
        configuredLimit -> limitsPerAccount.get(configuredLimit.getAccountId()).add(configuredLimit));

    return accountIds.stream().map(limitsPerAccount::get).collect(Collectors.toList());
  }

  @Override
  public List<ConfiguredLimit> getLimitsConfiguredForAccount(String accountId) {
    List<List<ConfiguredLimit>> limitsforAccounts = getAllLimitsConfiguredForAccounts(Lists.newArrayList(accountId));
    if (!limitsforAccounts.isEmpty()) {
      return limitsforAccounts.get(0);
    }
    return Lists.newArrayList();
  }

  @Nullable
  private ConfiguredLimit getDefaultLimit(ActionType actionType, String accountId) {
    String accountType = AccountType.PAID;
    try {
      Account account = accountService.get(accountId);
      if (account.getLicenseInfo() != null) {
        accountType = account.getLicenseInfo().getAccountType();
      }
    } catch (AccountNotFoundException exception) {
      log.warn("Account {} does not exist", accountId, exception);
    }

    Limit limit = defaultLimits.get(actionType, accountType.toUpperCase());
    if (limit != null) {
      return new ConfiguredLimit<>(accountId, limit, actionType);
    }

    log.error(
        "Default limit is null. Action Type: {}, Account Type: {}. Please configure the same in DefaultLimitsImpl class",
        actionType, accountType);
    return null;
  }

  @Override
  public boolean configure(String accountId, ActionType actionType, Limit limit) {
    if (StringUtils.isEmpty(accountId)) {
      throw new InvalidRequestException("Account ID is empty", WingsException.USER);
    }

    ConfiguredLimit configuredLimit;

    switch (limit.getLimitType()) {
      case STATIC:
        configuredLimit = new ConfiguredLimit<>(accountId, (StaticLimit) limit, actionType);
        break;
      case RATE_LIMIT:
        RateLimit rateLimit = (RateLimit) limit;
        Objects.requireNonNull(rateLimit.getDurationUnit(), "durationUnit can't be null for a rate limit");
        configuredLimit = new ConfiguredLimit<>(accountId, rateLimit, actionType);
        break;
      default:
        throw new IllegalArgumentException("Unknown limit type: " + limit.getLimitType());
    }

    Datastore ds = dao.getDatastore(ConfiguredLimit.class);

    UpdateOperations<ConfiguredLimit> updateOp = ds.createUpdateOperations(ConfiguredLimit.class)
                                                     .set("accountId", configuredLimit.getAccountId())
                                                     .set("key", configuredLimit.getKey())
                                                     .set("limit", configuredLimit.getLimit());

    Query<ConfiguredLimit> query = ds.createQuery(ConfiguredLimit.class)
                                       .field("accountId")
                                       .equal(configuredLimit.getAccountId())
                                       .field("key")
                                       .equal(configuredLimit.getKey());

    UpdateOptions options = new UpdateOptions();
    options.upsert(true);

    UpdateResults results = ds.update(query, updateOp, options);
    if (results.getUpdatedCount() < 1 && results.getInsertedCount() < 1) {
      log.error(
          "Both inserted and updated count are less than 1. UpdateCount: {}, InsertCount: {}, Account ID: {}, actionType: {}, Limit: {}",
          results.getUpdatedCount(), results.getInsertedCount(), accountId, actionType, limit);
      return false;
    }

    log.info("Set the new limit for account: {}, action type: {}, limit: {}", accountId, actionType.name(),
        limit.getLimitType().name());
    return true;
  }

  @Override
  public void deleteByAccountId(String accountId) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      dao.delete(dao.createQuery(ConfiguredLimit.class).filter(ConfiguredLimitKeys.accountId, accountId));
      log.info("deleted limits for account {}", accountId);
    }
  }
}
