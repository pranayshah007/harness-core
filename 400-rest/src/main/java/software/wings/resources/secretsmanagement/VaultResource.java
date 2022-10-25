/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.secretsmanagement;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.logging.logcontext.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.security.PermissionAttribute.PermissionType.MANAGE_SECRET_MANAGERS;
import static software.wings.security.PermissionAttribute.ResourceType.SETTING;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ff.FeatureFlagService;
import io.harness.helpers.ext.vault.SecretEngineSummary;
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.rest.RestResponse;

import software.wings.beans.VaultConfig;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.security.VaultService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import java.util.List;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 10/10/17.
 */
@OwnedBy(PL)
@Api("vault")
@Path("/vault")
@Produces("application/json")
@Scope(SETTING)
@AuthRule(permissionType = MANAGE_SECRET_MANAGERS)
@Slf4j
public class VaultResource {
  @Inject private VaultService vaultService;
  @Inject private FeatureFlagService featureFlagService;

  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<String> saveOrUpdateVaultConfig(
      @QueryParam("accountId") final String accountId, VaultConfig vaultConfig) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Adding a vault config");
      return new RestResponse<>(vaultService.saveOrUpdateVaultConfig(accountId, vaultConfig, true));
    }
  }

  @DELETE
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteVaultConfig(
      @QueryParam("accountId") final String accountId, @QueryParam("vaultConfigId") final String vaultConfigId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Deleting a vault config");
      return new RestResponse<>(vaultService.deleteVaultConfig(accountId, vaultConfigId));
    }
  }

  @POST
  @Path("list-engines")
  @Timed
  @ExceptionMetered
  public RestResponse<List<SecretEngineSummary>> listSecretEngines(
      @QueryParam("accountId") final String accountId, VaultConfig vaultConfig) {
    // checkIfFeatureIsEnabled(accountId, vaultConfig);
    vaultConfig.setAccountId(accountId);
    return new RestResponse<>(vaultService.listSecretEngines(vaultConfig));
  }
}
