/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_AUTHENTICATION_SETTINGS;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.beans.ldap.LDAPTestAuthenticationRequest;
import io.harness.eraro.ErrorCode;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.rest.RestResponse;

import software.wings.beans.sso.LdapGroupResponse;
import software.wings.beans.sso.LdapSettings;
import software.wings.beans.sso.LdapTestResponse;
import software.wings.beans.sso.OauthSettings;
import software.wings.helpers.ext.ldap.LdapResponse;
import software.wings.helpers.ext.ldap.LdapResponse.Status;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.security.authentication.LoginTypeResponse;
import software.wings.security.authentication.LoginTypeResponse.LoginTypeResponseBuilder;
import software.wings.security.authentication.SSOConfig;
import software.wings.security.saml.SamlClientService;
import software.wings.service.intfc.SSOService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotBlank;

@OwnedBy(PL)
@Api("sso")
@Path("/sso")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Scope(ResourceType.SSO)
@Slf4j
@AuthRule(permissionType = MANAGE_AUTHENTICATION_SETTINGS)
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class SSOResource {
  private SSOService ssoService;
  private SamlClientService samlClientService;

  @Inject
  public SSOResource(SSOService ssoService, SamlClientService samlClientService) {
    this.ssoService = ssoService;
    this.samlClientService = samlClientService;
  }

  @POST
  @Path("saml-idp-metadata-upload")
  @Consumes(MULTIPART_FORM_DATA)
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> uploadSamlMetaData(@QueryParam("accountId") String accountId,
      @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail, @FormDataParam("displayName") String displayName,
      @FormDataParam("groupMembershipAttr") String groupMembershipAttr,
      @FormDataParam("authorizationEnabled") Boolean authorizationEnabled, @FormDataParam("logoutUrl") String logoutUrl,
      @FormDataParam("entityIdentifier") String entityIdentifier,
      @FormDataParam("samlProviderType") String samlProviderType, @FormDataParam("clientId") String clientId,
      @FormDataParam("clientSecret") String clientSecret) {
    return new RestResponse<>(ssoService.uploadSamlConfiguration(accountId, uploadedInputStream, displayName,
        groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
        isEmpty(clientSecret) ? null : clientSecret.toCharArray(), null, false));
  }

  @POST
  @Path("oauth-settings-upload")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> uploadOathSettings(
      @QueryParam("accountId") String accountId, OauthSettings oauthSettings) {
    return new RestResponse<>(
        ssoService.uploadOauthConfiguration(accountId, oauthSettings.getFilter(), oauthSettings.getAllowedProviders()));
  }

  @DELETE
  @Path("delete-oauth-settings")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> deleteOauthSettings(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.deleteOauthConfiguration(accountId));
  }

  @PUT
  @Path("oauth-settings-upload")
  @Consumes(MediaType.APPLICATION_JSON)
  public RestResponse<OauthSettings> updateOathSettings(
      @QueryParam("accountId") String accountId, OauthSettings oauthSettings) {
    return new RestResponse<>(
        ssoService.updateOauthSettings(accountId, oauthSettings.getFilter(), oauthSettings.getAllowedProviders()));
  }

  /**
   * This should have been a patch call ideally but because harness authFilters don't allow Patch calls,
   * making it a PUT call for now to Unblock SamlAuthorization changes.
   */
  @PUT
  @Path("saml-idp-metadata-upload")
  @Consumes(MULTIPART_FORM_DATA)
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> updateSamlMetaData(@QueryParam("accountId") String accountId,
      @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("file") FormDataContentDisposition fileDetail, @FormDataParam("displayName") String displayName,
      @FormDataParam("groupMembershipAttr") String groupMembershipAttr,
      @FormDataParam("authorizationEnabled") Boolean authorizationEnabled, @FormDataParam("logoutUrl") String logoutUrl,
      @FormDataParam("entityIdentifier") String entityIdentifier,
      @FormDataParam("samlProviderType") String samlProviderType, @FormDataParam("clientId") String clientId,
      @FormDataParam("clientSecret") String clientSecret) {
    return new RestResponse<>(ssoService.updateSamlConfiguration(accountId, uploadedInputStream, displayName,
        groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
        isEmpty(clientSecret) ? null : clientSecret.toCharArray(), null, false));
  }

  @PUT
  @Path("saml-logout-url-upload")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> updateLogoutUrlSamlSettings(
      @QueryParam("accountId") String accountId, @QueryParam("logoutUrl") String logoutUrl) {
    return new RestResponse<>(ssoService.updateLogoutUrlSamlSettings(accountId, logoutUrl));
  }

  @DELETE
  @Path("delete-saml-idp-metadata")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> deleteSamlMetaData(@QueryParam("accountId") String accountId) {
    return new RestResponse<SSOConfig>(ssoService.deleteSamlConfiguration(accountId));
  }

  @PUT
  @Path("assign-auth-mechanism")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> setAuthMechanism(@QueryParam("accountId") String accountId,
      @QueryParam("authMechanism") AuthenticationMechanism authenticationMechanism) {
    return new RestResponse<SSOConfig>(ssoService.setAuthenticationMechanism(accountId, authenticationMechanism));
  }

  @PUT
  @Path("auth-mechanism/LDAP")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> enableLdapAuthMechanism(
      @QueryParam("accountId") String accountId, @NotNull @Valid LDAPTestAuthenticationRequest authenticationRequest) {
    LdapSettings settings = ssoService.getLdapSettings(accountId);
    if (null == settings) {
      throw new InvalidRequestException(
          String.format("No LDAP SSO Provider settings found for account: %s", accountId));
    }
    // Validate ldap settings against provided username password.
    LdapResponse response = ssoService.validateLdapAuthentication(
        settings, authenticationRequest.getEmail(), authenticationRequest.getPassword());
    if (response.getStatus() == Status.FAILURE) {
      throw new InvalidRequestException(response.getMessage());
    }

    return new RestResponse<>(ssoService.setAuthenticationMechanism(accountId, AuthenticationMechanism.LDAP));
  }

  @PUT
  @Path("auth-mechanism/SAML")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> enableSamlAuthMechanism(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.setAuthenticationMechanism(accountId, AuthenticationMechanism.SAML));
  }

  @PUT
  @Path("auth-mechanism/OAUTH")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> enableOauthAuthMechanism(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.setAuthenticationMechanism(accountId, AuthenticationMechanism.OAUTH));
  }

  @PUT
  @Path("auth-mechanism/USER_PASSWORD")
  @Timed
  @ExceptionMetered
  public RestResponse<SSOConfig> enableBasicAuthMechanism(@QueryParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.setAuthenticationMechanism(accountId, AuthenticationMechanism.USER_PASSWORD));
  }

  @GET
  @Path("access-management/{accountId}")
  @Timed
  @AuthRule(permissionType = LOGGED_IN)
  @ExceptionMetered
  public RestResponse<SSOConfig> getAccountAccessManagementSettings(@PathParam("accountId") String accountId) {
    return new RestResponse<>(ssoService.getAccountAccessManagementSettings(accountId));
  }

  @POST
  @Path("ldap/settings")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapSettings> createLdapSettings(
      @QueryParam("accountId") @NotBlank String accountId, @Valid LdapSettings settings) {
    if (!settings.getAccountId().equals(accountId)) {
      throw new InvalidRequestException("accountId in the query parameter and request body don't match");
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
      throw new InvalidRequestException("accountId in the query parameter and request body don't match");
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

  @POST
  @Path("ldap/iterations")
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse<List<Long>> getIterationsFromCron(
      @QueryParam("accountId") @NotBlank String accountId, CronExpressionRequest cronExpressionRequest) {
    return new RestResponse<>(ssoService.getIterationsFromCron(accountId, cronExpressionRequest.getCronExpression()));
  }

  @DELETE
  @Path("ldap/settings")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapSettings> deleteLdapSettings(@QueryParam("accountId") @NotBlank String accountId) {
    return new RestResponse<>(ssoService.deleteLdapSettings(accountId));
  }

  @POST
  @Path("ldap/settings/test/connection")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapTestResponse> validateLdapConnectionSettings(
      @QueryParam("accountId") @NotBlank String accountId, @Valid LdapSettings settings) {
    return new RestResponse<>(ssoService.validateLdapConnectionSettings(settings, accountId));
  }

  @POST
  @Path("ldap/settings/test/user")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapTestResponse> validateLdapUserSettings(
      @QueryParam("accountId") @NotBlank String accountId, @Valid LdapSettings settings) {
    return new RestResponse<>(ssoService.validateLdapUserSettings(settings, accountId));
  }

  @POST
  @Path("ldap/settings/test/group")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapTestResponse> validateLdapGroupSettings(
      @QueryParam("accountId") @NotBlank String accountId, @Valid LdapSettings settings) {
    return new RestResponse<>(ssoService.validateLdapGroupSettings(settings, accountId));
  }

  @POST
  @Path("ldap/settings/test/authentication")
  @Timed
  @ExceptionMetered
  public RestResponse<LdapResponse> validateLdapAuthentication(@QueryParam("accountId") @NotBlank String accountId,
      @NotNull @Valid LDAPTestAuthenticationRequest authenticationRequest) {
    LdapSettings settings = ssoService.getLdapSettings(accountId);
    if (null == settings) {
      throw new InvalidRequestException(
          String.format("No LDAP SSO Provider settings found for account: %s", accountId));
    }
    return new RestResponse<>(ssoService.validateLdapAuthentication(
        settings, authenticationRequest.getEmail(), authenticationRequest.getPassword()));
  }

  @GET
  @Path("ldap/{ldapId}/search/group")
  @Timed
  @ExceptionMetered
  public RestResponse<Collection<LdapGroupResponse>> searchLdapGroups(@PathParam("ldapId") String ldapId,
      @QueryParam("accountId") @NotBlank String accountId, @QueryParam("q") @NotBlank String query) {
    Collection<LdapGroupResponse> groups = ssoService.searchGroupsByName(ldapId, query);
    return new RestResponse<>(groups);
  }

  @Data
  public static class CronExpressionRequest {
    @NotBlank String cronExpression;
  }

  /**
   * Returns redirect response to SAML authentication provider if SAML credentials exist for user
   * throws error if called when SAML account does not exist
   * Note: User is extracted inside generateTestSamlRequest function
   *
   * @param accountId - Currently active account of user
   * @return RestResponse - contains redirect request to SAML auth provider
   */
  @GET
  @Path("saml-login-test")
  public RestResponse<LoginTypeResponse> getSamlLoginTest(@QueryParam("accountId") @NotBlank String accountId) {
    LoginTypeResponseBuilder builder = LoginTypeResponse.builder();
    try {
      builder.SSORequest(samlClientService.generateTestSamlRequest(accountId));
      return new RestResponse<>(builder.authenticationMechanism(AuthenticationMechanism.SAML).build());
    } catch (Exception e) {
      throw new WingsException(ErrorCode.INVALID_SAML_CONFIGURATION);
    }
  }
}
