/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stateutils.buildstate;

import static io.harness.beans.serializer.RunTimeInputHandler.UNRESOLVED_PARAMETER;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveOSType;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.CODE_BASE_CONNECTOR_REF;
import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.PodCleanupDetails.CLEANUP_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.common.CIExecutionConstants.ACCOUNT_ID_ATTR;
import static io.harness.common.CIExecutionConstants.BUILD_NUMBER_ATTR;
import static io.harness.common.CIExecutionConstants.HARNESS_ACCOUNT_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_BUILD_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_CI_INDIRECT_LOG_UPLOAD_FF;
import static io.harness.common.CIExecutionConstants.HARNESS_EXECUTION_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_LOG_PREFIX_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_ORG_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_PIPELINE_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_PROJECT_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_SERVICE_LOG_KEY_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_STAGE_ID_VARIABLE;
import static io.harness.common.CIExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.common.CIExecutionConstants.LABEL_REGEX;
import static io.harness.common.CIExecutionConstants.LOG_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.CIExecutionConstants.LOG_SERVICE_TOKEN_VARIABLE;
import static io.harness.common.CIExecutionConstants.ORG_ID_ATTR;
import static io.harness.common.CIExecutionConstants.PIPELINE_EXECUTION_ID_ATTR;
import static io.harness.common.CIExecutionConstants.PIPELINE_ID_ATTR;
import static io.harness.common.CIExecutionConstants.POD_MAX_WAIT_UNTIL_READY_SECS;
import static io.harness.common.CIExecutionConstants.PROJECT_ID_ATTR;
import static io.harness.common.CIExecutionConstants.STAGE_ID_ATTR;
import static io.harness.common.CIExecutionConstants.STAGE_NAME_ATTR;
import static io.harness.common.CIExecutionConstants.TI_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.CIExecutionConstants.TI_SERVICE_TOKEN_VARIABLE;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_ENDPOINT_VARIABLE;
import static io.harness.common.STOExecutionConstants.STO_SERVICE_TOKEN_VARIABLE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.KubernetesConvention.getAccountIdentifier;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.environment.K8BuildJobEnvInfo.ConnectorConversionInfo;
import io.harness.beans.environment.pod.PodSetupInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.CodeBaseConnectorRefSweepingOutput;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Capabilities;
import io.harness.beans.yaml.extended.infrastrucutre.k8.SecurityContext;
import io.harness.beans.yaml.extended.infrastrucutre.k8.Toleration;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerCapabilities;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PVCParams;
import io.harness.delegate.beans.ci.pod.PodToleration;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.ff.CIFeatureFlagService;
import io.harness.k8s.model.ImageDetails;
import io.harness.logserviceclient.CILogServiceUtils;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.rbac.PipelineRbacHelper;
import io.harness.pms.sdk.core.data.ExecutionSweepingOutput;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.stateutils.buildstate.providers.InternalContainerParamsProvider;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.tiserviceclient.TIServiceUtils;
import io.harness.util.GithubApiFunctor;
import io.harness.util.GithubApiTokenEvaluator;
import io.harness.util.HarnessImageUtils;
import io.harness.util.LiteEngineSecretEvaluator;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.core.timeout.Timeout;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.jetbrains.annotations.NotNull;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class K8BuildSetupUtils {
  @Inject private SecretUtils secretUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private ConnectorUtils connectorUtils;
  @Inject private InternalContainerParamsProvider internalContainerParamsProvider;
  @Inject private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject CILogServiceUtils logServiceUtils;
  @Inject TIServiceUtils tiServiceUtils;
  @Inject STOServiceUtils stoServiceUtils;
  @Inject CodebaseUtils codebaseUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private PipelineRbacHelper pipelineRbacHelper;
  @Inject private HarnessImageUtils harnessImageUtils;
  private final Duration RETRY_SLEEP_DURATION = Duration.ofSeconds(2);
  private final int MAX_ATTEMPTS = 3;
  private static String RUNTIME_CLASS_NAME = "gvisor";

  public CIK8InitializeTaskParams getCIk8BuildTaskParams(InitializeStepInfo initializeStepInfo, Ambiance ambiance,
      Map<String, String> taskIds, String logPrefix, Map<String, String> stepLogKeys) {
    K8PodDetails k8PodDetails = (K8PodDetails) executionSweepingOutputResolver.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails));

    CIK8InitializeTaskParams initializeTaskParams = null;
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();

    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      initializeTaskParams = buildDirectCIK8InitializeTaskParams(initializeStepInfo, logPrefix, ambiance);
    } else {
      initializeTaskParams = buildHostedCIK8InitializeTaskParams(initializeStepInfo, logPrefix, ambiance);
    }

    return initializeTaskParams;
  }

  private CIK8InitializeTaskParams buildDirectCIK8InitializeTaskParams(
      InitializeStepInfo initializeStepInfo, String logPrefix, Ambiance ambiance) {
    K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) initializeStepInfo.getInfrastructure();

    K8PodDetails k8PodDetails = (K8PodDetails) executionSweepingOutputResolver.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails));

    Map<String, String> annotations = resolveMapParameter(
        "annotations", "K8BuildInfra", "stageSetup", k8sDirectInfraYaml.getSpec().getAnnotations(), false);
    Map<String, String> labels =
        resolveMapParameter("labels", "K8BuildInfra", "stageSetup", k8sDirectInfraYaml.getSpec().getLabels(), false);
    Map<String, String> nodeSelector = resolveMapParameter(
        "nodeSelector", "K8BuildInfra", "stageSetup", k8sDirectInfraYaml.getSpec().getNodeSelector(), false);

    Integer stageRunAsUser = resolveIntegerParameter(k8sDirectInfraYaml.getSpec().getRunAsUser(), null);
    String resolveStringParameter = resolveStringParameter(
        "serviceAccountName", null, "infrastructure", k8sDirectInfraYaml.getSpec().getServiceAccountName(), false);
    String serviceAccountName = null;
    if (resolveStringParameter != null && !resolveStringParameter.equals(UNRESOLVED_PARAMETER)) {
      serviceAccountName = resolveStringParameter;
    }

    SecurityContext ctrSecurityContext = k8sDirectInfraYaml.getSpec().getContainerSecurityContext().getValue();

    String runtime = null;

    List<PodToleration> podTolerations = getPodTolerations(k8sDirectInfraYaml.getSpec().getTolerations());
    String priorityClassName = k8sDirectInfraYaml.getSpec().getPriorityClassName().getValue();
    Boolean automountServiceAccountToken = k8sDirectInfraYaml.getSpec().getAutomountServiceAccountToken().getValue();
    OSType os = resolveOSType(k8sDirectInfraYaml.getSpec().getOs());

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    PodSetupInfo podSetupInfo = getPodSetupInfo((K8BuildJobEnvInfo) initializeStepInfo.getBuildJobEnvInfo());

    CIK8PodParams<CIK8ContainerParams> podParams = getPodParams(ngAccess, k8PodDetails, initializeStepInfo, false,
        logPrefix, ambiance, annotations, labels, stageRunAsUser, serviceAccountName, nodeSelector, podTolerations,
        podSetupInfo.getVolumes(), runtime, k8sDirectInfraYaml.getSpec().getNamespace().getValue(), ctrSecurityContext,
        automountServiceAccountToken, priorityClassName, os);

    log.info("Created pod params for pod name [{}]", podSetupInfo.getName());

    final String clusterName = k8sDirectInfraYaml.getSpec().getConnectorRef().getValue();
    ConnectorDetails k8sConnector = connectorUtils.getConnectorDetails(ngAccess, clusterName);

    return CIK8InitializeTaskParams.builder()
        .k8sConnector(k8sConnector)
        .cik8PodParams(podParams)
        .podMaxWaitUntilReadySecs(getPodWaitUntilReadTimeout(k8sDirectInfraYaml))
        .build();
  }
  private CIK8InitializeTaskParams buildHostedCIK8InitializeTaskParams(
      InitializeStepInfo initializeStepInfo, String logPrefix, Ambiance ambiance) {
    K8PodDetails k8PodDetails = (K8PodDetails) executionSweepingOutputResolver.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails));

    Map<String, String> annotations = null;
    Map<String, String> labels = null;
    Map<String, String> nodeSelector = null;
    Integer stageRunAsUser = null;
    String resolveStringParameter = null;
    String serviceAccountName = null;

    if (resolveStringParameter != null && !resolveStringParameter.equals(UNRESOLVED_PARAMETER)) {
      serviceAccountName = resolveStringParameter;
    }

    List<PodToleration> podTolerations = null;
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);

    String namespace = "account-" + getAccountIdentifier(ngAccess.getAccountIdentifier());

    PodSetupInfo podSetupInfo = getPodSetupInfo((K8BuildJobEnvInfo) initializeStepInfo.getBuildJobEnvInfo());

    CIK8PodParams<CIK8ContainerParams> podParams = getPodParams(ngAccess, k8PodDetails, initializeStepInfo, false,
        logPrefix, ambiance, annotations, labels, stageRunAsUser, serviceAccountName, nodeSelector, podTolerations,
        podSetupInfo.getVolumes(), RUNTIME_CLASS_NAME, namespace, null, null, null, OSType.Linux);

    log.info("Created pod params for pod name [{}]", podSetupInfo.getName());

    final String k8sIdetntifier = "account.Harness_Kubernetes_Cluster";
    ConnectorDetails k8sConnector = connectorUtils.getConnectorDetails(ngAccess, k8sIdetntifier);

    return CIK8InitializeTaskParams.builder()
        .k8sConnector(k8sConnector)
        .cik8PodParams(podParams)
        .podMaxWaitUntilReadySecs(POD_MAX_WAIT_UNTIL_READY_SECS)
        .build();
  }

  private int getPodWaitUntilReadTimeout(K8sDirectInfraYaml k8sDirectInfraYaml) {
    ParameterField<String> timeout = k8sDirectInfraYaml.getSpec().getInitTimeout();

    int podWaitUntilReadyTimeout = POD_MAX_WAIT_UNTIL_READY_SECS;
    if (timeout != null && timeout.fetchFinalValue() != null && isNotEmpty((String) timeout.fetchFinalValue())) {
      long timeoutInMillis = Timeout.fromString((String) timeout.fetchFinalValue()).getTimeoutInMillis();
      podWaitUntilReadyTimeout = (int) (timeoutInMillis / 1000);
    }
    return podWaitUntilReadyTimeout;
  }

  public List<ContainerDefinitionInfo> getCIk8BuildServiceContainers(InitializeStepInfo initializeStepInfo) {
    PodSetupInfo podSetupInfo = getPodSetupInfo((K8BuildJobEnvInfo) initializeStepInfo.getBuildJobEnvInfo());
    return podSetupInfo.getPodSetupParams()
        .getContainerDefinitionInfos()
        .stream()
        .filter(containerDefinitionInfo -> containerDefinitionInfo.getContainerType().equals(CIContainerType.SERVICE))
        .collect(toList());
  }

  public CIK8PodParams<CIK8ContainerParams> getPodParams(NGAccess ngAccess, K8PodDetails k8PodDetails,
      InitializeStepInfo initializeStepInfo, boolean usePVC, String logPrefix, Ambiance ambiance,
      Map<String, String> annotations, Map<String, String> labels, Integer stageRunAsUser, String serviceAccountName,
      Map<String, String> nodeSelector, List<PodToleration> podTolerations, List<PodVolume> podVolumes, String runtime,
      String namespace, SecurityContext securityContext, Boolean automountServiceAccountToken, String priorityClassName,
      OSType os) {
    PodSetupInfo podSetupInfo = getPodSetupInfo((K8BuildJobEnvInfo) initializeStepInfo.getBuildJobEnvInfo());
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }
    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(ngAccess, infrastructure);
    CodeBase ciCodebase = initializeStepInfo.getCiCodebase();
    ConnectorDetails gitConnector =
        codebaseUtils.getGitConnector(ngAccess, ciCodebase, initializeStepInfo.isSkipGitClone());
    Map<String, String> gitEnvVars = codebaseUtils.getGitEnvVariables(gitConnector, ciCodebase);
    Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance);

    List<CIK8ContainerParams> containerParamsList =
        getContainerParamsList(k8PodDetails, podSetupInfo, ngAccess, harnessInternalImageConnector, gitEnvVars,
            runtimeCodebaseVars, initializeStepInfo, securityContext, logPrefix, ambiance, os);

    CIK8ContainerParams setupAddOnContainerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        harnessInternalImageConnector, podSetupInfo.getVolumeToMountPath(), podSetupInfo.getWorkDirPath(),
        getCtrSecurityContext(securityContext, os), ngAccess.getAccountIdentifier(), os);

    // Service identifier usage in host alias requires that service identifier does not have capital letter characters
    // or _. For now, removing host alias usage otherwise pod creation itself fails.
    //
    //    List<HostAliasParams> hostAliasParamsList = new ArrayList<>();
    //    if (podSetupInfo.getServiceIdList() != null) {
    //      hostAliasParamsList.add(
    //          HostAliasParams.builder().ipAddress(LOCALHOST_IP).hostnameList(podSetupInfo.getServiceIdList()).build());
    //    }

    List<PVCParams> pvcParamsList = new ArrayList<>();
    if (usePVC) {
      pvcParamsList = podSetupInfo.getPvcParamsList();
    }

    List<String> containerNames = containerParamsList.stream().map(CIK8ContainerParams::getName).collect(toList());
    containerNames.add(setupAddOnContainerParams.getName());

    consumeSweepingOutput(ambiance,
        PodCleanupDetails.builder()
            .infrastructure(infrastructure)
            .podName(podSetupInfo.getName())
            .cleanUpContainerNames(containerNames)
            .build(),
        CLEANUP_DETAILS);

    consumeSweepingOutput(ambiance,
        K8StageInfraDetails.builder()
            .infrastructure(infrastructure)
            .podName(podSetupInfo.getName())
            .containerNames(containerNames)
            .build(),
        STAGE_INFRA_DETAILS);

    Map<String, String> buildLabels = getBuildLabels(ambiance, k8PodDetails);
    if (isNotEmpty(labels)) {
      buildLabels.putAll(labels);
    }

    return CIK8PodParams.<CIK8ContainerParams>builder()
        .name(podSetupInfo.getName())
        .namespace(namespace)
        .labels(buildLabels)
        .serviceAccountName(serviceAccountName)
        .annotations(annotations)
        .gitConnector(gitConnector)
        .containerParamsList(containerParamsList)
        .pvcParamList(pvcParamsList)
        .initContainerParamsList(singletonList(setupAddOnContainerParams))
        .runAsUser(stageRunAsUser)
        .tolerations(podTolerations)
        .nodeSelector(nodeSelector)
        .volumes(podVolumes)
        .runtime(runtime)
        .automountServiceAccountToken(automountServiceAccountToken)
        .priorityClassName(priorityClassName)
        .build();
  }

  private <T extends ExecutionSweepingOutput> void consumeSweepingOutput(Ambiance ambiance, T value, String key) {
    OptionalSweepingOutput optionalSweepingOutput =
        executionSweepingOutputService.resolveOptional(ambiance, RefObjectUtils.getSweepingOutputRefObject(key));
    if (!optionalSweepingOutput.isFound()) {
      executionSweepingOutputResolver.consume(ambiance, key, value, StepOutcomeGroup.STAGE.name());
    }
  }

  public List<CIK8ContainerParams> getContainerParamsList(K8PodDetails k8PodDetails, PodSetupInfo podSetupInfo,
      NGAccess ngAccess, ConnectorDetails harnessInternalImageConnector, Map<String, String> gitEnvVars,
      Map<String, String> runtimeCodebaseVars, InitializeStepInfo initializeStepInfo, SecurityContext securityContext,
      String logPrefix, Ambiance ambiance, OSType os) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    Map<String, String> logEnvVars = getLogServiceEnvVariables(k8PodDetails, accountId);
    Map<String, String> tiEnvVars = getTIServiceEnvVariables(accountId);
    Map<String, String> stoEnvVars = getSTOServiceEnvVariables(accountId);
    Map<String, String> commonEnvVars = getCommonStepEnvVariables(
        k8PodDetails, gitEnvVars, runtimeCodebaseVars, podSetupInfo.getWorkDirPath(), logPrefix, ambiance);
    Map<String, List<ConnectorConversionInfo>> stepConnectors =
        ((K8BuildJobEnvInfo) initializeStepInfo.getBuildJobEnvInfo()).getStepConnectorRefs();

    LiteEngineSecretEvaluator liteEngineSecretEvaluator =
        LiteEngineSecretEvaluator.builder().secretUtils(secretUtils).build();
    List<SecretVariableDetails> secretVariableDetails =
        liteEngineSecretEvaluator.resolve(initializeStepInfo, ngAccess, ambiance.getExpressionFunctorToken());
    checkSecretAccess(ambiance, secretVariableDetails, accountId, AmbianceUtils.getProjectIdentifier(ambiance),
        AmbianceUtils.getOrgIdentifier(ambiance));

    Map<String, ConnectorDetails> githubApiTokenFunctorConnectors =
        resolveGitAppFunctor(ngAccess, initializeStepInfo, ambiance);
    ContainerSecurityContext ctrSecurityContext = getCtrSecurityContext(securityContext, os);

    CIK8ContainerParams liteEngineContainerParams = createLiteEngineContainerParams(harnessInternalImageConnector,
        k8PodDetails, podSetupInfo.getStageCpuRequest(), podSetupInfo.getStageMemoryRequest(), logEnvVars, tiEnvVars,
        stoEnvVars, podSetupInfo.getVolumeToMountPath(), podSetupInfo.getWorkDirPath(), ctrSecurityContext, logPrefix,
        ambiance);

    List<CIK8ContainerParams> containerParams = new ArrayList<>();
    containerParams.add(liteEngineContainerParams);
    // user input containers with custom entry point
    consumePortDetails(ambiance, podSetupInfo.getPodSetupParams().getContainerDefinitionInfos());
    for (ContainerDefinitionInfo containerDefinitionInfo :
        podSetupInfo.getPodSetupParams().getContainerDefinitionInfos()) {
      CIK8ContainerParams cik8ContainerParams =
          createCIK8ContainerParams(ngAccess, containerDefinitionInfo, harnessInternalImageConnector, commonEnvVars,
              stoEnvVars, stepConnectors, podSetupInfo.getVolumeToMountPath(), podSetupInfo.getWorkDirPath(),
              securityContext, logPrefix, secretVariableDetails, githubApiTokenFunctorConnectors, os);
      containerParams.add(cik8ContainerParams);
    }
    return containerParams;
  }

  private Map<String, ConnectorDetails> resolveGitAppFunctor(
      NGAccess ngAccess, InitializeStepInfo initializeStepInfo, Ambiance ambiance) {
    String codeBaseConnectorRef = null;
    if (initializeStepInfo.getCiCodebase() != null
        && initializeStepInfo.getCiCodebase().getConnectorRef().getValue() != null) {
      codeBaseConnectorRef = initializeStepInfo.getCiCodebase().getConnectorRef().getValue();
      if (isNotEmpty(codeBaseConnectorRef)) {
        consumeSweepingOutput(ambiance,
            CodeBaseConnectorRefSweepingOutput.builder().codeBaseConnectorRef(codeBaseConnectorRef).build(),
            CODE_BASE_CONNECTOR_REF);
      }
    }
    GithubApiTokenEvaluator githubApiTokenEvaluator =
        GithubApiTokenEvaluator.builder()
            .githubApiFunctorConfig(GithubApiFunctor.Config.builder()
                                        .codeBaseConnectorRef(codeBaseConnectorRef)
                                        .fetchConnector(true)
                                        .build())
            .connectorUtils(connectorUtils)
            .build();

    return githubApiTokenEvaluator.resolve(initializeStepInfo, ngAccess, ambiance.getExpressionFunctorToken());
  }

  private void checkSecretAccess(Ambiance ambiance, List<SecretVariableDetails> secretVariableDetails,
      String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    List<EntityDetail> entityDetails =
        secretVariableDetails.stream()
            .map(secretVariableDetail -> {
              return createEntityDetails(secretVariableDetail.getSecretVariableDTO().getSecret().getIdentifier(),
                  accountIdentifier, projectIdentifier, orgIdentifier);
            })
            .collect(Collectors.toList());

    if (isNotEmpty(entityDetails)) {
      pipelineRbacHelper.checkRuntimePermissions(ambiance, entityDetails, false);
    }
  }

  private EntityDetail createEntityDetails(
      String secretIdentifier, String accountIdentifier, String projectIdentifier, String orgIdentifier) {
    IdentifierRef connectorRef =
        IdentifierRefHelper.getIdentifierRef(secretIdentifier, accountIdentifier, orgIdentifier, projectIdentifier);
    return EntityDetail.builder().entityRef(connectorRef).type(EntityType.SECRETS).build();
  }

  private void consumePortDetails(Ambiance ambiance, List<ContainerDefinitionInfo> containerDefinitionInfos) {
    Map<String, List<Integer>> portDetails = containerDefinitionInfos.stream().collect(
        Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getPorts));
    consumeSweepingOutput(ambiance, ContainerPortDetails.builder().portDetails(portDetails).build(), PORT_DETAILS);
  }

  private CIK8ContainerParams createCIK8ContainerParams(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, ConnectorDetails harnessInternalImageConnector,
      Map<String, String> commonEnvVars, Map<String, String> stoEnvVars,
      Map<String, List<ConnectorConversionInfo>> connectorRefs, Map<String, String> volumeToMountPath,
      String workDirPath, SecurityContext securityContext, String logPrefix,
      List<SecretVariableDetails> secretVariableDetails, Map<String, ConnectorDetails> githubApiTokenFunctorConnectors,
      OSType os) {
    Map<String, String> envVars = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getEnvVars())) {
      envVars.putAll(containerDefinitionInfo.getEnvVars()); // Put customer input env variables
    }
    Map<String, ConnectorDetails> stepConnectorDetails = new HashMap<>();
    if (isNotEmpty(containerDefinitionInfo.getStepIdentifier()) && isNotEmpty(connectorRefs)) {
      List<ConnectorConversionInfo> connectorConversionInfos =
          connectorRefs.get(containerDefinitionInfo.getStepIdentifier());
      if (connectorConversionInfos != null && connectorConversionInfos.size() > 0) {
        for (ConnectorConversionInfo connectorConversionInfo : connectorConversionInfos) {
          ConnectorDetails connectorDetails =
              connectorUtils.getConnectorDetailsWithConversionInfo(ngAccess, connectorConversionInfo);
          IdentifierRef identifierRef = IdentifierRefHelper.getIdentifierRef(connectorConversionInfo.getConnectorRef(),
              ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
          stepConnectorDetails.put(identifierRef.getFullyQualifiedName(), connectorDetails);
        }
      }
    }

    ImageDetails imageDetails = containerDefinitionInfo.getContainerImageDetails().getImageDetails();
    ConnectorDetails connectorDetails = null;
    if (containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier() != null) {
      connectorDetails = connectorUtils.getConnectorDetails(
          ngAccess, containerDefinitionInfo.getContainerImageDetails().getConnectorIdentifier());
    }

    ConnectorDetails imgConnector = connectorDetails;
    if (containerDefinitionInfo.isHarnessManagedImage()) {
      imgConnector = harnessInternalImageConnector;
    }
    String fullyQualifiedImageName =
        IntegrationStageUtils.getFullyQualifiedImageName(imageDetails.getName(), imgConnector);
    imageDetails.setName(fullyQualifiedImageName);
    ImageDetailsWithConnector imageDetailsWithConnector =
        ImageDetailsWithConnector.builder().imageConnectorDetails(imgConnector).imageDetails(imageDetails).build();

    List<SecretVariableDetails> containerSecretVariableDetails =
        getSecretVariableDetails(ngAccess, containerDefinitionInfo, secretVariableDetails);

    Map<String, String> envVarsWithSecretRef = removeEnvVarsWithSecretRef(envVars);
    envVars.putAll(commonEnvVars); //  commonEnvVars needs to be put in end because they overrides webhook parameters
    if (containerDefinitionInfo.getContainerType() == CIContainerType.SERVICE) {
      envVars.put(HARNESS_SERVICE_LOG_KEY_VARIABLE,
          format("%s/serviceId:%s", logPrefix, containerDefinitionInfo.getStepIdentifier()));
    }

    ContainerSecurityContext ctrSecurityContext = getCtrSecurityContext(securityContext, os);
    if (containerDefinitionInfo.getPrivileged() != null) {
      ctrSecurityContext.setPrivileged(containerDefinitionInfo.getPrivileged());
    }
    if (containerDefinitionInfo.getRunAsUser() != null) {
      ctrSecurityContext.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }
    boolean privileged = containerDefinitionInfo.getPrivileged() != null && containerDefinitionInfo.getPrivileged();

    CIK8ContainerParams cik8ContainerParams =
        CIK8ContainerParams.builder()
            .name(containerDefinitionInfo.getName())
            .containerResourceParams(containerDefinitionInfo.getContainerResourceParams())
            .containerType(containerDefinitionInfo.getContainerType())
            .envVars(envVars)
            .envVarsWithSecretRef(envVarsWithSecretRef)
            .containerSecrets(ContainerSecrets.builder()
                                  .secretVariableDetails(containerSecretVariableDetails)
                                  .connectorDetailsMap(stepConnectorDetails)
                                  .functorConnectors(githubApiTokenFunctorConnectors)
                                  .plainTextSecretsByName(internalContainerParamsProvider.getLiteEngineSecretVars(
                                      emptyMap(), emptyMap(), stoEnvVars))
                                  .build())
            .commands(containerDefinitionInfo.getCommands())
            .ports(containerDefinitionInfo.getPorts())
            .args(containerDefinitionInfo.getArgs())
            .imageDetailsWithConnector(imageDetailsWithConnector)
            .volumeToMountPath(volumeToMountPath)
            .imagePullPolicy(containerDefinitionInfo.getImagePullPolicy())
            .securityContext(ctrSecurityContext)
            .build();

    if (os != OSType.Windows) {
      cik8ContainerParams.setPrivileged(privileged);
      cik8ContainerParams.setRunAsUser(containerDefinitionInfo.getRunAsUser());
    }

    if (containerDefinitionInfo.getContainerType() != CIContainerType.SERVICE) {
      cik8ContainerParams.setWorkingDir(workDirPath);
    }
    return cik8ContainerParams;
  }

  private Map<String, String> removeEnvVarsWithSecretRef(Map<String, String> envVars) {
    HashMap<String, String> hashMap = new HashMap<>();
    final Map<String, String> secretEnvVariables =
        envVars.entrySet()
            .stream()
            .filter(entry -> entry.getValue().contains("ngSecretManager"))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    envVars.entrySet().removeAll(secretEnvVariables.entrySet());

    return secretEnvVariables;
  }

  private CIK8ContainerParams createLiteEngineContainerParams(ConnectorDetails connectorDetails,
      K8PodDetails k8PodDetails, Integer stageCpuRequest, Integer stageMemoryRequest, Map<String, String> logEnvVars,
      Map<String, String> tiEnvVars, Map<String, String> stoEnvVars, Map<String, String> volumeToMountPath,
      String workDirPath, ContainerSecurityContext ctrSecurityContext, String logPrefix, Ambiance ambiance) {
    Map<String, ConnectorDetails> stepConnectorDetails = new HashMap<>();

    return internalContainerParamsProvider.getLiteEngineContainerParams(connectorDetails, stepConnectorDetails,
        k8PodDetails, stageCpuRequest, stageMemoryRequest, logEnvVars, tiEnvVars, stoEnvVars, volumeToMountPath,
        workDirPath, ctrSecurityContext, logPrefix, ambiance);
  }

  @NotNull
  private PodSetupInfo getPodSetupInfo(K8BuildJobEnvInfo k8BuildJobEnvInfo) {
    // Supporting single pod currently
    Optional<PodSetupInfo> podSetupInfoOpt =
        k8BuildJobEnvInfo.getPodsSetupInfo().getPodSetupInfoList().stream().findFirst();
    if (!podSetupInfoOpt.isPresent()) {
      throw new InvalidRequestException("Pod setup info can not be empty");
    }
    return podSetupInfoOpt.get();
  }

  @NotNull
  private List<SecretVariableDetails> getSecretVariableDetails(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, List<SecretVariableDetails> scriptsSecretVariableDetails) {
    List<SecretVariableDetails> secretVariableDetails = new ArrayList<>();
    secretVariableDetails.addAll(scriptsSecretVariableDetails);
    if (isNotEmpty(containerDefinitionInfo.getSecretVariables())) {
      containerDefinitionInfo.getSecretVariables().forEach(
          secretVariable -> secretVariableDetails.add(secretUtils.getSecretVariableDetails(ngAccess, secretVariable)));
    }
    return secretVariableDetails.stream().filter(Objects::nonNull).collect(Collectors.toList());
  }

  @NotNull
  private Map<String, String> getLogServiceEnvVariables(K8PodDetails k8PodDetails, String accountID) {
    Map<String, String> envVars = new HashMap<>();
    final String logServiceBaseUrl = logServiceUtils.getLogServiceConfig().getBaseUrl();

    RetryPolicy<Object> retryPolicy =
        getRetryPolicy(format("[Retrying failed call to fetch log service token attempt: {}"),
            format("Failed to fetch log service token after retrying {} times"));

    // Make a call to the log service and get back the token
    String logServiceToken =
        Failsafe.with(retryPolicy).get(() -> { return logServiceUtils.getLogServiceToken(accountID); });

    envVars.put(LOG_SERVICE_TOKEN_VARIABLE, logServiceToken);
    envVars.put(LOG_SERVICE_ENDPOINT_VARIABLE, logServiceBaseUrl);

    return envVars;
  }

  @NotNull
  private Map<String, String> getTIServiceEnvVariables(String accountId) {
    Map<String, String> envVars = new HashMap<>();
    final String tiServiceBaseUrl = tiServiceUtils.getTiServiceConfig().getBaseUrl();

    String tiServiceToken = "token";

    // Make a call to the TI service and get back the token. We do not need TI service token for all steps,
    // so we can continue even if the service is down.
    // TODO: (vistaar) Get token only when TI service interaction is required.
    try {
      tiServiceToken = tiServiceUtils.getTIServiceToken(accountId);
    } catch (Exception e) {
      log.error("Could not call token endpoint for TI service", e);
    }

    envVars.put(TI_SERVICE_TOKEN_VARIABLE, tiServiceToken);
    envVars.put(TI_SERVICE_ENDPOINT_VARIABLE, tiServiceBaseUrl);

    return envVars;
  }

  @NotNull
  private Map<String, String> getSTOServiceEnvVariables(String accountId) {
    Map<String, String> envVars = new HashMap<>();
    final String stoServiceBaseUrl = stoServiceUtils.getStoServiceConfig().getBaseUrl();

    String stoServiceToken = "token";

    // Make a call to the STO service and get back the token.
    try {
      stoServiceToken = stoServiceUtils.getSTOServiceToken(accountId);
    } catch (Exception e) {
      log.error("Could not call token endpoint for STO service", e);
    }

    envVars.put(STO_SERVICE_TOKEN_VARIABLE, stoServiceToken);
    envVars.put(STO_SERVICE_ENDPOINT_VARIABLE, stoServiceBaseUrl);

    return envVars;
  }

  @NotNull
  private Map<String, String> getCommonStepEnvVariables(K8PodDetails k8PodDetails, Map<String, String> gitEnvVars,
      Map<String, String> runtimeCodebaseVars, String workDirPath, String logPrefix, Ambiance ambiance) {
    Map<String, String> envVars = new HashMap<>();
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = k8PodDetails.getStageID();
    final String executionID = ambiance.getPlanExecutionId();

    // Add git connector environment variables
    envVars.putAll(gitEnvVars);

    // Add runtime git vars, i.e. manual pull request execution data.
    envVars.putAll(runtimeCodebaseVars);

    // Check whether FF to enable blob upload to log service (as opposed to directly blob storage) is enabled
    if (featureFlagService.isEnabled(FeatureName.CI_INDIRECT_LOG_UPLOAD, accountID)) {
      log.info("Indirect log upload FF is enabled for accountID: {}", accountID);
      envVars.put(HARNESS_CI_INDIRECT_LOG_UPLOAD_FF, "true");
    }

    // Add other environment variables needed in the containers
    envVars.put(HARNESS_WORKSPACE, workDirPath);
    envVars.put(HARNESS_ACCOUNT_ID_VARIABLE, accountID);
    envVars.put(HARNESS_PROJECT_ID_VARIABLE, projectID);
    envVars.put(HARNESS_ORG_ID_VARIABLE, orgID);
    envVars.put(HARNESS_PIPELINE_ID_VARIABLE, pipelineID);
    envVars.put(HARNESS_BUILD_ID_VARIABLE, String.valueOf(buildNumber));
    envVars.put(HARNESS_STAGE_ID_VARIABLE, stageID);
    envVars.put(HARNESS_EXECUTION_ID_VARIABLE, executionID);
    envVars.put(HARNESS_LOG_PREFIX_VARIABLE, logPrefix);
    return envVars;
  }

  private Map<String, String> getBuildLabels(Ambiance ambiance, K8PodDetails k8PodDetails) {
    final String accountID = AmbianceUtils.getAccountId(ambiance);
    final String orgID = AmbianceUtils.getOrgIdentifier(ambiance);
    final String projectID = AmbianceUtils.getProjectIdentifier(ambiance);
    final String pipelineID = ambiance.getMetadata().getPipelineIdentifier();
    final String pipelineExecutionID = ambiance.getPlanExecutionId();
    final int buildNumber = ambiance.getMetadata().getRunSequence();
    final String stageID = k8PodDetails.getStageID();
    final String stageName = k8PodDetails.getStageName();

    Map<String, String> labels = new HashMap<>();
    if (isLabelAllowed(accountID)) {
      labels.put(ACCOUNT_ID_ATTR, accountID);
    }
    if (isLabelAllowed(orgID)) {
      labels.put(ORG_ID_ATTR, orgID);
    }
    if (isLabelAllowed(projectID)) {
      labels.put(PROJECT_ID_ATTR, projectID);
    }
    if (isLabelAllowed(pipelineID)) {
      labels.put(PIPELINE_ID_ATTR, pipelineID);
    }
    if (isLabelAllowed(pipelineExecutionID)) {
      labels.put(PIPELINE_EXECUTION_ID_ATTR, pipelineExecutionID);
    }
    if (isLabelAllowed(stageID)) {
      labels.put(STAGE_ID_ATTR, stageID);
    }
    if (isLabelAllowed(stageName)) {
      labels.put(STAGE_NAME_ATTR, stageName);
    }
    if (isLabelAllowed(String.valueOf(buildNumber))) {
      labels.put(BUILD_NUMBER_ATTR, String.valueOf(buildNumber));
    }
    return labels;
  }

  private boolean isLabelAllowed(String label) {
    if (label == null) {
      return false;
    }

    return label.matches(LABEL_REGEX);
  }

  private List<PodToleration> getPodTolerations(ParameterField<List<Toleration>> parameterizedTolerations) {
    List<PodToleration> podTolerations = new ArrayList<>();
    List<Toleration> tolerations = RunTimeInputHandler.resolveTolerations(parameterizedTolerations);
    if (tolerations == null) {
      return podTolerations;
    }

    for (Toleration toleration : tolerations) {
      String effect = resolveStringParameter("effect", null, "infrastructure", toleration.getEffect(), false);
      String key = resolveStringParameter("key", null, "infrastructure", toleration.getKey(), false);
      String operator = resolveStringParameter("operator", null, "infrastructure", toleration.getOperator(), false);
      String value = resolveStringParameter("value", null, "infrastructure", toleration.getValue(), false);
      Integer tolerationSeconds = resolveIntegerParameter(toleration.getTolerationSeconds(), null);

      validateTolerationEffect(effect);
      validateTolerationOperator(operator);

      podTolerations.add(PodToleration.builder()
                             .effect(effect)
                             .key(key)
                             .operator(operator)
                             .value(value)
                             .tolerationSeconds(tolerationSeconds)
                             .build());
    }
    return podTolerations;
  }

  private void validateTolerationEffect(String effect) {
    if (isNotEmpty(effect)) {
      if (!effect.equals("NoSchedule") && !effect.equals("PreferNoSchedule") && !effect.equals("NoExecute")) {
        throw new CIStageExecutionException(format("Invalid value %s for effect in toleration", effect));
      }
    }
  }

  private void validateTolerationOperator(String operator) {
    if (isNotEmpty(operator)) {
      if (!operator.equals("Equal") && !operator.equals("Exists")) {
        throw new CIStageExecutionException(format("Invalid value %s for operator in toleration", operator));
      }
    }
  }

  private RetryPolicy<Object> getRetryPolicy(String failedAttemptMessage, String failureMessage) {
    return new RetryPolicy<>()
        .handle(Exception.class)
        .withDelay(RETRY_SLEEP_DURATION)
        .withMaxAttempts(MAX_ATTEMPTS)
        .onFailedAttempt(event -> log.info(failedAttemptMessage, event.getAttemptCount(), event.getLastFailure()))
        .onFailure(event -> log.error(failureMessage, event.getAttemptCount(), event.getFailure()));
  }

  private ContainerSecurityContext getCtrSecurityContext(SecurityContext securityContext, OSType os) {
    if (securityContext == null || os == OSType.Windows) {
      return ContainerSecurityContext.builder().build();
    }
    return ContainerSecurityContext.builder()
        .allowPrivilegeEscalation(securityContext.getAllowPrivilegeEscalation().getValue())
        .privileged(securityContext.getPrivileged().getValue())
        .procMount(securityContext.getProcMount().getValue())
        .readOnlyRootFilesystem(securityContext.getReadOnlyRootFilesystem().getValue())
        .runAsNonRoot(securityContext.getRunAsNonRoot().getValue())
        .runAsGroup(securityContext.getRunAsGroup().getValue())
        .runAsUser(resolveIntegerParameter(securityContext.getRunAsUser(), null))
        .capabilities(getCtrCapabilities(securityContext.getCapabilities().getValue()))
        .build();
  }

  private ContainerCapabilities getCtrCapabilities(Capabilities capabilities) {
    if (capabilities == null) {
      return ContainerCapabilities.builder().build();
    }

    return ContainerCapabilities.builder()
        .add(capabilities.getAdd().getValue())
        .drop(capabilities.getDrop().getValue())
        .build();
  }
}
