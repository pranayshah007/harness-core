/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.visitor.helpers.deploymentstage;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.executions.steps.StepSpecTypeConstants.CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT;

import static java.util.Objects.isNull;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.creator.plan.stage.DeploymentStageConfig;
import io.harness.cdng.customDeployment.FetchInstanceScriptStepNode;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.InvalidYamlException;
import io.harness.plancreator.execution.ExecutionElementConfig;
import io.harness.plancreator.execution.ExecutionWrapperConfig;
import io.harness.plancreator.steps.ParallelStepElementConfig;
import io.harness.plancreator.steps.StepGroupElementConfig;
import io.harness.pms.yaml.YamlUtils;
import io.harness.walktree.visitor.entityreference.EntityReferenceExtractor;
import io.harness.walktree.visitor.validation.ConfigValidator;
import io.harness.walktree.visitor.validation.ValidationVisitor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

@OwnedBy(CDC)
public class DeploymentStageVisitorHelper implements ConfigValidator, EntityReferenceExtractor {
  @Override
  public void validate(Object object, ValidationVisitor visitor) {
    // Nothing to validate
  }

  @Override
  public Object createDummyVisitableElement(Object originalElement) {
    return DeploymentStageConfig.builder().build();
  }
  @Override
  public Set<EntityDetailProtoDTO> addReference(Object object, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Map<String, Object> contextMap) {
    if (object instanceof DeploymentStageConfig) {
      DeploymentStageConfig stageConfig = (DeploymentStageConfig) object;
      if (stageConfig.getDeploymentType() == ServiceDefinitionType.CUSTOM_DEPLOYMENT) {
        ExecutionElementConfig executionElementConfig = stageConfig.getExecution();
        int fetchInstanceStepCount = getFetchInstanceStepCount(executionElementConfig.getSteps());
        if (fetchInstanceStepCount != 1) {
          throw new InvalidYamlException(
              "Fetch instance script step should be present only 1 time found: " + fetchInstanceStepCount);
        }
        int rollbackFetchInstanceStepCount = getFetchInstanceStepCount(executionElementConfig.getRollbackSteps());
        if (rollbackFetchInstanceStepCount > 1) {
          throw new InvalidYamlException("Rollback: Fetch instance script step should be present at max 1 time, found: "
              + rollbackFetchInstanceStepCount);
        }
      }
    }
    return Collections.emptySet();
  }

  private int getFetchInstanceStepCount(List<ExecutionWrapperConfig> steps) {
    if (isNull(steps)) {
      return 0;
    }
    int fetchInstanceScriptStepCount = 0;
    for (ExecutionWrapperConfig wrapperConfig : steps) {
      try {
        if (!isNull(wrapperConfig.getStep())) {
          if (wrapperConfig.getStep().has("type")
              && wrapperConfig.getStep().get("type").asText().equals(CUSTOM_DEPLOYMENT_FETCH_INSTANCE_SCRIPT)) {
            YamlUtils.read(wrapperConfig.getStep().toString(), FetchInstanceScriptStepNode.class);
            fetchInstanceScriptStepCount++;
          }
        } else if (!isNull(wrapperConfig.getParallel())) {
          ParallelStepElementConfig parallelSteps =
              YamlUtils.read(wrapperConfig.getParallel().toString(), ParallelStepElementConfig.class);
          fetchInstanceScriptStepCount += getFetchInstanceStepCount(parallelSteps.getSections());
        } else if (!isNull(wrapperConfig.getStepGroup())) {
          StepGroupElementConfig stepGroup =
              YamlUtils.read(wrapperConfig.getStepGroup().toString(), StepGroupElementConfig.class);
          fetchInstanceScriptStepCount += getFetchInstanceStepCount(stepGroup.getSteps());
        }
      } catch (IOException e) {
        throw new InvalidRequestException("Unable to parse these steps in yaml: " + wrapperConfig);
      }
    }
    return fetchInstanceScriptStepCount;
  }
}
