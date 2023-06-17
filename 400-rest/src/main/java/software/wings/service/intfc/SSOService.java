/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataAndPasswordDetail;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.account.OauthProviderType;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.security.authentication.SSOConfig;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotBlank;

// TODO: Refactor this to make it more abstract and common across different SSO providers
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public interface SSOService {
  SSOConfig uploadSamlConfiguration(String accountId, InputStream inputStream, String displayName,
      String groupMembershipAttr, Boolean authorizationEnabled, String logoutUrl, String entityIdentifier,
      String samlProviderType, String clientId, char[] clientSecret, String friendlySamlName, boolean isNGSSO,
      Boolean jitEnabled, String jitValidationKey, String jitValidationValue);

  SSOConfig uploadOauthConfiguration(
      String accountId, String filter, Set<OauthProviderType> allowedProviders, boolean isNG);

  SSOConfig updateSamlConfiguration(@NotNull String accountId, InputStream inputStream, String displayName,
      String groupMembershipAttr, @NotNull Boolean authorizationEnabled, String logoutUrl, String entityIdentifier,
      String samlProviderType, String clientId, char[] clientSecret, boolean isNGSSO, @NotNull Boolean jitEnabled,
      String jitValidationKey, String jitValidationValue);

  // this overloading is for updating a SAML setting (samlSSOId) among list of saml settings in account
  SSOConfig updateSamlConfiguration(@NotNull String accountId, @NotNull String samlSSOId, InputStream inputStream,
      String displayName, String groupMembershipAttr, @NotNull Boolean authorizationEnabled, String logoutUrl,
      String entityIdentifier, String samlProviderType, String clientId, char[] clientSecret,
      String friendlySamlAppName, boolean isNGSSO, @NotNull Boolean jitEnabled, String jitValidationKey,
      String jitValidationValue);

  SSOConfig updateLogoutUrlSamlSettings(@NotNull String accountId, @NotNull String logoutUrl);

  SSOConfig deleteSamlConfiguration(@NotNull String accountId);

  // this overloading is for deleting a SAML setting (samlSSOId) among list of saml settings in account
  SSOConfig deleteSamlConfiguration(@NotNull String accountId, @NotNull String samlSSOId);

  SSOConfig setAuthenticationMechanism(
      @NotNull String accountId, @NotNull AuthenticationMechanism authenticationMechanism, boolean isFromNG);

  SSOConfig getAccountAccessManagementSettings(@NotNull String accountId, boolean isNG);

  // this overloading is for NG case
  // SSOConfig getAccountAccessManagementSettings(@NotNull String accountId, boolean isNGSSO);

  SSOConfig getAccountAccessManagementSettingsV2(@NotNull String accountId);

  LdapSettings createLdapSettings(@NotNull LdapSettings settings);

  LdapSettings updateLdapSettings(@NotNull LdapSettings settings);

  LdapSettings getLdapSettings(@NotBlank String accountId);

  LdapSettingsWithEncryptedDataDetail getLdapSettingWithEncryptedDataDetail(
      @NotBlank String accountId, LdapSettings ldapSettings);

  LdapSettings deleteLdapSettings(@NotBlank String accountId);

  SamlSettings getSamlSettings(@NotBlank String accountId);

  // this overloading is to GET a SAML setting (samlSSOId) among list of saml settings in account
  SamlSettings getSamlSettings(@NotBlank String accountId, @NotNull String samlSSOId);

  LdapTestResponse validateLdapConnectionSettings(@NotNull LdapSettings ldapSettings, @NotBlank String accountId);

  LdapTestResponse validateLdapUserSettings(@NotNull LdapSettings ldapSettings, @NotBlank String accountId);

  LdapTestResponse validateLdapGroupSettings(@NotNull LdapSettings ldapSettings, @NotBlank String accountId);

  LdapResponse validateLdapAuthentication(
      @NotNull LdapSettings ldapSettings, @NotBlank String identifier, @NotBlank String password);

  Collection<LdapGroupResponse> searchGroupsByName(@NotBlank String ldapSettingsId, @NotBlank String nameQuery);

  OauthSettings updateOauthSettings(String accountId, String filter, Set<OauthProviderType> allowedProviders);

  SSOConfig deleteOauthConfiguration(String accountId);

  List<Long> getIterationsFromCron(String accountId, String cron);

  LdapSettingsWithEncryptedDataAndPasswordDetail getLdapSettingsWithEncryptedDataAndPasswordDetail(
      @NotBlank String accountId, @NotBlank String password);

  void updateAuthenticationEnabledForSAMLSetting(@NotBlank String accountId, @NotNull String samlSSOId, boolean enable);
}
