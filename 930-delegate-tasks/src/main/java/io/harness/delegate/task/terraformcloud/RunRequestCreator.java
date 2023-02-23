/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.terraformcloud;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.terraformcloud.PlanType;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskParams;
import io.harness.delegate.beans.terraformcloud.TerraformCloudTaskType;
import io.harness.exception.InvalidRequestException;
import io.harness.terraformcloud.model.Attributes;
import io.harness.terraformcloud.model.Attributes.AttributesBuilder;
import io.harness.terraformcloud.model.CreateRunData;
import io.harness.terraformcloud.model.ResourceLinkage;
import io.harness.terraformcloud.model.RunRequest;
import io.harness.terraformcloud.model.SingleRelationship;
import io.harness.terraformcloud.model.Variable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
@Singleton
public class RunRequestCreator {
  private static final String WORKSPACE = "workspace";
  private static final String WORKSPACES = "workspaces";

  public RunRequest createRunRequest(TerraformCloudTaskParams terraformCloudTaskParams) throws JsonProcessingException {
    CreateRunData createRunData = new CreateRunData();
    TerraformCloudTaskType terraformCloudTaskType = terraformCloudTaskParams.getTerraformCloudTaskType();
    AttributesBuilder builder = Attributes.builder();

    switch (terraformCloudTaskType) {
      case RUN_REFRESH_STATE:
        builder.refreshOnly(true).autoApply(true).build();
        break;
      case RUN_PLAN_ONLY:
        builder.planOnly(true)
            .terraformVersion(terraformCloudTaskParams.getTerraformVersion())
            .isDestroy(terraformCloudTaskParams.getPlanType() == PlanType.DESTROY)
            .targets(terraformCloudTaskParams.getTargets())
            .build();
        break;
      case RUN_PLAN_AND_APPLY:
        builder.planAndApply(true).autoApply(true).targets(terraformCloudTaskParams.getTargets()).build();
        break;
      case RUN_PLAN_AND_DESTROY:
        builder.planAndApply(true)
            .autoApply(true)
            .isDestroy(true)
            .targets(terraformCloudTaskParams.getTargets())
            .build();
        break;
      case RUN_PLAN:
        builder.isDestroy(terraformCloudTaskParams.getPlanType() == PlanType.DESTROY)
            .targets(terraformCloudTaskParams.getTargets())
            .build();
        break;
      default:
        throw new InvalidRequestException(format("Can't create Run request for %s type", terraformCloudTaskType));
    }
    builder.message(terraformCloudTaskParams.getMessage()).variables(getVariables(terraformCloudTaskParams));
    createRunData.setAttributes(builder.build());
    createRunData.setRelationships(Collections.singletonMap(WORKSPACE,
        SingleRelationship.builder()
            .data(ResourceLinkage.builder().id(terraformCloudTaskParams.getWorkspace()).type(WORKSPACES).build())
            .build()));
    return RunRequest.builder().data(createRunData).build();
  }

  private List<Variable> getVariables(TerraformCloudTaskParams terraformCloudTaskParams) {
    return isNotEmpty(terraformCloudTaskParams.getVariables())
        ? terraformCloudTaskParams.getVariables()
              .entrySet()
              .stream()
              .map(entry -> Variable.builder().key(entry.getKey()).value(entry.getValue()).build())
              .collect(Collectors.toList())
        : Collections.emptyList();
  }
}
