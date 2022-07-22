/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.ci.serializer.vm;

import static io.harness.ci.commonconstants.CIExecutionConstants.HARNESS_WORKSPACE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.beans.serializer.RunTimeInputHandler;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.beans.steps.stepinfo.PluginStepInfo;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.execution.CIExecutionConfigService;
import io.harness.ci.integrationstage.InitializeStepUtils;
import io.harness.ci.integrationstage.IntegrationStageUtils;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.vm.steps.VmPluginStep;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.timeout.Timeout;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.Map;

@Singleton
public class VmGitCloneStepSerializer {
  @Inject CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject HarnessImageUtils harnessImageUtils;
  @Inject VmPluginStepSerializer vmPluginStepSerializer;
  @Inject CodebaseUtils codebaseUtils;
  @Inject CIExecutionConfigService ciExecutionConfigService;

  public VmPluginStep serialize(GitCloneStepInfo gitCloneStepInfo, StageInfraDetails stageInfraDetails, String identifier,
                                ParameterField<Timeout> parameterFieldTimeout, String stepName, Ambiance ambiance) {

    PluginStepInfo pluginStepInfo = InitializeStepUtils.createPluginStepInfo(gitCloneStepInfo, ciExecutionConfigService);

    VmPluginStep vmPluginStep = vmPluginStepSerializer.serialize(pluginStepInfo, stageInfraDetails, identifier,
            parameterFieldTimeout, stepName, ambiance);
    NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
    Map<String, String> envVars = new HashMap<>();
    envVars.putAll(vmPluginStep.getEnvVariables());
    String image = null;
    ConnectorDetails harnessInternalImageConnector = null;

    if (pluginStepInfo.isHarnessManagedImage()) {
      String gitImage = ciExecutionServiceConfig.getStepConfig().getVmImageConfig().getGitClone();

      harnessInternalImageConnector =
              harnessImageUtils.getHarnessImageConnectorDetailsForVM(ngAccess, stageInfraDetails);
      image = IntegrationStageUtils.getFullyQualifiedImageName(gitImage, harnessInternalImageConnector);
    }

    //Get the Git Connector
    final String connectorRef = gitCloneStepInfo.getConnectorRef().getValue();
    ConnectorDetails gitConnector = codebaseUtils.getGitConnector(ngAccess, connectorRef);

    //Set the Git Connector Reference environment variables
    final String repoName = gitCloneStepInfo.getRepoName().getValue();
    final Map<String, String> gitEnvVars = codebaseUtils.getGitEnvVariables(gitConnector, repoName);
    envVars.putAll(gitEnvVars);

    //TODO: Probably also have to make change at VmInitializeTaskParamsBuilder.getVmInitializeParams
    String cloneDirectoryString = RunTimeInputHandler.resolveStringParameter("cloneDirectory", stepName,
            identifier, gitCloneStepInfo.getCloneDirectory(), false);
    if (isNotEmpty(cloneDirectoryString)) {
      envVars.put(HARNESS_WORKSPACE, cloneDirectoryString);
    }

    //vmPluginStep immutable, build a new one based and add a few gitClone relevant values to it
    return VmPluginStep.builder()
            .image(image)
            .imageConnector(harnessInternalImageConnector)
            .envVariables(envVars)
            .connector(gitConnector)
            .connectorSecretEnvMap(vmPluginStep.getConnectorSecretEnvMap())
            .pullPolicy(vmPluginStep.getPullPolicy())
            .privileged(vmPluginStep.isPrivileged())
            .runAsUser(vmPluginStep.getRunAsUser())
            .unitTestReport(vmPluginStep.getUnitTestReport())
            .timeoutSecs(vmPluginStep.getTimeoutSecs())
            .build();
  }
}