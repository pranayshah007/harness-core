/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessModule._950_NG_AUTHENTICATION_SERVICE;
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
import io.harness.beans.FeatureName;
import io.harness.beans.SecretText;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.OauthProviderType;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.Account;
import software.wings.beans.Event;
import software.wings.beans.SyncTaskContext;
import software.wings.beans.sso.LdapConnectionSettings;
import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettingsMapper;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SAMLProviderType;
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

  @Override
  public SSOConfig uploadSamlConfiguration(String accountId, InputStream inputStream, String displayName,
      String groupMembershipAttr, Boolean authorizationEnabled, String logoutUrl, String entityIdentifier,
      String samlProviderType, String clientId, char[] clientSecret) {
    try {
      String fileAsString = IOUtils.toString(inputStream, Charset.defaultCharset());
      groupMembershipAttr = authorizationEnabled ? groupMembershipAttr : null;
      buildAndUploadSamlSettings(accountId, fileAsString, displayName, groupMembershipAttr, logoutUrl, entityIdentifier,
          samlProviderType, clientId, clientSecret);
      return getAccountAccessManagementSettings(accountId);
    } catch (SamlException | IOException | URISyntaxException e) {
      throw new WingsException(ErrorCode.INVALID_SAML_CONFIGURATION, e);
    }
  }

  @Override
  public SSOConfig uploadOauthConfiguration(String accountId, String filter, Set<OauthProviderType> allowedProviders) {
    if (isEmpty(allowedProviders)) {
      throw new InvalidRequestException("At least one OAuth provider must be selected.");
    }
    buildAndUploadOauthSettings(accountId, filter, allowedProviders);
    return getAccountAccessManagementSettings(accountId);
  }

  @Override
  public SSOConfig updateSamlConfiguration(String accountId, InputStream inputStream, String displayName,
      String groupMembershipAttr, Boolean authorizationEnabled, String logoutUrl, String entityIdentifier,
      String samlProviderType, String clientId, char[] clientSecret) {
    try {
      SamlSettings settings = ssoSettingService.getSamlSettingsByAccountId(accountId);
      String fileAsString;

      groupMembershipAttr = authorizationEnabled ? groupMembershipAttr : null;

      if (null != inputStream) {
        fileAsString = IOUtils.toString(inputStream, Charset.defaultCharset());
      } else {
        fileAsString = settings.getMetaDataFile();
      }

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
          samlProviderType, clientId, clientSecret);
      return getAccountAccessManagementSettings(accountId);
    } catch (SamlException | IOException | URISyntaxException e) {
      throw new WingsException(ErrorCode.INVALID_SAML_CONFIGURATION, e);
    }
  }

  @Override
  public SSOConfig updateLogoutUrlSamlSettings(String accountId, String logoutUrl) {
    log.info("Logout url being set from API is {}", logoutUrl);
    SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
    if (samlSettings != null) {
      samlSettings.setLogoutUrl(logoutUrl);
      ssoSettingService.saveSamlSettings(samlSettings);
    } else {
      throw new InvalidRequestException("Cannot update Logout URL as no SAML Config exists for your account");
    }
    return getAccountAccessManagementSettings(accountId);
  }

  @Override
  public SSOConfig deleteSamlConfiguration(String accountId) {
    ssoSettingService.deleteSamlSettings(accountId);
    SSOConfig ssoConfig = setAuthenticationMechanism(accountId, USER_PASSWORD);
    setOauthIfSetAfterSSODelete(accountId);
    return ssoConfig;
  }

  private void auditSSOActivity(
      String accountId, AuthenticationMechanism mechanism, AuthenticationMechanism currentAuthMechanism) {
    boolean createAudit = false;
    boolean enableFlag = false;
    SSOSettings ssoSettings = null;
    if (mechanism == SAML && currentAuthMechanism == USER_PASSWORD) {
      createAudit = true;
      enableFlag = true;
      ssoSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
    } else if (currentAuthMechanism == SAML && mechanism == USER_PASSWORD) {
      createAudit = true;
      ssoSettings = ssoSettingService.getSamlSettingsByAccountId(accountId);
    } else if (currentAuthMechanism == USER_PASSWORD && mechanism == LDAP) {
      createAudit = true;
      ssoSettings = ssoSettingService.getLdapSettingsByAccountId(accountId);
      enableFlag = true;
    } else if (currentAuthMechanism == LDAP && mechanism == USER_PASSWORD) {
      createAudit = true;
      ssoSettings = ssoSettingService.getLdapSettingsByAccountId(accountId);
    }
    if (createAudit) {
      if (enableFlag) {
        auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, ssoSettings, Event.Type.ENABLE);
      } else {
        auditServiceHelper.reportForAuditingUsingAccountId(accountId, null, ssoSettings, Event.Type.DISABLE);
      }
    }
  }

  @Override
  public SSOConfig setAuthenticationMechanism(String accountId, AuthenticationMechanism mechanism) {
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
    if (shouldUpdateAuthMechanism) {
      if (featureFlagService.isEnabled(FeatureName.AUDIT_TRAIL_ENHANCEMENT, accountId)) {
        auditSSOActivity(accountId, mechanism, currentAuthMechanism);
      }
      account.setAuthenticationMechanism(mechanism);
    }
    accountService.update(account);
    return getAccountAccessManagementSettings(accountId);
  }

  @Override
  public SSOConfig getAccountAccessManagementSettings(String accountId) {
    // We are handling the check programmatically for now, since we don't have enough info in the query / path
    // parameters
    authorizeAccessManagementCall();
    Account account = accountService.get(accountId);
    return SSOConfig.builder()
        .accountId(accountId)
        .authenticationMechanism(account.getAuthenticationMechanism())
        .ssoSettings(getSSOSettings(account))
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
    SamlSettings samlSettings = ssoSettingService.getSamlSettingsByAccountId(account.getUuid());
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

  private SamlSettings buildAndUploadSamlSettings(String accountId, String fileAsString, String displayName,
      String groupMembershipAttr, String logoutUrl, String entityIdentifier, String samlProviderType, String clientId,
      char[] clientSecret) throws SamlException, URISyntaxException {
    SamlClient samlClient = samlClientService.getSamlClient(entityIdentifier, fileAsString);

    SamlSettings samlSettings = SamlSettings.builder()
                                    .metaDataFile(fileAsString)
                                    .url(samlClient.getIdentityProviderUrl())
                                    .accountId(accountId)
                                    .displayName(displayName)
                                    .origin(new URI(samlClient.getIdentityProviderUrl()).getHost())
                                    .groupMembershipAttr(groupMembershipAttr)
                                    .entityIdentifier(entityIdentifier)
                                    .build();

    samlSettings.setSamlProviderType(getSAMLProviderType(samlProviderType));
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
    return ssoSettingService.saveSamlSettings(samlSettings);
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
      setAuthenticationMechanism(accountId, USER_PASSWORD);
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
  public SamlSettings getSamlSettings(@NotBlank String accountId) {
    return ssoSettingService.getSamlSettingsByAccountId(accountId);
  }

  @Override
  public LdapTestResponse validateLdapConnectionSettings(
      @NotNull LdapSettings ldapSettings, @NotBlank final String accountId) {
    boolean temporaryEncryption = !populateEncryptedFields(ldapSettings);
    encryptSecretIfFFisEnabled(ldapSettings);
    ldapSettings.encryptLdapInlineSecret(secretManager);
    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
    try {
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(accountId)
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      return delegateProxyFactory.get(LdapDelegateService.class, syncTaskContext)
          .validateLdapConnectionSettings(LdapSettingsMapper.ldapSettingsDTO(ldapSettings), encryptedDataDetail);
    } finally {
      deleteTempSecret(temporaryEncryption, encryptedDataDetail, ldapSettings, accountId);
    }
  }

  @Override
  public LdapTestResponse validateLdapUserSettings(
      @NotNull LdapSettings ldapSettings, @NotBlank final String accountId) {
    boolean temporaryEncryption = !populateEncryptedFields(ldapSettings);
    encryptSecretIfFFisEnabled(ldapSettings);
    ldapSettings.encryptLdapInlineSecret(secretManager);
    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
    try {
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(accountId)
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      return delegateProxyFactory.get(LdapDelegateService.class, syncTaskContext)
          .validateLdapUserSettings(LdapSettingsMapper.ldapSettingsDTO(ldapSettings), encryptedDataDetail);
    } finally {
      deleteTempSecret(temporaryEncryption, encryptedDataDetail, ldapSettings, accountId);
    }
  }

  @Override
  public LdapTestResponse validateLdapGroupSettings(
      @NotNull LdapSettings ldapSettings, @NotBlank final String accountId) {
    boolean temporaryEncryption = !populateEncryptedFields(ldapSettings);
    encryptSecretIfFFisEnabled(ldapSettings);
    ldapSettings.encryptLdapInlineSecret(secretManager);
    EncryptedDataDetail encryptedDataDetail = ldapSettings.getEncryptedDataDetails(secretManager);
    try {
      SyncTaskContext syncTaskContext = SyncTaskContext.builder()
                                            .accountId(accountId)
                                            .appId(GLOBAL_APP_ID)
                                            .timeout(DEFAULT_SYNC_CALL_TIMEOUT)
                                            .build();
      return delegateProxyFactory.get(LdapDelegateService.class, syncTaskContext)
          .validateLdapGroupSettings(LdapSettingsMapper.ldapSettingsDTO(ldapSettings), encryptedDataDetail);
    } finally {
      deleteTempSecret(temporaryEncryption, encryptedDataDetail, ldapSettings, accountId);
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
      return delegateProxyFactory.get(LdapDelegateService.class, syncTaskContext)
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
    return delegateProxyFactory.get(LdapDelegateService.class, syncTaskContext)
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
    if (!settings.getConnectionSettings().getBindPassword().equals(LdapConstants.MASKED_STRING)) {
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
    return setAuthenticationMechanism(accountId, USER_PASSWORD);
  }

  @Override
  public OauthSettings updateOauthSettings(String accountId, String filter, Set<OauthProviderType> allowedProviders) {
    return ssoSettingService.updateOauthSettings(accountId, filter, allowedProviders);
  }

  @Override
  public List<Long> getIterationsFromCron(String accountId, String cron) {
    return ssoSettingService.getIterationsFromCron(accountId, cron);
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
    if (featureFlagService.isEnabled(FeatureName.LDAP_SECRET_AUTH, ldapSettings.getAccountId())) {
      ssoServiceHelper.encryptLdapSecret(
          ldapSettings.getConnectionSettings(), secretManager, ldapSettings.getAccountId());
    }
  }

  private void deleteTempSecret(boolean temporaryEncryption, EncryptedDataDetail encryptedDataDetail,
      LdapSettings ldapSettings, String accountId) {
    if (!featureFlagService.isEnabled(FeatureName.LDAP_SECRET_AUTH, ldapSettings.getAccountId())) {
      if (temporaryEncryption) {
        secretManager.deleteSecret(accountId, encryptedDataDetail.getEncryptedData().getUuid(), new HashMap<>(), false);
      }
    } else {
      if (temporaryEncryption
          && LdapConnectionSettings.INLINE_SECRET.equals(ldapSettings.getConnectionSettings().getPasswordType())) {
        secretManager.deleteSecret(accountId, encryptedDataDetail.getEncryptedData().getUuid(), new HashMap<>(), false);
      }
    }
  }
}
