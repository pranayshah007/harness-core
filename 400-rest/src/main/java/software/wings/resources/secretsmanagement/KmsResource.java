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
import io.harness.logging.logcontext.AccountLogContext;
import io.harness.logging.logcontext.AutoLogContext;
import io.harness.rest.RestResponse;

import software.wings.beans.KmsConfig;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.Scope;
import software.wings.service.intfc.security.KmsService;
import software.wings.utils.AccountPermissionUtils;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.inject.Inject;
import io.swagger.annotations.Api;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by rsingh on 10/10/17.
 */
@Api("kms")
@Path("/kms")
@Produces("application/json")
@Scope(SETTING)
@AuthRule(permissionType = MANAGE_SECRET_MANAGERS)
@Slf4j
@OwnedBy(PL)
public class KmsResource {
  @Inject private KmsService kmsService;
  @Inject private AccountPermissionUtils accountPermissionUtils;
  @Inject private FeatureFlagService featureFlagService;

  @POST
  @Path("/save-global-kms")
  @Timed
  @ExceptionMetered
  public RestResponse<String> saveGlobalKmsConfig(
      @QueryParam("accountId") final String accountId, KmsConfig kmsConfig) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Adding Global KMS Secret Manager");
      RestResponse<String> response = accountPermissionUtils.checkIfHarnessUser("User not allowed to save global KMS");
      if (response == null) {
        response = new RestResponse<>(kmsService.saveGlobalKmsConfig(accountId, kmsConfig));
      }
      return response;
    }
  }

  @POST
  @Path("/save-kms")
  @Timed
  @ExceptionMetered
  public RestResponse<String> saveKmsConfig(@QueryParam("accountId") final String accountId, KmsConfig kmsConfig) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Adding KMS Secret Manager");
      return new RestResponse<>(kmsService.saveKmsConfig(accountId, kmsConfig));
    }
  }

  @GET
  @Path("/delete-kms")
  @Timed
  @ExceptionMetered
  // TODO: Delete this method once UI switched to use the new endpoint below.
  public RestResponse<Boolean> deleteKmsConfig(
      @QueryParam("accountId") final String accountId, @QueryParam("kmsConfigId") final String kmsConfigId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Deleting KMS Secret Manager");
      return new RestResponse<>(kmsService.deleteKmsConfig(accountId, kmsConfigId));
    }
  }

  @DELETE
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteKmsConfig2(
      @QueryParam("accountId") final String accountId, @QueryParam("kmsConfigId") final String kmsConfigId) {
    try (AutoLogContext ignore = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Deleting KMS Secret Manager");
      return new RestResponse<>(kmsService.deleteKmsConfig(accountId, kmsConfigId));
    }
  }
}
