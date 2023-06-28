/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings.remote;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.ldap.LDAPTestAuthenticationRequest;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataAndPasswordDetail;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.user.SessionTimeoutSettings;
import io.harness.ng.core.user.TwoFactorAdminOverrideSettings;
import io.harness.rest.RestResponse;
import io.harness.serializer.kryo.KryoRequest;
import io.harness.serializer.kryo.KryoResponse;

import software.wings.beans.loginSettings.LoginSettings;
import software.wings.beans.loginSettings.PasswordStrengthPolicy;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.security.authentication.LoginTypeResponse;
import software.wings.security.authentication.SSOConfig;

import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

@OwnedBy(HarnessTeam.PL)
public interface AuthSettingsManagerClient {
  String API_PREFIX = "ng/";

  @GET(API_PREFIX + "sso/get-access-management")
  Call<RestResponse<SSOConfig>> getAccountAccessManagementSettings(@Query("accountId") @NotEmpty String accountId);

  @GET(API_PREFIX + "sso/v2/get-access-management")
  Call<RestResponse<SSOConfig>> getAccountAccessManagementSettingsV2(@Query("accountId") @NotEmpty String accountId);

  @GET(API_PREFIX + "accounts/get-whitelisted-domains")
  Call<RestResponse<Set<String>>> getWhitelistedDomains(@Query("accountId") @NotEmpty String accountId);

  @GET(API_PREFIX + "login-settings/username-password")
  Call<RestResponse<LoginSettings>> getUserNamePasswordSettings(@Query("accountId") @NotEmpty String accountId);

  @GET(API_PREFIX + "accounts/two-factor-enabled")
  Call<RestResponse<Boolean>> twoFactorEnabled(@Query("accountId") @NotEmpty String accountId);

  @GET(API_PREFIX + "accounts/session-timeout-account-level")
  Call<RestResponse<Integer>> getSessionTimeoutAtAccountLevel(@Query("accountId") @NotEmpty String accountId);

  @POST(API_PREFIX + "sso/oauth-settings-upload")
  Call<RestResponse<SSOConfig>> uploadOauthSettings(
      @Query("accountId") @NotEmpty String accountId, @Body OauthSettings oauthSettings);

  @DELETE(API_PREFIX + "sso/delete-oauth-settings")
  Call<RestResponse<SSOConfig>> deleteOauthSettings(@Query("accountId") String accountId);

  @PUT(API_PREFIX + "sso/assign-auth-mechanism")
  Call<RestResponse<SSOConfig>> updateAuthMechanism(@Query("accountId") @NotEmpty String accountId,
      @Query("authMechanism") AuthenticationMechanism authenticationMechanism);

  @PUT(API_PREFIX + "login-settings/{loginSettingsId}")
  Call<RestResponse<LoginSettings>> updateLoginSettings(@Path("loginSettingsId") String loginSettingsId,
      @Query("accountId") @NotEmpty String accountId, @Body @NotNull @Valid LoginSettings loginSettings);

  @PUT(API_PREFIX + "accounts/whitelisted-domains")
  Call<RestResponse<LoginSettings>> updateWhitelistedDomains(
      @Query("accountId") @NotEmpty String accountId, @Body Set<String> whitelistedDomains);

  @Multipart
  @POST(API_PREFIX + "sso/saml-idp-metadata-upload")
  Call<RestResponse<SSOConfig>> uploadSAMLMetadata(@Query("accountId") String accountId,
      @Part MultipartBody.Part uploadedInputStream, @Part("displayName") RequestBody displayName,
      @Part("groupMembershipAttr") RequestBody groupMembershipAttr,
      @Part("authorizationEnabled") RequestBody authorizationEnabled, @Part("logoutUrl") RequestBody logoutUrl,
      @Part("entityIdentifier") RequestBody entityIdentifier, @Part("samlProviderType") RequestBody samlProviderType,
      @Part("clientId") RequestBody clientId, @Part("clientSecret") RequestBody clientSecret,
      @Part("friendlySamlName") RequestBody friendlySamlName, @Part("jitEnabled") RequestBody jitEnabled,
      @Part("jitValidationKey") RequestBody jitValidationKey,
      @Part("jitValidationValue") RequestBody jitValidationValue);

  @Multipart
  @PUT(API_PREFIX + "sso/saml-idp-metadata-upload")
  Call<RestResponse<SSOConfig>> updateSAMLMetadata(@Query("accountId") String accountId,
      @Part MultipartBody.Part uploadedInputStream, @Part("displayName") RequestBody displayName,
      @Part("groupMembershipAttr") RequestBody groupMembershipAttr,
      @Part("authorizationEnabled") RequestBody authorizationEnabled, @Part("logoutUrl") RequestBody logoutUrl,
      @Part("entityIdentifier") RequestBody entityIdentifier, @Part("samlProviderType") RequestBody samlProviderType,
      @Part("clientId") RequestBody clientId, @Part("clientSecret") RequestBody clientSecret,
      @Part("jitEnabled") RequestBody jitEnabled, @Part("jitValidationKey") RequestBody jitValidationKey,
      @Part("jitValidationValue") RequestBody jitValidationValue);

  @Multipart
  @PUT(API_PREFIX + "sso/saml-idp-metadata-upload-sso-id")
  Call<RestResponse<SSOConfig>> updateSAMLMetadata(@Query("accountId") String accountId,
      @Query("samlSSOId") String samlSSOId, @Part MultipartBody.Part uploadedInputStream,
      @Part("displayName") RequestBody displayName, @Part("groupMembershipAttr") RequestBody groupMembershipAttr,
      @Part("authorizationEnabled") RequestBody authorizationEnabled, @Part("logoutUrl") RequestBody logoutUrl,
      @Part("entityIdentifier") RequestBody entityIdentifier, @Part("samlProviderType") RequestBody samlProviderType,
      @Part("clientId") RequestBody clientId, @Part("clientSecret") RequestBody clientSecret,
      @Part("friendlySamlName") RequestBody friendlySamlAppName, @Part("jitEnabled") RequestBody jitEnabled,
      @Part("jitValidationKey") RequestBody jitValidationKey,
      @Part("jitValidationValue") RequestBody jitValidationValue);

  @DELETE(API_PREFIX + "sso/delete-saml-idp-metadata")
  Call<RestResponse<SSOConfig>> deleteSAMLMetadata(@Query("accountId") String accountIdentifier);

  @DELETE(API_PREFIX + "sso/delete-saml-idp-metadata-sso-id")
  Call<RestResponse<SSOConfig>> deleteSAMLMetadata(
      @Query("accountId") String accountIdentifier, @Query("samlSSOId") String samlSSOId);

  @GET(API_PREFIX + "sso/get-saml-settings")
  Call<RestResponse<SamlSettings>> getSAMLMetadata(@Query("accountId") String accountIdentifier);

  @GET(API_PREFIX + "sso/get-saml-settings-sso-id")
  Call<RestResponse<SamlSettings>> getSAMLMetadata(
      @Query("accountId") String accountIdentifier, @Query("samlSSOId") String samlSSOId);

  @PUT(API_PREFIX + "sso/update-saml-setting-authentication")
  Call<RestResponse<Boolean>> updateAuthenticationEnabledForSAMLSetting(@Query("accountId") @NotEmpty String accountId,
      @Query("samlSSOId") @NotEmpty String samlSSOId, @Query("enable") boolean enable);

  @GET(API_PREFIX + "sso/saml-login-test")
  Call<RestResponse<LoginTypeResponse>> getSAMLLoginTest(@Query("accountId") @NotEmpty String accountIdentifier);

  @GET(API_PREFIX + "sso/v2/saml-login-test")
  Call<RestResponse<LoginTypeResponse>> getSAMLLoginTestV2(
      @Query("accountId") @NotEmpty String accountIdentifier, @Query("samlSSOId") @NotEmpty String samlSSOId);

  @PUT(API_PREFIX + "user/two-factor-admin-override-settings")
  Call<RestResponse<Boolean>> setTwoFactorAuthAtAccountLevel(@Query("accountId") @NotEmpty String accountId,
      @Body TwoFactorAdminOverrideSettings twoFactorAdminOverrideSettings);

  @PUT(API_PREFIX + "accounts/session-timeout-account-level")
  Call<RestResponse<Boolean>> setSessionTimeoutAtAccountLevel(
      @Query("accountId") @NotEmpty String accountId, @Body SessionTimeoutSettings sessionTimeoutSettings);

  @GET(API_PREFIX + "login-settings/username-password/password-strength-policy")
  Call<RestResponse<PasswordStrengthPolicy>> getPasswordStrengthSettings(@Query("accountId") String accountIdentifier);

  @POST(API_PREFIX + "sso/ldap/setting-with-encrypted-details")
  @KryoRequest
  @KryoResponse
  Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> getLdapSettingsUsingAccountIdAndLdapSettings(
      @Query("accountId") String accountIdentifier, @Body software.wings.beans.dto.LdapSettings ldapSettings);

  @GET(API_PREFIX + "sso/ldap/setting-with-encrypted-details")
  @KryoRequest
  @KryoResponse
  Call<RestResponse<LdapSettingsWithEncryptedDataDetail>> getLdapSettingsUsingAccountId(
      @Query("accountId") String accountIdentifier);

  @POST(API_PREFIX + "sso/ldap/settings")
  Call<RestResponse<LdapSettings>> createLdapSettings(
      @Query("accountId") @NotEmpty String accountId, @Body LdapSettings ldapSettings);

  @PUT(API_PREFIX + "sso/ldap/settings")
  Call<RestResponse<LdapSettings>> updateLdapSettings(
      @Query("accountId") @NotEmpty String accountId, @Body LdapSettings ldapSettings);

  @GET(API_PREFIX + "sso/ldap/settings")
  Call<RestResponse<LdapSettings>> getLdapSettings(@Query("accountId") @NotEmpty String accountId);

  @DELETE(API_PREFIX + "sso/ldap/settings")
  Call<RestResponse<LdapSettings>> deleteLdapSettings(@Query("accountId") @NotEmpty String accountId);

  @Multipart
  @POST(API_PREFIX + "sso/ldap/settings/test/authentication")
  Call<RestResponse<LdapResponse>> testLdapAuthentication(@Query("accountId") @NotEmpty String accountId,
      @Part("email") RequestBody email, @Part("password") RequestBody password);

  @POST(API_PREFIX + "sso/ldap/setting-with-encrypted-data-password-details")
  @KryoRequest
  @KryoResponse
  Call<RestResponse<LdapSettingsWithEncryptedDataAndPasswordDetail>> getLdapSettingsAndEncryptedPassword(
      @Query("accountId") String accountIdentifier, @Body LDAPTestAuthenticationRequest authenticationRequest);
}
