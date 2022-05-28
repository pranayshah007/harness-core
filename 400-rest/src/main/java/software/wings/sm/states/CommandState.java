/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.COMMAND_DOES_NOT_EXIST;
import static io.harness.eraro.ErrorCode.SSH_CONNECTION_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.api.CommandStateExecutionData.Builder.aCommandStateExecutionData;
import static software.wings.beans.command.Command.Builder.aCommand;
import static software.wings.beans.command.ServiceCommand.Builder.aServiceCommand;
import static software.wings.service.impl.artifact.ArtifactServiceImpl.metadataOnlyBehindFlag;
import static software.wings.sm.StateMachineExecutor.DEFAULT_STATE_TIMEOUT_MILLIS;
import static software.wings.sm.StateType.COMMAND;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.joor.Reflect.on;

import io.harness.annotations.dev.BreakDependencyOn;
import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.Cd1SetupFields;
import io.harness.beans.DelegateTask;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.context.ContextElementType;
import io.harness.data.algorithm.HashGenerator;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.command.CommandExecutionResult;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.expression.ExpressionReflectionUtils;
import io.harness.ff.FeatureFlagService;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.shell.ScriptType;
import io.harness.tasks.ResponseData;

import software.wings.annotation.EncryptableSetting;
import software.wings.api.CommandStateExecutionData;
import software.wings.api.DeploymentType;
import software.wings.api.InstanceElement;
import software.wings.api.SimpleWorkflowParam;
import software.wings.beans.Activity;
import software.wings.beans.Activity.Type;
import software.wings.beans.Application;
import software.wings.beans.CountsByStatuses;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.EntityType;
import software.wings.beans.HostConnectionAttributes;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.SSHVaultConfig;
import software.wings.beans.Service;
import software.wings.beans.ServiceInstance;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.SettingAttribute;
import software.wings.beans.StringValue;
import software.wings.beans.TaskType;
import software.wings.beans.TemplateExpression;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.WinRmConnectionAttributes;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.command.CleanupPowerShellCommandUnit;
import software.wings.beans.command.CleanupSshCommandUnit;
import software.wings.beans.command.Command;
import software.wings.beans.command.CommandMapper;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.CommandUnitType;
import software.wings.beans.command.ExecCommandUnit;
import software.wings.beans.command.InitPowerShellCommandUnit;
import software.wings.beans.command.InitSshCommandUnit;
import software.wings.beans.command.InitSshCommandUnitV2;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.command.TailFilePatternEntry;
import software.wings.beans.delegation.CommandParameters;
import software.wings.beans.delegation.CommandParameters.CommandParametersBuilder;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.template.ReferencedTemplate;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateUtils;
import software.wings.beans.template.command.SshCommandTemplate;
import software.wings.common.TemplateExpressionProcessor;
import software.wings.delegatetasks.aws.AwsCommandHelper;
import software.wings.exception.ShellScriptException;
import software.wings.service.impl.ActivityHelperService;
import software.wings.service.impl.servicetemplates.ServiceTemplateHelper;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceInstanceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.security.SSHVaultService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.template.TemplateService;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.ExecutionResponse;
import software.wings.sm.State;
import software.wings.sm.StateExecutionContext;
import software.wings.sm.StateExecutionContext.StateExecutionContextBuilder;
import software.wings.sm.WorkflowStandardParams;
import software.wings.sm.WorkflowStandardParamsExtensionService;
import software.wings.stencils.Expand;
import software.wings.utils.MappingUtils;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.annotations.Transient;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
@BreakDependencyOn("software.wings.service.intfc.DelegateService")
public class CommandState extends State {
  public static final String RUNTIME_PATH = "RUNTIME_PATH";
  public static final String BACKUP_PATH = "BACKUP_PATH";
  public static final String STAGING_PATH = "STAGING_PATH";
  public static final String WINDOWS_RUNTIME_PATH = "WINDOWS_RUNTIME_PATH";

  @Inject @Transient @SchemaIgnore private transient ExecutorService executorService;
  @Inject @Transient private transient ActivityHelperService activityHelperService;
  @Inject @Transient private transient ActivityService activityService;
  @Inject @Transient private transient AppService appService;
  @Inject @Transient private transient ArtifactService artifactService;
  @Inject @Transient private transient ArtifactStreamService artifactStreamService;
  @Inject @Transient private transient DelegateService delegateService;
  @Inject @Transient private transient HostService hostService;
  @Inject @Transient private transient InfrastructureMappingService infrastructureMappingService;
  @Inject @Transient private transient SecretManager secretManager;
  @Inject @Transient private transient ServiceInstanceService serviceInstanceService;
  @Inject @Transient private transient ServiceResourceService serviceResourceService;
  @Inject @Transient private transient ServiceTemplateService serviceTemplateService;
  @Inject @Transient private transient SettingsService settingsService;
  @Inject @Transient private transient WorkflowExecutionService workflowExecutionService;
  @Inject @Transient private transient FeatureFlagService featureFlagService;
  @Inject @Transient private transient AwsCommandHelper awsCommandHelper;
  @Inject @Transient private transient TemplateService templateService;
  @Inject @Transient private transient TemplateUtils templateUtils;
  @Inject @Transient private transient ServiceTemplateHelper serviceTemplateHelper;
  @Inject @Transient private transient TemplateExpressionProcessor templateExpressionProcessor;
  @Inject @Transient private transient SSHVaultService sshVaultService;
  @Inject @Transient private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  @Attributes(title = "Command") @Expand(dataProvider = CommandStateEnumDataProvider.class) private String commandName;

  @NotEmpty @SchemaIgnore private String sshKeyRef;
  @NotEmpty @SchemaIgnore private String connectionAttributes;

  @SchemaIgnore private boolean executeOnDelegate;

  @NotEmpty @Getter @Setter @Attributes(title = "selectors") private List<String> delegateSelectors;

  @NotEmpty @SchemaIgnore private String host;

  public enum ConnectionType { SSH, WINRM }

  @NotEmpty @SchemaIgnore private ConnectionType connectionType;

  @SchemaIgnore private static String artifactFileName;

  @SchemaIgnore
  public String getSshKeyRef() {
    return sshKeyRef;
  }

  public void setSshKeyRef(String sshKeyRef) {
    this.sshKeyRef = sshKeyRef;
  }

  @SchemaIgnore
  public String getConnectionAttributes() {
    return connectionAttributes;
  }

  public void setConnectionAttributes(String connectionAttributes) {
    this.connectionAttributes = connectionAttributes;
  }

  @SchemaIgnore
  public boolean isExecuteOnDelegate() {
    return executeOnDelegate;
  }

  public void setExecuteOnDelegate(boolean executeOnDelegate) {
    this.executeOnDelegate = executeOnDelegate;
  }

  @SchemaIgnore
  public String getHost() {
    return host;
  }

  public void setHost(String host) {
    this.host = host;
  }

  @SchemaIgnore
  public ConnectionType getConnectionType() {
    return connectionType;
  }

  public void setConnectionType(ConnectionType connectionType) {
    this.connectionType = connectionType;
  }

  /**
   * Instantiates a new Command state.
   *
   * @param name        the name
   * @param commandName the command name
   */
  public CommandState(String name, String commandName) {
    super(name, COMMAND.name());
    this.commandName = commandName;
  }

  /**
   * Instantiates a new Command state.
   *
   * @param name the name
   */
  public CommandState(String name) {
    super(name, COMMAND.name());
  }

  /**
   * Gets command name.
   *
   * @return the command name
   */
  public String getCommandName() {
    return commandName;
  }

  /**
   * Sets command name.
   *
   * @param commandName the command name
   */
  public void setCommandName(String commandName) {
    this.commandName = commandName;
  }

  @Attributes(title = "Timeout (ms)")
  @Override
  public Integer getTimeoutMillis() {
    Integer timeout = super.getTimeoutMillis();
    return timeout == null ? DEFAULT_STATE_TIMEOUT_MILLIS : timeout;
  }

  // Entry function for execution of Command State (for both Service linked and Template library linked)
  @Override
  public ExecutionResponse execute(ExecutionContext context) {
    try {
      return executeInternal(context);
    } catch (WingsException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(ex), ex);
    }
  }

  public ExecutionResponse executeInternal(ExecutionContext context) {
    CommandStateExecutionData.Builder executionDataBuilder = aCommandStateExecutionData();
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();
    String envId = workflowStandardParams.getEnvId();
    String activityId = null;

    Host host = null;
    InstanceElement instanceElement = context.getContextElement(ContextElementType.INSTANCE);
    ServiceTemplate serviceTemplate = null;
    String infrastructureMappingId = context.fetchInfraMappingId();
    InfrastructureMapping infrastructureMapping = null;
    Service service = null;
    ServiceInstance serviceInstance = null;
    DeploymentType deploymentType = null;
    SSHVaultConfig sshVaultConfig = null;

    if (instanceElement != null) {
      String serviceTemplateId = instanceElement.getServiceTemplateElement().getUuid();
      serviceTemplate = serviceTemplateService.get(appId, serviceTemplateId);
    }

    if (infrastructureMappingId != null) {
      infrastructureMapping = infrastructureMappingService.get(appId, infrastructureMappingId);
      if (infrastructureMapping != null) {
        service = serviceResourceService.getWithDetails(appId, infrastructureMapping.getServiceId());
        if (service != null && getTemplateUuid() == null) {
          deploymentType = serviceResourceService.getDeploymentType(infrastructureMapping, service, null);
        }
        if (instanceElement == null) {
          serviceTemplate = serviceTemplateHelper.fetchServiceTemplate(infrastructureMapping);
        }
      }
    }

    // if execute on delegate then no need for host resolution else we need to
    if (executeOnDelegate) {
      deploymentType = DeploymentType.SSH;
      executionDataBuilder.withServiceId(service != null ? service.getUuid() : null)
          .withServiceName(service != null ? service.getName() : null)
          .withTemplateId(serviceTemplate != null ? serviceTemplate.getUuid() : null)
          .withTemplateName(instanceElement != null ? instanceElement.getServiceTemplateElement().getName() : null)
          .withAppId(appId);
    } else {
      // If the service command is linked via service then we get the host using Select nodes step as before. Else if
      // the service command is linked via Template library then we build the host using connection details received
      // through the UI
      if (getHost() == null) {
        if (getTemplateUuid() == null) {
          // get host details using service instance details
          if (instanceElement != null) {
            serviceInstance = serviceInstanceService.get(appId, envId, instanceElement.getUuid());
            if (serviceInstance != null) {
              host = hostService.getHostByEnv(appId, envId, serviceInstance.getHostId());
              if (host == null) {
                throw new ShellScriptException("Host cannot be empty", SSH_CONNECTION_ERROR, Level.ERROR, USER);
              }
            } else {
              throw new InvalidRequestException("Unable to find service instance", USER);
            }
          } else {
            throw new InvalidRequestException("Unable to find instance element from context", USER);
          }
        } else {
          // If execute on delegate is false, host is not given and service command is linked via template library,
          // throw Exception
          throw new ShellScriptException("Host cannot be empty", SSH_CONNECTION_ERROR, Level.ERROR, USER);
        }
      } else {
        // host can contain either ${instance.name} or some valid host name/ip
        // Deployment type is taken as SSH
        if (connectionType == null || connectionType == ConnectionType.SSH) {
          deploymentType = DeploymentType.SSH;

          if (!isEmpty(getTemplateExpressions())) {
            TemplateExpression sshConfigExp =
                templateExpressionProcessor.getTemplateExpression(getTemplateExpressions(), "sshKeyRef");
            if (sshConfigExp != null) {
              sshKeyRef = templateExpressionProcessor.resolveTemplateExpression(context, sshConfigExp);
            }
          }
          if (isEmpty(sshKeyRef)) {
            throw new ShellScriptException("SSH Connection Attribute not provided in Command Step",
                ErrorCode.SSH_CONNECTION_ERROR, Level.ERROR, WingsException.USER);
          }
          SettingAttribute keySettingAttribute = settingsService.get(sshKeyRef);
          if (keySettingAttribute == null) {
            keySettingAttribute = settingsService.getSettingAttributeByName(context.getApp().getAccountId(), sshKeyRef);
            if (keySettingAttribute == null) {
              throw new ShellScriptException("SSH Connection Attribute provided in Shell Script Step not found",
                  ErrorCode.SSH_CONNECTION_ERROR, Level.ERROR, WingsException.USER);
            }
            sshKeyRef = keySettingAttribute.getUuid();
          }
          if (keySettingAttribute.getValue() instanceof HostConnectionAttributes
              && ((HostConnectionAttributes) keySettingAttribute.getValue()).isVaultSSH()) {
            sshVaultConfig = sshVaultService.getSSHVaultConfig(keySettingAttribute.getAccountId(),
                ((HostConnectionAttributes) keySettingAttribute.getValue()).getSshVaultConfigId());
          }

          String hostName = context.renderExpression(getHost());
          host = Host.Builder.aHost()
                     .withHostName(hostName)
                     .withPublicDns(hostName)
                     .withAppId(appId)
                     .withHostConnAttr(sshKeyRef)
                     .build();
          executionDataBuilder.withServiceId(service != null ? service.getUuid() : null)
              .withTemplateId(serviceTemplate != null ? serviceTemplate.getUuid() : null)
              .withTemplateName(instanceElement != null ? instanceElement.getServiceTemplateElement().getName() : null)
              .withHostName(hostName)
              .withPublicDns(hostName)
              .withAppId(appId);

        } else if (connectionType == ConnectionType.WINRM) {
          deploymentType = DeploymentType.WINRM;
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
            notNullCheck("Winrm Connection Attribute provided in Shell Script Step not found", keySettingAttribute);
            connectionAttributes = keySettingAttribute.getUuid();
          }

          WinRmConnectionAttributes winRmConnectionAttributes =
              (WinRmConnectionAttributes) keySettingAttribute.getValue();

          if (winRmConnectionAttributes == null) {
            throw new ShellScriptException("Winrm Connection Attribute provided in Shell Script Step not found",
                ErrorCode.SSH_CONNECTION_ERROR, Level.ERROR, WingsException.USER);
          }

          String hostName = context.renderExpression(getHost());
          host = Host.Builder.aHost()
                     .withHostName(hostName)
                     .withPublicDns(hostName)
                     .withAppId(appId)
                     .withWinrmConnAttr(connectionAttributes)
                     .build();

          executionDataBuilder.withServiceId(service != null ? service.getUuid() : null)
              .withTemplateId(serviceTemplate != null ? serviceTemplate.getUuid() : null)
              .withTemplateName(instanceElement != null ? instanceElement.getServiceTemplateElement().getName() : null)
              .withHostName(hostName)
              .withPublicDns(hostName)
              .withAppId(appId);
        }
      }
    }

    updateWorkflowExecutionStatsInProgress(context);

    // need command.setGraph = null?

    int expressionFunctorToken = HashGenerator.generateIntegerHash();

    String delegateTaskId;
    try {
      Application application = appService.get(appId);
      String accountId = application.getAccountId();
      Artifact artifact = null;
      Map<String, Artifact> multiArtifacts = null;

      Command command = null;

      // Get the command object for both types of service commands
      command = extractCommand(context, executionDataBuilder, appId, envId, service);

      if (command.isArtifactNeeded()) {
        if (service == null) {
          throw new ShellScriptException("Linked command needs artifact but service is not found", null, null, USER);
        }
        if (!featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
          artifact = findArtifact(service.getUuid(), context);
        } else {
          multiArtifacts = findArtifacts(service.getUuid(), context);
        }
      }

      Map<String, String> serviceVariables = context.getServiceVariables().entrySet().stream().collect(
          Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
      Map<String, String> safeDisplayServiceVariables = context.getSafeDisplayServiceVariables();

      serviceVariables.replaceAll((name, value) -> context.renderExpression(value));

      if (safeDisplayServiceVariables != null) {
        safeDisplayServiceVariables.replaceAll((name, value) -> context.renderExpression(value));
      }

      String backupPath = getEvaluatedSettingValue(context, accountId, appId, envId, BACKUP_PATH);
      String runtimePath = getEvaluatedSettingValue(context, accountId, appId, envId, RUNTIME_PATH);
      String stagingPath = getEvaluatedSettingValue(context, accountId, appId, envId, STAGING_PATH);
      String windowsRuntimePath = getEvaluatedSettingValue(context, accountId, appId, envId, WINDOWS_RUNTIME_PATH);

      CommandParametersBuilder commandParametersBuilder =
          CommandParameters.builderWithCommand()
              .appId(appId)
              .envId(envId)
              .backupPath(backupPath)
              .runtimePath(runtimePath)
              .stagingPath(stagingPath)
              .windowsRuntimePath(windowsRuntimePath)
              .executionCredential(workflowStandardParams.getExecutionCredential())
              .serviceVariables(serviceVariables)
              .safeDisplayServiceVariables(safeDisplayServiceVariables)
              .serviceTemplateId(serviceTemplate != null ? serviceTemplate.getUuid() : null)
              .appContainer(service != null ? service.getAppContainer() : null)
              .host(host)
              .accountId(accountId)
              .timeout(getTimeoutMillis())
              .executeOnDelegate(executeOnDelegate)
              .deploymentType(deploymentType != null ? deploymentType.name() : null)
              .delegateSelectors(getDelegateSelectors(context))
              .disableWinRMEnvVariables(
                  featureFlagService.isNotEnabled(FeatureName.ENABLE_WINRM_ENV_VARIABLES, accountId))
              .sshVaultConfig(sshVaultConfig);

      if (host != null) {
        getHostConnectionDetails(context, host, commandParametersBuilder);
      }

      // handle both types of service commands
      processTemplateVariables(context, executionDataBuilder, appId, envId, instanceElement, service, accountId,
          artifact, command, expressionFunctorToken);

      Map<String, String> flattenedTemplateVariables = new HashMap<>();
      flattenTemplateVariables(command, flattenedTemplateVariables);
      addArtifactTemplateVariablesToContext(flattenedTemplateVariables, multiArtifacts, context);

      // get artifact details for both types of artifacts
      fetchArtifactDetails(context, executionDataBuilder, service, accountId, artifact, multiArtifacts, command,
          commandParametersBuilder);

      List<CommandUnit> flattenCommandUnits;
      if (getTemplateUuid() != null) {
        flattenCommandUnits =
            getFlattenCommandUnits(appId, envId, service, deploymentType.name(), accountId, command, true);
        executionDataBuilder.withTemplateVariable(
            templateUtils.processTemplateVariables(context, command.getTemplateVariables()));
      } else {
        flattenCommandUnits =
            getFlattenCommandUnits(appId, envId, service, deploymentType.name(), accountId, command, false);
      }

      // need multiartifact check??
      Activity activity = activityHelperService.createAndSaveActivity(
          context, Type.Command, command.getName(), command.getCommandUnitType().name(), flattenCommandUnits, artifact);
      activityId = activity.getUuid();
      executionDataBuilder.withActivityId(activityId);

      setPropertiesFromFeatureFlags(accountId, commandParametersBuilder);

      CommandParameters commandParameters = commandParametersBuilder.activityId(activityId)
                                                .deploymentType(deploymentType.name())
                                                .command(CommandMapper.toCommandDTO(command))
                                                .build();

      delegateTaskId = queueDelegateTask(
          activityId, envId, infrastructureMappingId, accountId, commandParameters, context, expressionFunctorToken);
      log.info("DelegateTaskId [{}] sent for activityId [{}]", delegateTaskId, activityId);

    } catch (WingsException ex) {
      return handleException(context, executionDataBuilder, activityId, appId, ex);
    }
    return getExecutionResponse(executionDataBuilder, activityId, delegateTaskId);
  }

  private void fetchArtifactDetails(ExecutionContext context, CommandStateExecutionData.Builder executionDataBuilder,
      Service service, String accountId, Artifact artifact, Map<String, Artifact> multiArtifacts, Command command,
      CommandParametersBuilder commandParametersBuilder) {
    if (!featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId)) {
      if (artifact != null) {
        getArtifactDetails(context, executionDataBuilder, service, accountId, artifact, commandParametersBuilder);
      } else if (command.isArtifactNeeded()) {
        throw new ShellScriptException(
            format("Unable to find artifact for service %s", service.getName()), null, null, WingsException.USER);
      }
    } else {
      if (isNotEmpty(multiArtifacts)) {
        getMultiArtifactDetails(
            context, executionDataBuilder, service, accountId, multiArtifacts, commandParametersBuilder);
      } else if (command.isArtifactNeeded()) {
        throw new ShellScriptException(
            format("Unable to find artifact for service %s", service.getName()), null, null, WingsException.USER);
      }
    }
  }

  private void processTemplateVariables(ExecutionContext context,
      CommandStateExecutionData.Builder executionDataBuilder, String appId, String envId,
      InstanceElement instanceElement, Service service, String accountId, Artifact artifact, Command command,
      int expressionFunctorToken) {
    context.resetPreparedCache();
    if (getTemplateUuid() != null) {
      String templateId = getTemplateUuid();
      String templateVersion = getTemplateVersion();
      expandCommand(command, templateId, templateVersion, command.getName());
      Template template = templateService.get(templateId, templateVersion);
      if (template != null) {
        SshCommandTemplate sshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
        resolveTemplateVariablesInLinkedCommands(
            command, sshCommandTemplate.getReferencedTemplateList(), getTemplateVariables());
        executionDataBuilder.withTemplateVariable(
            templateUtils.processTemplateVariables(context, getTemplateVariables()));
        renderCommandString(command, context, executionDataBuilder.build(), expressionFunctorToken);
      }
    } else {
      if (instanceElement != null) {
        ServiceInstance serviceInstance = serviceInstanceService.get(appId, envId, instanceElement.getUuid());
        expandCommand(serviceInstance, command, service.getUuid(), envId);
      }
      executionDataBuilder.withTemplateVariable(
          templateUtils.processTemplateVariables(context, command.getTemplateVariables()));
      // remove artifact refactor
      renderCommandString(command, context, executionDataBuilder.build(), artifact, expressionFunctorToken);
    }
    context.resetPreparedCache();
  }

  private Command extractCommand(ExecutionContext context, CommandStateExecutionData.Builder executionDataBuilder,
      String appId, String envId, Service service) {
    Command command;
    if (getTemplateUuid() != null) {
      command = getCommandFromTemplate(getTemplateUuid(), getTemplateVersion());
      executionDataBuilder.withCommandName(command.getName());
    } else {
      String actualCommand = context.renderExpression(commandName);

      if (service == null) {
        throw new ShellScriptException(
            format("Command %s is linked via service but service is not found", actualCommand), null, null, USER);
      }
      ServiceCommand serviceCommand =
          serviceResourceService.getCommandByName(appId, service.getUuid(), envId, actualCommand);
      if (serviceCommand == null || serviceCommand.getCommand() == null) {
        throw new ShellScriptException(
            format("Unable to find Command %s for Service %s", actualCommand, service.getName()), null, null,
            WingsException.USER);
      }
      command = serviceCommand.getCommand();
      command.setTemplateId(serviceCommand.getTemplateUuid());
    }
    return command;
  }

  private ExecutionResponse getExecutionResponse(
      CommandStateExecutionData.Builder executionDataBuilder, String activityId, String delegateTaskId) {
    return ExecutionResponse.builder()
        .async(true)
        .correlationIds(Collections.singletonList(activityId))
        .stateExecutionData(executionDataBuilder.withDelegateTaskId(delegateTaskId).build())
        .delegateTaskId(delegateTaskId)
        .build();
  }

  private void addArtifactTemplateVariablesToContext(
      Map<String, String> artifactTemplateVariableMap, Map<String, Artifact> multiArtifacts, ExecutionContext context) {
    if (artifactTemplateVariableMap.size() > 0) {
      for (Entry<String, String> entry : artifactTemplateVariableMap.entrySet()) {
        String param = entry.getKey();
        String paramValue = entry.getValue();
        String expression = templateUtils.getExpression(paramValue);
        Artifact evaluatedValue = (Artifact) context.evaluateExpression(expression);
        if (evaluatedValue != null) {
          multiArtifacts.put(param, evaluatedValue);
        }
      }
    }
  }

  private void flattenTemplateVariables(Command command, Map<String, String> artifactTemplateVariables) {
    if (isNotEmpty(command.getTemplateVariables())) {
      for (Variable var : command.getTemplateVariables()) {
        if (var.getType() == VariableType.ARTIFACT) {
          artifactTemplateVariables.put(var.getName(), var.getValue());
        }
      }
      if (isNotEmpty(command.getCommandUnits())) {
        for (CommandUnit cu : command.getCommandUnits()) {
          if (cu instanceof Command) {
            flattenTemplateVariables((Command) cu, artifactTemplateVariables);
          }
        }
      }
    }
  }

  private String queueDelegateTask(String activityId, String envId, String infrastructureMappingId, String accountId,
      CommandParameters commandParameters, ExecutionContext context, int expressionFunctorToken) {
    String appId = context.getAppId();
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infrastructureMappingId);
    String serviceId = infrastructureMapping == null ? null : infrastructureMapping.getServiceId();
    String delegateTaskId;

    DelegateTask delegateTask =
        DelegateTask.builder()
            .accountId(accountId)
            .setupAbstraction(Cd1SetupFields.APP_ID_FIELD, appId)
            .description("Command state Execution")
            .waitId(activityId)
            .tags(awsCommandHelper.getAwsConfigTagsFromContext(commandParameters))
            .workflowExecutionId(context.getWorkflowExecutionId())
            .data(TaskData.builder()
                      .async(true)
                      .taskType(TaskType.COMMAND.name())
                      .parameters(new Object[] {commandParameters})
                      .timeout(defaultIfNullTimeout(TimeUnit.HOURS.toMillis(4)))
                      .expressionFunctorToken(expressionFunctorToken)
                      .build())
            .setupAbstraction(Cd1SetupFields.ENV_ID_FIELD, envId)
            .setupAbstraction(Cd1SetupFields.ENV_TYPE_FIELD, context.getEnvType())

            .setupAbstraction(Cd1SetupFields.INFRASTRUCTURE_MAPPING_ID_FIELD, infrastructureMappingId)
            .setupAbstraction(Cd1SetupFields.SERVICE_ID_FIELD, serviceId)
            .selectionLogsTrackingEnabled(isSelectionLogsTrackingForTasksEnabled())
            .build();
    delegateTaskId = delegateService.queueTask(delegateTask);
    appendDelegateTaskDetails(context, delegateTask);
    return delegateTaskId;
  }

  private void getArtifactDetails(ExecutionContext context, CommandStateExecutionData.Builder executionDataBuilder,
      Service service, String accountId, Artifact artifact, CommandParametersBuilder commandParametersBuilder) {
    log.info("Artifact being used: {} for stateExecutionInstanceId: {}", artifact.getUuid(),
        context.getStateExecutionInstanceId());
    commandParametersBuilder.metadata(MappingUtils.safeCopy(artifact.getMetadata()));
    // Observed NPE in alerts
    ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
    if (artifactStream == null) {
      throw new WingsException(format("Unable to find artifact stream for service %s, artifact %s", service.getName(),
                                   artifact.getArtifactSourceName()),
          WingsException.USER);
    }

    ArtifactStreamAttributes artifactStreamAttributes =
        artifactStream.fetchArtifactStreamAttributes(featureFlagService);
    artifactStreamAttributes.setArtifactStreamId(artifactStream.getUuid());
    if (!ArtifactStreamType.CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
      if (settingAttribute == null) {
        throw new WingsException(
            format("Unable to find Connector/Cloud Provider for artifact stream %s", artifactStream.getSourceName()),
            WingsException.USER);
      }
      artifactStreamAttributes.setServerSetting(settingAttribute);
      artifactStreamAttributes.setArtifactServerEncryptedDataDetails(secretManager.getEncryptionDetails(
          (EncryptableSetting) artifactStreamAttributes.getServerSetting().getValue(), context.getAppId(),
          context.getWorkflowExecutionId()));
      commandParametersBuilder.artifactServerEncryptedDataDetails(secretManager.getEncryptionDetails(
          (EncryptableSetting) artifactStreamAttributes.getServerSetting().getValue(), context.getAppId(),
          context.getWorkflowExecutionId()));
    }

    artifactStreamAttributes.setMetadataOnly(
        metadataOnlyBehindFlag(featureFlagService, artifactStream.getAccountId(), artifactStream.isMetadataOnly()));
    artifactStreamAttributes.setMetadata(artifact.getMetadata());
    artifactStreamAttributes.setArtifactFileMetadata(artifact.getArtifactFileMetadata());

    artifactStreamAttributes.setArtifactType(service.getArtifactType());
    commandParametersBuilder.artifactStreamAttributes(artifactStreamAttributes);

    commandParametersBuilder.artifactFiles(artifact.getArtifactFiles());
    executionDataBuilder.withArtifactName(artifact.getDisplayName()).withActivityId(artifact.getUuid());
    populateArtifactFileNameToContext(commandParametersBuilder, artifact);
  }

  private void populateArtifactFileNameToContext(CommandParametersBuilder commandParametersBuilder, Artifact artifact) {
    String artifactFileName = null;
    if (artifact == null) {
      return;
    }
    List<ArtifactFile> artifactFileList = artifact.getArtifactFiles();
    if (artifactFileList == null || artifactFileList.size() == 0) {
      return;
    }
    ArtifactFile artifactFile = artifact.getArtifactFiles().get(0);
    if (artifactFile.getName() == null) {
      if (artifact.getMetadata() != null) {
        artifactFileName = artifact.getMetadata().get(ArtifactMetadataKeys.artifactFileName);
      }
    } else {
      artifactFileName = artifactFile.getName();
    }
    commandParametersBuilder.artifactFileName(artifactFileName);
  }

  private void getMultiArtifactDetails(ExecutionContext context, CommandStateExecutionData.Builder executionDataBuilder,
      Service service, String accountId, Map<String, Artifact> map, CommandParametersBuilder commandParametersBuilder) {
    commandParametersBuilder.multiArtifactMap(map);
    Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap = new HashMap<>();
    Map<String, List<EncryptedDataDetail>> artifactServerEncryptedDataDetailsMap = new HashMap<>();
    if (isNotEmpty(map)) {
      for (Entry<String, Artifact> entry : map.entrySet()) {
        Artifact artifact = entry.getValue();
        log.info("Artifact being used: {} for stateExecutionInstanceId: {}", artifact.getUuid(),
            context.getStateExecutionInstanceId());
        // Observed NPE in alerts
        ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
        if (artifactStream == null) {
          throw new WingsException(format("Unable to find artifact stream for service %s, artifact %s",
                                       service.getName(), artifact.getArtifactSourceName()),
              WingsException.USER);
        }

        ArtifactStreamAttributes artifactStreamAttributes =
            artifactStream.fetchArtifactStreamAttributes(featureFlagService);
        artifactStreamAttributes.setArtifactStreamId(artifactStream.getUuid());
        if (!ArtifactStreamType.CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
          SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
          if (settingAttribute == null) {
            throw new WingsException(format("Unable to find Connector/Cloud Provider for artifact stream %s",
                                         artifactStream.getSourceName()),
                WingsException.USER);
          }
          artifactStreamAttributes.setServerSetting(settingAttribute);
          List<EncryptedDataDetail> encryptedDataDetails = secretManager.getEncryptionDetails(
              (EncryptableSetting) artifactStreamAttributes.getServerSetting().getValue(), context.getAppId(),
              context.getWorkflowExecutionId());
          artifactStreamAttributes.setArtifactServerEncryptedDataDetails(encryptedDataDetails);
          artifactServerEncryptedDataDetailsMap.put(artifact.getUuid(), encryptedDataDetails);
        }

        artifactStreamAttributes.setMetadataOnly(
            metadataOnlyBehindFlag(featureFlagService, artifactStream.getAccountId(), artifactStream.isMetadataOnly()));
        artifactStreamAttributes.setMetadata(artifact.getMetadata());
        artifactStreamAttributes.setArtifactFileMetadata(artifact.getArtifactFileMetadata());

        artifactStreamAttributes.setArtifactType(service.getArtifactType());
        artifactStreamAttributesMap.put(artifact.getUuid(), artifactStreamAttributes);

        artifact.setArtifactFiles(artifactService.fetchArtifactFiles(artifact.getUuid()));
      }
      commandParametersBuilder.artifactStreamAttributesMap(artifactStreamAttributesMap);
      commandParametersBuilder.artifactServerEncryptedDataDetailsMap(artifactServerEncryptedDataDetailsMap);
      addArtifactFileNameToContext(map, artifactStreamAttributesMap, commandParametersBuilder);
    }
  }

  private void addArtifactFileNameToContext(Map<String, Artifact> multiArtifactMap,
      Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap,
      CommandParametersBuilder commandParametersBuilder) {
    artifactFileName = resolveArtifactFileName(multiArtifactMap, artifactStreamAttributesMap);
    // add $ARTIFACT_FILE_NAME to context
    if (isNotEmpty(artifactFileName)) {
      commandParametersBuilder.artifactFileName(artifactFileName);
    }
  }

  private String resolveArtifactFileName(
      Map<String, Artifact> multiArtifactMap, Map<String, ArtifactStreamAttributes> artifactStreamAttributesMap) {
    String artifactFileName = null;
    if (multiArtifactMap.size() == 1) {
      String artifactVariableName = multiArtifactMap.keySet().stream().findFirst().isPresent()
          ? multiArtifactMap.keySet().stream().findFirst().get()
          : null;
      Artifact artifact = multiArtifactMap.get(artifactVariableName);
      if (artifact != null) {
        ArtifactStreamAttributes artifactStreamAttributes = artifactStreamAttributesMap.get(artifact.getUuid());
        if (artifactStreamAttributes == null) {
          throw new InvalidRequestException(
              format("ArtifactStreamAttributes not found for artifact: %s", artifactVariableName));
        }
        if (artifactStreamAttributes.getMetadata() != null) {
          String value = artifactStreamAttributes.getMetadata().get(ArtifactMetadataKeys.artifactFileName);
          if (isNotEmpty(value)) {
            artifactFileName = value;
          }
        } else if (isNotEmpty(artifact.getArtifactFiles())) {
          String name = artifact.getArtifactFiles().get(0).getName();
          if (isNotEmpty(name)) {
            artifactFileName = name;
          }
        }
      }
    }
    return artifactFileName;
  }

  private void getHostConnectionDetails(
      ExecutionContext context, Host host, CommandParametersBuilder commandParametersBuilder) {
    if (isNotEmpty(host.getHostConnAttr())) {
      SettingAttribute hostConnectionAttribute = settingsService.get(host.getHostConnAttr());
      commandParametersBuilder.hostConnectionAttributes(hostConnectionAttribute);
      commandParametersBuilder.hostConnectionCredentials(
          secretManager.getEncryptionDetails((EncryptableSetting) hostConnectionAttribute.getValue(),
              context.getAppId(), context.getWorkflowExecutionId()));
      if (hostConnectionAttribute.getValue() instanceof HostConnectionAttributes
          && ((HostConnectionAttributes) hostConnectionAttribute.getValue()).isVaultSSH()) {
        commandParametersBuilder.sshVaultConfig(
            sshVaultService.getSSHVaultConfig(hostConnectionAttribute.getAccountId(),
                ((HostConnectionAttributes) hostConnectionAttribute.getValue()).getSshVaultConfigId()));
      }
    }
    if (isNotEmpty(host.getBastionConnAttr())) {
      SettingAttribute bastionConnectionAttribute = settingsService.get(host.getBastionConnAttr());
      commandParametersBuilder.bastionConnectionAttributes(bastionConnectionAttribute);
      commandParametersBuilder.bastionConnectionCredentials(
          secretManager.getEncryptionDetails((EncryptableSetting) bastionConnectionAttribute.getValue(),
              context.getAppId(), context.getWorkflowExecutionId()));
    }
    if (isNotEmpty(host.getWinrmConnAttr())) {
      WinRmConnectionAttributes winrmConnectionAttribute =
          (WinRmConnectionAttributes) settingsService.get(host.getWinrmConnAttr()).getValue();
      commandParametersBuilder.winrmConnectionAttributes(winrmConnectionAttribute);
      commandParametersBuilder.winrmConnectionEncryptedDataDetails(secretManager.getEncryptionDetails(
          winrmConnectionAttribute, context.getAppId(), context.getWorkflowExecutionId()));
    }
  }

  @NotNull
  private List<String> getDelegateSelectors(ExecutionContext context) {
    List<String> renderedSelectorsSet = new ArrayList<>();

    if (EmptyPredicate.isNotEmpty(delegateSelectors)) {
      for (String selector : delegateSelectors) {
        renderedSelectorsSet.add(context.renderExpression(selector));
      }
    }
    return renderedSelectorsSet;
  }

  private void setPropertiesFromFeatureFlags(String accountId, CommandParametersBuilder commandParametersBuilder) {
    if (featureFlagService.isEnabled(FeatureName.INLINE_SSH_COMMAND, accountId)) {
      commandParametersBuilder.inlineSshCommand(true);
    }
    if (featureFlagService.isEnabled(FeatureName.DISABLE_WINRM_COMMAND_ENCODING, accountId)) {
      commandParametersBuilder.disableWinRMCommandEncodingFFSet(true);
    }

    commandParametersBuilder.multiArtifact(
        featureFlagService.isEnabled(FeatureName.ARTIFACT_STREAM_REFACTOR, accountId));

    commandParametersBuilder.useWinRMKerberosUniqueCacheFile(
        featureFlagService.isEnabled(FeatureName.WINRM_KERBEROS_CACHE_UNIQUE_FILE, accountId));
  }

  private ExecutionResponse handleException(ExecutionContext context,
      CommandStateExecutionData.Builder executionDataBuilder, String activityId, String appId, Exception e) {
    if (e instanceof WingsException) {
      log.warn("Exception in command execution", e);
    } else {
      // unhandled exception
      log.error("Exception in command execution", e);
    }
    handleCommandException(context, activityId, appId);
    updateWorkflowExecutionStats(ExecutionStatus.FAILED, context);
    return ExecutionResponse.builder()
        .executionStatus(ExecutionStatus.FAILED)
        .stateExecutionData(executionDataBuilder.build())
        .errorMessage(ExceptionUtils.getMessage(e))
        .build();
  }

  private Command getCommandFromTemplate(String templateId, String version) {
    Template template = templateService.get(templateId, version);
    Command commandEntity = aCommand().build();
    if (getTemplateUuid() != null) {
      commandEntity.setTemplateVariables(getTemplateVariables());
    } else {
      commandEntity.setTemplateVariables(template.getVariables());
    }
    if (template != null) {
      SshCommandTemplate sshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
      commandEntity.setName(template.getName());
      commandEntity.setCommandType(sshCommandTemplate.getCommandType());
      commandEntity.setCommandUnits(
          sshCommandTemplate.getCommandUnits() != null ? sshCommandTemplate.getCommandUnits() : new ArrayList<>());
      for (CommandUnit commandUnit : commandEntity.getCommandUnits()) {
        if (commandUnit instanceof Command) {
          if (((Command) commandUnit).getTemplateReference() != null) {
            template = templateService.get(((Command) commandUnit).getTemplateReference().getTemplateUuid(),
                String.valueOf(((Command) commandUnit).getTemplateReference().getTemplateVersion()));
            if (template != null) {
              List<CommandUnit> commandUnits = ((SshCommandTemplate) template.getTemplateObject()).getCommandUnits();
              ((Command) commandUnit).setCommandUnits(commandUnits != null ? commandUnits : new ArrayList<>());
            }
          }
        }
      }
    }
    return commandEntity;
  }

  void renderCommandString(Command command, ExecutionContext context,
      CommandStateExecutionData commandStateExecutionData, Artifact artifact, int expressionFunctorToken) {
    renderCommandString(command, context, commandStateExecutionData, artifact, false, expressionFunctorToken);
  }

  void renderCommandString(Command command, ExecutionContext context,
      CommandStateExecutionData commandStateExecutionData, Artifact artifact, boolean linkedFromTemplateLibrary,
      int expressionFunctorToken) {
    for (CommandUnit commandUnit : command.getCommandUnits()) {
      if (CommandUnitType.COMMAND == commandUnit.getCommandUnitType()) {
        Command commandCommandUnit = (Command) commandUnit;
        commandStateExecutionData.setTemplateVariable(
            templateUtils.processTemplateVariables(context, commandCommandUnit.getTemplateVariables()));
        renderCommandString((Command) commandUnit, context, commandStateExecutionData, artifact,
            linkedFromTemplateLibrary, expressionFunctorToken);
        continue;
      }

      if (linkedFromTemplateLibrary) {
        commandStateExecutionData.setTemplateVariable(
            templateUtils.processTemplateVariables(context, command.getTemplateVariables()));
        commandUnit.setVariables(command.getTemplateVariables());
      }

      // handle all command units
      ExpressionReflectionUtils.applyExpression(commandUnit,
          (secretMode, value)
              -> context.renderExpression(value,
                  StateExecutionContext.builder()
                      .stateExecutionData(commandStateExecutionData)
                      .artifact(artifact)
                      .adoptDelegateDecryption(true)
                      .expressionFunctorToken(expressionFunctorToken)
                      .build()));
    }
  }

  void renderCommandString(Command command, ExecutionContext context,
      CommandStateExecutionData commandStateExecutionData, int expressionFunctorToken) {
    renderCommandString(command, context, commandStateExecutionData, false, expressionFunctorToken);
  }

  void renderCommandString(Command command, ExecutionContext context,
      CommandStateExecutionData commandStateExecutionData, boolean linkedFromTemplateLibrary,
      int expressionFunctorToken) {
    for (CommandUnit commandUnit : command.getCommandUnits()) {
      if (CommandUnitType.COMMAND == commandUnit.getCommandUnitType()) {
        Command commandCommandUnit = (Command) commandUnit;
        commandStateExecutionData.setTemplateVariable(
            templateUtils.processTemplateVariables(context, commandCommandUnit.getTemplateVariables()));
        renderCommandString((Command) commandUnit, context, commandStateExecutionData, linkedFromTemplateLibrary,
            expressionFunctorToken);
        continue;
      }

      if (linkedFromTemplateLibrary) {
        commandStateExecutionData.setTemplateVariable(
            templateUtils.processTemplateVariables(context, command.getTemplateVariables()));
        commandUnit.setVariables(command.getTemplateVariables());
      }

      StateExecutionContextBuilder stateExecutionContextBuilder = StateExecutionContext.builder()
                                                                      .stateExecutionData(commandStateExecutionData)
                                                                      .adoptDelegateDecryption(true)
                                                                      .expressionFunctorToken(expressionFunctorToken);
      if (artifactFileName != null) {
        stateExecutionContextBuilder.artifactFileName(artifactFileName);
      }
      ExpressionReflectionUtils.applyExpression(
          commandUnit, (secretMode, value) -> context.renderExpression(value, stateExecutionContextBuilder.build()));
    }
  }

  // used in tests
  void renderTailFilePattern(ExecutionContext context, CommandStateExecutionData commandStateExecutionData,
      Artifact artifact, ExecCommandUnit execCommandUnit) {
    List<TailFilePatternEntry> filePatternEntries = execCommandUnit.getTailPatterns();
    for (TailFilePatternEntry filePatternEntry : filePatternEntries) {
      if (isNotEmpty(filePatternEntry.getFilePath())) {
        filePatternEntry.setFilePath(context.renderExpression(filePatternEntry.getFilePath(),
            StateExecutionContext.builder().stateExecutionData(commandStateExecutionData).artifact(artifact).build()));
      }
      if (isNotEmpty(filePatternEntry.getPattern())) {
        filePatternEntry.setPattern(context.renderExpression(filePatternEntry.getPattern(),
            StateExecutionContext.builder().stateExecutionData(commandStateExecutionData).artifact(artifact).build()));
      }
    }
    execCommandUnit.setTailPatterns(filePatternEntries);
  }

  private List<CommandUnit> getFlattenCommandUnits(String appId, String envId, Service service, String deploymentType,
      String accountId, Command templateCommand, boolean fromTemplate) {
    List<CommandUnit> flattenCommandUnitList;
    if (fromTemplate) {
      flattenCommandUnitList = getFlattenCommandUnitList(templateCommand);
    } else {
      flattenCommandUnitList =
          serviceResourceService.getFlattenCommandUnitList(appId, service.getUuid(), envId, commandName);
    }

    // if (!executeOnDelegate) {
    if (DeploymentType.SSH.name().equals(deploymentType)) {
      if (getScriptType(flattenCommandUnitList) == ScriptType.POWERSHELL) {
        flattenCommandUnitList.add(0, new InitPowerShellCommandUnit());
        flattenCommandUnitList.add(new CleanupPowerShellCommandUnit());
      } else {
        if (featureFlagService.isEnabled(FeatureName.INLINE_SSH_COMMAND, accountId)) {
          flattenCommandUnitList.add(0, new InitSshCommandUnitV2());
        } else {
          flattenCommandUnitList.add(0, new InitSshCommandUnit());
        }
        flattenCommandUnitList.add(new CleanupSshCommandUnit());
      }
    } else if (DeploymentType.WINRM.name().equals(deploymentType)) {
      flattenCommandUnitList.add(0, new InitPowerShellCommandUnit());
      flattenCommandUnitList.add(new CleanupPowerShellCommandUnit());
    }
    // }
    return flattenCommandUnitList;
  }

  private List<CommandUnit> getFlattenCommandUnitList(Command command) {
    return command.getCommandUnits()
        .stream()
        .flatMap(commandUnit -> {
          if (CommandUnitType.COMMAND == commandUnit.getCommandUnitType()) {
            return getFlattenCommandUnitList((Command) commandUnit).stream();
          } else {
            return Stream.of(commandUnit);
          }
        })
        .collect(toList());
  }

  private Artifact findArtifact(String serviceId, ExecutionContext context) {
    if (isRollback()) {
      if (context.getContextElement(ContextElementType.INSTANCE) == null) {
        WorkflowStandardParams contextElement = context.getContextElement(ContextElementType.STANDARD);
        return workflowStandardParamsExtensionService.getRollbackArtifactForService(contextElement, serviceId);
      }
      Artifact previousArtifact = serviceResourceService.findPreviousArtifact(
          context.getAppId(), context.getWorkflowExecutionId(), context.getContextElement(ContextElementType.INSTANCE));
      if (previousArtifact != null) {
        return previousArtifact;
      }
    }
    return ((DeploymentExecutionContext) context).getArtifactForService(serviceId);
  }

  private Map<String, Artifact> findArtifacts(String serviceId, ExecutionContext context) {
    // NOTE: This function also takes care of rollback.
    return ((DeploymentExecutionContext) context).getArtifactsForService(serviceId);
  }

  private ScriptType getScriptType(List<CommandUnit> commandUnits) {
    if (commandUnits.stream().anyMatch(unit
            -> (unit.getCommandUnitType() == CommandUnitType.EXEC
                   || unit.getCommandUnitType() == CommandUnitType.DOWNLOAD_ARTIFACT)
                && ((ExecCommandUnit) unit).getScriptType() == ScriptType.POWERSHELL)) {
      return ScriptType.POWERSHELL;
    } else {
      return ScriptType.BASH;
    }
  }

  private void handleCommandException(ExecutionContext context, String activityId, String appId) {
    if (activityId != null) {
      activityHelperService.updateStatus(activityId, appId, ExecutionStatus.FAILED);
    }
  }

  @Override
  public ExecutionResponse handleAsyncResponse(ExecutionContext context, Map<String, ResponseData> response) {
    if (response.size() != 1) {
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage("Unexpected number of response data items")
          .build();
    }

    DelegateResponseData notifyResponseData = (DelegateResponseData) response.values().iterator().next();

    if (notifyResponseData instanceof ErrorNotifyResponseData) {
      return ExecutionResponse.builder()
          .executionStatus(ExecutionStatus.FAILED)
          .errorMessage(((ErrorNotifyResponseData) notifyResponseData).getErrorMessage())
          .build();
    }

    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    String appId = workflowStandardParams.getAppId();

    CommandExecutionResult commandExecutionResult = (CommandExecutionResult) notifyResponseData;
    String activityId = response.keySet().iterator().next();

    if (commandExecutionResult.getStatus() != SUCCESS && isNotEmpty(commandExecutionResult.getErrorMessage())) {
      handleCommandException(context, activityId, appId);
    }

    activityHelperService.updateStatus(activityId, appId,
        commandExecutionResult.getStatus() == SUCCESS ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);

    ExecutionStatus executionStatus =
        commandExecutionResult.getStatus() == SUCCESS ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILED;
    updateWorkflowExecutionStats(executionStatus, context);

    CommandStateExecutionData commandStateExecutionData = (CommandStateExecutionData) context.getStateExecutionData();
    commandStateExecutionData.setStatus(executionStatus);
    on(commandStateExecutionData).set("activityService", activityService);
    commandStateExecutionData.setCountsByStatuses(
        (CountsByStatuses) commandStateExecutionData.getExecutionSummary().get("breakdown").getValue());
    commandStateExecutionData.setDelegateMetaInfo(commandExecutionResult.getDelegateMetaInfo());

    return ExecutionResponse.builder()
        .executionStatus(executionStatus)
        .errorMessage(commandExecutionResult.getErrorMessage())
        .stateExecutionData(commandStateExecutionData)
        .build();
  }

  /**
   * Handle abort event.
   *
   * @param context the context
   */
  @Override
  public void handleAbortEvent(ExecutionContext context) {}

  private void updateWorkflowExecutionStats(ExecutionStatus executionStatus, ExecutionContext context) {
    Optional<ContextElement> simpleWorkflowParamOpt =
        context.getContextElementList(ContextElementType.PARAM)
            .stream()
            .filter(contextElement -> contextElement instanceof SimpleWorkflowParam)
            .findFirst();
    if (simpleWorkflowParamOpt.isPresent()) {
      String appId = getAppId(context);
      if (executionStatus == ExecutionStatus.FAILED) {
        workflowExecutionService.incrementFailed(appId, context.getWorkflowExecutionId(), 1);
      } else {
        workflowExecutionService.incrementSuccess(appId, context.getWorkflowExecutionId(), 1);
      }
    }
  }

  private String getAppId(ExecutionContext context) {
    WorkflowStandardParams workflowStandardParams = context.getContextElement(ContextElementType.STANDARD);
    return workflowStandardParams.getAppId();
  }

  private void updateWorkflowExecutionStatsInProgress(ExecutionContext context) {
    Optional<ContextElement> simpleWorkflowParamOpt =
        context.getContextElementList(ContextElementType.PARAM)
            .stream()
            .filter(contextElement -> contextElement instanceof SimpleWorkflowParam)
            .findFirst();
    if (simpleWorkflowParamOpt.isPresent()) {
      String appId = getAppId(context);
      workflowExecutionService.incrementInProgressCount(appId, context.getWorkflowExecutionId(), 1);
    }
  }

  private String getEvaluatedSettingValue(
      ExecutionContext context, String accountId, String appId, String envId, String variable) {
    SettingAttribute settingAttribute = settingsService.getByName(accountId, appId, envId, variable);

    if (settingAttribute == null) {
      return "";
    }

    StringValue stringValue = (StringValue) settingAttribute.getValue();
    String settingValue = stringValue.getValue();
    try {
      settingValue = context.renderExpression(settingValue);
    } catch (Exception e) {
      // ignore
    }
    return settingValue;
  }

  private void expandCommand(ServiceInstance serviceInstance, Command command, String serviceId, String envId) {
    if (isNotEmpty(command.getReferenceId())) {
      Command referredCommand = Optional
                                    .ofNullable(serviceResourceService.getCommandByName(
                                        serviceInstance.getAppId(), serviceId, envId, command.getReferenceId()))
                                    .orElse(aServiceCommand().build())
                                    .getCommand();
      if (referredCommand == null) {
        throw new WingsException(COMMAND_DOES_NOT_EXIST, USER);
      }
      command.setCommandUnits(referredCommand.getCommandUnits());
      command.setTemplateVariables(referredCommand.getTemplateVariables());
    }

    if (command.getTemplateReference() != null) {
      Template template = templateService.get(command.getTemplateReference().getTemplateUuid(),
          command.getTemplateReference().getTemplateVersion().toString());
      if (template != null) {
        SshCommandTemplate sshCommandTemplate = (SshCommandTemplate) template.getTemplateObject();
        command.setCommandUnits(sshCommandTemplate.getCommandUnits());
      }
    }

    for (CommandUnit commandUnit : command.getCommandUnits()) {
      if (CommandUnitType.COMMAND == commandUnit.getCommandUnitType()) {
        expandCommand(serviceInstance, (Command) commandUnit, serviceId, envId);
      }
    }
  }

  private void expandCommand(Command command, String templateId, String templateVersion, String parentName) {
    Command referredCommand = Optional.ofNullable(getCommandFromTemplate(templateId, templateVersion))
                                  .orElse(aServiceCommand().build().getCommand());

    if (referredCommand == null) {
      throw new WingsException(COMMAND_DOES_NOT_EXIST, USER);
    }
    command.setCommandUnits(referredCommand.getCommandUnits());
    for (CommandUnit commandUnit : command.getCommandUnits()) {
      if (CommandUnitType.COMMAND == commandUnit.getCommandUnitType()) {
        if (((Command) commandUnit).getTemplateReference() == null) {
          throw new WingsException("No command units found in command " + commandUnit.getName(), USER);
        }
        expandCommand((Command) commandUnit, ((Command) commandUnit).getTemplateReference().getTemplateUuid(),
            String.valueOf(((Command) commandUnit).getTemplateReference().getTemplateVersion()),
            parentName + "/" + commandUnit.getName());
      } else {
        commandUnit.setName(parentName + "/" + commandUnit.getName());
      }
    }
  }

  private void resolveTemplateVariablesInLinkedCommands(
      Command command, List<ReferencedTemplate> referencedTemplateList, List<Variable> globalVariables) {
    for (int i = 0; i < command.getCommandUnits().size(); i++) {
      CommandUnit commandUnit = command.getCommandUnits().get(i);
      if (CommandUnitType.COMMAND == commandUnit.getCommandUnitType()) {
        List<Variable> variables = ((Command) commandUnit).getTemplateVariables();
        if (isNotEmpty(referencedTemplateList)) {
          ReferencedTemplate referencedTemplate = referencedTemplateList.get(i);
          if (referencedTemplate != null) {
            Map<String, Variable> variableMapping = referencedTemplate.getVariableMapping();
            if (isNotEmpty(variables) && isNotEmpty(variableMapping)) {
              for (Variable variable : variables) {
                if (variableMapping.containsKey(variable.getName())) {
                  Variable mappedVariable = variableMapping.get(variable.getName());
                  if (mappedVariable == null) {
                    throw new WingsException(
                        format("Value was not set for variable %s/%s", commandUnit.getName(), variable.getName()),
                        USER);
                  }
                  variable.setValue(getTopLevelTemplateVariableValue(globalVariables, mappedVariable.getName()));
                }
              }
            }
          }
        }
      }
    }
  }

  private String getTopLevelTemplateVariableValue(List<Variable> variables, String lookupVariable) {
    if (isNotEmpty(variables)) {
      for (Variable variable : variables) {
        if (variable.getName().equals(lookupVariable)) {
          return variable.getValue();
        }
      }
    }
    return null;
  }

  @Override
  @SchemaIgnore
  public List<EntityType> getRequiredExecutionArgumentTypes() {
    return Lists.newArrayList(EntityType.SERVICE, EntityType.INSTANCE);
  }

  @Override
  @SchemaIgnore
  public List<String> getPatternsForRequiredContextElementType() {
    if (executeOnDelegate) {
      return null;
    }
    if (host != null) {
      return asList(host);
    }
    return asList("${instance}");
  }

  /**
   * Gets executor service.
   *
   * @return the executor service
   */
  @SchemaIgnore
  public ExecutorService getExecutorService() {
    return executorService;
  }

  /**
   * Sets executor service.
   *
   * @param executorService the executor service
   */
  @SchemaIgnore
  public void setExecutorService(ExecutorService executorService) {
    this.executorService = executorService;
  }

  @Override
  public boolean isSelectionLogsTrackingForTasksEnabled() {
    return true;
  }

  /**
   * The type Builder.
   */
  public static final class Builder {
    private String commandName;
    private String name;

    private Builder() {}

    /**
     * A command state builder.
     *
     * @return the builder
     */
    public static Builder aCommandState() {
      return new Builder();
    }

    /**
     * With command name builder.
     *
     * @param commandName the command name
     * @return the builder
     */
    public Builder withCommandName(String commandName) {
      this.commandName = commandName;
      return this;
    }

    /**
     * With name builder.
     *
     * @param name the name
     * @return the builder
     */
    public Builder withName(String name) {
      this.name = name;
      return this;
    }

    /**
     * Build command state.
     *
     * @return the command state
     */
    public CommandState build() {
      CommandState commandState = new CommandState(name);
      commandState.setCommandName(commandName);
      return commandState;
    }
  }
}
