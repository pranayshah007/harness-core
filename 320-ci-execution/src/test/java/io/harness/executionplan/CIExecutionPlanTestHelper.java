/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.executionplan;

import static io.harness.annotations.dev.HarnessTeam.CI;
import static io.harness.common.BuildEnvironmentConstants.DRONE_BUILD_NUMBER;
import static io.harness.common.BuildEnvironmentConstants.DRONE_COMMIT_BRANCH;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_DEPTH_ATTRIBUTE;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_MANUAL_DEPTH;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.common.CIExecutionConstants.GIT_CLONE_STEP_NAME;
import static io.harness.common.CIExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.common.CIExecutionConstants.ID_PREFIX;
import static io.harness.common.CIExecutionConstants.IMAGE_PREFIX;
import static io.harness.common.CIExecutionConstants.PLUGIN_ENV_PREFIX;
import static io.harness.common.CIExecutionConstants.PORT_PREFIX;
import static io.harness.common.CIExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.common.CIExecutionConstants.SERVICE_ARG_COMMAND;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.common.CIExecutionConstants.UNIX_STEP_COMMAND;
import static io.harness.delegate.beans.ci.pod.CIContainerType.PLUGIN;
import static io.harness.delegate.beans.ci.pod.CIContainerType.RUN;
import static io.harness.delegate.beans.ci.pod.CIContainerType.SERVICE;
import static io.harness.pms.yaml.ParameterField.createValueField;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.assertj.core.util.Lists.newArrayList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.dependencies.CIServiceInfo;
import io.harness.beans.dependencies.DependencyElement;
import io.harness.beans.environment.BuildJobEnvInfo;
import io.harness.beans.environment.K8BuildJobEnvInfo;
import io.harness.beans.environment.K8BuildJobEnvInfo.ConnectorConversionInfo;
import io.harness.beans.environment.pod.PodSetupInfo;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.execution.BranchWebhookEvent;
import io.harness.beans.execution.ExecutionSource;
import io.harness.beans.execution.ManualExecutionSource;
import io.harness.beans.execution.PRWebhookEvent;
import io.harness.beans.execution.Repository;
import io.harness.beans.execution.WebhookBaseAttributes;
import io.harness.beans.execution.WebhookEvent;
import io.harness.beans.execution.WebhookExecutionSource;
import io.harness.beans.executionargs.CIExecutionArgs;
import io.harness.beans.script.ScriptInfo;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.stages.IntegrationStageConfigImpl;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.CustomSecretVariable;
import io.harness.beans.yaml.extended.CustomTextVariable;
import io.harness.beans.yaml.extended.CustomVariable;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml.EmptyDirYamlSpec;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml.HostPathYamlSpec;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml.PersistentVolumeClaimYamlSpec;
import io.harness.common.CIExecutionConstants;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams.CIK8ContainerParamsBuilder;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume;
import io.harness.delegate.beans.ci.pod.EnvVariableEnum;
import io.harness.delegate.beans.ci.pod.HostPathVolume;
import io.harness.delegate.beans.ci.pod.ImageDetailsWithConnector;
import io.harness.delegate.beans.ci.pod.PVCParams;
import io.harness.delegate.beans.ci.pod.PVCVolume;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.delegate.beans.ci.pod.SecretVariableDTO;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.docker.DockerAuthType;
import io.harness.delegate.beans.connector.docker.DockerAuthenticationDTO;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.docker.DockerUserNamePasswordDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesAuthType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType;
import io.harness.delegate.beans.connector.k8Connector.KubernetesDelegateDetailsDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesUserNamePasswordDTO;
import io.harness.delegate.beans.connector.scm.GitAuthType;
import io.harness.delegate.beans.connector.scm.GitConnectionType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitConnectorDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsAuthType;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitHttpsCredentialsDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitSecretKeyAccessKeyDTO;
import io.harness.delegate.beans.connector.scm.awscodecommit.AwsCodeCommitUrlType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoConnectorDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.azurerepo.AzureRepoUsernameTokenDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitHTTPAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubAuthenticationDTO;
import io.harness.delegate.beans.connector.scm.github.GithubConnectorDTO;
import io.harness.delegate.beans.connector.scm.github.GithubHttpAuthenticationType;
import io.harness.delegate.beans.connector.scm.github.GithubHttpCredentialsDTO;
import io.harness.delegate.beans.connector.scm.github.GithubUsernamePasswordDTO;
import io.harness.encryption.Scope;
import io.harness.encryption.SecretRefData;
import io.harness.k8s.model.ImageDetails;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.stages.stage.StageElementConfig;
import io.harness.pms.yaml.ParameterField;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.core.variables.StringNGVariable;
import io.harness.yaml.extended.ci.codebase.CodeBase;
import io.harness.yaml.extended.ci.container.Container;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
@OwnedBy(CI)
public class CIExecutionPlanTestHelper {
  private static final String BUILD_STAGE_NAME = "buildStage";
  private static final String ENV_SETUP_NAME = "envSetupName";
  private static final String BUILD_SCRIPT = "mvn clean install";
  private static final long BUILD_NUMBER = 20;
  private static final Integer DEFAULT_LIMIT_MILLI_CPU = 200;
  private static final Integer PVC_DEFAULT_STORAGE_SIZE = 25600;
  private static final Integer DEFAULT_LIMIT_MEMORY_MIB = 200;

  private static final String RUN_STEP_IMAGE = "maven:3.6.3-jdk-8";
  private static final String RUN_STEP_CONNECTOR = "run";
  private static final String RUN_STEP_ID = "step-2";
  private static final String RUN_STEP_NAME = "test script";

  private static final String PLUGIN_STEP_IMAGE = "plugins/git";
  private static final String PLUGIN_STEP_ID = "step-3";
  private static final String PLUGIN_STEP_NAME = "plugin step";

  private static final Integer PLUGIN_STEP_LIMIT_MEM = 50;
  private static final Integer PLUGIN_STEP_LIMIT_CPU = 100;
  private static final String PLUGIN_STEP_LIMIT_MEM_STRING = "50Mi";
  private static final String PLUGIN_STEP_LIMIT_CPU_STRING = "100m";
  private static final String PLUGIN_ENV_VAR = "foo";
  private static final String PLUGIN_ENV_VAL = "bar";

  private static final String SERVICE_ID = "db";
  private static final String SERVICE_CTR_NAME = "service-0";
  private static final String SERVICE_LIMIT_MEM_STRING = "60Mi";
  private static final String SERVICE_LIMIT_CPU_STRING = "80m";
  private static final Integer SERVICE_LIMIT_MEM = 60;
  private static final Integer SERVICE_LIMIT_CPU = 80;
  private static final String SERVICE_IMAGE = "redis";
  private static final String SERVICE_ENTRYPOINT = "redis";
  private static final String SERVICE_ARGS = "start";
  private static final String SERVICE_CONNECTOR_REF = "dockerConnector";

  private static final String REPO_NAMESPACE = "wings";
  private static final String REPO_NAME = "portal";
  private static final String REPO_BRANCH = "master";

  private static final String COMMIT_MESSAGE = "foo=bar";
  private static final String COMMIT_LINK = "foo/bar";
  private static final String COMMIT = "e9a0d31c5ac677ec1e06fb3ab69cd1d2cc62a74a";

  private static final String MOUNT_PATH = "/harness";
  private static final String VOLUME_NAME = "harness";
  private static final String WORK_DIR = "/harness";

  public static final String GIT_CONNECTOR = "git-connector";
  private static final String CLONE_STEP_ID = "step-1";
  private static final String GIT_PLUGIN_DEPTH_ENV = "PLUGIN_DEPTH";
  private static final Integer GIT_STEP_LIMIT_MEM = 200;
  private static final Integer GIT_STEP_LIMIT_CPU = 200;
  private static final String GIT_CLONE_IMAGE = "drone/git";

  private final ImageDetails imageDetails = ImageDetails.builder().name("maven").tag("3.6.3-jdk-8").build();
  private static final String mountPath1 = "/mnt1";
  private static final String mountPath2 = "/mnt2";
  private static final String mountPath3 = "/mnt3";
  private static final String claimName = "test";

  public List<ScriptInfo> getBuildCommandSteps() {
    return singletonList(ScriptInfo.builder().scriptString(BUILD_SCRIPT).build());
  }

  public CodeBase getCICodebase() {
    return CodeBase.builder().connectorRef(ParameterField.createValueField(GIT_CONNECTOR)).build();
  }

  public CodeBase getCICodebaseWithRepoName() {
    return CodeBase.builder()
        .connectorRef(ParameterField.createValueField(GIT_CONNECTOR))
        .repoName(ParameterField.createValueField("portal"))
        .build();
  }

  public ConnectorDetails getGitConnector() {
    ConnectorInfoDTO connectorInfo = getGitConnectorDTO().getConnectorInfo();
    return buildConnector(connectorInfo);
  }

  public ConnectorDetails getGitAccountConnector() {
    ConnectorInfoDTO connectorInfo = getGitConnectorDTOAccountLevel().getConnectorInfo();
    return buildConnector(connectorInfo);
  }

  public ConnectorDetails getGitLabConnector() {
    ConnectorInfoDTO connectorInfo = getGitLabConnectorDTO().getConnectorInfo();
    return buildConnector(connectorInfo);
  }

  public ConnectorDetails getBitBucketConnector() {
    ConnectorInfoDTO connectorInfo = getBitbucketConnectorDTO().getConnectorInfo();
    return buildConnector(connectorInfo);
  }

  private ConnectorDetails buildConnector(ConnectorInfoDTO connectorInfo) {
    return ConnectorDetails.builder()
        .connectorConfig(connectorInfo.getConnectorConfig())
        .connectorType(connectorInfo.getConnectorType())
        .identifier(connectorInfo.getIdentifier())
        .projectIdentifier(connectorInfo.getProjectIdentifier())
        .orgIdentifier(connectorInfo.getOrgIdentifier())
        .build();
  }

  public InitializeStepInfo getExpectedLiteEngineTaskInfoOnFirstPod(
      ExecutionSource executionSource, StageElementConfig stageElementConfig) {
    return InitializeStepInfo.builder()
        .identifier("liteEngineTask")
        .name("liteEngineTask")
        .executionSource(executionSource)
        .stageIdentifier(stageElementConfig.getIdentifier())
        .variables(stageElementConfig.getVariables())
        .stageElementConfig((IntegrationStageConfig) stageElementConfig.getStageType())
        .executionElementConfig(getExecutionElementConfig())
        .ciCodebase(getCICodebase())
        .infrastructure(getInfrastructureWithVolume())
        .timeout(600000)
        .build();
  }

  public InitializeStepInfo getExpectedLiteEngineTaskInfoOnFirstPodWithSetCallbackId() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    return InitializeStepInfo.builder()
        .identifier("liteEngineTask")
        .name("liteEngineTask")
        .buildJobEnvInfo(getCIBuildJobEnvInfoOnFirstPod())
        .accountId("accountId")
        .ciCodebase(getCICodebase())
        .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
        .build();
  }

  public InitializeStepInfo getExpectedLiteEngineTaskInfoOnFirstPodWithSetCallbackIdReponameSet() {
    List<ExecutionWrapperConfig> steps = new ArrayList<>();
    return InitializeStepInfo.builder()
        .identifier("liteEngineTask")
        .name("liteEngineTask")
        .buildJobEnvInfo(getCIBuildJobEnvInfoOnFirstPod())
        .accountId("accountId")
        .ciCodebase(getCICodebaseWithRepoName())
        .executionElementConfig(ExecutionElementConfig.builder().steps(steps).build())
        .build();
  }

  public InitializeStepInfo getExpectedLiteEngineTaskInfoOnOtherPods(
      ExecutionSource executionSource, StageElementConfig stageElementConfig) {
    return InitializeStepInfo.builder()
        .identifier("liteEngineTask")
        .name("liteEngineTask")
        .executionSource(executionSource)
        .stageIdentifier(stageElementConfig.getIdentifier())
        .variables(stageElementConfig.getVariables())
        .stageElementConfig((IntegrationStageConfig) stageElementConfig.getStageType())
        .executionElementConfig(getExecutionElementConfig())
        .infrastructure(getInfrastructureWithVolume())
        .timeout(600000)
        .build();
  }

  public BuildJobEnvInfo getCIBuildJobEnvInfoOnFirstPod() {
    return K8BuildJobEnvInfo.builder()
        .workDir("/harness")
        .podsSetupInfo(getCIPodsSetupInfoOnFirstPod())
        .stepConnectorRefs(emptyMap())
        .build();
  }

  public BuildJobEnvInfo getCIBuildJobEnvInfoOnOtherPods() {
    return K8BuildJobEnvInfo.builder()
        .workDir("/harness")
        .podsSetupInfo(getCIPodsSetupInfoOnOtherPods())
        .stepConnectorRefs(emptyMap())
        .build();
  }

  private Map<String, ConnectorConversionInfo> getStepConnectorConversionInfoMap() {
    Map<String, ConnectorConversionInfo> map = new HashMap<>();
    Map<EnvVariableEnum, String> envToSecretEntry = new HashMap<>();
    envToSecretEntry.put(EnvVariableEnum.GCP_KEY_AS_FILE, "PLUGIN_JSON_KEY");
    map.put("publish-1",
        ConnectorConversionInfo.builder().connectorRef("gcr-connector").envToSecretsMap(envToSecretEntry).build());
    envToSecretEntry.clear();
    envToSecretEntry.put(EnvVariableEnum.AWS_ACCESS_KEY, "PLUGIN_ACCESS_KEY");
    envToSecretEntry.put(EnvVariableEnum.AWS_ACCESS_KEY, "PLUGIN_ACCESS_KEY");
    map.put("publish-2",
        ConnectorConversionInfo.builder().connectorRef("ecr-connector").envToSecretsMap(envToSecretEntry).build());
    return map;
  }

  private List<PodVolume> getVolumes() {
    EmptyDirVolume vol1 = EmptyDirVolume.builder().name("volume-0").mountPath(mountPath1).build();
    HostPathVolume vol2 = HostPathVolume.builder().name("volume-1").mountPath(mountPath2).path(mountPath2).build();
    PVCVolume vol3 = PVCVolume.builder().name("volume-2").claimName(claimName).mountPath(mountPath3).build();
    return Arrays.asList(vol1, vol2, vol3);
  }

  public K8BuildJobEnvInfo.PodsSetupInfo getCIPodsSetupInfoOnFirstPod() {
    List<PodSetupInfo> pods = new ArrayList<>();
    Integer index = 1;
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(VOLUME_NAME, MOUNT_PATH);
    volumeToMountPath.put("shared-0", "share/");
    volumeToMountPath.put("addon", "/addon");
    volumeToMountPath.put("volume-0", mountPath1);
    volumeToMountPath.put("volume-1", mountPath2);
    volumeToMountPath.put("volume-2", mountPath3);
    pods.add(PodSetupInfo.builder()
                 .name("")
                 .pvcParamsList(Arrays.asList(PVCParams.builder()
                                                  .volumeName("volume-0")
                                                  .claimName("")
                                                  .isPresent(false)
                                                  .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                                                  .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                                                  .build(),
                     PVCParams.builder()
                         .volumeName("volume-2")
                         .claimName("")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build(),
                     PVCParams.builder()
                         .volumeName("shared-0")
                         .claimName("")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build(),
                     PVCParams.builder()
                         .volumeName("volume-1")
                         .claimName("")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build(),
                     PVCParams.builder()
                         .volumeName("addon")
                         .claimName("pod-addon")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build(),
                     PVCParams.builder()
                         .volumeName("harness")
                         .claimName("pod-harness")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build()))
                 .podSetupParams(
                     PodSetupInfo.PodSetupParams.builder()
                         .containerDefinitionInfos(asList(getServiceContainer(), getGitPluginStepContainer(index),
                             getRunStepContainer(index + 1), getPluginStepContainer(index + 2)))
                         .build())
                 .stageMemoryRequest(250)
                 .stageCpuRequest(300)
                 .serviceIdList(Collections.singletonList(SERVICE_ID))
                 .serviceGrpcPortList(Collections.singletonList(PORT_STARTING_RANGE))
                 .volumeToMountPath(volumeToMountPath)
                 .workDirPath(WORK_DIR)
                 .volumes(getVolumes())
                 .build());
    return K8BuildJobEnvInfo.PodsSetupInfo.builder().podSetupInfoList(pods).build();
  }

  public K8BuildJobEnvInfo.PodsSetupInfo getCIPodsSetupInfoOnOtherPods() {
    List<PodSetupInfo> pods = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<Integer> serviceGrpcPortList = new ArrayList<>();
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(VOLUME_NAME, MOUNT_PATH);
    volumeToMountPath.put("shared-0", "share/");
    volumeToMountPath.put("addon", "/addon");
    volumeToMountPath.put("volume-0", mountPath1);
    volumeToMountPath.put("volume-1", mountPath2);
    volumeToMountPath.put("volume-2", mountPath3);
    Integer index = 1;
    pods.add(PodSetupInfo.builder()
                 .name("")
                 .pvcParamsList(Arrays.asList(PVCParams.builder()
                                                  .volumeName("volume-0")
                                                  .claimName("")
                                                  .isPresent(false)
                                                  .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                                                  .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                                                  .build(),
                     PVCParams.builder()
                         .volumeName("volume-2")
                         .claimName("")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build(),
                     PVCParams.builder()
                         .volumeName("shared-0")
                         .claimName("pod-2-shared-0")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build(),
                     PVCParams.builder()
                         .volumeName("volume-1")
                         .claimName("")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build(),
                     PVCParams.builder()
                         .volumeName("addon")
                         .claimName("pod-2-addon")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build(),
                     PVCParams.builder()
                         .volumeName("harness")
                         .claimName("pod-2-harness")
                         .isPresent(false)
                         .sizeMib(PVC_DEFAULT_STORAGE_SIZE)
                         .storageClass(CIExecutionConstants.PVC_DEFAULT_STORAGE_CLASS)
                         .build()))
                 .podSetupParams(
                     PodSetupInfo.PodSetupParams.builder()
                         .containerDefinitionInfos(asList(getServiceContainer(), getGitPluginStepContainer(index),
                             getRunStepContainer(index + 1), getPluginStepContainer(index + 2)))
                         .build())
                 .stageMemoryRequest(250)
                 .stageCpuRequest(300)
                 .serviceIdList(serviceIds)
                 .serviceGrpcPortList(serviceGrpcPortList)
                 .serviceIdList(Collections.singletonList(SERVICE_ID))
                 .serviceGrpcPortList(Collections.singletonList(PORT_STARTING_RANGE))
                 .volumeToMountPath(volumeToMountPath)
                 .workDirPath(WORK_DIR)
                 .volumes(getVolumes())
                 .build());
    return K8BuildJobEnvInfo.PodsSetupInfo.builder().podSetupInfoList(pods).build();
  }

  public ExecutionElementConfig getExecutionElementConfig() {
    List<ExecutionWrapperConfig> executionSectionList = getExecutionWrapperConfigList();
    return ExecutionElementConfig.builder().steps(executionSectionList).build();
  }

  public List<ExecutionWrapperConfig> getExecutionWrapperConfigList() {
    return newArrayList(ExecutionWrapperConfig.builder().step(getGitCloneStepElementConfigAsJsonNode()).build(),
        ExecutionWrapperConfig.builder().parallel(getRunAndPluginStepsInParallelAsJsonNode()).build());
  }

  private DependencyElement getServiceDependencyElement() {
    return DependencyElement.builder()
        .identifier(SERVICE_ID)
        .dependencySpecType(CIServiceInfo.builder()
                                .identifier(SERVICE_ID)
                                .grpcPort(20002)
                                .args(createValueField(Collections.singletonList(SERVICE_ARGS)))
                                .entrypoint(createValueField(Collections.singletonList(SERVICE_ENTRYPOINT)))
                                .image(createValueField(SERVICE_IMAGE))
                                .connectorRef(createValueField(SERVICE_CONNECTOR_REF))
                                .resources(ContainerResource.builder()
                                               .limits(ContainerResource.Limits.builder()
                                                           .cpu(createValueField(SERVICE_LIMIT_CPU_STRING))
                                                           .memory(createValueField(SERVICE_LIMIT_MEM_STRING))
                                                           .build())
                                               .build())
                                .build())
        .build();
  }

  // CI Containers

  private ContainerDefinitionInfo getServiceContainer() {
    Integer port = PORT_STARTING_RANGE;
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(VOLUME_NAME, MOUNT_PATH);

    List<String> args = Arrays.asList(
        SERVICE_ARG_COMMAND, ID_PREFIX, SERVICE_ID, IMAGE_PREFIX, SERVICE_IMAGE, PORT_PREFIX, port.toString());

    return ContainerDefinitionInfo.builder()
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(ImageDetails.builder().name(SERVICE_IMAGE).tag("").build())
                                   .connectorIdentifier("dockerConnector")
                                   .build())
        .name(SERVICE_CTR_NAME)
        .envVars(ImmutableMap.of("HARNESS_SERVICE_ENTRYPOINT", "redis", "HARNESS_SERVICE_ARGS", "start"))
        .containerType(SERVICE)
        .args(args)
        .commands(asList(UNIX_STEP_COMMAND))
        .ports(asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(SERVICE_LIMIT_CPU)
                                     .resourceRequestMemoryMiB(SERVICE_LIMIT_MEM)
                                     .resourceLimitMilliCpu(SERVICE_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(SERVICE_LIMIT_MEM)
                                     .build())
        .stepIdentifier(SERVICE_ID)
        .privileged(false)
        .build();
  }

  public CIK8ContainerParams getServiceCIK8Container() {
    Integer port = PORT_STARTING_RANGE;
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(VOLUME_NAME, MOUNT_PATH);

    List<String> args = Arrays.asList(
        SERVICE_ARG_COMMAND, ID_PREFIX, SERVICE_ID, IMAGE_PREFIX, SERVICE_IMAGE, PORT_PREFIX, port.toString());

    return CIK8ContainerParams.builder()
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                       .imageDetails(ImageDetails.builder().name(SERVICE_IMAGE).tag("").build())
                                       .build())
        .name(SERVICE_CTR_NAME)
        .containerType(SERVICE)
        .args(args)
        .commands(asList(UNIX_STEP_COMMAND))
        .ports(asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(SERVICE_LIMIT_CPU)
                                     .resourceRequestMemoryMiB(SERVICE_LIMIT_MEM)
                                     .resourceLimitMilliCpu(SERVICE_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(SERVICE_LIMIT_MEM)
                                     .build())
        .workingDir(WORK_DIR)
        .volumeToMountPath(volumeToMountPath)
        .build();
  }

  private ContainerDefinitionInfo getRunStepContainer(Integer index) {
    Integer port = PORT_STARTING_RANGE + index;
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(VOLUME_NAME, MOUNT_PATH);
    return ContainerDefinitionInfo.builder()
        .containerImageDetails(
            ContainerImageDetails.builder().connectorIdentifier(RUN_STEP_CONNECTOR).imageDetails(imageDetails).build())
        .name("step-" + index.toString())
        .containerType(RUN)
        .args(Arrays.asList(PORT_PREFIX, port.toString()))
        .commands(Arrays.asList(UNIX_STEP_COMMAND))
        .ports(Arrays.asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(DEFAULT_LIMIT_MILLI_CPU)
                                     .resourceLimitMemoryMiB(DEFAULT_LIMIT_MEMORY_MIB)
                                     .build())
        .envVars(getEnvVariables(false))
        .secretVariables(new ArrayList<>())
        .stepIdentifier(RUN_STEP_ID)
        .stepName(RUN_STEP_NAME)
        .build();
  }

  public CIK8ContainerParamsBuilder getRunStepCIK8Container() {
    Integer port = PORT_STARTING_RANGE + 2;
    return CIK8ContainerParams.builder()
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                       .imageDetails(imageDetails)
                                       .imageConnectorDetails(ConnectorDetails.builder().build())
                                       .build())
        .name(RUN_STEP_ID)
        .containerType(RUN)
        .args(asList(PORT_PREFIX, port.toString()))
        .commands(asList(UNIX_STEP_COMMAND))
        .ports(asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(DEFAULT_LIMIT_MILLI_CPU)
                                     .resourceLimitMemoryMiB(DEFAULT_LIMIT_MEMORY_MIB)
                                     .build())
        .envVars(getEnvVariables(true));
  }

  private ContainerDefinitionInfo getPluginStepContainer(Integer index) {
    Map<String, String> envVar = new HashMap<>();
    envVar.putAll(getEnvVariables(false));

    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(VOLUME_NAME, MOUNT_PATH);
    Integer port = PORT_STARTING_RANGE + index;
    return ContainerDefinitionInfo.builder()
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(ImageDetails.builder().name(PLUGIN_STEP_IMAGE).tag("").build())
                                   .build())
        .name("step-" + index.toString())
        .containerType(PLUGIN)
        .args(Arrays.asList(PORT_PREFIX, port.toString()))
        .commands(Arrays.asList(UNIX_STEP_COMMAND))
        .ports(Arrays.asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(PLUGIN_STEP_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(PLUGIN_STEP_LIMIT_MEM)
                                     .build())
        .envVars(envVar)
        .secretVariables(new ArrayList<>())
        .stepIdentifier(PLUGIN_STEP_ID)
        .stepName(PLUGIN_STEP_NAME)
        .build();
  }

  private ContainerDefinitionInfo getGitPluginStepContainer(Integer index) {
    Map<String, String> envVar = new HashMap<>();
    envVar.putAll(getEnvVariables(false));
    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(VOLUME_NAME, MOUNT_PATH);
    Integer port = PORT_STARTING_RANGE + index;
    return ContainerDefinitionInfo.builder()
        .containerImageDetails(ContainerImageDetails.builder()
                                   .imageDetails(ImageDetails.builder().name(GIT_CLONE_IMAGE).tag("").build())
                                   .build())
        .name("step-" + index.toString())
        .containerType(PLUGIN)
        .args(Arrays.asList(PORT_PREFIX, port.toString()))
        .commands(Arrays.asList(UNIX_STEP_COMMAND))
        .ports(Arrays.asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(GIT_STEP_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(GIT_STEP_LIMIT_MEM)
                                     .build())
        .envVars(envVar)
        .secretVariables(new ArrayList<>())
        .stepName(GIT_CLONE_STEP_NAME)
        .stepIdentifier(GIT_CLONE_STEP_ID)
        .build();
  }

  public CIK8ContainerParamsBuilder getGitCloneStepCIK8Container() {
    Map<String, String> envVar = new HashMap<>();
    envVar.put(PLUGIN_ENV_PREFIX + PLUGIN_ENV_VAR.toUpperCase(), PLUGIN_ENV_VAL);
    envVar.put(DRONE_BUILD_NUMBER, Long.toString(BUILD_NUMBER));
    envVar.put(HARNESS_WORKSPACE, WORK_DIR);
    envVar.put(DRONE_COMMIT_BRANCH, REPO_BRANCH);

    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(VOLUME_NAME, MOUNT_PATH);
    Integer port = PORT_STARTING_RANGE + 1;
    return CIK8ContainerParams.builder()
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                       .imageDetails(ImageDetails.builder().name(GIT_CLONE_IMAGE).tag("").build())
                                       .build())
        .name(CLONE_STEP_ID)
        .containerType(PLUGIN)
        .args(asList(PORT_PREFIX, port.toString()))
        .commands(asList(UNIX_STEP_COMMAND))
        .ports(asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(DEFAULT_LIMIT_MILLI_CPU)
                                     .resourceLimitMemoryMiB(DEFAULT_LIMIT_MEMORY_MIB)
                                     .build())
        .workingDir(WORK_DIR)
        .volumeToMountPath(volumeToMountPath)
        .envVars(envVar)
        .secretEnvVars(Collections.emptyMap());
  }

  public CIK8ContainerParamsBuilder getPluginStepCIK8Container() {
    Map<String, String> envVar = new HashMap<>();
    envVar.put(PLUGIN_ENV_PREFIX + PLUGIN_ENV_VAR.toUpperCase(), PLUGIN_ENV_VAL);
    envVar.put(DRONE_BUILD_NUMBER, Long.toString(BUILD_NUMBER));
    envVar.put(HARNESS_WORKSPACE, WORK_DIR);
    envVar.put(DRONE_COMMIT_BRANCH, REPO_BRANCH);

    Map<String, String> volumeToMountPath = new HashMap<>();
    volumeToMountPath.put(VOLUME_NAME, MOUNT_PATH);
    Integer port = PORT_STARTING_RANGE + 3;
    return CIK8ContainerParams.builder()
        .imageDetailsWithConnector(ImageDetailsWithConnector.builder()
                                       .imageDetails(ImageDetails.builder().name(PLUGIN_STEP_IMAGE).tag("").build())
                                       .build())
        .name(PLUGIN_STEP_ID)
        .containerType(PLUGIN)
        .args(asList(PORT_PREFIX, port.toString()))
        .commands(asList(UNIX_STEP_COMMAND))
        .ports(asList(port))
        .containerResourceParams(ContainerResourceParams.builder()
                                     .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
                                     .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
                                     .resourceLimitMilliCpu(PLUGIN_STEP_LIMIT_CPU)
                                     .resourceLimitMemoryMiB(PLUGIN_STEP_LIMIT_MEM)
                                     .build())
        .workingDir(WORK_DIR)
        .volumeToMountPath(volumeToMountPath)
        .envVars(envVar);
  }

  // CI STEPS

  public JsonNode getGitCloneStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", GIT_CLONE_STEP_ID);

    stepElementConfig.put("type", "Plugin");
    stepElementConfig.put("name", GIT_CLONE_STEP_NAME);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", GIT_CLONE_STEP_ID);
    stepSpecType.put("name", GIT_CLONE_STEP_NAME);
    stepSpecType.put("image", GIT_CLONE_IMAGE);

    ObjectNode settings = mapper.createObjectNode();

    settings.put(GIT_CLONE_DEPTH_ATTRIBUTE, GIT_CLONE_MANUAL_DEPTH.toString());
    stepSpecType.set("settings", settings);

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private JsonNode getRunStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", RUN_STEP_ID);

    stepElementConfig.put("type", "Run");
    stepElementConfig.put("name", RUN_STEP_NAME);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", RUN_STEP_ID);
    stepSpecType.put("name", RUN_STEP_NAME);
    stepSpecType.put("command", "./test-script1.sh");
    stepSpecType.put("image", RUN_STEP_IMAGE);
    stepSpecType.put("connectorRef", RUN_STEP_CONNECTOR);

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private JsonNode getPluginStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", PLUGIN_STEP_ID);

    stepElementConfig.put("type", "Plugin");
    stepElementConfig.put("name", PLUGIN_STEP_NAME);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", PLUGIN_STEP_ID);
    stepSpecType.put("name", PLUGIN_STEP_NAME);
    stepSpecType.put("image", PLUGIN_STEP_IMAGE);
    stepSpecType.set("resources",
        mapper.createObjectNode().set("limits",
            mapper.createObjectNode()
                .put("cpu", PLUGIN_STEP_LIMIT_CPU_STRING)
                .put("memory", PLUGIN_STEP_LIMIT_MEM_STRING)));
    stepSpecType.set("settings", mapper.createObjectNode().put(PLUGIN_ENV_VAR, PLUGIN_ENV_VAL));

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private JsonNode getECRStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "publish-2");
    stepElementConfig.put("type", "BuildAndPushECR");

    ArrayNode tags = mapper.createArrayNode();
    tags.add("v01");

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("connectorRef", "ecr-connector");
    stepSpecType.put("region", "987923132879");
    stepSpecType.put("account", "./test-script1.sh");
    stepSpecType.set("tags", tags);
    stepSpecType.put("imageName", "ci-play/portal");
    stepSpecType.put("context", "~/");
    stepSpecType.put("dockerfile", "~/Dockerfile");

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private JsonNode getGCRStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", "publish-1");
    stepElementConfig.put("type", "BuildAndPushGCR");

    ArrayNode tags = mapper.createArrayNode();
    tags.add("v01");

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("connectorRef", "gcr-connector");
    stepSpecType.set("tags", tags);
    stepSpecType.put("imageName", "portal");
    stepSpecType.put("host", "us.gcr.io");
    stepSpecType.put("projectID", "ci-play");
    stepSpecType.put("context", "~/");
    stepSpecType.put("dockerfile", "~/Dockerfile");

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  private JsonNode getECRAndGcrStepsInParallelAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(mapper.createObjectNode().set("step", getECRStepElementConfigAsJsonNode()));
    arrayNode.add(mapper.createObjectNode().set("step", getGCRStepElementConfigAsJsonNode()));

    return arrayNode;
  }

  private JsonNode getRunAndPluginStepsInParallelAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode arrayNode = mapper.createArrayNode();
    arrayNode.add(mapper.createObjectNode().set("step", getRunStepElementConfigAsJsonNode()));
    arrayNode.add(mapper.createObjectNode().set("step", getPluginStepElementConfigAsJsonNode()));

    return arrayNode;
  }

  public Set<String> getPublishArtifactStepIds() {
    Set<String> ids = new HashSet<>();
    ids.add("publish-1");
    ids.add("publish-2");
    return ids;
  }

  public Container getContainer() {
    return Container.builder()
        .connector("testConnector")
        .identifier("testContainer")
        .imagePath("maven:3.6.3-jdk-8")
        .resources(Container.Resources.builder()
                       .limit(Container.Limit.builder().cpu(1000).memory(1000).build())
                       .reserve(Container.Reserve.builder().cpu(1000).memory(1000).build())
                       .build())
        .build();
  }

  public Infrastructure getInfrastructure() {
    return K8sDirectInfraYaml.builder()
        .type(Infrastructure.Type.KUBERNETES_DIRECT)
        .spec(K8sDirectInfraYamlSpec.builder()
                  .connectorRef(createValueField("testKubernetesCluster"))
                  .namespace(createValueField("testNamespace"))
                  .build())
        .build();
  }

  private List<CustomVariable> getCustomVariables() {
    CustomVariable var1 = CustomSecretVariable.builder()
                              .name("VAR1")
                              .value(SecretRefData.builder().identifier("VAR1_secret").scope(Scope.ACCOUNT).build())
                              .build();
    CustomVariable var2 = CustomSecretVariable.builder()
                              .name("VAR2")
                              .value(SecretRefData.builder().identifier("VAR2_secret").scope(Scope.ACCOUNT).build())
                              .build();
    CustomVariable var3 = CustomTextVariable.builder().name("VAR3").value("value3").build();
    CustomVariable var4 = CustomTextVariable.builder().name("VAR4").value("value4").build();
    return asList(var1, var2, var3, var4);
  }

  private List<NGVariable> getStageNGVariables() {
    SecretNGVariable var1 =
        SecretNGVariable.builder()
            .name("VAR1")
            .value(createValueField(SecretRefData.builder().identifier("VAR1_secret").scope(Scope.ACCOUNT).build()))
            .build();
    SecretNGVariable var2 =
        SecretNGVariable.builder()
            .name("VAR2")
            .value(createValueField(SecretRefData.builder().identifier("VAR2_secret").scope(Scope.ACCOUNT).build()))
            .build();
    StringNGVariable var3 = StringNGVariable.builder().name("VAR3").value(createValueField("value3")).build();
    StringNGVariable var4 = StringNGVariable.builder().name("VAR4").value(createValueField("value4")).build();
    return asList(var1, var2, var3, var4);
  }

  public Map<String, String> getEnvVariables(boolean includeWorkspace) {
    Map<String, String> envVars = new HashMap<>();
    envVars.put(DRONE_COMMIT_BRANCH, REPO_BRANCH);
    envVars.put("DRONE_BUILD_NUMBER", Long.toString(BUILD_NUMBER));
    if (includeWorkspace) {
      envVars.put("HARNESS_WORKSPACE", WORK_DIR);
    }
    return envVars;
  }

  public List<SecretNGVariable> getCustomSecretVariable() {
    List<SecretNGVariable> secretVariables = new ArrayList<>();
    secretVariables.add(
        SecretNGVariable.builder()
            .name("VAR1")
            .value(createValueField(SecretRefData.builder().identifier("VAR1_secret").scope(Scope.ACCOUNT).build()))
            .build());
    secretVariables.add(
        SecretNGVariable.builder()
            .name("VAR2")
            .value(createValueField(SecretRefData.builder().identifier("VAR2_secret").scope(Scope.ACCOUNT).build()))
            .build());
    return secretVariables;
  }

  public List<SecretVariableDetails> getSecretVariableDetails() {
    List<SecretVariableDetails> secretVariableDetails = new ArrayList<>();
    secretVariableDetails.add(
        SecretVariableDetails.builder()
            .secretVariableDTO(
                SecretVariableDTO.builder()
                    .name("VAR1")
                    .type(SecretVariableDTO.Type.TEXT)
                    .secret(SecretRefData.builder().identifier("VAR1_secret").scope(Scope.ACCOUNT).build())
                    .build())
            .encryptedDataDetailList(singletonList(EncryptedDataDetail.builder().build()))
            .build());
    secretVariableDetails.add(
        SecretVariableDetails.builder()
            .secretVariableDTO(
                SecretVariableDTO.builder()
                    .name("VAR2")
                    .type(SecretVariableDTO.Type.TEXT)
                    .secret(SecretRefData.builder().identifier("VAR2_secret").scope(Scope.ACCOUNT).build())
                    .build())
            .encryptedDataDetailList(singletonList(EncryptedDataDetail.builder().build()))
            .build());
    return secretVariableDetails;
  }
  public ConnectorDTO getK8sConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name("k8sConnector")
                .identifier("k8sConnector")
                .connectorType(ConnectorType.KUBERNETES_CLUSTER)
                .connectorConfig(
                    KubernetesClusterConfigDTO.builder()
                        .credential(
                            KubernetesCredentialDTO.builder()
                                .kubernetesCredentialType(KubernetesCredentialType.MANUAL_CREDENTIALS)
                                .config(KubernetesClusterDetailsDTO.builder()
                                            .masterUrl("10.10.10.10")
                                            .auth(KubernetesAuthDTO.builder()
                                                      .authType(KubernetesAuthType.USER_PASSWORD)
                                                      .credentials(
                                                          KubernetesUserNamePasswordDTO.builder()
                                                              .username("username")
                                                              .passwordRef(SecretRefData.builder()
                                                                               .identifier("k8sPassword")
                                                                               .scope(Scope.ACCOUNT)
                                                                               .decryptedValue("password".toCharArray())
                                                                               .build())
                                                              .build())
                                                      .build())
                                            .build())
                                .build())
                        .build())
                .build())
        .build();
  }
  public ConnectorDTO getK8sConnectorFromDelegateDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name("k8sConnector")
                           .identifier("k8sConnector")
                           .connectorType(ConnectorType.KUBERNETES_CLUSTER)
                           .connectorConfig(KubernetesClusterConfigDTO.builder()
                                                .delegateSelectors(Collections.singleton("delegate"))
                                                .credential(KubernetesCredentialDTO.builder()
                                                                .kubernetesCredentialType(
                                                                    KubernetesCredentialType.INHERIT_FROM_DELEGATE)
                                                                .config(KubernetesDelegateDetailsDTO.builder().build())
                                                                .build())
                                                .build())
                           .build())
        .build();
  }
  public ConnectorDTO getDockerConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name("dockerConnector")
                .identifier("dockerConnector")
                .connectorType(ConnectorType.DOCKER)
                .connectorConfig(
                    DockerConnectorDTO.builder()
                        .dockerRegistryUrl("https://index.docker.io/v1/")
                        .auth(DockerAuthenticationDTO.builder()
                                  .authType(DockerAuthType.USER_PASSWORD)
                                  .credentials(
                                      DockerUserNamePasswordDTO.builder()
                                          .username("uName")
                                          .passwordRef(
                                              SecretRefData.builder().decryptedValue("pWord".toCharArray()).build())
                                          .build())
                                  .build())
                        .build())
                .build())
        .build();
  }

  public ConnectorDTO getGitLabConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name("gitLabConnector")
                           .identifier("gitLabConnector")
                           .connectorType(ConnectorType.GIT)
                           .connectorConfig(GitConfigDTO.builder()
                                                .url("https://gitlab.com/harshjain123/springboot.git")
                                                .branchName("master")
                                                .gitAuthType(GitAuthType.HTTP)
                                                .gitAuth(GitHTTPAuthenticationDTO.builder()
                                                             .username("username")
                                                             .passwordRef(SecretRefData.builder()
                                                                              .identifier("gitPassword")
                                                                              .scope(Scope.ACCOUNT)
                                                                              .decryptedValue("password".toCharArray())
                                                                              .build())
                                                             .build())
                                                .build())
                           .build())
        .build();
  }

  public ConnectorDTO getBitbucketConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name("bitBucketConnector")
                           .identifier("bitBucketConnector")
                           .connectorType(ConnectorType.GIT)
                           .connectorConfig(GitConfigDTO.builder()
                                                .url("https://harshjain12@bitbucket.org/harshjain12/springboot.git")
                                                .branchName("master")
                                                .gitAuthType(GitAuthType.HTTP)
                                                .gitAuth(GitHTTPAuthenticationDTO.builder()
                                                             .username("username")
                                                             .passwordRef(SecretRefData.builder()
                                                                              .identifier("gitPassword")
                                                                              .scope(Scope.ACCOUNT)
                                                                              .decryptedValue("password".toCharArray())
                                                                              .build())
                                                             .build())
                                                .build())
                           .build())
        .build();
  }

  public ConnectorDTO getGitConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name("gitConnector")
                           .identifier("gitConnector")
                           .connectorType(ConnectorType.GIT)
                           .connectorConfig(GitConfigDTO.builder()
                                                .url("https://github.com/harness/harness-core.git")
                                                .branchName("master")
                                                .gitAuthType(GitAuthType.HTTP)
                                                .gitConnectionType(GitConnectionType.REPO)
                                                .gitAuth(GitHTTPAuthenticationDTO.builder()
                                                             .username("username")
                                                             .passwordRef(SecretRefData.builder()
                                                                              .identifier("gitPassword")
                                                                              .scope(Scope.ACCOUNT)
                                                                              .decryptedValue("password".toCharArray())
                                                                              .build())
                                                             .build())
                                                .build())
                           .build())
        .build();
  }

  public ConnectorDTO getGitHubConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name("gitHubConnector")
                .identifier("gitHubConnector")
                .connectorType(ConnectorType.GITHUB)
                .connectorConfig(
                    GithubConnectorDTO.builder()
                        .url("https://github.com/harness/harness-core.git")
                        .connectionType(GitConnectionType.REPO)
                        .authentication(
                            GithubAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(GithubHttpCredentialsDTO.builder()
                                                 .type(GithubHttpAuthenticationType.USERNAME_AND_PASSWORD)
                                                 .httpCredentialsSpec(
                                                     GithubUsernamePasswordDTO.builder()
                                                         .username("username")
                                                         .passwordRef(SecretRefData.builder()
                                                                          .identifier("gitPassword")
                                                                          .scope(Scope.ACCOUNT)
                                                                          .decryptedValue("password".toCharArray())
                                                                          .build())
                                                         .build())
                                                 .build())
                                .build())

                        .build())
                .build())
        .build();
  }

  public ConnectorDTO getAzureRepoConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name("azureRepoConnector")
                .identifier("azureRepoConnector")
                .connectorType(ConnectorType.AZURE_REPO)
                .connectorConfig(
                    AzureRepoConnectorDTO.builder()
                        .url("https://dev.azure.com/harness/project/repo")
                        .connectionType(GitConnectionType.REPO)
                        .authentication(
                            AzureRepoAuthenticationDTO.builder()
                                .authType(GitAuthType.HTTP)
                                .credentials(
                                    AzureRepoHttpCredentialsDTO.builder()
                                        .type(AzureRepoHttpAuthenticationType.USERNAME_AND_TOKEN)
                                        .httpCredentialsSpec(AzureRepoUsernameTokenDTO.builder()
                                                                 .username("username")
                                                                 .tokenRef(SecretRefData.builder()
                                                                               .identifier("gitPassword")
                                                                               .scope(Scope.ACCOUNT)
                                                                               .decryptedValue("password".toCharArray())
                                                                               .build())
                                                                 .build())
                                        .build())
                                .build())

                        .build())
                .build())
        .build();
  }

  public ConnectorDTO getAwsCodeCommitConnectorDTO() {
    return ConnectorDTO.builder()
        .connectorInfo(
            ConnectorInfoDTO.builder()
                .name("awsCodeCommitConnector")
                .identifier("awsCodeCommitConnector")
                .connectorType(ConnectorType.CODECOMMIT)
                .connectorConfig(
                    AwsCodeCommitConnectorDTO.builder()
                        .url("https://git-codecommit.eu-central-1.amazonaws.com/v1/repos/test")
                        .urlType(AwsCodeCommitUrlType.REPO)
                        .authentication(
                            AwsCodeCommitAuthenticationDTO.builder()
                                .authType(AwsCodeCommitAuthType.HTTPS)
                                .credentials(
                                    AwsCodeCommitHttpsCredentialsDTO.builder()
                                        .type(AwsCodeCommitHttpsAuthType.ACCESS_KEY_AND_SECRET_KEY)
                                        .httpCredentialsSpec(
                                            AwsCodeCommitSecretKeyAccessKeyDTO.builder()
                                                .accessKey("AKIAIOSFODNN7EXAMPLE")
                                                .secretKeyRef(SecretRefData.builder()
                                                                  .identifier("secretKeyRefIdentifier")
                                                                  .scope(Scope.ACCOUNT)
                                                                  .decryptedValue("S3CR3TKEYEXAMPLE".toCharArray())
                                                                  .build())
                                                .build())
                                        .build())

                                .build())
                        .build())
                .build())
        .build();
  }

  public ConnectorDTO getGitConnectorDTOAccountLevel() {
    return ConnectorDTO.builder()
        .connectorInfo(ConnectorInfoDTO.builder()
                           .name("gitConnector")
                           .identifier("gitConnector")
                           .connectorType(ConnectorType.GIT)
                           .connectorConfig(GitConfigDTO.builder()
                                                .url("https://github.com/wings-software/")
                                                .branchName("master")
                                                .gitAuthType(GitAuthType.HTTP)
                                                .gitConnectionType(GitConnectionType.ACCOUNT)
                                                .gitAuth(GitHTTPAuthenticationDTO.builder()
                                                             .username("username")
                                                             .passwordRef(SecretRefData.builder()
                                                                              .identifier("gitPassword")
                                                                              .scope(Scope.ACCOUNT)
                                                                              .decryptedValue("password".toCharArray())
                                                                              .build())
                                                             .build())
                                                .build())
                           .build())
        .build();
  }

  public CIExecutionArgs getCIExecutionArgs() {
    return CIExecutionArgs.builder()
        .executionSource(ManualExecutionSource.builder().branch(REPO_BRANCH).build())
        .build();
  }

  public CIExecutionArgs getPRCIExecutionArgs() {
    WebhookBaseAttributes core =
        WebhookBaseAttributes.builder().link(COMMIT_LINK).message(COMMIT_MESSAGE).after(COMMIT).build();
    Repository repo = Repository.builder().branch(REPO_BRANCH).name(REPO_NAME).namespace(REPO_NAMESPACE).build();

    WebhookEvent webhookEvent = PRWebhookEvent.builder().baseAttributes(core).repository(repo).build();
    ExecutionSource prExecutionSource = WebhookExecutionSource.builder().webhookEvent(webhookEvent).build();
    return CIExecutionArgs.builder().executionSource(prExecutionSource).build();
  }

  public Map<String, String> getPRCIExecutionArgsEnvVars() {
    Map<String, String> envVarMap = new HashMap<>();
    envVarMap.put("DRONE_REPO_NAME", REPO_NAME);
    envVarMap.put("DRONE_REPO_NAMESPACE", REPO_NAMESPACE);
    envVarMap.put("DRONE_REPO_OWNER", REPO_NAMESPACE);
    envVarMap.put("DRONE_REPO_PRIVATE", "false");
    envVarMap.put("DRONE_REPO_SCM", "git");
    envVarMap.put("DRONE_REPO_BRANCH", REPO_BRANCH);
    envVarMap.put("DRONE_COMMIT_LINK", COMMIT_LINK);
    envVarMap.put("DRONE_COMMIT_MESSAGE", COMMIT_MESSAGE);
    envVarMap.put("DRONE_BUILD_NUMBER", BUILD_NUMBER + "");
    envVarMap.put("DRONE_BUILD_EVENT", "pull_request");
    envVarMap.put("DRONE_COMMIT_SHA", COMMIT);
    envVarMap.put("DRONE_COMMIT_AFTER", COMMIT);
    envVarMap.put("DRONE_COMMIT", COMMIT);
    return envVarMap;
  }

  public CIExecutionArgs getBranchCIExecutionArgs() {
    WebhookBaseAttributes core = WebhookBaseAttributes.builder().link(COMMIT_LINK).message(COMMIT_MESSAGE).build();
    Repository repo = Repository.builder().branch(REPO_BRANCH).name(REPO_NAME).namespace(REPO_NAMESPACE).build();

    WebhookEvent webhookEvent = BranchWebhookEvent.builder().baseAttributes(core).repository(repo).build();
    ExecutionSource prExecutionSource = WebhookExecutionSource.builder().webhookEvent(webhookEvent).build();
    return CIExecutionArgs.builder().executionSource(prExecutionSource).build();
  }

  public Map<String, String> getBranchCIExecutionArgsEnvVars() {
    Map<String, String> envVarMap = new HashMap<>();
    envVarMap.put("DRONE_REPO_NAME", REPO_NAME);
    envVarMap.put("DRONE_REPO_NAMESPACE", REPO_NAMESPACE);
    envVarMap.put("DRONE_REPO_OWNER", REPO_NAMESPACE);
    envVarMap.put("DRONE_REPO_PRIVATE", "false");
    envVarMap.put("DRONE_REPO_SCM", "git");
    envVarMap.put("DRONE_REPO_BRANCH", REPO_BRANCH);
    envVarMap.put("DRONE_COMMIT_LINK", COMMIT_LINK);
    envVarMap.put("DRONE_COMMIT_MESSAGE", COMMIT_MESSAGE);
    envVarMap.put("DRONE_BUILD_NUMBER", BUILD_NUMBER + "");
    envVarMap.put("DRONE_BUILD_EVENT", "push");
    return envVarMap;
  }

  public StageElementConfig getIntegrationStageElementConfig() {
    return StageElementConfig.builder()
        .identifier("ciStage")
        .type("CI")
        .stageType(getIntegrationStageConfig())
        .variables(getStageNGVariables())
        .build();
  }

  public IntegrationStageConfig getIntegrationStageConfig() {
    return IntegrationStageConfigImpl.builder()
        .execution(getExecutionElementConfig())
        .infrastructure(getInfrastructureWithVolume())
        .sharedPaths(createValueField(newArrayList("share/")))
        .serviceDependencies(ParameterField.createValueField(Collections.singletonList(getServiceDependencyElement())))
        .build();
  }

  public Infrastructure getInfrastructureWithVolume() {
    EmptyDirYaml emptyDirYaml = EmptyDirYaml.builder()
                                    .mountPath(createValueField(mountPath1))
                                    .spec(EmptyDirYamlSpec.builder()
                                              .medium(ParameterField.createValueField(null))
                                              .size(ParameterField.createValueField(null))
                                              .build())
                                    .build();
    HostPathYaml hostPathYaml = HostPathYaml.builder()
                                    .mountPath(createValueField(mountPath2))
                                    .spec(HostPathYamlSpec.builder()
                                              .path(ParameterField.createValueField(mountPath2))
                                              .type(ParameterField.createValueField(null))
                                              .build())
                                    .build();
    PersistentVolumeClaimYaml pvcYaml = PersistentVolumeClaimYaml.builder()
                                            .mountPath(createValueField(mountPath3))
                                            .spec(PersistentVolumeClaimYamlSpec.builder()
                                                      .claimName(ParameterField.createValueField(claimName))
                                                      .readOnly(ParameterField.createValueField(null))
                                                      .build())
                                            .build();
    return K8sDirectInfraYaml.builder()
        .type(Infrastructure.Type.KUBERNETES_DIRECT)
        .spec(K8sDirectInfraYamlSpec.builder()
                  .connectorRef(createValueField("testKubernetesCluster"))
                  .namespace(createValueField("testNamespace"))
                  .volumes(ParameterField.createValueField(Arrays.asList(emptyDirYaml, hostPathYaml, pvcYaml)))
                  .build())
        .build();
  }
}
