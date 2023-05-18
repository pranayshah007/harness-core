/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.SecretString.SECRET_MASK;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EncryptedData;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.delegate.beans.ldap.LDAPTestAuthenticationRequest;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataAndPasswordDetail;
import io.harness.delegate.beans.ldap.LdapSettingsWithEncryptedDataDetail;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.rest.RestResponse;
import io.harness.secretmanagers.SecretManagerConfigService;
import io.harness.security.annotations.InternalApi;
import io.harness.security.annotations.NextGenManagerAuth;

import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapSettingsMapper;
import software.wings.beans.sso.OauthSettings;
import software.wings.beans.sso.SamlSettings;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.security.annotations.AuthRule;
import software.wings.security.authentication.LoginTypeResponse;
import software.wings.security.authentication.SSOConfig;
import software.wings.security.saml.SamlClientService;
import software.wings.service.intfc.SSOService;
import software.wings.service.intfc.security.SecretManager;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.InputStream;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotBlank;

@Api(value = "/ng/sso", hidden = true)
@Path("/ng/sso")
@Consumes({MediaType.APPLICATION_JSON, MediaType.MULTIPART_FORM_DATA})
@Produces(MediaType.APPLICATION_JSON)
@NextGenManagerAuth
@Slf4j
@OwnedBy(HarnessTeam.PL)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class SSOResourceNG {
  private SSOService ssoService;

  @Inject
  public SSOResourceNG(SSOService ssoService) {
    this.ssoService = ssoService;
  }
  @Inject private SamlClientService samlClientService;
  @Inject private SecretManagerConfigService secretManagerConfigService;
  @Inject private SecretManager secretManager;

  static final String CLIENT_SECRET_NAME_PREFIX = "ClientSecret-NamePrefix-";

  @GET
  @Path("get-access-management")
  @Timed
  @AuthRule(permissionType = LOGGED_IN)
  @ExceptionMetered
  public RestResponse<SSOConfig> getAccountAccessManagementSettings(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.getAccountAccessManagementSettings(
        accountId, false)); // even though NG but there is V2 API so here 'false' is passed
  }

  @GET
  @Path("v2/get-access-management")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<SSOConfig> getAccountAccessManagementSettingsV2(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.getAccountAccessManagementSettingsV2(accountId));
  }

  @POST
  @Path("oauth-settings-upload")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> uploadOathSettings(
      @QueryParam("accountId") String accountId, OauthSettings oauthSettings) {
    return new RestResponse<>(ssoService.uploadOauthConfiguration(
        accountId, oauthSettings.getFilter(), oauthSettings.getAllowedProviders(), true));
  }

  @PUT
  @Path("assign-auth-mechanism")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> setAuthMechanism(@QueryParam("accountId") String accountId,
      @QueryParam("authMechanism") AuthenticationMechanism authenticationMechanism) {
    return new RestResponse<>(ssoService.setAuthenticationMechanism(accountId, authenticationMechanism, true));
  }

  @DELETE
  @Path("delete-oauth-settings")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> deleteOauthSettings(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.deleteOauthConfiguration(accountId));
  }

  @GET
  @Path("get-saml-settings")
  @Timed
  @ExceptionMetered
  public RestResponse<SamlSettings> getSamlSetting(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.getSamlSettings(accountId));
  }

  @GET
  @Path("get-saml-settings-sso-id")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<SamlSettings> getSamlSetting(
      @QueryParam("accountId") String accountId, @QueryParam("samlSSOId") String samlSSOId) {
    return new RestResponse<>(ssoService.getSamlSettings(accountId));
  }

  @POST
  @Path("saml-idp-metadata-upload")
  @Timed
  @AuthRule(permissionType = LOGGED_IN)
  @ExceptionMetered
  public RestResponse<SSOConfig> uploadSamlMetaData(@QueryParam("accountId") String accountId,
      @FormDataParam("file") InputStream uploadedInputStream, @FormDataParam("displayName") String displayName,
      @FormDataParam("groupMembershipAttr") String groupMembershipAttr,
      @FormDataParam("authorizationEnabled") Boolean authorizationEnabled, @FormDataParam("logoutUrl") String logoutUrl,
      @FormDataParam("entityIdentifier") String entityIdentifier,
      @FormDataParam("samlProviderType") String samlProviderType, @FormDataParam("clientId") String clientId,
      @FormDataParam("clientSecret") String clientSecret, @FormDataParam("friendlySamlName") String friendlySamlName) {
    final String clientSecretRef = getCGSecretManagerRefForClientSecret(accountId, true, clientId, clientSecret);
    return new RestResponse<>(ssoService.uploadSamlConfiguration(accountId, uploadedInputStream, displayName,
        groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
        isEmpty(clientSecretRef) ? null : clientSecretRef.toCharArray(), friendlySamlName, true));
  }

  @PUT
  @Path("saml-idp-metadata-upload")
  @Timed
  @AuthRule(permissionType = LOGGED_IN)
  @ExceptionMetered
  public RestResponse<SSOConfig> updateSamlMetaData(@QueryParam("accountId") String accountId,
      @FormDataParam("file") InputStream uploadedInputStream, @FormDataParam("displayName") String displayName,
      @FormDataParam("groupMembershipAttr") String groupMembershipAttr,
      @FormDataParam("authorizationEnabled") Boolean authorizationEnabled, @FormDataParam("logoutUrl") String logoutUrl,
      @FormDataParam("entityIdentifier") String entityIdentifier,
      @FormDataParam("samlProviderType") String samlProviderType, @FormDataParam("clientId") String clientId,
      @FormDataParam("clientSecret") String clientSecret) {
    final String clientSecretRef = getCGSecretManagerRefForClientSecret(accountId, false, clientId, clientSecret);
    return new RestResponse<>(ssoService.updateSamlConfiguration(accountId, uploadedInputStream, displayName,
        groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
        isEmpty(clientSecretRef) ? null : clientSecretRef.toCharArray(), true));
  }

  @PUT
  @Path("saml-idp-metadata-upload-sso-id")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<SSOConfig> updateSamlMetaData(@QueryParam("accountId") String accountId,
      @QueryParam("samlSSOId") String samlSSOId, @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("displayName") String displayName,
      @FormDataParam("groupMembershipAttr") String groupMembershipAttr,
      @FormDataParam("authorizationEnabled") Boolean authorizationEnabled, @FormDataParam("logoutUrl") String logoutUrl,
      @FormDataParam("entityIdentifier") String entityIdentifier,
      @FormDataParam("samlProviderType") String samlProviderType, @FormDataParam("clientId") String clientId,
      @FormDataParam("clientSecret") String clientSecret,
      @FormDataParam("friendlySamlName") String friendlySamlAppName) {
    final String clientSecretRef = getCGSecretManagerRefForClientSecret(accountId, false, clientId, clientSecret);
    return new RestResponse<>(ssoService.updateSamlConfiguration(accountId, samlSSOId, uploadedInputStream, displayName,
        groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
        isEmpty(clientSecretRef) ? null : clientSecretRef.toCharArray(), friendlySamlAppName, true));
  }

  @PUT
  @Path("update-saml-setting-authentication")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<Boolean> updateLoginEnabledForSAMLSetting(@QueryParam("accountId") String accountId,
      @QueryParam("samlSSOId") String samlSSOId, @QueryParam("enable") @DefaultValue("true") boolean enable) {
    ssoService.updateAuthenticationEnabledForSAMLSetting(accountId, samlSSOId, enable);
    return new RestResponse<>(true);
  }

  @DELETE
  @Path("delete-saml-idp-metadata")
  @Timed
  @AuthRule(permissionType = LOGGED_IN)
  @ExceptionMetered
  public RestResponse<SSOConfig> deleteSamlMetaData(@QueryParam("accountId") String accountId) {
    return new RestResponse<SSOConfig>(ssoService.deleteSamlConfiguration(accountId));
  }

  @DELETE
  @Path("delete-saml-idp-metadata-sso-id")
  @Timed
  @ExceptionMetered
  @InternalApi
  public RestResponse<SSOConfig> deleteSamlMetaData(
      @QueryParam("accountId") String accountId, @QueryParam("samlSSOId") String samlSSOId) {
    return new RestResponse<>(ssoService.deleteSamlConfiguration(accountId, samlSSOId));
  }

  @GET
  @Path("saml-login-test")
  public RestResponse<LoginTypeResponse> getSamlLoginTest(@QueryParam("accountId") @NotBlank String accountId) {
    LoginTypeResponse response = LoginTypeResponse.builder().build();
    try {
      response.setSSORequest(samlClientService.generateTestSamlRequest(accountId));
      response.setAuthenticationMechanism(AuthenticationMechanism.SAML);
      return new RestResponse<>(response);
    } catch (Exception e) {
      throw new WingsException(ErrorCode.INVALID_SAML_CONFIGURATION);
    }
  }

  @GET
  @Path("v2/saml-login-test")
  public RestResponse<LoginTypeResponse> getSamlLoginTest(
      @QueryParam("accountId") @NotBlank String accountId, @QueryParam("samlSSOId") @NotNull String samlSSOId) {
    LoginTypeResponse response = LoginTypeResponse.builder().build();
    try {
      response.setSSORequest(samlClientService.generateTestSamlRequest(accountId, samlSSOId));
      response.setAuthenticationMechanism(AuthenticationMechanism.SAML);
      return new RestResponse<>(response);
    } catch (Exception e) {
      throw new WingsException(ErrorCode.INVALID_SAML_CONFIGURATION);
    }
  }

  @GET
  @Path("ldap/setting-with-encrypted-details")
  @Timed
  @ExceptionMetered
  @Produces("application/x-kryo")
  @Consumes("application/x-kryo")
  public RestResponse<LdapSettingsWithEncryptedDataDetail> getLdapSetting(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.getLdapSettingWithEncryptedDataDetail(accountId, null));
  }

  @POST
  @Path("ldap/setting-with-encrypted-details")
  @Timed
  @ExceptionMetered
  @Produces("application/x-kryo")
  @Consumes("application/x-kryo")
  public RestResponse<LdapSettingsWithEncryptedDataDetail> getLdapSetting(
      @QueryParam("accountId") String accountId, @Valid software.wings.beans.dto.LdapSettings settings) {
    return new RestResponse<>(
        ssoService.getLdapSettingWithEncryptedDataDetail(accountId, LdapSettingsMapper.fromLdapSettingsDTO(settings)));
  }

  @POST
  @Path("ldap/settings")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapSettings> createLdapSettings(
      @QueryParam("accountId") @NotBlank String accountId, @Valid LdapSettings settings) {
    if (!settings.getAccountId().equals(accountId)) {
      throw new InvalidRequestException("AccountId in the query parameter and request body don't match");
    }
    return new RestResponse<>(ssoService.createLdapSettings(settings));
  }

  @PUT
  @Path("ldap/settings")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapSettings> updateLdapSettings(
      @QueryParam("accountId") @NotBlank String accountId, @Valid LdapSettings settings) {
    if (!settings.getAccountId().equals(accountId)) {
      throw new InvalidRequestException("AccountId in the query parameter and request body don't match");
    }
    return new RestResponse<>(ssoService.updateLdapSettings(settings));
  }

  @GET
  @Path("ldap/settings")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapSettings> getLdapSettings(@QueryParam("accountId") @NotBlank String accountId) {
    return new RestResponse<>(ssoService.getLdapSettings(accountId));
  }

  @DELETE
  @Path("ldap/settings")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapSettings> deleteLdapSettings(@QueryParam("accountId") @NotBlank String accountId) {
    return new RestResponse<>(ssoService.deleteLdapSettings(accountId));
  }

  @POST
  @Path("ldap/settings/test/authentication")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapResponse> testLdapAuthentication(@QueryParam("accountId") @NotBlank String accountId,
      @FormDataParam("email") String email, @FormDataParam("password") String password) {
    LdapSettings settings = ssoService.getLdapSettings(accountId);
    if (null == settings) {
      throw new InvalidRequestException(
          String.format("No LDAP SSO Provider settings found for account: %s", accountId));
    }
    return new RestResponse<>(ssoService.validateLdapAuthentication(settings, email, password));
  }

  @POST
  @Path("ldap/setting-with-encrypted-data-password-details")
  @Timed
  @ExceptionMetered
  @Produces("application/x-kryo")
  @Consumes("application/x-kryo")
  public RestResponse<LdapSettingsWithEncryptedDataAndPasswordDetail> getLdapSettingsAndEncryptedPassword(
      @QueryParam("accountId") @NotBlank String accountId,
      @NotNull @Valid LDAPTestAuthenticationRequest authenticationRequest) {
    if (isEmpty(authenticationRequest.getPassword())) {
      throw new InvalidRequestException(
          String.format("Password field is required for fetching encrypted data details: %s", accountId));
    }
    return new RestResponse<>(
        ssoService.getLdapSettingsWithEncryptedDataAndPasswordDetail(accountId, authenticationRequest.getPassword()));
  }

  @VisibleForTesting
  String getCGSecretManagerRefForClientSecret(
      final String accountId, final boolean isCreateCall, final String clientId, final String clientSecret) {
    final String validationErrorMsg = "Both clientId and clientSecret needs to be provided together for SAML setting";
    if (isCreateCall) {
      if (isNotEmpty(clientId) && isEmpty(clientSecret) || isEmpty(clientId) && isNotEmpty(clientSecret)) {
        throw new InvalidRequestException(validationErrorMsg, WingsException.USER);
      }
    }
    if (isEmpty(clientSecret)) {
      return null;
    }
    if (!isCreateCall && isNotEmpty(clientId) && SECRET_MASK.equals(clientSecret)) {
      return clientSecret;
    } else if (!isCreateCall && isEmpty(clientId) && isNotEmpty(clientSecret)) {
      throw new InvalidRequestException(validationErrorMsg, WingsException.USER);
    }
    return handleSecretRefCreateOrUpdate(accountId, clientSecret, isCreateCall);
  }

  private String handleSecretRefCreateOrUpdate(
      final String accountId, final String clientSecret, final boolean isCreateCall) {
    SecretManagerConfig secretManagerConfig = secretManagerConfigService.getDefaultSecretManager(accountId);
    final String secretName = CLIENT_SECRET_NAME_PREFIX + accountId;
    EncryptedData secretByNameData = secretManager.getSecretByName(accountId, secretName);
    String cgSecretRefId = null;
    if (null != secretByNameData) {
      cgSecretRefId = secretByNameData.getUuid();
    }
    final SecretText secretText =
        buildSecretTextForClientSecret(clientSecret, secretName, secretManagerConfig.getUuid());
    if (!isCreateCall || isNotEmpty(cgSecretRefId)) {
      secretManager.updateSecretText(accountId, cgSecretRefId, secretText, true);
      return cgSecretRefId;
    } else {
      return secretManager.saveSecretText(accountId, secretText, true);
    }
  }

  private SecretText buildSecretTextForClientSecret(
      final String clientSecret, final String secretName, final String managerConfigUuid) {
    SecretText secretText = new SecretText();
    secretText.setValue(clientSecret);
    secretText.setName(secretName);
    secretText.setScopedToAccount(true);
    secretText.setKmsId(managerConfigUuid);
    return secretText;
  }
}
