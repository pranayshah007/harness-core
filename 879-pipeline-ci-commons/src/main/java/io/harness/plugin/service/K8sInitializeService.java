/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.plugin.service;

import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.beans.executionargs.ExecutionArgs;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.ci.utils.PortFinder;
import io.harness.delegate.beans.ci.pod.ContainerResourceParams;
import io.harness.plancreator.stages.stage.AbstractStageNode;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.yaml.core.variables.SecretNGVariable;
import io.harness.yaml.extended.ci.container.ContainerResource;

import java.util.Map;

public interface K8sInitializeService {
  ContainerDefinitionInfo createPluginCompatibleStepContainerDefinition(PluginCompatibleStep stepInfo,
      AbstractStageNode stageNode, ExecutionArgs ciExecutionArgs, PortFinder portFinder, int stepIndex,
      String identifier, String stepName, String stepType, long timeout, String accountId, OSType os, Ambiance ambiance,
      Integer extraMemoryPerStep, Integer extraCPUPerStep);

  ContainerResourceParams getContainerResources(ContainerResource resources, String stepType, String identifier,
      String accountId, Integer extraMemoryPerStep, Integer extraCPUPerStep);

  Map<String, String> getEnvironmentVarMap(AbstractStageNode stageNode);

  Map<String, SecretNGVariable> getSecretMap(AbstractStageNode stageNode);

  String getImage(PluginCompatibleStep step, StageInfraDetails.Type infraType, String accountId);
}
