/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.buildstate;

import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.ALL_TASK_SELECTORS;
import static io.harness.beans.sweepingoutputs.CISweepingOutputNames.TASK_SELECTORS;
import static io.harness.beans.sweepingoutputs.StageInfraDetails.STAGE_INFRA_DETAILS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.StageInfraDetails;
import io.harness.beans.sweepingoutputs.TaskSelectorSweepingOutput;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.utils.BaseConnectorUtils;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.TaskSelector;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.exception.ConnectorNotFoundException;
import io.harness.exception.ngexception.CIStageExecutionException;
import io.harness.git.GitClientHelper;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionPrincipalInfo;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.security.JWTTokenServiceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CI)
public class ConnectorUtils extends BaseConnectorUtils {
  private final ConnectorResourceClient connectorResourceClient;
  private final SecretManagerClientService secretManagerClientService;
  private final SecretUtils secretUtils;
  private final CIExecutionServiceConfig cIExecutionServiceConfig;
  @Inject private CIFeatureFlagService featureFlagService;
  @Inject @Named("harnessCodeGitBaseUrl") private String harnessCodeGitBaseUrl;
  private final long TEN_HOURS_IN_MS = TimeUnit.MILLISECONDS.convert(10, TimeUnit.HOURS);

  @Inject
  public ConnectorUtils(ConnectorResourceClient connectorResourceClient, SecretUtils secretUtils,
      CIExecutionServiceConfig ciExecutionServiceConfig,
      @Named("PRIVILEGED") SecretManagerClientService secretManagerClientService) {
    this.connectorResourceClient = connectorResourceClient;
    this.secretUtils = secretUtils;
    this.secretManagerClientService = secretManagerClientService;
    this.cIExecutionServiceConfig = ciExecutionServiceConfig;
  }

  public List<TaskSelector> fetchDelegateSelector(
      Ambiance ambiance, ExecutionSweepingOutputService executionSweepingOutputResolver) {
    String accountID = AmbianceUtils.getAccountId(ambiance);
    if (featureFlagService.isEnabled(FeatureName.DISABLE_CI_STAGE_DEL_SELECTOR, accountID)) {
      log.info("DISABLE_CI_STAGE_DEL_SELECTOR Feature flag is enabled for account {}", accountID);
      return Collections.emptyList();
    }

    // Delegate Selector Precedence: 1)Stage ->  2)Pipeline ->  3)Connector .If not specified use any delegate
    OptionalSweepingOutput optionalTaskSelectorSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(TASK_SELECTORS));

    if (optionalTaskSelectorSweepingOutput.isFound()) {
      TaskSelectorSweepingOutput taskSelectorSweepingOutput =
          (TaskSelectorSweepingOutput) optionalTaskSelectorSweepingOutput.getOutput();

      return taskSelectorSweepingOutput.getTaskSelectors();
    }

    OptionalSweepingOutput optionalSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(STAGE_INFRA_DETAILS));
    List<TaskSelector> taskSelectors = new ArrayList<>();

    if (!optionalSweepingOutput.isFound()) {
      return taskSelectors;
    }

    try {
      StageInfraDetails stageInfraDetails = (StageInfraDetails) optionalSweepingOutput.getOutput();
      if (stageInfraDetails.getType() == StageInfraDetails.Type.K8) {
        K8StageInfraDetails k8StageInfraDetails = (K8StageInfraDetails) stageInfraDetails;
        String clusterConnectorRef;

        if (k8StageInfraDetails.getInfrastructure() == null) {
          throw new CIStageExecutionException("Input infrastructure can not be empty");
        }

        if (k8StageInfraDetails.getInfrastructure().getType() == Infrastructure.Type.KUBERNETES_DIRECT) {
          K8sDirectInfraYaml k8sDirectInfraYaml = (K8sDirectInfraYaml) k8StageInfraDetails.getInfrastructure();

          clusterConnectorRef = k8sDirectInfraYaml.getSpec().getConnectorRef().getValue();
        } else {
          throw new CIStageExecutionException("Wrong k8s type");
        }

        BaseNGAccess ngAccess = BaseNGAccess.builder()
                                    .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
                                    .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                                    .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                                    .build();

        ConnectorDetails connectorDetails = getConnectorDetails(ngAccess, clusterConnectorRef);
        if (connectorDetails != null) {
          taskSelectors =
              TaskSelectorYaml.toTaskSelector(((KubernetesClusterConfigDTO) connectorDetails.getConnectorConfig())
                                                  .getDelegateSelectors()
                                                  .stream()
                                                  .map(TaskSelectorYaml::new)
                                                  .collect(Collectors.toList()));
        }
      }
    } catch (Exception ex) {
      log.error("Failed to fetch task selector", ex);
    }
    return taskSelectors;
  }

  public List<TaskSelector> fetchCodebaseDelegateSelector(Ambiance ambiance, ConnectorDetails connectorDetails,
      ExecutionSweepingOutputService executionSweepingOutputResolver) {
    List<TaskSelector> taskSelectors = new ArrayList<>();

    // Delegate Selector Precedence: 1)Pipeline -> 2)Connector. If not specified use any delegate
    OptionalSweepingOutput optionalTaskSelectorSweepingOutput = executionSweepingOutputResolver.resolveOptional(
        ambiance, RefObjectUtils.getSweepingOutputRefObject(ALL_TASK_SELECTORS));

    if (optionalTaskSelectorSweepingOutput.isFound()) {
      TaskSelectorSweepingOutput taskSelectorSweepingOutput =
          (TaskSelectorSweepingOutput) optionalTaskSelectorSweepingOutput.getOutput();

      // only select pipeline delegate selectors for codebase since it's defined on pipeline level
      taskSelectors = taskSelectorSweepingOutput.getTaskSelectors()
                          .stream()
                          .filter(taskSelector -> YAMLFieldNameConstants.PIPELINE.equals(taskSelector.getOrigin()))
                          .toList();
      if (isNotEmpty(taskSelectors)) {
        return taskSelectors;
      }
    }

    // fetch from git connector
    if (connectorDetails != null && isNotEmpty(connectorDetails.getDelegateSelectors())) {
      taskSelectors = TaskSelectorYaml.toTaskSelector(
          connectorDetails.getDelegateSelectors().stream().map(TaskSelectorYaml::new).collect(Collectors.toList()));
    }

    return taskSelectors;
  }

  public ConnectorDetails getDefaultInternalConnector(NGAccess ngAccess) {
    ConnectorDetails connectorDetails = null;
    if (isNotEmpty(cIExecutionServiceConfig.getDefaultInternalImageConnector())) {
      try {
        connectorDetails = getConnectorDetails(ngAccess, cIExecutionServiceConfig.getDefaultInternalImageConnector());
      } catch (ConnectorNotFoundException e) {
        log.info("Default harness image connector does not exist: {}", e.getMessage());
        connectorDetails = null;
      }
    }
    return connectorDetails;
  }

  public ConnectorDetails getConnectorDetails(NGAccess ngAccess, String connectorIdentifier, boolean isGitConnector) {
    if (isGitConnector && isEmpty(connectorIdentifier)
        && featureFlagService.isEnabled(FeatureName.CODE_ENABLED, ngAccess.getAccountIdentifier())) {
      log.info("fetching harness scm connector");
      String gitBaseUrl = harnessCodeGitBaseUrl;
      String authToken = "";
      // todo: with this internal url we assume scm is in same cluster as ci manager, will need changes for ci saas and
      // scm on prem or vice versa
      return super.getHarnessConnectorDetails(ngAccess, gitBaseUrl, authToken,
          cIExecutionServiceConfig.getGitnessConfig().getHttpClientConfig().getBaseUrl());
    }

    if (isEmpty(connectorIdentifier)) {
      throw new CIStageExecutionException("Git connector is mandatory in case git clone is enabled");
    }

    return super.getConnectorDetails(ngAccess, connectorIdentifier);
  }

  public ConnectorDetails getConnectorDetailsWithToken(
      NGAccess ngAccess, String connectorIdentifier, boolean isGitConnector, Ambiance ambiance, String repoName) {
    if (isEmpty(connectorIdentifier)) {
      if (isGitConnector && featureFlagService.isEnabled(FeatureName.CODE_ENABLED, ngAccess.getAccountIdentifier())) {
        log.info("fetching harness scm connector");
        String baseUrl = harnessCodeGitBaseUrl;
        String authToken = fetchAuthToken(ngAccess, ambiance, repoName);
        // todo: with this internal url we assume scm is in same cluster as ci manager, will need changes for ci saas
        // and scm on prem or vice versa
        return super.getHarnessConnectorDetails(ngAccess, baseUrl, authToken,
            cIExecutionServiceConfig.getGitnessConfig().getHttpClientConfig().getBaseUrl());
      } else {
        throw new CIStageExecutionException("Git connector is mandatory in case git clone is enabled");
      }
    }

    return super.getConnectorDetails(ngAccess, connectorIdentifier);
  }

  private String fetchAuthToken(NGAccess ngAccess, Ambiance ambiance, String repoName) {
    ExecutionPrincipalInfo executionPrincipalInfo = ambiance.getMetadata().getPrincipalInfo();
    String principal = executionPrincipalInfo.getPrincipal();
    io.harness.pms.contracts.plan.PrincipalType principalType = executionPrincipalInfo.getPrincipalType();

    String completeRepoName = GitClientHelper.convertToHarnessRepoName(
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier(), repoName);

    String[] allowedResources = {completeRepoName};

    ImmutableMap<String, String> claims = ImmutableMap.of("name", principal, "type", principalType.name());
    ImmutableMap<String, String[]> arrayClaims = ImmutableMap.of("allowedResources", allowedResources);

    return JWTTokenServiceUtils.generateJWTToken(
        claims, arrayClaims, TEN_HOURS_IN_MS, cIExecutionServiceConfig.getGitnessConfig().getJwtSecret());
  }
}
