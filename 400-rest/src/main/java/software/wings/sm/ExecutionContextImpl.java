/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm;

import static io.harness.annotations.dev.HarnessModule._870_CG_ORCHESTRATION;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.EnvironmentType.ALL;
import static io.harness.beans.ExecutionStatus.RUNNING;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.beans.TriggeredBy.triggeredBy;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.exception.WingsException.USER;
import static io.harness.govern.Switch.unhandled;
import static io.harness.k8s.KubernetesConvention.getNormalizedInfraMappingIdLabelValue;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_NESTS;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_ENV_ID;
import static software.wings.beans.ServiceVariableType.TEXT;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.MASKED;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE;
import static software.wings.sm.ContextElement.ARTIFACT;
import static software.wings.sm.ContextElement.ENVIRONMENT_VARIABLE;
import static software.wings.sm.ContextElement.HELM_CHART;
import static software.wings.sm.ContextElement.ROLLBACK_ARTIFACT;
import static software.wings.sm.ContextElement.SAFE_DISPLAY_SERVICE_VARIABLE;
import static software.wings.sm.ContextElement.SERVICE_VARIABLE;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.join;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EmbeddedUser;
import io.harness.beans.FeatureName;
import io.harness.beans.OrchestrationWorkflowType;
import io.harness.beans.SweepingOutput;
import io.harness.beans.SweepingOutputInstance;
import io.harness.beans.SweepingOutputInstance.Scope;
import io.harness.beans.SweepingOutputInstance.SweepingOutputInstanceBuilder;
import io.harness.beans.WorkflowType;
import io.harness.context.ContextElementType;
import io.harness.data.structure.CollectionUtils;
import io.harness.deployment.InstanceDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.expression.LateBindingMap;
import io.harness.expression.SecretString;
import io.harness.expression.TerraformPlanExpressionFunctor;
import io.harness.expression.VariableResolverTracker;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.security.SimpleEncryption;
import io.harness.serializer.KryoSerializer;
import io.harness.terraform.expression.TerraformPlanExpressionInterface;

import software.wings.api.ContextElementParamMapper;
import software.wings.api.ContextElementParamMapperFactory;
import software.wings.api.DeploymentType;
import software.wings.api.InfraMappingElement;
import software.wings.api.InfraMappingElement.CloudProvider;
import software.wings.api.InfraMappingElement.Helm;
import software.wings.api.InfraMappingElement.InfraMappingElementBuilder;
import software.wings.api.InfraMappingElement.Kubernetes;
import software.wings.api.InfraMappingElement.Pcf;
import software.wings.api.InstanceElement;
import software.wings.api.PhaseElement;
import software.wings.api.ServiceElement;
import software.wings.api.ServiceTemplateElement;
import software.wings.api.artifact.ServiceArtifactElement;
import software.wings.api.artifact.ServiceArtifactElements;
import software.wings.api.artifact.ServiceArtifactVariableElement;
import software.wings.api.artifact.ServiceArtifactVariableElements;
import software.wings.api.helm.HelmReleaseInfoElement;
import software.wings.api.helm.ServiceHelmElement;
import software.wings.api.helm.ServiceHelmElements;
import software.wings.api.instancedetails.InstanceApiResponse;
import software.wings.api.instancedetails.InstanceInfoVariables;
import software.wings.beans.Activity;
import software.wings.beans.Activity.ActivityBuilder;
import software.wings.beans.Application;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.CustomInfrastructureMapping;
import software.wings.beans.DeploymentExecutionContext;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.Environment;
import software.wings.beans.ErrorStrategy;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfraMappingSweepingOutput;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureMappingType;
import software.wings.beans.NameValuePair;
import software.wings.beans.PcfInfrastructureMapping;
import software.wings.beans.RancherKubernetesInfrastructureMapping;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Variable;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.customdeployment.CustomDeploymentTypeDTO;
import software.wings.common.InfrastructureConstants;
import software.wings.common.RancherK8sClusterProcessor;
import software.wings.common.VariableProcessor;
import software.wings.expression.ArtifactLabelEvaluator;
import software.wings.expression.ArtifactMetadataEvaluator;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.expression.SecretFunctor;
import software.wings.expression.ShellScriptFunctor;
import software.wings.expression.SubstitutionFunctor;
import software.wings.expression.SweepingOutputFunctor;
import software.wings.expression.SweepingOutputSecretManagerFunctor;
import software.wings.infra.InfrastructureDefinition;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.AppLogContext;
import software.wings.service.impl.PipelineWorkflowExecutionLogContext;
import software.wings.service.impl.StateExecutionInstanceLogContext;
import software.wings.service.impl.SweepingOutputServiceImpl;
import software.wings.service.impl.WorkflowExecutionLogContext;
import software.wings.service.impl.WorkflowLogContext;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceTemplateService;
import software.wings.service.intfc.ServiceTemplateService.EncryptedFieldComputeMode;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.StateExecutionService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeService;
import software.wings.service.intfc.security.ManagerDecryptionService;
import software.wings.service.intfc.security.SecretManager;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry;
import software.wings.service.intfc.sweepingoutput.SweepingOutputInquiry.SweepingOutputInquiryBuilder;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;
import software.wings.settings.SettingValue;
import software.wings.sm.LateBindingServiceVariables.LateBindingServiceVariablesBuilder;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Injector;
import dev.morphia.Key;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(CDC)
@Slf4j
@TargetModule(_870_CG_ORCHESTRATION)
public class ExecutionContextImpl implements DeploymentExecutionContext {
  public static final String PHASE_PARAM = "PHASE_PARAM";
  private static final SecureRandom random = new SecureRandom();
  private static final Pattern wildCharPattern = Pattern.compile("[-+*/\\\\ &$\"'.|\\(\\)]");
  private static final Pattern argsCharPattern = Pattern.compile("[()\"']");
  private static final String CURRENT_STEP_LITERAL = "currentStep";

  @Inject private BuildSourceService buildSourceService;
  @Inject private transient ArtifactService artifactService;
  @Inject private transient ArtifactStreamService artifactStreamService;
  @Inject private transient ExpressionProcessorFactory expressionProcessorFactory;
  @Inject private transient ManagerDecryptionService managerDecryptionService;
  @Inject private transient ManagerExpressionEvaluator evaluator;
  @Inject private transient SecretManager secretManager;
  @Inject private transient InfrastructureMappingService infrastructureMappingService;
  @Inject private transient ServiceResourceService serviceResourceService;
  @Inject private transient ServiceTemplateService serviceTemplateService;
  @Inject private transient ServiceVariableService serviceVariableService;
  @Inject private transient SettingsService settingsService;
  @Inject private transient SweepingOutputService sweepingOutputService;
  @Inject private transient VariableProcessor variableProcessor;
  @Inject private transient FeatureFlagService featureFlagService;
  @Inject private transient InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private transient StateExecutionService stateExecutionService;
  @Inject private transient ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private transient KryoSerializer kryoSerializer;
  @Inject private transient CustomDeploymentTypeService customDeploymentTypeService;
  @Inject private transient HelmChartService helmChartService;
  @Inject private transient FileService fileService;
  @Inject private transient ContextElementParamMapperFactory contextElementParamMapperFactory;
  @Inject private transient WorkflowStandardParamsExtensionService workflowStandardParamsExtensionService;

  private StateMachine stateMachine;
  private StateExecutionInstance stateExecutionInstance;
  @Getter private transient Map<String, Object> contextMap;
  @Getter private transient VariableResolverTracker variableResolverTracker = new VariableResolverTracker();

  /**
   * Instantiates a new execution context impl.
   * Only used for UTs.
   *
   * @param stateExecutionInstance the state execution instance
   */
  public ExecutionContextImpl(StateExecutionInstance stateExecutionInstance) {
    this.stateExecutionInstance = stateExecutionInstance;
  }

  /**
   * Instantiates a new execution context impl.
   *
   * @param stateExecutionInstance the state execution instance
   * @param stateMachine           the state machine
   * @param injector               the injector
   */
  public ExecutionContextImpl(
      StateExecutionInstance stateExecutionInstance, StateMachine stateMachine, Injector injector) {
    injector.injectMembers(this);
    this.stateExecutionInstance = stateExecutionInstance;
    this.stateMachine = stateMachine;
    if (isNotEmpty(stateExecutionInstance.getContextElements())) {
      stateExecutionInstance.getContextElements().forEach(
          contextElement -> { injector.injectMembers(contextElement); });
    }
    if (isNotEmpty(stateExecutionInstance.getExecutionEventAdvisors())) {
      stateExecutionInstance.getExecutionEventAdvisors().forEach(injector::injectMembers);
    }
  }

  public static void addArtifactToContext(ArtifactStreamService artifactStreamService, String accountId,
      Map<String, Object> map, Artifact artifact, BuildSourceService buildSourceService, boolean rollbackArtifact) {
    if (artifact != null) {
      artifact.setSource(
          artifactStreamService.fetchArtifactSourceProperties(accountId, artifact.getArtifactStreamId()));
      ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());

      artifact.label = ArtifactLabelEvaluator.builder()
                           .buildNo(artifact.getBuildNo())
                           .buildSourceService(buildSourceService)
                           .artifactStream(artifactStream)
                           .build();
      ArtifactMetadataEvaluator metadataEvaluator = new ArtifactMetadataEvaluator(
          artifact.getMetadata(), artifact.getBuildNo(), artifactStream, buildSourceService);
      artifact.setMetadata(metadataEvaluator);
      map.put(rollbackArtifact ? ROLLBACK_ARTIFACT : ARTIFACT, artifact);
      String artifactFileName = null;
      if (isNotEmpty(artifact.getArtifactFiles())) {
        artifactFileName = artifact.getArtifactFiles().get(0).getName();
      } else if (artifact.getMetadata() != null) {
        artifactFileName = artifact.getFileName();
      }
      if (isNotEmpty(artifactFileName)) {
        map.put(rollbackArtifact ? ExpressionEvaluator.ROLLBACK_ARTIFACT_FILE_NAME_VARIABLE
                                 : ExpressionEvaluator.ARTIFACT_FILE_NAME_VARIABLE,
            artifactFileName);
      }
    } else {
      if (rollbackArtifact) {
        // Roll back artifact could be null if it is the first execution or service has changed/templatized since last
        // execution
        map.put(ROLLBACK_ARTIFACT, Artifact.Builder.anArtifact().build());
      }
    }
  }

  @NotNull
  public static void populateActivity(ActivityBuilder builder, final @NotNull ExecutionContext executionContext) {
    final Application app = executionContext.fetchRequiredApp();
    final Environment env = ((ExecutionContextImpl) executionContext).getEnv();
    final WorkflowStandardParams workflowStandardParams =
        executionContext.getContextElement(ContextElementType.STANDARD);
    final EmbeddedUser currentUser = workflowStandardParams.getCurrentUser();
    final InstanceElement instanceElement = executionContext.getContextElement(ContextElementType.INSTANCE);
    notNullCheck("workflowStandardParams", workflowStandardParams, USER);
    notNullCheck("currentUser", currentUser, USER);

    builder.appId(app.getUuid());
    builder.applicationName(app.getName());
    builder.type(Activity.Type.Verification);
    builder.workflowType(executionContext.getWorkflowType());
    builder.workflowExecutionName(executionContext.getWorkflowExecutionName());
    builder.stateExecutionInstanceId(executionContext.getStateExecutionInstanceId());
    builder.stateExecutionInstanceName(executionContext.getStateExecutionInstanceName());
    builder.workflowId(executionContext.getWorkflowId());
    builder.workflowExecutionId(executionContext.getWorkflowExecutionId());
    builder.commandUnits(Collections.emptyList());
    builder.status(RUNNING);
    builder.triggeredBy(triggeredBy(currentUser.getName(), currentUser.getEmail()));
    builder.accountId(app.getAccountId());

    if (executionContext.getOrchestrationWorkflowType() != null
        && executionContext.getOrchestrationWorkflowType() == BUILD) {
      builder.environmentId(GLOBAL_ENV_ID);
      builder.environmentName(GLOBAL_ENV_ID);
      builder.environmentType(ALL);
    } else if (env != null) {
      builder.environmentId(env.getUuid());
      builder.environmentName(env.getName());
      builder.environmentType(env.getEnvironmentType());
    }
    if (instanceElement != null) {
      final ServiceTemplateElement serviceTemplateElement = instanceElement.getServiceTemplateElement();
      builder.serviceTemplateId(serviceTemplateElement.getUuid());
      builder.serviceTemplateName(serviceTemplateElement.getName());
      builder.serviceId(serviceTemplateElement.getServiceElement().getUuid());
      builder.serviceName(serviceTemplateElement.getServiceElement().getName());
      builder.serviceInstanceId(instanceElement.getUuid());
      builder.hostName(instanceElement.getHost().getHostName());
    }
  }

  public static void addHelmChartToContext(String appId, Map<String, Object> map, HelmChart helmChart,
      ApplicationManifestService applicationManifestService) {
    if (helmChart == null) {
      return;
    }

    if (isEmpty(helmChart.getMetadata())) {
      helmChart.setMetadata(
          applicationManifestService.fetchAppManifestProperties(appId, helmChart.getApplicationManifestId()));
    }
    map.put(HELM_CHART, helmChart);
  }

  @Override
  public String renderExpression(String expression) {
    Map<String, Object> context = prepareContext(null);
    return renderExpression(expression, context);
  }

  @Override
  public String renderExpressionSecured(String expression) {
    Map<String, Object> context = prepareContext(null);
    return renderExpressionSecured(expression, context);
  }

  @Override
  public String renderExpression(String expression, StateExecutionContext stateExecutionContext) {
    return renderExpression(expression, prepareContext(stateExecutionContext));
  }

  @Override
  public List<String> renderExpressionList(List<String> expressions) {
    return renderExpressionList(expressions, ",");
  }

  @Override
  public List<String> renderExpressionList(List<String> expressions, String separator) {
    List<String> result = null;
    if (expressions != null) {
      result = new ArrayList<>();
      for (String expression : expressions) {
        result.addAll(
            Splitter.on(separator).trimResults().omitEmptyStrings().splitToList(renderExpression(expression)));
      }
    }
    return result;
  }

  @Override
  public Object evaluateExpression(String expression) {
    return evaluateExpression(expression, null);
  }

  @Override
  public Object evaluateExpression(String expression, StateExecutionContext stateExecutionContext) {
    return normalizeAndEvaluate(expression, prepareContext(stateExecutionContext),
        asList(normalizeStateName(stateExecutionInstance.getDisplayName()), "context"));
  }

  @Override
  public StateExecutionData getStateExecutionData() {
    return stateExecutionInstance.fetchStateExecutionData();
  }

  @Override
  public boolean isRetry() {
    return stateExecutionInstance.isRetry();
  }

  @Override
  public int retryCount() {
    return stateExecutionInstance.getRetryCount();
  }

  @Override
  public <T extends ContextElement> T getContextElement() {
    return (T) stateExecutionInstance.getContextElement();
  }

  @Override
  public <T extends ContextElement> T getContextElement(ContextElementType contextElementType) {
    if (stateExecutionInstance.getContextElements() == null) {
      return null;
    }
    return (T) stateExecutionInstance.getContextElements()
        .stream()
        .filter(contextElement -> contextElement.getElementType() == contextElementType)
        .findFirst()
        .orElse(null);
  }

  @Override
  public WorkflowStandardParams fetchWorkflowStandardParamsFromContext() {
    if (stateExecutionInstance.getContextElements() == null) {
      throw new InvalidRequestException("State Execution Context Elements can not be null");
    }
    WorkflowStandardParams workflowStandardParams =
        (WorkflowStandardParams) stateExecutionInstance.getContextElements()
            .stream()
            .filter(contextElement -> contextElement.getElementType() == ContextElementType.STANDARD)
            .findFirst()
            .orElse(null);

    if (workflowStandardParams == null) {
      throw new InvalidRequestException("Workflow Standard Params can not be null");
    }
    return workflowStandardParams;
  }

  @Override
  public <T extends ContextElement> T getContextElement(ContextElementType contextElementType, String name) {
    return (T) stateExecutionInstance.getContextElements()
        .stream()
        .filter(contextElement
            -> contextElement.getElementType() == contextElementType && name.equals(contextElement.getName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public <T extends ContextElement> List<T> getContextElementList(ContextElementType contextElementType) {
    return stateExecutionInstance.getContextElements()
        .stream()
        .filter(contextElement -> contextElement.getElementType() == contextElementType)
        .map(contextElement -> (T) contextElement)
        .collect(toList());
  }

  @Override
  public List<Artifact> getArtifacts() {
    WorkflowStandardParams workflowStandardParams = fetchWorkflowStandardParamsFromContext();
    List<ServiceArtifactElement> artifactElements = getArtifactElements();
    if (isEmpty(artifactElements)) {
      return workflowStandardParamsExtensionService.getArtifacts(workflowStandardParams);
    }
    List<Artifact> list = new ArrayList<>();
    for (ServiceArtifactElement artifactElement : artifactElements) {
      list.add(artifactService.get(artifactElement.getUuid()));
    }
    return list;
  }

  @Override
  public Artifact getArtifactForService(String serviceId) {
    Optional<ServiceArtifactElement> artifactElement = getArtifactElement(serviceId);
    if (artifactElement.isPresent()) {
      return artifactService.get(artifactElement.get().getUuid());
    } else {
      WorkflowStandardParams workflowStandardParams = fetchWorkflowStandardParamsFromContext();
      return workflowStandardParamsExtensionService.getArtifactForService(workflowStandardParams, serviceId);
    }
  }

  private List<ServiceHelmElement> getHelmChartElements() {
    List<ServiceHelmElement> helmElements = getHelmChartElementsFromSweepingOutput();
    helmElements.addAll(getHelmChartsElementsContext());
    return helmElements;
  }

  private List<ServiceArtifactElement> getArtifactElements() {
    List<ServiceArtifactElement> artifactElements = getArtifactElementsFromSweepingOutput();
    artifactElements.addAll(getArtifactElementsFromContext());
    return artifactElements;
  }

  private Optional<ServiceArtifactElement> getArtifactElement(String serviceId) {
    List<ServiceArtifactElement> artifactElementsList = getArtifactElements();
    if (isEmpty(artifactElementsList)) {
      return Optional.empty();
    }

    return artifactElementsList.stream()
        .filter(element -> element.getServiceIds() != null && element.getServiceIds().contains(serviceId))
        .findFirst();
  }

  private List<ServiceHelmElement> getHelmChartElementsFromSweepingOutput() {
    SweepingOutputInquiry sweepingOutputInquiry =
        prepareSweepingOutputInquiryBuilder().name(ServiceHelmElements.SWEEPING_OUTPUT_NAME).build();
    if (sweepingOutputInquiry.getPipelineExecutionId() == null) {
      return new ArrayList<>();
    }

    List<ServiceHelmElements> helmElements =
        sweepingOutputService.findSweepingOutputsWithNamePrefix(sweepingOutputInquiry, Scope.PIPELINE);
    if (helmElements == null) {
      return new ArrayList<>();
    }

    return helmElements.stream()
        .flatMap(serviceArtifactElements
            -> serviceArtifactElements.getHelmElements() == null ? Stream.empty()
                                                                 : serviceArtifactElements.getHelmElements().stream())
        .collect(toList());
  }

  private List<ServiceArtifactElement> getArtifactElementsFromSweepingOutput() {
    SweepingOutputInquiry sweepingOutputInquiry =
        prepareSweepingOutputInquiryBuilder().name(ServiceArtifactElements.SWEEPING_OUTPUT_NAME).build();
    if (sweepingOutputInquiry.getPipelineExecutionId() == null) {
      return new ArrayList<>();
    }

    List<ServiceArtifactElements> artifactElementsList =
        sweepingOutputService.findSweepingOutputsWithNamePrefix(sweepingOutputInquiry, Scope.PIPELINE);
    if (artifactElementsList == null) {
      return new ArrayList<>();
    }

    return artifactElementsList.stream()
        .flatMap(serviceArtifactElements
            -> serviceArtifactElements.getArtifactElements() == null
                ? Stream.empty()
                : serviceArtifactElements.getArtifactElements().stream())
        .collect(toList());
  }

  private List<ServiceHelmElement> getHelmChartsElementsContext() {
    List<ContextElement> contextElementList = getContextElementList(ContextElementType.HELM_CHART);
    if (contextElementList == null) {
      return new ArrayList<>();
    }

    return contextElementList.stream().map(element -> (ServiceHelmElement) element).collect(toList());
  }

  private List<ServiceArtifactElement> getArtifactElementsFromContext() {
    List<ContextElement> contextElementList = getContextElementList(ContextElementType.ARTIFACT);
    if (contextElementList == null) {
      return new ArrayList<>();
    }

    return contextElementList.stream().map(element -> (ServiceArtifactElement) element).collect(toList());
  }

  @Override
  public List<ServiceArtifactVariableElement> getArtifactVariableElements() {
    List<ServiceArtifactVariableElement> artifactElements = getArtifactVariableElementsFromSweepingOutput();
    artifactElements.addAll(getArtifactVariableElementsFromContext());
    return artifactElements;
  }

  private List<ServiceArtifactVariableElement> getArtifactVariableElementsFromSweepingOutput() {
    SweepingOutputInquiry sweepingOutputInquiry =
        prepareSweepingOutputInquiryBuilder().name(ServiceArtifactVariableElements.SWEEPING_OUTPUT_NAME).build();
    if (sweepingOutputInquiry.getPipelineExecutionId() == null) {
      return new ArrayList<>();
    }

    List<ServiceArtifactVariableElements> artifactVariableElementsList =
        sweepingOutputService.findSweepingOutputsWithNamePrefix(sweepingOutputInquiry, Scope.PIPELINE);
    if (artifactVariableElementsList == null) {
      return new ArrayList<>();
    }

    return artifactVariableElementsList.stream()
        .flatMap(serviceArtifactVariableElements
            -> serviceArtifactVariableElements.getArtifactVariableElements() == null
                ? Stream.empty()
                : serviceArtifactVariableElements.getArtifactVariableElements().stream())
        .collect(toList());
  }

  private List<ServiceArtifactVariableElement> getArtifactVariableElementsFromContext() {
    List<ContextElement> contextElementList = getContextElementList(ContextElementType.ARTIFACT_VARIABLE);
    if (contextElementList == null) {
      return new ArrayList<>();
    }

    return contextElementList.stream().map(element -> (ServiceArtifactVariableElement) element).collect(toList());
  }

  private Map<String, Artifact> getArtifactMapForPhase() {
    SweepingOutputInquiry sweepingOutputInput = prepareSweepingOutputInquiryBuilder().name("artifacts").build();
    SweepingOutputInstance result = sweepingOutputService.find(sweepingOutputInput);

    if (result == null) {
      return null;
    }
    return (Map<String, Artifact>) kryoSerializer.asInflatedObject(result.getOutput());
  }

  private boolean isArtifactVariableForService(String serviceId, ArtifactVariable artifactVariable) {
    switch (artifactVariable.getEntityType()) {
      case SERVICE:
        return artifactVariable.getEntityId().equals(serviceId);
      case ENVIRONMENT:
        if (isEmpty(artifactVariable.getOverriddenArtifactVariables())) {
          return true;
        }
        for (ArtifactVariable overriddenArtifactVariable : artifactVariable.getOverriddenArtifactVariables()) {
          if (isArtifactVariableForService(serviceId, overriddenArtifactVariable)) {
            return true;
          }
        }
        return false;
      case WORKFLOW:
        return true;
      default:
        return false;
    }
  }

  @Override
  public Map<String, Artifact> getArtifactsForService(String serviceId) {
    WorkflowStandardParams workflowStandardParams = fetchWorkflowStandardParamsFromContext();
    Map<String, Artifact> map = new HashMap<>();
    if (workflowStandardParams.getWorkflowElement() == null) {
      return map;
    }
    List<ArtifactVariable> artifactVariables = workflowStandardParams.getWorkflowElement().getArtifactVariables();
    if (isEmpty(artifactVariables)) {
      return map;
    }

    Map<String, Artifact> artifactMapForPhase = getArtifactMapForPhase();
    if (isEmpty(artifactMapForPhase)) {
      return map;
    }

    for (ArtifactVariable artifactVariable : artifactVariables) {
      if (!isArtifactVariableForService(serviceId, artifactVariable)) {
        continue;
      }

      Artifact artifact = artifactMapForPhase.getOrDefault(artifactVariable.getName(), null);
      // TODO: ASR: throw error if null?
      if (artifact != null) {
        map.put(artifactVariable.getName(), artifact);
      }
    }
    return map;
  }

  @Override
  public Artifact getDefaultArtifactForService(String serviceId) {
    return getArtifactForService(serviceId);
  }

  @Override
  public List<HelmChart> getHelmCharts() {
    WorkflowStandardParams workflowStandardParams = fetchWorkflowStandardParamsFromContext();

    List<ServiceHelmElement> helmElements = getHelmChartElements();
    if (isEmpty(helmElements)) {
      return workflowStandardParamsExtensionService.getHelmCharts(workflowStandardParams);
    }
    List<HelmChart> list = new ArrayList<>();
    for (ServiceHelmElement helmElement : helmElements) {
      list.add(helmChartService.get(getAppId(), helmElement.getUuid()));
    }
    return list;
  }

  @Override
  public HelmChart getHelmChartForService(String serviceId) {
    WorkflowStandardParams workflowStandardParams = fetchWorkflowStandardParamsFromContext();
    return workflowStandardParamsExtensionService.getHelmChartForService(workflowStandardParams, serviceId);
  }

  @Override
  public Application getApp() {
    WorkflowStandardParams stdParam = getContextElement(ContextElementType.STANDARD);
    if (stdParam != null) {
      return workflowStandardParamsExtensionService.getApp(stdParam);
    }
    return null;
  }

  @Override
  public Application fetchRequiredApp() {
    WorkflowStandardParams stdParam = getContextElement(ContextElementType.STANDARD);
    if (stdParam != null) {
      return workflowStandardParamsExtensionService.fetchRequiredApp(stdParam);
    }
    throw new InvalidRequestException("WorkflowStandardParams cannot be null");
  }

  @Override
  public Environment fetchRequiredEnvironment() {
    WorkflowStandardParams stdParam = getContextElement(ContextElementType.STANDARD);
    if (stdParam != null) {
      return workflowStandardParamsExtensionService.fetchRequiredEnv(stdParam);
    }
    throw new InvalidRequestException("WorkflowStandardParams cannot be null");
  }

  public Environment getEnv() {
    WorkflowStandardParams stdParam = getContextElement(ContextElementType.STANDARD);
    if (stdParam != null) {
      return workflowStandardParamsExtensionService.getEnv(stdParam);
    }
    return null;
  }

  @Override
  public ErrorStrategy getErrorStrategy() {
    WorkflowStandardParams stdParam = getContextElement(ContextElementType.STANDARD);
    if (stdParam != null) {
      return stdParam.getErrorStrategy();
    }
    return null;
  }

  /**
   * Gets state machine.
   *
   * @return the state machine
   */
  public StateMachine getStateMachine() {
    return stateMachine;
  }

  /**
   * Gets state execution instance.
   *
   * @return the state execution instance
   */
  public StateExecutionInstance getStateExecutionInstance() {
    return stateExecutionInstance;
  }

  /**
   * Sets state execution instance.
   *
   * @param stateExecutionInstance the state execution instance
   */
  void setStateExecutionInstance(StateExecutionInstance stateExecutionInstance) {
    this.stateExecutionInstance = stateExecutionInstance;
  }

  /**
   * Push context element.
   *
   * @param contextElement the context element
   */
  public void pushContextElement(ContextElement contextElement) {
    stateExecutionInstance.getContextElements().push(contextElement);
  }

  public String renderExpression(String expression, Map<String, Object> context) {
    return evaluator.substitute(
        expression, context, variableResolverTracker, normalizeStateName(stateExecutionInstance.getDisplayName()));
  }

  public String renderExpressionSecured(String expression, Map<String, Object> context) {
    return evaluator.substituteSecured(
        expression, context, variableResolverTracker, normalizeStateName(stateExecutionInstance.getDisplayName()));
  }

  private Object normalizeAndEvaluate(
      String expression, Map<String, Object> context, List<String> defaultObjectPrefixes) {
    if (expression == null) {
      return null;
    }
    List<ExpressionProcessor> expressionProcessors = new ArrayList<>();
    Matcher matcher = ManagerExpressionEvaluator.wingsVariablePattern.matcher(expression);

    StringBuffer sb = new StringBuffer();

    String varPrefix = "VAR_";
    Map<String, String> normalizedExpressionMap = new HashMap<>();

    while (matcher.find()) {
      String variable = matcher.group(0);
      log.debug("wingsVariable found: {}", variable);

      // remove $ and braces(${varName})
      variable = variable.substring(2, variable.length() - 1);

      String topObjectName = variable;
      String topObjectNameSuffix = null;
      int ind = variable.indexOf('.');
      if (ind > 0) {
        String firstPart = variable.substring(0, ind);
        if (!argsCharPattern.matcher(firstPart).find()) {
          topObjectName = normalizeStateName(firstPart);
          topObjectNameSuffix = variable.substring(ind);
          variable = topObjectName + topObjectNameSuffix;
        }
      }

      boolean unknownObject = false;
      if (!context.containsKey(topObjectName)) {
        unknownObject = true;
      }
      if (unknownObject) {
        for (ExpressionProcessor expressionProcessor : expressionProcessors) {
          String newVariable = expressionProcessor.normalizeExpression(variable);
          if (newVariable != null) {
            variable = newVariable;
            unknownObject = false;
            break;
          }
        }
      }
      if (unknownObject) {
        ExpressionProcessor expressionProcessor = expressionProcessorFactory.getExpressionProcessor(variable, this);
        if (expressionProcessor != null) {
          variable = expressionProcessor.normalizeExpression(variable);
          expressionProcessors.add(expressionProcessor);
          unknownObject = false;
        }
      }
      if (unknownObject) {
        for (String defaultObjectPrefix : defaultObjectPrefixes) {
          String normalizedVariable = defaultObjectPrefix + "." + variable;
          try {
            evaluate(normalizedVariable, new HashMap<>(), context, new ArrayList<>());
          } catch (RuntimeException exception) {
            continue;
          }
          variable = normalizedVariable;
          break;
        }
      }

      String varId = varPrefix + random.nextInt(10000);
      while (normalizedExpressionMap.containsKey(varId)) {
        varId = varPrefix + random.nextInt(10000);
      }
      normalizedExpressionMap.put(varId, variable);
      matcher.appendReplacement(sb, varId);
    }
    matcher.appendTail(sb);

    for (ExpressionProcessor expressionProcessor : expressionProcessors) {
      context.put(expressionProcessor.getPrefixObjectName(), expressionProcessor);
    }

    if (isNotEmpty(normalizedExpressionMap)) {
      log.info("The above code seems obsolete, but if you see me in the logs, it is not");
    }

    return evaluate(sb.toString(), normalizedExpressionMap, context, defaultObjectPrefixes);
  }

  private Object evaluate(String expr, Map<String, String> normalizedExpressionMap, Map<String, Object> context,
      List<String> defaultObjectPrefixes) {
    Map<String, Object> evaluatedValueMap = new HashMap<>(context);
    for (Entry<String, String> entry : normalizedExpressionMap.entrySet()) {
      String key = entry.getKey();
      Object val = evaluator.evaluate(normalizedExpressionMap.get(key), context);
      if (val instanceof String) {
        String valStr = (String) val;
        Matcher matcher = ManagerExpressionEvaluator.wingsVariablePattern.matcher(valStr);
        if (matcher.find()) {
          val = normalizeAndEvaluate(valStr, context, defaultObjectPrefixes);
        }
      }
      evaluatedValueMap.put(key, val);
    }

    log.debug("expr: {}, evaluatedValueMap: {}", expr, evaluatedValueMap);
    return evaluator.evaluate(expr, evaluatedValueMap);
  }

  @Override
  public Map<String, Object> asMap() {
    Map<String, Object> context = new LateBindingMap();
    context.putAll(prepareContext(null));
    return context;
  }

  @VisibleForTesting
  String normalizeStateName(String name) {
    Matcher matcher = wildCharPattern.matcher(name);
    return matcher.replaceAll("__");
  }

  protected void prepareVariables(EncryptedFieldMode encryptedFieldMode, ServiceVariable serviceVariable,
      Map<String, Object> variables, boolean adoptDelegateDecryption, int expressionFunctorToken) {
    String variableName = renderExpression(serviceVariable.getName());

    if (!variables.containsKey(variableName) && ServiceVariableType.ARTIFACT != serviceVariable.getType()) {
      if (serviceVariable.getType() == TEXT || encryptedFieldMode == MASKED) {
        variables.put(variableName, renderExpression(new String(serviceVariable.getValue())));
      } else if (ServiceVariableType.ARTIFACT != serviceVariable.getType()) {
        if (isEmpty(serviceVariable.getAccountId())) {
          Application app = getApp();
          notNullCheck("app", app);
          serviceVariable.setAccountId(app.getAccountId());
        }
        variables.put(variableName,
            LateBindingServiceEncryptedVariable.builder()
                .serviceVariable(serviceVariable)
                .adoptDelegateDecryption(adoptDelegateDecryption)
                .expressionFunctorToken(expressionFunctorToken)
                .executionContext(this)
                .managerDecryptionService(managerDecryptionService)
                .secretManager(secretManager)
                .featureFlagService(featureFlagService)
                .build());
      }
    }
  }

  @Override
  public void resetPreparedCache() {
    contextMap = null;
  }

  private Map<String, Object> prepareCacheContext(StateExecutionContext stateExecutionContext) {
    if (contextMap != null) {
      return contextMap;
    }
    contextMap = new LateBindingMap();

    if (stateExecutionInstance != null) {
      contextMap.put(CURRENT_STEP_LITERAL, buildStateInfo(stateExecutionInstance));
    }

    // add state execution data
    stateExecutionInstance.getStateExecutionMap().forEach(
        (key, value) -> contextMap.put(normalizeStateName(key), value));

    // add context params
    Iterator<ContextElement> it = stateExecutionInstance.getContextElements().descendingIterator();
    while (it.hasNext()) {
      ContextElement contextElement = it.next();

      ContextElementParamMapper paramMapper = this.contextElementParamMapperFactory.getParamMapper(contextElement);
      Map<String, Object> map = paramMapper.paramMap(this);
      if (map != null) {
        contextMap.putAll(map);
      }
    }

    final String accountId = getAccountId();
    boolean adoptDelegateDecryption = accountId != null
        && featureFlagService.isEnabled(FeatureName.SPG_ADOPT_DELEGATE_DECRYPTION_ON_SERVICE_VARIABLES, accountId);
    if (stateExecutionContext != null) {
      adoptDelegateDecryption = stateExecutionContext.isAdoptDelegateDecryption();
    }
    PhaseElement phaseElement = getContextElement(ContextElementType.PARAM, PHASE_PARAM);

    if (stateExecutionContext != null && stateExecutionContext.getScriptType() != null) {
      ShellScriptFunctor shellScriptFunctor =
          ShellScriptFunctor.builder().scriptType(stateExecutionContext.getScriptType()).build();
      evaluator.addFunctor("shell", shellScriptFunctor);
    }

    Application app = getApp();
    if (stateExecutionContext != null && app != null) {
      contextMap.put(TerraformPlanExpressionInterface.FUNCTOR_NAME,
          TerraformPlanExpressionFunctor.builder()
              .obtainTfPlanFunction(planName
                  -> sweepingOutputService.findSweepingOutput(
                      prepareSweepingOutputInquiryBuilder().name(planName).build()))
              .expressionFunctorToken(stateExecutionContext.getExpressionFunctorToken())
              .fileService(fileService)
              .build());
    }

    LateBindingServiceVariablesBuilder serviceVariablesBuilder =
        LateBindingServiceVariables.builder()
            .phaseOverrides(phaseElement == null ? null : phaseElement.getVariableOverrides())
            .executionContext(this)
            .managerDecryptionService(managerDecryptionService)
            .secretManager(secretManager)
            .adoptDelegateDecryption(adoptDelegateDecryption)
            .expressionFunctorToken(
                stateExecutionContext == null ? 0 : stateExecutionContext.getExpressionFunctorToken());

    if (phaseElement != null) {
      contextMap.put(SERVICE_VARIABLE, serviceVariablesBuilder.encryptedFieldMode(OBTAIN_VALUE).build());
      contextMap.put(SAFE_DISPLAY_SERVICE_VARIABLE, serviceVariablesBuilder.encryptedFieldMode(MASKED).build());
    }

    LateBindingEnvironmentVariables environmentVariables =
        LateBindingEnvironmentVariables.builder()
            .executionContext(this)
            .serviceVariableService(serviceVariableService)
            .managerDecryptionService(managerDecryptionService)
            .secretManager(secretManager)
            .adoptDelegateDecryption(adoptDelegateDecryption)
            .expressionFunctorToken(
                stateExecutionContext == null ? 0 : stateExecutionContext.getExpressionFunctorToken())
            .build();

    contextMap.put(ENVIRONMENT_VARIABLE, environmentVariables);

    String workflowExecutionId = getWorkflowExecutionId();

    contextMap.putAll(variableProcessor.getVariables(stateExecutionInstance.getContextElements(), workflowExecutionId));

    contextMap.put("context",
        SweepingOutputFunctor.builder()
            .sweepingOutputService(sweepingOutputService)
            .kryoSerializer(kryoSerializer)
            .sweepingOutputInquiryBuilder(prepareSweepingOutputInquiryBuilder())
            .build());

    contextMap.put("harnessShellUtils", SubstitutionFunctor.builder().build());

    if (app != null) {
      Environment env = getEnv();
      contextMap.put("secrets",
          SecretFunctor.builder()
              .managerDecryptionService(managerDecryptionService)
              .secretManager(secretManager)
              .accountId(app.getAccountId())
              .appId(app.getUuid())
              .envId(env != null ? env.getUuid() : null)
              .adoptDelegateDecryption(adoptDelegateDecryption)
              .expressionFunctorToken(
                  stateExecutionContext == null ? 0 : stateExecutionContext.getExpressionFunctorToken())
              .build());
    }
    // For tasks where we need to resolve context output on Manager Side
    if (!adoptDelegateDecryption) {
      contextMap.put("sweepingOutputSecrets",
          SweepingOutputSecretManagerFunctor.builder().simpleEncryption(new SimpleEncryption()).build());
    }

    String infraMappingId = fetchInfraMappingId();
    if (infraMappingId != null) {
      String appId = getAppId();
      InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
      if (infrastructureMapping != null
          && InfrastructureMappingType.RANCHER_KUBERNETES.name().equals(infrastructureMapping.getInfraMappingType())) {
        contextMap.put(RancherK8sClusterProcessor.FILTERED_CLUSTERS_EXPR_PREFIX,
            expressionProcessorFactory.getExpressionProcessor(
                RancherK8sClusterProcessor.EXPRESSION_EQUAL_PATTERN, this));
      }
    }

    return contextMap;
  }

  private Map<String, Object> copyIfNeeded(Map<String, Object> map) {
    return contextMap == map ? new HashMap<>(map) : map;
  }

  private Map<String, Object> prepareContext(StateExecutionContext stateExecutionContext) {
    Map<String, Object> map = prepareCacheContext(stateExecutionContext);

    if (stateExecutionContext == null) {
      return map;
    }

    List<ContextElement> contextElements = stateExecutionContext.getContextElements();
    if (contextElements != null) {
      for (ContextElement contextElement : contextElements) {
        ContextElementParamMapper paramMapper = this.contextElementParamMapperFactory.getParamMapper(contextElement);
        Map<String, Object> paramMap = paramMapper.paramMap(this);

        if (paramMap != null) {
          map = copyIfNeeded(map);
          map.putAll(paramMap);
        }
      }
    }
    StateExecutionData stateExecutionData = stateExecutionContext.getStateExecutionData();
    if (stateExecutionData != null) {
      map = copyIfNeeded(map);
      map.put(normalizeStateName(getStateExecutionInstance().getDisplayName()), stateExecutionData);

      if (isNotEmpty(stateExecutionData.getTemplateVariable())) {
        map = copyIfNeeded(map);
        map.putAll(stateExecutionData.getTemplateVariable());
      }
    }
    if (stateExecutionContext.getArtifact() != null) {
      map = copyIfNeeded(map);
      Application app = getApp();
      if (app != null) {
        addArtifactToContext(artifactStreamService, app.getAccountId(), map, stateExecutionContext.getArtifact(),
            buildSourceService, false);
      }
    }
    if (stateExecutionContext.getArtifactFileName() != null) {
      map.put(ExpressionEvaluator.ARTIFACT_FILE_NAME_VARIABLE, stateExecutionContext.getArtifactFileName());
    }
    return map;
  }

  private StateInfo buildStateInfo(StateExecutionInstance stateExecutionInstance) {
    return StateInfo.builder()
        .name(stateExecutionInstance.getStateName())
        .type(stateExecutionInstance.getStateType())
        .build();
  }

  @Override
  public String getWorkflowExecutionId() {
    return stateExecutionInstance.getExecutionUuid();
  }

  @Override
  public String getWorkflowId() {
    return stateExecutionInstance.getWorkflowId();
  }

  @Override
  public String getWorkflowExecutionName() {
    return stateExecutionInstance.getExecutionName();
  }

  @Override
  public WorkflowType getWorkflowType() {
    return stateExecutionInstance.getExecutionType();
  }

  @Override
  public OrchestrationWorkflowType getOrchestrationWorkflowType() {
    return stateExecutionInstance.getOrchestrationWorkflowType();
  }

  @Override
  public String getStateExecutionInstanceId() {
    return stateExecutionInstance.getUuid();
  }

  @Override
  public String getPipelineStageElementId() {
    return stateExecutionInstance.getPipelineStageElementId();
  }

  @Override
  public int getPipelineStageParallelIndex() {
    return stateExecutionInstance.getPipelineStageParallelIndex();
  }

  @Override
  public String getPipelineStageName() {
    return stateExecutionInstance.getStageName();
  }

  @Override
  public String getAppId() {
    ContextElement contextElement = getContextElement(ContextElementType.STANDARD);
    if (!(contextElement instanceof WorkflowStandardParams)) {
      return null;
    }
    return ((WorkflowStandardParams) contextElement).getAppId();
  }

  @Override
  public String getAccountId() {
    ContextElement contextElement = getContextElement(ContextElementType.STANDARD);
    if (!(contextElement instanceof WorkflowStandardParams)) {
      return null;
    }

    Application app = workflowStandardParamsExtensionService.getApp((WorkflowStandardParams) contextElement);
    notNullCheck("app", app);
    return app.getAccountId();
  }

  @Override
  public String getStateExecutionInstanceName() {
    return stateExecutionInstance.getDisplayName();
  }

  @Override
  public Map<String, Object> getServiceVariables() {
    return getServiceVariables(OBTAIN_VALUE);
  }

  @Override
  public Map<String, String> getSafeDisplayServiceVariables() {
    Map<String, Object> map = getServiceVariables(MASKED);
    return map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getServiceVariables(EncryptedFieldMode encryptedFieldMode) {
    List<ServiceVariable> serviceVariables = prepareServiceVariables(
        encryptedFieldMode == MASKED ? EncryptedFieldComputeMode.MASKED : EncryptedFieldComputeMode.OBTAIN_VALUE);

    Map<String, Object> variables = new HashMap<>();
    if (isNotEmpty(serviceVariables)) {
      serviceVariables.forEach(serviceVariable -> {
        if (ServiceVariableType.ARTIFACT != serviceVariable.getType()) {
          String name = renderExpression(serviceVariable.getName());
          if (serviceVariable.isDecrypted()) {
            variables.put(name, SecretString.builder().value(new String(serviceVariable.getValue())).build());
          } else {
            variables.put(name, renderExpression(new String(serviceVariable.getValue())));
          }
        }
      });
    }

    PhaseElement phaseElement = getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    if (phaseElement != null && isNotEmpty(phaseElement.getVariableOverrides())) {
      variables.putAll(phaseElement.getVariableOverrides()
                           .stream()
                           .filter(variableOverride -> variableOverride.getValue() != null)
                           .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue)));
    }

    if (contextMap != null) {
      String key = encryptedFieldMode == MASKED ? SAFE_DISPLAY_SERVICE_VARIABLE : SERVICE_VARIABLE;
      contextMap.put(key, variables);
    }

    return variables;
  }

  protected List<ServiceVariable> prepareServiceVariables(EncryptedFieldComputeMode encryptedFieldComputeMode) {
    PhaseElement phaseElement = getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    if (phaseElement == null || phaseElement.getServiceElement() == null
        || phaseElement.getServiceElement().getUuid() == null) {
      return null;
    }

    Environment env = getEnv();
    notNullCheck("env", env);
    String envId = env.getUuid();
    Optional<Key<ServiceTemplate>> serviceTemplateKey =
        serviceTemplateService
            .getTemplateRefKeysByService(getAppId(), phaseElement.getServiceElement().getUuid(), envId)
            .stream()
            .findFirst();
    if (!serviceTemplateKey.isPresent()) {
      return null;
    }
    return serviceTemplateService.computeServiceVariables(getAppId(), envId, (String) serviceTemplateKey.get().getId(),
        getWorkflowExecutionId(), encryptedFieldComputeMode);
  }

  @Override
  public SettingValue getGlobalSettingValue(String accountId, String settingId) {
    return settingsService.getSettingValueById(accountId, settingId);
  }

  @Override
  public SweepingOutputInstanceBuilder prepareSweepingOutputBuilder(SweepingOutputInstance.Scope sweepingOutputScope) {
    // Default scope is pipeline
    if (sweepingOutputScope == null) {
      sweepingOutputScope = Scope.PIPELINE;
    }
    WorkflowStandardParams workflowStandardParams = getContextElement(ContextElementType.STANDARD);
    String pipelineExecutionId = workflowStandardParams == null || workflowStandardParams.getWorkflowElement() == null
        ? null
        : workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid();
    String workflowExecutionId = getWorkflowExecutionId();

    PhaseElement phaseElement = getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String phaseExecutionId =
        phaseElement == null ? null : workflowExecutionId + phaseElement.getUuid() + phaseElement.getPhaseName();

    String stateExecutionId = stateExecutionInstance.getUuid();

    SweepingOutputInstanceBuilder sweepingOutputBuilder = SweepingOutputServiceImpl.prepareSweepingOutputBuilder(
        getAppId(), pipelineExecutionId, workflowExecutionId, phaseExecutionId, stateExecutionId, sweepingOutputScope);
    return sweepingOutputBuilder.uuid(generateUuid());
  }

  @Override
  public SweepingOutputInquiryBuilder prepareSweepingOutputInquiryBuilder() {
    SweepingOutputInquiryBuilder builder = SweepingOutputInquiry.builder().appId(getAppId());

    WorkflowStandardParams workflowStandardParams = getContextElement(ContextElementType.STANDARD);
    builder.pipelineExecutionId(workflowStandardParams == null || workflowStandardParams.getWorkflowElement() == null
            ? null
            : workflowStandardParams.getWorkflowElement().getPipelineDeploymentUuid());

    String workflowExecutionId = getWorkflowExecutionId();
    builder.workflowExecutionId(getWorkflowExecutionId());

    PhaseElement phaseElement = getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String phaseExecutionId =
        phaseElement == null ? null : workflowExecutionId + phaseElement.getUuid() + phaseElement.getPhaseName();
    builder.phaseExecutionId(phaseExecutionId);

    builder.stateExecutionId(stateExecutionInstance.getUuid());
    builder.isOnDemandRollback(stateExecutionInstance.getIsOnDemandRollback());
    return builder;
  }

  @VisibleForTesting
  @software.wings.security.annotations.Scope()
  protected void populateNamespaceInInfraMappingElement(
      InfrastructureMapping infrastructureMapping, InfraMappingElementBuilder builder) {
    DeploymentType deploymentType =
        serviceResourceService.getDeploymentType(infrastructureMapping, null, infrastructureMapping.getServiceId());

    if ((DeploymentType.KUBERNETES == deploymentType) || (DeploymentType.HELM == deploymentType)) {
      String namespace = null;

      if (infrastructureMapping instanceof GcpKubernetesInfrastructureMapping) {
        namespace = ((GcpKubernetesInfrastructureMapping) infrastructureMapping).getNamespace();
      } else if (infrastructureMapping instanceof AzureKubernetesInfrastructureMapping) {
        namespace = ((AzureKubernetesInfrastructureMapping) infrastructureMapping).getNamespace();
      } else if (infrastructureMapping instanceof DirectKubernetesInfrastructureMapping) {
        namespace = ((DirectKubernetesInfrastructureMapping) infrastructureMapping).getNamespace();
      } else if (infrastructureMapping instanceof RancherKubernetesInfrastructureMapping) {
        namespace = ((RancherKubernetesInfrastructureMapping) infrastructureMapping).getNamespace();
      } else {
        unhandled(infrastructureMapping.getInfraMappingType());
      }

      if (isBlank(namespace)) {
        namespace = "default";
      }

      builder.kubernetes(Kubernetes.builder()
                             .namespace(namespace)
                             .infraId(getNormalizedInfraMappingIdLabelValue(infrastructureMapping.getUuid()))
                             .build());
    }
  }

  public void populateDeploymentSpecificInfoInInfraMappingElement(
      InfrastructureMapping infrastructureMapping, PhaseElement phaseElement, InfraMappingElementBuilder builder) {
    String infraMappingId = fetchInfraMappingId();
    notNullCheck("infraMappingId", infraMappingId);
    CloudProvider cloudProvider = generateCloudProviderElement(infrastructureMapping);
    builder.cloudProvider(cloudProvider);
    if (DeploymentType.PCF.name().equals(phaseElement.getDeploymentType())) {
      PcfInfrastructureMapping pcfInfrastructureMapping = (PcfInfrastructureMapping) infrastructureMapping;

      String route = isNotEmpty(pcfInfrastructureMapping.getRouteMaps())
          ? pcfInfrastructureMapping.getRouteMaps().get(0)
          : StringUtils.EMPTY;
      String tempRoute = isNotEmpty(pcfInfrastructureMapping.getTempRouteMap())
          ? pcfInfrastructureMapping.getTempRouteMap().get(0)
          : StringUtils.EMPTY;

      builder.pcf(Pcf.builder()
                      .route(route)
                      .tempRoute(tempRoute)
                      .organization(pcfInfrastructureMapping.getOrganization())
                      .space(pcfInfrastructureMapping.getSpace())
                      .cloudProvider(cloudProvider)
                      .build());
    } else if (DeploymentType.HELM.name().equals(phaseElement.getDeploymentType())) {
      HelmReleaseInfoElement helmReleaseInfoElement = sweepingOutputService.findSweepingOutput(
          prepareSweepingOutputInquiryBuilder().name(HelmReleaseInfoElement.SWEEPING_OUTPUT_NAME).build());

      builder.helm(Helm.builder()
                       .releaseName(helmReleaseInfoElement != null ? helmReleaseInfoElement.getReleaseName() : null)
                       .shortId(infraMappingId.substring(0, 7).toLowerCase().replace('-', 'z').replace('_', 'z'))
                       .build());
    } else if (DeploymentType.CUSTOM.name().equals(phaseElement.getDeploymentType())) {
      final CustomDeploymentTypeDTO customDeploymentTypeDTO = customDeploymentTypeService.get(
          infrastructureMapping.getAccountId(), infrastructureMapping.getCustomDeploymentTemplateId(),
          ((CustomInfrastructureMapping) infrastructureMapping).getDeploymentTypeTemplateVersion());
      final Map<String, String> infraVariables = applyOverrides(customDeploymentTypeDTO.getInfraVariables(),
          ((CustomInfrastructureMapping) infrastructureMapping).getInfraVariables());
      builder.custom(InfraMappingElement.Custom.builder().vars(infraVariables).build());
    }
  }

  private Map<String, String> applyOverrides(List<Variable> sourceVariables, List<NameValuePair> overrideVariables) {
    final Map<String, String> overrideValues =
        CollectionUtils.collectionToStream(overrideVariables)
            .collect(HashMap::new, (m, v) -> m.put(v.getName(), v.getValue()), HashMap::putAll);
    // Not using Collectors here because it throws if value is null
    // https://stackoverflow.com/questions/24630963/java-8-nullpointerexception-in-collectors-tomap
    return CollectionUtils.collectionToStream(sourceVariables)
        .map(variable -> overrideVariable(overrideValues, variable))
        .filter(variable -> variable.getValue() != null)
        .collect(Collectors.toMap(Variable::getName, Variable::getValue));
  }

  private Variable overrideVariable(Map<String, String> overrideValues, Variable variable) {
    if (overrideValues.containsKey(variable.getName())) {
      return variable.but().value(overrideValues.get(variable.getName())).build();
    }
    return variable;
  }

  private CloudProvider generateCloudProviderElement(InfrastructureMapping infrastructureMapping) {
    SettingAttribute settingAttribute = settingsService.get(infrastructureMapping.getComputeProviderSettingId());
    if (settingAttribute == null) {
      return null;
    }

    return CloudProvider.builder().name(settingAttribute.getName()).build();
  }

  @Override
  public InfraMappingElement fetchInfraMappingElement() {
    PhaseElement phaseElement = getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    String infraMappingId = fetchInfraMappingId();
    if (infraMappingId == null) {
      return null;
    }
    String appId = getAppId();
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (infrastructureMapping == null) {
      return null;
    }

    InfrastructureDefinition infrastructureDefinition =
        infrastructureDefinitionService.get(appId, infrastructureMapping.getInfrastructureDefinitionId());
    String name = infrastructureDefinition.getName();
    InfraMappingElementBuilder builder =
        InfraMappingElement.builder().name(name).infraId(infrastructureMapping.getUuid());

    populateNamespaceInInfraMappingElement(infrastructureMapping, builder);
    populateDeploymentSpecificInfoInInfraMappingElement(infrastructureMapping, phaseElement, builder);
    return builder.build();
  }

  @Override
  public ServiceElement fetchServiceElement() {
    ServiceElement serviceElement = getContextElement(ContextElementType.SERVICE);
    if (serviceElement != null) {
      return serviceElement;
    }

    ServiceTemplateElement serviceTemplateElement = getContextElement(ContextElementType.SERVICE_TEMPLATE);
    if (serviceTemplateElement != null) {
      return serviceTemplateElement.getServiceElement();
    }

    PhaseElement phaseElement = getContextElement(ContextElementType.PARAM, PHASE_PARAM);
    if (phaseElement != null) {
      return phaseElement.getServiceElement();
    }
    return null;
  }

  @Override
  public AutoLogContext autoLogContext() {
    ImmutableMap.Builder<String, String> context = ImmutableMap.builder();
    if (getAccountId() != null) {
      context.put(AccountLogContext.ID, getAccountId());
    }
    if (getAppId() != null) {
      context.put(AppLogContext.ID, getAppId());
    }
    if (getWorkflowId() != null) {
      context.put(WorkflowLogContext.ID, getWorkflowId());
    }
    if (getWorkflowExecutionId() != null) {
      if (getWorkflowType() == PIPELINE) {
        context.put(PipelineWorkflowExecutionLogContext.ID, getWorkflowExecutionId());
      } else {
        context.put(WorkflowExecutionLogContext.ID, getWorkflowExecutionId());
      }
    }
    if (getStateExecutionInstanceId() != null) {
      context.put(StateExecutionInstanceLogContext.ID, getStateExecutionInstanceId());
    }

    return new AutoLogContext(context.build(), OVERRIDE_NESTS);
  }

  @Override
  public String fetchInfraMappingId() {
    PhaseElement phaseElement = getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);

    if (phaseElement == null) {
      return null;
    }
    SweepingOutputInquiry sweepingOutputInquiry =
        prepareSweepingOutputInquiryBuilder()
            .name(InfrastructureConstants.PHASE_INFRA_MAPPING_KEY_NAME + phaseElement.getUuid())
            .build();
    SweepingOutputInstance sweepingOutputInstance = sweepingOutputService.find(sweepingOutputInquiry);
    return sweepingOutputInstance == null
        ? null
        : ((InfraMappingSweepingOutput) sweepingOutputInstance.getValue()).getInfraMappingId();
  }

  @Override
  public InstanceApiResponse renderExpressionsForInstanceDetails(String expression, boolean newInstancesOnly) {
    List<SweepingOutput> sweepingOutputs = sweepingOutputService.findSweepingOutputsWithNamePrefix(
        prepareSweepingOutputInquiryBuilder().name(InstanceInfoVariables.SWEEPING_OUTPUT_NAME).build(), Scope.PHASE);

    InstanceInfoVariables instanceInfoVariables = getAccumulatedInstanceInfoVariables(sweepingOutputs);
    return renderExpressionFromInstanceInfoVariables(expression, newInstancesOnly, instanceInfoVariables);
  }

  @Override
  public InstanceApiResponse renderExpressionsForInstanceDetailsForWorkflow(
      String expression, boolean newInstancesOnly) {
    List<SweepingOutput> sweepingOutputs = sweepingOutputService.findSweepingOutputsWithNamePrefix(
        prepareSweepingOutputInquiryBuilder().name(InstanceInfoVariables.SWEEPING_OUTPUT_NAME).build(), Scope.WORKFLOW);

    InstanceInfoVariables instanceInfoVariables = getAccumulatedInstanceInfoVariables(sweepingOutputs);
    return renderExpressionFromInstanceInfoVariables(expression, newInstancesOnly, instanceInfoVariables);
  }

  @VisibleForTesting
  InstanceInfoVariables getAccumulatedInstanceInfoVariables(List<SweepingOutput> sweepingOutputs) {
    if (isEmpty(sweepingOutputs)) {
      return InstanceInfoVariables.builder().build();
    }
    if (sweepingOutputs.size() == 1) {
      return (InstanceInfoVariables) sweepingOutputs.get(0);
    }

    Set<String> newHosts = new HashSet<>();
    final List<InstanceInfoVariables> instanceInfoVariables = getAllStateSweepingOutputs(sweepingOutputs);
    final List<InstanceInfoVariables> instanceInfoVariableDeployed = getDeployStateSweepingOutputs(sweepingOutputs);
    AtomicBoolean skipVerification = new AtomicBoolean(true);
    instanceInfoVariableDeployed.forEach(instanceInfoVariable -> {
      newHosts.addAll(instanceInfoVariable.getInstanceDetails()
                          .stream()
                          .filter(InstanceDetails::isNewInstance)
                          .map(InstanceDetails::getHostName)
                          .collect(Collectors.toSet()));

      if (!instanceInfoVariable.isSkipVerification()) {
        skipVerification.set(false);
      }
    });

    InstanceInfoVariables finalInstanceInfo = Iterables.getLast(instanceInfoVariableDeployed);
    Set<String> finalInstances =
        finalInstanceInfo.getInstanceElements().stream().map(InstanceElement::getHostName).collect(Collectors.toSet());
    List<InstanceElement> instanceElements = finalInstanceInfo.getInstanceElements();
    List<InstanceDetails> instanceDetails = finalInstanceInfo.getInstanceDetails();

    instanceDetails.removeIf(instanceDetail -> !finalInstances.contains(instanceDetail.getHostName()));
    instanceElements.removeIf(instanceElement -> !finalInstances.contains(instanceElement.getHostName()));

    instanceDetails.forEach(detail -> {
      if (newHosts.contains(detail.getHostName())) {
        detail.setNewInstance(true);
      }
    });
    instanceElements.forEach(element -> {
      if (newHosts.contains(element.getHostName())) {
        element.setNewInstance(true);
      }
    });

    return InstanceInfoVariables.builder()
        .instanceDetails(instanceDetails)
        .instanceElements(instanceElements)
        .skipVerification(skipVerification.get())
        .newInstanceTrafficPercent(getMostRecentTrafficShiftToNewInstances(instanceInfoVariables))
        .build();
  }

  private Integer getMostRecentTrafficShiftToNewInstances(List<InstanceInfoVariables> instanceInfoVariables) {
    return instanceInfoVariables.stream()
        .map(InstanceInfoVariables::getNewInstanceTrafficPercent)
        .filter(Objects::nonNull)
        .reduce((first, second) -> second)
        .orElse(null);
  }

  private List<InstanceInfoVariables> getAllStateSweepingOutputs(List<SweepingOutput> sweepingOutputs) {
    return sweepingOutputs.stream().map(InstanceInfoVariables.class ::cast).collect(toList());
  }

  private List<InstanceInfoVariables> getDeployStateSweepingOutputs(List<SweepingOutput> sweepingOutputs) {
    return sweepingOutputs.stream()
        .map(InstanceInfoVariables.class ::cast)
        .filter(InstanceInfoVariables::isDeployStateInfo)
        .collect(toList());
  }

  @VisibleForTesting
  InstanceApiResponse renderExpressionFromInstanceInfoVariables(
      String expression, boolean newInstancesOnly, InstanceInfoVariables instanceInfoVariables) {
    List<String> list = new ArrayList<>();
    Map<String, InstanceDetails> hostNameToDetailMap = new HashMap<>();
    Map<String, InstanceElement> hostNameToInstanceElementMap = new HashMap<>();
    if (isNotEmpty(instanceInfoVariables.getInstanceElements())) {
      instanceInfoVariables.getInstanceElements().forEach(
          instanceElement -> hostNameToInstanceElementMap.put(instanceElement.getHostName(), instanceElement));
      instanceInfoVariables.getInstanceDetails().forEach(
          details -> hostNameToDetailMap.put(details.getHostName(), details));
      list.addAll(instanceInfoVariables.getInstanceElements()
                      .stream()
                      .filter(instanceElement -> isEligible(instanceElement.isNewInstance(), newInstancesOnly))
                      .map(instanceElement -> {
                        StateExecutionData stateExecutionData = new StateExecutionData();
                        stateExecutionData.setTemplateVariable(
                            ImmutableMap.<String, Object>builder()
                                .put("instanceDetails", hostNameToDetailMap.get(instanceElement.getHostName()))
                                .build());
                        return renderExpression(expression,
                            StateExecutionContext.builder()
                                .contextElements(Lists.newArrayList(instanceElement))
                                .stateExecutionData(stateExecutionData)
                                .build());
                      })
                      .collect(toList()));
    }

    return InstanceApiResponse.builder()
        .instances(list)
        .newInstanceTrafficPercent(instanceInfoVariables.getNewInstanceTrafficPercent())
        .skipVerification(instanceInfoVariables.isSkipVerification())
        .build();
  }

  @Override
  public String appendStateExecutionId(@Nonnull String str) {
    return join(str, stateExecutionInstance.getUuid());
  }

  public String getEnvType() {
    WorkflowStandardParams workflowStandardParams = getContextElement(ContextElementType.STANDARD);
    return (workflowStandardParams == null
               || workflowStandardParamsExtensionService.getEnv(workflowStandardParams) == null)
        ? null
        : workflowStandardParamsExtensionService.getEnv(workflowStandardParams).getEnvironmentType().name();
  }
  private boolean isEligible(boolean instanceFromNewDeployment, boolean newInstancesOnly) {
    return !newInstancesOnly || instanceFromNewDeployment;
  }

  @Override
  public boolean isLastPhase(boolean rollback) {
    WorkflowStandardParams workflowStandardParams = getContextElement(ContextElementType.STANDARD);
    PhaseElement phaseElement = getContextElement(ContextElementType.PARAM, PhaseElement.PHASE_PARAM);
    if (rollback) {
      return phaseElement.getUuid().equals(workflowStandardParams.getLastRollbackPhaseId());
    } else {
      return phaseElement.getUuid().equals(workflowStandardParams.getLastDeployPhaseId());
    }
  }

  public SweepingOutputFunctor fetchSweepingOutputFunctor() {
    if (isEmpty(contextMap)) {
      return null;
    }

    Object var = contextMap.get("context");

    if (var instanceof SweepingOutputFunctor) {
      return (SweepingOutputFunctor) var;
    }
    return null;
  }
}