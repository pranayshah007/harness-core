/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.DISABLE_WINRM_COMMAND_ENCODING;
import static io.harness.beans.FeatureName.LOCAL_DELEGATE_CONFIG_OVERRIDE;
import static io.harness.beans.FeatureName.TIMEOUT_FAILURE_SUPPORT;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.ListUtils.trimStrings;
import static io.harness.delegate.beans.TaskData.DEFAULT_ASYNC_CALL_TIMEOUT;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;

import static software.wings.beans.delegation.ShellScriptParameters.CommandUnit;

import static com.google.common.collect.Lists.newArrayList;
import static java.lang.Boolean.FALSE;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.SweepingOutputInstance;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.encoding.EncodingUtils;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.delegate.task.TaskParameters;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.FailureType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.security.SimpleEncryption;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.serializer.KryoSerializer;
import io.harness.shell.AccessType;
import io.harness.shell.AuthenticationScheme;
import io.harness.shell.KerberosConfig;
import io.harness.shell.ScriptType;
import io.harness.shell.ShellExecutionData;
import io.harness.tasks.ResponseData;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.ScriptStateExecutionData;
import software.wings.beans.Activity.Type;
import software.wings.beans.AwsConfig;
import software.wings.beans.ConnectionType;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.SettingAttribute;
import software.wings.beans.TaskType;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.command.Command.Builder;
import software.wings.beans.command.CommandType;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.delegation.ShellScriptParameters;
import software.wings.beans.delegation.ShellScriptParameters.ShellScriptParametersBuilder;
import software.wings.beans.template.TemplateUtils;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.exception.ShellScriptException;
import software.wings.expression.ShellScriptEnvironmentVariables;
import software.wings.helpers.ext.container.ContainerDeploymentManagerHelper;
import software.wings.service.impl.ActivityHelperService;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.security.SSHVaultService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingValue;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionContextImpl;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.ExecutionResponse.ExecutionResponseBuilder;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateType;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.sm.states.mixin.SweepingOutputStateMixin;
import software.wings.stencils.DefaultValue;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Property;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@Slf4j
public class ShellScriptState extends State implements SweepingOutputStateMixin {
  @Inject @Transient private transient ActivityHelperService activityHelperService;
  @Inject @Transient private transient SettingsService settingsService;
  @Inject @Transient private transient SecretManager secretManager;
  @Inject @Transient private transient InfrastructureMappingService infrastructureMappingService;
  @Inject @Transient private ContainerDeploymentManagerHelper containerDeploymentManagerHelper;
  @Inject @Transient private SweepingOutputService sweepingOutputService;
  @Inject @Transient private TemplateUtils templateUtils;
  @Inject @Transient private ServiceTemplateService serviceTemplateService;
  @Inject @Transient private ServiceTemplateHelper serviceTemplateHelper;
  @Inject @Transient private TemplateExpressionProcessor templateExpressionProcessor;
  @Inject @Transient private DelegateService delegateService;
  @Inject @Transient private FeatureFlagService featureFlagService;
  @Transient @Inject KryoSerializer kryoSerializer;
  @Inject @Transient private SSHVaultService sshVaultService;
  @Inject @Transient private WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Getter @Setter @Attributes(title = "Execute on Delegate") private boolean executeOnDelegate;

  // Added to support delegate profile startup script execution through the workflow
  @Getter @Setter private String mustExecuteOnDelegateId;

  @NotEmpty @Getter @Setter @Attributes(title = "Target Host") private String host;
  // Please use delegateselectors instead, tags is not longer used but cannot be removed to support older workflows
  @NotEmpty @Getter @Setter @Attributes(title = "Tags") @Deprecated private List<String> tags;

  @NotEmpty @Getter @Setter @Attributes(title = "delegateSelectors") private List<String> delegateSelectors;

  @Override
  public KryoSerializer getKryoSerializer() {
    return kryoSerializer;
  }

  @NotEmpty
  @Getter
  @Setter
  @DefaultValue("SSH")
  @Attributes(title = "Connection Type")
  private ConnectionType connectionType;

  @NotEmpty @Getter @Setter @Attributes(title = "SSH Key") @Property("sshKeyRef") private String sshKeyRef;

  @NotEmpty @Getter @Setter @Attributes(title = "Connection Attributes") private String connectionAttributes;

  @Getter @Setter @Attributes(title = "Working Directory") private String commandPath;

  @NotEmpty @Getter @Setter @DefaultValue("BASH") @Attributes(title = "Script Type") private ScriptType scriptType;

  @NotEmpty @Getter @Setter @Attributes(title = "Script") private String scriptString;

  @Getter @Setter private String outputVars;
  @Getter @Setter private String secretOutputVars;
  @Getter @Setter private SweepingOutputInstance.Scope sweepingOutputScope;
  @Getter @Setter private String sweepingOutputName;
  @Getter @Setter private Boolean includeInfraSelectors;

  /**
   * Create a new Script State with given name.
   *
   * @param name name of the state.
   */
  public ShellScriptState(String name) {
    super(name, StateType.SHELL_SCRIPT.name());
  }

  @Attributes(title = "Timeout (ms)")
  @DefaultValue("" + DEFAULT_ASYNC_CALL_TIMEOUT)
  @Override
  public Integer getTimeoutMillis() {
    return super.getTimeoutMillis();
  }

  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    String activityId = createActivity(context);
    try {
      return executeInternal(context, activityId);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  @Override
  public void handleAbortEvent(ExecutionContext context) {
    // nothing to handle
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    ExecutionResponseBuilder executionResponseBuilder = ExecutionResponse.builder();
    String activityId = response.keySet().iterator().next();
    DelegateResponseData data = (DelegateResponseData) response.values().iterator().next();
    boolean saveSweepingOutputToContext = false;
    if (data instanceof CommandExecutionResult) {
      CommandExecutionResult commandExecutionResult = (CommandExecutionResult) data;

      switch (commandExecutionResult.getStatus()) {
        case SUCCESS:
          executionResponseBuilder.executionStatus(ExecutionStatus.SUCCESS);
          break;
        case FAILURE:
          executionResponseBuilder.executionStatus(ExecutionStatus.FAILED);
          if (featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, context.getAccountId())
              && commandExecutionResult.getCommandExecutionData() instanceof ShellExecutionData) {
            ShellExecutionData shellExecutionData =
                (ShellExecutionData) commandExecutionResult.getCommandExecutionData();
            if (shellExecutionData != null && shellExecutionData.isExpired()) {
              executionResponseBuilder.executionStatus(ExecutionStatus.EXPIRED);
              executionResponseBuilder.failureTypes(EnumSet.of(FailureType.EXPIRED, FailureType.TIMEOUT_ERROR));
            }
          }
          break;
        case RUNNING:
          executionResponseBuilder.executionStatus(ExecutionStatus.RUNNING);
          break;
        case QUEUED:
          executionResponseBuilder.executionStatus(ExecutionStatus.QUEUED);
          break;
        default:
          throw new ShellScriptException(
              "Unhandled type CommandExecutionStatus: " + commandExecutionResult.getStatus().name(),
              ErrorCode.SSH_CONNECTION_ERROR, Level.ERROR, WingsException.USER);
      }
      executionResponseBuilder.errorMessage(commandExecutionResult.getErrorMessage());

      ScriptStateExecutionData scriptStateExecutionData = (ScriptStateExecutionData) context.getStateExecutionData();
      if (commandExecutionResult.getStatus() == SUCCESS) {
        Map<String, String> sweepingOutputEnvVariables =
            ((ShellExecutionData) ((CommandExecutionResult) data).getCommandExecutionData())
                .getSweepingOutputEnvVariables();
        scriptStateExecutionData.setSweepingOutputEnvVariables(sweepingOutputEnvVariables);

        // also set the secret vars so that its displayed masked
        List<String> secretOutputVarsList = new ArrayList<>();
        if (secretOutputVars != null && StringUtils.isNotEmpty(secretOutputVars.trim())) {
          secretOutputVarsList = Arrays.asList(secretOutputVars.split("\\s*,\\s*"));
          secretOutputVarsList.replaceAll(String::trim);
        }

        scriptStateExecutionData.setSecretOutputVars(secretOutputVarsList);
        saveSweepingOutputToContext = true;
      }
      executionResponseBuilder.stateExecutionData(scriptStateExecutionData);
    } else if (data instanceof ErrorNotifyResponseData) {
      executionResponseBuilder.executionStatus(ExecutionStatus.FAILED);
      executionResponseBuilder.errorMessage(((ErrorNotifyResponseData) data).getErrorMessage());
      return executionResponseBuilder.build();
    } else {
      log.error("Unhandled DelegateResponseData class " + data.getClass().getCanonicalName(), new Exception(""));
    }

    ExecutionResponse executionResponse = executionResponseBuilder.build();

    updateActivityStatus(
        activityId, ((ExecutionContextImpl) context).getAppId(), executionResponse.getExecutionStatus());

    if (saveSweepingOutputToContext) {
      handleSweepingOutput(sweepingOutputService, context,
          buildSweepingOutput(((ShellExecutionData) ((CommandExecutionResult) data).getCommandExecutionData())
                                  .getSweepingOutputEnvVariables()));
    }

    return executionResponse;
  }

  @VisibleForTesting
  ShellScriptEnvironmentVariables buildSweepingOutput(Map<String, String> sweepingOutputEnvVariables) {
    SimpleEncryption encryption = new SimpleEncryption();

    if (isEmpty(sweepingOutputEnvVariables)) {
      return null;
    }
    List<String> outputVarsList = new ArrayList<>();
    if (outputVars != null && StringUtils.isNotEmpty(outputVars.trim())) {
      outputVarsList = Arrays.asList(outputVars.split("\\s*,\\s*"));
      outputVarsList.replaceAll(String::trim);
    }
    List<String> secretOutputVarsList = new ArrayList<>();
    if (secretOutputVars != null && StringUtils.isNotEmpty(secretOutputVars.trim())) {
      secretOutputVarsList = Arrays.asList(secretOutputVars.split("\\s*,\\s*"));
      secretOutputVarsList.replaceAll(String::trim);
    }
    List<String> finalOutputVarsList = outputVarsList;
    Map<String, String> envVarMap = sweepingOutputEnvVariables.entrySet()
                                        .stream()
                                        .filter(e -> finalOutputVarsList.contains(e.getKey()))
                                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    List<String> finalSecretOutputVarsList = secretOutputVarsList;
    Map<String, String> secretEnvVarMap = sweepingOutputEnvVariables.entrySet()
                                              .stream()
                                              .filter(e -> finalSecretOutputVarsList.contains(e.getKey()))
                                              .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    Map<String, String> encSecretMap = new HashMap<>();
    for (Map.Entry<String, String> entry : secretEnvVarMap.entrySet()) {
      encSecretMap.put(entry.getKey(),
          EncodingUtils.encodeBase64(encryption.encrypt(entry.getValue().getBytes(StandardCharsets.UTF_8))));
    }
    return new ShellScriptEnvironmentVariables(envVarMap, encSecretMap);
  }

  protected void updateActivityStatus(String activityId, String appId, ExecutionStatus status) {
    activityHelperService.updateStatus(activityId, appId, status);
  }

  private ExecutionResponse executeInternal(ExecutionContext context, String activityId) {
    ExecutionContextImpl executionContext = (ExecutionContextImpl) context;
    ScriptStateExecutionData scriptStateExecutionData =
        ScriptStateExecutionData.builder().activityId(activityId).build();
    scriptStateExecutionData.setTemplateVariable(
        templateUtils.processTemplateVariables(context, getTemplateVariables()));
    String infrastructureMappingId = context.fetchInfraMappingId();

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String envId = (workflowStandardParams == null
                       || workflowStandardParamsExtensionService.getEnv(workflowStandardParams) == null)
        ? null
        : workflowStandardParamsExtensionService.getEnv(workflowStandardParams).getUuid();

    String appId = workflowStandardParams == null ? null : workflowStandardParams.getAppId();

    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infrastructureMappingId);
    String serviceTemplateId =
        infrastructureMapping == null ? null : serviceTemplateHelper.fetchServiceTemplateId(infrastructureMapping);
    String serviceId = infrastructureMapping == null ? null : infrastructureMapping.getServiceId();
    String username = null;
    String keyPath = null;
    boolean keyless = false;
    Integer port = null;
    AccessType accessType = null;
    AuthenticationScheme authenticationScheme = null;
    String keyName = null;
    WinRmConnectionAttributes winRmConnectionAttributes = null;
    List<EncryptedDataDetail> winrmEdd = emptyList();
    List<EncryptedDataDetail> keyEncryptionDetails = emptyList();
    KerberosConfig kerberosConfig = null;
    SSHVaultConfig sshVaultConfig = null;
    boolean isVaultSSH = false;
    String role = null;
    String publicKey = null;

    HostConnectionAttributes hostConnectionAttributes = null;

    if (connectionType == null) {
      connectionType = ConnectionType.SSH;
    }

    if (scriptType == null) {
      scriptType = ScriptType.BASH;
    }

    if (!executeOnDelegate) {
      if (connectionType == ConnectionType.SSH) {
        if (!isEmpty(getTemplateExpressions())) {
          TemplateExpression sshConfigExp =
              templateExpressionProcessor.getTemplateExpression(getTemplateExpressions(), "sshKeyRef");
          if (sshConfigExp != null) {
            sshKeyRef = templateExpressionProcessor.resolveTemplateExpression(context, sshConfigExp);
          }
        }

        if (isEmpty(sshKeyRef)) {
          throw new ShellScriptException("Valid SSH Connection Attribute not provided in Shell Script Step",
              ErrorCode.SSH_CONNECTION_ERROR, Level.ERROR, WingsException.USER);
        }
        SettingAttribute keySettingAttribute = settingsService.get(sshKeyRef);
        if (keySettingAttribute == null) {
          keySettingAttribute =
              settingsService.getSettingAttributeByName(executionContext.getApp().getAccountId(), sshKeyRef);
        }

        if (keySettingAttribute == null) {
          throw new ShellScriptException("SSH Connection Attribute provided in Shell Script Step not found",
              ErrorCode.SSH_CONNECTION_ERROR, Level.ERROR, WingsException.USER);
        }
        hostConnectionAttributes = (HostConnectionAttributes) keySettingAttribute.getValue();
        username = ((HostConnectionAttributes) keySettingAttribute.getValue()).getUserName();
        keyPath = ((HostConnectionAttributes) keySettingAttribute.getValue()).getKeyPath();
        keyless = ((HostConnectionAttributes) keySettingAttribute.getValue()).isKeyless();
        port = ((HostConnectionAttributes) keySettingAttribute.getValue()).getSshPort();
        if (port == null) {
          port = 22;
        }
        accessType = ((HostConnectionAttributes) keySettingAttribute.getValue()).getAccessType();
        authenticationScheme = ((HostConnectionAttributes) keySettingAttribute.getValue()).getAuthenticationScheme();
        kerberosConfig = hostConnectionAttributes != null ? hostConnectionAttributes.getKerberosConfig() : null;
        keyName = keySettingAttribute.getUuid();
        keyEncryptionDetails = secretManager.getEncryptionDetails(
            (EncryptableSetting) keySettingAttribute.getValue(), context.getAppId(), context.getWorkflowExecutionId());
        isVaultSSH = ((HostConnectionAttributes) keySettingAttribute.getValue()).isVaultSSH();
        role = ((HostConnectionAttributes) keySettingAttribute.getValue()).getRole();
        publicKey = ((HostConnectionAttributes) keySettingAttribute.getValue()).getPublicKey();
        String sshVaultConfigId = ((HostConnectionAttributes) keySettingAttribute.getValue()).getSshVaultConfigId();
        sshVaultConfig = sshVaultService.getSSHVaultConfig(executionContext.getApp().getAccountId(), sshVaultConfigId);

      } else if (connectionType == ConnectionType.WINRM) {
        winRmConnectionAttributes = setupWinrmCredentials(connectionAttributes, context);
        username = winRmConnectionAttributes.getUsername();
        winrmEdd = secretManager.getEncryptionDetails(
            winRmConnectionAttributes, context.getAppId(), context.getWorkflowExecutionId());
      }
    }

    ContainerServiceParams containerServiceParams = null;
    if (infrastructureMappingId != null) {
      InfrastructureMapping infraMapping =
          infrastructureMappingService.get(context.getAppId(), infrastructureMappingId);
      if (infraMapping instanceof ContainerInfrastructureMapping) {
        containerServiceParams = containerDeploymentManagerHelper.getContainerServiceParams(
            (ContainerInfrastructureMapping) infraMapping, "", context);
      }
    }

    if (StringUtils.isEmpty(commandPath)) {
      if (scriptType == ScriptType.BASH) {
        commandPath = "/tmp";
      } else if (scriptType == ScriptType.POWERSHELL) {
        commandPath = "%TEMP%";
        if (executeOnDelegate) {
          commandPath = "/tmp";
        }
      }
    }

    List<String> allTags = newArrayList();
    List<String> renderedTags = newArrayList();

    if (!Objects.equals(includeInfraSelectors, FALSE)) {
      String cloudProviderTag = getTagFromCloudProvider(containerServiceParams);
      if (isNotEmpty(cloudProviderTag)) {
        allTags.add(cloudProviderTag);
      }
    }

    if (isNotEmpty(delegateSelectors)) {
      allTags.addAll(delegateSelectors);
    }

    if (isNotEmpty(tags)) {
      allTags.addAll(tags);
    }
    if (isNotEmpty(allTags)) {
      for (String tag : allTags) {
        renderedTags.add(context.renderExpression(tag));
      }
      renderedTags = trimStrings(renderedTags);
    }

    ShellScriptParametersBuilder shellScriptParameters =
        ShellScriptParameters.builder()
            .accountId(executionContext.getApp().getAccountId())
            .appId(executionContext.getAppId())
            .activityId(activityId)
            .host(context.renderExpression(host))
            .connectionType(connectionType)
            .winrmConnectionAttributes(winRmConnectionAttributes)
            .winrmConnectionEncryptedDataDetails(winrmEdd)
            .userName(username)
            .keyEncryptedDataDetails(keyEncryptionDetails)
            .containerServiceParams(containerServiceParams)
            .workingDirectory(context.renderExpression(commandPath))
            .scriptType(scriptType)
            .script(scriptString)
            .executeOnDelegate(executeOnDelegate)
            .outputVars(outputVars)
            .secretOutputVars(secretOutputVars)
            .hostConnectionAttributes(hostConnectionAttributes)
            .keyless(keyless)
            .keyPath(keyPath)
            .port(port)
            .accessType(accessType)
            .authenticationScheme(authenticationScheme)
            .kerberosConfig(kerberosConfig)
            .sshVaultConfig(sshVaultConfig)
            .role(role)
            .includeInfraSelectors(includeInfraSelectors)
            .publicKey(publicKey)
            .isVaultSSH(isVaultSSH)
            .localOverrideFeatureFlag(
                featureFlagService.isEnabled(LOCAL_DELEGATE_CONFIG_OVERRIDE, executionContext.getApp().getAccountId()))
            .keyName(keyName)
            .disableWinRMCommandEncodingFFSet(
                featureFlagService.isEnabled(DISABLE_WINRM_COMMAND_ENCODING, executionContext.getApp().getAccountId()))
            .disableWinRMEnvVariables(featureFlagService.isNotEnabled(
                FeatureName.ENABLE_WINRM_ENV_VARIABLES, executionContext.getApp().getAccountId()))
            .saveExecutionLogs(true)
            .enableJSchLogs(isJSchLogsEnabledPerAccount(executionContext.getApp().getAccountId()));

    if (featureFlagService.isEnabled(TIMEOUT_FAILURE_SUPPORT, context.getAccountId())) {
      shellScriptParameters.sshTimeOut(getTimeoutMillis());
    }

    Map<String, String> serviceVariables = context.getServiceVariables().entrySet().stream().collect(
        Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    Map<String, String> safeDisplayServiceVariables = context.getSafeDisplayServiceVariables();

    serviceVariables.replaceAll((name, value) -> context.renderExpression(value));

    if (safeDisplayServiceVariables != null) {
      safeDisplayServiceVariables.replaceAll((name, value) -> context.renderExpression(value));
    }
    shellScriptParameters.serviceVariables(serviceVariables).safeDisplayServiceVariables(safeDisplayServiceVariables);
    ShellScriptParameters taskParams = shellScriptParameters.build();
    log.info(
        "Shell script task parameters: accountId - {}, appId - {}, workingDir - {}, activityId - {} & commandPath - {}",
        taskParams.getAccountId(), taskParams.getAppId(), taskParams.getWorkingDirectory(), taskParams.getActivityId(),
        commandPath);

    int expressionFunctorToken = HashGenerator.generateIntegerHash();

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(executionContext.getApp().getAccountId())
            .description("Shell Script Execution")
            .waitId(activityId)
            .tags(renderedTags)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, context.getApp().getAppId())
            .workflowExecutionId(context.getWorkflowExecutionId())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.SCRIPT.name())
                      .parameters(new Object[] {taskParams})
                      .timeout(defaultIfNullTimeout(DEFAULT_ASYNC_CALL_TIMEOUT))
                      .expressionFunctorToken(expressionFunctorToken)
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, context.getEnvType())

            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMappingId)
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, serviceId)
            .setupAbstraction(Cd1SetupFields.SERVICE_TEMPLATE_ID_FIELD, serviceTemplateId)
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();

    String delegateTaskId = renderAndScheduleDelegateTask(context, delegateTask,
        StateExecutionContext.builder()
            .stateExecutionData(scriptStateExecutionData)
            .scriptType(scriptType)
            .adoptDelegateDecryption(true)
            .expressionFunctorToken(expressionFunctorToken)
            .build());

    appendDelegateTaskDetails(context, delegateTask);

    return ExecutionResponse.builder()
        .async(true)
        .stateExecutionData(scriptStateExecutionData)
        .correlationIds(singletonList(activityId))
        .delegateTaskId(delegateTaskId)
        .build();
  }

  String getTagFromCloudProvider(ContainerServiceParams containerServiceParams) {
    if (containerServiceParams != null) {
      SettingAttribute settingAttribute = containerServiceParams.getSettingAttribute();
      SettingValue settingValue = settingAttribute.getValue();
      if (settingValue instanceof AwsConfig) {
        return ((AwsConfig) settingValue).getTag();
      }
    }
    return null;
  }

  private String createActivity(ExecutionContext executionContext) {
    List<CommandUnit> commandUnits =
        singletonList(Builder.aCommand().withName(CommandUnit).withCommandType(CommandType.OTHER).build());
    return activityHelperService
        .createAndSaveActivity(executionContext, Type.Verification, getName(), getStateType(), commandUnits)
        .getUuid();
  }

  protected String renderAndScheduleDelegateTask(
      ExecutionContext context, DelegateTask task, StateExecutionContext stateExecutionContext) {
    context.resetPreparedCache();
    if (task.getData().getParameters().length == 1 && task.getData().getParameters()[0] instanceof TaskParameters) {
      task.setWorkflowExecutionId(context.getWorkflowExecutionId());
      ExpressionReflectionUtils.applyExpression(task.getData().getParameters()[0],
          (secretMode, value) -> context.renderExpression(value, stateExecutionContext));
    }

    return delegateService.queueTask(task);
  }

  @Override
  @SchemaIgnore
  public List<String> getPatternsForRequiredContextElementType() {
    List<String> patterns = new LinkedList<>();
    if (templateVariables != null) {
      patterns =
          templateVariables.stream().map(Variable::getValue).filter(Objects::nonNull).collect(Collectors.toList());
    }
    patterns.add(scriptString);
    patterns.add(host);
    return patterns;
  }

  public WinRmConnectionAttributes setupWinrmCredentials(String connectionAttributes, ExecutionContext context) {
    WinRmConnectionAttributes winRmConnectionAttributes = null;

    if (!isEmpty(getTemplateExpressions())) {
      TemplateExpression winRmConfigExp =
          templateExpressionProcessor.getTemplateExpression(getTemplateExpressions(), "connectionAttributes");
      if (winRmConfigExp != null) {
        connectionAttributes = templateExpressionProcessor.resolveTemplateExpression(context, winRmConfigExp);
      }
    }
    if (isEmpty(connectionAttributes)) {
      throw new ShellScriptException("WinRM Connection Attribute not provided in Shell Script Step",
          ErrorCode.SSH_CONNECTION_ERROR, Level.ERROR, WingsException.USER);
    }

    SettingAttribute keySettingAttribute = settingsService.get(connectionAttributes);
    if (keySettingAttribute == null) {
      keySettingAttribute =
          settingsService.getSettingAttributeByName(context.getApp().getAccountId(), connectionAttributes);
    }

    winRmConnectionAttributes = (WinRmConnectionAttributes) keySettingAttribute.getValue();

    if (winRmConnectionAttributes == null) {
      throw new ShellScriptException("WinRM Connection Attribute provided in Shell Script Step not found",
          ErrorCode.SSH_CONNECTION_ERROR, Level.ERROR, WingsException.USER);
    }
    return winRmConnectionAttributes;
  }

  private boolean isJSchLogsEnabledPerAccount(final String accountId) {
    return featureFlagService.isEnabled(FeatureName.SSH_JSCH_LOGS, accountId);
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }
}
