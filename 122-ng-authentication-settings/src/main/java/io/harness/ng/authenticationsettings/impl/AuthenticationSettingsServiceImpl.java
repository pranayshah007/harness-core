/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.accesscontrol.AccountIdentifier;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
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

import software.wings.beans.loginSettings.LoginSettings;
import software.wings.beans.loginSettings.PasswordStrengthPolicy;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SAMLProviderType;
import software.wings.beans.sso.SSOSettings;
import software.wings.beans.sso.SSOType;
import software.wings.beans.sso.SamlSettings;
import software.wings.security.authentication.LoginTypeResponse;
import software.wings.security.authentication.SSOConfig;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
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

  @Override
  public AuthenticationSettingsResponse getAuthenticationSettings(String accountIdentifier) {
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
  public SSOConfig deleteSAMLMetadata(@NotNull @AccountIdentifier String accountIdentifier) {
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
  @FeatureRestrictionCheck(FeatureRestrictionName.SAML_SUPPORT)
  public LoginTypeResponse getSAMLLoginTest(@NotNull @AccountIdentifier String accountIdentifier) {
    return getResponse(managerClient.getSAMLLoginTest(accountIdentifier));
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
}
