/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.event.handler.impl.account;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.event.model.EventConstants.EMAIL;
import static io.harness.event.model.EventConstants.FIRST_NAME;
import static io.harness.event.model.EventConstants.LAST_NAME;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.SecretManagerConfig;
import io.harness.ccm.license.CeLicenseInfo;
import io.harness.event.handler.EventHandler;
import io.harness.event.handler.impl.Utils;
import io.harness.event.handler.impl.segment.SalesforceApiCheck;
import io.harness.event.handler.impl.segment.SegmentHandler;
import io.harness.event.handler.impl.segment.SegmentHelper;
import io.harness.event.listener.EventListener;
import io.harness.event.model.Event;
import io.harness.event.model.EventData;
import io.harness.event.model.EventType;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;
import io.harness.secretmanagers.SecretManagerConfigService;

import software.wings.app.MainConfiguration;
import software.wings.beans.Account;
import software.wings.beans.LicenseInfo;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.User;
import software.wings.beans.account.AccountStatus;
import software.wings.beans.instance.dashboard.InstanceStatsUtils;
import software.wings.service.impl.AuthServiceImpl.Keys;
import software.wings.service.impl.event.AccountEntityEvent;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.UserService;
import software.wings.service.intfc.instance.stats.InstanceStatService;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import com.segment.analytics.messages.GroupMessage;
import com.segment.analytics.messages.IdentifyMessage;
import dev.morphia.query.CountOptions;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(PL)
@NoArgsConstructor
@Singleton
@Slf4j
public class AccountChangeHandler implements EventHandler {
  @Inject private SegmentHelper segmentHelper;
  @Inject private InstanceStatService instanceStatService;
  @Inject private MainConfiguration mainConfiguration;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private UserService userService;
  @Inject private HPersistence hPersistence;
  @Inject private AccountService accountService;
  @Inject private SalesforceApiCheck salesforceApiCheck;

  @Inject private FeatureFlagService featureFlagService;
  @Inject private Utils utils;

  private Map<String, Boolean> integrations = new HashMap<>();
  private static final String NAME = "name";

  public AccountChangeHandler(EventListener eventListener) {
    eventListener.registerEventHandler(this, Sets.newHashSet(EventType.ACCOUNT_ENTITY_CHANGE));
  }

  @Override
  public void handleEvent(final Event event) {
    EventData eventData = event.getEventData();
    if (null == eventData) {
      log.error("Unexpected event with null eventData. Type: {}", event.getEventType());
      return;
    }
    AccountEntityEvent accountEntityEvent = (AccountEntityEvent) eventData.getEventInfo();
    if (null == accountEntityEvent || null == accountEntityEvent.getAccount()) {
      log.error("Unexpected event with null account entity event. Type: {}", event.getEventType());
      return;
    }

    Account account = accountEntityEvent.getAccount();
    if (StringUtils.isEmpty(account.getUuid())) {
      log.error("No accountId present in account entity event. account={} eventType={}", account, event.getEventType());
      return;
    }
    if (mainConfiguration.getSegmentConfig().isEnabled()) {
      publishAccountEventToSegment(account);
    } else {
      log.info("Segment is disabled. No events will be sent");
    }
  }

  public void publishAccountEventToSegment(Account account) {
    enqueueIdentity(account.getUuid(), account.getAccountName());
    enqueueGroup(account);
  }

  private void enqueueIdentity(String accountId, String accountName) {
    DummySystemUser user = new DummySystemUser(accountId, accountName);

    Builder<String, Object> identityTraits = ImmutableMap.builder();

    integrations.put(SegmentHandler.Keys.NATERO, true);
    integrations.put(SegmentHandler.Keys.SALESFORCE, false);

    String firstName = utils.getFirstName(user.getUserName(), user.getEmail());
    String lastName = utils.getLastName(user.getUserName(), user.getEmail());

    IdentifyMessage.Builder identity =
        IdentifyMessage.builder()
            .userId(user.getId())
            .traits(identityTraits.put(NAME, user.getUserName())
                        .put(EMAIL, user.getEmail())
                        .put(FIRST_NAME, isNotEmpty(firstName) ? firstName : user.getEmail())
                        .put(LAST_NAME, isNotEmpty(lastName) ? lastName : "")
                        .build());

    integrations.forEach((k, v) -> {
      boolean value = false;
      if (v != null) {
        value = v.booleanValue();
      }
      identity.enableIntegration(k, value);
    });

    segmentHelper.enqueue(identity);
  }

  private long getCountOfServicesInAccount(Account account) {
    return hPersistence.createQuery(Service.class)
        .filter(ServiceKeys.accountId, account.getUuid())
        .count(createCountOptionsToHitSecondaryNode(account.getUuid()));
  }

  private void enqueueGroup(Account account) {
    if (!accountService.getAccountStatus(account.getUuid()).equals(AccountStatus.ACTIVE)) {
      return;
    }
    String accountId = account.getUuid();
    DummySystemUser user = new DummySystemUser(accountId, account.getAccountName());

    double usage = InstanceStatsUtils.actualUsage(accountId, instanceStatService);
    double currentUsage = InstanceStatsUtils.currentActualUsage(accountId, instanceStatService);
    String env = System.getenv("ENV");
    LicenseInfo licenseInfo = account.getLicenseInfo();
    String accountType = licenseInfo != null ? licenseInfo.getAccountType() : null;
    SecretManagerConfig defaultSecretManager = secretManagerConfigService.getDefaultSecretManager(accountId);
    String name = account.getAccountName();

    boolean isGlobal = false;
    if (defaultSecretManager != null) {
      isGlobal = Objects.equals(GLOBAL_ACCOUNT_ID, defaultSecretManager.getAccountId());
    }

    List<User> users = userService.getUsersOfAccount(accountId);
    long count = getUserCount(users);

    boolean isPresentInSalesforce = false;
    try {
      isPresentInSalesforce =
          salesforceApiCheck.isSalesForceIntegrationEnabled() && salesforceApiCheck.isPresentInSalesforce(account);
    } catch (Exception e) {
      log.error("Exception while querying Salesforce for account {} ", account.getUuid(), e);
    }

    if (isPresentInSalesforce) {
      name = salesforceApiCheck.getSalesforceAccountName();
    }

    ImmutableMap.Builder<String, Object> groupTraitsMapBuilder =
        ImmutableMap.<String, Object>builder()
            .put("name", name)
            .put("company_name", account.getCompanyName())
            .put("account_type", isEmpty(accountType) ? "" : accountType)
            .put("cluster", isEmpty(env) ? "" : env)
            .put("usage_service_instances_30d", usage)
            .put("security_secrets_manager_default",
                defaultSecretManager != null ? defaultSecretManager.getEncryptionType().name() : "LOCAL")
            .put("is_global", isGlobal)
            .put("user_count", count)
            .put("service_count", getCountOfServicesInAccount(account))
            .put("usage_service_instances_current", currentUsage);
    CeLicenseInfo ceLicenseInfo =
        Optional.ofNullable(account.getCeLicenseInfo()).orElse(CeLicenseInfo.builder().build());
    if (ceLicenseInfo.getLicenseType() != null) {
      groupTraitsMapBuilder.put("ce_license_type", ceLicenseInfo.getLicenseType().name());
    }
    groupTraitsMapBuilder.put("ce_license_expiry", ceLicenseInfo.getExpiryTime());
    log.info("Enqueuing group event. accountId={} traits={}", accountId, groupTraitsMapBuilder);

    GroupMessage.Builder groupMessageBuilder = GroupMessage.builder(accountId)
                                                   .userId(user.getId())
                                                   .traits(groupTraitsMapBuilder.build())
                                                   .enableIntegration(SegmentHandler.Keys.NATERO, true);

    segmentHelper.enqueue(groupMessageBuilder.enableIntegration(SegmentHandler.Keys.SALESFORCE, isPresentInSalesforce));
    log.info("Group call sent to Salesforce is={} for accountId={}", isPresentInSalesforce, account.getUuid());
  }

  private static long getUserCount(List<User> users) {
    if (isEmpty(users)) {
      return 0;
    }

    return users.stream()
        .filter(userObj -> {
          if (userObj.getDisabled()) {
            return false;
          }
          if (userObj.getEmail() != null) {
            return !userObj.getEmail().endsWith(Keys.HARNESS_EMAIL);
          }
          return true;
        })
        .count();
  }

  private CountOptions createCountOptionsToHitSecondaryNode(String accountId) {
    if (accountId != null && featureFlagService.isEnabled(FeatureName.CDS_QUERY_OPTIMIZATION, accountId)) {
      return new CountOptions().readPreference(ReadPreference.secondaryPreferred());
    }
    return new CountOptions();
  }
}