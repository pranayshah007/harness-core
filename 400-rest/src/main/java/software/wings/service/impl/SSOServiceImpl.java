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
import static io.harness.delegate.beans.TaskData.DEFAULT_SYNC_CALL_TIMEOUT;
import static io.harness.eraro.ErrorCode.USER_NOT_AUTHORIZED;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.ng.core.account.AuthenticationMechanism.LDAP;
import static io.harness.ng.core.account.AuthenticationMechanism.OAUTH;
import static io.harness.ng.core.account.AuthenticationMechanism.SAML;
import static io.harness.ng.core.account.AuthenticationMechanism.USER_PASSWORD;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.authenticationservice.beans.SAMLProviderType;
import io.harness.beans.SecretText;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataAndPasswordDetail;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.OauthProviderType;
import io.harness.outbox.OutboxEvent;
import io.harness.outbox.api.OutboxService;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.Account;
import software.wings.beans.Event;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.loginSettings.events.AuthMechanismYamlDTO;
import software.wings.beans.loginSettings.events.LoginSettingsAuthMechanismUpdateEvent;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettingsMapper;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.delegatetasks.DelegateProxyFactory;
import software.wings.features.LdapFeature;
import software.wings.features.SamlFeature;
import software.wings.features.api.PremiumFeature;
import software.wings.helpers.ext.ldap.LdapConstants;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.security.PermissionAttribute;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.authentication.SSOConfig;
import software.wings.security.authentication.oauth.OauthOptions;
import software.wings.security.saml.SamlClientService;
import software.wings.service.impl.security.auth.AuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.SSOSettingService;
import software.wings.service.intfc.ldap.LdapDelegateService;
import software.wings.service.intfc.security.EncryptionService;
import software.wings.service.intfc.security.SecretManager;

import com.coveo.saml.SamlClient;
import com.coveo.saml.SamlException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.validation.constraints.NotNull;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hibernate.validator.constraints.NotBlank;

@ValidateOnExecution
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(_950_NG_AUTHENTICATION_SERVICE)
public class SSOServiceImpl implements SSOService {
  @Inject AccountService accountService;
  @Inject SSOSettingService ssoSettingService;
  @Inject SamlClientService samlClientService;
  @Inject private DelegateProxyFactory delegateProxyFactory;
  @Inject private SecretManager secretManager;
  @Inject OauthOptions oauthOptions;
  @Inject private AuthHandler authHandler;
  @Inject @Named(LdapFeature.FEATURE_NAME) private PremiumFeature ldapFeature;
  @Inject @Named(SamlFeature.FEATURE_NAME) private PremiumFeature samlFeature;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private EncryptionService encryptionService;
  @Inject private SSOServiceHelper ssoServiceHelper;
  @Inject private OutboxService outboxService;

  @Override
  public SSOConfig uploadSamlConfiguration(String accountId, InputStream inputStream, String displayName,
      String groupMembershipAttr, Boolean authorizationEnabled, String logoutUrl, String entityIdentifier,
      String samlProviderType, String clientId, char[] clientSecret, String friendlySamlName, boolean isNGSSO,
      Boolean jitEnabled, String jitValidationKey, String jitValidationValue) {
    try {
      String fileAsString = IOUtils.toString(inputStream, Charset.defaultCharset());
      groupMembershipAttr = authorizationEnabled ? groupMembershipAttr : null;
      buildAndUploadSamlSettings(accountId, fileAsString, displayName, groupMembershipAttr, logoutUrl, entityIdentifier,
          samlProviderType, clientId, clientSecret, friendlySamlName, isNGSSO, false, null, jitEnabled,
          jitValidationKey, jitValidationValue);
      return getAccountAccessManagementSettings(accountId, isNGSSO);
    } catch (SamlException | IOException | URISyntaxException e) {
      throw new WingsException(ErrorCode.INVALID_SAML_CONFIGURATION, e);
    }
  }

  @Override
  public SSOConfig uploadOauthConfiguration(
      String accountId, String filter, Set<OauthProviderType> allowedProviders, boolean isNG) {
    if (isEmpty(allowedProviders)) {
      throw new InvalidRequestException("At least one OAuth provider must be selected.");
    }
    buildAndUploadOauthSettings(accountId, filter, allowedProviders);
    return getAccountAccessManagementSettings(accountId, isNG);
  }

  @Override
  public SSOConfig updateSamlConfiguration(String accountId, InputStream inputStream, String displayName,
      String groupMembershipAttr, Boolean authorizationEnabled, String logoutUrl, String entityIdentifier,
      String samlProviderType, String clientId, char[] clientSecret, boolean isNGSSO, Boolean jitEnabled,
      String jitValidationKey, String jitValidationValue) {
    try {
      SamlSettings settings = featureFlagService.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)
          ? ssoSettingService.getSamlSettingsByAccountId(accountId)
          : ssoSettingService.getSamlSettingsByAccountIdNotConfiguredFromNG(accountId);
      if (null == settings) {
        throw new InvalidRequestException(String.format(
            "Multiple IdP support FF 'PL_ENABLE_MULTIPLE_IDP_SUPPORT' enabled on account %s and SAML setting not created from CG. "
                + "Please update on saml SSO Id API endpoint from NG: 'saml-metadata-upload/{samlSSOId}' to update a SAML setting.",
            accountId));
      }
      return updateAndGetSamlSsoConfigInternal(groupMembershipAttr, authorizationEnabled, inputStream, settings,
          displayName, clientId, clientSecret, accountId, logoutUrl, entityIdentifier, samlProviderType, null, isNGSSO,
          jitEnabled, jitValidationKey, jitValidationValue);
    } catch (SamlException | IOException | URISyntaxException e) {
      throw new WingsException(ErrorCode.INVALID_SAML_CONFIGURATION, e);
    }
  }

  @Override
  public SSOConfig updateSamlConfiguration(String accountId, String samlSSOId, InputStream inputStream,
      String displayName, String groupMembershipAttr, Boolean authorizationEnabled, String logoutUrl,
      String entityIdentifier, String samlProviderType, String clientId, char[] clientSecret, String friendlySamlName,
      boolean isNGSSO, Boolean jitEnabled, String jitValidationKey, String jitValidationValue) {
    try {
      SamlSettings settings = ssoSettingService.getSamlSettingsByAccountIdAndUuid(accountId, samlSSOId);
      return updateAndGetSamlSsoConfigInternal(groupMembershipAttr, authorizationEnabled, inputStream, settings,
          displayName, clientId, clientSecret, accountId, logoutUrl, entityIdentifier, samlProviderType,
          friendlySamlName, isNGSSO, jitEnabled, jitValidationKey, jitValidationValue);
    } catch (SamlException | IOException | URISyntaxException e) {
      throw new WingsException(ErrorCode.INVALID_SAML_CONFIGURATION, e);
    }
  }

  @Override
  public void updateAuthenticationEnabledForSAMLSetting(String accountId, String samlSSOId, boolean enable) {
    ssoSettingService.updateAuthenticationEnabledForSAMLSetting(accountId, samlSSOId, enable);
  }

  @Override
  public SSOConfig updateLogoutUrlSamlSettings(String accountId, String logoutUrl) {
    SamlSettings samlSettings = featureFlagService.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)
        ? ssoSettingService.getSamlSettingsByAccountId(accountId)
        : ssoSettingService.getSamlSettingsByAccountIdNotConfiguredFromNG(accountId);
    if (samlSettings != null) {
      log.info("Logout url being set from API is {}", logoutUrl);
      samlSettings.setLogoutUrl(logoutUrl);
      ssoSettingService.saveSamlSettings(samlSettings);
    } else {
      throw new InvalidRequestException("Cannot update Logout URL as no SAML Config exists for your account from CG");
    }
    return getAccountAccessManagementSettings(accountId, false);
  }

  @Override
  public SSOConfig deleteSamlConfiguration(String accountId) {
    ssoSettingService.deleteSamlSettings(accountId);
    return setToAuthMechanismAndReturnSsoConfig(accountId, false);
  }

  @Override
  public SSOConfig deleteSamlConfiguration(String accountId, String samlSSOId) {
    SamlSettings settings = ssoSettingService.getSamlSettingsByAccountIdAndUuid(accountId, samlSSOId);
    ssoSettingService.deleteSamlSettingsWithAudits(settings);
    return setToAuthMechanismAndReturnSsoConfig(accountId, true);
  }

  private SSOConfig setToAuthMechanismAndReturnSsoConfig(String accountId, boolean isWithSamlSSOId) {
    boolean updateAuthMechanismToUserPwd = true;
    if (isWithSamlSSOId && featureFlagService.isEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)) {
      List<SamlSettings> samlSettings = ssoSettingService.getSamlSettingsListByAccountId(accountId);
      if (isNotEmpty(samlSettings)) {
        for (SamlSettings setting : samlSettings) {
          if (setting != null && setting.isAuthenticationEnabled()) {
            updateAuthMechanismToUserPwd = false;
            break;
          }
        }
      }
    }

    SSOConfig ssoConfig;
    if (updateAuthMechanismToUserPwd) {
      ssoConfig = setAuthenticationMechanism(accountId, USER_PASSWORD, false);
      setOauthIfSetAfterSSODelete(accountId);
    } else {
      ssoConfig = getAccountAccessManagementSettingsV2(accountId);
    }
    return ssoConfig;
  }

  private void cgAuditLoginSettings(
      String accountIdentifier, AuthenticationMechanism oldAuthMechanism, AuthenticationMechanism newAuthMechanism) {
    SSOSettings ssoSettings = null;

    if (newAuthMechanism == USER_PASSWORD) {
      if (oldAuthMechanism == SAML) {
        ssoSettings = featureFlagService.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountIdentifier)
            ? ssoSettingService.getSamlSettingsByAccountId(accountIdentifier)
            : ssoSettingService.getSamlSettingsByAccountIdNotConfiguredFromNG(accountIdentifier);
      } else if (oldAuthMechanism == LDAP) {
        ssoSettings = ssoSettingService.getLdapSettingsByAccountId(accountIdentifier);
      }
      if (null != ssoSettings) {
        auditServiceHelper.reportForAuditingUsingAccountId(accountIdentifier, null, ssoSettings, Event.Type.DISABLE);
      }
    } else {
      switch (newAuthMechanism) {
        case SAML:
          ssoSettings = featureFlagService.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountIdentifier)
              ? ssoSettingService.getSamlSettingsByAccountId(accountIdentifier)
              : ssoSettingService.getSamlSettingsByAccountIdNotConfiguredFromNG(accountIdentifier);
          break;
        case LDAP:
          ssoSettings = ssoSettingService.getLdapSettingsByAccountId(accountIdentifier);
          break;
        case OAUTH:
          ssoSettings = ssoSettingService.getOauthSettingsByAccountId(accountIdentifier);
          break;
        default:
          throw new InvalidRequestException("Unexpected authentication mechanism type: " + newAuthMechanism.name());
      }
      if (null != ssoSettings) {
        auditServiceHelper.reportForAuditingUsingAccountId(accountIdentifier, null, ssoSettings, Event.Type.ENABLE);
      }
    }
    log.info("CG Auth Audits: for account {} successfully audited the change of authentication mechanism from {} to {}",
        accountIdentifier, oldAuthMechanism.name(), newAuthMechanism.name());
  }

  private void ngAuditLoginSettings(
      String accountIdentifier, AuthenticationMechanism oldAuthMechanism, AuthenticationMechanism newAuthMechanism) {
    try {
      OutboxEvent outboxEvent = outboxService.save(
          LoginSettingsAuthMechanismUpdateEvent.builder()
              .accountIdentifier(accountIdentifier)
              .oldAuthMechanismYamlDTO(AuthMechanismYamlDTO.builder().authenticationMechanism(oldAuthMechanism).build())
              .newAuthMechanismYamlDTO(AuthMechanismYamlDTO.builder().authenticationMechanism(newAuthMechanism).build())
              .build());
      log.info(
          "NG Auth Audits: for account {} and outboxEventId {} successfully saved the audit for LoginSettingsAuthMechanismUpdateEvent to outbox",
          accountIdentifier, outboxEvent.getId());
    } catch (Exception ex) {
      log.error(
          "NG Auth Audits: for account {} saving the LoginSettingsAuthMechanismUpdateEvent to outbox failed with exception: ",
          accountIdentifier, ex);
    }
  }

  @Override
  public SSOConfig setAuthenticationMechanism(String accountId, AuthenticationMechanism mechanism, boolean isFromNG) {
    checkIfOperationIsAllowed(accountId, mechanism);

    Account account = accountService.get(accountId);
    AuthenticationMechanism currentAuthMechanism = account.getAuthenticationMechanism();
    if (null == currentAuthMechanism) {
      currentAuthMechanism = USER_PASSWORD;
    }
    boolean isOauthEnabledCurrently = account.isOauthEnabled();

    boolean shouldEnableOauth = false;
    boolean shouldUpdateAuthMechanism = true;

    if (mechanism == OAUTH && (currentAuthMechanism == USER_PASSWORD && !isOauthEnabledCurrently)) {
      shouldUpdateAuthMechanism = false;
    }

    if (mechanism == OAUTH || (currentAuthMechanism == OAUTH && mechanism == USER_PASSWORD)) {
      shouldEnableOauth = true;
    }

    if (shouldEnableOauth && null == ssoSettingService.getOauthSettingsByAccountId(accountId)) {
      throw new InvalidRequestException(
          String.format("Cannot enable OAuth for accountId %s because OAuthSetting does not exist", accountId));
    }
    account.setOauthEnabled(shouldEnableOauth);
    if (shouldUpdateAuthMechanism && currentAuthMechanism != mechanism) {
      cgAuditLoginSettings(accountId, currentAuthMechanism, mechanism);
      ngAuditLoginSettings(accountId, currentAuthMechanism, mechanism);
      account.setAuthenticationMechanism(mechanism);
      if (featureFlagService.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)) {
        SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
        if (samlSettings != null) {
          ssoSettingService.updateAuthenticationEnabledForSAMLSetting(
              accountId, samlSettings.getUuid(), SAML == mechanism);
        }
      } else {
        if (!isFromNG) {
          SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountIdNotConfiguredFromNG(accountId);
          if (null == samlSettings) {
            if (SAML == mechanism) {
              throw new InvalidRequestException(String.format(
                  "Multiple IdP support FF 'PL_ENABLE_MULTIPLE_IDP_SUPPORT' enabled on account %s and SAML setting not configured from CG. Please enable SAML authentication mechanism for account from NG",
                  accountId));
            }
          } else {
            ssoSettingService.updateAuthenticationEnabledForSAMLSetting(
                accountId, samlSettings.getUuid(), SAML == mechanism);
          }
        }
      }
    }
    accountService.update(account);
    return featureFlagService.isEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId) && isFromNG
        ? getAccountAccessManagementSettingsV2(accountId)
        : getAccountAccessManagementSettings(accountId, false);
  }

  @Override
  public SSOConfig getAccountAccessManagementSettings(String accountId, boolean isNG) {
    // We are handling the check programmatically for now, since we don't have enough info in the query / path
    // parameters
    authorizeAccessManagementCall();
    Account account = accountService.get(accountId);
    SSOConfig ssoConfigBuilt =
        SSOConfig.builder().accountId(accountId).authenticationMechanism(account.getAuthenticationMechanism()).build();
    List<SSOSettings> ssoSettingsList =
        featureFlagService.isEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid()) && isNG
        ? getSSOSettingsV2(account)
        : getSSOSettings(account);
    ssoConfigBuilt.setSsoSettings(ssoSettingsList);
    return ssoConfigBuilt;
  }

  @Override
  public SSOConfig getAccountAccessManagementSettingsV2(String accountId) {
    authorizeAccessManagementCall();
    Account account = accountService.get(accountId);
    return SSOConfig.builder()
        .accountId(accountId)
        .authenticationMechanism(account.getAuthenticationMechanism())
        .ssoSettings(getSSOSettingsV2(account))
        .build();
  }

  private void authorizeAccessManagementCall() {
    PermissionAttribute userReadPermissionAttribute =
        new PermissionAttribute(PermissionType.USER_PERMISSION_READ, Action.READ);
    PermissionAttribute accountManagementPermission =
        new PermissionAttribute(PermissionType.MANAGE_AUTHENTICATION_SETTINGS, Action.READ);
    boolean isAuthorized =
        handleExceptionInAuthorization(asList(userReadPermissionAttribute, accountManagementPermission));
    if (!isAuthorized) {
      throw new InvalidRequestException("User not Authorized", USER_NOT_AUTHORIZED, WingsException.USER);
    }
  }

  private boolean handleExceptionInAuthorization(List<PermissionAttribute> userPermissionList) {
    try {
      authHandler.authorizeAccountPermission(userPermissionList);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private List<SSOSettings> getSSOSettings(Account account) {
    List<SSOSettings> settings = new ArrayList<>();
    SamlSettings samlSettings = featureFlagService.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, account.getUuid())
        ? ssoSettingService.getSamlSettingsByAccountId(account.getUuid())
        : ssoSettingService.getSamlSettingsByAccountIdNotConfiguredFromNG(account.getUuid());
    if (samlSettings != null) {
      settings.add(samlSettings.getPublicSSOSettings());
    }
    LdapSettings ldapSettings = ssoSettingService.getLdapSettingsByAccountId(account.getUuid());
    if (ldapSettings != null) {
      settings.add(ldapSettings.getPublicSSOSettings());
    }
    OauthSettings oauthSettings = ssoSettingService.getOauthSettingsByAccountId(account.getUuid());
    if (oauthSettings != null) {
      settings.add(oauthSettings.getPublicSSOSettings());
    }
    return settings;
  }

  private List<SSOSettings> getSSOSettingsV2(Account account) {
    List<SSOSettings> settings = new ArrayList<>();
    List<SamlSettings> samlSettings = ssoSettingService.getSamlSettingsListByAccountId(account.getUuid());
    if (isNotEmpty(samlSettings)) {
      samlSettings.forEach(setting -> settings.add(setting.getPublicSSOSettings()));
    }
    LdapSettings ldapSettings = ssoSettingService.getLdapSettingsByAccountId(account.getUuid());
    if (ldapSettings != null) {
      settings.add(ldapSettings.getPublicSSOSettings());
    }
    OauthSettings oauthSettings = ssoSettingService.getOauthSettingsByAccountId(account.getUuid());
    if (oauthSettings != null) {
      settings.add(oauthSettings.getPublicSSOSettings());
    }
    return settings;
  }

  private SamlSettings buildAndUploadSamlSettings(String accountId, String fileAsString, String displayName,
      String groupMembershipAttr, String logoutUrl, String entityIdentifier, String samlProviderType, String clientId,
      char[] clientSecret, String friendlySamlName, boolean isNGSSOSetting, boolean isUpdateCase, String samlSSOId,
      boolean jitEnabled, String jitValidationKey, String jitValidationValue) throws SamlException, URISyntaxException {
    SamlClient samlClient = samlClientService.getSamlClient(entityIdentifier, fileAsString);

    SamlSettings samlSettings = SamlSettings.builder()
                                    .metaDataFile(fileAsString)
                                    .url(samlClient.getIdentityProviderUrl())
                                    .accountId(accountId)
                                    .displayName(displayName)
                                    .origin(new URI(samlClient.getIdentityProviderUrl()).getHost())
                                    .groupMembershipAttr(groupMembershipAttr)
                                    .entityIdentifier(entityIdentifier)
                                    .friendlySamlName(friendlySamlName)
                                    .jitEnabled(jitEnabled)
                                    .jitValidationKey(jitValidationKey)
                                    .jitValidationValue(jitValidationValue)
                                    .build();

    samlSettings.setSamlProviderType(getSAMLProviderType(samlProviderType));
    if (isNotEmpty(samlSSOId)) {
      samlSettings.setUuid(samlSSOId);
    }
    if (isNotEmpty(clientId) && isNotEmpty(clientSecret)) {
      samlSettings.setClientId(clientId);
      samlSettings.setEncryptedClientSecret(String.valueOf(clientSecret));
    } else if (isNotEmpty(clientId) && isEmpty(clientSecret) || isEmpty(clientId) && isNotEmpty(clientSecret)) {
      throw new InvalidRequestException(
          "Both clientId and clientSecret needs to be provided together for SAML setting", WingsException.USER);
    }

    if (logoutUrl != null) {
      samlSettings.setLogoutUrl(logoutUrl);
    }
    if (isNGSSOSetting) {
      return featureFlagService.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)
          ? ssoSettingService.saveSamlSettingsWithoutCGLicenseCheck(samlSettings)
          : ssoSettingService.saveSamlSettingsWithoutCGLicenseCheck(samlSettings, isUpdateCase, isNGSSOSetting);
    } else {
      return featureFlagService.isNotEnabled(PL_ENABLE_MULTIPLE_IDP_SUPPORT, accountId)
          ? ssoSettingService.saveSamlSettings(samlSettings)
          : ssoSettingService.saveSamlSettings(samlSettings, isUpdateCase, isNGSSOSetting);
    }
  }

  private OauthSettings buildAndUploadOauthSettings(
      String accountId, String filter, Set<OauthProviderType> allowedProviders) {
    OauthSettings oauthSettings =
        OauthSettings.builder().accountId(accountId).allowedProviders(allowedProviders).filter(filter).build();
    return ssoSettingService.saveOauthSettings(oauthSettings);
  }

  @Override
  public LdapSettings createLdapSettings(@NotNull LdapSettings settings) {
    return ssoSettingService.createLdapSettings(settings);
  }

  @Override
  public LdapSettings deleteLdapSettings(@NotBlank String accountId) {
    LdapSettings settings = ssoSettingService.deleteLdapSettings(accountId);
    if (accountService.get(accountId).getAuthenticationMechanism() == AuthenticationMechanism.LDAP) {
      setAuthenticationMechanism(accountId, USER_PASSWORD, false);
      setOauthIfSetAfterSSODelete(accountId);
    }
    return settings;
  }

  @Override
  public LdapSettings updateLdapSettings(@NotNull LdapSettings settings) {
    return ssoSettingService.updateLdapSettings(settings);
  }

  @Override
  public LdapSettings getLdapSettings(@NotBlank String accountId) {
    return ssoSettingService.getLdapSettingsByAccountId(accountId);
  }

  @Override
  public LdapSettingsWithEncryptedDataDetail getLdapSettingWithEncryptedDataDetail(
      @NotBlank String accountId, LdapSettings inputLdapSettings) {
    if (null == inputLdapSettings) {
      LdapSettings ldapSettings = ssoSettingService.getLdapSettingsByAccountId(accountId);
      populateEncryptedFields(ldapSettings);
      encryptSecretIfFFisEnabled(ldapSettings);
      ldapSettings.encryptLdapInlineSecret(secretManager, false);
      EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
      return buildLdapSettingsWithEncryptedDataDetail(ldapSettings, encryptedDataDetail);
    } else {
      return getLdapSettingsWithEncryptedDataDetailFromInputSettings(accountId, inputLdapSettings);
    }
  }

  private LdapSettingsWithEncryptedDataDetail getLdapSettingsWithEncryptedDataDetailFromInputSettings(
      String accountId, LdapSettings inputLdapSettings) {
    boolean temporaryEncryption = !populateEncryptedFields(inputLdapSettings);
    encryptSecretIfFFisEnabled(inputLdapSettings);
    EncryptedDataDetail encryptedDataDetail = null;
    try {
      inputLdapSettings.encryptLdapInlineSecret(secretManager, true);
      encryptedDataDetail = inputLdapSettings.getEncryptedDataDetails(secretManager);
    } finally {
      if (null != encryptedDataDetail) {
        deleteTempSecret(temporaryEncryption, encryptedDataDetail, accountId);
      }
    }
    return buildLdapSettingsWithEncryptedDataDetail(inputLdapSettings, encryptedDataDetail);
  }

  private LdapSettingsWithEncryptedDataDetail buildLdapSettingsWithEncryptedDataDetail(
      LdapSettings ldapSettings, EncryptedDataDetail encryptedDataDetail) {
    return LdapSettingsWithEncryptedDataDetail.builder()
        .ldapSettings(LdapSettingsMapper.ldapSettingsDTO(ldapSettings))
        .encryptedDataDetail(encryptedDataDetail)
        .build();
  }

  @Override
  public SamlSettings getSamlSettings(@NotBlank String accountId) {
    return ssoSettingService.getSamlSettingsByAccountId(accountId);
  }

  @Override
  public SamlSettings getSamlSettings(@NotBlank String accountId, @NotNull String samlSSOId) {
    return ssoSettingService.getSamlSettingsByAccountIdAndUuid(accountId, samlSSOId);
  }

  @Override
  public LdapTestResponse validateLdapConnectionSettings(
      @NotNull LdapSettings ldapSettings, @NotBlank final String accountId) {
    populateEncryptedFields(ldapSettings);
    boolean temporaryEncryption = isNotEmpty(ldapSettings.getConnectionSettings().getBindPassword())
        && !ldapSettings.getConnectionSettings().getBindPassword().equals(LdapConstants.MASKED_STRING);
    encryptSecretIfFFisEnabled(ldapSettings);
    ldapSettings.encryptLdapInlineSecret(secretManager, false);
    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
    try {
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(accountId)
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      return delegateProxyFactory.getV2(LdapDelegateService.class, syncTaskContext)
          .validateLdapConnectionSettings(LdapSettingsMapper.ldapSettingsDTO(ldapSettings), encryptedDataDetail);
    } finally {
      deleteTempSecret(temporaryEncryption, encryptedDataDetail, accountId);
    }
  }

  @Override
  public LdapTestResponse validateLdapUserSettings(
      @NotNull LdapSettings ldapSettings, @NotBlank final String accountId) {
    populateEncryptedFields(ldapSettings);
    boolean temporaryEncryption = isNotEmpty(ldapSettings.getConnectionSettings().getBindPassword())
        && !ldapSettings.getConnectionSettings().getBindPassword().equals(LdapConstants.MASKED_STRING);
    encryptSecretIfFFisEnabled(ldapSettings);
    ldapSettings.encryptLdapInlineSecret(secretManager, false);
    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
    try {
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(accountId)
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      return delegateProxyFactory.getV2(LdapDelegateService.class, syncTaskContext)
          .validateLdapUserSettings(LdapSettingsMapper.ldapSettingsDTO(ldapSettings), encryptedDataDetail);
    } finally {
      deleteTempSecret(temporaryEncryption, encryptedDataDetail, accountId);
    }
  }

  @Override
  public LdapTestResponse validateLdapGroupSettings(
      @NotNull LdapSettings ldapSettings, @NotBlank final String accountId) {
    populateEncryptedFields(ldapSettings);
    boolean temporaryEncryption = isNotEmpty(ldapSettings.getConnectionSettings().getBindPassword())
        && !ldapSettings.getConnectionSettings().getBindPassword().equals(LdapConstants.MASKED_STRING);
    encryptSecretIfFFisEnabled(ldapSettings);
    ldapSettings.encryptLdapInlineSecret(secretManager, false);
    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
    try {
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(accountId)
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      return delegateProxyFactory.getV2(LdapDelegateService.class, syncTaskContext)
          .validateLdapGroupSettings(LdapSettingsMapper.ldapSettingsDTO(ldapSettings), encryptedDataDetail);
    } finally {
      deleteTempSecret(temporaryEncryption, encryptedDataDetail, accountId);
    }
  }

  @Override
  public LdapResponse validateLdapAuthentication(LdapSettings ldapSettings, String identifier, String password) {
    EncryptedDataDetail settingsEncryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
    SecretText secretText = SecretText.builder()
                                .value(password)
                                .hideFromListing(true)
                                .name(UUID.randomUUID().toString())
                                .scopedToAccount(true)
                                .build();
    String encryptedPassword = secretManager.saveSecretText(ldapSettings.getAccountId(), secretText, false);
    EncryptedDataDetail passwordEncryptedDataDetail =
        secretManager
            .encryptedDataDetails(ldapSettings.getAccountId(), LdapConstants.USER_PASSWORD_KEY, encryptedPassword, null)
            .get();
    try {
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(ldapSettings.getAccountId())
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      return delegateProxyFactory.getV2(LdapDelegateService.class, syncTaskContext)
          .authenticate(LdapSettingsMapper.ldapSettingsDTO(ldapSettings), settingsEncryptedDataDetail, identifier,
              passwordEncryptedDataDetail);
    } finally {
      secretManager.deleteSecret(ldapSettings.getAccountId(), passwordEncryptedDataDetail.getEncryptedData().getUuid(),
          new HashMap<>(), false);
    }
  }

  @Override
  public Collection<LdapGroupResponse> searchGroupsByName(@NotBlank String ldapSettingsId, @NotBlank String nameQuery) {
    LdapSettings ldapSettings = ssoSettingService.getLdapSettingsByUuid(ldapSettingsId);
    if (null == ldapSettings) {
      throw new InvalidRequestException("Invalid Ldap Settings ID.");
    }

    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
    SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                          .accountId(ldapSettings.getAccountId())
                                          .appId(GLOBAL_APP_ID)
                                          .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                          .build();
    return delegateProxyFactory.getV2(LdapDelegateService.class, syncTaskContext)
        .searchGroupsByName(LdapSettingsMapper.ldapSettingsDTO(ldapSettings), encryptedDataDetail, nameQuery);
  }

  private boolean isExistingSetting(@NotNull LdapSettings settings) {
    if (isNotEmpty(settings.getUuid())) {
      if (!ssoSettingService.isLdapSettingsPresent(settings.getUuid())) {
        throw new InvalidRequestException("Invalid Ldap Settings ID.");
      }
      return true;
    }
    return false;
  }

  private boolean populateEncryptedFields(@NotNull LdapSettings settings) {
    if (!isExistingSetting(settings)) {
      if (isEmpty(settings.getConnectionSettings().getBindDN())) {
        return false;
      }
      if (settings.getConnectionSettings().getBindPassword().equals(LdapConstants.MASKED_STRING)) {
        throw new InvalidRequestException("Invalid password.");
      }
      return false;
    }
    if (!LdapConstants.MASKED_STRING.equals(settings.getConnectionSettings().getBindPassword())) {
      return false;
    }
    LdapSettings savedSettings = ssoSettingService.getLdapSettingsByUuid(settings.getUuid());
    if (isEmpty(savedSettings.getConnectionSettings().getPasswordType())
        || LdapConnectionSettings.INLINE_SECRET.equals(savedSettings.getConnectionSettings().getPasswordType())) {
      settings.getConnectionSettings().setEncryptedBindPassword(
          savedSettings.getConnectionSettings().getEncryptedBindPassword());
    } else {
      settings.getConnectionSettings().setEncryptedBindSecret(
          savedSettings.getConnectionSettings().getEncryptedBindSecret());
    }
    return true;
  }

  @Override
  public SSOConfig deleteOauthConfiguration(String accountId) {
    ssoSettingService.deleteOauthSettings(accountId);
    return setAuthenticationMechanism(accountId, USER_PASSWORD, false);
  }

  @Override
  public OauthSettings updateOauthSettings(String accountId, String filter, Set<OauthProviderType> allowedProviders) {
    return ssoSettingService.updateOauthSettings(accountId, filter, allowedProviders);
  }

  @Override
  public List<Long> getIterationsFromCron(String accountId, String cron) {
    return ssoSettingService.getIterationsFromCron(accountId, cron);
  }

  @Override
  public LdapSettingsWithEncryptedDataAndPasswordDetail getLdapSettingsWithEncryptedDataAndPasswordDetail(
      String accountId, String password) {
    LdapSettingsWithEncryptedDataDetail settingWithEncryptedDataDetail =
        getLdapSettingWithEncryptedDataDetail(accountId, null);
    SecretText secretText = SecretText.builder()
                                .value(password)
                                .hideFromListing(true)
                                .name(UUID.randomUUID().toString())
                                .scopedToAccount(true)
                                .kmsId(accountId) // for local encryption
                                .build();
    String encryptedPassword = secretManager.saveSecretText(accountId, secretText, false);
    EncryptedDataDetail encryptedPwdDataDetail = null;
    try {
      Optional<EncryptedDataDetail> optionalEncryptedDataDetail =
          secretManager.encryptedDataDetails(accountId, LdapConstants.USER_PASSWORD_KEY, encryptedPassword, null);
      if (optionalEncryptedDataDetail.isPresent()) {
        encryptedPwdDataDetail = optionalEncryptedDataDetail.get();
      }
    } finally {
      if (null != encryptedPwdDataDetail && null != encryptedPwdDataDetail.getEncryptedData()) {
        secretManager.deleteSecret(
            accountId, encryptedPwdDataDetail.getEncryptedData().getUuid(), new HashMap<>(), false);
      }
    }
    return LdapSettingsWithEncryptedDataAndPasswordDetail.builder()
        .ldapSettings(settingWithEncryptedDataDetail.getLdapSettings())
        .encryptedDataDetail(settingWithEncryptedDataDetail.getEncryptedDataDetail())
        .encryptedPwdDataDetail(encryptedPwdDataDetail)
        .build();
  }

  private boolean deleteSamlSettings(String accountId, String targetAccountType) {
    boolean samlSettingsDeleted = true;
    SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
    if (samlSettings != null) {
      log.info("Deleting SAML SSO Settings for accountId={} and targetAccountType={}", accountId, targetAccountType);
      samlSettingsDeleted = ssoSettingService.deleteSamlSettings(samlSettings);
    }
    return samlSettingsDeleted;
  }

  private void setOauthIfSetAfterSSODelete(String accountId) {
    OauthSettings oauthSettings = ssoSettingService.getOauthSettingsByAccountId(accountId);
    if (oauthSettings != null) {
      log.info("Setting Oauth enabled to true for account {} after SSO settings delete", accountId);
      Account account = accountService.get(accountId);
      account.setOauthEnabled(true);
      accountService.update(account);
    }
  }

  private void checkIfOperationIsAllowed(String accountId, AuthenticationMechanism authenticationMechanism) {
    if (authenticationMechanism == AuthenticationMechanism.LDAP && !ldapFeature.isAvailableForAccount(accountId)
        || authenticationMechanism == AuthenticationMechanism.SAML && !samlFeature.isAvailableForAccount(accountId)) {
      throw new InvalidRequestException(String.format("Operation not permitted for account [%s]", accountId), USER);
    }
  }

  private SAMLProviderType getSAMLProviderType(String samlProviderType) {
    if (isNotEmpty(samlProviderType)) {
      switch (samlProviderType.toUpperCase()) {
        case "AZURE":
          return SAMLProviderType.AZURE;
        case "OKTA":
          return SAMLProviderType.OKTA;
        case "ONELOGIN":
          return SAMLProviderType.ONELOGIN;
        default:
          return SAMLProviderType.OTHER;
      }
    }

    return SAMLProviderType.OTHER;
  }

  private void encryptSecretIfFFisEnabled(@NotNull LdapSettings ldapSettings) {
    ssoServiceHelper.encryptLdapSecret(
        ldapSettings.getConnectionSettings(), secretManager, ldapSettings.getAccountId());
  }

  private SSOConfig updateAndGetSamlSsoConfigInternal(String groupMembershipAttr, Boolean authorizationEnabled,
      InputStream inputStream, SamlSettings settings, String displayName, String clientId, char[] clientSecret,
      String accountId, String logoutUrl, String entityIdentifier, String samlProviderType, String friendlySamlName,
      boolean isNGSSO, boolean jitEnabled, String jitValidationKey, String jitValidationValue)
      throws IOException, SamlException, URISyntaxException {
    String fileAsString =
        null != inputStream ? IOUtils.toString(inputStream, Charset.defaultCharset()) : settings.getMetaDataFile();
    groupMembershipAttr = authorizationEnabled ? groupMembershipAttr : null;

    if (isEmpty(displayName)) {
      displayName = settings.getDisplayName();
    }
    if (isNotEmpty(clientId) && isNotEmpty(clientSecret)
        && SECRET_MASK.equals(String.valueOf(clientSecret))) { // suggests only clientId updated
      // set the old cg secret ref
      final String oldClientSecretRef = settings.getEncryptedClientSecret();
      clientSecret = isNotEmpty(oldClientSecretRef) ? oldClientSecretRef.toCharArray() : clientSecret;
    }

    buildAndUploadSamlSettings(accountId, fileAsString, displayName, groupMembershipAttr, logoutUrl, entityIdentifier,
        samlProviderType, clientId, clientSecret, friendlySamlName, isNGSSO, true, settings.getUuid(), jitEnabled,
        jitValidationKey, jitValidationValue);
    return getAccountAccessManagementSettings(accountId, isNGSSO);
  }

  public void deleteTempSecret(boolean temporaryEncryption, EncryptedDataDetail encryptedDataDetail, String accountId) {
    if (temporaryEncryption) {
      secretManager.deleteSecret(accountId, encryptedDataDetail.getEncryptedData().getUuid(), new HashMap<>(), false);
    }
  }
}
