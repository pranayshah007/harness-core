/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.beans.serializer.RunTimeInputHandler.resolveIntegerParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveListParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveMapParameter;
import static io.harness.beans.serializer.RunTimeInputHandler.resolveStringParameter;
import static io.harness.beans.sweepingoutputs.ContainerPortDetails.PORT_DETAILS;
import static io.harness.beans.sweepingoutputs.PodCleanupDetails.CLEANUP_DETAILS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_SERVICE_LOG_KEY_VARIABLE;
import static io.harness.ci.commonconstants.CIExecutionConstants.POD_MAX_WAIT_UNTIL_READY_SECS;
import static io.harness.ci.commonconstants.CIExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.k8s.KubernetesConvention.getAccountIdentifier;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.beans.environment.ConnectorConversionInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.stages.IntegrationStageNode;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContainerPortDetails;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.PodCleanupDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.cache.Caching;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sHostedInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.buildstate.providers.InternalContainerParamsProvider;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.ci.utils.LiteEngineSecretEvaluator;
import io.harness.ci.utils.PortFinder;
import io.harness.cimanager.stages.IntegrationStageConfigImpl;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8PodParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerSecrets;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.delegate.beans.ci.pod.HostAliasParams;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.task.citasks.cik8handler.params.CIConstants;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.k8s.model.ImageDetails;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.extended.ci.codebase.CodeBase;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class K8InitializeTaskParamsBuilder {
  @Inject private ConnectorUtils connectorUtils;
  @Inject private K8InitializeTaskUtils k8InitializeTaskUtils;
  @Inject private K8InitializeStepUtils k8InitializeStepUtils;
  @Inject private K8InitializeServiceUtils k8InitializeServiceUtils;
  @Inject private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Inject private HarnessImageUtils harnessImageUtils;
  @Inject private InternalContainerParamsProvider internalContainerParamsProvider;
  @Inject private SecretUtils secretUtils;
  @Inject private CILicenseService ciLicenseService;
  @Inject CodebaseUtils codebaseUtils;
  @Inject SSCAServiceUtils sscaServiceUtils;
  @Inject private CIFeatureFlagService featureFlagService;

  private static String RUNTIME_CLASS_NAME = "gvisor";

  public CIK8InitializeTaskParams getK8InitializeTaskParams(
      InitializeStepInfo initializeStepInfo, Ambiance ambiance, String logPrefix) {
    Infrastructure infrastructure = initializeStepInfo.getInfrastructure();
    if (infrastructure == null) {
      throw new CIStageExecutionException("Input infrastructure can not be empty");
    }

    if (infrastructure.getType() != Infrastructure.Type.KUBERNETES_DIRECT
        && infrastructure.getType() != Infrastructure.Type.KUBERNETES_HOSTED) {
      throw new CIStageExecutionException(format("Invalid infrastructure type: %s", infrastructure.getType()));
    }

    K8PodDetails k8PodDetails = (K8PodDetails) executionSweepingOutputResolver.resolve(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails));
    if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
      return buildK8DirectTaskParams(
          initializeStepInfo, k8PodDetails, (K8sDirectInfraYaml) infrastructure, ambiance, logPrefix);
    } else if (infrastructure.getType() == Infrastructure.Type.KUBERNETES_HOSTED) {
      return buildK8HostedTaskParams(
          initializeStepInfo, k8PodDetails, (K8sHostedInfraYaml) infrastructure, ambiance, logPrefix);
    }
    return null;
  }

  private CIK8InitializeTaskParams buildK8HostedTaskParams(InitializeStepInfo initializeStepInfo,
      K8PodDetails k8PodDetails, K8sHostedInfraYaml k8sHostedInfraYaml, Ambiance ambiance, String logPrefix) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String k8Identifier = "account.Harness_Kubernetes_Cluster";
    ConnectorDetails k8sConnector = connectorUtils.getConnectorDetails(ngAccess, k8Identifier);
    return CIK8InitializeTaskParams.builder()
        .k8sConnector(k8sConnector)
        .cik8PodParams(getK8HostedPodParams(initializeStepInfo, k8PodDetails, k8sHostedInfraYaml, ambiance, logPrefix))
        .podMaxWaitUntilReadySecs(POD_MAX_WAIT_UNTIL_READY_SECS)
        .build();
  }

  private CIK8InitializeTaskParams buildK8DirectTaskParams(InitializeStepInfo initializeStepInfo,
      K8PodDetails k8PodDetails, K8sDirectInfraYaml k8sDirectInfraYaml, Ambiance ambiance, String logPrefix) {
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String connectorRef = k8sDirectInfraYaml.getSpec().getConnectorRef().getValue();
    ConnectorDetails k8sConnector = connectorUtils.getConnectorDetails(ngAccess, connectorRef);
    return CIK8InitializeTaskParams.builder()
        .k8sConnector(k8sConnector)
        .cik8PodParams(getK8DirectPodParams(initializeStepInfo, k8PodDetails, k8sDirectInfraYaml, ambiance, logPrefix))
        .podMaxWaitUntilReadySecs(k8InitializeTaskUtils.getPodWaitUntilReadTimeout(k8sDirectInfraYaml))
        .build();
  }

  private CIK8PodParams<CIK8ContainerParams> getK8HostedPodParams(InitializeStepInfo initializeStepInfo,
      K8PodDetails k8PodDetails, K8sHostedInfraYaml k8sHostedInfraYaml, Ambiance ambiance, String logPrefix) {
    String podName = getPodName(ambiance, initializeStepInfo.getStageIdentifier());
    Map<String, String> buildLabels = k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails);
    List<PodVolume> volumes = new ArrayList<>();
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    String namespace = "account-" + getAccountIdentifier(ngAccess.getAccountIdentifier());

    ConnectorDetails gitConnector = codebaseUtils.getGitConnector(
        ngAccess, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone(), ambiance);
    Pair<CIK8ContainerParams, List<CIK8ContainerParams>> podContainers = getStageContainers(
        initializeStepInfo, k8PodDetails, k8sHostedInfraYaml, ambiance, volumes, logPrefix, gitConnector);
    saveSweepingOutput(podName, k8sHostedInfraYaml, podContainers, ambiance);
    return CIK8PodParams.<CIK8ContainerParams>builder()
        .name(podName)
        .namespace(namespace)
        .labels(buildLabels)
        .gitConnector(gitConnector)
        .containerParamsList(podContainers.getRight())
        //.pvcParamList(pvcParamsList)
        .initContainerParamsList(singletonList(podContainers.getLeft()))
        .volumes(volumes)
        .runtime(RUNTIME_CLASS_NAME)
        .activeDeadLineSeconds(
            IntegrationStageUtils.getStageTtl(ciLicenseService, ngAccess.getAccountIdentifier(), k8sHostedInfraYaml))
        .build();
  }

  private CIK8PodParams<CIK8ContainerParams> getK8DirectPodParams(InitializeStepInfo initializeStepInfo,
      K8PodDetails k8PodDetails, K8sDirectInfraYaml k8sDirectInfraYaml, Ambiance ambiance, String logPrefix) {
    String podName = getPodName(ambiance, initializeStepInfo.getStageIdentifier());
    Map<String, String> buildLabels = k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails);

    Map<String, String> annotations = resolveMapParameter(
        "annotations", "K8InitializeStep", "stageSetup", k8sDirectInfraYaml.getSpec().getAnnotations(), false);
    Map<String, String> labels = resolveMapParameter(
        "labels", "K8InitializeStep", "stageSetup", k8sDirectInfraYaml.getSpec().getLabels(), false);
    Map<String, String> nodeSelector = resolveMapParameter(
        "nodeSelector", "K8InitializeStep", "stageSetup", k8sDirectInfraYaml.getSpec().getNodeSelector(), false);
    Integer stageRunAsUser = resolveIntegerParameter(k8sDirectInfraYaml.getSpec().getRunAsUser(), null);
    String serviceAccountName = resolveStringParameter("serviceAccountName", "K8InitializeStep", "stageSetup",
        k8sDirectInfraYaml.getSpec().getServiceAccountName(), false);

    List<String> hostNames = resolveListParameter(
        "hostNames", "K8InitializeStep", "stageSetup", k8sDirectInfraYaml.getSpec().getHostNames(), false);
    List<HostAliasParams> hostAliasParamsList = new ArrayList<>();
    if (isNotEmpty(hostNames)) {
      hostAliasParamsList.add(HostAliasParams.builder().ipAddress("127.0.0.1").hostnameList(hostNames).build());
    }
    if (isNotEmpty(labels)) {
      buildLabels.putAll(labels);
    }

    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    ConnectorDetails gitConnector = codebaseUtils.getGitConnector(
        ngAccess, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone(), ambiance);
    List<PodVolume> volumes = k8InitializeTaskUtils.convertDirectK8Volumes(k8sDirectInfraYaml);
    Pair<CIK8ContainerParams, List<CIK8ContainerParams>> podContainers = getStageContainers(
        initializeStepInfo, k8PodDetails, k8sDirectInfraYaml, ambiance, volumes, logPrefix, gitConnector);
    saveSweepingOutput(podName, k8sDirectInfraYaml, podContainers, ambiance);
    return CIK8PodParams.<CIK8ContainerParams>builder()
        .name(podName)
        .namespace(k8sDirectInfraYaml.getSpec().getNamespace().getValue())
        .labels(buildLabels)
        .serviceAccountName(serviceAccountName)
        .annotations(annotations)
        .nodeSelector(nodeSelector)
        .runAsUser(stageRunAsUser)
        .automountServiceAccountToken(k8sDirectInfraYaml.getSpec().getAutomountServiceAccountToken().getValue())
        .priorityClassName(k8sDirectInfraYaml.getSpec().getPriorityClassName().getValue())
        .tolerations(k8InitializeTaskUtils.getPodTolerations(k8sDirectInfraYaml.getSpec().getTolerations()))
        .gitConnector(gitConnector)
        .containerParamsList(podContainers.getRight())
        //.pvcParamList(pvcParamsList)
        .initContainerParamsList(singletonList(podContainers.getLeft()))
        .activeDeadLineSeconds(CIConstants.POD_MAX_TTL_SECS)
        .volumes(volumes)
        .hostAliasParamsList(hostAliasParamsList)
        .build();
  }

  private Pair<CIK8ContainerParams, List<CIK8ContainerParams>> getStageContainers(InitializeStepInfo initializeStepInfo,
      K8PodDetails k8PodDetails, Infrastructure infrastructure, Ambiance ambiance, List<PodVolume> volumes,
      String logPrefix, ConnectorDetails gitConnector) {
    List<String> sharedPaths = k8InitializeTaskUtils.getSharedPaths(initializeStepInfo);
    Map<String, String> volumeToMountPath = k8InitializeTaskUtils.getVolumeToMountPath(sharedPaths, volumes);
    OSType os = k8InitializeTaskUtils.getOS(infrastructure);
    String accountId = AmbianceUtils.getAccountId(ambiance);
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    CodeBase ciCodebase = initializeStepInfo.getCiCodebase();

    Map<String, String> logEnvVars = k8InitializeTaskUtils.getLogServiceEnvVariables(k8PodDetails, accountId);
    Map<String, String> tiEnvVars = k8InitializeTaskUtils.getTIServiceEnvVariables(accountId);
    Map<String, String> stoEnvVars = k8InitializeTaskUtils.getSTOServiceEnvVariables(accountId);
    Map<String, String> gitEnvVars =
        codebaseUtils.getGitEnvVariables(gitConnector, ciCodebase, initializeStepInfo.isSkipGitClone());
    SecretEnvVars secretEnvVars = getSecretEnvVars(ambiance);
    Map<String, String> runtimeCodebaseVars = codebaseUtils.getRuntimeCodebaseVars(ambiance, gitConnector);
    Map<String, String> commonEnvVars = k8InitializeTaskUtils.getCommonStepEnvVariables(
        k8PodDetails, gitEnvVars, runtimeCodebaseVars, k8InitializeTaskUtils.getWorkDir(), logPrefix, ambiance);

    Caching caching = initializeStepInfo.getStageElementConfig().getCaching();
    if (caching != null && RunTimeInputHandler.resolveBooleanParameter(caching.getEnabled(), false)) {
      Map<String, String> cacheEnvVars = k8InitializeTaskUtils.getCacheEnvironmentVariable();
      commonEnvVars.putAll(cacheEnvVars);
    }

    ConnectorDetails harnessInternalImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(ngAccess, infrastructure);
    Map<String, ConnectorDetails> githubApiTokenFunctorConnectors =
        k8InitializeTaskUtils.resolveGitAppFunctor(ngAccess, initializeStepInfo, ambiance);

    LiteEngineSecretEvaluator liteEngineSecretEvaluator =
        LiteEngineSecretEvaluator.builder().secretUtils(secretUtils).build();
    List<SecretVariableDetails> secretVariableDetails =
        liteEngineSecretEvaluator.resolve(initializeStepInfo, ngAccess, ambiance.getExpressionFunctorToken());
    k8InitializeTaskUtils.checkSecretAccess(ambiance, secretVariableDetails, accountId,
        AmbianceUtils.getProjectIdentifier(ambiance), AmbianceUtils.getOrgIdentifier(ambiance));

    CIK8ContainerParams setupAddOnContainerParams = internalContainerParamsProvider.getSetupAddonContainerParams(
        harnessInternalImageConnector, volumeToMountPath, k8InitializeTaskUtils.getWorkDir(),
        k8InitializeTaskUtils.getCtrSecurityContext(infrastructure), ngAccess.getAccountIdentifier(), os);

    Pair<Integer, Integer> wrapperRequests = k8InitializeStepUtils.getStageRequest(initializeStepInfo, accountId);
    Integer stageCpuRequest = wrapperRequests.getLeft();
    Integer stageMemoryRequest = wrapperRequests.getRight();

    CIK8ContainerParams liteEngineContainerParams = internalContainerParamsProvider.getLiteEngineContainerParams(
        harnessInternalImageConnector, new HashMap<>(), k8PodDetails, stageCpuRequest, stageMemoryRequest, logEnvVars,
        tiEnvVars, stoEnvVars, volumeToMountPath, k8InitializeTaskUtils.getWorkDir(),
        k8InitializeTaskUtils.getCtrSecurityContext(infrastructure), logPrefix, ambiance, secretEnvVars);

    List<CIK8ContainerParams> containerParams = new ArrayList<>();
    containerParams.add(liteEngineContainerParams);

    List<ContainerDefinitionInfo> stageCtrDefinitions =
        getStageContainerDefinitions(initializeStepInfo, infrastructure, ambiance);
    consumePortDetails(ambiance, stageCtrDefinitions);
    Map<String, List<ConnectorConversionInfo>> stepConnectors =
        k8InitializeStepUtils.getStepConnectorRefs(initializeStepInfo.getExecutionElementConfig(), ambiance);
    for (ContainerDefinitionInfo containerDefinitionInfo : stageCtrDefinitions) {
      CIK8ContainerParams cik8ContainerParams = createCIK8ContainerParams(ngAccess, containerDefinitionInfo,
          harnessInternalImageConnector, commonEnvVars, stoEnvVars, stepConnectors, volumeToMountPath,
          k8InitializeTaskUtils.getWorkDir(), k8InitializeTaskUtils.getCtrSecurityContext(infrastructure), logPrefix,
          secretVariableDetails, githubApiTokenFunctorConnectors, os, secretEnvVars);
      containerParams.add(cik8ContainerParams);
    }

    return Pair.of(setupAddOnContainerParams, containerParams);
  }

  private void consumePortDetails(Ambiance ambiance, List<ContainerDefinitionInfo> containerDefinitionInfos) {
    Map<String, List<Integer>> portDetails = containerDefinitionInfos.stream().collect(
        Collectors.toMap(ContainerDefinitionInfo::getStepIdentifier, ContainerDefinitionInfo::getPorts));
    k8InitializeTaskUtils.consumeSweepingOutput(
        ambiance, ContainerPortDetails.builder().portDetails(portDetails).build(), PORT_DETAILS);
  }

  private SecretEnvVars getSecretEnvVars(Ambiance ambiance) {
    String accountId = AmbianceUtils.getAccountId(ambiance);
    if (featureFlagService.isEnabled(FeatureName.SSCA_ENABLED, accountId)) {
      String orgId = AmbianceUtils.getOrgIdentifier(ambiance);
      String projectId = AmbianceUtils.getProjectIdentifier(ambiance);
      Map<String, String> sscaEnvVars = sscaServiceUtils.getSSCAServiceEnvVariables(accountId, orgId, projectId);
      return SecretEnvVars.builder().sscaEnvVars(sscaEnvVars).build();
    }
    return null;
  }

  private CIK8ContainerParams createCIK8ContainerParams(NGAccess ngAccess,
      ContainerDefinitionInfo containerDefinitionInfo, ConnectorDetails harnessInternalImageConnector,
      Map<String, String> commonEnvVars, Map<String, String> stoEnvVars,
      Map<String, List<ConnectorConversionInfo>> connectorRefs, Map<String, String> volumeToMountPath,
      String workDirPath, ContainerSecurityContext ctrSecurityContext, String logPrefix,
      List<SecretVariableDetails> secretVariableDetails, Map<String, ConnectorDetails> githubApiTokenFunctorConnectors,
      OSType os, SecretEnvVars secretEnvVars) {
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
        k8InitializeTaskUtils.getSecretVariableDetails(ngAccess, containerDefinitionInfo, secretVariableDetails);

    Map<String, String> envVarsWithSecretRef = k8InitializeTaskUtils.removeEnvVarsWithSecretRef(envVars);
    envVars.putAll(commonEnvVars); //  commonEnvVars needs to be put in end because they overrides webhook parameters
    if (containerDefinitionInfo.getContainerType() == CIContainerType.SERVICE) {
      envVars.put(HARNESS_SERVICE_LOG_KEY_VARIABLE,
          format("%s/serviceId:%s", logPrefix, containerDefinitionInfo.getStepIdentifier()));
    }

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
                                      emptyMap(), emptyMap(), stoEnvVars, secretEnvVars))
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

  private List<ContainerDefinitionInfo> getStageContainerDefinitions(
      InitializeStepInfo initializeStepInfo, Infrastructure infrastructure, Ambiance ambiance) {
    OSType os = k8InitializeTaskUtils.getOS(infrastructure);
    Set<Integer> usedPorts = new HashSet<>();
    PortFinder portFinder = PortFinder.builder().startingPort(PORT_STARTING_RANGE).usedPorts(usedPorts).build();

    IntegrationStageNode stageNode =
        IntegrationStageNode.builder()
            .type(IntegrationStageNode.StepType.CI)
            .identifier(initializeStepInfo.getStageIdentifier())
            .variables(initializeStepInfo.getVariables())
            .pipelineVariables(initializeStepInfo.getPipelineVariables())
            .integrationStageConfig((IntegrationStageConfigImpl) initializeStepInfo.getStageElementConfig())
            .build();
    StageDetails stageDetails = getStageDetails(ambiance);
    CIExecutionArgs ciExecutionArgs =
        CIExecutionArgs.builder()
            .runSequence(String.valueOf(ambiance.getMetadata().getRunSequence()))
            .executionSource(initializeStepInfo.getExecutionSource() != null ? initializeStepInfo.getExecutionSource()
                                                                             : stageDetails.getExecutionSource())
            .build();
    List<ContainerDefinitionInfo> serviceCtrDefinitionInfos =
        k8InitializeServiceUtils.createServiceContainerDefinitions(stageNode, portFinder, os);
    List<ContainerDefinitionInfo> stepCtrDefinitionInfos =
        k8InitializeStepUtils.createStepContainerDefinitions(initializeStepInfo, stageNode, ciExecutionArgs, portFinder,
            AmbianceUtils.getAccountId(ambiance), os, ambiance, 0);

    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();
    containerDefinitionInfos.addAll(serviceCtrDefinitionInfos);
    containerDefinitionInfos.addAll(stepCtrDefinitionInfos);
    return containerDefinitionInfos;
  }

  private void saveSweepingOutput(String podName, Infrastructure infrastructure,
      Pair<CIK8ContainerParams, List<CIK8ContainerParams>> podContainers, Ambiance ambiance) {
    List<String> containerNames = podContainers.getRight().stream().map(CIK8ContainerParams::getName).collect(toList());
    containerNames.add(podContainers.getLeft().getName());

    k8InitializeTaskUtils.consumeSweepingOutput(ambiance,
        PodCleanupDetails.builder()
            .infrastructure(infrastructure)
            .podName(podName)
            .cleanUpContainerNames(containerNames)
            .build(),
        CLEANUP_DETAILS);

    k8InitializeTaskUtils.consumeSweepingOutput(ambiance,
        K8StageInfraDetails.builder()
            .infrastructure(infrastructure)
            .podName(podName)
            .containerNames(containerNames)
            .build(),
        STAGE_INFRA_DETAILS);
  }

  private String getPodName(Ambiance ambiance, String stageId) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
    if (optionalSweepingOutput.isFound()) {
      StageInfraDetails stageInfraDetails = (StageInfraDetails) optionalSweepingOutput.getOutput();
      StageInfraDetails.Type type = stageInfraDetails.getType();
      if (type == StageInfraDetails.Type.K8) {
        K8StageInfraDetails k8StageInfraDetails = (K8StageInfraDetails) stageInfraDetails;
        return k8StageInfraDetails.getPodName();
      }
    }
    return k8InitializeTaskUtils.generatePodName(stageId);
  }

  private StageDetails getStageDetails(Ambiance ambiance) {
    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails));
    if (!optionalSweepingOutput.isFound()) {
      throw new CIStageExecutionException("Stage details sweeping output cannot be empty");
    }
    return (StageDetails) optionalSweepingOutput.getOutput();
  }
}
