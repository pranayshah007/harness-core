/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.ExecutionStatus.ABORTED;
import static io.harness.beans.ExecutionStatus.EXPIRED;
import static io.harness.beans.ExecutionStatus.FAILED;
import static io.harness.beans.ExecutionStatus.PAUSED;
import static io.harness.beans.ExecutionStatus.REJECTED;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.ExecutionStatus.SKIPPED;
import static io.harness.beans.ExecutionStatus.SUCCESS;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.event.model.EventConstants.ENVIRONMENT_ID;
import static io.harness.event.model.EventConstants.ENVIRONMENT_NAME;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import static software.wings.beans.alert.AlertType.ApprovalNeeded;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.APPROVAL_EXPIRED_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.APPROVAL_EXPIRED_WORKFLOW_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.APPROVAL_NEEDED_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.APPROVAL_STATE_CHANGE_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.WORKFLOW_ABORT_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.WORKFLOW_PAUSE_NOTIFICATION;
import static software.wings.common.NotificationMessageResolver.NotificationMessageType.WORKFLOW_RESUME_NOTIFICATION;
import static software.wings.security.JWT_CATEGORY.EXTERNAL_SERVICE_SECRET;
import static software.wings.service.impl.slack.SlackApprovalUtils.createSlackApprovalMessage;
import static software.wings.service.impl.workflow.WorkflowNotificationHelper.USER_NAME;
import static software.wings.sm.states.ApprovalState.ApprovalStateType.USER_GROUP;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.TriggeredBy;
import io.harness.beans.WorkflowType;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.HarnessJiraException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.Misc;
import io.harness.reflection.ExpressionReflectionUtils;
import io.harness.scheduler.PersistentScheduler;
import io.harness.serializer.KryoSerializer;
import io.harness.shell.ScriptType;
import io.harness.tasks.ResponseData;

import software.wings.api.ApprovalStateExecutionData;
import software.wings.api.ApprovalStateExecutionData.ApprovalStateExecutionDataKeys;
import software.wings.api.ServiceNowExecutionData;
import software.wings.api.WorkflowElement;
import software.wings.api.jira.JiraExecutionData;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.ExecutionScope;
import software.wings.beans.InformationNotification;
import software.wings.beans.NameValuePair;
import software.wings.beans.NameValuePair.NameValuePairKeys;
import software.wings.beans.NotificationRule;
import software.wings.beans.NotificationRule.NotificationRuleBuilder;
import software.wings.beans.TemplateExpression;
import software.wings.beans.User;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.alert.ApprovalNeededAlert;
import software.wings.beans.approval.ApprovalPollingJobEntity;
import software.wings.beans.approval.ApprovalStateParams;
import software.wings.beans.approval.ApprovalStateParams.ApprovalStateParamsKeys;
import software.wings.beans.approval.JiraApprovalParams;
import software.wings.beans.approval.ServiceNowApprovalParams;
import software.wings.beans.approval.ShellScriptApprovalParams;
import software.wings.beans.approval.ShellScriptApprovalParams.ShellScriptApprovalParamsKeys;
import software.wings.beans.approval.SlackApprovalParams;
import software.wings.beans.command.Command.Builder;
import software.wings.beans.command.CommandType;
import software.wings.beans.security.UserGroup;
import software.wings.common.NotificationMessageResolver;
import software.wings.common.NotificationMessageResolver.NotificationMessageType;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.exception.ApprovalStateException;
import software.wings.security.SecretManager;
import software.wings.security.UserThreadLocal;
import software.wings.service.impl.JiraHelperService;
import software.wings.service.impl.notifications.SlackApprovalMessageKeys;
import software.wings.service.impl.workflow.WorkflowNotificationDetails;
import software.wings.service.impl.workflow.WorkflowNotificationHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.ApprovalPolingService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.NotificationService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.servicenow.ServiceNowService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.states.EnvState.EnvStateKeys;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;

import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.annotations.Transient;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONArray;
import okhttp3.MediaType;
import org.apache.commons.jexl3.JexlException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
@FieldNameConstants(innerTypeName = "ApprovalStateKeys")
public class ApprovalState extends State implements SweepingOutputStateMixin {
  public static final String APPROVAL_STATUS_KEY = "approvalStatus";

  @Getter @Setter private String groupName;
  @Getter @Setter private List<String> userGroups = new ArrayList<>();
  @Getter @Setter private boolean disable;
  @Getter @Setter private String disableAssertion;
  @Getter @Setter private boolean autoRejectPreviousDeployments;
  @Setter @SchemaIgnore private String stageName;
  @Getter @Setter private boolean userGroupAsExpression;
  /**
   * This should be used to get user groups to approval if {@link #userGroupAsExpression} is set.
   */
  @NotNull @Getter @Setter private String userGroupExpression;
  @Getter @Setter private String description;

  @Override
  public KryoSerializer getKryoSerializer() {
    return kryoSerializer;
  }

  public enum ApprovalStateType { JIRA, USER_GROUP, SHELL_SCRIPT, SERVICENOW }

  public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  @Inject private AlertService alertService;

  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private NotificationMessageResolver notificationMessageResolver;
  @Inject private NotificationService notificationService;
  @Inject private JiraHelperService jiraHelperService;
  @Inject private ServiceNowService serviceNowService;
  @Inject private transient ActivityService activityService;
  @Inject private transient WorkflowNotificationHelper workflowNotificationHelper;
  @Inject private ApprovalPolingService approvalPolingService;
  @Inject private SecretManager secretManager;
  @Inject private transient SweepingOutputService sweepingOutputService;
  @Inject private UserGroupService userGroupService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private FeatureFlagService featureFlagService;

  @Inject @Transient private TemplateExpressionProcessor templateExpressionProcessor;
  @Transient @Inject KryoSerializer kryoSerializer;

  @Inject @Named("ServiceJobScheduler") private PersistentScheduler serviceJobScheduler;
  private Integer DEFAULT_APPROVAL_STATE_TIMEOUT_MILLIS = 7 * 24 * 60 * 60 * 1000; // 7 days
  private String SCRIPT_APPROVAL_COMMAND = "Execute Approval Script";
  private String SCRIPT_APPROVAL_JOB_GROUP = "SHELL_SCRIPT_APPROVAL_JOB";
  public static final String APPROVAL_STATE_TYPE_VARIABLE = "approvalStateType";
  public static final String USER_GROUPS_VARIABLE = "userGroups";
  private static final Integer MAXIMUM_TIMEOUT = 2147400049;
  private static final String TIMEOUT_PROPERTY_KEY = "timeoutMillis";

  @Getter @Setter ApprovalStateParams approvalStateParams;
  @Getter @Setter ApprovalStateType approvalStateType = USER_GROUP;

  @Getter @Setter private SweepingOutputInstance.Scope sweepingOutputScope = Scope.PIPELINE;
  @Getter @Setter private String sweepingOutputName;

  @Getter @Setter List<NameValuePair> variables;

  public ApprovalState(String name) {
    super(name, StateType.APPROVAL.name());
  }

  public String getStageName() {
    return stageName;
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    String approvalId = generateUuid();
    renderExpressions(context);

    if (isNotEmpty(getTemplateExpressions())) {
      resolveUserGroupFromTemplate(context, executionContext);
    } else if (isUserGroupAsExpression()) {
      resolveUserGroupFromExpression(context);
    }

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    ApprovalStateExecutionData executionData = ApprovalStateExecutionData.builder()
                                                   .approvalId(approvalId)
                                                   .approvalStateType(approvalStateType)
                                                   .timeoutMillis(getTimeoutMillis())
                                                   .variables(getVariables())
                                                   .stageName(getStageName())
                                                   .triggeredBy(workflowStandardParams.getCurrentUser())
                                                   .description(getDescription())
                                                   .build();
    if (disableAssertion != null) {
      ExecutionResponse skipResponse = handleSkipCondition(context, executionData);
      if (skipResponse != null) {
        return skipResponse;
      }
    }

    setPipelineVariables(context);

    Application app = context.getApp();

    Map<String, String> placeholderValues = getPlaceholderValues(context, "", PAUSED);
    // Open an alert
    ApprovalNeededAlert approvalNeededAlert = ApprovalNeededAlert.builder()
                                                  .executionId(context.getWorkflowExecutionId())
                                                  .approvalId(approvalId)
                                                  .name(context.getWorkflowExecutionName())
                                                  .build();
    populateApprovalAlert(approvalNeededAlert, context);
    alertService.openAlert(app.getAccountId(), app.getUuid(), ApprovalNeeded, approvalNeededAlert);

    if (approvalStateType == null) {
      approvalStateType = USER_GROUP;
    }
    try {
      workflowNotificationHelper.sendApprovalNotification(app.getAccountId(), WORKFLOW_PAUSE_NOTIFICATION,
          new HashMap<>(placeholderValues), context, approvalStateType);
    } catch (Exception e) {
      // catch exception so that failure to send notification doesn't affect rest of execution
      log.error("Error sending approval notification. accountId={}", app.getAccountId(), e);
    }

    WorkflowExecution workflowExecution = workflowExecutionService.fetchWorkflowExecution(app.getAppId(),
        context.getWorkflowExecutionId(), WorkflowExecutionKeys.pipelineSummary, WorkflowExecutionKeys.workflowId);
    if (workflowExecution != null) {
      if (workflowExecution.getPipelineSummary() != null) {
        executionData.setWorkflowId(workflowExecution.getPipelineSummary().getPipelineId());
      } else {
        executionData.setWorkflowId(workflowExecution.getWorkflowId());
      }
    }
    executionData.setAppId(app.getAppId());
    executionData.setApprovalStateType(approvalStateType);

    switch (approvalStateType) {
      case JIRA:
        return executeJiraApproval(context, executionData, approvalId);
      case SERVICENOW:
        return executeServiceNowApproval(context, executionData, approvalId);
      case USER_GROUP:
        return executeUserGroupApproval(
            userGroups, app.getAccountId(), placeholderValues, approvalId, executionData, app.getUuid(), context);
      case SHELL_SCRIPT:
        return executeShellScriptApproval(context, app.getAccountId(), app.getUuid(), approvalId,
            approvalStateParams.getShellScriptApprovalParams(), executionData);
      default:
        throw new InvalidRequestException(
            "Invalid ApprovalStateType, it should be one of ServiceNow, Jira, HarnessUi or Custom Shell script");
    }
  }

  private void renderExpressions(ExecutionContext context) {
    description = context.renderExpression(description);
  }

  private void resolveUserGroupFromTemplate(ExecutionContext context, ExecutionContextImpl executionContext) {
    List<String> resolvedUserGroup = new ArrayList<>();
    TemplateExpression userGroupExp =
        templateExpressionProcessor.getTemplateExpression(getTemplateExpressions(), ApprovalStateKeys.userGroups);

    if (userGroupExp != null && isNotEmpty(userGroupExp.getExpression())) {
      String userGroupsString = templateExpressionProcessor.resolveTemplateExpression(context, userGroupExp);
      if (!isEmpty(userGroupsString)) {
        userGroups = Arrays.asList(userGroupsString.split("\\s*,\\s*"));
      } else {
        throw new InvalidRequestException("User group templatised but value is not provided", USER);
      }
    }

    if (isEmpty(userGroups)) {
      throw new ApprovalStateException("Valid user groups not provided in Approval Step", ErrorCode.USER_GROUP_ERROR,
          Level.ERROR, WingsException.USER);
    }

    userGroups = resolveUserGroup(userGroups, executionContext.getApp().getAccountId());
  }

  @VisibleForTesting
  void resolveUserGroupFromExpression(ExecutionContext context) {
    if (isEmpty(getUserGroupExpression())) {
      throw new InvalidRequestException("User group expression is set but value is not provided", USER);
    }

    String expression = getUserGroupExpression();
    String renderedExpression = context.renderExpression(expression);

    if (isEmpty(renderedExpression)) {
      log.error("[EMPTY_EXPRESSION] Rendered expression is: [{}]. Original Expression: [{}], Context: [{}]",
          renderedExpression, expression, context.asMap());
      throw new InvalidRequestException("User group expression is invalid", USER);
    }

    List<String> userGroupNames =
        Arrays.stream(renderedExpression.split(",")).map(String::trim).collect(Collectors.toList());

    userGroups = resolveUserGroup(userGroupNames, context.getAccountId());
  }

  private List<String> resolveUserGroup(List<String> userGroupNames, String accountId) {
    List<String> resolvedUserGroup = new ArrayList<>();

    for (String singleUserGroup : userGroupNames) {
      UserGroup userGroup = userGroupService.get(accountId, singleUserGroup);
      if (userGroup == null) {
        userGroup = userGroupService.fetchUserGroupByName(accountId, singleUserGroup);
      }
      if (userGroup == null) {
        throw new ApprovalStateException("User Group provided in Approval Step not found for " + singleUserGroup,
            ErrorCode.USER_GROUP_ERROR, Level.ERROR, WingsException.USER);
      }
      resolvedUserGroup.add(userGroup.getUuid());
    }

    return resolvedUserGroup;
  }

  @Nullable
  private ExecutionResponse handleSkipCondition(ExecutionContext context, ApprovalStateExecutionData executionData) {
    try {
      if (isTrueExpression(disableAssertion, context, executionData)) {
        return ExecutionResponse.builder()
            .executionStatus(SKIPPED)
            .errorMessage(getName() + " step in " + context.getPipelineStageName() + " has been skipped"
                + ("true".equals(disableAssertion) ? "" : " based on assertion expression [" + disableAssertion + "]"))
            .stateExecutionData(executionData)
            .build();
      }
    } catch (JexlException je) {
      log.error("Skip Assertion Evaluation Failed", je);
      String jexlError = Optional.ofNullable(je.getMessage()).orElse("");
      if (jexlError.contains(":")) {
        jexlError = jexlError.split(":")[1];
      }
      if (je instanceof JexlException.Variable
          && ((JexlException.Variable) je).getVariable().equals("sweepingOutputSecrets")) {
        jexlError = "Secret Variables defined in Script output of shell scripts cannot be used in assertions";
      }
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage("Skip Assertion Evaluation Failed : " + jexlError)
              .stateExecutionData(executionData));
    } catch (Exception e) {
      log.error("Skip Assertion Evaluation Failed", e);
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage("Skip Assertion Evaluation Failed : " + (e.getMessage() != null ? e.getMessage() : ""))
              .stateExecutionData(executionData));
    }
    return null;
  }

  private boolean isTrueExpression(
      String disableAssertion, ExecutionContext context, ApprovalStateExecutionData executionData) {
    // rendering expression in order to have it tracked
    context.renderExpression(disableAssertion);
    if ("true".equals(disableAssertion)) {
      return true;
    }
    return (boolean) context.evaluateExpression(
        disableAssertion, StateExecutionContext.builder().stateExecutionData(executionData).build());
  }

  @Override
  public void parseProperties(Map<String, Object> properties) {
    prepareVariables(properties);
    boolean isDisabled = properties.get(EnvStateKeys.disable) != null && (boolean) properties.get(EnvStateKeys.disable);
    if (isDisabled && properties.get(EnvStateKeys.disableAssertion) == null) {
      properties.put(EnvStateKeys.disableAssertion, "true");
    }
    validateProperties(properties);
    mapApprovalObject(properties, this);
  }

  private void validateProperties(Map<String, Object> properties) {
    Object timeoutMillis = properties.get(TIMEOUT_PROPERTY_KEY);
    if (timeoutMillis != null) {
      try {
        Integer timeoutMillisNumber = Integer.valueOf(timeoutMillis.toString());
        if (timeoutMillisNumber > MAXIMUM_TIMEOUT) {
          throw new InvalidRequestException("Value exceeded maximum timeout of 3w 3d 20h 30m.");
        } else if (timeoutMillisNumber < 0) {
          throw new InvalidRequestException("Timeout value should be positive");
        }
      } catch (NumberFormatException ex) {
        throw new InvalidRequestException(
            "Invalid value for timeoutMillis or value exceeded maximum timeout of 3w 3d 20h 30m.");
      }
    }
  }

  /*
  1) Remove duplicate variable names
  2) Replace null by empty String
   */
  private void prepareVariables(Map<String, Object> properties) {
    List<Map<String, String>> variableMapList = (List<Map<String, String>>) properties.get(ApprovalStateKeys.variables);
    Set<String> duplicateTracker = new HashSet<>();
    if (variableMapList != null) {
      Iterator<Map<String, String>> iterator = variableMapList.iterator();
      while (iterator.hasNext()) {
        Map<String, String> variableMap = iterator.next();
        String variableName = variableMap.get(NameValuePairKeys.name);
        if (!duplicateTracker.add(variableName)) {
          iterator.remove();
        }
        variableMap.putIfAbsent(NameValuePairKeys.value, StringUtils.EMPTY);
      }
    }
  }

  @VisibleForTesting
  void setPipelineVariables(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    Map<String, Object> workflowVariables = new HashMap<>();
    if (isNotEmpty(workflowStandardParams.getWorkflowVariables())) {
      workflowStandardParams.getWorkflowVariables().forEach(workflowVariables::put);
      if (isNotEmpty(workflowVariables)) {
        if (workflowStandardParams.getWorkflowElement() == null) {
          workflowStandardParams.setWorkflowElement(WorkflowElement.builder().variables(workflowVariables).build());
        } else {
          Map<String, Object> allStepWorkflowVariables = workflowStandardParams.getWorkflowElement().getVariables();
          allStepWorkflowVariables.putAll(workflowVariables);
          workflowStandardParams.getWorkflowElement().setVariables(allStepWorkflowVariables);
        }
      }
    }
  }

  private String createActivity(ExecutionContext executionContext) {
    Application app = executionContext.getApp();
    Environment env = ((ExecutionContextImpl) executionContext).getEnv();
    WorkflowStandardParams workflowStandardParams = executionContext.getContextElement(ContextElementType.STANDARD);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", workflowStandardParams.getCurrentUser(), USER);

    ActivityBuilder activityBuilder =
        Activity.builder()
            .applicationName(app.getName())
            .commandName(getName())
            .type(Type.Other)
            .workflowType(executionContext.getWorkflowType())
            .workflowExecutionName(executionContext.getWorkflowExecutionName())
            .stateExecutionInstanceId(executionContext.getStateExecutionInstanceId())
            .stateExecutionInstanceName(executionContext.getStateExecutionInstanceName())
            .commandType(getStateType())
            .workflowExecutionId(executionContext.getWorkflowExecutionId())
            .workflowId(executionContext.getWorkflowId())
            .commandUnits(
                asList(Builder.aCommand().withName(SCRIPT_APPROVAL_COMMAND).withCommandType(CommandType.OTHER).build()))
            .status(RUNNING)
            .triggeredBy(TriggeredBy.builder()
                             .email(workflowStandardParams.getCurrentUser().getEmail())
                             .name(workflowStandardParams.getCurrentUser().getName())
                             .build());

    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      activityBuilder.environmentId(GLOBAL_ENV_ID).environmentName(GLOBAL_ENV_ID).environmentType(ALL);
    } else {
      activityBuilder.environmentId(env != null ? env.getUuid() : ENVIRONMENT_ID)
          .environmentName(env != null ? env.getName() : ENVIRONMENT_NAME)
          .environmentType(env != null ? env.getEnvironmentType() : ALL);
    }

    Activity activity = activityBuilder.build();
    activity.setAppId(app.getUuid());
    return activityService.save(activity).getUuid();
  }

  private ExecutionResponse executeShellScriptApproval(ExecutionContext context, String accountId, String appId,
      String approvalId, ShellScriptApprovalParams parameters, ApprovalStateExecutionData executionData) {
    context.resetPreparedCache();
    int expressionFunctorToken = HashGenerator.generateIntegerHash();

    StateExecutionContext stateExecutionContext = StateExecutionContext.builder()
                                                      .stateExecutionData(executionData)
                                                      .scriptType(ScriptType.BASH)
                                                      .adoptDelegateDecryption(true)
                                                      .expressionFunctorToken(expressionFunctorToken)
                                                      .build();
    ExpressionReflectionUtils.applyExpression(
        parameters, (secretMode, value) -> context.renderExpression(value, stateExecutionContext));

    parameters.setDelegateSelectors(getDelegateSelectors(context, parameters.fetchDelegateSelectors()));

    String activityId = createActivity(context);
    executionData.setActivityId(activityId);

    executionData.setRetryInterval(parameters.getRetryInterval());

    ApprovalPollingJobEntity approvalPollingJobEntity =
        ApprovalPollingJobEntity.builder()
            .accountId(accountId)
            .appId(appId)
            .approvalId(approvalId)
            .stateExecutionInstanceId(context.getStateExecutionInstanceId())
            .workflowExecutionId(context.getWorkflowExecutionId())
            .activityId(activityId)
            .scriptString(parameters.getScriptString())
            .delegateSelectors(parameters.fetchDelegateSelectors())
            .approvalType(approvalStateType)
            .retryInterval(parameters.getRetryInterval())
            .expressionFunctorToken(expressionFunctorToken)
            .build();

    try {
      approvalPolingService.save(approvalPollingJobEntity);
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .async(true)
              .executionStatus(PAUSED)
              .errorMessage("Waiting for Approval")
              .correlationIds(singletonList(approvalId))
              .stateExecutionData(executionData));
    } catch (WingsException e) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage("Failed to schedule Approval" + e.getMessage())
              .stateExecutionData(executionData));
    }
  }

  @VisibleForTesting
  ExecutionResponse executeJiraApproval(
      ExecutionContext context, ApprovalStateExecutionData executionData, String approvalId) {
    JiraApprovalParams jiraApprovalParams = approvalStateParams.getJiraApprovalParams();
    boolean areRequiredFieldsTemplatized = ExpressionEvaluator.containsVariablePattern(jiraApprovalParams.getProject())
        || ExpressionEvaluator.containsVariablePattern(jiraApprovalParams.getRejectionValue())
        || ExpressionEvaluator.containsVariablePattern(jiraApprovalParams.getApprovalValue());
    jiraApprovalParams.setIssueId(context.renderExpression(jiraApprovalParams.getIssueId()));
    jiraApprovalParams.setApprovalValue(context.renderExpression(jiraApprovalParams.getApprovalValue()));
    jiraApprovalParams.setRejectionValue(context.renderExpression(jiraApprovalParams.getRejectionValue()));
    jiraApprovalParams.setProject(context.renderExpression(jiraApprovalParams.getProject()));

    if (ExpressionEvaluator.containsVariablePattern(jiraApprovalParams.getIssueId())) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage("Expression not rendered for Jira issue Id: " + jiraApprovalParams.getIssueId())
              .stateExecutionData(executionData));
    }

    Application app = context.getApp();

    if (areRequiredFieldsTemplatized) {
      try {
        validateRequiredFields(context, jiraApprovalParams);
      } catch (HarnessJiraException e) {
        log.error("Failing Approval Step due to: ", e);
        return respondWithStatus(context, executionData, null,
            ExecutionResponse.builder()
                .executionStatus(FAILED)
                .errorMessage(e.getMessage())
                .stateExecutionData(executionData));
      }
    }

    executionData.setApprovalField(jiraApprovalParams.getApprovalField());
    executionData.setApprovalValue(jiraApprovalParams.getApprovalValue());
    executionData.setRejectionField(jiraApprovalParams.getRejectionField());
    executionData.setRejectionValue(jiraApprovalParams.getRejectionValue());

    JiraExecutionData jiraExecutionData =
        jiraHelperService.fetchIssue(jiraApprovalParams, app.getAccountId(), app.getAppId(), approvalId);

    if (jiraExecutionData.getExecutionStatus() != null && FAILED == jiraExecutionData.getExecutionStatus()) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage(jiraExecutionData.getErrorMessage())
              .stateExecutionData(executionData));
    }

    executionData.setIssueUrl(jiraExecutionData.getIssueUrl());
    executionData.setIssueKey(jiraExecutionData.getIssueKey());
    executionData.setCurrentStatus(jiraExecutionData.getCurrentStatus());

    if (jiraExecutionData.getCurrentStatus().equalsIgnoreCase(jiraApprovalParams.getApprovalValue())) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(SUCCESS)
              .errorMessage("Approval provided on ticket: " + jiraExecutionData.getIssueKey())
              .stateExecutionData(executionData));
    }

    if (jiraApprovalParams.getRejectionValue() != null
        && jiraExecutionData.getCurrentStatus().equalsIgnoreCase(jiraApprovalParams.getRejectionValue())) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(REJECTED)
              .errorMessage("Rejection provided on ticket: " + jiraExecutionData.getIssueKey())
              .stateExecutionData(executionData));
    }

    ApprovalPollingJobEntity approvalPollingJobEntity =
        ApprovalPollingJobEntity.builder()
            .accountId(app.getAccountId())
            .appId(app.getAppId())
            .approvalField(jiraApprovalParams.getApprovalField())
            .rejectionField(jiraApprovalParams.getRejectionField())
            .approvalValue(jiraApprovalParams.getApprovalValue())
            .rejectionValue(jiraApprovalParams.getRejectionValue())
            .approvalId(approvalId)
            .approvalType(approvalStateType)
            .connectorId(jiraApprovalParams.getJiraConnectorId())
            .issueId(jiraApprovalParams.getIssueId())
            .stateExecutionInstanceId(context.getStateExecutionInstanceId())
            .workflowExecutionId(context.getWorkflowExecutionId())
            .build();
    try {
      // Create a cron job which polls JIRA for approval status

      String id = approvalPolingService.save(approvalPollingJobEntity);
      log.info("IssueId = {} while creating Jira polling Job. ApprovalPollingJobId: {}",
          jiraApprovalParams.getIssueId(), id);
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .async(true)
              .executionStatus(PAUSED)
              .errorMessage(jiraExecutionData.getErrorMessage())
              .correlationIds(asList(approvalId))
              .stateExecutionData(executionData));
    } catch (WingsException e) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage("Failed to schedule Approval" + e.getMessage())
              .stateExecutionData(executionData));
    }
  }

  @NotNull
  private List<String> getDelegateSelectors(ExecutionContext context, List<String> delegateSelectors) {
    List<String> renderedSelectorsSet = new ArrayList<>();

    if (EmptyPredicate.isNotEmpty(delegateSelectors)) {
      for (String selector : delegateSelectors) {
        renderedSelectorsSet.add(context.renderExpression(selector));
      }
    }
    return renderedSelectorsSet;
  }

  private void validateRequiredFields(ExecutionContext context, JiraApprovalParams jiraApprovalParams) {
    validateProject(context, jiraApprovalParams);
    validateStatus(context, jiraApprovalParams);
  }

  private void validateStatus(ExecutionContext context, JiraApprovalParams jiraApprovalParams) {
    Map<String, String> allowedStatuses =
        Arrays
            .stream(((JSONArray) jiraHelperService.getStatuses(jiraApprovalParams.getJiraConnectorId(),
                         jiraApprovalParams.getProject(), context.getAccountId(), context.getAppId()))
                        .toArray())
            .map(ob -> (net.sf.json.JSONObject) ob)
            .flatMap(issue -> Arrays.stream(((JSONArray) issue.get("statuses")).toArray()))
            .map(ob -> (net.sf.json.JSONObject) ob)
            .map(statuses -> (String) statuses.get("name"))
            .distinct()
            .collect(Collectors.toMap(String::toLowerCase, status -> status));
    if (!allowedStatuses.containsKey(jiraApprovalParams.getApprovalValue().toLowerCase())) {
      throw new HarnessJiraException(String.format("Invalid approval status [%s]. Please, check out allowed values %s",
                                         jiraApprovalParams.getApprovalValue(), allowedStatuses.values()),
          null);
    }
    if (StringUtils.isNotBlank(jiraApprovalParams.getRejectionValue())
        && !allowedStatuses.containsKey(jiraApprovalParams.getRejectionValue().toLowerCase())) {
      throw new HarnessJiraException(String.format("Invalid rejection status [%s]. Please, check out allowed values %s",
                                         jiraApprovalParams.getRejectionValue(), allowedStatuses.values()),
          null);
    }

    jiraApprovalParams.setApprovalValue(allowedStatuses.get(jiraApprovalParams.getApprovalValue().toLowerCase()));
    jiraApprovalParams.setRejectionValue(allowedStatuses.get(jiraApprovalParams.getRejectionValue().toLowerCase()));
  }

  private void validateProject(ExecutionContext context, JiraApprovalParams jiraApprovalParams) {
    Map<String, String> projects = Arrays
                                       .stream(jiraHelperService
                                                   .getProjects(jiraApprovalParams.getJiraConnectorId(),
                                                       context.getAccountId(), context.getAppId())
                                                   .toArray())
                                       .map(ob -> (net.sf.json.JSONObject) ob)
                                       .map(existingProjects -> (String) existingProjects.get("key"))
                                       .collect(Collectors.toMap(String::toLowerCase, projectKey -> projectKey));
    if (!projects.containsKey(jiraApprovalParams.getProject().toLowerCase())) {
      throw new HarnessJiraException(String.format("Invalid project key [%s]. Please, check out allowed values: %s",
                                         jiraApprovalParams.getProject(), projects.values()),
          null);
    } else {
      jiraApprovalParams.setProject(projects.get(jiraApprovalParams.getProject().toLowerCase()));
    }
  }

  @VisibleForTesting
  ExecutionResponse executeServiceNowApproval(
      ExecutionContext context, ApprovalStateExecutionData executionData, String approvalId) {
    ServiceNowApprovalParams servicenowApprovalParams = approvalStateParams.getServiceNowApprovalParams();
    servicenowApprovalParams.setIssueNumber(context.renderExpression(servicenowApprovalParams.getIssueNumber()));

    if (ExpressionEvaluator.containsVariablePattern(servicenowApprovalParams.getIssueNumber())) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage("Expression not rendered for issue Number: " + servicenowApprovalParams.getIssueNumber())
              .stateExecutionData(executionData));
    }

    executionData.setSnowApproval(servicenowApprovalParams.getApproval());
    executionData.setSnowRejection(servicenowApprovalParams.getRejection());

    Application app = context.getApp();

    try {
      ServiceNowExecutionData serviceNowExecutionData =
          serviceNowService.getIssueUrl(app.getAppId(), app.getAccountId(), servicenowApprovalParams);
      executionData.setTicketUrl(serviceNowExecutionData.getIssueUrl());
      executionData.setCurrentStatus(serviceNowExecutionData.getCurrentState());
      executionData.setTicketType(servicenowApprovalParams.getTicketType());

      ExecutionResponse executionResponse =
          checkForSnowApprovalOrRejection(executionData, servicenowApprovalParams, serviceNowExecutionData, context);
      if (executionResponse != null) {
        return executionResponse;
      }

    } catch (WingsException we) {
      String errorMessage = ExceptionUtils.getMessage(we);
      if (we.getParams() != null && we.getParams().get("message") != null) {
        errorMessage = we.getParams().get("message").toString();
      }
      log.error("Exception while executing service now approval in workflow: {}", context.getWorkflowExecutionId(), we);
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage(errorMessage)
              .stateExecutionData(executionData));
    }
    return createApprovalPollingJob(context, executionData, approvalId, servicenowApprovalParams, app);
  }

  private ExecutionResponse createApprovalPollingJob(ExecutionContext context, ApprovalStateExecutionData executionData,
      String approvalId, ServiceNowApprovalParams servicenowApprovalParams, Application app) {
    // Create a cron job which polls ServiceNow for approval status
    log.info("IssueId = {} while creating ServiceNow polling Job", servicenowApprovalParams.getIssueNumber());
    ApprovalPollingJobEntity approvalPollingJobEntity =
        ApprovalPollingJobEntity.builder()
            .accountId(app.getAccountId())
            .appId(app.getAppId())
            .approval(servicenowApprovalParams.getApproval())
            .rejection(servicenowApprovalParams.getRejection())
            .changeWindowPresent(servicenowApprovalParams.isChangeWindowPresent())
            .changeWindowStartField(servicenowApprovalParams.getChangeWindowStartField())
            .changeWindowEndField(servicenowApprovalParams.getChangeWindowEndField())
            .approvalId(approvalId)
            .approvalType(approvalStateType)
            .connectorId(servicenowApprovalParams.getSnowConnectorId())
            .issueNumber(servicenowApprovalParams.getIssueNumber())
            .issueType(servicenowApprovalParams.getTicketType())
            .stateExecutionInstanceId(context.getStateExecutionInstanceId())
            .workflowExecutionId(context.getWorkflowExecutionId())
            .build();

    try {
      approvalPolingService.save(approvalPollingJobEntity);
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .async(true)
              .executionStatus(PAUSED)
              .errorMessage(servicenowApprovalParams.isChangeWindowPresent()
                      ? executionData.getErrorMsg()
                      : ("Waiting for approval on Ticket " + servicenowApprovalParams.getIssueNumber()))
              .correlationIds(asList(approvalId))
              .stateExecutionData(executionData));
    } catch (WingsException e) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage("Failed to schedule Approval" + e.getMessage())
              .stateExecutionData(executionData));
    }
  }

  void mapApprovalObject(Object from, Object to) {
    ModelMapper modelMapper = new ModelMapper();
    modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

    // need to remove delegate selector due to modelmapper issue
    Object delegateSelectors = removeDelegateSelectorsForShellScriptApproval((Map<String, Object>) from);
    modelMapper.map(from, to);
    repopulateDelegateSelector(from, delegateSelectors, to);
  }

  private void repopulateDelegateSelector(Object from, Object delegateSelectors, Object to) {
    repopulateSourceHashmapWithDelegateSelector((Map<String, Object>) from, delegateSelectors);
    setDelegateSelectorForShellScriptApproval(delegateSelectors, to);
  }

  private void repopulateSourceHashmapWithDelegateSelector(Map<String, Object> source, Object delegateSelectors) {
    if (delegateSelectors != null) {
      Map<String, Object> approvalStateParams = (Map<String, Object>) source.get(ApprovalStateKeys.approvalStateParams);
      Map<String, Object> shellScriptApprovalParams =
          (Map<String, Object>) approvalStateParams.get(ApprovalStateParamsKeys.shellScriptApprovalParams);
      shellScriptApprovalParams.put(ShellScriptApprovalParamsKeys.delegateSelectors, delegateSelectors);
    }
  }

  private void setDelegateSelectorForShellScriptApproval(Object delegateSelectors, Object to) {
    if (delegateSelectors != null) {
      ApprovalState approvalState = (ApprovalState) to;

      approvalState.getApprovalStateParams().getShellScriptApprovalParams().setDelegateSelectors(
          (List<String>) delegateSelectors);
    }
  }

  private Object removeDelegateSelectorsForShellScriptApproval(Map<String, Object> source) {
    if (source.get(ApprovalStateKeys.approvalStateParams) != null) {
      Map<String, Object> approvalStateParams = (Map<String, Object>) source.get(ApprovalStateKeys.approvalStateParams);
      if (approvalStateParams.get(ApprovalStateParamsKeys.shellScriptApprovalParams) != null) {
        Map<String, Object> shellScriptApprovalParams =
            (Map<String, Object>) approvalStateParams.get(ApprovalStateParamsKeys.shellScriptApprovalParams);
        Object delegateSelector = shellScriptApprovalParams.get(ShellScriptApprovalParamsKeys.delegateSelectors);
        shellScriptApprovalParams.remove(ShellScriptApprovalParamsKeys.delegateSelectors);
        return delegateSelector;
      }
    }
    return null;
  }

  @Nullable
  private ExecutionResponse checkForSnowApprovalOrRejection(ApprovalStateExecutionData executionData,
      ServiceNowApprovalParams servicenowApprovalParams, ServiceNowExecutionData serviceNowExecutionData,
      ExecutionContext context) {
    Map<String, String> currentStatus = serviceNowExecutionData.getCurrentStatus();

    if (executionData.getSnowApproval() == null || isEmpty(executionData.getSnowApproval().fetchConditions())) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(FAILED)
              .errorMessage("Approval criteria empty in service now approval state")
              .stateExecutionData(executionData));
    }

    if (executionData.getSnowApproval().satisfied(currentStatus)) {
      if (servicenowApprovalParams.withinChangeWindow(currentStatus)) {
        return respondWithStatus(context, executionData, null,
            ExecutionResponse.builder()
                .executionStatus(SUCCESS)
                .errorMessage("Approval provided on Ticket: " + servicenowApprovalParams.getIssueNumber())
                .stateExecutionData(executionData));
      }
      executionData.setTimeoutMillis(Integer.MAX_VALUE);
      executionData.setWaitingForChangeWindow(true);
      executionData.setErrorMsg(
          "Approved but waiting for Change Window (" + serviceNowExecutionData.getMessage() + " )");
    }

    if (executionData.getSnowRejection() != null && executionData.getSnowRejection().satisfied(currentStatus)) {
      return respondWithStatus(context, executionData, null,
          ExecutionResponse.builder()
              .executionStatus(REJECTED)
              .errorMessage("Rejection provided on Ticket: " + servicenowApprovalParams.getIssueNumber())
              .stateExecutionData(executionData));
    }
    return null;
  }

  private ExecutionResponse executeUserGroupApproval(List<String> userGroups, String accountId,
      Map<String, String> placeholderValues, String approvalId, ApprovalStateExecutionData executionData, String appId,
      ExecutionContext context) {
    executionData.setUserGroups(userGroups);
    executionData.setAutoRejectPreviousDeployments(autoRejectPreviousDeployments);
    updatePlaceholderValuesForSlackApproval(approvalId, accountId, placeholderValues, context);
    sendNotificationForUserGroupApproval(userGroups, appId, accountId, APPROVAL_NEEDED_NOTIFICATION, placeholderValues);

    return respondWithStatus(context, executionData, null,
        ExecutionResponse.builder()
            .async(true)
            .executionStatus(PAUSED)
            .correlationIds(asList(approvalId))
            .stateExecutionData(executionData));
  }

  @VisibleForTesting
  void updatePlaceholderValuesForSlackApproval(
      String approvalId, String accountId, Map<String, String> placeHolderValues, ExecutionContext context) {
    String pausedStageName = null;
    StringBuilder environments = new StringBuilder(128);
    StringBuilder services = new StringBuilder(128);
    WorkflowNotificationDetails serviceDetails = null;
    StringBuilder artifacts = new StringBuilder(128);
    StringBuilder infrastructureDefinitions = new StringBuilder(128);
    WorkflowNotificationDetails infraDetails = null;

    WorkflowNotificationDetails applicationDetails =
        workflowNotificationHelper.calculateApplicationDetails(accountId, context.getAppId(), context.getApp());
    String appDetails = applicationDetails.getMessage();
    String appName = applicationDetails.getName();

    int tokenValidDuration = getTimeoutMillis();

    boolean isPipeline = context.getWorkflowType() == WorkflowType.PIPELINE;
    if (isPipeline) {
      pausedStageName = context.getPipelineStageName();
    } else {
      pausedStageName = context.getStateExecutionInstanceName();
    }

    WorkflowExecution workflowExecution = workflowExecutionService.fetchWorkflowExecution(context.getAppId(),
        context.getWorkflowExecutionId(), WorkflowExecutionKeys.artifacts, WorkflowExecutionKeys.environments,
        WorkflowExecutionKeys.serviceIds, WorkflowExecutionKeys.infraDefinitionIds);

    if (isNotEmpty(workflowExecution.getArtifacts())) {
      artifacts.append(
          workflowNotificationHelper.getArtifactsDetails(context, workflowExecution, ExecutionScope.WORKFLOW, null)
              .getMessage());
    } else {
      artifacts.append("*Artifacts*: no artifacts");
    }
    if (isNotEmpty(workflowExecution.getEnvironments())) {
      StringJoiner env = new StringJoiner(", ");
      workflowExecution.getEnvironments().forEach(envSummary -> env.add(envSummary.getName()));
      environments.append("*Environments*: ").append(env.toString());
    } else {
      environments.append("*Environments*: no environments");
    }
    String envDetailsForEmail =
        workflowNotificationHelper
            .calculateEnvDetails(accountId, context.getAppId(), workflowExecution.getEnvironments())
            .getMessage();
    placeHolderValues.put("ENV", envDetailsForEmail.replace("*Environments:*", "<b>Environments:</b>"));
    if (isNotEmpty(workflowExecution.getServiceIds())) {
      serviceDetails = workflowNotificationHelper.calculateServiceDetailsForAllServices(
          accountId, context.getAppId(), context, workflowExecution, ExecutionScope.WORKFLOW, null);
      services.append("*Services*: ").append(serviceDetails.getName());
      placeHolderValues.put("SERVICE_NAMES", serviceDetails.getMessage().replace("*Services*", "<b>Services</b>"));
    } else {
      services.append("*Services*: no services");
      placeHolderValues.put("SERVICE_NAMES", services.toString().replace("*Services*", "<b>Services</b>"));
    }
    List<String> infraDefinitionIds = workflowExecution.getInfraDefinitionIds();
    if (isNotEmpty(infraDefinitionIds)) {
      infraDetails = workflowNotificationHelper.calculateInfraDetails(accountId, context.getAppId(), workflowExecution);
      infrastructureDefinitions.append("*Infrastructure Definitions*: ").append(infraDetails.getName());
      placeHolderValues.put("INFRA_NAMES",
          infraDetails.getMessage().replace("*Infrastructure Definitions*", "<b>Infrastructure Definitions</b>"));

    } else {
      infrastructureDefinitions.append("*Infrastructure Definitions*: no infrastructure definitions");
      placeHolderValues.put("INFRA_NAMES",
          infrastructureDefinitions.toString().replace(
              "*Infrastructure Definitions*", "<b>Infrastructure Definitions</b>"));
    }

    Map<String, String> claims = new HashMap<>();
    claims.put("approvalId", approvalId);
    String workflowURL = placeHolderValues.get("WORKFLOW_URL");

    String jwtToken = secretManager.generateJWTTokenWithCustomTimeOut(
        claims, secretManager.getJWTSecret(EXTERNAL_SERVICE_SECRET), tokenValidDuration);

    placeHolderValues.put("APPROVAL_STEP", String.format("Approval Step: <<<%s|-|%s>>>", workflowURL, pausedStageName));
    placeHolderValues.put("WORKFLOW", String.format("<<<%s|-|%s>>>", workflowURL, context.getWorkflowExecutionName()));
    placeHolderValues.put("APP", appDetails);
    placeHolderValues.put("ARTIFACTS", artifacts.toString().replace("*Artifacts*", "<b>Artifacts</b>"));

    SlackApprovalParams slackApprovalParams =
        SlackApprovalParams.builder()
            .appId(context.getAppId())
            .appName(appDetails)
            .nonFormattedAppName(appName)
            .routingId(accountId)
            .deploymentId(context.getWorkflowExecutionId())
            .workflowId(context.getWorkflowId())
            .workflowExecutionName(context.getWorkflowExecutionName())
            .stateExecutionId(context.getStateExecutionInstanceId())
            .stateExecutionInstanceName(context.getStateExecutionInstanceName())
            .approvalId(approvalId)
            .pausedStageName(pausedStageName)
            .servicesInvolved(services.toString())
            .environmentsInvolved(environments.toString())
            .artifactsInvolved(artifacts.toString())
            .infraDefinitionsInvolved(infrastructureDefinitions.toString())
            .confirmation(false)
            .pipeline(isPipeline)
            .workflowUrl(workflowURL)
            .jwtToken(jwtToken)
            .startTsSecs(placeHolderValues.get(SlackApprovalMessageKeys.START_TS_SECS))
            .endTsSecs(placeHolderValues.get(SlackApprovalMessageKeys.END_TS_SECS))
            .startDate(placeHolderValues.get(SlackApprovalMessageKeys.START_DATE))
            .expiryTsSecs(placeHolderValues.get(SlackApprovalMessageKeys.EXPIRES_TS_SECS))
            .endDate(placeHolderValues.get(SlackApprovalMessageKeys.END_DATE))
            .expiryDate(placeHolderValues.get(SlackApprovalMessageKeys.EXPIRES_DATE))
            .verb(placeHolderValues.get(SlackApprovalMessageKeys.VERB))
            .triggeredByUser("*Triggered By*: " + placeHolderValues.get(USER_NAME))
            .build();
    JSONObject customData = createCustomData(slackApprovalParams);

    URL notificationTemplateUrl;
    if (slackApprovalParams.isPipeline()) {
      notificationTemplateUrl =
          this.getClass().getResource(SlackApprovalMessageKeys.PIPELINE_APPROVAL_MESSAGE_TEMPLATE);
    } else {
      notificationTemplateUrl =
          this.getClass().getResource(SlackApprovalMessageKeys.WORKFLOW_APPROVAL_MESSAGE_TEMPLATE);
    }

    String displayText = createSlackApprovalMessage(slackApprovalParams, notificationTemplateUrl);
    String validatedMessage = validateMessageLength(displayText, slackApprovalParams, notificationTemplateUrl,
        serviceDetails, artifacts, infraDetails, environments);
    String buttonValue = customData.toString();
    buttonValue = StringEscapeUtils.escapeJson(buttonValue);
    placeHolderValues.put(SlackApprovalMessageKeys.SLACK_APPROVAL_PARAMS, buttonValue);
    placeHolderValues.put(SlackApprovalMessageKeys.APPROVAL_MESSAGE, validatedMessage);
    placeHolderValues.put(SlackApprovalMessageKeys.MESSAGE_IDENTIFIER, "suppressTraditionalNotificationOnSlack");
  }

  @VisibleForTesting
  String validateMessageLength(String displayText, SlackApprovalParams slackApprovalParams, URL notificationTemplateUrl,
      WorkflowNotificationDetails serviceDetails, StringBuilder artifacts, WorkflowNotificationDetails infraDetails,
      StringBuilder environments) {
    // Current caller never send notificationTemplateUrl argument as null
    if (displayText.length() < 1900) {
      return displayText;
    } else {
      int serviceCount = 0;
      boolean areServicesTrimmed = false;
      if (serviceDetails != null) {
        serviceCount = serviceDetails.getName().split(",").length;
        areServicesTrimmed = trimNotificationDetails(serviceDetails, serviceCount);
      }

      int artifactsCount = 0;
      boolean areArtifactsTrimmed = false;
      if (artifacts != null) {
        artifactsCount = artifacts.toString().replace("*Artifacts*: ", "").split(", ").length;
        areArtifactsTrimmed = trimArtifacts(artifacts, artifactsCount);
      }

      int infraCount = 0;
      boolean areInfrasTrimmed = false;
      if (infraDetails != null) {
        infraCount = infraDetails.getName().split(",").length;
        areInfrasTrimmed = trimNotificationDetails(infraDetails, infraCount);
      }

      int envCount = 0;
      boolean areEnvsTrimmed = false;
      if (environments != null) {
        envCount = environments.toString().replace("*Environments*: ", "").split(", ").length;
        areEnvsTrimmed = trimEnvironments(environments, envCount);
      }

      SlackApprovalParams params =
          slackApprovalParams.toBuilder()
              .servicesInvolved(areServicesTrimmed
                      ? String.format("*Services*: %s... %s more", serviceDetails.getName(), serviceCount - 3)
                      : slackApprovalParams.getServicesInvolved())
              .artifactsInvolved(areArtifactsTrimmed
                      ? String.format("*Artifacts*: %s... %s more", artifacts.toString(), artifactsCount - 3)
                      : slackApprovalParams.getArtifactsInvolved())
              .infraDefinitionsInvolved(areInfrasTrimmed ? String.format("*Infrastructure Definitions*: %s... %s more",
                                            infraDetails.getName(), infraCount - 3)
                                                         : slackApprovalParams.getInfraDefinitionsInvolved())
              .environmentsInvolved(areEnvsTrimmed
                      ? String.format("*Environments*: %s... %s more", environments, envCount - 3)
                      : slackApprovalParams.getEnvironmentsInvolved())
              .build();
      return createSlackApprovalMessage(params, notificationTemplateUrl);
    }
  }

  private boolean trimArtifacts(StringBuilder artifactsDetails, int artifactsCount) {
    if (artifactsCount > 3) {
      String[] trimmedArtifacts =
          Arrays.copyOfRange(artifactsDetails.toString().replace("*Artifacts*: ", "").split(", "), 0, 3);
      StringJoiner artifacts = new StringJoiner(", ");
      for (String artifact : trimmedArtifacts) {
        artifacts.add(artifact);
      }
      artifactsDetails.setLength(0);
      artifactsDetails.append(artifacts);
      return true;
    }
    return false;
  }

  private boolean trimEnvironments(StringBuilder environmentsDetails, int environmentsCount) {
    if (environmentsCount > 3) {
      String[] trimmedArtifacts =
          Arrays.copyOfRange(environmentsDetails.toString().replace("*Environments*: ", "").split(", "), 0, 3);
      StringJoiner artifacts = new StringJoiner(", ");
      for (String artifact : trimmedArtifacts) {
        artifacts.add(artifact);
      }
      environmentsDetails.setLength(0);
      environmentsDetails.append(artifacts);
      return true;
    }
    return false;
  }

  private boolean trimNotificationDetails(WorkflowNotificationDetails notificationDetails, int detailCount) {
    if (detailCount > 3) {
      String[] trimmedDetails = Arrays.copyOfRange(notificationDetails.getName().split(","), 0, 3);
      StringJoiner details = new StringJoiner(", ");
      for (String detail : trimmedDetails) {
        details.add(detail);
      }
      notificationDetails.setName(details.toString());
      return true;
    }
    return false;
  }

  private JSONObject createCustomData(SlackApprovalParams slackApprovalParams) {
    return new JSONObject(SlackApprovalParams.getExternalParams(slackApprovalParams));
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ApprovalStateExecutionData approvalNotifyResponse =
        (ApprovalStateExecutionData) response.values().iterator().next();

    boolean isApprovalFromSlack = approvalNotifyResponse.isApprovalFromSlack();
    boolean isApprovalFromGraphQL = approvalNotifyResponse.isApprovalFromGraphQL();

    if (isNotEmpty(approvalNotifyResponse.getVariables())) {
      setVariables(approvalNotifyResponse.getVariables());
    }

    // Close the alert
    Application app = context.getApp();
    ApprovalNeededAlert approvalNeededAlert = ApprovalNeededAlert.builder()
                                                  .executionId(context.getWorkflowExecutionId())
                                                  .approvalId(approvalNotifyResponse.getApprovalId())
                                                  .build();
    populateApprovalAlert(approvalNeededAlert, context);
    alertService.closeAlert(app.getAccountId(), app.getUuid(), ApprovalNeeded, approvalNeededAlert);

    ApprovalStateExecutionData executionData = (ApprovalStateExecutionData) context.getStateExecutionData();
    executionData.setApprovedBy(approvalNotifyResponse.getApprovedBy());
    executionData.setComments(approvalNotifyResponse.getComments());
    executionData.setApprovedOn(System.currentTimeMillis());
    executionData.setCurrentStatus(approvalNotifyResponse.getCurrentStatus());
    executionData.setApprovalFromGraphQL(isApprovalFromGraphQL);
    executionData.setApprovalViaApiKey(approvalNotifyResponse.isApprovalViaApiKey());

    Map<String, String> placeholderValues;
    if (approvalNotifyResponse.getApprovedBy() != null) {
      placeholderValues = getPlaceholderValues(
          context, approvalNotifyResponse.getApprovedBy().getName(), approvalNotifyResponse.getStatus());
    } else {
      placeholderValues = getPlaceholderValues(context, "", approvalNotifyResponse.getStatus());
    }

    if (approvalStateType == null) {
      approvalStateType = USER_GROUP;
    }
    workflowNotificationHelper.sendApprovalNotification(
        app.getAccountId(), WORKFLOW_RESUME_NOTIFICATION, placeholderValues, context, approvalStateType);

    executionData.setVariables(approvalNotifyResponse.getVariables());

    switch (approvalStateType) {
      case JIRA:
        return handleAsyncJira(context, executionData, approvalNotifyResponse);
      case SERVICENOW:
        return handleAsyncServiceNow(context, executionData, approvalNotifyResponse);
      case USER_GROUP:
        return handleAsyncUserGroup(
            userGroups, placeholderValues, context, executionData, approvalNotifyResponse, isApprovalFromSlack);
      case SHELL_SCRIPT:
        return handleAsyncShellScript(context, executionData, approvalNotifyResponse);
      default:
        throw new InvalidRequestException("Invalid ApprovalStateType");
    }
  }

  void fillSweepingOutput(ExecutionContext context, ApprovalStateExecutionData executionData,
      ApprovalStateExecutionData approvalNotifyResponse) {
    Map<String, Object> output = new HashMap<>();
    Map<String, Object> variableMap = new HashMap<>();
    if (isNotEmpty(variables)) {
      for (NameValuePair expression : variables) {
        variableMap.put(expression.getName(),
            context.renderExpression(
                expression.getValue(), StateExecutionContext.builder().stateExecutionData(executionData).build()));
        // We would want to deprecate use of directly accessing variables, once that is done,
        // this block of code can be removed
        output.put(expression.getName(),
            context.renderExpression(
                expression.getValue(), StateExecutionContext.builder().stateExecutionData(executionData).build()));
      }
    }

    output.put(ApprovalStateExecutionDataKeys.variables, variableMap);
    output.put(ApprovalStateExecutionDataKeys.approvalStateType, executionData.getApprovalStateType());
    output.put(ApprovalStateExecutionDataKeys.timeoutMillis, executionData.getTimeoutMillis());
    output.put(APPROVAL_STATUS_KEY,
        approvalNotifyResponse != null ? StringUtils.capitalize(String.valueOf(approvalNotifyResponse.getStatus()))
                                       : null);
    switch (executionData.getApprovalStateType()) {
      case USER_GROUP:
        output.put(ApprovalStateExecutionDataKeys.approvedBy, executionData.getApprovedBy());
        output.put(ApprovalStateExecutionDataKeys.approvedOn, executionData.getApprovedOn());
        output.put(ApprovalStateExecutionDataKeys.comments, executionData.getComments());
        if (isNotEmpty(executionData.getUserGroups())) {
          output.put(ApprovalStateExecutionDataKeys.userGroups,
              userGroupService.fetchUserGroupNamesFromIds(executionData.getUserGroups()));
        }
        // Via GraphQL Approval
        output.put(ApprovalStateExecutionDataKeys.approvalFromGraphQL, executionData.isApprovalFromGraphQL());
        break;
      case JIRA:
        output.put(ApprovalStateExecutionDataKeys.issueUrl, executionData.getIssueUrl());
        output.put(ApprovalStateExecutionDataKeys.issueKey, executionData.getIssueKey());
        output.put(ApprovalStateExecutionDataKeys.currentStatus, executionData.getCurrentStatus());
        output.put(ApprovalStateExecutionDataKeys.approvalField, executionData.getApprovalField());
        output.put(ApprovalStateExecutionDataKeys.approvalValue, executionData.getApprovalValue());
        output.put(ApprovalStateExecutionDataKeys.rejectionField, executionData.getRejectionField());
        output.put(ApprovalStateExecutionDataKeys.rejectionValue, executionData.getRejectionValue());
        break;
      case SERVICENOW:
        output.put(ApprovalStateExecutionDataKeys.ticketUrl, executionData.getTicketUrl());
        output.put(ApprovalStateExecutionDataKeys.ticketType, executionData.getTicketType());
        break;
      case SHELL_SCRIPT:
        break;
      default:
        log.warn("Unsupported approval type ", executionData.getApprovalStateType());
    }

    // Slack Approval
    output.put(ApprovalStateExecutionDataKeys.approvalFromSlack, executionData.isApprovalFromSlack());

    handleSweepingOutput(sweepingOutputService, context, output);
  }

  private ExecutionResponse handleAsyncShellScript(ExecutionContext context, ApprovalStateExecutionData executionData,
      ApprovalStateExecutionData approvalNotifyResponse) {
    String errorMessage;
    if (approvalNotifyResponse.getStatus() == REJECTED) {
      errorMessage = "Rejected by Script";
    } else if (approvalNotifyResponse.getStatus() == SUCCESS) {
      errorMessage = "Approved by Script";
    } else {
      errorMessage = "Waiting for Approval";
    }

    setPipelineVariables(context);
    approvalPolingService.delete(executionData.getApprovalId());

    return respondWithStatus(context, executionData, approvalNotifyResponse,
        ExecutionResponse.builder()
            .stateExecutionData(executionData)
            .executionStatus(approvalNotifyResponse.getStatus())
            .errorMessage(errorMessage));
  }

  private ExecutionResponse respondWithStatus(ExecutionContext context, ApprovalStateExecutionData executionData,
      ApprovalStateExecutionData approvalNotifyResponse, ExecutionResponseBuilder executionResponseBuilder) {
    ExecutionResponse executionResponse = executionResponseBuilder.build();
    if (ExecutionStatus.isFinalStatus(executionResponse.getExecutionStatus())) {
      fillSweepingOutput(context, executionData, approvalNotifyResponse);
    }
    return executionResponse;
  }

  private ExecutionResponse handleAsyncJira(ExecutionContext context, ApprovalStateExecutionData executionData,
      ApprovalStateExecutionData approvalNotifyResponse) {
    JiraApprovalParams jiraApprovalParams = approvalStateParams.getJiraApprovalParams();
    setPipelineVariables(context);
    jiraApprovalParams.setIssueId(context.renderExpression(jiraApprovalParams.getIssueId()));
    log.info("Deleting job for approvalId: {}, workflowExecutionId: {} ", executionData.getApprovalId(),
        executionData.getWorkflowId());
    approvalPolingService.delete(executionData.getApprovalId());

    return respondWithStatus(context, executionData, approvalNotifyResponse,
        ExecutionResponse.builder()
            .stateExecutionData(executionData)
            .executionStatus(approvalNotifyResponse.getStatus())
            .errorMessage(approvalNotifyResponse.getErrorMsg() + executionData.getIssueKey()));
  }

  private ExecutionResponse handleAsyncServiceNow(ExecutionContext context, ApprovalStateExecutionData executionData,
      ApprovalStateExecutionData approvalNotifyResponse) {
    ServiceNowApprovalParams servicenowApprovalParams = approvalStateParams.getServiceNowApprovalParams();
    servicenowApprovalParams.setIssueNumber(context.renderExpression(servicenowApprovalParams.getIssueNumber()));

    log.info("Deleting job for approvalId: {}, workflowExecutionId: {} ", executionData.getApprovalId(),
        executionData.getWorkflowId());
    approvalPolingService.delete(executionData.getApprovalId());

    setPipelineVariables(context);
    return respondWithStatus(context, executionData, approvalNotifyResponse,
        ExecutionResponse.builder()
            .stateExecutionData(executionData)
            .executionStatus(approvalNotifyResponse.getStatus())
            .errorMessage(approvalNotifyResponse.getErrorMsg() + servicenowApprovalParams.getIssueNumber()));
  }

  private ExecutionResponse handleAsyncUserGroup(List<String> userGroups, Map<String, String> placeholderValues,
      ExecutionContext context, ApprovalStateExecutionData executionData,
      ApprovalStateExecutionData approvalNotifyResponse, boolean isApprovalFromSlack) {
    Application app = context.getApp();
    if (!isApprovalFromSlack) {
      sendNotificationForUserGroupApproval(
          userGroups, app.getUuid(), app.getAccountId(), APPROVAL_STATE_CHANGE_NOTIFICATION, placeholderValues);
    }

    return respondWithStatus(context, executionData, approvalNotifyResponse,
        ExecutionResponse.builder()
            .stateExecutionData(executionData)
            .executionStatus(approvalNotifyResponse.getStatus()));
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    if (context == null || context.getStateExecutionData() == null) {
      return;
    }

    Application app = context.getApp();
    Integer timeout = getTimeoutMillis();
    Long startTimeMillis = context.getStateExecutionData().getStartTs();
    long currentTimeMillis = System.currentTimeMillis();
    NotificationMessageType notificationMessageType;

    String errorMsg;
    String approvalType = "";
    if (((ExecutionContextImpl) context).getStateExecutionInstance() != null
        && ((ExecutionContextImpl) context).getStateExecutionInstance().getExecutionType() != null) {
      approvalType = notificationMessageResolver.getApprovalType(
          ((ExecutionContextImpl) context).getStateExecutionInstance().getExecutionType());
    }

    Map<String, String> placeholderValues;
    if (approvalStateType == null) {
      approvalStateType = USER_GROUP;
    }
    if (currentTimeMillis >= (timeout + startTimeMillis)) {
      if (approvalType != null && approvalType.equalsIgnoreCase("PIPELINE")) {
        errorMsg = "Pipeline was not approved within " + Misc.getDurationString(getTimeoutMillis());
      } else if (approvalType != null && approvalType.equalsIgnoreCase("ORCHESTRATION")) {
        errorMsg = "Workflow was not approved within " + Misc.getDurationString(getTimeoutMillis());
      } else {
        errorMsg = "Approval not approved within " + Misc.getDurationString(getTimeoutMillis());
      }
      notificationMessageType = APPROVAL_EXPIRED_NOTIFICATION;
      placeholderValues = getPlaceholderValues(context, errorMsg);
      workflowNotificationHelper.sendApprovalNotification(
          app.getAccountId(), APPROVAL_EXPIRED_WORKFLOW_NOTIFICATION, placeholderValues, context, approvalStateType);
    } else {
      if (approvalType != null && approvalType.equalsIgnoreCase("PIPELINE")) {
        errorMsg = "Pipeline was aborted";
      } else if (approvalType != null && approvalType.equalsIgnoreCase("ORCHESTRATION")) {
        errorMsg = "Workflow was aborted";
      } else {
        errorMsg = "Workflow or Pipeline was aborted";
      }

      notificationMessageType = APPROVAL_STATE_CHANGE_NOTIFICATION;
      User user = UserThreadLocal.get();
      String userName = (user != null && user.getName() != null) ? user.getName() : "System";
      placeholderValues = getPlaceholderValues(context, userName, ABORTED);
      workflowNotificationHelper.sendApprovalNotification(
          app.getAccountId(), WORKFLOW_ABORT_NOTIFICATION, placeholderValues, context, approvalStateType);
    }

    context.getStateExecutionData().setErrorMsg(errorMsg);

    switch (approvalStateType) {
      case JIRA:
        handleAbortEventJira(context);
        return;
      case SERVICENOW:
        handleAbortEventServiceNow(context);
        return;
      case USER_GROUP:
        sendNotificationForUserGroupApproval(
            userGroups, app.getUuid(), app.getAccountId(), notificationMessageType, placeholderValues);
        return;
      case SHELL_SCRIPT:
        handleAbortScriptApproval(context);
        return;
      default:
        throw new InvalidRequestException("Invalid ApprovalStateType : neither JIRA nor USER_GROUP");
    }
  }

  private void sendNotificationForUserGroupApproval(List<String> approvalUserGroups, String appId, String accountId,
      NotificationMessageType notificationMessageType, Map<String, String> placeHolderValues) {
    NotificationRule rule = NotificationRuleBuilder.aNotificationRule().withUserGroupIds(approvalUserGroups).build();
    InformationNotification notification = InformationNotification.builder()
                                               .appId(appId)
                                               .accountId(accountId)
                                               .notificationTemplateId(notificationMessageType.name())
                                               .notificationTemplateVariables(placeHolderValues)
                                               .build();

    notificationService.sendNotificationAsync(notification, Collections.singletonList(rule));
  }

  private void handleAbortScriptApproval(ExecutionContext context) {
    ApprovalStateExecutionData executionData = (ApprovalStateExecutionData) context.getStateExecutionData();
    approvalPolingService.delete(executionData.getApprovalId());
    // left to clean up the old jobs. Remove later.
    serviceJobScheduler.deleteJob(executionData.getApprovalId(), SCRIPT_APPROVAL_JOB_GROUP);
  }

  private void handleAbortEventJira(ExecutionContext context) {
    ApprovalStateExecutionData executionData = (ApprovalStateExecutionData) context.getStateExecutionData();
    approvalPolingService.delete(executionData.getApprovalId());
  }

  private void handleAbortEventServiceNow(ExecutionContext context) {
    ApprovalStateExecutionData executionData = (ApprovalStateExecutionData) context.getStateExecutionData();
    approvalPolingService.delete(executionData.getApprovalId());
  }

  @Override
  public Integer getTimeoutMillis() {
    if (super.getTimeoutMillis() == null) {
      return DEFAULT_APPROVAL_STATE_TIMEOUT_MILLIS;
    }
    return super.getTimeoutMillis();
  }

  public static String fetchAndTrimSweepingOutputName(Map<String, Object> propertyMap) {
    String publishedVarName = propertyMap.getOrDefault(ApprovalStateKeys.sweepingOutputName, "").toString().trim();
    if (isNotEmpty(publishedVarName)) {
      propertyMap.put(ApprovalStateKeys.sweepingOutputName, publishedVarName);
    }
    return publishedVarName;
  }

  private static String getStatusMessage(ExecutionStatus status) {
    switch (status) {
      case SUCCESS:
        return "approved";
      case ABORTED:
        return "aborted";
      case REJECTED:
        return "rejected";
      case EXPIRED:
        return "expired";
      case PAUSED:
        return "paused";
      default:
        unhandled(status);
        return "failed";
    }
  }

  Map<String, String> getPlaceholderValues(ExecutionContext context, String userName, ExecutionStatus status) {
    Application app = Objects.requireNonNull(context.getApp());
    WorkflowExecution workflowExecution =
        workflowExecutionService.fetchWorkflowExecution(app.getUuid(), context.getWorkflowExecutionId(),
            WorkflowExecutionKeys.createdAt, WorkflowExecutionKeys.triggeredBy, WorkflowExecutionKeys.status);

    String statusMsg = getStatusMessage(status);
    long startTs = (status == PAUSED) ? workflowExecution.getCreatedAt() : context.getStateExecutionData().getStartTs();
    if (status == PAUSED) {
      userName = workflowExecution.getTriggeredBy().getName();
    }

    return notificationMessageResolver.getPlaceholderValues(context, userName, startTs, System.currentTimeMillis(),
        getTimeoutMillis().toString(), statusMsg, "", status, ApprovalNeeded);
  }

  private Map<String, String> getPlaceholderValues(ExecutionContext context, String timeout) {
    return notificationMessageResolver.getPlaceholderValues(
        context, "", 0, 0, timeout, "", "", EXPIRED, ApprovalNeeded);
  }

  private void populateApprovalAlert(ApprovalNeededAlert approvalNeededAlert, ExecutionContext context) {
    if (((ExecutionContextImpl) context).getStateExecutionInstance() != null
        && ((ExecutionContextImpl) context).getStateExecutionInstance().getExecutionType() != null) {
      approvalNeededAlert.setWorkflowType(
          ((ExecutionContextImpl) context).getStateExecutionInstance().getExecutionType());
    }

    if (((ExecutionContextImpl) context).getEnv() != null) {
      approvalNeededAlert.setEnvId(((ExecutionContextImpl) context).getEnv().getUuid());
    }

    if (approvalNeededAlert.getWorkflowType() == null) {
      throw new InvalidRequestException("Workflow type cannot be null");
    }

    WorkflowType workflowType = approvalNeededAlert.getWorkflowType();
    switch (workflowType) {
      case PIPELINE: {
        approvalNeededAlert.setPipelineExecutionId(context.getWorkflowExecutionId());
        break;
      }
      case ORCHESTRATION: {
        approvalNeededAlert.setWorkflowExecutionId(context.getWorkflowExecutionId());
        WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);

        if (workflowStandardParams != null && workflowStandardParams.getWorkflowElement() != null) {
          approvalNeededAlert.setPipelineExecutionId(
              workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid());
        }
        break;
      }
      default:
        unhandled(workflowType);
    }
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return approvalStateType == ApprovalStateType.SHELL_SCRIPT;
  }
}
