/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings.impl;

import static io.harness.beans.FeatureName.PL_ENABLE_MULTIPLE_IDP_SUPPORT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.authenticationservice.beans.SAMLProviderType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.enforcement.client.annotation.FeatureRestrictionCheck;
import io.harness.enforcement.client.services.EnforcementClientService;
import io.harness.enforcement.constants.FeatureRestrictionName;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.authenticationsettings.dtos.AuthenticationSettingsResponse;
import io.harness.ng.authenticationsettings.dtos.mechanisms.LDAPSettings;
import io.harness.ng.authenticationsettings.dtos.mechanisms.NGAuthSettings;
import io.harness.ng.authenticationsettings.dtos.mechanisms.OAuthSettings;
import io.harness.ng.authenticationsettings.dtos.mechanisms.SAMLSettings;
import io.harness.ng.authenticationsettings.dtos.mechanisms.UsernamePasswordSettings;
import io.harness.ng.authenticationsettings.remote.AuthSettingsManagerClient;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.api.UserGroupService;
import io.harness.ng.core.user.SessionTimeoutSettings;
import io.harness.ng.core.user.TwoFactorAdminOverrideSettings;
import io.harness.utils.NGFeatureFlagHelperService;

import software.wings.beans.loginSettings.LoginSettings;
import software.wings.beans.loginSettings.PasswordStrengthPolicy;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlSettings;
import software.wings.security.authentication.LoginTypeResponse;
import software.wings.security.authentication.SSOConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PL)
public class AuthenticationSettingsServiceImpl implements AuthenticationSettingsService {
  private final AuthSettingsManagerClient managerClient;
  private final EnforcementClientService enforcementClientService;
  private final UserGroupService userGroupService;
  private final NGFeatureFlagHelperService ngFeatureFlagHelperService;

  @Override
  public AuthenticationSettingsResponse getAuthenticationSettings(String accountIdentifier) {
    // when FF is enabled, avoid using this API which can result in discrepancy of SAML setting update
    if (ngFeatureFlagHelperService.isEnabled(accountIdentifier, PL_ENABLE_MULTIPLE_IDP_SUPPORT)) {
      throw new InvalidRequestException(String.format(
          "Multiple IdP support FF 'PL_ENABLE_MULTIPLE_IDP_SUPPORT' enabled on account %s. Please use v2 version of API endpoint: "
              + "'authentication-settings/v2' to list all configured authentication settings on account",
          accountIdentifier));
    }
    Set<String> whitelistedDomains = getResponse(managerClient.getWhitelistedDomains(accountIdentifier));
    log.info("Whitelisted domains for accountId {}: {}", accountIdentifier, whitelistedDomains);
    SSOConfig ssoConfig = getResponse(managerClient.getAccountAccessManagementSettings(accountIdentifier));
    return buildAndReturnAuthenticationSettingsResponse(ssoConfig, accountIdentifier, whitelistedDomains);
  }

  @Override
  public AuthenticationSettingsResponse getAuthenticationSettingsV2(String accountIdentifier) {
    Set<String> whitelistedDomains = getResponse(managerClient.getWhitelistedDomains(accountIdentifier));
    log.info("Whitelisted domains for accountId {}: {}", accountIdentifier, whitelistedDomains);
    SSOConfig ssoConfig = getResponse(managerClient.getAccountAccessManagementSettingsV2(accountIdentifier));
    return buildAndReturnAuthenticationSettingsResponse(ssoConfig, accountIdentifier, whitelistedDomains);
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.OAUTH_SUPPORT)
  public void updateOauthProviders(@AccountIdentifier String accountId, OAuthSettings oAuthSettings) {
    getResponse(managerClient.uploadOauthSettings(accountId,
        OauthSettings.builder()
            .allowedProviders(oAuthSettings.getAllowedProviders())
            .filter(oAuthSettings.getFilter())
            .accountId(accountId)
            .build()));
  }

  @Override
  public void updateAuthMechanism(String accountId, AuthenticationMechanism authenticationMechanism) {
    checkLicenseEnforcement(accountId, authenticationMechanism);
    boolean updateAuth = true;
    if (AuthenticationMechanism.SAML == authenticationMechanism
        && ngFeatureFlagHelperService.isEnabled(accountId, PL_ENABLE_MULTIPLE_IDP_SUPPORT)) {
      SSOConfig ssoConfig = getResponse(managerClient.getAccountAccessManagementSettingsV2(accountId));
      if (null != ssoConfig && isNotEmpty(ssoConfig.getSsoSettings())) {
        updateAuth = ssoConfig.getSsoSettings()
                         .stream()
                         .filter(Objects::nonNull)
                         .filter(ssoSetting -> ssoSetting.getType() == SSOType.SAML)
                         .noneMatch(setting -> ((SamlSettings) setting).isAuthenticationEnabled());
      }
    }
    if (!updateAuth) {
      throw new InvalidRequestException(String.format(
          "Cannot update authentication mechanism for account %s to SAML as no SAML SSO setting has authentication enabled. Please enable authentication for at least one SAML setting"
              + " and then update account level authentication mechanism to SAML",
          accountId));
    }
    getResponse(managerClient.updateAuthMechanism(accountId, authenticationMechanism));
  }

  private void checkLicenseEnforcement(String accountId, AuthenticationMechanism authenticationMechanism) {
    switch (authenticationMechanism) {
      case OAUTH:
        enforcementClientService.checkAvailability(FeatureRestrictionName.OAUTH_SUPPORT, accountId);
        break;
      case SAML:
        enforcementClientService.checkAvailability(FeatureRestrictionName.SAML_SUPPORT, accountId);
        break;
      case LDAP:
        enforcementClientService.checkAvailability(FeatureRestrictionName.LDAP_SUPPORT, accountId);
        break;
      default:
        break;
    }
  }

  @Override
  public void removeOauthMechanism(String accountId) {
    getResponse(managerClient.deleteOauthSettings(accountId));
  }

  @Override
  public LoginSettings updateLoginSettings(
      String loginSettingsId, String accountIdentifier, LoginSettings loginSettings) {
    return getResponse(managerClient.updateLoginSettings(loginSettingsId, accountIdentifier, loginSettings));
  }

  @Override
  public void updateWhitelistedDomains(String accountIdentifier, Set<String> whitelistedDomains) {
    getResponse(managerClient.updateWhitelistedDomains(accountIdentifier, whitelistedDomains));
  }

  private List<NGAuthSettings> buildAuthSettingsList(SSOConfig ssoConfig, String accountIdentifier) {
    List<NGAuthSettings> settingsList = getNGAuthSettings(ssoConfig);

    LoginSettings loginSettings = getResponse(managerClient.getUserNamePasswordSettings(accountIdentifier));
    settingsList.add(UsernamePasswordSettings.builder().loginSettings(loginSettings).build());

    return settingsList;
  }

  private List<NGAuthSettings> getNGAuthSettings(SSOConfig ssoConfig) {
    List<SSOSettings> ssoSettings = ssoConfig.getSsoSettings();
    List<NGAuthSettings> result = new ArrayList<>();

    if (EmptyPredicate.isEmpty(ssoSettings)) {
      return result;
    }

    for (SSOSettings ssoSetting : ssoSettings) {
      if (ssoSetting.getType().equals(SSOType.SAML)) {
        SamlSettings samlSettings = (SamlSettings) ssoSetting;
        SAMLSettings samlSettingsBuilt = SAMLSettings.builder()
                                             .identifier(samlSettings.getUuid())
                                             .groupMembershipAttr(samlSettings.getGroupMembershipAttr())
                                             .logoutUrl(samlSettings.getLogoutUrl())
                                             .origin(samlSettings.getOrigin())
                                             .displayName(samlSettings.getDisplayName())
                                             .authorizationEnabled(samlSettings.isAuthorizationEnabled())
                                             .entityIdentifier(samlSettings.getEntityIdentifier())
                                             .friendlySamlName(samlSettings.getFriendlySamlName())
                                             .authenticationEnabled(samlSettings.isAuthenticationEnabled())
                                             .build();

        if (null != samlSettings.getSamlProviderType()) {
          samlSettingsBuilt.setSamlProviderType(samlSettings.getSamlProviderType().name());
        } else {
          samlSettingsBuilt.setSamlProviderType(SAMLProviderType.OTHER.name());
        }
        if (isNotEmpty(samlSettings.getClientId()) && isNotEmpty(samlSettings.getEncryptedClientSecret())) {
          samlSettingsBuilt.setClientId(samlSettings.getClientId());
          samlSettingsBuilt.setClientSecret(SECRET_MASK); // setting to a masked value for clientSecret
        } else if (isNotEmpty(samlSettings.getClientId()) && isEmpty(samlSettings.getEncryptedClientSecret())
            || isEmpty(samlSettings.getClientId()) && isNotEmpty(samlSettings.getEncryptedClientSecret())) {
          throw new InvalidRequestException(
              "Both clientId and clientSecret needs to be present together in SAML setting", WingsException.USER);
        }

        result.add(samlSettingsBuilt);
      } else if (ssoSetting.getType().equals(SSOType.OAUTH)) {
        OauthSettings oAuthSettings = (OauthSettings) ssoSetting;
        result.add(OAuthSettings.builder()
                       .allowedProviders(oAuthSettings.getAllowedProviders())
                       .filter(oAuthSettings.getFilter())
                       .build());
      } else if (ssoSetting.getType().equals(SSOType.LDAP)) {
        LdapSettings ldapSettings = (LdapSettings) ssoSetting;
        result.add(LDAPSettings.builder()
                       .identifier(ldapSettings.getUuid())
                       .connectionSettings(ldapSettings.getConnectionSettings())
                       .userSettingsList(ldapSettings.getUserSettingsList())
                       .groupSettingsList(ldapSettings.getGroupSettingsList())
                       .displayName(ldapSettings.getDisplayName())
                       .cronExpression(ldapSettings.getCronExpression())
                       .nextIterations(ldapSettings.getNextIterations())
                       .disabled(ldapSettings.isDisabled())
                       .build());
      }
    }
    return result;
  }

  private RequestBody createPartFromString(String string) {
    if (string == null) {
      return null;
    }
    return RequestBody.create(MultipartBody.FORM, string);
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.SAML_SUPPORT)
  public SSOConfig uploadSAMLMetadata(@NotNull @AccountIdentifier String accountId,
      @NotNull MultipartBody.Part inputStream, @NotNull String displayName, String groupMembershipAttr,
      @NotNull Boolean authorizationEnabled, String logoutUrl, String entityIdentifier, String samlProviderType,
      String clientId, String clientSecret, String friendlySamlName) {
    SamlSettings samlSettings = getResponse(managerClient.getSAMLMetadata(accountId));
    if (samlSettings != null && !ngFeatureFlagHelperService.isEnabled(accountId, PL_ENABLE_MULTIPLE_IDP_SUPPORT)) {
      throw new InvalidRequestException(String.format(
          "Multiple Saml settings cannot be created for account %s. Please enable FF PL_ENABLE_MULTIPLE_IDP_SUPPORT on account"
              + " for Multiple IdP support",
          accountId));
    }
    RequestBody displayNamePart = createPartFromString(displayName);
    RequestBody groupMembershipAttrPart = createPartFromString(groupMembershipAttr);
    RequestBody authorizationEnabledPart = createPartFromString(String.valueOf(authorizationEnabled));
    RequestBody logoutUrlPart = createPartFromString(logoutUrl);
    RequestBody entityIdentifierPart = createPartFromString(entityIdentifier);
    RequestBody samlProviderTypePart = createPartFromString(samlProviderType);
    RequestBody clientIdPart = createPartFromString(clientId);
    RequestBody clientSecretPart = createPartFromString(clientSecret);
    RequestBody friendlySamlNamePart = createPartFromString(friendlySamlName);
    return getResponse(managerClient.uploadSAMLMetadata(accountId, inputStream, displayNamePart,
        groupMembershipAttrPart, authorizationEnabledPart, logoutUrlPart, entityIdentifierPart, samlProviderTypePart,
        clientIdPart, clientSecretPart, friendlySamlNamePart));
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.SAML_SUPPORT)
  public SSOConfig updateSAMLMetadata(@NotNull @AccountIdentifier String accountId, MultipartBody.Part inputStream,
      String displayName, String groupMembershipAttr, @NotNull Boolean authorizationEnabled, String logoutUrl,
      String entityIdentifier, String samlProviderType, String clientId, String clientSecret) {
    // when FF is enabled, avoid using this API, which can result in discrepancy of SAML setting update
    checkMultipleIdpSupportFF(accountId, "update");
    if (ngFeatureFlagHelperService.isEnabled(accountId, PL_ENABLE_MULTIPLE_IDP_SUPPORT)) {
      throw new InvalidRequestException(String.format(
          "Multiple IdP support FF 'PL_ENABLE_MULTIPLE_IDP_SUPPORT' enabled on account %s. Please update on a samlSSOId API endpoint: "
              + "'saml-metadata-upload/{samlSSOId}' to update a SAML setting when Multiple IdP support is enabled on account",
          accountId));
    }
    RequestBody displayNamePart = createPartFromString(displayName);
    RequestBody groupMembershipAttrPart = createPartFromString(groupMembershipAttr);
    RequestBody authorizationEnabledPart = createPartFromString(String.valueOf(authorizationEnabled));
    RequestBody logoutUrlPart = createPartFromString(logoutUrl);
    RequestBody entityIdentifierPart = createPartFromString(entityIdentifier);
    RequestBody samlProviderTypePart = createPartFromString(samlProviderType);
    RequestBody clientIdPart = createPartFromString(clientId);
    RequestBody clientSecretPart = createPartFromString(clientSecret);
    return getResponse(managerClient.updateSAMLMetadata(accountId, inputStream, displayNamePart,
        groupMembershipAttrPart, authorizationEnabledPart, logoutUrlPart, entityIdentifierPart, samlProviderTypePart,
        clientIdPart, clientSecretPart));
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.SAML_SUPPORT)
  public SSOConfig updateSAMLMetadata(@NotNull @AccountIdentifier String accountId, @NotNull String samlSSOId,
      MultipartBody.Part inputStream, String displayName, String groupMembershipAttr,
      @NotNull Boolean authorizationEnabled, String logoutUrl, String entityIdentifier, String samlProviderType,
      String clientId, String clientSecret, String friendlySamlName) {
    RequestBody displayNamePart = createPartFromString(displayName);
    RequestBody groupMembershipAttrPart = createPartFromString(groupMembershipAttr);
    RequestBody authorizationEnabledPart = createPartFromString(String.valueOf(authorizationEnabled));
    RequestBody logoutUrlPart = createPartFromString(logoutUrl);
    RequestBody entityIdentifierPart = createPartFromString(entityIdentifier);
    RequestBody samlProviderTypePart = createPartFromString(samlProviderType);
    RequestBody clientIdPart = createPartFromString(clientId);
    RequestBody clientSecretPart = createPartFromString(clientSecret);
    RequestBody friendlySamlNamePart = createPartFromString(friendlySamlName);
    return getResponse(managerClient.updateSAMLMetadata(accountId, samlSSOId, inputStream, displayNamePart,
        groupMembershipAttrPart, authorizationEnabledPart, logoutUrlPart, entityIdentifierPart, samlProviderTypePart,
        clientIdPart, clientSecretPart, friendlySamlNamePart));
  }

  @Override
  public SSOConfig deleteSAMLMetadata(@NotNull @AccountIdentifier String accountIdentifier) {
    // when FF is enabled, avoid using this API, which can result in discrepancy of SAML setting update
    checkMultipleIdpSupportFF(accountIdentifier, "delete");
    SamlSettings samlSettings = getResponse(managerClient.getSAMLMetadata(accountIdentifier));
    if (samlSettings == null) {
      throw new InvalidRequestException("No Saml Metadata found for this account");
    }
    if (isNotEmpty(userGroupService.getUserGroupsBySsoId(accountIdentifier, samlSettings.getUuid()))) {
      throw new InvalidRequestException(
          "Deleting Saml provider with linked user groups is not allowed. Unlink the user groups first");
    }
    return getResponse(managerClient.deleteSAMLMetadata(accountIdentifier));
  }

  @Override
  public SSOConfig deleteSAMLMetadata(@NotNull @AccountIdentifier String accountIdentifier, @NotNull String samlSSOId) {
    SamlSettings samlSettings = getResponse(managerClient.getSAMLMetadata(accountIdentifier, samlSSOId));
    if (samlSettings == null) {
      throw new InvalidRequestException(
          String.format("No Saml Metadata found for account %s and saml sso id %s", accountIdentifier, samlSSOId));
    }
    if (isNotEmpty(userGroupService.getUserGroupsBySsoId(accountIdentifier, samlSSOId))) {
      throw new InvalidRequestException(String.format(
          "Deleting Saml setting having id %s with linked user groups is not allowed in account %s. Unlink the user groups first",
          samlSSOId, accountIdentifier));
    }
    return getResponse(managerClient.deleteSAMLMetadata(accountIdentifier, samlSSOId));
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.SAML_SUPPORT)
  public LoginTypeResponse getSAMLLoginTest(@NotNull @AccountIdentifier String accountIdentifier) {
    return getResponse(managerClient.getSAMLLoginTest(accountIdentifier));
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.SAML_SUPPORT)
  public LoginTypeResponse getSAMLLoginTestV2(
      @NotNull @AccountIdentifier String accountIdentifier, @NotNull String samlSSOId) {
    return getResponse(managerClient.getSAMLLoginTestV2(accountIdentifier, samlSSOId));
  }

  @Override
  public boolean setSessionTimeoutAtAccountLevel(
      @AccountIdentifier String accountIdentifier, SessionTimeoutSettings sessionTimeoutSettings) {
    return getResponse(managerClient.setSessionTimeoutAtAccountLevel(accountIdentifier, sessionTimeoutSettings));
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.TWO_FACTOR_AUTH_SUPPORT)
  public boolean setTwoFactorAuthAtAccountLevel(
      @AccountIdentifier String accountIdentifier, TwoFactorAdminOverrideSettings twoFactorAdminOverrideSettings) {
    return getResponse(managerClient.setTwoFactorAuthAtAccountLevel(accountIdentifier, twoFactorAdminOverrideSettings));
  }

  @Override
  public PasswordStrengthPolicy getPasswordStrengthSettings(String accountIdentifier) {
    return getResponse(managerClient.getPasswordStrengthSettings(accountIdentifier));
  }

  @Override
  public LDAPSettings getLdapSettings(String accountIdentifier) {
    log.info("NGLDAP: Get ldap settings call for accountId {}", accountIdentifier);
    return fromCGLdapSettings(getResponse(managerClient.getLdapSettings(accountIdentifier)));
  }

  @Override
  @FeatureRestrictionCheck(FeatureRestrictionName.LDAP_SUPPORT)
  public LDAPSettings createLdapSettings(
      @NotNull @AccountIdentifier String accountIdentifier, LDAPSettings ldapSettings) {
    log.info("NGLDAP: Create ldap settings call for accountId {}", accountIdentifier);
    return fromCGLdapSettings(getResponse(
        managerClient.createLdapSettings(accountIdentifier, toCGLdapSettings(ldapSettings, accountIdentifier))));
  }

  @Override
  public LDAPSettings updateLdapSettings(
      @NotNull @AccountIdentifier String accountIdentifier, LDAPSettings ldapSettings) {
    log.info("NGLDAP: Update ldap settings call for accountId {}, ldap name {}", accountIdentifier,
        ldapSettings.getDisplayName());
    return fromCGLdapSettings(getResponse(
        managerClient.updateLdapSettings(accountIdentifier, toCGLdapSettings(ldapSettings, accountIdentifier))));
  }

  @Override
  public void deleteLdapSettings(@NotNull @AccountIdentifier String accountIdentifier) {
    log.info("NGLDAP: Delete ldap settings call for accountId {}", accountIdentifier);
    LdapSettings settings = getResponse(managerClient.getLdapSettings(accountIdentifier));
    if (settings == null) {
      throw new InvalidRequestException("No Ldap Settings found for this account: " + accountIdentifier);
    }
    if (isNotEmpty(userGroupService.getUserGroupsBySsoId(accountIdentifier, settings.getUuid()))) {
      throw new InvalidRequestException(
          "Deleting Ldap provider with linked user groups is not allowed. Unlink the user groups first");
    }
    getResponse(managerClient.deleteLdapSettings(accountIdentifier));
  }

  @Override
  public void updateAuthenticationForSAMLSetting(String accountId, String samlSSOId, Boolean enable) {
    if (Boolean.FALSE.equals(enable)) { // check for disable case conflict
      SSOConfig ssoConfig = getResponse(managerClient.getAccountAccessManagementSettingsV2(accountId));
      if (ssoConfig != null && !checkIfSAMLSSOIdAuthenticationCanBeDisabled(ssoConfig)) {
        throw new InvalidRequestException(String.format(
            "SAML setting with SSO Id %s can not be disabled for authentication, as account's %s current authentication mechanism is SAML, "
                + "and this is the only SAML setting with authentication setting enabled. Please enable authentication on other configured SAML"
                + " setting(s) first or switch account authentication mechanism to other before disabling authentication for this SAML.",
            samlSSOId, accountId));
      }
    }
    getResponse(
        managerClient.updateAuthenticationEnabledForSAMLSetting(accountId, samlSSOId, Boolean.TRUE.equals(enable)));
  }

  private LDAPSettings fromCGLdapSettings(LdapSettings ldapSettings) {
    return LDAPSettings.builder()
        .identifier(ldapSettings.getUuid())
        .connectionSettings(ldapSettings.getConnectionSettings())
        .userSettingsList(ldapSettings.getUserSettingsList())
        .groupSettingsList(ldapSettings.getGroupSettingsList())
        .displayName(ldapSettings.getDisplayName())
        .cronExpression(ldapSettings.getCronExpression())
        .nextIterations(ldapSettings.getNextIterations())
        .disabled(ldapSettings.isDisabled())
        .build();
  }

  private LdapSettings toCGLdapSettings(LDAPSettings ldapSettings, final String accountId) {
    LdapSettings toLdapSettings = LdapSettings.builder()
                                      .displayName(ldapSettings.getDisplayName())
                                      .accountId(ldapSettings.getConnectionSettings().getAccountId())
                                      .connectionSettings(ldapSettings.getConnectionSettings())
                                      .userSettingsList(ldapSettings.getUserSettingsList())
                                      .groupSettingsList(ldapSettings.getGroupSettingsList())
                                      .accountId(accountId)
                                      .build();

    toLdapSettings.setUuid(ldapSettings.getIdentifier());
    toLdapSettings.setCronExpression(ldapSettings.getCronExpression());
    toLdapSettings.setDisabled(ldapSettings.isDisabled());
    return toLdapSettings;
  }

  private AuthenticationSettingsResponse buildAndReturnAuthenticationSettingsResponse(
      SSOConfig ssoConfig, String accountIdentifier, Set<String> whitelistedDomains) {
    List<NGAuthSettings> settingsList = buildAuthSettingsList(ssoConfig, accountIdentifier);
    log.info("NGAuthSettings list for accountId {}: {}", accountIdentifier, settingsList);

    boolean twoFactorEnabled = getResponse(managerClient.twoFactorEnabled(accountIdentifier));
    Integer sessionTimeoutInMinutes = getResponse(managerClient.getSessionTimeoutAtAccountLevel(accountIdentifier));

    return AuthenticationSettingsResponse.builder()
        .whitelistedDomains(whitelistedDomains)
        .ngAuthSettings(settingsList)
        .authenticationMechanism(ssoConfig.getAuthenticationMechanism())
        .twoFactorEnabled(twoFactorEnabled)
        .sessionTimeoutInMinutes(sessionTimeoutInMinutes)
        .build();
  }

  private boolean checkIfSAMLSSOIdAuthenticationCanBeDisabled(SSOConfig ssoConfig) {
    if (ssoConfig.getAuthenticationMechanism() != AuthenticationMechanism.SAML) {
      return true;
    }
    if (isNotEmpty(ssoConfig.getSsoSettings())) {
      return ssoConfig.getSsoSettings()
                 .stream()
                 .filter(Objects::nonNull)
                 .filter(ssoSetting -> ssoSetting.getType() == SSOType.SAML)
                 .filter(setting -> ((SamlSettings) setting).isAuthenticationEnabled())
                 .count()
          > 1;
    }
    return false;
  }

  private void checkMultipleIdpSupportFF(final String accountId, final String operation) {
    if (ngFeatureFlagHelperService.isEnabled(accountId, PL_ENABLE_MULTIPLE_IDP_SUPPORT)) {
      throw new InvalidRequestException(String.format(
          "Multiple IdP support FF 'PL_ENABLE_MULTIPLE_IDP_SUPPORT' enabled on account %s. Please %s on a given samlSSOId API endpoint"
              + " to %s a SAML setting when Multiple IdP support is enabled on account",
          accountId, operation, operation));
    }
  }
}
