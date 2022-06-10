/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.common.CIExecutionConstants.PORT_PREFIX;
import static io.harness.common.CIExecutionConstants.PORT_STARTING_RANGE;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.common.CIExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.common.CIExecutionConstants.UNIX_STEP_COMMAND;
import static io.harness.delegate.beans.ci.pod.CIContainerType.RUN;
import static io.harness.pms.yaml.ParameterField.createValueField;

import static java.util.Arrays.asList;
import static org.assertj.core.util.Lists.newArrayList;

import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.stages.IntegrationStageConfig;
import io.harness.beans.stages.IntegrationStageConfigImpl;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.volumes.CIVolume;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml;
import io.harness.beans.yaml.extended.volumes.EmptyDirYaml.EmptyDirYamlSpec;
import io.harness.beans.yaml.extended.volumes.HostPathYaml;
import io.harness.beans.yaml.extended.volumes.HostPathYaml.HostPathYamlSpec;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml;
import io.harness.beans.yaml.extended.volumes.PersistentVolumeClaimYaml.PersistentVolumeClaimYamlSpec;
import io.harness.delegate.beans.ci.pod.CIK8ContainerParams;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.delegate.beans.ci.pod.EmptyDirVolume;
import io.harness.delegate.beans.ci.pod.HostPathVolume;
import io.harness.delegate.beans.ci.pod.PVCVolume;
import io.harness.delegate.beans.ci.pod.PodVolume;
import io.harness.k8s.model.ImageDetails;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.pms.yaml.ParameterField;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class K8InitializeTaskUtilsHelper {
  public static final String K8_CONNECTOR = "testKubernetesCluster";
  public static final String K8_NAMESPACE = "testNamespace";

  public static final String EMPTY_DIR_SIZE = "1Gi";
  public static final String EMPTY_DIR_MOUNT_PATH = "/empty";
  public static final String HOST_DIR_MOUNT_PATH = "/host";
  public static final String PVC_DIR_MOUNT_PATH = "/pvc";
  public static final String PVC_CLAIM_NAME = "pvc";
  public static final String STAGE_ID = "k8_stage";

  private static final String RUN_STEP_IMAGE = "maven:3.6.3-jdk-8";
  private static final String RUN_STEP_CONNECTOR = "run";
  private static final String RUN_STEP_ID = "step-2";
  private static final String RUN_STEP_NAME = "test script";
  private static final String BUILD_SCRIPT = "mvn clean install";

  public static final Integer DEFAULT_LIMIT_MILLI_CPU = 200;
  public static final Integer DEFAULT_LIMIT_MEMORY_MIB = 200;

  public static InitializeStepInfo getDirectK8Step() {
    IntegrationStageConfig integrationStageConfig =
        IntegrationStageConfigImpl.builder()
            .sharedPaths(ParameterField.createValueField(null))
            .execution(ExecutionElementConfig.builder().steps(getExecutionWrapperConfigList()).build())
            .build();
    ExecutionElementConfig executionElementConfig =
        ExecutionElementConfig.builder().steps(getExecutionWrapperConfigList()).build();
    K8sDirectInfraYaml k8sDirectInfraYaml = K8sDirectInfraYaml.builder()
                                                .type(Infrastructure.Type.KUBERNETES_DIRECT)
                                                .spec(K8sDirectInfraYamlSpec.builder()
                                                          .connectorRef(createValueField(K8_CONNECTOR))
                                                          .namespace(createValueField(K8_NAMESPACE))
                                                          .automountServiceAccountToken(createValueField(true))
                                                          .priorityClassName(createValueField(null))
                                                          .build())
                                                .build();

    return InitializeStepInfo.builder()
        .stageIdentifier(STAGE_ID)
        .infrastructure(k8sDirectInfraYaml)
        .executionElementConfig(executionElementConfig)
        .stageElementConfig(integrationStageConfig)
        .build();
  }

  public static List<ExecutionWrapperConfig> getExecutionWrapperConfigList() {
    return newArrayList(ExecutionWrapperConfig.builder().step(getRunStepElementConfigAsJsonNode()).build());
  }

  private static JsonNode getRunStepElementConfigAsJsonNode() {
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode stepElementConfig = mapper.createObjectNode();
    stepElementConfig.put("identifier", RUN_STEP_ID);

    stepElementConfig.put("type", "Run");
    stepElementConfig.put("name", RUN_STEP_NAME);

    ObjectNode stepSpecType = mapper.createObjectNode();
    stepSpecType.put("identifier", RUN_STEP_ID);
    stepSpecType.put("name", RUN_STEP_NAME);
    stepSpecType.put("command", BUILD_SCRIPT);
    stepSpecType.put("image", RUN_STEP_IMAGE);
    stepSpecType.put("connectorRef", RUN_STEP_CONNECTOR);

    stepElementConfig.set("spec", stepSpecType);
    return stepElementConfig;
  }

  public static ContainerDefinitionInfo getRunStepContainer(Integer index) {
    Integer port = PORT_STARTING_RANGE + index - 1;
    ImageDetails imageDetails = ImageDetails.builder().name("maven").tag("3.6.3-jdk-8").build();

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
        .envVars(new HashMap<>())
        .secretVariables(new ArrayList<>())
        .stepIdentifier(RUN_STEP_ID)
        .stepName(RUN_STEP_NAME)
        .build();
  }

  public static CIK8ContainerParams getAddonContainer() {
    return CIK8ContainerParams.builder().name("addon").build();
  }

  public static CIK8ContainerParams getLiteEngineContainer() {
    return CIK8ContainerParams.builder().name("engine").build();
  }

  public static K8sDirectInfraYaml getDirectK8InfrastructureWithVolume() {
    List<CIVolume> volumes = asList(getEmptyDirVolYaml(), getHostPathVolYaml(), getPVCYaml());
    return K8sDirectInfraYaml.builder()
        .type(Infrastructure.Type.KUBERNETES_DIRECT)
        .spec(K8sDirectInfraYamlSpec.builder()
                  .connectorRef(createValueField(K8_CONNECTOR))
                  .namespace(createValueField(K8_NAMESPACE))
                  .volumes(createValueField(volumes))
                  .build())
        .build();
  }

  private static EmptyDirYaml getEmptyDirVolYaml() {
    return EmptyDirYaml.builder()
        .mountPath(createValueField(EMPTY_DIR_MOUNT_PATH))
        .spec(EmptyDirYamlSpec.builder().size(createValueField(EMPTY_DIR_SIZE)).medium(createValueField(null)).build())
        .build();
  }

  private static HostPathYaml getHostPathVolYaml() {
    return HostPathYaml.builder()
        .mountPath(createValueField(HOST_DIR_MOUNT_PATH))
        .spec(
            HostPathYamlSpec.builder().path(createValueField(HOST_DIR_MOUNT_PATH)).type(createValueField(null)).build())
        .build();
  }

  private static PersistentVolumeClaimYaml getPVCYaml() {
    return PersistentVolumeClaimYaml.builder()
        .mountPath(createValueField(PVC_DIR_MOUNT_PATH))
        .spec(PersistentVolumeClaimYamlSpec.builder()
                  .claimName(createValueField(PVC_CLAIM_NAME))
                  .readOnly(createValueField(null))
                  .build())
        .build();
  }

  public static List<PodVolume> getConvertedVolumes() {
    EmptyDirVolume emptyDirVolume =
        EmptyDirVolume.builder().name("volume-0").mountPath(EMPTY_DIR_MOUNT_PATH).sizeMib(1024).build();
    HostPathVolume hostPathVolume =
        HostPathVolume.builder().name("volume-1").mountPath(HOST_DIR_MOUNT_PATH).path(HOST_DIR_MOUNT_PATH).build();
    PVCVolume pvcVolume =
        PVCVolume.builder().name("volume-2").mountPath(PVC_DIR_MOUNT_PATH).claimName(PVC_CLAIM_NAME).build();

    return asList(emptyDirVolume, hostPathVolume, pvcVolume);
  }
}
