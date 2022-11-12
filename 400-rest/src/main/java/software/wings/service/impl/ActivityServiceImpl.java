/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.exception.WingsException.ADMIN;
import static io.harness.persistence.HQuery.excludeAuthority;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.queue.QueuePublisher;

import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityKeys;
import software.wings.beans.Log;
import software.wings.beans.command.Command;
import software.wings.beans.command.Command.CommandKeys;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitDetails;
import software.wings.dl.WingsPersistence;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.LogService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ownership.OwnedByActivity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ReadPreference;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.mapping.Mapper;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

/**
 * Created by peeyushaggarwal on 5/27/16.
 */
@OwnedBy(CDC)
@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class ActivityServiceImpl implements ActivityService {
  public static final int MAX_ACTIVITY_VERSION_RETRY = 5;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private LogService logService;
  @Inject private ServiceInstanceService serviceInstanceService;
  @Inject private AppService appService;

  @Inject private QueuePublisher<PruneEvent> pruneQueue;

  @Override
  public PageResponse<Activity> list(PageRequest<Activity> pageRequest) {
    return wingsPersistence.query(Activity.class, pageRequest);
  }

  @Override
  public Activity get(String id, String appId) {
    Activity activity = wingsPersistence.getWithAppId(Activity.class, appId, id);
    if (activity == null) {
      throw new WingsException(INVALID_ARGUMENT, ADMIN).addParam("args", "Activity doesn't exist");
    }
    return activity;
  }

  @Override
  public Activity save(Activity activity) {
    if (isEmpty(activity.getAccountId())) {
      activity.setAccountId(appService.getAccountIdByAppId(activity.getAppId()));
    }
    wingsPersistence.save(activity);

    if (isNotBlank(activity.getServiceInstanceId())) {
      serviceInstanceService.updateActivity(activity);
    }
    return activity;
  }

  @Override
  public void updateStatus(String activityId, String appId, ExecutionStatus status) {
    wingsPersistence.update(
        wingsPersistence.createQuery(Activity.class).filter(ID_KEY, activityId).filter(ActivityKeys.appId, appId),
        wingsPersistence.createUpdateOperations(Activity.class).set(ActivityKeys.status, status));
    Activity activity = get(activityId, appId);
    if (isNotBlank(activity.getServiceInstanceId())) {
      serviceInstanceService.updateActivity(activity);
    }
  }

  @Override
  public Map<String, List<CommandUnitDetails>> getCommandUnitsMapUsingSecondary(Collection<String> activityIds) {
    if (isEmpty(activityIds)) {
      return new HashMap<>();
    }

    // Activity doesn't have accountId in it's collection. So excluding authority for now. Use case is to get command
    // units for all activities for a set of executions in an account (may not be the same app). The account check is
    // done on workflow executions.
    List<Activity> activities = wingsPersistence.createAnalyticsQuery(Activity.class, excludeAuthority)
                                    .field(ActivityKeys.uuid)
                                    .in(activityIds)
                                    .asList(new FindOptions().readPreference(ReadPreference.secondaryPreferred()));
    if (activities == null) {
      return new HashMap<>();
    }

    return activities.stream().collect(Collectors.toMap(Activity::getUuid, this::getCommandUnits));
  }

  @Override
  public List<CommandUnitDetails> getCommandUnits(String appId, String activityId) {
    Activity activity = get(activityId, appId);
    return getCommandUnits(activity);
  }

  public List<CommandUnitDetails> getCommandUnits(Activity activity) {
    List<CommandUnitDetails> rv = new ArrayList<>();
    if (activity.getCommandUnitType() != null) {
      switch (activity.getCommandUnitType()) {
        case KUBERNETES:
        case RANCHER:
        case PCF_SETUP:
        case PCF_RESIZE:
        case PCF_PLUGIN:
        case COMMAND:
        case HELM:
        case AZURE_VMSS_SETUP:
        case AZURE_VMSS_DEPLOY:
        case AZURE_VMSS_SWAP:
        case AZURE_APP_SERVICE_SLOT_SETUP:
        case AZURE_APP_SERVICE_SLOT_TRAFFIC_SHIFT:
        case AZURE_APP_SERVICE_SLOT_SWAP:
        case AZURE_ARM_DEPLOYMENT:
        case AZURE_BLUEPRINT_DEPLOYMENT:
        case TERRAGRUNT_PROVISION:
        case AWS_AMI_SWITCH_ROUTES:
        case SPOTINST_SETUP:
        case SPOTINST_DEPLOY:
        case AWS_ECS_RUN_TASK_DEPLOY:
        case SPOTINST_UPDATE_LISTENER:
          List<CommandUnit> commandUnits = activity.getCommandUnits();
          for (CommandUnit commandUnit : commandUnits) {
            rv.add(CommandUnitDetails.builder()
                       .commandExecutionStatus(commandUnit.getCommandExecutionStatus())
                       .name(commandUnit.getName())
                       .commandUnitType(activity.getCommandUnitType())
                       .variables(commandUnit.getVariables())
                       .build());
          }
          break;
        case GCB:
        case JENKINS:
        case KUBERNETES_STEADY_STATE_CHECK:
        case ECS_STEADY_STATE_CHECK:
        case KUBERNETES_SWAP_SERVICE_SELECTORS:
        case AWS_ECS_UPDATE_LISTENER_BG:
        case AWS_ECS_UPDATE_ROUTE_53_DNS_WEIGHT:
        case AWS_ECS_SERVICE_SETUP:
        case AWS_ECS_SERVICE_SETUP_ROUTE53:
        case AWS_ECS_SERVICE_SETUP_ELB:
        case AWS_ECS_SERVICE_SETUP_DAEMON:
        case AWS_ECS_SERVICE_ROLLBACK_DAEMON:
        case AWS_ECS_SERVICE_DEPLOY:
          rv.add(CommandUnitDetails.builder()
                     .commandExecutionStatus(ExecutionStatus.translateExecutionStatus(activity.getStatus()))
                     .name(activity.getCommandUnitType().getName())
                     .commandUnitType(activity.getCommandUnitType())
                     .build());
          break;

        case PCF_MAP_ROUTE:
        case PCF_BG_SWAP_ROUTE:
          List<CommandUnit> pcfBgCommandUnits = activity.getCommandUnits();
          if (EmptyPredicate.isEmpty(pcfBgCommandUnits)) {
            rv.add(CommandUnitDetails.builder()
                       .commandExecutionStatus(ExecutionStatus.translateExecutionStatus(activity.getStatus()))
                       .name(activity.getCommandUnitType().getName())
                       .commandUnitType(activity.getCommandUnitType())
                       .build());
          } else {
            for (CommandUnit commandUnit : pcfBgCommandUnits) {
              rv.add(CommandUnitDetails.builder()
                         .commandExecutionStatus(commandUnit.getCommandExecutionStatus())
                         .name(commandUnit.getName())
                         .commandUnitType(activity.getCommandUnitType())
                         .variables(commandUnit.getVariables())
                         .build());
            }
          }
          break;
        default:
          throw new IllegalStateException("Invalid command type: " + activity.getCommandUnitType());
      }
    }
    return rv;
  }

  @Override
  public Activity getLastActivityForService(String appId, String serviceId) {
    return wingsPersistence.createQuery(Activity.class)
        .filter(ActivityKeys.appId, appId)
        .filter(ActivityKeys.serviceId, serviceId)
        .get();
  }

  @Override
  public List<Activity> listWorkflowExecutionActivitiesArtifactIdExists(
      String appId, String serviceId, String workflowId, String workflowExecutionId) {
    FindOptions findOptions = new FindOptions();
    return wingsPersistence.createQuery(Activity.class)
        .filter(ActivityKeys.appId, appId)
        .filter(ActivityKeys.serviceId, serviceId)
        .filter(ActivityKeys.workflowId, workflowId)
        .filter(ActivityKeys.workflowExecutionId, workflowExecutionId)
        .field(ActivityKeys.artifactId)
        .exists()
        .asList(findOptions);
  }

  @Override
  public Activity getLastProductionActivityForService(String appId, String serviceId) {
    return wingsPersistence.createQuery(Activity.class)
        .filter(ActivityKeys.appId, appId)
        .filter(ActivityKeys.serviceId, serviceId)
        .filter(ActivityKeys.environmentType, EnvironmentType.PROD)
        .get();
  }

  @Override
  public boolean delete(String appId, String activityId) {
    pruneQueue.send(new PruneEvent(Activity.class, appId, activityId));

    return wingsPersistence.delete(
        wingsPersistence.createQuery(Activity.class).filter(ActivityKeys.appId, appId).filter(ID_KEY, activityId));
  }

  @Override
  public void pruneDescendingEntities(@NotEmpty String appId, @NotEmpty String activityId) {
    List<OwnedByActivity> services =
        ServiceClassLocator.descendingServices(this, ActivityServiceImpl.class, OwnedByActivity.class);
    PruneEntityListener.pruneDescendingEntities(services, descending -> descending.pruneByActivity(appId, activityId));
  }

  @Override
  public void updateCommandUnitStatus(Map<String, Map<String, Log>> activityCommandUnitLastLogMap) {
    activityCommandUnitLastLogMap.forEach(this::updateActivityCommandUnitStatus);
  }

  @Override
  public void updateCommandUnitStatus(
      String appId, String activityId, String unitName, CommandExecutionStatus commandUnitStatus) {
    try {
      unitName = URLDecoder.decode(unitName, StandardCharsets.UTF_8.toString());
    } catch (UnsupportedEncodingException e) {
      throw new InvalidRequestException("Failed to decode unitName [" + unitName + "]");
    }
    //    Query<Activity> query =
    //        wingsPersistence.createQuery(Activity.class).filter(Mapper.ID_KEY, activityId).filter("appId",
    //        appId).disableValidation().filter("commandUnits.name",
    //            unitName);
    Query<Activity> query =
        wingsPersistence.createQuery(Activity.class)
            .filter(Mapper.ID_KEY, activityId)
            .filter(ActivityKeys.appId, appId)
            .field(ActivityKeys.commandUnits)
            .elemMatch(wingsPersistence.createQuery(Command.class).filter(CommandKeys.name, unitName));

    UpdateOperations<Activity> updateOperations = wingsPersistence.createUpdateOperations(Activity.class)
                                                      .disableValidation()
                                                      .set("commandUnits.$.commandExecutionStatus", commandUnitStatus);
    wingsPersistence.update(query, updateOperations);
  }

  private void updateActivityCommandUnitStatus(String activityId, Map<String, Log> commandUnitLastLogMap) {
    String appId = commandUnitLastLogMap.values().iterator().next().getAppId();

    commandUnitLastLogMap.forEach((unitName, logObject) -> {
      Query<Activity> query = wingsPersistence.createQuery(Activity.class)
                                  .filter(Mapper.ID_KEY, activityId)
                                  .filter(ActivityKeys.appId, appId)
                                  .disableValidation()
                                  .filter("commandUnits.name", unitName);

      UpdateOperations<Activity> updateOperations =
          wingsPersistence.createUpdateOperations(Activity.class)
              .disableValidation()
              .set("commandUnits.$.commandExecutionStatus", logObject.getCommandExecutionStatus());
      wingsPersistence.update(query, updateOperations);
    });
  }
}
