/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.resources.dashboard;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.event.reconciliation.service.LookerEntityReconServiceHelper.performReconciliationViaAPI;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.mongo.MongoConfig.NO_LIMIT;

import static software.wings.security.PermissionAttribute.PermissionType.LOGGED_IN;
import static software.wings.security.PermissionAttribute.ResourceType.CUSTOM_DASHBOARD;

import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.PageResponse.PageResponseBuilder;
import io.harness.beans.SearchFilter.Operator;
import io.harness.configuration.DeployMode;
import io.harness.dashboard.Action;
import io.harness.dashboard.DashboardSettings;
import io.harness.dashboard.DashboardSettingsService;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.event.reconciliation.ReconciliationStatus;
import io.harness.event.reconciliation.service.DeploymentReconService;
import io.harness.event.reconciliation.service.DeploymentStepReconServiceImpl;
import io.harness.event.reconciliation.service.ExecutionInterruptReconServiceImpl;
import io.harness.event.timeseries.processor.DeploymentEventProcessor;
import io.harness.event.timeseries.processor.StepEventProcessor;
import io.harness.event.timeseries.processor.instanceeventprocessor.InstanceEventProcessor;
import io.harness.event.timeseries.processor.instanceeventprocessor.instancereconservice.IInstanceReconService;
import io.harness.event.timeseries.processor.instanceeventprocessor.instancereconservice.InstanceReconConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;
import io.harness.rest.RestResponse.Builder;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.User;
import software.wings.features.api.AccountId;
import software.wings.search.entities.deployment.DeploymentExecutionEntity;
import software.wings.search.framework.TimeScaleEntity;
import software.wings.security.PermissionAttribute.ResourceType;
import software.wings.security.UserThreadLocal;
import software.wings.security.annotations.AuthRule;
import software.wings.security.annotations.ListAPI;
import software.wings.security.annotations.Scope;
import software.wings.service.impl.DashboardLogContext;
import software.wings.service.impl.security.auth.DashboardAuthHandler;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.HarnessUserGroupService;

import com.codahale.metrics.annotation.ExceptionMetered;
import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.query.Query;
import io.swagger.annotations.Api;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotBlank;
import org.hibernate.validator.constraints.NotEmpty;

@Slf4j
@Api("custom-dashboard")
@Path("/custom-dashboard")
@Scope(CUSTOM_DASHBOARD)
@Produces(MediaType.APPLICATION_JSON)
public class CustomDashboardResource {
  private DashboardSettingsService dashboardSettingsService;
  private FeatureFlagService featureFlagService;
  private DashboardAuthHandler dashboardAuthHandler;
  private HarnessUserGroupService harnessUserGroupService;
  private DeploymentReconService deploymentReconService;
  private AccountService accountService;
  private InstanceEventProcessor instanceEventProcessor;
  private IInstanceReconService instanceReconService;
  private DeploymentEventProcessor deploymentEventProcessor;
  private DeploymentStepReconServiceImpl deploymentStepReconService;
  private ExecutionInterruptReconServiceImpl executionInterruptReconService;

  @Inject private Set<TimeScaleEntity<?>> timeScaleEntities;
  @Inject private DeploymentExecutionEntity deploymentExecutionEntity;
  @Inject @Named("CustomDashboardAPIExecutor") ExecutorService executorService;
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject HPersistence persistence;

  @Inject
  public CustomDashboardResource(DashboardSettingsService dashboardSettingsService,
      FeatureFlagService featureFlagService, DashboardAuthHandler dashboardAuthHandler,
      HarnessUserGroupService harnessUserGroupService, DeploymentReconService deploymentReconService,
      AccountService accountService, InstanceEventProcessor instanceEventProcessor,
      IInstanceReconService instanceReconService, DeploymentEventProcessor deploymentEventProcessor,
      DeploymentStepReconServiceImpl deploymentStepReconService,
      ExecutionInterruptReconServiceImpl executionInterruptReconService) {
    this.dashboardSettingsService = dashboardSettingsService;
    this.featureFlagService = featureFlagService;
    this.dashboardAuthHandler = dashboardAuthHandler;
    this.harnessUserGroupService = harnessUserGroupService;
    this.deploymentReconService = deploymentReconService;
    this.accountService = accountService;
    this.instanceEventProcessor = instanceEventProcessor;
    this.instanceReconService = instanceReconService;
    this.deploymentEventProcessor = deploymentEventProcessor;
    this.deploymentStepReconService = deploymentStepReconService;
    this.executionInterruptReconService = executionInterruptReconService;
  }

  @POST
  @Timed
  @ExceptionMetered
  public RestResponse<DashboardSettings> createDashboardSetting(
      @QueryParam("accountId") @NotBlank @AccountId String accountId, DashboardSettings settings) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      if (!featureFlagService.isEnabled(FeatureName.CUSTOM_DASHBOARD, settings.getAccountId())) {
        throw new InvalidRequestException("User not authorized", USER);
      }
      dashboardAuthHandler.authorizeDashboardCreation(settings, accountId);
      settings.setAccountId(accountId);
      return new RestResponse<>(dashboardSettingsService.createDashboardSettings(accountId, settings));
    }
  }

  @PUT
  @Timed
  @ExceptionMetered
  public RestResponse<DashboardSettings> updateDashboardSettings(
      @QueryParam("accountId") @NotBlank @AccountId String accountId, DashboardSettings settings) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DashboardLogContext(settings.getUuid(), OVERRIDE_ERROR)) {
      if (!featureFlagService.isEnabled(FeatureName.CUSTOM_DASHBOARD, settings.getAccountId())) {
        throw new InvalidRequestException("User not authorized", USER);
      }
      DashboardSettings existingDashboardSetting = dashboardSettingsService.get(accountId, settings.getUuid());

      if (existingDashboardSetting == null) {
        throw new InvalidRequestException(
            String.format("No existing dashboard found for update of dashboard: %s ", settings.getUuid()));
      }

      if (!dashboardSettingsService.doesPermissionsMatch(settings, existingDashboardSetting)) {
        dashboardAuthHandler.authorize(existingDashboardSetting, accountId, Action.MANAGE);
      } else {
        dashboardAuthHandler.authorize(existingDashboardSetting, accountId, Action.UPDATE);
      }
      settings.setAccountId(accountId);
      return new RestResponse<>(dashboardSettingsService.updateDashboardSettings(accountId, settings));
    }
  }

  @DELETE
  @Timed
  @ExceptionMetered
  public RestResponse<Boolean> deleteDashboardSettings(
      @QueryParam("accountId") @NotBlank @AccountId String accountId, @QueryParam("dashboardId") @NotBlank String id) {
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR);
         AutoLogContext ignore2 = new DashboardLogContext(id, OVERRIDE_ERROR)) {
      if (!featureFlagService.isEnabled(FeatureName.CUSTOM_DASHBOARD, accountId)) {
        throw new InvalidRequestException("User not authorized", USER);
      }

      DashboardSettings existingDashboardSetting = dashboardSettingsService.get(accountId, id);
      dashboardAuthHandler.authorize(existingDashboardSetting, accountId, Action.DELETE);
      return new RestResponse<>(dashboardSettingsService.deleteDashboardSettings(accountId, id));
    }
  }

  @GET
  @Timed
  @ListAPI(CUSTOM_DASHBOARD)
  @ExceptionMetered
  public RestResponse<PageResponse<DashboardSettings>> getDashboardSettings(
      @QueryParam("accountId") @NotBlank String accountId, @BeanParam PageRequest<Application> pageRequest) {
    if (!featureFlagService.isEnabled(FeatureName.CUSTOM_DASHBOARD, accountId)) {
      throw new InvalidRequestException("User not authorized", USER);
    }
    Set<String> allowedDashboardSettingIds = dashboardAuthHandler.getAllowedDashboardSettingIds();

    if (isEmpty(allowedDashboardSettingIds)) {
      return new RestResponse<>(
          PageResponseBuilder.aPageResponse().withTotal(0).withResponse(Collections.emptyList()).build());
    }

    pageRequest.addFilter("_id", Operator.IN, allowedDashboardSettingIds.toArray());
    return new RestResponse<>(dashboardSettingsService.getDashboardSettingSummary(accountId, pageRequest));
  }

  @GET
  @Timed
  @Path("{dashboardId}")
  @ExceptionMetered
  public RestResponse<DashboardSettings> getDashboardSetting(
      @QueryParam("accountId") @NotBlank @AccountId String accountId, @PathParam("dashboardId") String dashboardId) {
    if (!featureFlagService.isEnabled(FeatureName.CUSTOM_DASHBOARD, accountId)) {
      throw new InvalidRequestException("User not authorized", USER);
    }

    DashboardSettings dashboardSetting = dashboardSettingsService.get(accountId, dashboardId);
    dashboardAuthHandler.authorize(dashboardSetting, accountId, Action.READ);
    return new RestResponse<>(dashboardSetting);
  }

  /**
   * Perform reconciliation
   *
   * @return the rest response
   */
  @PUT
  @Path("deployment-recon-per-account")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse performReconciliationSingleAccount(
      @QueryParam("targetAccountId") @NotEmpty String targetAccountId,
      @QueryParam("durationStartTs") Long durationStartTs, @QueryParam("durationEndTs") Long durationEndTs) {
    User authUser = UserThreadLocal.get();

    String deployMode = System.getenv(DeployMode.DEPLOY_MODE);
    if (DeployMode.isOnPrem(deployMode) || harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      if (durationEndTs == null || durationStartTs == null || durationStartTs <= 0 || durationEndTs <= 0) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder()
                                                         .message("durationStartTs or endTs is null or invalid")

                                                         .build()))
            .build();
      }

      Account account = accountService.get(targetAccountId);
      if (account == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder()
                                                         .message(targetAccountId + " not found")
                                                         .code(ErrorCode.INVALID_ARGUMENT)
                                                         .build()))
            .build();
      }
      ReconciliationStatus status = deploymentReconService.performReconciliation(
          targetAccountId, durationStartTs, durationEndTs, deploymentExecutionEntity);
      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(ResponseMessage.builder()
                                                       .message(targetAccountId + ":" + status.name())
                                                       .code(null)
                                                       .level(Level.INFO)
                                                       .build()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder()
                                     .message("User not allowed to perform the deployment-recon-per-account operation")
                                     .build()))
          .build();
    }
  }

  /**
   * Perform reconciliation
   *
   * @return the rest response
   */
  @PUT
  @Path("deployment-recon-all-accounts")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse performReconciliationAllAccounts(
      @QueryParam("durationStartTs") Long durationStartTs, @QueryParam("durationEndTs") Long durationEndTs) {
    User authUser = UserThreadLocal.get();
    if (harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      if (durationEndTs == null || durationStartTs == null || durationStartTs <= 0 || durationEndTs <= 0) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder()
                                                         .message("durationStartTs or endTs is null or invalid")
                                                         .code(ErrorCode.INVALID_ARGUMENT)
                                                         .build()))
            .build();
      }

      Map<String, String> accountReconStatusMap = new HashMap<>();
      Query<Account> query = accountService.getBasicAccountQuery().limit(NO_LIMIT);
      try (HIterator<Account> iterator = new HIterator<>(query.fetch())) {
        for (Account account : iterator) {
          ReconciliationStatus status = deploymentReconService.performReconciliation(
              account.getUuid(), durationStartTs, durationEndTs, deploymentExecutionEntity);
          accountReconStatusMap.put(account.getAccountName(), status.name());
          log.info("Reconcilation completed for accountID:[{}],accountName:[{}],status:[{}]", account.getUuid(),
              account.getAccountName(), status);
        }
      }
      return Builder.aRestResponse()
          .withResponseMessages(accountReconStatusMap.entrySet()
                                    .stream()
                                    .map(stringStringEntry
                                        -> ResponseMessage.builder()
                                               .message(stringStringEntry.getKey() + ":" + stringStringEntry.getValue())
                                               .code(null)
                                               .level(Level.INFO)
                                               .build())
                                    .collect(Collectors.toList()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder()
                                     .message("User not allowed to perform the deployment-recon-all-account operation")
                                     .build()))
          .build();
    }
  }
  //
  //  @POST
  //  @Path("gen-instance-data-point")
  //  @Timed
  //  @ExceptionMetered
  //  @PublicApi
  //  public RestResponse generateInstanceDataPoints(@Body InstanceDataGenRequest request) throws Exception {
  //    log.info("generateInstanceDataPoint timestamp: {}, accountId: {}, dataPoint :{}", request.getTimestamp(),
  //        request.getAccountId(), request.getDataMap());
  //
  //    instanceEventProcessor.generateInstanceDPs(request.getTimestamp(), request.getAccountId(),
  //    request.getDataMap());
  //
  //    log.info("Instance Data Points generation completed");
  //    return Builder.aRestResponse()
  //        .withResponseMessages(Lists.newArrayList(ResponseMessage.builder().message("SUCCESS").build()))
  //        .build();
  //  }

  @PUT
  @Path("instance-recon-per-account")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse doReconInstanceOnAccount(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("intervalStartTimeMs") Long intervalStartTimeMs,
      @QueryParam("intervalEndTimeMs") Long intervalEndTimeMs) {
    User authUser = UserThreadLocal.get();

    if (harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      if (intervalEndTimeMs == null || intervalStartTimeMs == null || intervalStartTimeMs <= 0
          || intervalEndTimeMs <= 0) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder()
                                                         .message("intervalStartTimeMs or endTs is null or invalid")

                                                         .build()))
            .build();
      }

      Account account = accountService.get(accountId);
      if (account == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message(accountId + " not found").code(ErrorCode.INVALID_ARGUMENT).build()))
            .build();
      }

      log.info("doReconInstanceOnAccount : {} {} {}", accountId, intervalStartTimeMs, intervalEndTimeMs);
      try {
        instanceReconService.aggregateEventsForGivenInterval(accountId, intervalStartTimeMs, intervalEndTimeMs,
            InstanceReconConstants.DEFAULT_QUERY_BATCH_SIZE, InstanceReconConstants.DEFAULT_EVENTS_LIMIT);
      } catch (Exception ex) {
        log.error("Instance Recon Failure", ex);
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder().message(ex.toString()).build()))
            .build();
      }

      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(ResponseMessage
                                                       .builder()
                                                       //                      .message(accountId + ":" + status.name())
                                                       .message("RECON COMPLETED")
                                                       .code(null)
                                                       .level(Level.INFO)
                                                       .build()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder()
                                     .message("User not allowed to perform the instance-recon-per-account operation : "
                                         + authUser.getUuid())
                                     .build()))
          .build();
    }
  }

  @PUT
  @Path("instance-data-migration")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse doInstanceDataMigration(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("batchSizeInHours") Integer batchSizeInHours) {
    User authUser = UserThreadLocal.get();

    if (harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      Account account = accountService.get(accountId);
      if (account == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message(accountId + " not found").code(ErrorCode.INVALID_ARGUMENT).build()))
            .build();
      }

      log.info("doInstanceDataMigration : {} {}", accountId, batchSizeInHours);
      try {
        instanceReconService.doDataMigration(accountId, batchSizeInHours);
      } catch (Exception ex) {
        log.error("Instance Data Migration Failure", ex);
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder().message(ex.toString()).build()))
            .build();
      }

      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(ResponseMessage
                                                       .builder()
                                                       //                      .message(accountId + ":" + status.name())
                                                       .message("Instance Data Migration COMPLETED")
                                                       .code(null)
                                                       .level(Level.INFO)
                                                       .build()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder()
                  .message("User not allowed to perform the instance-data-migration operation : " + authUser.getUuid())
                  .build()))
          .build();
    }
  }

  @PUT
  @Path("deployment-data-migration")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse doDeploymentDataMigration(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("batchSizeInHours") Integer batchSizeInHours) {
    User authUser = UserThreadLocal.get();

    if (harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      Account account = accountService.get(accountId);
      if (account == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message(accountId + " not found").code(ErrorCode.INVALID_ARGUMENT).build()))
            .build();
      }

      log.info("doDeploymentDataMigration : {} {}", accountId, batchSizeInHours);
      try {
        deploymentEventProcessor.doDataMigration(accountId, batchSizeInHours);
      } catch (Exception ex) {
        log.error("Deployment Data Migration Failure", ex);
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder().message(ex.toString()).build()))
            .build();
      }

      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(ResponseMessage
                                                       .builder()
                                                       //                      .message(accountId + ":" + status.name())
                                                       .message("Deployment Data Migration COMPLETED")
                                                       .code(null)
                                                       .level(Level.INFO)
                                                       .build()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(
              Lists.newArrayList(ResponseMessage.builder()
                                     .message("User not allowed to perform the deployment-data-migration operation : "
                                         + authUser.getUuid())
                                     .build()))
          .build();
    }
  }

  @PUT
  @Path("deployment-migration-per-account")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse doDeploymentMigrationOnAccount(@QueryParam("accountId") @NotEmpty String accountId,
      @QueryParam("intervalStartTimeMs") Long intervalStartTimeMs,
      @QueryParam("intervalEndTimeMs") Long intervalEndTimeMs) {
    User authUser = UserThreadLocal.get();

    if (harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      if (intervalEndTimeMs == null || intervalStartTimeMs == null || intervalStartTimeMs <= 0
          || intervalEndTimeMs <= 0) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder()
                                                         .message("intervalStartTimeMs or endTs is null or invalid")

                                                         .build()))
            .build();
      }

      Account account = accountService.get(accountId);
      if (account == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message(accountId + " not found").code(ErrorCode.INVALID_ARGUMENT).build()))
            .build();
      }

      log.info("doDeploymentMigrationOnAccount : {} {} {}", accountId, intervalStartTimeMs, intervalEndTimeMs);
      try {
        deploymentEventProcessor.handleBatchIntervalMigration(accountId, intervalStartTimeMs, intervalEndTimeMs,
            DeploymentEventProcessor.DEFAULT_MIGRATION_QUERY_BATCH_SIZE,
            DeploymentEventProcessor.DEFAULT_MIGRATION_ROW_LIMIT);
      } catch (Exception ex) {
        log.error("Deployment migration per account Failure", ex);
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder().message(ex.toString()).build()))
            .build();
      }

      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(ResponseMessage
                                                       .builder()
                                                       //                      .message(accountId + ":" + status.name())
                                                       .message("RECON COMPLETED")
                                                       .code(null)
                                                       .level(Level.INFO)
                                                       .build()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder()
                  .message("User not allowed to perform the deployment-migration-per-account operation : "
                      + authUser.getUuid())
                  .build()))
          .build();
    }
  }

  @PUT
  @Path("deployment-step-migration-per-account")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse doDeploymentStepMigrationOnAccount(@QueryParam("accountId") @NotEmpty String accountId,
      @NotNull @QueryParam("intervalStart") long intervalStart, @NotNull @QueryParam("intervalEnd") long intervalEnd) {
    User authUser = UserThreadLocal.get();

    if (harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      Account account = accountService.get(accountId);
      if (account == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message(accountId + " not found").code(ErrorCode.INVALID_ARGUMENT).build()))
            .build();
      }

      log.info("doDeploymentStepMigrationOnAccount : {}", accountId);
      try {
        executorService.submit(() -> {
          deploymentStepReconService.migrateDataMongoToTimescale(
              accountId, StepEventProcessor.DEFAULT_MIGRATION_QUERY_BATCH_SIZE, intervalStart, intervalEnd);
        });
      } catch (Exception ex) {
        log.error("Deployment Step migration per account Failure", ex);
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder().message(ex.toString()).build()))
            .build();
      }

      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(ResponseMessage
                                                       .builder()
                                                       //                      .message(accountId + ":" + status.name())
                                                       .message("Job created for migration")
                                                       .code(null)
                                                       .level(Level.INFO)
                                                       .build()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder()
                  .message("User not allowed to perform the deployment-step-migration-per-account operation : "
                      + authUser.getUuid())
                  .build()))
          .build();
    }
  }

  @PUT
  @Path("execution-interrupt-migration-per-account")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse doExecutionInterruptMigrationOnAccount(@QueryParam("accountId") @NotEmpty String accountId,
      @NotNull @QueryParam("intervalStart") long intervalStart, @NotNull @QueryParam("intervalEnd") long intervalEnd) {
    User authUser = UserThreadLocal.get();

    if (harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      Account account = accountService.get(accountId);
      if (account == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message(accountId + " not found").code(ErrorCode.INVALID_ARGUMENT).build()))
            .build();
      }

      log.info("doExecutionInterruptMigrationOnAccount : {}", accountId);
      try {
        executorService.submit(() -> {
          executionInterruptReconService.migrateDataMongoToTimescale(
              accountId, StepEventProcessor.DEFAULT_MIGRATION_QUERY_BATCH_SIZE, intervalStart, intervalEnd);
        });
      } catch (Exception ex) {
        log.error("Execution Interrupt migration per account Failure", ex);
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder().message(ex.toString()).build()))
            .build();
      }

      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(ResponseMessage
                                                       .builder()
                                                       //                      .message(accountId + ":" + status.name())
                                                       .message("Job created for migration")
                                                       .code(null)
                                                       .level(Level.INFO)
                                                       .build()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder()
                  .message("User not allowed to perform the execution-interrupt-migration-per-account operation : "
                      + authUser.getUuid())
                  .build()))
          .build();
    }
  }

  /**
   * Perform reconciliation
   *
   * @return the rest response
   */
  @PUT
  @Path("looker-entity-recon-per-account")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse performLookerEntityReconciliationSingleAccountSingleEntity(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("durationStartTs") Long durationStartTs,
      @QueryParam("durationEndTs") Long durationEndTs, @QueryParam("entity") String lookerEntity) {
    User authUser = UserThreadLocal.get();

    String deployMode = System.getenv(DeployMode.DEPLOY_MODE);
    if (DeployMode.isOnPrem(deployMode) || harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      if (durationEndTs == null || durationStartTs == null || durationStartTs <= 0 || durationEndTs <= 0) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message("durationStartTs or endTs is null or invalid").build()))
            .build();
      }

      Account account = accountService.get(accountId);
      if (account == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message(accountId + " not found").code(ErrorCode.INVALID_ARGUMENT).build()))
            .build();
      }

      TimeScaleEntity entity = null;
      for (TimeScaleEntity timeScaleEntity : timeScaleEntities) {
        if (timeScaleEntity.getSourceEntityClass().getSimpleName().equals(lookerEntity)) {
          entity = timeScaleEntity;
          break;
        }
      }

      if (entity == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message("The entity: " + lookerEntity + " not supported").build()))
            .build();
      }

      ReconciliationStatus status = performReconciliationViaAPI(
          accountId, durationStartTs, durationEndTs, entity, timeScaleDBService, persistence);

      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder()
                  .message(String.format("looker-entity-recon-per-account for account: %s, entity: %s, status: %s",
                      accountId, entity.getSourceEntityClass().getSimpleName(), status.name()))
                  .code(null)
                  .level(Level.INFO)
                  .build()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder()
                  .message("User not allowed to perform the looker-entity-recon-per-account operation")
                  .build()))
          .build();
    }
  }

  /**
   * Perform reconciliation
   *
   * @return the rest response
   */
  @PUT
  @Path("looker-entity-recon-per-account-per-entity")
  @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  @Timed
  @ExceptionMetered
  @AuthRule(permissionType = LOGGED_IN)
  public RestResponse performLookerEntityReconciliationSingleAccount(
      @QueryParam("accountId") @NotEmpty String accountId, @QueryParam("durationStartTs") Long durationStartTs,
      @QueryParam("durationEndTs") Long durationEndTs) {
    User authUser = UserThreadLocal.get();

    String deployMode = System.getenv(DeployMode.DEPLOY_MODE);
    if (DeployMode.isOnPrem(deployMode) || harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
      if (durationEndTs == null || durationStartTs == null || durationStartTs <= 0 || durationEndTs <= 0) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(ResponseMessage.builder()
                                                         .message("durationStartTs or endTs is null or invalid")

                                                         .build()))
            .build();
      }

      Account account = accountService.get(accountId);
      if (account == null) {
        return Builder.aRestResponse()
            .withResponseMessages(Lists.newArrayList(
                ResponseMessage.builder().message(accountId + " not found").code(ErrorCode.INVALID_ARGUMENT).build()))
            .build();
      }

      for (TimeScaleEntity timeScaleEntity : timeScaleEntities) {
        ReconciliationStatus status = performReconciliationViaAPI(
            accountId, durationStartTs, durationEndTs, timeScaleEntity, timeScaleDBService, persistence);
        log.info(String.format("looker-entity-recon-per-account-per-entity account: %s, entity: %s, status: %s",
            accountId, timeScaleEntity.getSourceEntityClass().getSimpleName(), status.name()));
      }

      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder()
                  .message(
                      String.format("performed looker-entity-recon-per-account-per-entity for account: %s", accountId))
                  .code(null)
                  .level(Level.INFO)
                  .build()))
          .build();
    } else {
      return Builder.aRestResponse()
          .withResponseMessages(Lists.newArrayList(
              ResponseMessage.builder()
                  .message("User not allowed to perform the looker-entity-recon-per-account-per-entity operation")
                  .build()))
          .build();
    }
  }

  //    /**
  //     * Perform reconciliation
  //     *
  //     * @return the rest response
  //     */
  //    @PUT
  //    @Path("looker-entity-recon-all-accounts")
  //    @Scope(value = ResourceType.USER, scope = LOGGED_IN)
  //    @Timed
  //    @ExceptionMetered
  //    @AuthRule(permissionType = LOGGED_IN)
  //    public RestResponse performLookerEntityReconciliationAllAccounts(
  //            @QueryParam("durationStartTs") Long durationStartTs, @QueryParam("durationEndTs") Long durationEndTs) {
  //      User authUser = UserThreadLocal.get();
  //      if (harnessUserGroupService.isHarnessSupportUser(authUser.getUuid())) {
  //        if (durationEndTs == null || durationStartTs == null || durationStartTs <= 0 || durationEndTs <= 0) {
  //          return Builder.aRestResponse()
  //                  .withResponseMessages(Lists.newArrayList(ResponseMessage.builder()
  //                          .message("durationStartTs or endTs is null or invalid")
  //                          .code(ErrorCode.INVALID_ARGUMENT)
  //                          .build()))
  //                  .build();
  //        }
  //
  //        List<Account> accountList = accountService.listAllAccountWithDefaultsWithoutLicenseInfo();
  //        Map<String, String> accountReconStatusMap = new HashMap<>();
  //        for (Account account : accountList) {
  //          ReconciliationStatus status =
  //                  lookerEntityReconService.performReconciliation(account.getUuid(), durationStartTs, durationEndTs);
  //          accountReconStatusMap.put(account.getAccountName(), status.name());
  //          log.info("Reconcilation completed for accountID:[{}],accountName:[{}],status:[{}]", account.getUuid(),
  //                  account.getAccountName(), status);
  //        }
  //        return Builder.aRestResponse()
  //                .withResponseMessages(accountReconStatusMap.entrySet()
  //                        .stream()
  //                        .map(stringStringEntry
  //                                -> ResponseMessage.builder()
  //                                .message(stringStringEntry.getKey() + ":" + stringStringEntry.getValue())
  //                                .code(null)
  //                                .level(Level.INFO)
  //                                .build())
  //                        .collect(Collectors.toList()))
  //                .build();
  //      } else {
  //        return Builder.aRestResponse()
  //                .withResponseMessages(
  //                        Lists.newArrayList(ResponseMessage.builder()
  //                                .message("User not allowed to perform the deployment-recon-all-account operation")
  //                                .build()))
  //                .build();
  //      }
  //    }
}
