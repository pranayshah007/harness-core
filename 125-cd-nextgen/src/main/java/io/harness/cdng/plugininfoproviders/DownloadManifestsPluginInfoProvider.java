/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.plugininfoproviders;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIAbstractStepNode;
import io.harness.beans.steps.nodes.GitCloneStepNode;
import io.harness.beans.steps.stepinfo.GitCloneStepInfo;
import io.harness.cdng.aws.sam.AwsSamDeployStepInfo;
import io.harness.cdng.expressions.CDExpressionResolver;
import io.harness.cdng.infra.beans.AwsSamInfrastructureOutcome;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.pipeline.executions.CDPluginInfoProvider;
import io.harness.cdng.pipeline.steps.CdAbstractStepNode;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.connector.ConnectorConfigDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsCredentialSpecDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsManualConfigSpecDTO;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ng.core.NGAccess;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ImageDetails;
import io.harness.pms.contracts.plan.PluginCreationRequest;
import io.harness.pms.contracts.plan.PluginCreationResponse;
import io.harness.pms.contracts.plan.PluginDetails;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.plugin.ContainerPluginParseException;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.steps.container.execution.plugin.StepImageConfig;
import io.harness.utils.IdentifierRefHelper;
import io.harness.yaml.extended.ci.codebase.Build;
import io.harness.yaml.extended.ci.codebase.BuildSpec;
import io.harness.yaml.extended.ci.codebase.BuildType;
import io.harness.yaml.extended.ci.codebase.impl.BranchBuildSpec;
import io.harness.yaml.utils.NGVariablesUtils;
import org.eclipse.jgit.api.Git;
import org.jooq.tools.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.harness.ci.commonconstants.CIExecutionConstants.GIT_CLONE_STEP_ID;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

@OwnedBy(HarnessTeam.CDP)
public class DownloadManifestsPluginInfoProvider implements CDPluginInfoProvider {
  @Inject private CDExpressionResolver cdExpressionResolver;
  @Inject private OutcomeService outcomeService;

  @Inject private GitClonePluginInfoProvider gitClonePluginInfoProvider;

  @Inject PluginExecutionConfig pluginExecutionConfig;

  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;

  @Override
  public PluginCreationResponse getPluginInfo(PluginCreationRequest request) {
//    String stepJsonNode = request.getStepJsonNode();
//    CdAbstractStepNode cdAbstractStepNode;
//
//    try {
//      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
//    } catch (IOException e) {
//      throw new ContainerPluginParseException(
//          String.format("Error in parsing CD step for step type [%s]", request.getType()), e);
//    }
//
//    AwsSamDeployStepInfo awsSamDeployStepInfo = (AwsSamDeployStepInfo) cdAbstractStepNode.getStepSpecType();
//
//    PluginDetails.Builder pluginDetailsBuilder = PluginInfoProviderHelper.buildPluginDetails(
//        request, awsSamDeployStepInfo.getResources(), awsSamDeployStepInfo.getRunAsUser());
//
//    ImageDetails imageDetails = null;
//
//    if (ParameterField.isNotNull(awsSamDeployStepInfo.getConnectorRef())
//        || isNotEmpty(awsSamDeployStepInfo.getConnectorRef().getValue())) {
//      imageDetails = PluginInfoProviderHelper.getImageDetails(awsSamDeployStepInfo.getConnectorRef(),
//          awsSamDeployStepInfo.getImage(), awsSamDeployStepInfo.getImagePullPolicy());
//
//    } else {
//      // todo: If image is not provided by user, default to an harness provided image
//      StepImageConfig stepImageConfig = pluginExecutionConfig.getSamDeployStepImageConfig();
//    }
//
//    pluginDetailsBuilder.setImageDetails(imageDetails);
//
//    pluginDetailsBuilder.putAllEnvVariables(getEnvironmentVariables(request.getAmbiance(), awsSamDeployStepInfo));
//
//    pluginDetailsBuilder.setPortUsed(0, 20008);

    String stepJsonNode = request.getStepJsonNode();
    CdAbstractStepNode cdAbstractStepNode;
    try {
      cdAbstractStepNode = YamlUtils.read(stepJsonNode, CdAbstractStepNode.class);
    } catch (IOException e) {
      throw new ContainerPluginParseException(
              String.format("Error in parsing CI step for step type [%s]", request.getType()), e);
    }

    Ambiance ambiance = request.getAmbiance();

    ManifestsOutcome manifestsOutcome = (ManifestsOutcome) outcomeService.resolveOptional(
            ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.MANIFESTS)).getOutcome();

    Build build = Build.builder()
            .spec(BranchBuildSpec.builder()
                    .branch(ParameterField.<String>builder().value("main").build())
                    .build())
            .type(BuildType.BRANCH)
            .build();

    GitCloneStepInfo gitCloneStepInfo = GitCloneStepInfo.builder()
            .cloneDirectory(ParameterField.<String>builder().value("m1").build())
            .identifier("m1")
            .name("m1")
            .connectorRef(ParameterField.<String>builder().value("Sainath_Github").build())
            .repoName(ParameterField.<String>builder().value("Sainath-Test").build())
            .build(ParameterField.<Build>builder().value(build).build())
            .build();

    GitCloneStepNode gitCloneStepNode = GitCloneStepNode.builder()
            .gitCloneStepInfo(gitCloneStepInfo)
            .failureStrategies(cdAbstractStepNode.getFailureStrategies())
            .timeout(cdAbstractStepNode.getTimeout())
            .type(GitCloneStepNode.StepType.GitClone)
            .identifier(GIT_CLONE_STEP_ID)
            .build();

    PluginCreationRequest pluginCreationRequest = request.toBuilder().setStepJsonNode(YamlUtils.write(gitCloneStepNode))
            .build();

    return gitClonePluginInfoProvider.getPluginInfo(pluginCreationRequest);
  }

  @Override
  public boolean isSupported(String stepType) {
    if (stepType.equals(StepSpecTypeConstants.DOWNLOAD_MANIFESTS)) {
      return true;
    }
    return false;
  }

}
