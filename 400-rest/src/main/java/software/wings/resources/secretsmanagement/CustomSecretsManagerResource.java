/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.secretsmanagement;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRET_MANAGERS;
import static software.wings.security.PermissionAttribute.ResourceType.SETTING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.rest.RestResponse;

import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.security.encryption.secretsmanagerconfigs.CustomSecretsManagerConfig;
import software.wings.service.intfc.security.CustomSecretsManagerService;

import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Api("custom-secrets-managers")
@Path("/custom-secrets-managers")
@Produces("application/json")
@Scope(SETTING)
@AuthRule(permissionType = MANAGE_SECRET_MANAGERS)
@Slf4j
public class CustomSecretsManagerResource {
  private CustomSecretsManagerService customSecretsManagerService;
  private FeatureFlagService featureFlagService;

  @Inject
  CustomSecretsManagerResource(
      CustomSecretsManagerService customSecretsManagerService, FeatureFlagService featureFlagService) {
    this.customSecretsManagerService = customSecretsManagerService;
    this.featureFlagService = featureFlagService;
  }

  @GET
  @Path("{configId}")
  public RestResponse<CustomSecretsManagerConfig> getCustomSecretsManagerConfig(
      @QueryParam("accountId") String accountId, @PathParam("configId") String configId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(customSecretsManagerService.getSecretsManager(accountId, configId));
    }
  }

  @POST
  @Path("validate")
  public RestResponse<Boolean> validateCustomSecretsManagerConfig(
      @QueryParam("accountId") String accountId, CustomSecretsManagerConfig customSecretsManagerConfig) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(
          customSecretsManagerService.validateSecretsManager(accountId, customSecretsManagerConfig));
    }
  }

  @PUT
  public RestResponse<String> saveCustomSecretsManagerConfig(
      @QueryParam("accountId") String accountId, CustomSecretsManagerConfig customSecretsManagerConfig) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(customSecretsManagerService.saveSecretsManager(accountId, customSecretsManagerConfig));
    }
  }

  @POST
  @Path("{configId}")
  public RestResponse<String> updateCustomSecretsManagerConfig(@QueryParam("accountId") String accountId,
      @PathParam("configId") String configId, CustomSecretsManagerConfig customSecretsManagerConfig) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      customSecretsManagerConfig.setUuid(configId);
      return new RestResponse<>(
          customSecretsManagerService.updateSecretsManager(accountId, customSecretsManagerConfig));
    }
  }

  @DELETE
  @Path("{configId}")
  public RestResponse<Boolean> deleteCustomSecretsManagerConfig(
      @QueryParam("accountId") String accountId, @PathParam("configId") String configId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      return new RestResponse<>(customSecretsManagerService.deleteSecretsManager(accountId, configId));
    }
  }
}
