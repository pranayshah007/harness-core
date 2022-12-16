/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static software.wings.beans.VariableType.ARTIFACT;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.SweepingOutputInstance;
import io.harness.exception.InvalidRequestException;
import io.harness.serializer.KryoSerializer;

import software.wings.beans.ArtifactVariable;
import software.wings.beans.EntityType;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.VariableType;
import software.wings.beans.WorkflowExecution;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.sweepingoutput.SweepingOutputService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class MultiArtifactWorkflowExecutionServiceHelper {
  @Inject private SweepingOutputService sweepingOutputService;
  @Inject private ArtifactService artifactService;
  @Inject private KryoSerializer kryoSerializer;

  public void saveArtifactsToSweepingOutput(
      ExecutionArgs executionArgs, WorkflowExecution workflowExecution, String workflowId, String accountId) {
    Map<String, Object> workflowVariables = resolveArtifactVariables(executionArgs, workflowId, accountId);
    if (isNotEmpty(workflowVariables)) {
      sweepingOutputService.save(SweepingOutputServiceImpl
                                     .prepareSweepingOutputBuilder(workflowExecution.getAppId(), null,
                                         workflowExecution.getUuid(), null, null, SweepingOutputInstance.Scope.WORKFLOW)
                                     .name("artifacts")
                                     .output(kryoSerializer.asDeflatedBytes(workflowVariables))
                                     .build());
    }
  }

  private Map<String, Object> resolveArtifactVariables(
      ExecutionArgs executionArgs, String workflowId, String accountId) {
    Map<String, Object> variables = new HashMap<>();
    if (isEmpty(executionArgs.getArtifactVariables())) {
      return variables;
    }
    for (ArtifactVariable variable : executionArgs.getArtifactVariables()) {
      if (variable.getEntityType() == null) {
        throw new InvalidRequestException(
            "Artifact variable [" + variable.getName() + "] does not have an associated entity type", USER);
      }
      if (variable.getEntityId() == null) {
        throw new InvalidRequestException(
            "Artifact variable [" + variable.getName() + "] does not have an associated entity id", USER);
      }

      // process only artifact variables associated with workflow
      if (variable.getEntityType() == EntityType.WORKFLOW && variable.getEntityId().equals(workflowId)) {
        if (variable.isFixed()) {
          setVariables(variable.getName(), variable.getValue(), variables, ARTIFACT, accountId);
          continue;
        }
        // no input from user
        if (variable.isMandatory() && isBlank(variable.getValue())) {
          throw new InvalidRequestException(
              "Workflow variable [" + variable.getName() + "] is mandatory for execution", USER);
        }
        // allowed list is empty
        if (isEmpty(variable.getAllowedList())) {
          throw new InvalidRequestException(
              "Workflow variable [" + variable.getName() + "] does not contain any artifact streams", USER);
        }

        String value = isBlank(variable.getValue()) ? "" : variable.getValue();
        Artifact artifact = setVariables(variable.getName(), value, variables, variable.getType(), accountId);
        // verify for allowed values
        if (artifact != null && !variable.getAllowedList().contains(artifact.getArtifactStreamId())) {
          throw new InvalidRequestException("Workflow variable value [" + variable.getValue()
              + "] is not in allowed values [" + variable.getAllowedList() + "]");
        }
      }
    }
    return variables;
  }

  private Artifact setVariables(
      String key, Object value, Map<String, Object> variableMap, VariableType variableType, String accountId) {
    if (!isEmpty(key) && !key.equals("null")) {
      if (variableType == ARTIFACT) {
        Artifact artifact = artifactService.get(accountId, String.valueOf(value));
        if (artifact != null) {
          variableMap.put(key, artifact);
          return artifact;
        }
      } else {
        variableMap.put(key, value);
      }
    }

    return null;
  }

  List<Artifact> filterArtifactsForExecution(
      List<ArtifactVariable> artifactVariables, WorkflowExecution workflowExecution, String accountId) {
    List<Artifact> artifacts = new ArrayList<>();
    if (isNotEmpty(artifactVariables)) {
      for (ArtifactVariable variable : artifactVariables) {
        if (variable.getEntityType() == null) {
          throw new InvalidRequestException(
              "Artifact variable [" + variable.getName() + "] does not have an associated entity type", USER);
        }
        if (variable.getEntityId() == null) {
          throw new InvalidRequestException(
              "Artifact variable [" + variable.getName() + "] does not have an associated entity id", USER);
        }
        switch (variable.getEntityType()) {
          case WORKFLOW:
            if (isNotEmpty(workflowExecution.getWorkflowIds())
                && workflowExecution.getWorkflowIds().contains(variable.getEntityId())) {
              findArtifactForArtifactVariable(accountId, artifacts, variable);
            }
            break;
          case ENVIRONMENT:
            if (isNotEmpty(workflowExecution.getEnvIds())
                && workflowExecution.getEnvIds().contains(variable.getEntityId())) {
              findArtifactForArtifactVariable(accountId, artifacts, variable);
            }
            break;
          case SERVICE:
            if (isNotEmpty(workflowExecution.getServiceIds())
                && workflowExecution.getServiceIds().contains(variable.getEntityId())) {
              findArtifactForArtifactVariable(accountId, artifacts, variable);
            }
            break;
          default:
            throw new InvalidRequestException(format("Unexpected value: %s", variable.getEntityType()), USER);
        }
      }
    }

    // TODO: remove collisions (only at the top level not overridden artifact variables) in filtered artifact variables.
    return artifacts;
  }

  private void findArtifactForArtifactVariable(String accountId, List<Artifact> artifacts, ArtifactVariable variable) {
    Artifact artifact = artifactService.get(accountId, variable.getValue());
    if (artifact == null) {
      throw new InvalidRequestException(format("Unable to get artifact for artifact variable: [%s], value: [%s]",
                                            variable.getName(), variable.getValue()),
          USER);
    }
    artifacts.add(artifact);
  }
}
