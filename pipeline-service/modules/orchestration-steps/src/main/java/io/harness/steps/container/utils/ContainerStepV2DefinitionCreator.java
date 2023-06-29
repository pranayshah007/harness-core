/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.steps.container.utils;

import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_PREFIX;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MEMORY_MIB;
import static io.harness.ci.commonconstants.ContainerExecutionConstants.STEP_REQUEST_MILLI_CPU;
import static io.harness.pms.sdk.core.plugin.ContainerUnitStepUtils.getKubernetesStandardPodName;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.environment.pod.container.ContainerImageDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.buildstate.StepContainerUtils;
import io.harness.delegate.beans.ci.pod.CIContainerType;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.grpc.utils.StringValueUtils;
import io.harness.pms.contracts.plan.PluginCreationResponseWrapper;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.contracts.plan.StepInfoProto;
import io.harness.pms.sdk.core.plugin.ImageDetailsUtils;
import io.harness.pms.sdk.core.plugin.SecretNgVariableUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.plugin.InitContainerV2StepInfo;
import io.harness.steps.plugin.infrastructure.ContainerK8sInfra;
import io.harness.yaml.core.variables.SecretNGVariable;

import io.fabric8.utils.Strings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class ContainerStepV2DefinitionCreator {
  public List<ContainerDefinitionInfo> getContainerDefinitionInfo(
      InitContainerV2StepInfo initContainerV2StepInfo, String stepGroupIdentifier) {
    ParameterField<OSType> os = ((ContainerK8sInfra) initContainerV2StepInfo.getInfrastructure()).getSpec().getOs();
    List<ContainerDefinitionInfo> containerDefinitionInfos = new ArrayList<>();

    initContainerV2StepInfo.getPluginsData().forEach((stepInfo1, value) -> {
      for (PluginCreationResponseWrapper response : value.getResponseList()) {
        PluginDetails pluginDetails = response.getResponse().getPluginDetails();
        StepInfoProto stepInfo = response.getStepInfo();
        String stepIdentifier = stepInfo.getIdentifier();
        if (Strings.isNotBlank(stepGroupIdentifier)) {
          stepIdentifier = stepGroupIdentifier + "_" + stepIdentifier;
        }
        String identifier = getKubernetesStandardPodName(stepInfo.getIdentifier());
        String containerName = String.format("%s%s", STEP_PREFIX, identifier).toLowerCase();
        Map<String, String> envMap = new HashMap<>(pluginDetails.getEnvVariablesMap());
        List<SecretNGVariable> secretNGVariableMap = pluginDetails.getSecretVariableList()
                                                         .stream()
                                                         .map(SecretNgVariableUtils::getSecretNgVariable)
                                                         .collect(Collectors.toList());
        containerDefinitionInfos.add(
            ContainerDefinitionInfo.builder()
                .name(containerName)
                .commands(StepContainerUtils.getCommand(
                    os.getValue() != null ? OSType.getOSType(String.valueOf(os.getValue())) : null))
                .args(StepContainerUtils.getArguments(pluginDetails.getPortUsed(0)))
                .envVars(envMap)
                .secretVariables(secretNGVariableMap)
                .containerImageDetails(
                    ContainerImageDetails.builder()
                        .imageDetails(
                            ImageDetailsUtils.getImageDetails(pluginDetails.getImageDetails().getImageInformation()))
                        .connectorIdentifier(pluginDetails.getImageDetails().getConnectorDetails().getConnectorRef())
                        .build())
                .isHarnessManagedImage(true)
                .containerResourceParams(getContainerResourceParams(pluginDetails))
                // Using this as proto object is being serialized
                .ports(new ArrayList<Integer>(pluginDetails.getPortUsedList()))
                .containerType(CIContainerType.PLUGIN)
                .stepIdentifier(stepIdentifier)
                .stepName(stepInfo.getIdentifier())
                .imagePullPolicy(StringValueUtils.getStringFromStringValue(
                    pluginDetails.getImageDetails().getImageInformation().getImagePullPolicy()))
                .privileged(pluginDetails.getPrivileged())
                .runAsUser(pluginDetails.getRunAsUser())
                .build());
      }
    });
    return containerDefinitionInfos;
  }

  private ContainerResourceParams getContainerResourceParams(PluginDetails pluginDetails) {
    return ContainerResourceParams.builder()
        .resourceRequestMemoryMiB(STEP_REQUEST_MEMORY_MIB)
        .resourceRequestMilliCpu(STEP_REQUEST_MILLI_CPU)
        .resourceLimitMemoryMiB(pluginDetails.getResource().getMemory())
        .resourceLimitMilliCpu(pluginDetails.getResource().getCpu())
        .build();
  }
}
