/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.steps;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.gitops.beans.FetchLinkedAppsStepParams;
import io.harness.cdng.gitops.beans.GitOpsLinkedAppsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.data.structure.CollectionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.executions.steps.ExecutionNodeType;
import io.harness.gitops.models.Application;
import io.harness.gitops.models.ApplicationQuery;
import io.harness.gitops.remote.GitopsResourceClient;
import io.harness.ng.beans.PageResponse;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.steps.StepCategory;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.sdk.core.steps.io.PassThroughData;
import io.harness.pms.sdk.core.steps.io.StepInputPackage;
import io.harness.pms.sdk.core.steps.io.StepParameters;
import io.harness.pms.sdk.core.steps.io.StepResponse;
import io.harness.steps.executable.SyncExecutableWithRbac;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import retrofit2.Response;

@OwnedBy(HarnessTeam.GITOPS)
@Slf4j
public class FetchLinkedAppsStep implements SyncExecutableWithRbac {
  public static final StepType STEP_TYPE = StepType.newBuilder()
                                               .setType(ExecutionNodeType.GITOPS_FETCH_LINKED_APPS.getYamlType())
                                               .setStepCategory(StepCategory.STEP)
                                               .build();
  public static final String GITOPS_LINKED_APPS = "GITOPS_LINKED_APPS";

  @Inject private ExecutionSweepingOutputService executionSweepingOutputService;
  @Inject private GitOpsStepHelper gitOpsStepHelper;
  @Inject private GitopsResourceClient gitopsResourceClient;

  @Override
  public void validateResources(Ambiance ambiance, StepParameters stepParameters) {}

  @Override
  public StepResponse executeSyncAfterRbac(Ambiance ambiance, StepParameters stepParameters,
      StepInputPackage inputPackage, PassThroughData passThroughData) {
    ManifestOutcome releaseRepoOutcome = gitOpsStepHelper.getReleaseRepoOutcome(ambiance);

    OptionalSweepingOutput optionalGitOpsSweepingOutput = executionSweepingOutputService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(GitopsClustersStep.GITOPS_SWEEPING_OUTPUT));

    if (optionalGitOpsSweepingOutput == null || !optionalGitOpsSweepingOutput.isFound()) {
      throw new InvalidRequestException("GitOps Clusters Outcome Not Found.");
    }

    GitopsClustersOutcome gitopsClustersOutcome = (GitopsClustersOutcome) optionalGitOpsSweepingOutput.getOutput();
    List<String> clusterIds = gitopsClustersOutcome.getClustersData()
                                  .stream()
                                  .map(GitopsClustersOutcome.ClusterData::getClusterId)
                                  .collect(Collectors.toList());

    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(AmbianceUtils.getAccountId(ambiance))
                                      .orgIdentifier(AmbianceUtils.getOrgIdentifier(ambiance))
                                      .projectIdentifier(AmbianceUtils.getProjectIdentifier(ambiance))
                                      .build();

    // TODO: Use appset name from manifest
    List<Application> applications = fetchLinkedApps("my-service-appset", clusterIds, identifierRef);

    return StepResponse.builder()
        .status(Status.SUCCEEDED)
        .stepOutcome(StepResponse.StepOutcome.builder()
                         .name(GITOPS_LINKED_APPS)
                         .outcome(GitOpsLinkedAppsOutcome.builder().apps(applications).build())
                         .build())
        .build();
  }

  private List<Application> fetchLinkedApps(String appSetName, List<String> clusterIds, IdentifierRef identifierRef) {
    Map<String, Object> filters = new HashMap<>();
    filters.put("app.objectmeta.ownerreferences.0.name", appSetName);
    filters.put("clusterIdentifier", ImmutableMap.of("$in", clusterIds));
    ApplicationQuery applicationQuery = ApplicationQuery.builder()
                                            .accountId(identifierRef.getAccountIdentifier())
                                            .orgIdentifier(identifierRef.getOrgIdentifier())
                                            .projectIdentifier(identifierRef.getProjectIdentifier())
                                            .pageIndex(0)
                                            .pageSize(1000) // Assuming not more than 1000 entries
                                            .filter(filters)
                                            .build();
    try {
      Response<PageResponse<Application>> applicationsPageResponse =
          gitopsResourceClient.listApps(applicationQuery).execute();

      if (applicationsPageResponse.body() != null) {
        return CollectionUtils.emptyIfNull(applicationsPageResponse.body().getContent());
      } else {
        log.error("Failed to retrieve Linked Apps from Gitops Service, response :{}", applicationsPageResponse);
        throw new InvalidRequestException("Failed to retrieve Linked Apps from Gitops Service");
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Failed to retrieve Linked Apps from Gitops Service", e);
    }
  }

  @Override
  public Class getStepParametersClass() {
    return FetchLinkedAppsStepParams.class;
  }
}
