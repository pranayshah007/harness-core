/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.secretsmanagement;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.SECRET_MANAGEMENT_ERROR;
import static io.harness.exception.WingsException.SRE;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRETS;
import static software.wings.security.PermissionAttribute.ResourceType.SETTING;

import static javax.ws.rs.core.MediaType.MULTIPART_FORM_DATA;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EncryptedData;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.beans.SecretChangeLog;
import io.harness.beans.SecretFile;
import io.harness.beans.SecretManagerConfig;
import io.harness.beans.SecretText;
import io.harness.beans.SecretUsageLog;
import io.harness.exception.SecretManagementException;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.persistence.UuidAware;
import io.harness.rest.RestResponse;
import io.harness.secrets.setupusage.SecretSetupUsage;
import io.harness.secrets.validation.BaseSecretValidator;
import io.harness.security.encryption.AdditionalMetadata;
import io.harness.security.encryption.EncryptionType;
import io.harness.serializer.JsonUtils;

import software.wings.app.MainConfiguration;
import software.wings.beans.SettingAttribute;
import software.wings.security.PermissionAttribute;
import software.wings.security.UsageRestrictions;
import software.wings.security.annotations.ApiKeyAuthorized;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.security.EncryptedSettingAttributes;
import software.wings.service.intfc.security.SecretManager;
import software.wings.settings.SettingVariableTypes;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import lombok.AllArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.glassfish.jersey.media.multipart.FormDataParam;
import retrofit2.http.Body;

/**
 * Created by rsingh on 10/30/17.
 */
@OwnedBy(PL)
@Api("secrets")
@Path("/secrets")
@Produces("application/json")
@Consumes("application/json")
@Scope(SETTING)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class SecretManagementResource {
  private final SecretManager secretManager;
  private final UsageRestrictionsService usageRestrictionsService;
  private final MainConfiguration configuration;
  private final EncryptedSettingAttributes encryptedSettingAttributes;

  @GET
  @Path("/usage")
  public RestResponse<PageResponse<SecretUsageLog>> getUsageLogs(@BeanParam PageRequest<SecretUsageLog> pageRequest,
      @QueryParam("accountId") final String accountId, @QueryParam("entityId") final String entityId,
      @QueryParam("type") final SettingVariableTypes variableType) throws IllegalAccessException {
    return new RestResponse<>(secretManager.getUsageLogs(pageRequest, accountId, entityId, variableType));
  }

  @GET
  @Path("/change-logs")
  public RestResponse<List<SecretChangeLog>> getChangeLogs(@QueryParam("accountId") final String accountId,
      @QueryParam("entityId") final String entityId, @QueryParam("type") final SettingVariableTypes variableType)
      throws IllegalAccessException {
    return new RestResponse<>(secretManager.getChangeLogs(accountId, entityId, variableType));
  }

  @GET
  @Path("/list-values")
  public RestResponse<Collection<SettingAttribute>> listEncryptedSettingAttributes(
      @QueryParam("accountId") final String accountId, @QueryParam("category") String category) {
    if (isEmpty(category)) {
      return new RestResponse<>(encryptedSettingAttributes.listEncryptedSettingAttributes(accountId));
    } else {
      return new RestResponse<>(encryptedSettingAttributes.listEncryptedSettingAttributes(
          accountId, Sets.newHashSet(category.toUpperCase())));
    }
  }

  @GET
  @Path("/list-configs")
  @ApiKeyAuthorized(permissionType = MANAGE_SECRETS, action = PermissionAttribute.Action.READ)
  public RestResponse<List<SecretManagerConfig>> listEncryptionConfig(@QueryParam("accountId") final String accountId) {
    return new RestResponse<>(secretManager.listSecretManagers(accountId));
  }

  @GET
  @Path("/get-config")
  public RestResponse<SecretManagerConfig> getEncryptionConfig(@QueryParam("accountId") final String accountId,
      @QueryParam("secretsManagerConfigId") final String secretsManagerConfigId) {
    return new RestResponse<>(secretManager.getSecretManager(accountId, secretsManagerConfigId));
  }

  @GET
  @Path("/create-secret-allowed-scopes")
  public RestResponse<UsageRestrictions> getAllowedUsageScopesToCreateSecret(
      @QueryParam("accountId") final String accountId,
      @QueryParam("secretsManagerConfigId") final String secretsManagerConfigId) {
    return new RestResponse<>(secretManager.getAllowedUsageScopesToCreateSecret(accountId, secretsManagerConfigId));
  }

  /*
   * Deprecated, use PUT /transition-config call instead of GET call
   * Templatized secret managers are not supported using this call.
   */
  @GET
  @Path("/transition-config")
  @AuthRule(permissionType = MANAGE_SECRETS)
  @Deprecated
  public RestResponse<Boolean> transitionSecrets(@QueryParam("accountId") final String accountId,
      @QueryParam("fromEncryptionType") EncryptionType fromEncryptionType, @QueryParam("fromKmsId") String fromKmsId,
      @QueryParam("toEncryptionType") EncryptionType toEncryptionType, @QueryParam("toKmsId") String toKmsId) {
    return new RestResponse<>(secretManager.transitionSecrets(
        accountId, fromEncryptionType, fromKmsId, toEncryptionType, toKmsId, new HashMap<>(), new HashMap<>()));
  }

  @PUT
  @Path("/transition-config")
  @AuthRule(permissionType = MANAGE_SECRETS)
  public RestResponse<Boolean> transitionSecrets(@QueryParam("accountId") final String accountId,
      @QueryParam("fromEncryptionType") EncryptionType fromEncryptionType, @QueryParam("fromKmsId") String fromKmsId,
      @QueryParam("toEncryptionType") EncryptionType toEncryptionType, @QueryParam("toKmsId") String toKmsId,
      @Body @NonNull Map<String, Map<String, String>> runtimeParameters) {
    return new RestResponse<>(secretManager.transitionSecrets(accountId, fromEncryptionType, fromKmsId,
        toEncryptionType, toKmsId, runtimeParameters.getOrDefault(fromKmsId, new HashMap<>()),
        runtimeParameters.getOrDefault(toKmsId, new HashMap<>())));
  }

  @POST
  @Path("/import-secrets")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  @Produces(MediaType.APPLICATION_JSON)
  public RestResponse<List<String>> importSecrets(
      @QueryParam("accountId") final String accountId, @FormDataParam("file") final InputStream uploadInputStream) {
    return new RestResponse<>(secretManager.importSecretsViaFile(accountId, uploadInputStream));
  }

  @POST
  @Path("/add-secret")
  @AuthRule(permissionType = MANAGE_SECRETS)
  @ApiKeyAuthorized(permissionType = MANAGE_SECRETS, action = PermissionAttribute.Action.CREATE)
  public RestResponse<String> saveSecret(@QueryParam("accountId") final String accountId, @Body SecretText secretText) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Adding a secret");
      return new RestResponse<>(secretManager.saveSecretText(accountId, secretText, true));
    }
  }

  @POST
  @Path("/add-local-secret")
  @AuthRule(permissionType = MANAGE_SECRETS)
  public RestResponse<String> saveSecretUsingLocalMode(
      @QueryParam("accountId") final String accountId, @Body SecretText secretText) {
    return new RestResponse<>(secretManager.saveSecretUsingLocalMode(accountId, secretText));
  }

  @POST
  @Path("/update-secret")
  @AuthRule(permissionType = MANAGE_SECRETS)
  @ApiKeyAuthorized(permissionType = MANAGE_SECRETS, action = PermissionAttribute.Action.UPDATE)
  public RestResponse<Boolean> updateSecret(@QueryParam("accountId") final String accountId,
      @QueryParam("uuid") final String uuid, @Body SecretText secretText) {
    return new RestResponse<>(secretManager.updateSecretText(accountId, uuid, secretText, true));
  }

  @DELETE
  @Path("/delete-secret")
  @AuthRule(permissionType = MANAGE_SECRETS)
  @Deprecated
  public RestResponse<Boolean> deleteSecret(
      @QueryParam("accountId") final String accountId, @QueryParam("uuid") final String uuId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Deleting a secret");
      return new RestResponse<>(secretManager.deleteSecret(accountId, uuId, new HashMap<>(), true));
    }
  }

  /*
   * Ideally call for deleting a resource shouldn't use post method, but we need to get run time parameters
   * and UI libraries don't support body in a DELETE call. We could have sent run time parameters as query parameter,
   * but since these are credentials, there is a chance of these getting exposed in places like Nginx logs,
   * load balancer logs etc.
   */
  @POST
  @Path("/delete-secret")
  @AuthRule(permissionType = MANAGE_SECRETS)
  @ApiKeyAuthorized(permissionType = MANAGE_SECRETS, action = PermissionAttribute.Action.DELETE)
  public RestResponse<Boolean> deleteSecret(@QueryParam("accountId") final String accountId,
      @QueryParam("uuid") final String uuId, @Body Map<String, String> runtimeParameters) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Deleting a secret");
      return new RestResponse<>(secretManager.deleteSecret(accountId, uuId, runtimeParameters, true));
    }
  }

  @POST
  @Path("/add-file")
  @Consumes(MULTIPART_FORM_DATA)
  @AuthRule(permissionType = MANAGE_SECRETS)
  public RestResponse<String> saveFile(@Context HttpServletRequest request,
      @QueryParam("accountId") final String accountId, @Nullable @FormDataParam("kmsId") final String kmsId,
      @FormDataParam("name") final String name, @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("usageRestrictions") final String usageRestrictionsString,
      @FormDataParam("additionalMetadata") final String additionalMetadata,
      @FormDataParam("runtimeParameters") final String runtimeParametersString,
      @FormDataParam("scopedToAccount") final boolean scopedToAccount,
      @FormDataParam("inheritScopesFromSM") final boolean inheritScopesFromSM) throws IOException {
    Map<String, String> runtimeParameters = new HashMap<>();
    if (!StringUtils.isEmpty(runtimeParametersString)) {
      runtimeParameters = JsonUtils.asObject(runtimeParametersString, new TypeReference<Map<String, String>>() {});
    }
    BaseSecretValidator.validateFileWithinSizeLimit(
        request.getContentLengthLong(), configuration.getFileUploadLimits().getEncryptedFileLimit());
    AdditionalMetadata additionalMetadataObj = isNotEmpty(additionalMetadata)
        ? JsonUtils.asObject(additionalMetadata, AdditionalMetadata.class)
        : AdditionalMetadata.builder().build();
    SecretFile secretFile =
        SecretFile.builder()
            .fileContent(ByteStreams.toByteArray(uploadedInputStream))
            .name(name)
            .additionalMetadata(additionalMetadataObj)
            .kmsId(kmsId)
            .hideFromListing(false)
            .scopedToAccount(scopedToAccount)
            .runtimeParameters(runtimeParameters)
            .usageRestrictions(usageRestrictionsService.getUsageRestrictionsFromJson(usageRestrictionsString))
            .inheritScopesFromSM(inheritScopesFromSM)
            .build();

    return new RestResponse<>(secretManager.saveSecretFile(accountId, secretFile));
  }

  @POST
  @Path("/update-file")
  @Consumes(MULTIPART_FORM_DATA)
  @AuthRule(permissionType = MANAGE_SECRETS)
  public RestResponse<Boolean> updateFile(@Context HttpServletRequest request,
      @QueryParam("accountId") final String accountId, @FormDataParam("name") final String name,
      @FormDataParam("usageRestrictions") final String usageRestrictionsString,
      @FormDataParam("additionalMetadata") final String additionalMetadata,
      @FormDataParam("runtimeParameters") final String runtimeParametersString,
      @FormDataParam("uuid") final String fileId, @FormDataParam("file") InputStream uploadedInputStream,
      @FormDataParam("scopedToAccount") final boolean scopedToAccount,
      @FormDataParam("inheritScopesFromSM") final boolean inheritScopesFromSM) throws IOException {
    Map<String, String> runtimeParameters = new HashMap<>();
    if (!StringUtils.isEmpty(runtimeParametersString)) {
      runtimeParameters = JsonUtils.asObject(runtimeParametersString, new TypeReference<Map<String, String>>() {});
    }
    BaseSecretValidator.validateFileWithinSizeLimit(
        request.getContentLengthLong(), configuration.getFileUploadLimits().getEncryptedFileLimit());
    AdditionalMetadata additionalMetadataObj = isNotEmpty(additionalMetadata)
        ? JsonUtils.asObject(additionalMetadata, AdditionalMetadata.class)
        : AdditionalMetadata.builder().build();
    SecretFile secretFile =
        SecretFile.builder()
            .fileContent(uploadedInputStream == null ? null : ByteStreams.toByteArray(uploadedInputStream))
            .name(name)
            .hideFromListing(false)
            .scopedToAccount(scopedToAccount)
            .additionalMetadata(additionalMetadataObj)
            .runtimeParameters(runtimeParameters)
            .usageRestrictions(usageRestrictionsService.getUsageRestrictionsFromJson(usageRestrictionsString))
            .inheritScopesFromSM(inheritScopesFromSM)
            .build();
    return new RestResponse<>(secretManager.updateSecretFile(accountId, fileId, secretFile));
  }

  /*
   * Deprecated, use POST call since this API requires runtimeParameters too
   */
  @DELETE
  @Path("/delete-file")
  @AuthRule(permissionType = MANAGE_SECRETS)
  @Deprecated
  public RestResponse<Boolean> deleteFile(
      @QueryParam("accountId") final String accountId, @QueryParam("uuid") final String existingRecordId) {
    return new RestResponse<>(secretManager.deleteSecret(accountId, existingRecordId, new HashMap<>(), true));
  }

  @POST
  @Path("/delete-file")
  @AuthRule(permissionType = MANAGE_SECRETS)
  public RestResponse<Boolean> deleteFilePost(@QueryParam("accountId") final String accountId,
      @QueryParam("uuid") final String existingRecordId, @Body Map<String, String> runtimeParameters) {
    return new RestResponse<>(secretManager.deleteSecret(accountId, existingRecordId, runtimeParameters, true));
  }

  @GET
  @Path("/list-secrets-page")
  @ApiKeyAuthorized(permissionType = MANAGE_SECRETS, action = PermissionAttribute.Action.READ)
  public RestResponse<PageResponse<EncryptedData>> listSecrets(@QueryParam("accountId") final String accountId,
      @QueryParam("type") final SettingVariableTypes type, @QueryParam("currentAppId") String currentAppId,
      @QueryParam("currentEnvId") String currentEnvId, @DefaultValue("true") @QueryParam("details") boolean details,
      @BeanParam PageRequest<EncryptedData> pageRequest) {
    try {
      return new RestResponse<>(
          secretManager.listSecrets(accountId, pageRequest, currentAppId, currentEnvId, details, false));
    } catch (IllegalAccessException e) {
      log.error("Illegal access exception while trying to get secrets", e);
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, SRE);
    }
  }

  @GET
  @Path("/list-account-secrets")
  public RestResponse<PageResponse<EncryptedData>> listSecrets(@QueryParam("accountId") final String accountId,
      @QueryParam("type") final SettingVariableTypes type, @DefaultValue("true") @QueryParam("details") boolean details,
      @BeanParam PageRequest<EncryptedData> pageRequest) {
    try {
      pageRequest.addFilter("type", Operator.EQ, type);
      pageRequest.addFilter("accountId", Operator.EQ, accountId);
      return new RestResponse<>(secretManager.listSecretsMappedToAccount(accountId, pageRequest, details));
    } catch (IllegalAccessException e) {
      log.error("Illegal access exception while trying to get secrets", e);
      throw new SecretManagementException(SECRET_MANAGEMENT_ERROR, SRE);
    }
  }

  @GET
  @Path("/list-secret-usage")
  public RestResponse<List<UuidAware>> getSecretUsage(
      @QueryParam("accountId") final String accountId, @QueryParam("uuid") final String secretId) {
    Set<SecretSetupUsage> setupUsages = secretManager.getSecretUsage(accountId, secretId);
    return new RestResponse<>(setupUsages.stream().map(SecretSetupUsage::getEntity).collect(Collectors.toList()));
  }

  @GET
  @Path("/list-setup-usage")
  public RestResponse<Set<SecretSetupUsage>> getSecretSetupUsage(
      @QueryParam("accountId") final String accountId, @QueryParam("uuid") final String secretId) {
    return new RestResponse<>(secretManager.getSecretUsage(accountId, secretId));
  }
}
