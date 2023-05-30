/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.RESUMED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.Misc.getDurationString;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.ExecutionScope.WORKFLOW;
import static software.wings.beans.ExecutionScope.WORKFLOW_PHASE;
import static software.wings.beans.FailureNotification.Builder.aFailureNotification;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.WORKFLOW_NOTIFICATION;
import static software.wings.sm.StateType.PHASE;

import static java.lang.String.format;
import static java.lang.String.join;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.context.ContextElementType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.ExpressionEvaluator;
import io.harness.sanitizer.HtmlInputSanitizer;

import software.wings.app.MainConfiguration;
import software.wings.beans.Application;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.EnvSummary;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionScope;
import software.wings.beans.InformationNotification;
import software.wings.beans.Notification;
import software.wings.beans.NotificationGroup;
import software.wings.beans.NotificationRule;
import software.wings.beans.NotificationRule.NotificationRuleBuilder;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.security.UserGroup;
import software.wings.common.NotificationMessageResolver;
import software.wings.common.NotificationMessageResolver.NotificationMessageType;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.url.SubdomainUrlHelperIntfc;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.workflow.WorkflowNotificationDetails.WorkflowNotificationDetailsBuilder;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.NotificationSetupService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.StateExecutionInstance;
import software.wings.sm.StateExecutionInstance.StateExecutionInstanceKeys;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.ApprovalState.ApprovalStateType;
import software.wings.sm.states.PhaseSubWorkflow;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Created by anubhaw on 4/7/17.
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class WorkflowNotificationHelper {
  private static final String APPLICATION = "APPLICATION";
  private static final String TRIGGER = "TRIGGER";
  private static final String PIPELINE = "PIPELINE";
  private static final String ENVIRONMENT = "ENVIRONMENT";
  private static final String ARTIFACTS = "ARTIFACTS";
  private static final String SERVICE = "SERVICE";
  private static final String NAME = "_NAME";
  private static final String URL = "_URL";
  public static final String USER_NAME = "USER_NAME";
  @Inject private WorkflowService workflowService;
  @Inject private NotificationService notificationService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private MainConfiguration configuration;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private Clock clock;
  @Inject private NotificationSetupService notificationSetupService;
  @Inject private UserGroupService userGroupService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private SubdomainUrlHelperIntfc subdomainUrlHelper;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private HtmlInputSanitizer userNameSanitizer;

  private final DateFormat dateFormat = new SimpleDateFormat("MMM d");
  private final DateFormat timeFormat = new SimpleDateFormat("HH:mm z");

  public void sendWorkflowStatusChangeNotification(ExecutionContext context, ExecutionStatus status) {
    List<NotificationRule> notificationRules =
        obtainNotificationApplicableToScope((ExecutionContextImpl) context, WORKFLOW, status);
    if (isEmpty(notificationRules)) {
      return;
    }

    Application app = Objects.requireNonNull(((ExecutionContextImpl) context).getApp());
    Environment env = ((ExecutionContextImpl) context).getEnv();

    Map<String, String> placeHolderValues = getPlaceholderValues(context, app, env, status, null);

    Notification notification;
    if (status == SUCCESS || status == PAUSED || status == RESUMED) {
      notification = InformationNotification.builder()
                         .accountId(app.getAccountId())
                         .appId(app.getUuid())
                         .entityId(context.getWorkflowExecutionId())
                         .entityType(EntityType.ORCHESTRATED_DEPLOYMENT)
                         .notificationTemplateId(WORKFLOW_NOTIFICATION.name())
                         .notificationTemplateVariables(placeHolderValues)
                         .build();
    } else {
      notification = aFailureNotification()
                         .withAccountId(app.getAccountId())
                         .withAppId(app.getUuid())
                         .withEnvironmentId(BUILD == context.getOrchestrationWorkflowType() ? null : env.getUuid())
                         .withEntityId(context.getWorkflowExecutionId())
                         .withEntityType(EntityType.ORCHESTRATED_DEPLOYMENT)
                         .withEntityName("Deployment")
                         .withNotificationTemplateId(WORKFLOW_NOTIFICATION.name())
                         .withNotificationTemplateVariables(placeHolderValues)
                         .withExecutionId(context.getWorkflowExecutionId())
                         .build();
    }

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    if (workflowStandardParams != null && workflowStandardParams.isNotifyTriggeredUserOnly()) {
      notificationService.sendNotificationToTriggeredByUserOnly(notification, workflowStandardParams.getCurrentUser());
    } else {
      notificationService.sendNotificationAsync(notification, notificationRules);
    }
  }

  public void sendWorkflowPhaseStatusChangeNotification(
      ExecutionContext context, ExecutionStatus status, PhaseSubWorkflow phaseSubWorkflow) {
    // TODO:: use phaseSubworkflow to send rollback notifications

    List<NotificationRule> notificationRules =
        obtainNotificationApplicableToScope((ExecutionContextImpl) context, WORKFLOW_PHASE, status);
    if (isEmpty(notificationRules)) {
      return;
    }

    Environment env = ((ExecutionContextImpl) context).getEnv();
    Application app = Objects.requireNonNull(((ExecutionContextImpl) context).getApp());

    Map<String, String> placeHolderValues = getPlaceholderValues(context, app, env, status, phaseSubWorkflow);

    Notification notification;
    if (status == SUCCESS || status == PAUSED) {
      notification = InformationNotification.builder()
                         .accountId(app.getAccountId())
                         .appId(app.getUuid())
                         .entityId(context.getWorkflowExecutionId())
                         .entityType(EntityType.ORCHESTRATED_DEPLOYMENT)
                         .notificationTemplateId(WORKFLOW_NOTIFICATION.name())
                         .notificationTemplateVariables(placeHolderValues)
                         .build();
    } else if (status == FAILED) {
      notification = aFailureNotification()
                         .withAccountId(app.getAccountId())
                         .withAppId(app.getUuid())
                         .withEnvironmentId(env.getUuid())
                         .withEntityId(context.getWorkflowExecutionId())
                         .withEntityType(EntityType.ORCHESTRATED_DEPLOYMENT)
                         .withEntityName("Deployment")
                         .withNotificationTemplateId(WORKFLOW_NOTIFICATION.name())
                         .withNotificationTemplateVariables(placeHolderValues)
                         .withExecutionId(context.getWorkflowExecutionId())
                         .build();
    } else {
      log.info("No template found for workflow status " + status);
      return;
    }

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    if (workflowStandardParams != null && workflowStandardParams.isNotifyTriggeredUserOnly()) {
      notificationService.sendNotificationToTriggeredByUserOnly(notification, workflowStandardParams.getCurrentUser());
    } else {
      notificationService.sendNotificationAsync(notification, notificationRules);
    }
  }

  public void sendApprovalNotification(String accountId, NotificationMessageType notificationMessageType,
      Map<String, String> placeHolderValues, ExecutionContext context, ApprovalStateType approvalStateType) {
    List<NotificationRule> rules = new LinkedList<>();

    Objects.requireNonNull(context, "Context can't be null. accountId=" + accountId);
    Objects.requireNonNull(context.getWorkflowType(), "workflow type can't be null. accountId=" + accountId);

    switch (context.getWorkflowType()) {
      case ORCHESTRATION:
        rules = obtainNotificationApplicableToScope(context, WORKFLOW, PAUSED);
        break;

      case PIPELINE:
        if (approvalStateType == ApprovalStateType.USER_GROUP) {
          break;
        }
        UserGroup defaultUserGroup = userGroupService.getDefaultUserGroup(accountId);
        if (null == defaultUserGroup) {
          log.error("There is no default user group. accountId={}", accountId);
        } else {
          NotificationRule rule = NotificationRuleBuilder.aNotificationRule()
                                      .withUserGroupIds(Collections.singletonList(defaultUserGroup.getUuid()))
                                      .build();

          rules.add(rule);
        }
        break;

      default:
        throw new IllegalArgumentException("Unknown workflow type: " + context.getWorkflowType());
    }

    InformationNotification notification = InformationNotification.builder()
                                               .appId(GLOBAL_APP_ID)
                                               .accountId(accountId)
                                               .notificationTemplateId(notificationMessageType.name())
                                               .notificationTemplateVariables(placeHolderValues)
                                               .build();

    notificationService.sendNotificationAsync(notification, rules);
  }

  List<NotificationRule> obtainNotificationApplicableToScope(
      ExecutionContext context, ExecutionScope executionScope, ExecutionStatus status) {
    if (ExecutionStatus.isNegativeStatus(status)) {
      status = FAILED;
    } else if (status == RESUMED) {
      status = PAUSED;
    }

    List<NotificationRule> filteredNotificationRules = new ArrayList<>();
    final Workflow workflow = workflowService.readWorkflow(context.getAppId(), context.getWorkflowId());

    OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();

    if (orchestrationWorkflow instanceof CanaryOrchestrationWorkflow) {
      List<NotificationRule> notificationRules = orchestrationWorkflow.getNotificationRules();
      for (NotificationRule notificationRule : notificationRules) {
        boolean shouldNotify = executionScope == notificationRule.getExecutionScope()
            && notificationRule.getConditions() != null && notificationRule.getConditions().contains(status);

        if (shouldNotify) {
          filteredNotificationRules.add(renderExpressions(context, notificationRule));
        }
      }
    }
    return filteredNotificationRules;
  }

  public NotificationRule renderExpressions(ExecutionContext context, NotificationRule notificationRule) {
    if (notificationRule.isNotificationGroupAsExpression()) {
      renderNotificationGroups(context, notificationRule);
    }

    if (notificationRule.isUserGroupAsExpression()) {
      renderUserGroups(context, notificationRule);
    }

    return notificationRule;
  }

  private void renderNotificationGroups(ExecutionContext context, NotificationRule notificationRule) {
    if (!notificationRule.isNotificationGroupAsExpression()) {
      return;
    }

    List<NotificationGroup> renderedNotificationGroups = new ArrayList<>();
    List<NotificationGroup> notificationGroups = notificationRule.getNotificationGroups();
    for (NotificationGroup notificationGroup : notificationGroups) {
      for (String notificationGroupName : context.renderExpression(notificationGroup.getName()).split(",")) {
        NotificationGroup renderedNotificationGroup = notificationSetupService.readNotificationGroupByName(
            context.getApp().getAccountId(), notificationGroupName.trim());
        if (renderedNotificationGroup != null) {
          renderedNotificationGroups.add(renderedNotificationGroup);
        }
      }
    }
    notificationRule.setNotificationGroups(renderedNotificationGroups);
  }

  private void renderUserGroups(ExecutionContext context, NotificationRule notificationRule) {
    notNullCheck("Invalid notificationRule", notificationRule);
    if (!notificationRule.isUserGroupAsExpression()) {
      return;
    }

    String accountId = context.getApp().getAccountId();
    if (StringUtils.isEmpty(accountId)) {
      log.error("Could not find accountId in context. User Groups can't be rendered. Context: {}", context.asMap());
    }

    String expr = notificationRule.getUserGroupExpression();
    String renderedExpression = context.renderExpression(expr);

    if (StringUtils.isEmpty(renderedExpression)) {
      log.error("[EMPTY_EXPRESSION] Rendered express is: {}. Original Expression: {}, Context: {}", renderedExpression,
          expr, context.asMap());
      return;
    }

    List<String> userGroupNames =
        Arrays.stream(renderedExpression.split(",")).map(String::trim).collect(Collectors.toList());

    List<UserGroup> userGroups = userGroupService.listByName(accountId, userGroupNames);
    if (isNotEmpty(userGroups)) {
      List<String> userGroupIds = userGroups.stream().map(UserGroup::getUuid).collect(Collectors.toList());
      notificationRule.setUserGroupIds(userGroupIds);
    }
  }

  public Map<String, String> getPlaceholderValues(ExecutionContext context, Application app, Environment env,
      ExecutionStatus status, @Nullable PhaseSubWorkflow phaseSubWorkflow) {
    WorkflowExecution workflowExecution =
        workflowExecutionService.getExecutionDetails(app.getUuid(), context.getWorkflowExecutionId(), true, false);
    String triggeredBy = workflowExecution.getTriggeredBy().getName();
    if (triggeredBy.equalsIgnoreCase("Deployment trigger")) {
      triggeredBy = triggeredBy.toLowerCase();
    }
    long startTs = Optional.ofNullable(workflowExecution.getStartTs()).orElse(workflowExecution.getCreatedAt());
    long endTs = Optional.ofNullable(workflowExecution.getEndTs()).orElse(startTs);

    if (phaseSubWorkflow != null) {
      StateExecutionInstance stateExecutionInstance =
          wingsPersistence.createQuery(StateExecutionInstance.class)
              .filter(StateExecutionInstanceKeys.executionUuid, workflowExecution.getUuid())
              .filter(StateExecutionInstanceKeys.stateType, PHASE.name())
              .filter(StateExecutionInstanceKeys.displayName, phaseSubWorkflow.getName())
              .get();
      if (stateExecutionInstance != null) {
        startTs =
            Optional.ofNullable(stateExecutionInstance.getStartTs()).orElse(stateExecutionInstance.getCreatedAt());
        endTs =
            Optional.ofNullable(stateExecutionInstance.getEndTs()).orElse(stateExecutionInstance.getLastUpdatedAt());
      }
    }

    if (endTs == startTs) {
      endTs = clock.millis();
    }

    String workflowUrl = calculateWorkflowUrl(context.getWorkflowExecutionId(), context.getOrchestrationWorkflowType(),
        app.getAccountId(), app.getUuid(), env == null ? null : env.getUuid());

    String startTime = getFormattedTime(startTs);
    String endTime = getFormattedTime(endTs);

    final WorkflowNotificationDetails applicationDetails =
        calculateApplicationDetails(app.getAccountId(), app.getAppId(), app);
    final WorkflowNotificationDetails triggerDetails =
        calculateTriggerDetails(app.getAccountId(), app.getAppId(), workflowExecution);
    final WorkflowNotificationDetails pipelineDetails = calculatePipelineDetails(app, workflowExecution, context);
    final WorkflowNotificationDetails environmentDetails =
        calculateEnvironmentDetails(app.getAccountId(), app.getAppId(), env);
    String pipelineNameForSubject =
        isEmpty(pipelineDetails.getName()) ? "" : String.format(" in pipeline %s", pipelineDetails.getName());

    Map<String, String> placeHolderValues = new HashMap<>();
    placeHolderValues.put("WORKFLOW_NAME", context.getWorkflowExecutionName());
    placeHolderValues.put("WORKFLOW_URL", workflowUrl);
    placeHolderValues.put("VERB", NotificationMessageResolver.getStatusVerb(status));
    placeHolderValues.put(USER_NAME, userNameSanitizer.sanitizeInput(triggeredBy));
    placeHolderValues.put("APP_NAME", app.getName());
    placeHolderValues.put(APPLICATION + NAME, app.getName());
    placeHolderValues.put("START_TS_SECS", Long.toString(startTs / 1000L));
    placeHolderValues.put("END_TS_SECS", Long.toString(endTs / 1000L));
    placeHolderValues.put("START_DATE", startTime);
    placeHolderValues.put("END_DATE", endTime);
    placeHolderValues.put(APPLICATION, applicationDetails.getMessage());
    placeHolderValues.put(APPLICATION + URL, applicationDetails.getUrl());
    placeHolderValues.put(TRIGGER, triggerDetails.getMessage());
    placeHolderValues.put(TRIGGER + NAME, triggerDetails.getName());
    placeHolderValues.put(TRIGGER + URL, triggerDetails.getUrl());
    placeHolderValues.put(PIPELINE, pipelineDetails.getMessage());
    placeHolderValues.put(PIPELINE + NAME, pipelineDetails.getName());
    placeHolderValues.put(PIPELINE + URL, pipelineDetails.getUrl());
    placeHolderValues.put("PIPELINE_NAME_EMAIL_SUBJECT", pipelineNameForSubject);
    placeHolderValues.put(ENVIRONMENT, environmentDetails.getMessage());
    placeHolderValues.put(ENVIRONMENT + URL, environmentDetails.getUrl());
    placeHolderValues.put("FAILED_PHASE", "");
    placeHolderValues.put("MORE_ERRORS", "");
    placeHolderValues.put("ERROR_URL", "");
    placeHolderValues.put("ERRORS", "");

    placeHolderValues.put("DURATION", getDurationString(startTs, endTs));
    String environmentName = BUILD == context.getOrchestrationWorkflowType() ? "no environment" : env.getName();
    placeHolderValues.put("ENV_NAME", environmentName);
    placeHolderValues.put(ENVIRONMENT + NAME, environmentName);
    WorkflowNotificationDetails artifactsDetails;
    WorkflowNotificationDetails serviceDetails;
    if (phaseSubWorkflow != null) {
      placeHolderValues.put("PHASE_NAME", phaseSubWorkflow.getName() + " of ");
      artifactsDetails = getArtifactsDetails(context, workflowExecution, WORKFLOW_PHASE, phaseSubWorkflow);
      serviceDetails = calculateServiceDetailsForAllServices(
          app.getAccountId(), app.getAppId(), context, workflowExecution, WORKFLOW_PHASE, phaseSubWorkflow);
    } else {
      placeHolderValues.put("PHASE_NAME", "");
      artifactsDetails = getArtifactsDetails(context, workflowExecution, WORKFLOW, null);
      serviceDetails = calculateServiceDetailsForAllServices(
          app.getAccountId(), app.getAppId(), context, workflowExecution, WORKFLOW, null);
    }
    WorkflowNotificationDetails infraDetails =
        calculateInfraDetails(app.getAccountId(), context.getAppId(), workflowExecution);

    placeHolderValues.put("INFRA", infraDetails.getMessage());
    placeHolderValues.put("INFRA_NAME", infraDetails.getName());
    placeHolderValues.put("INFRA_URL", infraDetails.getUrl());
    placeHolderValues.put(ARTIFACTS, artifactsDetails.getMessage());
    placeHolderValues.put(ARTIFACTS + NAME, artifactsDetails.getName());
    placeHolderValues.put(ARTIFACTS + URL, artifactsDetails.getUrl());
    placeHolderValues.put(SERVICE, serviceDetails.getMessage());
    placeHolderValues.put(SERVICE + NAME, serviceDetails.getName());
    placeHolderValues.put(SERVICE + URL, serviceDetails.getUrl());
    return placeHolderValues;
  }

  public String getFormattedTime(long startTs) {
    return format("%s at %s", dateFormat.format(new Date(startTs)), timeFormat.format(new Date(startTs)));
  }

  public String calculateWorkflowUrl(String workflowExecutionId, OrchestrationWorkflowType type, String accountId,
      String appId, String environmentId) {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    return NotificationMessageResolver.buildAbsoluteUrl(
        format("/account/%s/app/%s/env/%s/executions/%s/details", accountId, appId,
            BUILD == type ? "build" : environmentId, workflowExecutionId),
        baseUrl);
  }

  public WorkflowNotificationDetails calculatePipelineDetails(
      Application app, WorkflowExecution workflowExecution, ExecutionContext context) {
    WorkflowNotificationDetailsBuilder pipelineDetails = WorkflowNotificationDetails.builder();
    String pipelineMsg = "";
    if (workflowExecution.getPipelineExecutionId() != null) {
      String pipelineName = workflowExecution.getPipelineSummary().getPipelineName();
      if (isNotBlank(pipelineName)) {
        String baseUrl = subdomainUrlHelper.getPortalBaseUrl(context.getAccountId());
        String pipelineUrl = NotificationMessageResolver.buildAbsoluteUrl(
            format("/account/%s/app/%s/pipeline-execution/%s/workflow-execution/%s/details", app.getAccountId(),
                app.getUuid(), workflowExecution.getPipelineExecutionId(), context.getWorkflowExecutionId()),
            baseUrl);
        pipelineMsg = format(" in pipeline <<<%s|-|%s>>>", pipelineUrl, pipelineName);
        pipelineDetails.name(pipelineName);
        pipelineDetails.url(pipelineUrl);
      }
    }

    pipelineDetails.message(pipelineMsg);
    return pipelineDetails.build();
  }

  public WorkflowNotificationDetails calculatePipelineDetailsPipelineExecution(
      Application app, WorkflowExecution workflowExecution, ExecutionContext context) {
    WorkflowNotificationDetailsBuilder pipelineDetails = WorkflowNotificationDetails.builder();
    if (workflowExecution.getPipelineExecution() != null) {
      String pipelineName = workflowExecution.getPipelineExecution().getPipeline().getName();
      if (isNotBlank(pipelineName)) {
        String baseUrl = subdomainUrlHelper.getPortalBaseUrl(context.getAccountId());
        String pipelineUrl = NotificationMessageResolver.buildAbsoluteUrl(
            format("/account/%s/app/%s/pipeline-execution/%s/workflow-execution/%s/details", app.getAccountId(),
                app.getUuid(), workflowExecution.getUuid(), "undefined"),
            baseUrl);
        String pipelineMsg = format("*Pipeline:* <<<%s|-|%s>>>", pipelineUrl, pipelineName);
        pipelineDetails.message(pipelineMsg);
        pipelineDetails.name(pipelineName);
        pipelineDetails.url(pipelineUrl);
      }
    }
    return pipelineDetails.build();
  }

  public WorkflowNotificationDetails calculateInfraDetails(
      String accountId, String appId, WorkflowExecution workflowExecution) {
    WorkflowNotificationDetailsBuilder infraDetails = WorkflowNotificationDetails.builder();
    List<String> infraIds = workflowExecution.getInfraDefinitionIds();

    StringBuilder infraMsg = new StringBuilder(64);
    StringBuilder infraDetailsName = new StringBuilder(32);
    StringBuilder infraDetailsUrl = new StringBuilder(32);

    if (isNotEmpty(infraIds)) {
      List<String> infras = infraIds.stream()
                                .filter(EmptyPredicate::isNotEmpty)
                                .filter(id -> !ExpressionEvaluator.containsVariablePattern(id))
                                .collect(Collectors.toList());
      boolean firstInfra = true;
      for (String infraId : infras) {
        InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraId);
        if (!firstInfra) {
          infraMsg.append(", ");
          infraDetailsName.append(',');
          infraDetailsUrl.append(',');
        }
        notNullCheck("Infrastructure Definition might have been deleted", infrastructureDefinition, USER);
        String infraUrl = calculateInfraUrl(accountId, appId, infraId, infrastructureDefinition.getEnvId());
        infraMsg.append(format("<<<%s|-|%s>>>", infraUrl, infrastructureDefinition.getName()));
        infraDetailsName.append(infrastructureDefinition.getName());
        infraDetailsUrl.append(infraUrl);
        firstInfra = false;
      }

    } else {
      infraDetailsName.append("no infrastructure definitions");
      infraMsg.append("no infrastructure definitions");
    }
    infraDetails.name(infraDetailsName.toString());
    infraDetails.url(infraDetailsUrl.toString());
    infraDetails.message(format("*Infrastructure Definitions:* %s", infraMsg));
    return infraDetails.build();
  }

  private String calculateInfraUrl(String accountId, String appId, String infraId, String envId) {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    return NotificationMessageResolver.buildAbsoluteUrl(
        format("/account/%s/app/%s/environments/%s/infrastructure-definitions/%s/details", accountId, appId, envId,
            infraId),
        baseUrl);
  }

  public WorkflowNotificationDetails calculateEnvDetails(
      String accountId, String appId, List<EnvSummary> envSummaries) {
    WorkflowNotificationDetailsBuilder envDetails = WorkflowNotificationDetails.builder();

    StringBuilder envMsg = new StringBuilder();
    StringBuilder envDetailsName = new StringBuilder();
    StringBuilder envDetailsUrl = new StringBuilder();

    if (isNotEmpty(envSummaries)) {
      boolean firstEnv = true;
      for (EnvSummary envSummary : envSummaries) {
        if (!firstEnv) {
          envMsg.append(", ");
          envDetailsName.append(',');
          envDetailsUrl.append(',');
        }
        String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
        String envURL = NotificationMessageResolver.buildAbsoluteUrl(
            format("/account/%s/app/%s/environments/%s/details", accountId, appId, envSummary.getUuid()), baseUrl);
        envMsg.append(format("<<<%s|-|%s>>>", envURL, envSummary.getName()));
        envDetailsName.append(envSummary.getName());
        envDetailsUrl.append(envURL);
        firstEnv = false;
      }

    } else {
      envDetailsName.append("no environments");
      envMsg.append("no environments");
    }
    envDetails.name(envDetailsName.toString());
    envDetails.url(envDetailsUrl.toString());
    envDetails.message(format("*Environments:* %s", envMsg));
    return envDetails.build();
  }

  public WorkflowNotificationDetails calculateServiceDetailsForAllServices(String accountId, String appId,
      ExecutionContext context, WorkflowExecution workflowExecution, ExecutionScope scope,
      PhaseSubWorkflow phaseSubWorkflow) {
    WorkflowNotificationDetailsBuilder serviceDetails = WorkflowNotificationDetails.builder();
    List<String> serviceIds = new ArrayList<>();
    if (isNotEmpty(workflowExecution.getServiceIds())) {
      if (scope == WORKFLOW_PHASE) {
        serviceIds.add(phaseSubWorkflow.getServiceId());
      } else {
        serviceIds.addAll(workflowExecution.getServiceIds());
      }
    }

    StringBuilder serviceMsg = new StringBuilder();
    StringBuilder serviceDetailsName = new StringBuilder();
    StringBuilder serviceDetailsUrl = new StringBuilder();

    boolean firstService = true;
    List<String> filteredServices = serviceIds.stream()
                                        .filter(EmptyPredicate::isNotEmpty)
                                        .filter(id -> !ExpressionEvaluator.containsVariablePattern(id))
                                        .collect(Collectors.toList());
    for (String serviceId : filteredServices) {
      Service service = serviceResourceService.get(context.getAppId(), serviceId, false);
      if (!firstService) {
        serviceMsg.append(", ");
        serviceDetailsName.append(',');
        serviceDetailsUrl.append(',');
      }
      notNullCheck("Service might have been deleted", service, USER);
      String serviceUrl = calculateServiceUrl(accountId, appId, serviceId);
      serviceMsg.append(format("<<<%s|-|%s>>>", serviceUrl, service.getName()));
      serviceDetailsName.append(service.getName());
      serviceDetailsUrl.append(serviceUrl);
      firstService = false;
    }

    if (filteredServices.isEmpty()) {
      serviceMsg.append("no service");
      serviceDetailsName.append("no service");
    }

    serviceDetails.name(serviceDetailsName.toString());
    serviceDetails.url(serviceDetailsUrl.toString());
    serviceDetails.message(format("*Services:* %s", serviceMsg));
    return serviceDetails.build();
  }

  public String calculateServiceUrl(String accountId, String appId, String serviceId) {
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    return NotificationMessageResolver.buildAbsoluteUrl(
        format("/account/%s/app/%s/services/%s/details", accountId, appId, serviceId), baseUrl);
  }

  public WorkflowNotificationDetails calculateEnvironmentDetails(String accountId, String appId, Environment env) {
    WorkflowNotificationDetailsBuilder environmentDetails = WorkflowNotificationDetails.builder();
    String envMsg = "";
    if (env != null) {
      String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
      String envURL = NotificationMessageResolver.buildAbsoluteUrl(
          format("/account/%s/app/%s/environments/%s/details", accountId, appId, env.getUuid()), baseUrl);

      envMsg = format("*Environment:* <<<%s|-|%s>>>", envURL, env.getName());
      environmentDetails.name(env.getName());
      environmentDetails.url(envURL);
    }

    environmentDetails.message(envMsg);
    return environmentDetails.build();
  }

  public WorkflowNotificationDetails calculateApplicationDetails(String accountId, String appId, Application app) {
    WorkflowNotificationDetailsBuilder applicationDetails = WorkflowNotificationDetails.builder();
    String appMsg = "";

    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    if (isNotEmpty(appId) && (app != null)) {
      String appURL =
          NotificationMessageResolver.buildAbsoluteUrl(format("/account/%s/app/%s/details", accountId, appId), baseUrl);
      appMsg = format("*Application:* <<<%s|-|%s>>>", appURL, app.getName());
      applicationDetails.name(app.getName());
      applicationDetails.url(appURL);
    }
    applicationDetails.message(appMsg);
    return applicationDetails.build();
  }

  public WorkflowNotificationDetails calculateTriggerDetails(
      String accountId, String appId, WorkflowExecution workflowExecution) {
    WorkflowNotificationDetailsBuilder triggerDetails = WorkflowNotificationDetails.builder();
    String baseUrl = subdomainUrlHelper.getPortalBaseUrl(accountId);
    String triggeredBy = workflowExecution.getTriggeredBy().getName();

    String triggerMsg = "";
    if (triggeredBy.contains("Deployment Trigger")) {
      String triggerURL = NotificationMessageResolver.buildAbsoluteUrl(
          format("/account/%s/app/%s/triggers", accountId, appId), baseUrl);
      triggerMsg = format("*TriggeredBy:* <<<%s|-|%s>>>", triggerURL, triggeredBy);
      triggerDetails.url(triggerURL);
    } else {
      triggerMsg = format("*TriggeredBy:* %s", triggeredBy);
    }
    triggerDetails.name(triggeredBy);
    triggerDetails.message(triggerMsg);
    return triggerDetails.build();
  }

  public WorkflowNotificationDetails getArtifactsDetails(ExecutionContext context, WorkflowExecution workflowExecution,
      ExecutionScope scope, PhaseSubWorkflow phaseSubWorkflow) {
    WorkflowNotificationDetailsBuilder artifactsDetails = WorkflowNotificationDetails.builder();
    List<String> serviceIds = new ArrayList<>();
    if (isNotEmpty(workflowExecution.getServiceIds())) {
      if (scope == WORKFLOW_PHASE) {
        serviceIds.add(phaseSubWorkflow.getServiceId());
      } else {
        serviceIds.addAll(workflowExecution.getServiceIds());
      }
    }

    Map<String, Artifact> artifactStreamIdArtifacts = new HashMap<>();
    List<Artifact> artifacts = ((ExecutionContextImpl) context).getArtifacts();
    if (isNotEmpty(artifacts)) {
      for (Artifact artifact : artifacts) {
        artifactStreamIdArtifacts.put(artifact.getArtifactStreamId(), artifact);
      }
    }

    List<String> serviceMsgs = new ArrayList<>();
    for (String serviceId : serviceIds) {
      StringBuilder serviceMsg = new StringBuilder();
      Service service = serviceResourceService.get(context.getAppId(), serviceId, false);
      notNullCheck("Service might have been deleted", service, USER);
      serviceMsg.append(service.getName()).append(": ");

      List<String> artifactStreamIds = artifactStreamServiceBindingService.listArtifactStreamIds(service);
      boolean found = false;
      if (isNotEmpty(artifactStreamIds)) {
        for (String artifactStreamId : artifactStreamIds) {
          if (artifactStreamIdArtifacts.containsKey(artifactStreamId)) {
            Artifact artifact = artifactStreamIdArtifacts.get(artifactStreamId);
            serviceMsg.append(artifact.getArtifactSourceName())
                .append(" (build# ")
                .append((artifact.getBuildNo() == null) ? "unknown" : artifact.getBuildNo().replaceAll("\\*", "٭"))
                .append(')');
            found = true;
            break;
          }
        }
      }

      if (!found) {
        serviceMsg.append("no artifact");
      }

      serviceMsgs.add(serviceMsg.toString());
    }

    String artifactsMsg = "no artifacts";
    if (isNotEmpty(serviceMsgs)) {
      artifactsMsg = join(", ", serviceMsgs);
    }

    artifactsDetails.name(artifactsMsg);
    artifactsDetails.message(format("*Artifacts:* %s", artifactsMsg));
    return artifactsDetails.build();
  }
}
