/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.ecs;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.generateFileIdentifier;
import static io.harness.ngmigration.utils.MigratorUtility.getYamlManifestFile;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.ecs.EcsRunTaskStepInfo;
import io.harness.cdng.ecs.EcsRunTaskStepNode;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.MigrationInputDTO;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.CaseFormat;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GitFileConfig;
import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.EcsRunTaskDeploy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

@OwnedBy(HarnessTeam.CDC)
public class EcsRunTaskStepMapperImpl extends StepMapper {
  private static final String RUN_TASK_DEFINITION_JSON = "runTaskDefinition.json";
  private static final String RUN_TASK_REQUEST_DEFINITION_JSON = "runTaskRequestDefinition.json";

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.MANUAL_EFFORT;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.ECS;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.ECS_RUN_TASK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    EcsRunTaskDeploy state = new EcsRunTaskDeploy(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    EcsRunTaskDeploy state = (EcsRunTaskDeploy) getState(graphNode);
    EcsRunTaskStepNode stepNode = new EcsRunTaskStepNode();
    baseSetup(state, stepNode, context.getIdentifierCaseFormat());
    EcsRunTaskStepInfo stepInfo =
        EcsRunTaskStepInfo.infoBuilder()
            .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
            .skipSteadyStateCheck(ParameterField.createValueField(state.isSkipSteadyStateCheck()))
            .taskDefinition(ParameterField.createValueField(getTaskDefinition(state, context)))
            .runTaskRequestDefinition(ParameterField.createValueField(getRunTaskRequestDefinition(state, context)))
            .build();
    stepNode.setEcsRunTaskStepInfo(stepInfo);
    return stepNode;
  }

  private StoreConfigWrapper getTaskDefinition(EcsRunTaskDeploy state, WorkflowMigrationContext context) {
    if ("Inline".equals(state.getAddTaskDefinition())) {
      // inline
      List<String> fileNames = new ArrayList<>();
      fileNames.add("/"
          + getParameterFileName(context.getWorkflow().getName(), state.getName(), RUN_TASK_DEFINITION_JSON,
              context.getIdentifierCaseFormat()));
      return StoreConfigWrapper.builder()
          .type(StoreConfigType.HARNESS)
          .spec(HarnessStore.builder().files(ParameterField.createValueField(fileNames)).build())
          .build();
    } else {
      // remote
      GitStore store = GitStore.builder().connectorRef(MigratorUtility.RUNTIME_INPUT).build();

      GitFileConfig gitFileConfig = state.getGitFileConfig();
      if (gitFileConfig.isUseBranch() && StringUtils.isNotBlank(gitFileConfig.getBranch())) {
        store.setGitFetchType(FetchType.BRANCH);
        store.setBranch(ParameterField.createValueField(gitFileConfig.getBranch()));
      } else {
        store.setGitFetchType(FetchType.COMMIT);
        store.setCommitId(ParameterField.createValueField(gitFileConfig.getCommitId()));
      }
      if (isNotEmpty(gitFileConfig.getRepoName())) {
        store.setRepoName(ParameterField.createValueField(gitFileConfig.getRepoName()));
      }
      store.setPaths(ParameterField.createValueField(gitFileConfig.getFilePathList()));

      return StoreConfigWrapper.builder().type(StoreConfigType.GIT).spec(store).build();
    }
  }

  private StoreConfigWrapper getRunTaskRequestDefinition(EcsRunTaskDeploy state, WorkflowMigrationContext context) {
    List<String> fileNames = new ArrayList<>();
    fileNames.add("/"
        + getParameterFileName(context.getWorkflow().getName(), state.getName(), RUN_TASK_REQUEST_DEFINITION_JSON,
            context.getIdentifierCaseFormat()));
    return StoreConfigWrapper.builder()
        .type(StoreConfigType.HARNESS)
        .spec(HarnessStore.builder().files(ParameterField.createValueField(fileNames)).build())
        .build();
  }

  @Override
  public List<NGYamlFile> getChildNGYamlFiles(MigrationInputDTO inputDTO, GraphNode graphNode, String name) {
    List<NGYamlFile> result = new ArrayList<>();
    EcsRunTaskDeploy state = (EcsRunTaskDeploy) getState(graphNode);
    if ("Inline".equals(state.getAddTaskDefinition()) && isNotEmpty(state.getTaskDefinitionJson())) {
      byte[] fileContent = state.getTaskDefinitionJson().getBytes(StandardCharsets.UTF_8);
      NGYamlFile yamlConfigFile = getYamlManifestFile(inputDTO, fileContent,
          getParameterFileName(name, state.getName(), RUN_TASK_DEFINITION_JSON, inputDTO.getIdentifierCaseFormat()));
      if (null != yamlConfigFile) {
        result.add(yamlConfigFile);
      }
    }

    // Always create one empty file for EcsRunTaskRequest Definition
    byte[] fileContent = " ".getBytes();
    NGYamlFile yamlConfigFile = getYamlManifestFile(inputDTO, fileContent,
        getParameterFileName(
            name, state.getName(), RUN_TASK_REQUEST_DEFINITION_JSON, inputDTO.getIdentifierCaseFormat()));
    if (null != yamlConfigFile) {
      result.add(yamlConfigFile);
    }

    return result;
  }

  @NotNull
  private static String getParameterFileName(
      String workflowName, String stateName, String suffix, CaseFormat caseFormat) {
    String fileName = workflowName + "/" + stateName + "/" + suffix;
    return generateFileIdentifier(fileName, caseFormat);
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    EcsRunTaskDeploy state1 = (EcsRunTaskDeploy) getState(stepYaml1);
    EcsRunTaskDeploy state2 = (EcsRunTaskDeploy) getState(stepYaml2);
    return (state1.isSkipSteadyStateCheck() == state2.isSkipSteadyStateCheck())
        && StringUtils.equals(state1.getTaskDefinitionJson(), state2.getTaskDefinitionJson())
        && MigratorUtility.isGitFileConfigSimilar(state1.getGitFileConfig(), state2.getGitFileConfig());
  }
}
