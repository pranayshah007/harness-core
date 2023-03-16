/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.terraformcloud.Relationship.CONFIG_VERSION;
import static io.harness.delegate.task.terraformcloud.Relationship.WORKSPACE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanAndApplyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanAndDestroyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanOnlyTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudPlanTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudRefreshTaskParams;
import io.harness.delegate.task.terraformcloud.request.TerraformCloudTaskParams;
import io.harness.exception.InvalidRequestException;
import io.harness.terraformcloud.model.Attributes;
import io.harness.terraformcloud.model.Attributes.AttributesBuilder;
import io.harness.terraformcloud.model.CreateRunData;
import io.harness.terraformcloud.model.ResourceLinkage;
import io.harness.terraformcloud.model.RunData;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.SingleRelationship;
import io.harness.terraformcloud.model.Variable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class RunRequestCreator {
  private static final String WORKSPACES = "workspaces";
  private static final String CONFIG_VERSIONS = "configuration-versions";

  @Inject private TerraformCloudTaskHelper helper;

  public RunRequest createRunRequest(TerraformCloudTaskParams terraformCloudTaskParams) {
    CreateRunData createRunData = new CreateRunData();
    TerraformCloudTaskType terraformCloudTaskType = terraformCloudTaskParams.getTaskType();
    AttributesBuilder builder = Attributes.builder();
    String message;
    String workspace;
    List<Variable> variables;
    switch (terraformCloudTaskType) {
      case RUN_REFRESH_STATE:
        TerraformCloudRefreshTaskParams refreshParams = (TerraformCloudRefreshTaskParams) terraformCloudTaskParams;
        message = refreshParams.getMessage();
        workspace = refreshParams.getWorkspace();
        variables = getVariables(refreshParams.getVariables());
        builder.refreshOnly(true).autoApply(true).build();
        break;
      case RUN_PLAN_ONLY:
        TerraformCloudPlanOnlyTaskParams planOnlyParams = (TerraformCloudPlanOnlyTaskParams) terraformCloudTaskParams;
        message = planOnlyParams.getMessage();
        workspace = planOnlyParams.getWorkspace();
        variables = getVariables(planOnlyParams.getVariables());
        builder.planOnly(true)
            .terraformVersion(planOnlyParams.getTerraformVersion())
            .isDestroy(planOnlyParams.getPlanType() == PlanType.DESTROY)
            .targets(planOnlyParams.getTargets())
            .build();
        break;
      case RUN_PLAN_AND_APPLY:
        TerraformCloudPlanAndApplyTaskParams planAndApplyParams =
            (TerraformCloudPlanAndApplyTaskParams) terraformCloudTaskParams;
        message = planAndApplyParams.getMessage();
        workspace = planAndApplyParams.getWorkspace();
        variables = getVariables(planAndApplyParams.getVariables());
        builder.planAndApply(true).autoApply(true).targets(planAndApplyParams.getTargets()).build();
        break;
      case RUN_PLAN_AND_DESTROY:
        TerraformCloudPlanAndDestroyTaskParams planAndDestroyParams =
            (TerraformCloudPlanAndDestroyTaskParams) terraformCloudTaskParams;
        message = planAndDestroyParams.getMessage();
        workspace = planAndDestroyParams.getWorkspace();
        variables = getVariables(planAndDestroyParams.getVariables());
        builder.planAndApply(true).autoApply(true).isDestroy(true).targets(planAndDestroyParams.getTargets()).build();
        break;
      case RUN_PLAN:
        TerraformCloudPlanTaskParams planParams = (TerraformCloudPlanTaskParams) terraformCloudTaskParams;
        message = planParams.getMessage();
        workspace = planParams.getWorkspace();
        variables = getVariables(planParams.getVariables());
        builder.isDestroy(planParams.getPlanType() == PlanType.DESTROY).targets(planParams.getTargets()).build();
        break;
      default:
        throw new InvalidRequestException(format("Can't create Run request for %s type", terraformCloudTaskType));
    }
    builder.message(message).variables(variables);
    createRunData.setAttributes(builder.build());
    createRunData.setRelationships(Collections.singletonMap(WORKSPACE.getRelationshipName(),
        SingleRelationship.builder().data(ResourceLinkage.builder().id(workspace).type(WORKSPACES).build()).build()));
    return RunRequest.builder().data(createRunData).build();
  }

  public RunRequest mapRunDataToRunRequest(RunData runData, String message, RollbackType rollbackType) {
    CreateRunData createRunData = new CreateRunData();
    createRunData.setAttributes(
        Attributes.builder()
            .planAndApply(true)
            .autoApply(true)
            .isDestroy(rollbackType == RollbackType.DESTROY || runData.getAttributes().isDestroy())
            .message(message)
            .variables(runData.getAttributes().getVariables())
            .targets(runData.getAttributes().getTargets())
            .build());
    Map<String, SingleRelationship> relationships = new HashMap<>();
    relationships.put(WORKSPACE.getRelationshipName(),
        SingleRelationship.builder()
            .data(ResourceLinkage.builder().id(helper.getRelationshipId(runData, WORKSPACE)).type(WORKSPACES).build())
            .build());
    relationships.put(CONFIG_VERSION.getRelationshipName(),
        SingleRelationship.builder()
            .data(ResourceLinkage.builder()
                      .id(helper.getRelationshipId(runData, CONFIG_VERSION))
                      .type(CONFIG_VERSIONS)
                      .build())
            .build());
    createRunData.setRelationships(relationships);
    return RunRequest.builder().data(createRunData).build();
  }

  private List<Variable> getVariables(Map<String, String> variables) {
    return isNotEmpty(variables)
        ? variables.entrySet()
              .stream()
              .map(entry -> Variable.builder().key(entry.getKey()).value(entry.getValue()).build())
              .collect(Collectors.toList())
        : Collections.emptyList();
  }
}
