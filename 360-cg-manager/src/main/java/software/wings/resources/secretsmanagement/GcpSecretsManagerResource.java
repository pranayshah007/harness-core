/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.secretsmanagement;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.beans.Account.GLOBAL_ACCOUNT_ID;
import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRET_MANAGERS;
import static software.wings.security.PermissionAttribute.ResourceType.SETTING;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;
import io.harness.security.annotations.InternalApi;
import io.harness.security.encryption.EncryptionType;
import io.harness.stream.BoundedInputStream;

import software.wings.app.MainConfiguration;
import software.wings.beans.GcpKmsConfig;
import software.wings.security.UsageRestrictions;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.security.GcpSecretsManagerService;
import software.wings.utils.AccountPermissionUtils;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Hidden;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.media.multipart.FormDataParam;

@OwnedBy(PL)
@Api("gcp-secrets-manager")
@Path("/gcp-secrets-manager")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces("application/json")
@Scope(SETTING)
@AuthRule(permissionType = MANAGE_SECRET_MANAGERS)
public class GcpSecretsManagerResource {
  private GcpSecretsManagerService gcpSecretsManagerService;
  private AccountPermissionUtils accountPermissionUtils;
  private MainConfiguration configuration;
  private UsageRestrictionsService usageRestrictionsService;

  @Inject
  public GcpSecretsManagerResource(GcpSecretsManagerService gcpSecretsManagerService,
      AccountPermissionUtils accountPermissionUtils, MainConfiguration mainConfiguration,
      UsageRestrictionsService usageRestrictionsService) {
    this.gcpSecretsManagerService = gcpSecretsManagerService;
    this.accountPermissionUtils = accountPermissionUtils;
    this.configuration = mainConfiguration;
    this.usageRestrictionsService = usageRestrictionsService;
  }

  @PUT
  @Path("/global-kms")
  public RestResponse<String> saveGlobalKmsConfig(@QueryParam("accountId") final String accountId,
      @FormDataParam("name") String name, @FormDataParam("keyName") String keyName,
      @FormDataParam("keyRing") String keyRing, @FormDataParam("projectId") String projectId,
      @FormDataParam("region") String region, @FormDataParam("encryptionType") EncryptionType encryptionType,
      @FormDataParam("isDefault") boolean isDefault, @FormDataParam("usageRestrictions") String usageRestrictionsString,
      @FormDataParam("credentials") InputStream uploadedInputStream,
      @FormDataParam("delegateSelectors") Set<String> delegateSelectors) throws IOException {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      UsageRestrictions usageRestrictions =
          usageRestrictionsService.getUsageRestrictionsFromJson(usageRestrictionsString);
      BoundedInputStream boundedInputStream =
          new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getEncryptedFileLimit());
      char[] credentials = IOUtils.toString(boundedInputStream, Charset.defaultCharset()).toCharArray();
      GcpKmsConfig gcpKmsConfig =
          new GcpKmsConfig(name, projectId, region, keyRing, keyName, credentials, delegateSelectors);
      gcpKmsConfig.setDefault(isDefault);
      gcpKmsConfig.setEncryptionType(encryptionType);
      gcpKmsConfig.setUsageRestrictions(usageRestrictions);
      RestResponse<String> response = accountPermissionUtils.checkIfHarnessUser("User not allowed to save global KMS");
      if (response == null) {
        response = new RestResponse<>(gcpSecretsManagerService.saveGcpKmsConfig(GLOBAL_ACCOUNT_ID, gcpKmsConfig, true));
      }
      return response;
    }
  }

  @POST
  @Path("/global-kms")
  @ApiOperation(value = "This is used to update global kms", hidden = true)
  @InternalApi
  @Hidden
  public RestResponse<String> updateGlobalKmsConfig(@QueryParam("accountId") final String accountId,
      @FormDataParam("uuid") String uuid, @FormDataParam("name") String name, @FormDataParam("keyName") String keyName,
      @FormDataParam("keyRing") String keyRing, @FormDataParam("projectId") String projectId,
      @FormDataParam("region") String region, @FormDataParam("isDefault") boolean isDefault,
      @FormDataParam("usageRestrictions") String usageRestrictionsString,
      @FormDataParam("credentials") InputStream uploadedInputStream,
      @FormDataParam("delegateSelectors") Set<String> delegateSelectors) throws IOException {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      RestResponse<String> response = accountPermissionUtils.checkIfHarnessUser("User not allowed to save global KMS");
      if (response != null) {
        return response;
      }
      UsageRestrictions usageRestrictions =
          usageRestrictionsService.getUsageRestrictionsFromJson(usageRestrictionsString);
      BoundedInputStream boundedInputStream =
          new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getEncryptedFileLimit());
      char[] credentials = IOUtils.toString(boundedInputStream, Charset.defaultCharset()).toCharArray();
      GcpKmsConfig gcpKmsConfig =
          new GcpKmsConfig(name, projectId, region, keyRing, keyName, credentials, delegateSelectors);
      gcpKmsConfig.setDefault(isDefault);
      gcpKmsConfig.setEncryptionType(EncryptionType.GCP_KMS);
      gcpKmsConfig.setUuid(uuid);
      gcpKmsConfig.setUsageRestrictions(usageRestrictions);
      return new RestResponse<>(gcpSecretsManagerService.updateGcpKmsConfig(GLOBAL_ACCOUNT_ID, gcpKmsConfig, true));
    }
  }

  @POST
  @Path("/{secretMangerId}")
  public RestResponse<String> updateGcpSecretsManagerConfig(@QueryParam("accountId") final String accountId,
      @PathParam("secretMangerId") final String secretMangerId, @FormDataParam("name") String name,
      @FormDataParam("keyName") String keyName, @FormDataParam("keyRing") String keyRing,
      @FormDataParam("projectId") String projectId, @FormDataParam("region") String region,
      @FormDataParam("encryptionType") EncryptionType encryptionType, @FormDataParam("isDefault") boolean isDefault,
      @FormDataParam("usageRestrictions") String usageRestrictionsString,
      @FormDataParam("credentials") InputStream uploadedInputStream,
      @FormDataParam("delegateSelectors") Set<String> delegateSelectors) throws IOException {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      UsageRestrictions usageRestrictions =
          usageRestrictionsService.getUsageRestrictionsFromJson(usageRestrictionsString);
      BoundedInputStream boundedInputStream =
          new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getEncryptedFileLimit());
      char[] credentials = IOUtils.toString(boundedInputStream, Charset.defaultCharset()).toCharArray();
      GcpKmsConfig gcpKmsConfig =
          new GcpKmsConfig(name, projectId, region, keyRing, keyName, credentials, delegateSelectors);
      gcpKmsConfig.setUuid(secretMangerId);
      gcpKmsConfig.setDefault(isDefault);
      gcpKmsConfig.setEncryptionType(encryptionType);
      gcpKmsConfig.setUsageRestrictions(usageRestrictions);
      GcpKmsConfig globalKmsConfig = gcpSecretsManagerService.getGlobalKmsConfig();
      if (!isNull(globalKmsConfig) && globalKmsConfig.getUuid().equals(secretMangerId)) {
        return accountPermissionUtils.getErrorResponse("User not allowed to update global KMS");
      } else {
        return new RestResponse<>(gcpSecretsManagerService.updateGcpKmsConfig(accountId, gcpKmsConfig));
      }
    }
  }

  @POST
  public RestResponse<String> saveGcpSecretsManagerConfig(@QueryParam("accountId") final String accountId,
      @FormDataParam("name") String name, @FormDataParam("keyName") String keyName,
      @FormDataParam("keyRing") String keyRing, @FormDataParam("projectId") String projectId,
      @FormDataParam("region") String region, @FormDataParam("encryptionType") EncryptionType encryptionType,
      @FormDataParam("isDefault") boolean isDefault, @FormDataParam("usageRestrictions") String usageRestrictionsString,
      @FormDataParam("credentials") InputStream uploadedInputStream,
      @FormDataParam("delegateSelectors") Set<String> delegateSelectors) throws IOException {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      UsageRestrictions usageRestrictions =
          usageRestrictionsService.getUsageRestrictionsFromJson(usageRestrictionsString);
      BoundedInputStream boundedInputStream =
          new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getEncryptedFileLimit());
      char[] credentials = IOUtils.toString(boundedInputStream, Charset.defaultCharset()).toCharArray();
      GcpKmsConfig gcpKmsConfig =
          new GcpKmsConfig(name, projectId, region, keyRing, keyName, credentials, delegateSelectors);
      gcpKmsConfig.setDefault(isDefault);
      gcpKmsConfig.setEncryptionType(encryptionType);
      gcpKmsConfig.setUsageRestrictions(usageRestrictions);
      return new RestResponse<>(gcpSecretsManagerService.saveGcpKmsConfig(accountId, gcpKmsConfig, true));
    }
  }

  @DELETE
  public RestResponse<Boolean> deleteGcpSecretsManagerConfig(
      @QueryParam("accountId") final String accountId, @QueryParam("configId") final String secretsManagerConfigId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(gcpSecretsManagerService.deleteGcpKmsConfig(accountId, secretsManagerConfigId));
    }
  }
}
