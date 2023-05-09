/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.secretsmanagement;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRET_MANAGERS;
import static software.wings.security.PermissionAttribute.ResourceType.SETTING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ff.FeatureFlagService;
import io.harness.rest.RestResponse;
import io.harness.security.encryption.EncryptionType;
import io.harness.stream.BoundedInputStream;

import software.wings.app.MainConfiguration;
import software.wings.beans.GcpSecretsManagerConfig;
import software.wings.security.UsageRestrictions;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.security.GcpSecretsManagerServiceV2;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.media.multipart.FormDataParam;

@OwnedBy(PL)
@Api("gcp-secrets-manager-v2")
@Path("/gcp-secrets-manager-v2")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@Produces("application/json")
@Scope(SETTING)
@AuthRule(permissionType = MANAGE_SECRET_MANAGERS)
public class GcpSecretsManagerResourceV2 {
  private final GcpSecretsManagerServiceV2 gcpSecretsManagerService;
  private final MainConfiguration configuration;
  private final UsageRestrictionsService usageRestrictionsService;
  private final FeatureFlagService featureFlagService;

  @Inject
  GcpSecretsManagerResourceV2(GcpSecretsManagerServiceV2 gcpSecretsManagerService, MainConfiguration mainConfiguration,
      UsageRestrictionsService usageRestrictionsService, FeatureFlagService featureFlagService) {
    this.gcpSecretsManagerService = gcpSecretsManagerService;
    this.configuration = mainConfiguration;
    this.usageRestrictionsService = usageRestrictionsService;
    this.featureFlagService = featureFlagService;
  }

  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<String> saveGcpSecretsManagerConfig(@QueryParam("accountId") final String accountId,
      @FormDataParam("name") String name, @FormDataParam("encryptionType") EncryptionType encryptionType,
      @FormDataParam("isDefault") boolean isDefault, @FormDataParam("usageRestrictions") String usageRestrictionsString,
      @FormDataParam("credentials") InputStream uploadedInputStream,
      @FormDataParam("delegateSelectors") Set<String> delegateSelectors) throws IOException {
    UsageRestrictions usageRestrictions =
        usageRestrictionsService.getUsageRestrictionsFromJson(usageRestrictionsString);
    BoundedInputStream boundedInputStream =
        new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getEncryptedFileLimit());
    char[] credentials = IOUtils.toString(boundedInputStream, Charset.defaultCharset()).toCharArray();
    GcpSecretsManagerConfig gcpSecretsManagerConfig = new GcpSecretsManagerConfig(name, credentials, delegateSelectors);
    gcpSecretsManagerConfig.setDefault(isDefault);
    gcpSecretsManagerConfig.setEncryptionType(encryptionType);
    gcpSecretsManagerConfig.setUsageRestrictions(usageRestrictions);
    return new RestResponse<>(
        gcpSecretsManagerService.saveGcpSecretsManagerConfig(accountId, gcpSecretsManagerConfig, true));
  }

  @POST
  @Path("/{secretManagerId}")
  @ExceptionMetered
  @Timed
  public RestResponse<String> updateGcpSecretsManagerConfig(@QueryParam("accountId") final String accountId,
      @PathParam("secretManagerId") final String secretManagerId, @FormDataParam("name") String name,
      @FormDataParam("encryptionType") EncryptionType encryptionType, @FormDataParam("isDefault") boolean isDefault,
      @FormDataParam("usageRestrictions") String usageRestrictionsString,
      @FormDataParam("credentials") InputStream uploadedInputStream,
      @FormDataParam("delegateSelectors") Set<String> delegateSelectors) throws IOException {
    UsageRestrictions usageRestrictions =
        usageRestrictionsService.getUsageRestrictionsFromJson(usageRestrictionsString);
    BoundedInputStream boundedInputStream =
        new BoundedInputStream(uploadedInputStream, configuration.getFileUploadLimits().getEncryptedFileLimit());
    char[] credentials = IOUtils.toString(boundedInputStream, Charset.defaultCharset()).toCharArray();
    GcpSecretsManagerConfig gcpSecretsManagerConfig = new GcpSecretsManagerConfig(name, credentials, delegateSelectors);
    gcpSecretsManagerConfig.setUuid(secretManagerId);
    gcpSecretsManagerConfig.setDefault(isDefault);
    gcpSecretsManagerConfig.setEncryptionType(encryptionType);
    gcpSecretsManagerConfig.setUsageRestrictions(usageRestrictions);
    return new RestResponse<>(
        gcpSecretsManagerService.updateGcpSecretsManagerConfig(accountId, gcpSecretsManagerConfig));
  }

  @DELETE
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteGcpSecretsManagerConfig(
      @QueryParam("accountId") final String accountId, @QueryParam("configId") final String secretsManagerConfigId) {
    return new RestResponse<>(
        gcpSecretsManagerService.deleteGcpSecretsManagerConfig(accountId, secretsManagerConfigId));
  }

  @GET
  @Timed
  @Path("/regions")
  @ExceptionMetered
  public RestResponse<List<String>> regions(
      @QueryParam("accountId") final String accountId, @QueryParam("configId") final String secretsManagerConfigId) {
    return new RestResponse<>(gcpSecretsManagerService.getAllAvailableRegions(accountId, secretsManagerConfigId));
  }
}
