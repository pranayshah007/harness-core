/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.authenticationsettings.resources;

import static io.harness.NGCommonEntityConstants.ACCOUNT_PARAM_MESSAGE;
import static io.harness.ng.accesscontrol.PlatformPermissions.DELETE_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.EDIT_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformPermissions.VIEW_AUTHSETTING_PERMISSION;
import static io.harness.ng.accesscontrol.PlatformResourceTypes.AUTHSETTING;

import io.harness.NGCommonEntityConstants;
import io.harness.accesscontrol.acl.api.Resource;
import io.harness.accesscontrol.acl.api.ResourceScope;
import io.harness.accesscontrol.clients.AccessControlClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.GeneralException;
import io.harness.ng.authenticationsettings.dtos.AuthenticationSettingsResponse;
import io.harness.ng.authenticationsettings.dtos.mechanisms.LDAPSettings;
import io.harness.ng.authenticationsettings.dtos.mechanisms.OAuthSettings;
import io.harness.ng.authenticationsettings.impl.AuthenticationSettingsService;
import io.harness.ng.core.account.AuthenticationMechanism;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.ng.core.dto.FailureDTO;
import io.harness.ng.core.user.SessionTimeoutSettings;
import io.harness.ng.core.user.TwoFactorAdminOverrideSettings;
import io.harness.rest.RestResponse;
import io.harness.stream.BoundedInputStream;

import software.wings.app.MainConfiguration;
import software.wings.beans.loginSettings.LoginSettings;
import software.wings.beans.loginSettings.PasswordStrengthPolicy;
import software.wings.security.authentication.LoginTypeResponse;
import software.wings.security.authentication.SSOConfig;

import com.amazonaws.util.IOUtils;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.glassfish.jersey.media.multipart.FormDataContentDisposition;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;
import retrofit2.http.Multipart;

@Api("authentication-settings")
@Path("/authentication-settings")
@Produces(MediaType.APPLICATION_JSON)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Tag(name = "Authentication Settings",
    description = "This contains APIs related to Authentication settings as defined in Harness")
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Bad Request",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = FailureDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = FailureDTO.class))
    })
@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error",
    content =
    {
      @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorDTO.class))
      , @Content(mediaType = "application/yaml", schema = @Schema(implementation = ErrorDTO.class))
    })
@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class AuthenticationSettingsResource {
  AuthenticationSettingsService authenticationSettingsService;
  private final MainConfiguration mainConfiguration;
  private final AccessControlClient accessControlClient;

  @GET
  @Path("/")
  @ApiOperation(value = "Get authentication settings for an account", nickname = "getAuthenticationSettings")
  @Operation(operationId = "getAuthenticationSettings",
      summary = "Gets authentication settings for the given Account ID",
      description = "Gets authentication settings for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Successfully returns authentication settings of an Account")
      })
  public RestResponse<AuthenticationSettingsResponse>
  getAuthenticationSettings(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      "accountIdentifier") @NotNull String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), VIEW_AUTHSETTING_PERMISSION);
    AuthenticationSettingsResponse response =
        authenticationSettingsService.getAuthenticationSettings(accountIdentifier);
    return new RestResponse<>(response);
  }

  @GET
  @Path("/v2")
  @ApiOperation(
      value = "Get authentication settings version 2 for an account", nickname = "getAuthenticationSettingsV2")
  @Operation(operationId = "getAuthenticationSettingsV2",
      summary = "Gets authentication settings version 2 for the given Account ID",
      description = "Gets authentication settings version 2 for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Successfully returns authentication settings of an Account")
      })
  public RestResponse<AuthenticationSettingsResponse>
  getAuthenticationSettingsV2(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      "accountIdentifier") @NotNull String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), VIEW_AUTHSETTING_PERMISSION);
    AuthenticationSettingsResponse response =
        authenticationSettingsService.getAuthenticationSettingsV2(accountIdentifier);
    return new RestResponse<>(response);
  }

  @GET
  @Path("/login-settings/password-strength")
  @ApiOperation(value = "Get Password strength settings", nickname = "getPasswordStrengthSettings")
  @Operation(operationId = "getPasswordStrengthSettings", summary = "Get password strength",
      description = "Gets password strength for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns password strength of an Account")
      })
  public RestResponse<PasswordStrengthPolicy>
  getPasswordStrengthSettings(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      "accountIdentifier") @NotNull String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), VIEW_AUTHSETTING_PERMISSION);
    PasswordStrengthPolicy response = authenticationSettingsService.getPasswordStrengthSettings(accountIdentifier);
    return new RestResponse<>(response);
  }

  @PUT
  @Hidden
  @Path("/login-settings/{loginSettingsId}")
  @ApiOperation(value = "Update login settings - lockout, expiration, strength", nickname = "putLoginSettings")
  @Operation(operationId = "putLoginSettings", summary = "Updates the login settings",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns the updated login settings")
      })
  public RestResponse<LoginSettings>
  updateLoginSettings(
      @Parameter(description = "Login Settings Identifier") @PathParam("loginSettingsId") String loginSettingsId,
      @Parameter(description = ACCOUNT_PARAM_MESSAGE, required = true) @QueryParam(
          "accountIdentifier") @NotEmpty String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
          description =
              "This is the updated Login Settings. Please provide values for all fields, not just the fields you are updating")
      @NotNull @Valid LoginSettings loginSettings) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    LoginSettings updatedLoginSettings =
        authenticationSettingsService.updateLoginSettings(loginSettingsId, accountIdentifier, loginSettings);
    return new RestResponse<>(updatedLoginSettings);
  }

  @PUT
  @Path("/oauth/update-providers")
  @ApiOperation(value = "Update Oauth providers for an account", nickname = "updateOauthProviders")
  @Operation(operationId = "updateOauthProviders", summary = "Update Oauth providers",
      description = "Updates OAuth providers for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully updated the Oauth providers for the account")
      })
  public RestResponse<Boolean>
  updateOauthProviders(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                           "accountIdentifier") @NotNull String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
          description =
              "This is the updated OAuthSettings. Please provide values for all fields, not just the fields you are updating")
      OAuthSettings oAuthSettings) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    authenticationSettingsService.updateOauthProviders(accountIdentifier, oAuthSettings);
    return new RestResponse<>(true);
  }

  @DELETE
  @Path("/oauth/remove-mechanism")
  @ApiOperation(value = "Remove Oauth mechanism for an account", nickname = "removeOauthMechanism")
  @Operation(operationId = "removeOauthMechanism", summary = "Delete OAuth Setting",
      description = "Deletes OAuth settings for a given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Successfully removed OAuth settings configured to an account.")
      })
  public RestResponse<Boolean>
  removeOauthMechanism(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      "accountIdentifier") @NotNull String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), DELETE_AUTHSETTING_PERMISSION);
    authenticationSettingsService.removeOauthMechanism(accountIdentifier);
    return new RestResponse<>(true);
  }

  @PUT
  @Path("/update-auth-mechanism")
  @ApiOperation(value = "Update Auth mechanism for an account", nickname = "updateAuthMechanism")
  @Operation(operationId = "updateAuthMechanism", summary = "Update Auth mechanism",
      description = "Updates the authentication mechanism for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully updated Auth mechanism for an account.")
      })
  public RestResponse<Boolean>
  updateAuthMechanism(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                          "accountIdentifier") @NotNull String accountIdentifier,
      @Parameter(description = "Type of Authentication Mechanism SSO or NON_SSO") @QueryParam(
          "authenticationMechanism") AuthenticationMechanism authenticationMechanism) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    authenticationSettingsService.updateAuthMechanism(accountIdentifier, authenticationMechanism);
    return new RestResponse<>(true);
  }

  @PUT
  @Path("/whitelisted-domains")
  @ApiOperation(value = "Update Whitelisted domains for an account", nickname = "updateWhitelistedDomains")
  @Operation(operationId = "updateWhitelistedDomains", summary = "Updates the whitelisted domains",
      description = "Updates whitelisted domains configured for an account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Successfully updated whitelisted domains configured with an account")
      })
  public RestResponse<Boolean>
  updateWhitelistedDomains(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                               "accountIdentifier") @NotNull String accountIdentifier,
      @Parameter(description = "Set of whitelisted domains and IPs for the account") Set<String> whitelistedDomains) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    authenticationSettingsService.updateWhitelistedDomains(accountIdentifier, whitelistedDomains);
    return new RestResponse<>(true);
  }

  @Multipart
  @POST
  @Path("/saml-metadata-upload")
  @Consumes("multipart/form-data")
  @ApiOperation(value = "Create SAML Config", nickname = "uploadSamlMetaData")
  @Operation(operationId = "uploadSamlMetaData", summary = "Upload SAML metadata",
      description = "Updates the SAML metadata for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Successfully uploads SAML metadata to the SAML setting configured for an account")
      })
  public RestResponse<SSOConfig>
  uploadSamlMetaData(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @NotNull @QueryParam("accountId") String accountId,
      @Parameter(description = "Saml Metadata input file") @FormDataParam("file") InputStream uploadedInputStream,
      @Parameter(description = "Input file metadata") @FormDataParam(
          "fileMetadata") FormDataContentDisposition fileDetail,
      @Parameter(description = "Display Name of the SAML") @FormDataParam("displayName") String displayName,
      @Parameter(description = "Group membership attribute") @FormDataParam(
          "groupMembershipAttr") String groupMembershipAttr,
      @Parameter(description = "Specify whether or not to enable authorization") @FormDataParam("authorizationEnabled")
      Boolean authorizationEnabled, @Parameter(description = "Logout URL") @FormDataParam("logoutUrl") String logoutUrl,
      @Parameter(description = "SAML metadata Identifier") @FormDataParam("entityIdentifier") String entityIdentifier,
      @Parameter(description = "SAML provider type") @FormDataParam("samlProviderType") String samlProviderType,
      @Parameter(description = "Optional SAML clientId for Azure SSO") @FormDataParam("clientId") String clientId,
      @Parameter(description = "Optional SAML clientSecret reference string for Azure SSO") @FormDataParam(
          "clientSecret") String clientSecret,
      @Parameter(description = "Friendly name of the app on SAML SSO provider end in Harness") @FormDataParam(
          "friendlySamlName") String friendlySamlName) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      byte[] bytes = IOUtils.toByteArray(
          new BoundedInputStream(uploadedInputStream, mainConfiguration.getFileUploadLimits().getCommandUploadLimit()));
      final MultipartBody.Part formData =
          MultipartBody.Part.createFormData("file", null, RequestBody.create(MultipartBody.FORM, bytes));
      SSOConfig response = authenticationSettingsService.uploadSAMLMetadata(accountId, formData, displayName,
          groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
          clientSecret, friendlySamlName);
      return new RestResponse<>(response);
    } catch (Exception e) {
      throw new GeneralException("Error while creating new SAML Config", e);
    }
  }

  @Multipart
  @PUT
  @Path("/saml-metadata-upload")
  @Consumes("multipart/form-data")
  @ApiOperation(value = "Edit SAML Config", nickname = "updateSamlMetaData")
  @Operation(operationId = "updateSamlMetaData", summary = "Update SAML metadata",
      description = "Updates SAML metadata of the SAML configuration configured for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Successfully updated SAML metadata of SAML setting configured for an account")
      })
  public RestResponse<SSOConfig>
  updateSamlMetaData(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam("accountId") @NotNull String accountId,
      @Parameter(description = "SAML Metadata input file") @FormDataParam("file") InputStream uploadedInputStream,
      @Parameter(description = "Input file metadata") @FormDataParam(
          "fileMetadata") FormDataContentDisposition fileDetail,
      @Parameter(description = "Display Name of the SAML") @FormDataParam("displayName") String displayName,
      @Parameter(description = "Group membership attribute") @FormDataParam(
          "groupMembershipAttr") String groupMembershipAttr,
      @Parameter(description = "Specify whether or not to enable authorization") @FormDataParam("authorizationEnabled")
      Boolean authorizationEnabled, @Parameter(description = "Logout URL") @FormDataParam("logoutUrl") String logoutUrl,
      @Parameter(description = "SAML metadata Identifier") @FormDataParam("entityIdentifier") String entityIdentifier,
      @Parameter(description = "SAML provider type") @FormDataParam("samlProviderType") String samlProviderType,
      @Parameter(description = "Optional SAML clientId for Azure SSO") @FormDataParam("clientId") String clientId,
      @Parameter(description = "Optional SAML clientSecret reference string for Azure SSO") @FormDataParam(
          "clientSecret") String clientSecret) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      MultipartBody.Part formData = getMultipartBodyFromInputStream(uploadedInputStream);
      SSOConfig response =
          authenticationSettingsService.updateSAMLMetadata(accountId, formData, displayName, groupMembershipAttr,
              authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId, clientSecret);
      return new RestResponse<>(response);
    } catch (Exception e) {
      throw new GeneralException("Error while editing saml-config", e);
    }
  }

  @Multipart
  @PUT
  @Path("/saml-metadata-upload/{samlSSOId}")
  @Consumes("multipart/form-data")
  @ApiOperation(value = "Edit SAML Config for a given SAML SSO Id", nickname = "updateSamlMetaDataForSamlSSOId")
  @Operation(operationId = "updateSamlMetaDataForSamlSSOId", summary = "Update SAML metadata for a given SAML SSO Id",
      description = "Updates SAML metadata of the SAML configuration with given SSO Id, configured for an account",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Successfully updated SAML metadata of SAML setting configured for an account")
      })
  public RestResponse<SSOConfig>
  updateSamlMetaDataForSamlSSOId(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                     NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountId,
      @Parameter(description = "Saml Settings Identifier") @PathParam("samlSSOId") String samlSSOId,
      @Parameter(description = "SAML Metadata input file") @FormDataParam("file") InputStream uploadedInputStream,
      @Parameter(description = "Input file metadata") @FormDataParam(
          "fileMetadata") FormDataContentDisposition fileDetail,
      @Parameter(description = "Display Name of the SAML") @FormDataParam("displayName") String displayName,
      @Parameter(description = "Group membership attribute") @FormDataParam(
          "groupMembershipAttr") String groupMembershipAttr,
      @Parameter(description = "Specify whether or not to enable authorization") @FormDataParam("authorizationEnabled")
      Boolean authorizationEnabled, @Parameter(description = "Logout URL") @FormDataParam("logoutUrl") String logoutUrl,
      @Parameter(description = "SAML metadata Identifier") @FormDataParam("entityIdentifier") String entityIdentifier,
      @Parameter(description = "SAML provider type") @FormDataParam("samlProviderType") String samlProviderType,
      @Parameter(description = "Optional SAML clientId for Azure SSO") @FormDataParam("clientId") String clientId,
      @Parameter(description = "Optional SAML clientSecret reference string for Azure SSO") @FormDataParam(
          "clientSecret") String clientSecret,
      @Parameter(description = "Friendly name of the app on SAML SSO provider end in Harness") @FormDataParam(
          "friendlySamlName") String friendlySamlName) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    try {
      MultipartBody.Part formData = getMultipartBodyFromInputStream(uploadedInputStream);
      SSOConfig response = authenticationSettingsService.updateSAMLMetadata(accountId, samlSSOId, formData, displayName,
          groupMembershipAttr, authorizationEnabled, logoutUrl, entityIdentifier, samlProviderType, clientId,
          clientSecret, friendlySamlName);
      return new RestResponse<>(response);
    } catch (Exception e) {
      throw new GeneralException("Error while editing saml-config " + samlSSOId, e);
    }
  }

  @DELETE
  @Path("/delete-saml-metadata")
  @ApiOperation(value = "Delete SAML Config", nickname = "deleteSamlMetaData")
  @Operation(operationId = "deleteSamlMetaData", summary = "Delete SAML meta data",
      description = "Deletes SAML metadata for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Successfully deleted SAML meta associated with a SAML setting")
      })
  public RestResponse<SSOConfig>
  deleteSamlMetadata(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      "accountIdentifier") @NotNull String accountIdentifier) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), DELETE_AUTHSETTING_PERMISSION);
    SSOConfig response = authenticationSettingsService.deleteSAMLMetadata(accountIdentifier);
    return new RestResponse<>(response);
  }

  @DELETE
  @Path("/saml-metadata/{samlSSOId}/delete")
  @ApiOperation(value = "Delete SAML Config for given SAML sso id", nickname = "deleteSamlMetaDataForSamlSSOId")
  @Operation(operationId = "deleteSamlMetaDataForSamlSSOId", summary = "Delete SAML meta data for given SAML sso id",
      description = "Deletes SAML metadata for the given Account and SAML sso id",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Successfully deleted SAML meta associated with a SAML SSO setting id")
      })
  public RestResponse<SSOConfig>
  deleteSamlMetadataForSamlSSOId(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                     NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @Parameter(description = "Saml Settings Identifier") @PathParam("samlSSOId") String samlSSOId) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), DELETE_AUTHSETTING_PERMISSION);
    SSOConfig response = authenticationSettingsService.deleteSAMLMetadata(accountIdentifier, samlSSOId);
    return new RestResponse<>(response);
  }

  @PUT
  @Path("/saml-metadata-upload/{samlSSOId}/authentication")
  @ApiOperation(value = "Enables or disables authentication for the given SAML sso id",
      nickname = "enableDisableAuthenticationForSAMLSetting")
  @Operation(operationId = "enableDisableAuthenticationForSAMLSetting",
      summary = "Update authentication enabled or not for given SAML setting",
      description = "Updates if authentication is enabled or not for given SAML setting in Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "default",
            description = "Successfully updated login allowed status for SAML setting in account")
      })
  public RestResponse<Boolean>
  enableDisableLoginForSAMLSetting(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                       NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountIdentifier,
      @NotNull @QueryParam("enable") @DefaultValue("true") Boolean enable,
      @Parameter(description = "Saml Settings Identifier") @PathParam("samlSSOId") String samlSSOId) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    authenticationSettingsService.updateAuthenticationForSAMLSetting(accountIdentifier, samlSSOId, enable);
    return new RestResponse<>(true);
  }

  @GET
  @Path("/saml-login-test")
  @ApiOperation(value = "Get SAML Login Test", nickname = "getSamlLoginTest")
  @Operation(operationId = "getSamlLoginTest", summary = "Test SAML connectivity",
      description = "Tests SAML connectivity for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns connectivity status")
      })
  public RestResponse<LoginTypeResponse>
  getSamlLoginTest(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam("accountId") @NotNull String accountId) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, null, null), Resource.of(AUTHSETTING, null), VIEW_AUTHSETTING_PERMISSION);
    LoginTypeResponse response = authenticationSettingsService.getSAMLLoginTest(accountId);
    return new RestResponse<>(response);
  }

  @GET
  @Path("/saml-login-test/{samlSSOId}")
  @ApiOperation(value = "Get SAML Login Test", nickname = "getSamlLoginTestV2")
  @Operation(operationId = "getSamlLoginTestV2", summary = "Test SAML connectivity",
      description = "Tests SAML connectivity for the given Account ID and SAML setting.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns connectivity status")
      })
  public RestResponse<LoginTypeResponse>
  getSamlLoginTestV2(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @NotNull String accountId,
      @Parameter(description = "Saml Settings Identifier") @PathParam("samlSSOId") String samlSSOId) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, null, null), Resource.of(AUTHSETTING, null), VIEW_AUTHSETTING_PERMISSION);
    LoginTypeResponse response = authenticationSettingsService.getSAMLLoginTestV2(accountId, samlSSOId);
    return new RestResponse<>(response);
  }

  @GET
  @Path("/ldap/settings")
  @ApiOperation(value = "Get Ldap settings", nickname = "getLdapSettings")
  @Operation(operationId = "getLdapSettings", summary = "Return configured Ldap settings for the account",
      description = "Returns configured Ldap settings and its details for the account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Returns ldap setting")
      })
  public RestResponse<LDAPSettings>
  getLdapSettings(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @NotBlank String accountId) {
    LDAPSettings settings = authenticationSettingsService.getLdapSettings(accountId);
    return new RestResponse<>(settings);
  }

  @POST
  @Path("/ldap/settings")
  @ApiOperation(value = "Create Ldap settings - with user queries, group queries", nickname = "createLdapSettings")
  @Operation(operationId = "createLdapSettings", summary = "Create Ldap setting",
      description = "Creates Ldap settings along with the user, group queries.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Creates Ldap settings along with the user, group queries")
      })
  public RestResponse<LDAPSettings>
  createLdapSettings(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @NotBlank String accountId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
          description =
              "Create LdapSettings request body. Values for connection settings are needed, user and group settings can also be provided")
      LDAPSettings ldapSettings) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    LDAPSettings settings = authenticationSettingsService.createLdapSettings(accountId, ldapSettings);
    return new RestResponse<>(settings);
  }

  @PUT
  @Path("/ldap/settings")
  @ApiOperation(value = "Update Ldap settings - user queries, group queries", nickname = "updateLdapSettings")
  @Operation(operationId = "updateLdapSettings", summary = "Updates Ldap setting",
      description = "Updates configured Ldap settings along with the user, group queries.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Updated Ldap settings along with the user, group settings")
      })
  public RestResponse<LDAPSettings>
  updateLdapSettings(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                         NGCommonEntityConstants.ACCOUNT_KEY) @NotBlank String accountId,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
          description =
              "This is the updated LdapSettings. Values for all fields is needed, not just the fields you are updating")
      LDAPSettings ldapSettings) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    LDAPSettings settings = authenticationSettingsService.updateLdapSettings(accountId, ldapSettings);
    return new RestResponse<>(settings);
  }

  @DELETE
  @Path("/ldap/settings")
  @ApiOperation(value = "Delete Ldap settings", nickname = "deleteLdapSettings")
  @Operation(operationId = "deleteLdapSettings", summary = "Delete Ldap settings",
      description = "Delete configured Ldap settings on this account.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully deleted Ldap settings configured on account")
      })
  public RestResponse<Boolean>
  deleteLdapSettings(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
      NGCommonEntityConstants.ACCOUNT_KEY) @NotBlank String accountId) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountId, null, null), Resource.of(AUTHSETTING, null), DELETE_AUTHSETTING_PERMISSION);
    authenticationSettingsService.deleteLdapSettings(accountId);
    return new RestResponse<>(true);
  }

  @PUT
  @Path("/two-factor-admin-override-settings")
  @ApiOperation(value = "Set account level two factor auth setting", nickname = "setTwoFactorAuthAtAccountLevel")
  @Operation(operationId = "setTwoFactorAuthAtAccountLevel", summary = "Set two factor authorization",
      description = "Sets Two-Factor authorization for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "default", description = "Successfully configured two factor authorization for an account")
      })
  public RestResponse<Boolean>
  setTwoFactorAuthAtAccountLevel(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                     "accountIdentifier") @NotNull String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          required = true, description = "Boolean that specify whether or not to override two factor enabled setting")
      TwoFactorAdminOverrideSettings twoFactorAdminOverrideSettings) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    boolean response =
        authenticationSettingsService.setTwoFactorAuthAtAccountLevel(accountIdentifier, twoFactorAdminOverrideSettings);
    return new RestResponse<>(response);
  }

  @PUT
  @Path("/session-timeout-account-level")
  @ApiOperation(value = "Set account level session timeout", nickname = "setSessionTimeoutAtAccountLevel")
  @Operation(operationId = "setSessionTimeoutAtAccountLevel", summary = "Set session timeout at account level",
      description = "Sets session timeout of all users for the given Account ID.",
      responses =
      {
        @io.swagger.v3.oas.annotations.responses.
        ApiResponse(responseCode = "default", description = "Successfully configured session timeout for an account")
      })
  public RestResponse<Boolean>
  setSessionTimeoutAtAccountLevel(@Parameter(description = ACCOUNT_PARAM_MESSAGE) @QueryParam(
                                      "accountIdentifier") @NotNull String accountIdentifier,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
          description = "Information about the session timeout for all users of this account in minutes.") @NotNull
      @Valid SessionTimeoutSettings sessionTimeoutSettings) {
    accessControlClient.checkForAccessOrThrow(
        ResourceScope.of(accountIdentifier, null, null), Resource.of(AUTHSETTING, null), EDIT_AUTHSETTING_PERMISSION);
    boolean response =
        authenticationSettingsService.setSessionTimeoutAtAccountLevel(accountIdentifier, sessionTimeoutSettings);
    return new RestResponse<>(response);
  }

  private MultipartBody.Part getMultipartBodyFromInputStream(InputStream uploadedInputStream) throws IOException {
    return uploadedInputStream == null ? null
                                       : MultipartBody.Part.createFormData("file", null,
                                           RequestBody.create(MultipartBody.FORM,
                                               IOUtils.toByteArray(new BoundedInputStream(uploadedInputStream,
                                                   mainConfiguration.getFileUploadLimits().getCommandUploadLimit()))));
  }
}
