/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.EntityType;
import software.wings.beans.FailureStrategy;
import software.wings.beans.Pipeline;
import software.wings.beans.Variable;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.service.intfc.ownership.OwnedByApplication;

import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.validation.Valid;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by anubhaw on 10/26/16.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface PipelineService extends OwnedByApplication {
  /**
   * List pipelines page response.
   *
   * @param pageRequest  the page request
   * @param hitSecondary
   * @param accountId
   * @return the page response
   */
  PageResponse<Pipeline> listPipelines(PageRequest<Pipeline> pageRequest, boolean hitSecondary, String accountId);

  PageResponse<Pipeline> listPipelines(PageRequest<Pipeline> pageRequest, boolean withDetails,
      Integer previousExecutionsCount, boolean withTags, String tagFilter);

  Pipeline getPipeline(String appId, String pipelineId);

  /**
   * Read pipeline pipeline.
   *
   * @param appId        the app id
   * @param pipelineId   the pipeline id
   * @param withServices the with services
   * @return the pipeline
   */
  Pipeline readPipeline(String appId, String pipelineId, boolean withServices);

  boolean pipelineExists(@NotEmpty String appId, @NotEmpty String pipelineId);
  /**
   * Read pipeline with variables.
   *
   * @param appId        the app id
   * @param pipelineId   the pipeline id
   * @return the pipeline
   */
  Pipeline readPipelineWithVariables(String appId, String pipelineId);

  /**
   * Reads pipeline with Services, EnvironmentIds, Pipeline Variables and Resolved Pipeline variables
   *
   * @return
   */

  Pipeline readPipelineResolvedVariablesLoopedInfo(
      String appId, String pipelineId, Map<String, String> workflowVariables);
  Pipeline readPipelineWithResolvedVariables(String appId, String pipelineId, Map<String, String> workflowVariables);

  Pipeline readPipelineResolvedVariablesLoopedInfo(
      String appId, String pipelineId, Map<String, String> pipelineVariables, boolean preExecutionChecks);

  Pipeline readPipelineWithResolvedVariables(
      String appId, String pipelineId, Map<String, String> pipelineVariables, boolean preExecutionChecks);

  Pipeline getPipelineByName(String appId, String pipelineName);

  void setPipelineDetails(List<Pipeline> pipelines, boolean withFinalValuesOnly);

  List<Variable> getPipelineVariables(String appId, String pipelineId);

  /**
   * Create pipeline pipeline.
   *
   * @param pipeline the pipeline
   * @return the pipeline
   */
  @ValidationGroups(Create.class) Pipeline save(@Valid Pipeline pipeline);

  /**
   * Update pipeline pipeline.
   *
   * @param pipeline the pipeline
   * @param migration
   * @param fromYaml
   * @return the pipeline
   */
  @ValidationGroups(Update.class) Pipeline update(@Valid Pipeline pipeline, boolean migration, boolean fromYaml);

  /**
   * Update pipeline failure strategies.
   *
   * @param appId the app id
   * @param pipelineId the pipeline id
   * @param failureStrategies the new set of failureStrategies
   * @return the pipeline
   */
  @ValidationGroups(Update.class)
  List<FailureStrategy> updateFailureStrategies(
      String appId, String pipelineId, List<FailureStrategy> failureStrategies);

  /**
   * Delete pipeline boolean.
   *
   * @param appId      the app id
   * @param pipelineId the pipeline id
   * @return the boolean
   */
  boolean deletePipeline(String appId, String pipelineId);

  /**
   * Prune pipeline descending objects.
   *
   * @param appId      the app id
   * @param pipelineId the pipeline id
   */
  void pruneDescendingEntities(@NotEmpty String appId, @NotEmpty String pipelineId);

  Pipeline clonePipeline(Pipeline originalPipeline, Pipeline pipeline);

  List<EntityType> getRequiredEntities(String appId, String pipelineId);

  void deleteByYamlGit(String appId, String pipelineId, boolean syncFromGit);

  String fetchPipelineName(@NotEmpty String appId, @NotEmpty String pipelineId);

  /***
   * It verifies if the Templated entityIds like Service Infrastructure and Service referenced in the pipeline template
   * @param appId
   * @param templatedEntityId
   * @return Returns the list of names
   */
  List<String> obtainPipelineNamesReferencedByTemplatedEntity(
      @NotEmpty String appId, @NotEmpty String templatedEntityId);

  /**
   * Check if environment is referenced
   *
   * @param appId app Id
   * @param envId env Id
   * @return List of referenced pipelines
   */
  List<String> obtainPipelineNamesReferencedByEnvironment(String appId, @NotEmpty String envId);

  DeploymentMetadata fetchDeploymentMetadata(String appId, Pipeline pipeline, Map<String, String> pipelineVariables);

  DeploymentMetadata fetchDeploymentMetadata(String appId, String pipelineId, Map<String, String> pipelineVariables,
      List<String> artifactNeededServiceIds, List<String> envIds, boolean withDefaultArtifact,
      WorkflowExecution workflowExecution, DeploymentMetadata.Include... includeList);

  DeploymentMetadata fetchDeploymentMetadata(String appId, String pipelineId, Map<String, String> pipelineVariables,
      List<String> artifactNeededServiceIds, List<String> envIds, DeploymentMetadata.Include... includeList);

  DeploymentMetadata fetchDeploymentMetadata(String appId, Pipeline pipeline, List<String> artifactNeededServiceIds,
      List<String> envIds, DeploymentMetadata.Include... includeList);

  /**
   *
   * Saves a list of pipelines
   * @param pipelines list of pipelines to be saved
   * @param skipValidations If set to true, directly saves the following pipelines to database. Doesn't perform any
   *     validations or post-save actions, so please set it to false to save anywhere directly from apis. Meant for
   *     saving pipelines after very minor and trivial changes only.
   */
  void savePipelines(@NotEmpty List<Pipeline> pipelines, boolean skipValidations);

  Set<String> getUserGroups(Pipeline pipeline);
}
