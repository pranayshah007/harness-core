/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
import static io.harness.beans.FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.NotificationRule.NotificationRuleBuilder.aNotificationRule;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.SSO_PROVIDER_NOT_REACHABLE_NOTIFICATION;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest.PageRequestBuilder;
import io.harness.beans.SearchFilter.Operator;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.iterator.PersistentCronIterable;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.OauthProviderType;
import io.harness.ng.core.dto.UserGroupDTO;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HIterator;
import io.harness.remote.client.NGRestUtils;
import io.harness.scheduler.PersistentScheduler;
import io.harness.usergroups.UserGroupClient;

import software.wings.beans.Account;
import software.wings.beans.Event;
import software.wings.beans.InformationNotification;
import software.wings.beans.NotificationGroup;
import software.wings.beans.NotificationRule;
import software.wings.beans.alert.Alert;
import software.wings.beans.alert.AlertType;
import software.wings.beans.alert.SSOSyncFailedAlert;
import software.wings.beans.loginSettings.events.LdapSettingsYamlDTO;
import software.wings.beans.loginSettings.events.LoginSettingsLDAPCreateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsLDAPDeleteEvent;
import software.wings.beans.loginSettings.events.LoginSettingsLDAPUpdateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsOAuthCreateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsOAuthDeleteEvent;
import software.wings.beans.loginSettings.events.LoginSettingsOAuthUpdateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsSAMLCreateEvent;
import software.wings.beans.loginSettings.events.LoginSettingsSAMLDeleteEvent;
import software.wings.beans.loginSettings.events.LoginSettingsSAMLUpdateEvent;
import software.wings.beans.loginSettings.events.OAuthSettingsYamlDTO;
import software.wings.beans.loginSettings.events.SamlSettingsYamlDTO;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOSettings.SSOSettingsKeys;
import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlSettings;
import software.wings.dl.WingsPersistence;
import software.wings.features.LdapFeature;
import software.wings.features.SamlFeature;
import software.wings.features.api.GetAccountId;
import software.wings.features.api.RestrictedApi;
import software.wings.features.extractors.LdapSettingsAccountIdExtractor;
import software.wings.features.extractors.SamlSettingsAccountIdExtractor;
import software.wings.scheduler.LdapGroupScheduledHandler;
import software.wings.scheduler.LdapGroupSyncJobHelper;
import software.wings.scheduler.LdapSyncJobConfig;
import software.wings.security.authentication.oauth.OauthOptions;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.security.SecretManager;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;

@ValidateOnExecution
@Singleton
@Slf4j
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
@OwnedBy(HarnessTeam.PL)
public class SSOSettingServiceImpl implements SSOSettingService {
  private static final long MIN_INTERVAL = 900;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private SecretManager secretManager;
  @Inject private UserGroupService userGroupService;
  @Inject private AlertService alertService;
  @Inject private NotificationSetupService notificationSetupService;
  @Inject private NotificationService notificationService;
  @Inject private DelegateService delegateService;
  @Inject private AccountService accountService;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private OauthOptions oauthOptions;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private LdapGroupSyncJobHelper ldapGroupSyncJobHelper;
  @Inject private LdapGroupScheduledHandler ldapGroupScheduledHandler;
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;
  @Inject private LdapSyncJobConfig ldapSyncJobConfig;
  @Inject private SSOServiceHelper ssoServiceHelper;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private OutboxService outboxService;
  @Inject @Named("PRIVILEGED") private UserGroupClient userGroupClient;
  static final int ONE_DAY = 86400000;

  @Override
  public SamlSettings getSamlSettingsByIdpUrl(String idpUrl) {
    return wingsPersistence.createQuery(SamlSettings.class).field("url").equal(idpUrl).get();
  }

  @Override
  public SamlSettings getSamlSettingsByAccountId(String accountId) {
    return wingsPersistence.createQuery(SamlSettings.class)
        .field(SamlSettings.ACCOUNT_ID_KEY2)
        .equal(accountId)
        .field("type")
        .equal(SSOType.SAML)
        .get();
  }

  @Override
  public SamlSettings getSamlSettingsByAccountIdNotConfiguredFromNG(String accountId) {
    return wingsPersistence.createQuery(SamlSettings.class)
        .field(SamlSettings.ACCOUNT_ID_KEY2)
        .equal(accountId)
        .field("type")
        .equal(SSOType.SAML)
        .field("configuredFromNG")
        .equal(false)
        .get();
  }

  @Override
  public List<SamlSettings> getSamlSettingsListByAccountId(String accountId) {
    return wingsPersistence.createQuery(SamlSettings.class)
        .field(SamlSettings.ACCOUNT_ID_KEY2)
        .equal(accountId)
        .field("type")
        .equal(SSOType.SAML)
        .asList();
  }

  @Override
  public SamlSettings getSamlSettingsByAccountIdAndUuid(String accountId, String uuid) {
    return wingsPersistence.createQuery(SamlSettings.class)
        .field(SamlSettings.ACCOUNT_ID_KEY2)
        .equal(accountId)
        .field(NGCommonEntityConstants.UUID)
        .equal(uuid)
        .field(NGCommonEntityConstants.TYPE_KEY)
        .equal(SSOType.SAML)
        .get();
  }

  @Override
  public OauthSettings getOauthSettingsByAccountId(String accountId) {
    return wingsPersistence.createQuery(OauthSettings.class)
        .field("accountId")
        .equal(accountId)
        .field("type")
        .equal(SSOType.OAUTH)
        .get();
  }

  @Override
  @RestrictedApi(SamlFeature.class)
  public SamlSettings saveSamlSettings(@GetAccountId(SamlSettingsAccountIdExtractor.class) SamlSettings settings) {
    return saveSSOSettingsInternal(settings);
  }

  @Override
  @RestrictedApi(SamlFeature.class)
  public SamlSettings saveSamlSettings(@GetAccountId(SamlSettingsAccountIdExtractor.class) SamlSettings settings,
      boolean isUpdateCase, boolean isNGSso) {
    return saveSSOSettingsInternal(settings, isUpdateCase, isNGSso);
  }

  // This function is meant to be called for ng sso settings, as the license check is already done in NG Service
  @Override
  public SamlSettings saveSamlSettingsWithoutCGLicenseCheck(
      @GetAccountId(SamlSettingsAccountIdExtractor.class) SamlSettings settings) {
    return saveSSOSettingsInternal(settings);
  }

  @Override
  public SamlSettings saveSamlSettingsWithoutCGLicenseCheck(
      @GetAccountId(SamlSettingsAccountIdExtractor.class) SamlSettings settings, boolean isUpdateCase,
      boolean isNGSso) {
    return saveSSOSettingsInternal(settings, isUpdateCase, isNGSso);
  }

  private SamlSettings saveSSOSettingsInternal(
      @GetAccountId(SamlSettingsAccountIdExtractor.class) SamlSettings settings) {
    SamlSettings queriedSettings = getSamlSettingsByAccountId(settings.getAccountId());
    SamlSettings savedSettings;
    if (queriedSettings != null) {
      setSamlSettingValuesInternal(queriedSettings, settings);
      SamlSettings currentSamlSettings = getSamlSettingsByAccountId(settings.getAccountId());
      savedSettings = saveAndAuditSAMLSettingUpdateInternal(currentSamlSettings, queriedSettings);
    } else {
      savedSettings = savePublishEventAndAuditSAMLSettingUploadInternal(settings);
    }
    auditServiceHelper.reportForAuditingUsingAccountId(settings.getAccountId(), null, settings, Event.Type.CREATE);
    log.info("Auditing creation of SAML Settings for account={}", settings.getAccountId());

    return savedSettings;
  }

  private SamlSettings saveSSOSettingsInternal(@GetAccountId(SamlSettingsAccountIdExtractor.class)
                                               SamlSettings settings, boolean isUpdateCase, boolean isNGSsoSetting) {
    SamlSettings savedSettings = null;
    SamlSettings currentSamlSettings;
    if (!isUpdateCase) {
      settings.setConfiguredFromNG(isNGSsoSetting);
      if (isNGSsoSetting && isEmpty(settings.getFriendlySamlName())) {
        settings.setFriendlySamlName(settings.getDisplayName());
      }
      savedSettings = savePublishEventAndAuditSAMLSettingUploadInternal(settings);
      if (!isNGSsoSetting) {
        log.info("CG_SAML_AUDITS: Auditing creation of SAML Settings for account={}", settings.getAccountId());
        auditServiceHelper.reportForAuditingUsingAccountId(settings.getAccountId(), null, settings, Event.Type.CREATE);
      }
    } else {
      SamlSettings queriedSettings = getSamlSettingsByAccountIdAndUuid(settings.getAccountId(), settings.getUuid());
      if (queriedSettings != null) {
        setSamlSettingValuesInternal(queriedSettings, settings);
        if (isNotEmpty(settings.getFriendlySamlName())) {
          queriedSettings.setFriendlySamlName(settings.getFriendlySamlName());
        }
        currentSamlSettings = getSamlSettingsByAccountIdAndUuid(settings.getAccountId(), settings.getUuid());
        savedSettings = saveAndAuditSAMLSettingUpdateInternal(currentSamlSettings, queriedSettings);
        if (!isNGSsoSetting) {
          log.info("CG_SAML_AUDITS: Auditing updation of SAML Settings for account={} and saml setting id={}",
              settings.getAccountId(), settings.getUuid());
          auditServiceHelper.reportForAuditingUsingAccountId(
              settings.getAccountId(), currentSamlSettings, savedSettings, Event.Type.UPDATE);
        }
      }
    }
    return savedSettings;
  }

  private void setSamlSettingValuesInternal(SamlSettings queriedSettings, SamlSettings settings) {
    queriedSettings.setUrl(settings.getUrl());
    queriedSettings.setMetaDataFile(settings.getMetaDataFile());
    queriedSettings.setDisplayName(settings.getDisplayName());
    queriedSettings.setOrigin(settings.getOrigin());
    queriedSettings.setGroupMembershipAttr(settings.getGroupMembershipAttr());
    queriedSettings.setLogoutUrl(settings.getLogoutUrl());
    queriedSettings.setEntityIdentifier(settings.getEntityIdentifier());
    queriedSettings.setSamlProviderType(settings.getSamlProviderType());
    queriedSettings.setClientId(settings.getClientId());
    queriedSettings.setEncryptedClientSecret(settings.getEncryptedClientSecret());
    queriedSettings.setJitEnabled(settings.isJitEnabled());
    queriedSettings.setJitValidationKey(settings.getJitValidationKey());
    queriedSettings.setJitValidationValue(settings.getJitValidationValue());
  }

  private SamlSettings savePublishEventAndAuditSAMLSettingUploadInternal(SamlSettings settings) {
    String ssoSettingUuid = wingsPersistence.save(settings);
    SamlSettings savedSettings = wingsPersistence.get(SamlSettings.class, ssoSettingUuid);
    eventPublishHelper.publishSSOEvent(settings.getAccountId());
    ngAuditLoginSettingsForSAMLUpload(savedSettings);
    return savedSettings;
  }

  private SamlSettings saveAndAuditSAMLSettingUpdateInternal(
      SamlSettings currentSettings, SamlSettings queriedSettings) {
    String ssoSettingUuid = wingsPersistence.save(queriedSettings);
    SamlSettings savedSettings = wingsPersistence.get(SamlSettings.class, ssoSettingUuid);
    ngAuditLoginSettingsForSAMLUpdate(currentSettings, savedSettings);
    return savedSettings;
  }

  @Override
  public void updateAuthenticationEnabledForSAMLSetting(String accountId, String samlSSOId, boolean enable) {
    SamlSettings samlSettings = getSamlSettingsByAccountIdAndUuid(accountId, samlSSOId);
    samlSettings.setAuthenticationEnabled(enable);
    wingsPersistence.save(samlSettings);
  }

  private void ngAuditLoginSettingsForSAMLUpload(SamlSettings newSamlSettings) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsSAMLCreateEvent.builder()
              .accountIdentifier(newSamlSettings.getAccountId())
              .newSamlSettingsYamlDTO(SamlSettingsYamlDTO.builder().samlSettings(newSamlSettings).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsSAMLCreateEvent to outbox",
          newSamlSettings.getAccountId(), outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsSAMLCreateEvent to outbox failed with exception: ",
          newSamlSettings.getAccountId(), ex);
    }
  }

  private void ngAuditLoginSettingsForSAMLUpdate(SamlSettings oldSamlSettings, SamlSettings newSamlSettings) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsSAMLUpdateEvent.builder()
              .accountIdentifier(newSamlSettings.getAccountId())
              .oldSamlSettingsYamlDTO(SamlSettingsYamlDTO.builder().samlSettings(oldSamlSettings).build())
              .newSamlSettingsYamlDTO(SamlSettingsYamlDTO.builder().samlSettings(newSamlSettings).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsSAMLUpdateEvent to outbox",
          newSamlSettings.getAccountId(), outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsSAMLUpdateEvent to outbox failed with exception: ",
          newSamlSettings.getAccountId(), ex);
    }
  }

  private void ngAuditLoginSettingsForSAMLDelete(SamlSettings oldSamlSettings) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsSAMLDeleteEvent.builder()
              .accountIdentifier(oldSamlSettings.getAccountId())
              .oldSamlSettingsYamlDTO(SamlSettingsYamlDTO.builder().samlSettings(oldSamlSettings).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsSAMLDeleteEvent to outbox",
          oldSamlSettings.getAccountId(), outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsSAMLDeleteEvent to outbox failed with exception: ",
          oldSamlSettings.getAccountId(), ex);
    }
  }

  @Override
  public OauthSettings saveOauthSettings(OauthSettings settings) {
    OauthSettings queriedSettings = getOauthSettingsByAccountId(settings.getAccountId());
    OauthSettings savedSettings;
    if (queriedSettings != null) {
      queriedSettings.setUrl(settings.getUrl());
      queriedSettings.setDisplayName(settings.getDisplayName());
      queriedSettings.setAllowedProviders(settings.getAllowedProviders());
      queriedSettings.setFilter(settings.getFilter());
      OauthSettings currentSettings = wingsPersistence.get(OauthSettings.class, queriedSettings.getUuid());
      wingsPersistence.save(queriedSettings);
      savedSettings = wingsPersistence.get(OauthSettings.class, queriedSettings.getUuid());
      ngAuditLoginSettingsForOAuthUpdate(settings.getAccountId(), currentSettings, savedSettings);
    } else {
      String ssoSettingUuid = wingsPersistence.save(settings);
      savedSettings = wingsPersistence.get(OauthSettings.class, ssoSettingUuid);
      eventPublishHelper.publishSSOEvent(settings.getAccountId());
      ngAuditLoginSettingsForOAuthUpload(settings.getAccountId(), savedSettings);
    }
    Account account = accountService.get(settings.getAccountId());
    accountService.update(account);
    auditServiceHelper.reportForAuditingUsingAccountId(account.getUuid(), null, settings, Event.Type.CREATE);
    log.info("Auditing creation of OAUTH Settings for account={}", settings.getAccountId());
    return savedSettings;
  }

  private void ngAuditLoginSettingsForOAuthUpload(String accountIdentifier, OauthSettings newOauthSettings) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsOAuthCreateEvent.builder()
              .accountIdentifier(accountIdentifier)
              .newOAuthSettingsYamlDTO(OAuthSettingsYamlDTO.builder().oauthSettings(newOauthSettings).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsOAuthCreateEvent to outbox",
          accountIdentifier, outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsOAuthCreateEvent to outbox failed with exception: ",
          accountIdentifier, ex);
    }
  }

  private void ngAuditLoginSettingsForOAuthUpdate(
      String accountIdentifier, OauthSettings oldOauthSettings, OauthSettings newOauthSettings) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsOAuthUpdateEvent.builder()
              .accountIdentifier(accountIdentifier)
              .oldOAuthSettingsYamlDTO(OAuthSettingsYamlDTO.builder().oauthSettings(oldOauthSettings).build())
              .newOAuthSettingsYamlDTO(OAuthSettingsYamlDTO.builder().oauthSettings(newOauthSettings).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsOAuthUpdateEvent to outbox",
          accountIdentifier, outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsOAuthUpdateEvent to outbox failed with exception: ",
          accountIdentifier, ex);
    }
  }

  private void ngAuditLoginSettingsForOAuthDelete(String accountIdentifier, OauthSettings oldOauthSettings) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsOAuthDeleteEvent.builder()
              .accountIdentifier(accountIdentifier)
              .oldOAuthSettingsYamlDTO(OAuthSettingsYamlDTO.builder().oauthSettings(oldOauthSettings).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsOAuthDeleteEvent to outbox",
          accountIdentifier, outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsOAuthDeleteEvent to outbox failed with exception: ",
          accountIdentifier, ex);
    }
  }

  @Override
  public OauthSettings updateOauthSettings(String accountId, String filter, Set<OauthProviderType> allowedProviders) {
    OauthSettings oldSettings = getOauthSettingsByAccountId(accountId);
    if (oldSettings == null) {
      throw new InvalidRequestException("No existing Oauth settings found for this account.");
    }
    oldSettings.setFilter(filter);
    oldSettings.setAllowedProviders(allowedProviders);
    oldSettings.setDisplayName(allowedProviders.stream().map(OauthProviderType::name).collect(Collectors.joining(",")));
    wingsPersistence.save(oldSettings);
    OauthSettings newSettings = wingsPersistence.get(OauthSettings.class, oldSettings.getUuid());
    auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, newSettings, Event.Type.UPDATE);
    log.info("Auditing updation of OAUTH Settings for account={}", newSettings.getAccountId());
    return newSettings;
  }

  @Override
  public boolean deleteOauthSettings(String accountId) {
    OauthSettings settings = getOauthSettingsByAccountId(accountId);
    if (settings == null) {
      throw new InvalidRequestException("No Oauth settings found for this account.");
    }
    Account account = accountService.get(accountId);
    if (AuthenticationMechanism.OAUTH == account.getAuthenticationMechanism()) {
      throw new InvalidRequestException("Oauth settings cannot be deleted as authentication mechanism is OAUTH.");
    }
    account.setOauthEnabled(false);
    accountService.update(account);
    auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, settings);
    ngAuditLoginSettingsForOAuthDelete(accountId, settings);
    log.info("Auditing deletion of OAUTH Settings for account={}", accountId);
    return wingsPersistence.delete(settings);
  }

  @Override
  public boolean deleteSamlSettings(String accountId) {
    SamlSettings samlSettings = featureFlagService.isEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)
        ? getSamlSettingsByAccountIdNotConfiguredFromNG(accountId)
        : getSamlSettingsByAccountId(accountId);
    if (samlSettings == null) {
      log.error(
          "No SAML setting found or No SAML setting configured from CG for case of FF PL_ENABLE_MULTIPLE_IDP_SUPPORT enabled in account {} found",
          accountId);
      throw new InvalidRequestException(String.format("No Saml settings found for this account %s", accountId));
    }
    auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, samlSettings);
    ngAuditLoginSettingsForSAMLDelete(samlSettings);
    log.info("Auditing deletion of SAML Settings {} for account={}", samlSettings.getUuid(), accountId);
    return deleteSamlSettings(samlSettings);
  }

  @Override
  public boolean deleteSamlSettings(SamlSettings samlSettings) {
    deleteSamlSettingsCheckLinkedUserGroupsInternal(samlSettings);
    return wingsPersistence.delete(samlSettings);
  }

  @Override
  public boolean deleteSamlSettingsWithAudits(SamlSettings samlSettings) {
    if (samlSettings == null) {
      throw new InvalidRequestException("DELETE_SAML_SSO: No Saml settings found with configuration for the account");
    }
    deleteSamlSettingsCheckLinkedUserGroupsInternal(samlSettings);
    log.info(
        "Auditing deletion of SAML Settings {} for account={}", samlSettings.getUuid(), samlSettings.getAccountId());
    auditServiceHelper.reportDeleteForAuditingUsingAccountId(samlSettings.getAccountId(), samlSettings);
    ngAuditLoginSettingsForSAMLDelete(samlSettings);
    return wingsPersistence.delete(samlSettings);
  }

  private void deleteSamlSettingsCheckLinkedUserGroupsInternal(SamlSettings samlSettings) {
    if (userGroupService.existsLinkedUserGroup(samlSettings.getAccountId(), samlSettings.getUuid())) {
      throw new InvalidRequestException(
          "Deleting Saml provider with linked user groups is not allowed. Unlink the user groups first.");
    }
    checkForLinkedSSOGroupsOnNG(samlSettings.getAccountId(), samlSettings.getUuid());
  }

  @Override
  public SamlSettings getSamlSettingsByOrigin(String origin) {
    return wingsPersistence.createQuery(SamlSettings.class).field("origin").equal(origin).get();
  }

  @Override
  public Iterator<SamlSettings> getSamlSettingsIteratorByOrigin(@NotNull String origin, String accountId) {
    Query<SamlSettings> query =
        wingsPersistence.createQuery(SamlSettings.class, excludeAuthority).field("origin").equal(origin);
    if (isNotEmpty(accountId)) {
      query.field("accountId").equal(accountId);
    }
    HIterator hSamlSettingsIterator = new HIterator(query.fetch());
    if (hSamlSettingsIterator.hasNext()) {
      return hSamlSettingsIterator;
    }
    if (isNotEmpty(accountId)) {
      query = wingsPersistence.createQuery(SamlSettings.class, excludeAuthority)
                  .field("accountId")
                  .equal(accountId)
                  .field("type")
                  .equal(SSOType.SAML);
      return new HIterator(query.fetch());
    }
    return null;
  }

  @Override
  public Iterator<SamlSettings> getSamlSettingsIteratorByAccountId(@NotNull String accountId) {
    Query<SamlSettings> query = wingsPersistence.createQuery(SamlSettings.class, excludeAuthority)
                                    .field("accountId")
                                    .equal(accountId)
                                    .field("type")
                                    .equal(SSOType.SAML);
    return new HIterator(query.fetch());
  }

  @Override
  @RestrictedApi(LdapFeature.class)
  public LdapSettings createLdapSettings(
      @GetAccountId(LdapSettingsAccountIdExtractor.class) @NotNull LdapSettings settings) {
    if (getLdapSettingsByAccountId(settings.getAccountId()) != null) {
      throw new InvalidRequestException("Ldap settings already exist for this account.");
    }
    ssoServiceHelper.encryptLdapSecret(settings.getConnectionSettings(), secretManager, settings.getAccountId());

    settings.encryptLdapInlineSecret(secretManager, false);
    if (isEmpty(settings.getCronExpression())) {
      settings.setCronExpression(ldapSyncJobConfig.getDefaultCronExpression());
    }
    updateNextIterations(settings);
    LdapSettings savedSettings = wingsPersistence.saveAndGet(LdapSettings.class, settings);
    ldapGroupScheduledHandler.handle(savedSettings);
    auditServiceHelper.reportForAuditingUsingAccountId(settings.getAccountId(), null, settings, Event.Type.CREATE);
    ngAuditLoginSettingsForLdapUpload(savedSettings.getAccountId(), savedSettings);
    log.info("Auditing creation of LDAP Settings for account={}", settings.getAccountId());
    eventPublishHelper.publishSSOEvent(settings.getAccountId());
    return savedSettings;
  }

  @Override
  @RestrictedApi(LdapFeature.class)
  public LdapSettings updateLdapSettings(
      @GetAccountId(LdapSettingsAccountIdExtractor.class) @NotNull LdapSettings settings) {
    LdapSettings oldSettings = getLdapSettingsByAccountId(settings.getAccountId());
    if (oldSettings == null) {
      throw new InvalidRequestException("No existing Ldap settings found for this account.");
    }
    settings.getConnectionSettings().setEncryptedBindPassword(
        oldSettings.getConnectionSettings().getEncryptedBindPassword());
    settings.getConnectionSettings().setPasswordType(oldSettings.getConnectionSettings().getPasswordType());
    settings.getConnectionSettings().setEncryptedBindSecret(
        oldSettings.getConnectionSettings().getEncryptedBindSecret());
    oldSettings.getConnectionSettings().setAccountId(settings.getAccountId());
    oldSettings.getConnectionSettings().setDelegateSelectors(settings.getConnectionSettings().getDelegateSelectors());
    oldSettings.setUrl(settings.getUrl());
    oldSettings.setDisplayName(settings.getDisplayName());
    oldSettings.setConnectionSettings(settings.getConnectionSettings());
    oldSettings.setUserSettingsList(settings.getUserSettingsList());
    oldSettings.setGroupSettingsList(settings.getGroupSettingsList());
    oldSettings.setDisabled(settings.isDisabled());
    ssoServiceHelper.encryptLdapSecret(oldSettings.getConnectionSettings(), secretManager, settings.getAccountId());

    oldSettings.encryptLdapInlineSecret(secretManager, false);
    oldSettings.setDefaultCronExpression(ldapSyncJobConfig.getDefaultCronExpression());
    oldSettings.setCronExpression(settings.getCronExpression());
    updateNextIterations(oldSettings);
    LdapSettings currentLdapSettings = getLdapSettingsByUuid(oldSettings.getUuid());
    LdapSettings savedSettings = wingsPersistence.saveAndGet(LdapSettings.class, oldSettings);
    auditServiceHelper.reportForAuditingUsingAccountId(
        settings.getAccountId(), oldSettings, savedSettings, Event.Type.UPDATE);
    ngAuditLoginSettingsForLdapUpdate(settings.getAccountId(), currentLdapSettings, savedSettings);
    log.info("Auditing updation of LDAP for account={}", savedSettings.getAccountId());
    ldapGroupScheduledHandler.handle(savedSettings);
    return savedSettings;
  }

  @Override
  public LdapSettings deleteLdapSettings(@NotBlank String accountId) {
    LdapSettings settings = getLdapSettingsByAccountId(accountId);
    if (settings == null) {
      throw new InvalidRequestException("No Ldap settings found for this account.");
    }
    return deleteLdapSettings(settings);
  }

  @Override
  public LdapSettings deleteLdapSettings(@NotNull LdapSettings settings) {
    if (userGroupService.existsLinkedUserGroup(settings.getAccountId(), settings.getUuid())) {
      throw new InvalidRequestException(
          "Deleting SSO provider with linked user groups is not allowed. Unlink the user groups first.");
    }
    checkForLinkedSSOGroupsOnNG(settings.getAccountId(), settings.getUuid());
    if (LdapConnectionSettings.INLINE_SECRET.equals(settings.getConnectionSettings().getPasswordType())) {
      secretManager.deleteSecret(
          settings.getAccountId(), settings.getConnectionSettings().getEncryptedBindPassword(), new HashMap<>(), false);
    }
    wingsPersistence.delete(settings);
    auditServiceHelper.reportDeleteForAuditingUsingAccountId(settings.getAccountId(), settings);
    ngAuditLoginSettingsForLdapDelete(settings.getAccountId(), settings);
    log.info("Auditing deletion of LDAP Settings for account={}", settings.getAccountId());
    return settings;
  }

  private void ngAuditLoginSettingsForLdapUpload(String accountIdentifier, LdapSettings newLdapSettings) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsLDAPCreateEvent.builder()
              .accountIdentifier(accountIdentifier)
              .newLdapSettingsYamlDTO(LdapSettingsYamlDTO.builder().ldapSettings(newLdapSettings).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsLDAPCreateEvent to outbox",
          accountIdentifier, outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsLDAPCreateEvent to outbox failed with exception: ",
          accountIdentifier, ex);
    }
  }

  private void ngAuditLoginSettingsForLdapUpdate(
      String accountIdentifier, LdapSettings oldLdapSettings, LdapSettings newLdapSettings) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsLDAPUpdateEvent.builder()
              .accountIdentifier(accountIdentifier)
              .oldLdapSettingsYamlDTO(LdapSettingsYamlDTO.builder().ldapSettings(oldLdapSettings).build())
              .newLdapSettingsYamlDTO(LdapSettingsYamlDTO.builder().ldapSettings(newLdapSettings).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsLDAPUpdateEvent to outbox",
          accountIdentifier, outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsLDAPUpdateEvent to outbox failed with exception: ",
          accountIdentifier, ex);
    }
  }

  private void ngAuditLoginSettingsForLdapDelete(String accountIdentifier, LdapSettings oldLdapSettings) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsLDAPDeleteEvent.builder()
              .accountIdentifier(accountIdentifier)
              .oldLdapSettingsYamlDTO(LdapSettingsYamlDTO.builder().ldapSettings(oldLdapSettings).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsLDAPDeleteEvent to outbox",
          accountIdentifier, outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsLDAPDeleteEvent to outbox failed with exception: ",
          accountIdentifier, ex);
    }
  }

  @Override
  public LdapSettings getLdapSettingsByAccountId(@NotBlank String accountId) {
    if (isEmpty(accountId)) {
      return null;
    }
    LdapSettings ldapSettings = wingsPersistence.createQuery(LdapSettings.class)
                                    .field(SSOSettingsKeys.accountId)
                                    .equal(accountId)
                                    .field(SSOSettingsKeys.type)
                                    .equal(SSOType.LDAP)
                                    .get();
    sanitizeLdapSetting(ldapSettings);
    return ldapSettings;
  }

  @Override
  public LdapSettings getLdapSettingsByUuid(@NotBlank String uuid) {
    LdapSettings ldapSettings = wingsPersistence.createQuery(LdapSettings.class)
                                    .field("uuid")
                                    .equal(uuid)
                                    .field("type")
                                    .equal(SSOType.LDAP)
                                    .get();
    sanitizeLdapSetting(ldapSettings);
    return ldapSettings;
  }

  private void sanitizeLdapSetting(LdapSettings ldapSettings) {
    if (ldapSettings != null && isNotEmpty(ldapSettings.getConnectionSettings().getEncryptedBindSecret())) {
      ldapSettings.getConnectionSettings().setBindSecret(
          ldapSettings.getConnectionSettings().getEncryptedBindSecret().toCharArray());
    }
  }

  @Override
  public boolean isLdapSettingsPresent(@NotBlank String uuid) {
    return 0
        != wingsPersistence.createQuery(LdapSettings.class)
               .field("uuid")
               .equal(uuid)
               .field("type")
               .equal(SSOType.LDAP)
               .count();
  }

  @Override
  public SSOSettings getSsoSettings(String uuid) {
    return wingsPersistence.createQuery(SSOSettings.class).field("uuid").equal(uuid).get();
  }

  @Override
  public void raiseSyncFailureAlert(String accountId, String ssoId, String message) {
    // We will send the alert message every 24 hours
    SSOSyncFailedAlert alertData =
        SSOSyncFailedAlert.builder().accountId(accountId).ssoId(ssoId).message(message).build();
    Optional<Alert> existingAlert =
        alertService.findExistingAlert(accountId, GLOBAL_APP_ID, AlertType.USERGROUP_SYNC_FAILED, alertData);
    if (existingAlert.isPresent()) {
      Alert alert = existingAlert.get();
      long lastUpdatedAt = alert.getLastUpdatedAt();
      // If the previous alert happened within 24 hours, then we skip that alert
      if (System.currentTimeMillis() - lastUpdatedAt < ONE_DAY) {
        return;
      }
    }
    closeSyncFailureAlertIfOpen(accountId, ssoId);
    alertService.openAlert(accountId, GLOBAL_APP_ID, AlertType.USERGROUP_SYNC_FAILED, alertData);
  }

  @Override
  public void closeSyncFailureAlertIfOpen(String accountId, String ssoId) {
    SSOSyncFailedAlert alertData = SSOSyncFailedAlert.builder().accountId(accountId).ssoId(ssoId).build();
    alertService.closeAlert(accountId, GLOBAL_APP_ID, AlertType.USERGROUP_SYNC_FAILED, alertData);
  }

  @Override
  public void sendSSONotReachableNotification(String accountId, SSOSettings settings) {
    List<Delegate> delegates = delegateService.list(
        PageRequestBuilder.aPageRequest().addFilter(DelegateKeys.accountId, Operator.EQ, accountId).build());
    String hostNamesForDelegates = "\n" + delegates.stream().map(Delegate::getHostName).collect(joining("\n"));

    String hostNamesForDelegatesHtml =
        "<br />" + delegates.stream().map(Delegate::getHostName).collect(joining("<br />"));

    List<NotificationGroup> notificationGroups = notificationSetupService.listDefaultNotificationGroup(accountId);
    NotificationRule notificationRule = aNotificationRule().withNotificationGroups(notificationGroups).build();

    notificationService.sendNotificationAsync(
        InformationNotification.builder()
            .appId(GLOBAL_APP_ID)
            .accountId(accountId)
            .notificationTemplateId(SSO_PROVIDER_NOT_REACHABLE_NOTIFICATION.name())
            .notificationTemplateVariables(ImmutableMap.of("SSO_PROVIDER_NAME", settings.getDisplayName(),
                "SSO_PROVIDER_TYPE", settings.getType().name(), "SSO_PROVIDER_URL", settings.getUrl(), "DELEGATE_HOSTS",
                hostNamesForDelegates, "DELEGATE_HOSTS_HTML", hostNamesForDelegatesHtml))
            .build(),
        singletonList(notificationRule));
  }

  @Override
  public boolean isDefault(String accountId, String ssoId) {
    SSOSettings ssoSettings = getSsoSettings(ssoId);
    if (null == ssoSettings) {
      return false;
    }

    AuthenticationMechanism authenticationMechanism = accountService.get(accountId).getAuthenticationMechanism();
    if (authenticationMechanism == AuthenticationMechanism.LDAP) {
      if (ssoSettings.getType() == SSOType.LDAP) {
        return true;
      }
    }

    if (authenticationMechanism == AuthenticationMechanism.SAML) {
      if (ssoSettings.getType() == SSOType.SAML) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(SSOSettings.class)
                                .disableValidation()
                                .filter(SSOSettings.ACCOUNT_ID_KEY2, accountId));
  }

  @Override
  public List<SSOSettings> getAllSsoSettings(String accountId) {
    return wingsPersistence.createQuery(SSOSettings.class)
        .disableValidation()
        .filter(SSOSettings.ACCOUNT_ID_KEY2, accountId)
        .asList();
  }

  @Override
  public List<Long> getIterationsFromCron(String accountId, String cron) {
    List<Long> nextIterations = new ArrayList<>();
    try {
      getPersistentCronIterableObject().expandNextIterations(true, 0, cron, nextIterations);
    } catch (Exception ex) {
      String message = "Given cron expression doesn't evaluate to a valid time. Please check the expression provided";
      log.error(message, ex);
      throw new InvalidRequestException(message);
    }

    return validateIterationsAndRemoveCurrentTime(nextIterations);
  }

  private void updateNextIterations(LdapSettings ldapSettings) {
    ldapSettings.getNextIterations().clear();
    ldapSettings.recalculateNextIterations(SSOSettingsKeys.nextIterations, true, 0);
    ldapSettings.setNextIterations(validateIterationsAndRemoveCurrentTime(ldapSettings.getNextIterations()));
  }

  private List<Long> validateIterationsAndRemoveCurrentTime(List<Long> nextIterations) {
    if (nextIterations.size() > 1 && ((nextIterations.get(1) - nextIterations.get(0)) / 1000 < MIN_INTERVAL)) {
      throw new InvalidRequestException(
          "Cron Expression should evaluate to time intervals of at least " + MIN_INTERVAL + " seconds.");
    }
    if (isEmpty(nextIterations)) {
      throw new InvalidRequestException(
          "Given cron expression doesn't evaluate to a valid time. Please check the expression provided");
    }
    return nextIterations;
  }

  private PersistentCronIterable getPersistentCronIterableObject() {
    return new PersistentCronIterable() {
      @Override
      public String getUuid() {
        return null;
      }

      @Override
      public Long obtainNextIteration(String fieldName) {
        return null;
      }

      @Override
      public List<Long> recalculateNextIterations(String fieldName, boolean skipMissed, long throttled) {
        return null;
      }
    };
  }

  private void checkForLinkedSSOGroupsOnNG(final String accountId, final String ssoUuid) {
    List<UserGroupDTO> userGroupDTOs = null;
    if (accountService.isNextGenEnabled(accountId)) {
      try {
        userGroupDTOs = NGRestUtils.getResponse(userGroupClient.getSsoLinkedUserGroups(ssoUuid, accountId));
      } catch (Exception exc) {
        log.error(
            "For account {} and ssoId {} for delete SSO provider request, finding linked SSO groups on NG call failed with exception: ",
            accountId, ssoUuid, exc);
        throw exc;
      }
    }
    if (isNotEmpty(userGroupDTOs)) {
      throw new InvalidRequestException(
          "Deleting SSO provider with linked user groups is not allowed. Unlink the user groups in NG also first.");
    }
  }
}
