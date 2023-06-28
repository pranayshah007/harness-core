/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.sm.states;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.sm.states.k8s.K8sStateHelper.fetchSafeTimeoutInMillis;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.context.ContextElementType;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.k8s.model.HelmVersion;
import io.harness.k8s.model.ImageDetails;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.api.HelmDeployContextElement;
import software.wings.api.HelmDeployStateExecutionData.HelmDeployStateExecutionDataBuilder;
import software.wings.beans.Application;
import software.wings.beans.GitConfig;
import software.wings.beans.GitFileConfig;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.command.CommandUnit;
import software.wings.beans.command.HelmDummyCommandUnit;
import software.wings.beans.command.HelmDummyCommandUnitConstants;
import software.wings.beans.container.HelmChartSpecification;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest.HelmRollbackCommandRequestBuilder;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;
import software.wings.helpers.ext.k8s.request.K8sValuesLocation;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.impl.ContainerServiceParams;
import software.wings.sm.ContextElement;
import software.wings.sm.ExecutionContext;
import software.wings.sm.StateType;
import software.wings.stencils.DefaultValue;
import software.wings.utils.HelmChartSpecificationMapper;

import com.github.reinert.jjschema.Attributes;
import com.github.reinert.jjschema.SchemaIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by anubhaw on 4/3/18.
 */
@OwnedBy(CDP)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class HelmRollbackState extends HelmDeployState {
  /**
   * Instantiates a new state.
   *
   * @param name the name
   */
  public HelmRollbackState(String name) {
    super(name, StateType.HELM_ROLLBACK.name());
  }

  @Override
  protected HelmCommandRequest getHelmCommandRequest(ExecutionContext context,
      HelmChartSpecification helmChartSpecification, ContainerServiceParams containerServiceParams, String releaseName,
      String accountId, String appId, String activityId, ImageDetails imageTag, String repoName, GitConfig gitConfig,
      List<EncryptedDataDetail> encryptedDataDetails, String commandFlags, K8sDelegateManifestConfig manifestConfig,
      Map<K8sValuesLocation, ApplicationManifest> appManifestMap, HelmVersion helmVersion,
      HelmCommandFlag helmCommandFlag) {
    Integer previousReleaseRevision = null;

    ContextElement contextElement = context.getContextElement(ContextElementType.HELM_DEPLOY);
    if (contextElement != null) {
      previousReleaseRevision = ((HelmDeployContextElement) contextElement).getPreviousReleaseRevision();
    }

    List<String> helmValueOverridesYamlFilesEvaluated =
        getValuesYamlOverrides(context, containerServiceParams, imageTag, appManifestMap);

    HelmRollbackCommandRequestBuilder requestBuilder =
        HelmRollbackCommandRequest.builder()
            .releaseName(releaseName)
            .prevReleaseVersion(previousReleaseRevision != null ? previousReleaseRevision : -1)
            .accountId(accountId)
            .appId(context.getAppId())
            .activityId(activityId)
            .commandName(HELM_COMMAND_NAME)
            .timeoutInMillis(fetchSafeTimeoutInMillis(getTimeoutMillis()))
            .containerServiceParams(containerServiceParams)
            .chartSpecification(HelmChartSpecificationMapper.helmChartSpecificationDTO(helmChartSpecification))
            .repoName(repoName)
            .gitConfig(gitConfig)
            .encryptedDataDetails(encryptedDataDetails)
            .commandFlags(commandFlags)
            .sourceRepoConfig(manifestConfig)
            .helmVersion(helmVersion)
            .variableOverridesYamlFiles(helmValueOverridesYamlFilesEvaluated)
            .isGitHostConnectivityCheck(
                featureFlagService.isEnabled(FeatureName.HELM_MERGE_CAPABILITIES, context.getAccountId()))
            .useNewKubectlVersion(featureFlagService.isEnabled(FeatureName.NEW_KUBECTL_VERSION, context.getAccountId()))
            .skipSteadyStateCheck(isSkipSteadyStateCheck());

    if (getGitFileConfig() != null) {
      requestBuilder.gitFileConfig(getGitFileConfig());
    }

    if (null != manifestConfig) {
      requestBuilder.helmCommandFlag(manifestConfig.getHelmCommandFlag());
    }

    return requestBuilder.build();
  }

  @Override
  protected ImageDetails getImageDetails(ExecutionContext context, Artifact artifact) {
    return null;
  }

  @Override
  protected void setNewAndPrevReleaseVersion(ExecutionContext context, Application app, String releaseName,
      ContainerServiceParams containerServiceParams, HelmDeployStateExecutionDataBuilder stateExecutionDataBuilder,
      GitConfig gitConfig, List<EncryptedDataDetail> encryptedDataDetails, String commandFlags, HelmVersion helmVersion,
      int expressionFunctorToken, HelmCommandFlag helmCommandFlag, String activityId) {
    HelmDeployContextElement contextElement = context.getContextElement(ContextElementType.HELM_DEPLOY);
    if (contextElement != null) {
      stateExecutionDataBuilder.releaseOldVersion(contextElement.getNewReleaseRevision());
      stateExecutionDataBuilder.releaseNewVersion(contextElement.getNewReleaseRevision() + 1);
      stateExecutionDataBuilder.rollbackVersion(contextElement.getPreviousReleaseRevision());
      stateExecutionDataBuilder.activityId(activityId);
    }
  }

  @Override
  @Attributes(title = "Deployment steady state timeout (in minutes).")
  @DefaultValue("10")
  public int getSteadyStateTimeout() {
    return super.getSteadyStateTimeout();
  }

  @Override
  @SchemaIgnore
  public String getHelmReleaseNamePrefix() {
    return super.getHelmReleaseNamePrefix();
  }

  @Override
  @SchemaIgnore
  public GitFileConfig getGitFileConfig() {
    return super.getGitFileConfig();
  }

  @Override
  @SchemaIgnore
  public String getCommandFlags() {
    return super.getCommandFlags();
  }

  @Override
  public Map<String, String> validateFields() {
    return null;
  }

  @Override
  protected List<CommandUnit> getCommandUnits(
      boolean valuesInGit, boolean valuesInHelmChartRepo, boolean isCustomManifest) {
    List<CommandUnit> commandUnits = new ArrayList<>();

    commandUnits.add(new HelmDummyCommandUnit(HelmDummyCommandUnitConstants.Init));
    commandUnits.add(new HelmDummyCommandUnit(HelmDummyCommandUnitConstants.Rollback));
    commandUnits.add(new HelmDummyCommandUnit(HelmDummyCommandUnitConstants.WaitForSteadyState));

    return commandUnits;
  }
}
