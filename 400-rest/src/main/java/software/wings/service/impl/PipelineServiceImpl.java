/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.beans.OrchestrationWorkflowType.ROLLING;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.eraro.ErrorCode.PIPELINE_EXECUTION_IN_PROGRESS;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.validation.Validator.notEmptyCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.EntityType.APPDYNAMICS_APPID;
import static software.wings.beans.EntityType.APPDYNAMICS_CONFIGID;
import static software.wings.beans.EntityType.ARTIFACT;
import static software.wings.beans.EntityType.ELK_CONFIGID;
import static software.wings.beans.EntityType.ENVIRONMENT;
import static software.wings.beans.EntityType.INFRASTRUCTURE_DEFINITION;
import static software.wings.beans.EntityType.NEWRELIC_CONFIGID;
import static software.wings.beans.EntityType.NEWRELIC_MARKER_CONFIGID;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.EntityType.SPLUNK_CONFIGID;
import static software.wings.beans.EntityType.USER_GROUP;
import static software.wings.beans.EntityType.valueOf;
import static software.wings.beans.Variable.VariableBuilder;
import static software.wings.beans.VariableType.ENTITY;
import static software.wings.beans.VariableType.TEXT;
import static software.wings.expression.ManagerExpressionEvaluator.getName;
import static software.wings.expression.ManagerExpressionEvaluator.matchesVariablePattern;
import static software.wings.service.impl.pipeline.PipelineServiceValidator.checkUniqueApprovalPublishedVariable;
import static software.wings.service.impl.pipeline.PipelineServiceValidator.validateStageName;
import static software.wings.service.impl.pipeline.PipelineServiceValidator.validateTemplateExpressions;
import static software.wings.service.impl.pipeline.PipelineServiceValidator.validateUserGroupExpression;
import static software.wings.sm.StateType.APPROVAL;
import static software.wings.sm.StateType.ENV_STATE;

import static com.google.common.base.Strings.isNullOrEmpty;
import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.lang.String.format;
import static java.lang.String.join;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageRequest.Option;
import io.harness.beans.PageResponse;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.expression.ExpressionEvaluator;
import io.harness.ff.FeatureFlagService;
import io.harness.limits.Action;
import io.harness.limits.ActionType;
import io.harness.limits.LimitCheckerFactory;
import io.harness.limits.LimitEnforcementUtils;
import io.harness.limits.checker.StaticLimitCheckerWithDecrement;
import io.harness.limits.counter.service.CounterSyncer;
import io.harness.mongo.MongoPersistence;
import io.harness.persistence.HIterator;
import io.harness.queue.QueuePublisher;
import io.harness.validation.Create;

import software.wings.api.DeploymentType;
import software.wings.beans.AccountEvent;
import software.wings.beans.AccountEventType;
import software.wings.beans.ArtifactVariable;
import software.wings.beans.CanaryOrchestrationWorkflow;
import software.wings.beans.EntityType;
import software.wings.beans.Event.Type;
import software.wings.beans.FailureStrategy;
import software.wings.beans.ManifestVariable;
import software.wings.beans.OrchestrationWorkflow;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.PipelineStage;
import software.wings.beans.PipelineStage.PipelineStageElement;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.DeploymentMetadata.Include;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.dl.WingsPersistence;
import software.wings.expression.ManagerExpressionEvaluator;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.service.impl.pipeline.PipelineServiceHelper;
import software.wings.service.impl.pipeline.PipelineServiceValidator;
import software.wings.service.impl.workflow.WorkflowServiceHelper;
import software.wings.service.impl.workflow.WorkflowServiceTemplateHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.UserGroupService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.ownership.OwnedByPipeline;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.sm.StateMachine;
import software.wings.sm.StateType;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by anubhaw on 10/26/16.
 */
@OwnedBy(CDC)
@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PipelineServiceImpl implements PipelineService {
  private static final String USER_GROUPS = "userGroups";
  private static final Set<Character> ALLOWED_CHARS_SET_PIPELINE_STAGE =
      Sets.newHashSet(Lists.charactersOf("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_ ()"));

  private static final String PIPELINE_ENV_STATE_VALIDATION_MESSAGE =
      "Some steps %s are found to be invalid/incomplete.";

  @Inject private AppService appService;
  @Inject private ExecutorService executorService;
  @Inject private TriggerService triggerService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private WorkflowService workflowService;
  @Inject private UserGroupService userGroupService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private YamlPushService yamlPushService;
  @Inject private WorkflowServiceHelper workflowServiceHelper;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private EnvironmentService environmentService;
  @Inject private LimitCheckerFactory limitCheckerFactory;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private CounterSyncer counterSyncer;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject private PipelineServiceValidator pipelineServiceValidator;

  @Inject private QueuePublisher<PruneEvent> pruneQueue;
  @Inject private HarnessTagService harnessTagService;
  @Inject private ResourceLookupService resourceLookupService;
  @Inject private MongoPersistence mongoPersistence;
  @Inject FeatureFlagService featureFlagService;

  /**
   * {@inheritDoc}
   */
  @Override
  public PageResponse<Pipeline> listPipelines(PageRequest<Pipeline> pageRequest) {
    return wingsPersistence.query(Pipeline.class, pageRequest);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public PageResponse<Pipeline> listPipelines(PageRequest<Pipeline> pageRequest, boolean withDetails,
      Integer previousExecutionsCount, boolean withTags, String tagFilter) {
    PageResponse<Pipeline> res =
        resourceLookupService.listWithTagFilters(pageRequest, tagFilter, EntityType.PIPELINE, withTags, false);

    List<Pipeline> pipelines = res.getResponse();
    if (withDetails) {
      setPipelineDetails(pipelines, false);
    }
    if (previousExecutionsCount != null && previousExecutionsCount > 0) {
      for (Pipeline pipeline : pipelines) {
        PageRequest<WorkflowExecution> innerPageRequest =
            aPageRequest()
                .withLimit(previousExecutionsCount.toString())
                .addFilter(WorkflowExecutionKeys.accountId, EQ, pipeline.getAccountId())
                .addFilter(WorkflowExecutionKeys.workflowId, EQ, pipeline.getUuid())
                .addFilter(WorkflowExecutionKeys.cdPageCandidate, EQ, Boolean.TRUE)
                .build();
        innerPageRequest.setOptions(Collections.singletonList(Option.SKIPCOUNT));
        try {
          List<WorkflowExecution> workflowExecutions =
              workflowExecutionService.listExecutions(innerPageRequest, false, false, false, false, false, true)
                  .getResponse();
          pipeline.setWorkflowExecutions(workflowExecutions);
        } catch (Exception e) {
          log.error("Failed to fetch recent executions for pipeline {}", pipeline, e);
        }
      }
    }
    return res;
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Pipeline update(Pipeline pipeline, boolean migration, boolean fromYaml) {
    validateTemplateExpressions(pipeline);

    validateUserGroupExpression(pipeline);

    validateStageName(pipeline, featureFlagService);

    Pipeline savedPipeline = wingsPersistence.getWithAppId(Pipeline.class, pipeline.getAppId(), pipeline.getUuid());
    notNullCheck("Pipeline not saved", savedPipeline, USER);

    if (!savedPipeline.getName().equals(pipeline.getName())) {
      validatePipelineNameForDuplicates(pipeline);
    }

    Set<String> keywords = pipeline.generateKeywords();
    checkUniquePipelineStepName(pipeline);
    checkUniqueApprovalPublishedVariable(pipeline);

    handlePipelineStageDeletion(pipeline, savedPipeline, fromYaml);

    ensurePipelineStageUuidAndParallelIndex(pipeline);

    validatePipeline(pipeline, keywords);

    Set<String> previousUserGroups = getUserGroups(savedPipeline);

    // TODO: remove this when all the needed verification is done from validatePipeline
    new StateMachine(pipeline, workflowService.stencilMap(pipeline.getAppId()));

    UpdateOperations<Pipeline> ops = wingsPersistence.createUpdateOperations(Pipeline.class);
    setUnset(ops, "description", pipeline.getDescription());
    setUnset(ops, "name", pipeline.getName());
    setUnset(ops, "pipelineStages", pipeline.getPipelineStages());
    setUnset(ops, "failureStrategies", pipeline.getFailureStrategies());
    setUnset(ops, "keywords", trimmedLowercaseSet(keywords));
    setUnset(ops, "rollbackPreviousStages", pipeline.rollbackPreviousStages);

    wingsPersistence.update(wingsPersistence.createQuery(Pipeline.class)
                                .filter("appId", pipeline.getAppId())
                                .filter(ID_KEY, pipeline.getUuid()),
        ops);

    Pipeline updatedPipeline = wingsPersistence.getWithAppId(Pipeline.class, pipeline.getAppId(), pipeline.getUuid());

    setSinglePipelineDetails(updatedPipeline, true);

    String accountId = appService.getAccountIdByAppId(pipeline.getAppId());
    boolean isRename = !savedPipeline.getName().equals(pipeline.getName());

    Set<String> currentUserGroups = getUserGroups(updatedPipeline);
    try {
      updatePipelineReferenceInUserGroup(
          previousUserGroups, currentUserGroups, accountId, updatedPipeline.getAppId(), updatedPipeline.getUuid());
    } catch (Exception e) {
      log.error("An error occurred when trying to reference this pipeline {} in userGroups ", pipeline.getUuid(), e);
    }

    if (isRename) {
      executorService.submit(() -> triggerService.updateByApp(pipeline.getAppId()));
    }

    if (!migration) {
      yamlPushService.pushYamlChangeSet(
          accountId, savedPipeline, updatedPipeline, Type.UPDATE, pipeline.isSyncFromGit(), isRename);
    }

    return updatedPipeline;
  }

  @Override
  public void savePipelines(List<Pipeline> pipelines, boolean skipValidations) {
    if (skipValidations) {
      wingsPersistence.save(pipelines);
    } else {
      pipelines.forEach(this::save);
    }
  }

  @VisibleForTesting
  void handlePipelineStageDeletion(Pipeline pipeline, Pipeline savedPipeline, boolean fromYaml) {
    List<PipelineStage> newPipelineStages = pipeline.getPipelineStages();
    List<PipelineStage> savedPipelineStages = savedPipeline.getPipelineStages();
    // Not deletion case also we dont want to handle this from yaml. YAML we consider that as source of truth
    if (fromYaml || isEmpty(newPipelineStages) || isEmpty(savedPipelineStages)
        || savedPipelineStages.size() <= newPipelineStages.size()) {
      return;
    }

    List<String> stepNames = newPipelineStages.stream()
                                 .map(pipelineStage -> pipelineStage.getPipelineStageElements().get(0).getName())
                                 .collect(toList());
    for (PipelineStage pipelineStage : savedPipelineStages) {
      String stepName = pipelineStage.getPipelineStageElements().get(0).getName();
      if (!stepNames.contains(stepName)) {
        // This stage is being deleted
        int indexDeleted = savedPipelineStages.indexOf(pipelineStage);
        if (newPipelineStages.size() - 1 >= indexDeleted && indexDeleted != 0) {
          PipelineStage before = newPipelineStages.get(indexDeleted - 1);
          PipelineStage after = newPipelineStages.get(indexDeleted);
          if (after.isParallel()) {
            int parallelIndexBefore = before.getPipelineStageElements().get(0).getParallelIndex();
            int parallelIndexAfter = after.getPipelineStageElements().get(0).getParallelIndex();
            if (parallelIndexAfter != parallelIndexBefore) {
              log.info("Pipeline Stage {} should not be parallel to pipeline stage {} in pipeline {}", after.getName(),
                  before.getName(), pipeline.getUuid());
              after.setParallel(false);
            }
          }
        }
      }
    }
  }

  public static void ensurePipelineStageUuidAndParallelIndex(Pipeline pipeline) {
    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    if (isEmpty(pipelineStages)) {
      return;
    }

    int parallelIndex = 0;
    String firstParallelStageName = pipelineStages.get(0).getName();
    for (PipelineStage stage : pipelineStages) {
      if (!stage.isParallel()) {
        ++parallelIndex;
      }
      if (isEmpty(stage.getName())) {
        stage.setName("STAGE " + parallelIndex);
      }
      if (!stage.isParallel()) {
        firstParallelStageName = stage.getName();
      } else {
        stage.setName(firstParallelStageName);
      }
      for (PipelineStageElement element : stage.getPipelineStageElements()) {
        // The UI or other agents my try to update the pipeline with new stages without uuid. This makes sure that
        // they all will have one.
        if (element.getUuid() == null) {
          element.setUuid(generateUuid());
        }
        // The parallel index is important to be correct, lets enforce it
        element.setParallelIndex(parallelIndex);
      }
    }
  }

  @Override
  public List<FailureStrategy> updateFailureStrategies(
      String appId, String pipelineId, List<FailureStrategy> failureStrategies) {
    Pipeline savedPipeline = wingsPersistence.getWithAppId(Pipeline.class, appId, pipelineId);
    notNullCheck("pipeline", savedPipeline);

    savedPipeline.setFailureStrategies(failureStrategies);
    Pipeline pipeline = update(savedPipeline, false, false);
    return pipeline.getFailureStrategies();
  }

  @Override
  public List<String> obtainPipelineNamesReferencedByEnvironment(String appId, String envId) {
    List<String> referencedPipelines = new ArrayList<>();
    try (HIterator<Pipeline> pipelineHIterator =
             new HIterator<>(wingsPersistence.createQuery(Pipeline.class).filter(PipelineKeys.appId, appId).fetch())) {
      while (pipelineHIterator.hasNext()) {
        Pipeline pipeline = pipelineHIterator.next();
      PIPELINE_STAGE_LOOP:
        for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
          for (PipelineStageElement pipelineStageElement : pipelineStage.getPipelineStageElements()) {
            // Env Id in pipeline
            if (ENV_STATE.name().equals(pipelineStageElement.getType())) {
              Workflow workflow = wingsPersistence.getWithAppId(
                  Workflow.class, appId, (String) pipelineStageElement.getProperties().get("workflowId"));
              if (!workflow.checkEnvironmentTemplatized()) {
                if (envId.equals(workflow.getEnvId())) {
                  referencedPipelines.add(pipeline.getName());
                  break PIPELINE_STAGE_LOOP;
                }
              } else if (pipelineStageElement.getWorkflowVariables() != null
                  && pipelineStageElement.getWorkflowVariables().values().contains(envId)) {
                referencedPipelines.add(pipeline.getName());
                break PIPELINE_STAGE_LOOP;
              }
            }
          }
        }
      }
    }
    return referencedPipelines;
  }

  @Override
  public Pipeline getPipeline(String appId, String pipelineId) {
    Pipeline pipeline = wingsPersistence.getWithAppId(Pipeline.class, appId, pipelineId);
    if (pipeline != null) {
      pipeline.setTagLinks(harnessTagService.getTagLinksWithEntityId(pipeline.getAccountId(), pipelineId));
    }
    return pipeline;
  }

  private void ensurePipelineSafeToDelete(Pipeline pipeline) {
    boolean runningExecutions =
        workflowExecutionService.runningExecutionsPresent(pipeline.getAppId(), pipeline.getUuid());
    if (!runningExecutions) {
      List<String> triggerNames;
      List<Trigger> triggers = triggerService.getTriggersHasPipelineAction(pipeline.getAppId(), pipeline.getUuid());
      if (isEmpty(triggers)) {
        return;
      }
      triggerNames = triggers.stream().map(Trigger::getName).collect(toList());
      throw new InvalidRequestException(
          format("Pipeline associated as a trigger action to triggers [%s]", join(", ", triggerNames)), USER);
    }
    throw new InvalidRequestException(
        format("Pipeline:[%s] couldn't be deleted", pipeline.getName()), PIPELINE_EXECUTION_IN_PROGRESS, USER);
  }

  @Override
  public boolean deletePipeline(String appId, String pipelineId) {
    return deletePipeline(appId, pipelineId, false, false);
  }

  private boolean deletePipeline(String appId, String pipelineId, boolean forceDelete, boolean syncFromGit) {
    Pipeline pipeline = wingsPersistence.getWithAppId(Pipeline.class, appId, pipelineId);
    if (pipeline == null) {
      return true;
    }
    Set<String> previousUserGroups = getUserGroups(pipeline);
    String accountId = appService.getAccountIdByAppId(pipeline.getAppId());
    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new Action(accountId, ActionType.CREATE_PIPELINE));

    boolean successful = LimitEnforcementUtils.withCounterDecrement(checker, () -> {
      if (!forceDelete) {
        ensurePipelineSafeToDelete(pipeline);
      }

      yamlPushService.pushYamlChangeSet(accountId, pipeline, null, Type.DELETE, syncFromGit, false);

      if (!prunePipeline(appId, pipelineId)) {
        throw new InvalidRequestException(
            String.format("Pipeline %s does not exist or might already be deleted.", pipeline.getName()));
      }

      return true;
    });
    if (successful) {
      try {
        updatePipelineReferenceInUserGroup(previousUserGroups, new HashSet<>(), accountId, appId, pipelineId);
      } catch (Exception e) {
        log.error("An error occurred when trying to reference this pipeline {} in userGroups ", pipeline.getUuid(), e);
      }
    }
    return successful;
  }

  @Override
  public void deleteByYamlGit(String appId, String pipelineId, boolean syncFromGit) {
    deletePipeline(appId, pipelineId, false, syncFromGit);
  }

  @Override
  public String fetchPipelineName(String appId, String pipelineId) {
    Pipeline pipeline = wingsPersistence.createQuery(Pipeline.class)
                            .project(Pipeline.NAME_KEY, true)
                            .filter(PipelineKeys.appId, appId)
                            .filter(Pipeline.ID_KEY2, pipelineId)
                            .get();
    notNullCheck("Pipeline does not exist", pipeline, USER);
    return pipeline.getName();
  }

  @Override
  public void pruneDescendingEntities(@NotEmpty String appId, @NotEmpty String pipelineId) {
    List<OwnedByPipeline> services =
        ServiceClassLocator.descendingServices(this, PipelineServiceImpl.class, OwnedByPipeline.class);
    PruneEntityListener.pruneDescendingEntities(services, descending -> descending.pruneByPipeline(appId, pipelineId));
  }

  @Override
  public void pruneByApplication(String appId) {
    List<Pipeline> pipelines = wingsPersistence.createQuery(Pipeline.class)
                                   .filter(PipelineKeys.appId, appId)
                                   .project(PipelineKeys.name, true)
                                   .project(PipelineKeys.accountId, true)
                                   .project(PipelineKeys.appId, true)
                                   .project(PipelineKeys.uuid, true)
                                   .asList();

    String accountId = null;
    for (Pipeline pipeline : pipelines) {
      accountId = pipeline.getAccountId();
      if (prunePipeline(appId, pipeline.getUuid())) {
        auditServiceHelper.reportDeleteForAuditing(appId, pipeline);
      }
      harnessTagService.pruneTagLinks(pipeline.getAccountId(), pipeline.getUuid());
    }

    if (StringUtils.isNotEmpty(accountId)) {
      counterSyncer.syncPipelineCount(accountId);
    }
  }

  @Override
  public Pipeline clonePipeline(Pipeline originalPipeline, Pipeline pipeline) {
    Pipeline clonedPipeline = originalPipeline.cloneInternal();
    clonedPipeline.setName(pipeline.getName());
    clonedPipeline.setDescription(pipeline.getDescription());
    return save(clonedPipeline);
  }

  @Override
  public List<EntityType> getRequiredEntities(String appId, String pipelineId) {
    Pipeline pipeline = wingsPersistence.getWithAppId(Pipeline.class, appId, pipelineId);
    notNullCheck("pipeline", pipeline, USER);
    List<EntityType> entityTypes = new ArrayList<>();
    for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
      for (PipelineStageElement pipelineStageElement : pipelineStage.getPipelineStageElements()) {
        if (pipelineStageElement.checkDisableAssertion()) {
          continue;
        }
        if (ENV_STATE.name().equals(pipelineStageElement.getType())) {
          Workflow workflow = workflowService.readWorkflowWithoutServices(
              pipeline.getAppId(), (String) pipelineStageElement.getProperties().get("workflowId"));
          OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
          if (orchestrationWorkflow != null) {
            if (BUILD == orchestrationWorkflow.getOrchestrationWorkflowType()) {
              return new ArrayList<>();
            }
            if (!entityTypes.contains(ARTIFACT)) {
              DeploymentMetadata deploymentMetadata = workflowService.fetchDeploymentMetadata(pipeline.getAppId(),
                  workflow, pipelineStageElement.getWorkflowVariables(), null, null, Include.ARTIFACT_SERVICE);
              if (deploymentMetadata != null) {
                if (isNotEmpty(deploymentMetadata.getArtifactRequiredServiceIds())) {
                  entityTypes.add(ARTIFACT);
                }
              }
            }
          }
        }
      }
    }
    return entityTypes;
  }

  /**
   * Read Pipeline with Services. It filters out the services that does not need artifact
   *
   * @param appId        the app id
   * @param pipelineId   the pipeline id
   * @param withServices the with services
   * @return
   */
  @Override
  public Pipeline readPipeline(String appId, String pipelineId, boolean withServices) {
    Pipeline pipeline = wingsPersistence.getWithAppId(Pipeline.class, appId, pipelineId);
    notNullCheck("Pipeline does not exist", pipeline, USER);
    if (withServices) {
      // Note: It filters out the services that does need artifact
      setServicesAndPipelineVariables(pipeline);
    }
    return pipeline;
  }

  @Override
  public boolean pipelineExists(String appId, String pipelineId) {
    return wingsPersistence.createQuery(Trigger.class)
               .filter(TriggerKeys.appId, appId)
               .filter(TriggerKeys.workflowId, pipelineId)
               .getKey()
        != null;
  }

  /**
   * Read Pipeline with Services. It filters out the services that does not need artifact
   *
   * @param appId      the app id
   * @param pipelineId the pipeline id
   * @return
   */
  @Override
  public Pipeline readPipelineWithVariables(String appId, String pipelineId) {
    Pipeline pipeline = wingsPersistence.getWithAppId(Pipeline.class, appId, pipelineId);
    notNullCheck("Pipeline does not exist", pipeline, USER);
    // Note: It filters out the services that does need artifact
    setPipelineDetails(Collections.singletonList(pipeline), true);
    return pipeline;
  }

  @Override
  public Pipeline readPipelineResolvedVariablesLoopedInfo(
      String appId, String pipelineId, Map<String, String> pipelineVariables) {
    Pipeline pipeline = readPipelineWithResolvedVariables(appId, pipelineId, pipelineVariables, false, null);

    // checking pipeline for loops and replacing looped states with multiple parallel states.
    PipelineServiceHelper.updatePipelineWithLoopedState(pipeline);
    return pipeline;
  }

  @Override
  public Pipeline readPipelineWithResolvedVariables(
      String appId, String pipelineId, Map<String, String> pipelineVariables) {
    return readPipelineWithResolvedVariables(appId, pipelineId, pipelineVariables, false, null);
  }

  @Override
  public Pipeline readPipelineWithResolvedVariables(
      String appId, String pipelineId, Map<String, String> pipelineVariables, boolean preExecutionChecks) {
    return readPipelineWithResolvedVariables(appId, pipelineId, pipelineVariables, preExecutionChecks, null);
  }

  @Override
  public Pipeline readPipelineResolvedVariablesLoopedInfo(
      String appId, String pipelineId, Map<String, String> pipelineVariables, boolean preExecutionChecks) {
    Pipeline pipeline =
        readPipelineWithResolvedVariables(appId, pipelineId, pipelineVariables, preExecutionChecks, null);
    // checking pipeline for loops and replacing looped states with multiple parallel states.
    PipelineServiceHelper.updatePipelineWithLoopedState(pipeline);
    return pipeline;
  }

  private Pipeline readPipelineWithResolvedVariables(String appId, String pipelineId,
      Map<String, String> pipelineVariables, boolean preExecutionChecks, Map<String, Workflow> workflowCache) {
    Pipeline pipeline = wingsPersistence.getWithAppId(Pipeline.class, appId, pipelineId);
    notNullCheck("Pipeline does not exist", pipeline, USER);
    readPipelineWithResolvedVariables(pipeline, pipelineVariables, workflowCache, preExecutionChecks);
    return pipeline;
  }

  private void readPipelineWithResolvedVariables(Pipeline pipeline, Map<String, String> pipelineVariables,
      Map<String, Workflow> workflowCache, boolean preExecutionChecks) {
    List<Service> services = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    List<String> envIds = new ArrayList<>();
    List<String> workflowIds = new ArrayList<>();
    List<String> infraMappingIds = new ArrayList<>();
    List<String> infraDefinitionIds = new ArrayList<>();
    Set<String> invalidStages = new HashSet<>();
    if (workflowCache == null) {
      workflowCache = new HashMap<>();
    }

    setSinglePipelineDetails(pipeline, false, workflowCache, true);
    validateMultipleValuesAllowed(pipeline, pipelineVariables);

    for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
      for (PipelineStageElement pipelineStageElement : pipelineStage.getPipelineStageElements()) {
        if (!ENV_STATE.name().equals(pipelineStageElement.getType())) {
          continue;
        }

        String workflowId = (String) pipelineStageElement.getProperties().get("workflowId");
        Workflow workflow = getWorkflowWithServices(pipeline, workflowCache, workflowId);
        if (!workflow.getOrchestrationWorkflow().isValid()) {
          invalidStages.add(pipelineStageElement.getName());
          pipelineStageElement.setValid(false);
          pipelineStageElement.setValidationMessage(workflow.getOrchestrationWorkflow().getValidationMessage());
          pipelineStage.setValid(false);
          pipelineStage.setValidationMessage(workflow.getOrchestrationWorkflow().getValidationMessage());
        }

        if (preExecutionChecks) {
          WorkflowServiceHelper.checkWorkflowVariablesOverrides(pipelineStageElement,
              workflow.getOrchestrationWorkflow().getUserVariables(), pipelineStageElement.getWorkflowVariables(),
              pipelineVariables);
        }

        Map<String, String> resolvedWorkflowStepVariables =
            WorkflowServiceHelper.overrideWorkflowVariables(workflow.getOrchestrationWorkflow().getUserVariables(),
                pipelineStageElement.getWorkflowVariables(), pipelineVariables);
        pipelineStageElement.setWorkflowVariables(resolvedWorkflowStepVariables);

        PipelineServiceHelper.updateLoopingInfo(pipelineStage, workflow, infraDefinitionIds);

        if (BUILD != workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType()) {
          resolveServices(services, serviceIds, resolvedWorkflowStepVariables, workflow);
          resolveInfraMappings(infraMappingIds, resolvedWorkflowStepVariables, workflow);
          if (!pipelineStage.isLooped()) {
            if (pipelineStageElement.checkDisableAssertion()) {
              try {
                resolveInfraDefinitions(infraDefinitionIds, resolvedWorkflowStepVariables, workflow);
              } catch (Exception ignored) {
              }
            } else {
              resolveInfraDefinitions(infraDefinitionIds, resolvedWorkflowStepVariables, workflow);
            }
          }
          if (pipelineStageElement.checkDisableAssertion()) {
            try {
              resolveEnvIds(envIds, resolvedWorkflowStepVariables, workflow);
            } catch (InvalidRequestException ignored) {
            }
          } else {
            resolveEnvIds(envIds, resolvedWorkflowStepVariables, workflow);
          }
        } else {
          pipeline.setHasBuildWorkflow(true);
        }
        if (!workflowIds.contains(workflowId)) {
          workflowIds.add(workflowId);
        }
      }
    }

    if (!invalidStages.isEmpty()) {
      pipeline.setValid(false);
      pipeline.setValidationMessage(format(PIPELINE_ENV_STATE_VALIDATION_MESSAGE, invalidStages.toString()));

      if (preExecutionChecks) {
        throw new InvalidRequestException(pipeline.getValidationMessage());
      }
    }

    pipeline.setServices(services);
    pipeline.setEnvIds(envIds);
    pipeline.setInfraMappingIds(infraMappingIds);
    pipeline.setInfraDefinitionIds(infraDefinitionIds);
    pipeline.setWorkflowIds(workflowIds);
  }

  @VisibleForTesting
  void validateMultipleValuesAllowed(Pipeline pipeline, Map<String, String> variableValues) {
    List<Variable> variables = pipeline.getPipelineVariables();
    if (isEmpty(variables) || isEmpty(variableValues)) {
      return;
    }
    for (Variable variable : variables) {
      if (variableValues.containsKey(variable.getName())) {
        String variableValue = variableValues.get(variable.getName());
        if (isNotEmpty(variableValue) && variableValue.contains(",") && !variable.isAllowMultipleValues()
            && variable.obtainEntityType() != null) {
          throw new InvalidRequestException(format("variable %s cannot take multiple values", variable.getName()));
        }
      }
    }
  }

  private List<Service> resolveServices(
      List<Service> services, List<String> serviceIds, Map<String, String> pseWorkflowVariables, Workflow workflow) {
    List<Service> resolvedServices = workflowService.getResolvedServices(workflow, pseWorkflowVariables);
    if (resolvedServices != null) {
      for (Service resolvedService : resolvedServices) {
        if (!serviceIds.contains(resolvedService.getUuid())) {
          services.add(resolvedService);
          serviceIds.add(resolvedService.getUuid());
        }
      }
    }
    return resolvedServices;
  }

  private void resolveInfraMappings(
      List<String> infraMappingIds, Map<String, String> pseWorkflowVariables, Workflow workflow) {
    List<String> resolvedInfraMappingIds = workflowService.getResolvedInfraMappingIds(workflow, pseWorkflowVariables);
    if (resolvedInfraMappingIds != null) {
      resolvedInfraMappingIds.stream()
          .filter(resolvedInfraId -> !infraMappingIds.contains(resolvedInfraId))
          .forEach(infraMappingIds::add);
    }
  }

  private void resolveInfraDefinitions(
      List<String> infraDefinitionIds, Map<String, String> pseWorkflowVariables, Workflow workflow) {
    List<String> resolvedInfraDefinitionIds =
        workflowService.getResolvedInfraDefinitionIds(workflow, pseWorkflowVariables, null);
    if (resolvedInfraDefinitionIds != null) {
      resolvedInfraDefinitionIds.stream()
          .filter(resolvedInfraId -> !infraDefinitionIds.contains(resolvedInfraId))
          .forEach(infraDefinitionIds::add);
    }
  }

  @Override
  public Pipeline getPipelineByName(String appId, String pipelineName) {
    return wingsPersistence.createQuery(Pipeline.class)
        .filter("appId", appId)
        .filter(PipelineKeys.name, pipelineName)
        .get();
  }

  @Override
  public void setPipelineDetails(List<Pipeline> pipelines, boolean withFinalValuesOnly) {
    for (Pipeline pipeline : pipelines) {
      setSinglePipelineDetails(pipeline, withFinalValuesOnly);
    }
  }

  @Override
  public List<Variable> getPipelineVariables(String appId, String pipelineId) {
    Pipeline pipeline = wingsPersistence.getWithAppId(Pipeline.class, appId, pipelineId);
    notNullCheck("Pipeline does not exist", pipeline, USER);

    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    List<Variable> pipelineVariables = new ArrayList<>();
    Map<String, Workflow> workflowCache = new HashMap<>();
    for (PipelineStage pipelineStage : pipelineStages) {
      for (PipelineStageElement pse : pipelineStage.getPipelineStageElements()) {
        if (pse.checkDisableAssertion()) {
          continue;
        } else if (ENV_STATE.name().equals(pse.getType())) {
          String workflowId = (String) pse.getProperties().get("workflowId");
          Workflow workflow = getWorkflow(pipeline, workflowCache, workflowId);
          setPipelineVariables(workflow, pse, pipelineVariables, true);
        } else if (APPROVAL.name().equals(pse.getType())) {
          setPipelineVariablesApproval(pse, pipelineVariables, pipelineStage.getName());
        }
      }
    }
    return pipelineVariables;
  }

  private void setSinglePipelineDetails(Pipeline pipeline, boolean withFinalValuesOnly) {
    Map<String, Workflow> workflowCache = new HashMap<>();
    setSinglePipelineDetails(pipeline, withFinalValuesOnly, workflowCache, false);
  }

  private void setSinglePipelineDetails(
      Pipeline pipeline, boolean withFinalValuesOnly, Map<String, Workflow> workflowCache, boolean withServices) {
    boolean hasSshInfraMapping = false;
    boolean templatized = false;
    boolean pipelineParameterized = false;
    Set<String> invalidStages = new HashSet<>();
    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    List<Variable> pipelineVariables = new ArrayList<>();
    List<DeploymentType> deploymentTypes = new ArrayList<>();
    for (PipelineStage pipelineStage : pipelineStages) {
      for (PipelineStageElement pse : pipelineStage.getPipelineStageElements()) {
        if (pse.checkDisableAssertion()) {
          continue;
        } else if (ENV_STATE.name().equals(pse.getType())) {
          try {
            String workflowId = (String) pse.getProperties().get("workflowId");
            Workflow workflow = withServices ? getWorkflowWithServices(pipeline, workflowCache, workflowId)
                                             : getWorkflow(pipeline, workflowCache, workflowId);

            if (!hasSshInfraMapping) {
              hasSshInfraMapping = workflowServiceHelper.workflowHasSshDeploymentPhase(
                  (CanaryOrchestrationWorkflow) workflow.getOrchestrationWorkflow());
            }
            deploymentTypes.addAll(workflowServiceHelper.obtainDeploymentTypes(workflow.getOrchestrationWorkflow()));
            if (!templatized && isNotEmpty(pse.getWorkflowVariables())) {
              templatized = true;
            }

            if (!workflow.getOrchestrationWorkflow().isValid()) {
              invalidStages.add(pse.getName());
              pse.setValid(false);
              pse.setValidationMessage(workflow.getOrchestrationWorkflow().getValidationMessage());
              pipelineStage.setValid(false);
              pipelineStage.setValidationMessage(workflow.getOrchestrationWorkflow().getValidationMessage());
            }

            validateWorkflowVariables(workflow, pse, pipelineStage, invalidStages);
            setPipelineVariables(workflow, pse, pipelineVariables, withFinalValuesOnly);
            if (!pipelineParameterized) {
              pipelineParameterized = checkPipelineEntityParameterized(pse.getWorkflowVariables(), workflow);
            }
          } catch (Exception ex) {
            log.warn(
                format("Exception occurred while reading workflow associated to the pipeline %s", pipeline.toString()),
                ex);
          }
        } else if (APPROVAL.name().equals(pse.getType())) {
          setPipelineVariablesApproval(pse, pipelineVariables, pipelineStage.getName());
        }
      }
    }
    if (!invalidStages.isEmpty()) {
      pipeline.setValid(false);
      pipeline.setValidationMessage(format(PIPELINE_ENV_STATE_VALIDATION_MESSAGE, invalidStages.toString()));
    }

    pipeline.setPipelineVariables(reorderPipelineVariables(pipelineVariables));
    pipeline.setHasSshInfraMapping(hasSshInfraMapping);
    pipeline.setEnvParameterized(pipelineParameterized);
    pipeline.setTemplatized(templatized);
    pipeline.setDeploymentTypes(deploymentTypes.stream().distinct().collect(toList()));
  }

  private Workflow getWorkflow(Pipeline pipeline, Map<String, Workflow> workflowCache, String workflowId) {
    Workflow workflow;
    if (workflowCache.containsKey(workflowId)) {
      workflow = workflowCache.get(workflowId);
    } else {
      workflow = workflowService.readWorkflowWithoutServices(pipeline.getAppId(), workflowId);
      requireOrchestrationWorkflow(workflow);
      workflowCache.put(workflowId, workflow);
    }
    return workflow;
  }

  private Workflow getWorkflowWithServices(Pipeline pipeline, Map<String, Workflow> workflowCache, String workflowId) {
    Workflow workflow;
    if (workflowCache.containsKey(workflowId)) {
      workflow = workflowCache.get(workflowId);
    } else {
      workflow = workflowService.readWorkflow(pipeline.getAppId(), workflowId);
      requireOrchestrationWorkflow(workflow);
      workflowCache.put(workflowId, workflow);
    }
    return workflow;
  }

  private List<Variable> reorderPipelineVariables(List<Variable> pipelineVariables) {
    // Reorder pipeline variables
    List<Variable> reorderedPipelineVariables = new ArrayList<>();
    List<Variable> nonEntityVariables =
        pipelineVariables.stream().filter(variable -> variable.obtainEntityType() == null).collect(toList());
    List<Variable> entityVariables =
        pipelineVariables.stream().filter(variable -> variable.obtainEntityType() != null).collect(toList());
    reorderedPipelineVariables.addAll(entityVariables);
    reorderedPipelineVariables.addAll(nonEntityVariables);
    return reorderedPipelineVariables;
  }

  private boolean checkPipelineEntityParameterized(Map<String, String> pseWorkflowVaraibles, Workflow workflow) {
    List<Variable> workflowVariables = workflow.getOrchestrationWorkflow().getUserVariables();
    if (isEmpty(workflowVariables) || isEmpty(pseWorkflowVaraibles)) {
      return false;
    }
    boolean atleastOneEntityParameterized;
    List<Variable> entityVariables =
        workflowVariables.stream().filter(variable -> variable.obtainEntityType() != null).collect(toList());
    for (Variable variable : entityVariables) {
      String value = pseWorkflowVaraibles.get(variable.getName());
      if (value != null) {
        atleastOneEntityParameterized = matchesVariablePattern(value);
        if (atleastOneEntityParameterized) {
          return true;
        }
      }
    }
    return false;
  }

  @VisibleForTesting
  void setServicesAndPipelineVariables(Pipeline pipeline) {
    List<String> serviceIds = new ArrayList<>();
    List<String> envIds = new ArrayList<>();
    List<Variable> pipelineVariables = new ArrayList<>();
    boolean templatized = false;
    boolean envParameterized = false;
    boolean hasBuildWorkflow = false;
    List<DeploymentType> deploymentTypes = new ArrayList<>();
    Set<String> invalidStages = new HashSet<>();
    Map<String, Workflow> workflowCache = new HashMap<>();
    for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
      for (PipelineStageElement pse : pipelineStage.getPipelineStageElements()) {
        if (ENV_STATE.name().equals(pse.getType())) {
          if (pse.checkDisableAssertion()) {
            continue;
          }

          String workflowId = (String) pse.getProperties().get("workflowId");
          Workflow workflow = getWorkflow(pipeline, workflowCache, workflowId);

          if (!workflow.getOrchestrationWorkflow().isValid()) {
            invalidStages.add(pse.getName());
            pse.setValid(false);
            pse.setValidationMessage(workflow.getOrchestrationWorkflow().getValidationMessage());
            pipelineStage.setValid(false);
            pipelineStage.setValidationMessage(workflow.getOrchestrationWorkflow().getValidationMessage());
          }

          if (!hasBuildWorkflow) {
            hasBuildWorkflow = BUILD == workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType();
          }
          resolveArtifactAndManifestNeededServicesOfWorkflowAndEnvIds(
              serviceIds, envIds, deploymentTypes, pse.getWorkflowVariables(), workflow);

          validateWorkflowVariables(workflow, pse, pipelineStage, invalidStages);

          setPipelineVariables(workflow, pse, pipelineVariables, false);
          if (!templatized && isNotEmpty(pse.getWorkflowVariables())) {
            templatized = true;
          }
          if (!envParameterized) {
            envParameterized = checkPipelineEntityParameterized(pse.getWorkflowVariables(), workflow);
          }
        } else if (APPROVAL.name().equals(pse.getType())) {
          setPipelineVariablesApproval(pse, pipelineVariables, pipelineStage.getName());
        }
      }
    }
    if (!invalidStages.isEmpty()) {
      pipeline.setValid(false);
      pipeline.setValidationMessage(format(PIPELINE_ENV_STATE_VALIDATION_MESSAGE, invalidStages.toString()));
    }

    pipeline.setServices(serviceResourceService.fetchServicesByUuids(pipeline.getAppId(), serviceIds));
    pipeline.setPipelineVariables(reorderPipelineVariables(pipelineVariables));
    pipeline.setEnvSummaries(environmentService.obtainEnvironmentSummaries(pipeline.getAppId(), envIds));
    pipeline.setTemplatized(templatized);
    pipeline.setEnvParameterized(envParameterized);
    pipeline.setDeploymentTypes(deploymentTypes);
    pipeline.setHasBuildWorkflow(hasBuildWorkflow);
    pipeline.setEnvIds(envIds);
  }

  @VisibleForTesting
  void setPipelineVariables(
      Workflow workflow, PipelineStageElement pse, List<Variable> pipelineVariables, boolean withFinalValuesOnly) {
    List<Variable> workflowVariables = workflow.getOrchestrationWorkflow().getUserVariables();
    if (isEmpty(workflowVariables)) {
      return;
    }
    Map<String, String> pseWorkflowVariables = pse.getWorkflowVariables();
    List<Variable> nonEntityVariables =
        workflowVariables.stream()
            .filter(variable -> variable.obtainEntityType() == null && !variable.isFixed())
            .collect(toList());

    if (isEmpty(pseWorkflowVariables)) {
      if (!isEmpty(workflowVariables)) {
        nonEntityVariables.forEach(variable -> {
          if (!contains(pipelineVariables, variable.getName())) {
            Variable cloned = variable.cloneInternal();
            cloned.setRuntimeInput(false);
            pipelineVariables.add(cloned);
          } else {
            mergeNonEntityPipelineVariables(variable, false, pipelineVariables, variable.getName(), variable.getName());
          }
        });
      }
      return;
    }
    int infraVarsCount = Math.toIntExact(
        workflowVariables.stream().filter(t -> INFRASTRUCTURE_DEFINITION == t.obtainEntityType()).count());
    List<String> runtimeVariables = pse.getRuntimeInputsConfig() != null
        ? pse.getRuntimeInputsConfig().getRuntimeInputVariables()
        : new ArrayList<>();
    for (Variable variable : workflowVariables) {
      notEmptyCheck("Empty variable name", variable.getName());
      String value = pseWorkflowVariables.get(variable.getName());
      boolean isRuntime = false;
      if (isNotEmpty(runtimeVariables) && runtimeVariables.contains(variable.getName())) {
        isRuntime = true;
      }
      if (variable.obtainEntityType() == null) {
        handleNonEntityVariables(pipelineVariables, variable, value, isRuntime);
      } else {
        boolean allowMulti = false;
        if ((infraVarsCount == 1 && INFRASTRUCTURE_DEFINITION == variable.obtainEntityType())
            || USER_GROUP == variable.obtainEntityType()) {
          allowMulti = true;
        }
        // Entity variables.
        handleEntityVariables(pipelineVariables, withFinalValuesOnly, workflowVariables, pseWorkflowVariables, variable,
            allowMulti, isRuntime);
      }
    }
  }

  @VisibleForTesting
  void setPipelineVariablesApproval(PipelineStageElement pse, List<Variable> pipelineVariables, String stageName) {
    Map<String, Object> properties = pse.getProperties();

    Object obj = properties.get("templateExpressions");
    if (obj instanceof List) {
      List<Map<String, Object>> templateExpressions = (List<Map<String, Object>>) obj;

      if (templateExpressions != null) {
        addToUserVariablePipelineApproval(templateExpressions, pipelineVariables, pse.getType(), APPROVAL.name());
      }
    }
  }

  private void addToUserVariablePipelineApproval(List<Map<String, Object>> templateExpressions,
      List<Variable> pipelineVariables, String stageName, String stateType) {
    for (Map<String, Object> templateExpression : templateExpressions) {
      EntityType entityType = null;
      Map<String, Object> metadata = (Map<String, Object>) templateExpression.get("metadata");
      if (metadata != null) {
        if (metadata.get("entityType") != null) {
          entityType = valueOf((String) metadata.get(Variable.ENTITY_TYPE));
        }
      }
      if (USER_GROUP != entityType) {
        throw new InvalidRequestException("Approval can only have User Group template expression");
      }
      String expression = (String) templateExpression.get("expression");
      Matcher matcher = ManagerExpressionEvaluator.wingsVariablePattern.matcher(expression);
      if (matcher.matches()) {
        expression = validateAndGetVariablePipeLine(matcher.group(0).substring(2, matcher.group(0).length() - 1));
      }
      Variable variable = getContainedVariable(pipelineVariables, expression);

      if (variable == null) {
        VariableBuilder variableBuilder = VariableBuilder.aVariable()
                                              .name(expression)
                                              .entityType(entityType)
                                              .type(entityType != null ? ENTITY : TEXT)
                                              .allowMultipleValues(entityType == USER_GROUP)
                                              .mandatory(entityType != null);

        if (isNotEmpty(stateType)) {
          variableBuilder.stateType(stateType);
        }
        // Set the description
        variable = variableBuilder.build();
        setVariableDescription(variable, stageName);
        pipelineVariables.add(variable);
      }
    }
  }

  private String validateAndGetVariablePipeLine(String substring) {
    Matcher matcher = ManagerExpressionEvaluator.variableNamePattern.matcher(substring);
    if (!matcher.matches()) {
      throw new InvalidRequestException("Template variable:[" + substring
              + "] not in proper format ,should start with ${ and end with }, only a-zA-Z0-9_ - allowed",
          USER);
    }
    return substring;
  }

  private void setVariableDescription(Variable variable, String stateName) {
    variable.setDescription(
        WorkflowServiceTemplateHelper.getVariableDescription(variable.obtainEntityType(), null, stateName));
  }

  private void handleEntityVariables(List<Variable> pipelineVariables, boolean withFinalValuesOnly,
      List<Variable> workflowVariables, Map<String, String> pseWorkflowVariables, Variable variable, boolean allowMulti,
      boolean isRuntime) {
    String value = pseWorkflowVariables.get(variable.getName());
    if (isNotEmpty(value)) {
      String variableName = matchesVariablePattern(value) && !value.contains(".") ? getName(value) : null;
      if (variableName != null) {
        // Variable is an expression - templatized pipeline.
        Variable pipelineVariable = variable.cloneInternal();
        pipelineVariable.setName(variableName);
        EntityType entityType = pipelineVariable.obtainEntityType();

        setParentAndRelatedFields(workflowVariables, pseWorkflowVariables, variable, pipelineVariable, entityType);
        if (!contains(pipelineVariables, variableName)) {
          // Variable is an expression so prompt for the value at runtime.
          if (withFinalValuesOnly) {
            // If only final concrete values are needed, set value as null as the used has not entered them yet.
            pipelineVariable.setValue(null);
          } else {
            // Set variable value as workflow templatized variable name.
            pipelineVariable.setValue(variable.getName());
          }
          pipelineVariable.setAllowMultipleValues(allowMulti);
          pipelineVariable.setRuntimeInput(isRuntime);
          pipelineVariables.add(pipelineVariable);
        } else {
          updateStoredVariable(pipelineVariables, workflowVariables, pseWorkflowVariables, pipelineVariable,
              variableName, allowMulti, isRuntime);
        }
      }
    }
  }

  private static void setParentAndRelatedFields(List<Variable> workflowVariables,
      Map<String, String> pseWorkflowVariables, Variable variable, Variable pipelineVariable, EntityType entityType) {
    if (ENVIRONMENT == entityType) {
      setRelatedFieldEnvironment(workflowVariables, pseWorkflowVariables, pipelineVariable);
    } else {
      cloneRelatedFieldName(pseWorkflowVariables, pipelineVariable);
    }
    populateParentFields(pipelineVariable, entityType, workflowVariables, variable.getName(), pseWorkflowVariables);
  }

  public static void setParentAndRelatedFieldsForRuntime(List<Variable> workflowVariables,
      Map<String, String> pseWorkflowVariables, String varName, Variable pipelineVariable, EntityType entityType) {
    if (ENVIRONMENT != entityType) {
      cloneRelatedFieldNameForRuntimeVars(pseWorkflowVariables, pipelineVariable);
    }
    populateParentFields(pipelineVariable, entityType, workflowVariables, varName, pseWorkflowVariables);
  }

  private void handleNonEntityVariables(
      List<Variable> pipelineVariables, Variable variable, String value, boolean isRuntime) {
    // Non-entity variables. Here we can handle values like ${myTeam} for non entity variables
    if (!variable.isFixed()) {
      Variable newVar = null;
      String variableName = variable.getName();
      if (isEmpty(value)) {
        newVar = variable.cloneInternal();
      } else if (matchesVariablePattern(value) && !value.contains(".")) {
        variableName = getName(value);
        newVar = variable.cloneInternal();
        newVar.setName(variableName);
      }
      mergeNonEntityPipelineVariables(newVar, isRuntime, pipelineVariables, variableName, variable.getName());
    }
  }

  @VisibleForTesting
  void mergeNonEntityPipelineVariables(
      Variable newVar, boolean isRuntime, List<Variable> pipelineVariables, String variableName, String originalName) {
    if (newVar != null) {
      if (!contains(pipelineVariables, variableName)) {
        pipelineVariables.add(newVar);
        newVar.setRuntimeInput(isRuntime);
      } else {
        Variable existingVar =
            pipelineVariables.stream().filter(t -> t.getName().equals(variableName)).findFirst().orElse(null);
        if (existingVar != null) {
          mergeRequired(existingVar, newVar);
          mergeAllowedValuesAndList(existingVar, newVar);
          checkRuntime(existingVar, isRuntime);
          overWriteDefaultValue(existingVar, newVar.getValue());
        }
      }
    }
  }

  private void overWriteDefaultValue(Variable existingVar, String value) {
    if (value != null && !value.equals("")) {
      if (existingVar.getAllowedList() == null) {
        existingVar.setValue(value);
      } else if (existingVar.getAllowedList().contains(value)) {
        existingVar.setValue(value);
      }
    }
  }

  private void mergeAllowedValuesAndList(Variable existingVar, Variable newVar) {
    if (newVar.getAllowedList() != null) {
      if (existingVar.getAllowedList() == null) {
        existingVar.setAllowedList(newVar.getAllowedList());
      }
      List<String> newAllowedList = existingVar.getAllowedList()
                                        .stream()
                                        .distinct()
                                        .filter(newVar.getAllowedList()::contains)
                                        .collect(Collectors.toList());
      existingVar.setAllowedList(newAllowedList);
      existingVar.setAllowedValues(join(",", existingVar.getAllowedList()));
      if (existingVar.getAllowedList() != null && existingVar.getAllowedList().size() == 0) {
        throw new InvalidRequestException(String.format(
            "Variable %s does not have any common allowed values between all stages", existingVar.getName()));
      }
      if (existingVar.getValue() != null && (!existingVar.getAllowedList().contains(existingVar.getValue()))) {
        existingVar.setValue(null);
      }
    }
  }

  private void checkRuntime(Variable existingVar, boolean isRuntime) {
    if (existingVar.getRuntimeInput() == null) {
      existingVar.setRuntimeInput(isRuntime);
    } else if (existingVar.getRuntimeInput() != isRuntime) {
      throw new InvalidRequestException(
          String.format("Variable %s is not marked as runtime in all pipeline stages", existingVar.getName()));
    }
  }

  private void mergeRequired(Variable existingVar, Variable newVar) {
    if (existingVar.isMandatory()) {
      return;
    }
    existingVar.setMandatory(newVar.isMandatory());
  }

  @VisibleForTesting
  void updateStoredVariable(List<Variable> pipelineVariables, List<Variable> workflowVariables,
      Map<String, String> pseWorkflowVariables, Variable variable, String variableName, boolean allowMulti,
      boolean isRuntime) {
    Variable storedVar = getContainedVariable(pipelineVariables, variableName);
    if (storedVar != null) {
      if (ENVIRONMENT == storedVar.obtainEntityType()) {
        updateRelatedFieldEnvironment(workflowVariables, pseWorkflowVariables, storedVar);
      } else if (SERVICE == storedVar.obtainEntityType()) {
        mergeMetadataServiceVariable(variable, storedVar);
      } else if (INFRASTRUCTURE_DEFINITION == storedVar.obtainEntityType()) {
        storedVar.setAllowMultipleValues(allowMulti && storedVar.isAllowMultipleValues());
        mergeMetadataInfraVariable(variable, storedVar);
      }
      if (storedVar.getRuntimeInput() == null) {
        storedVar.setRuntimeInput(isRuntime);
      } else if (storedVar.getRuntimeInput() != isRuntime) {
        throw new InvalidRequestException(
            String.format("Variable %s is not marked as runtime in all pipeline stages", variable.getName()));
      }
    }
  }

  private void mergeMetadataServiceVariable(Variable variable, Variable storedVar) {
    if (isEmpty(storedVar.getMetadata())) {
      throw new UnexpectedException(
          "Service variable" + storedVar.getName() + " stored without any AtifactType and Metadata");
    }
    validateArtifactType(storedVar.obtainArtifactTypeField(), variable.obtainArtifactTypeField(), storedVar.getName());

    String newRelatedFiledVal = joinFieldValuesMetadata(storedVar.obtainRelatedField(), variable.obtainRelatedField());
    if (isNotEmpty(newRelatedFiledVal)) {
      storedVar.getMetadata().put(Variable.RELATED_FIELD, newRelatedFiledVal);
    } else {
      storedVar.getMetadata().remove(Variable.RELATED_FIELD);
    }

    String newDeploymentTypedVal =
        joinFieldValuesMetadata(storedVar.obtainDeploymentTypeField(), variable.obtainDeploymentTypeField());
    if (isNotEmpty(newDeploymentTypedVal)) {
      storedVar.getMetadata().put(Variable.DEPLOYMENT_TYPE, newDeploymentTypedVal);
    } else {
      storedVar.getMetadata().remove(Variable.DEPLOYMENT_TYPE);
    }

    String mergedInfraIdVal = joinFieldValuesMetadata(storedVar.obtainInfraIdField(), variable.obtainInfraIdField());
    if (isNotEmpty(mergedInfraIdVal)) {
      storedVar.getMetadata().put(Variable.INFRA_ID, mergedInfraIdVal);
    } else {
      storedVar.getMetadata().remove(Variable.INFRA_ID);
    }
  }

  private void validateArtifactType(String storedArtifactTypeField, String newArtifactTypeField, String varName) {
    if (isNotEmpty(storedArtifactTypeField) && isNotEmpty(newArtifactTypeField)) {
      if (!storedArtifactTypeField.equals(newArtifactTypeField)) {
        throw new InvalidRequestException("The same Workflow variable name " + varName
                + " cannot be used for Services using different Artifact types. Change the name of the variable in one or more Workflow.",
            USER);
      }
    }
  }

  private void mergeMetadataInfraVariable(Variable variable, Variable storedVar) {
    if (isEmpty(storedVar.getMetadata())) {
      throw new UnexpectedException("Infra variable" + storedVar.getName() + " stored without any Metadata");
    }
    validateEnvId(storedVar.obtainEnvIdField(), variable.obtainEnvIdField(), storedVar.getName());
    validateDeploymentType(
        storedVar.obtainDeploymentTypeField(), variable.obtainDeploymentTypeField(), storedVar.getName());

    String newRelatedFiledVal = joinFieldValuesMetadata(storedVar.obtainRelatedField(), variable.obtainRelatedField());
    if (isNotEmpty(newRelatedFiledVal)) {
      storedVar.getMetadata().put(Variable.RELATED_FIELD, newRelatedFiledVal);
    } else {
      storedVar.getMetadata().remove(Variable.RELATED_FIELD);
    }

    String mergedServiceIdsVal =
        joinFieldValuesMetadata(storedVar.obtainServiceIdField(), variable.obtainServiceIdField());
    if (isNotEmpty(mergedServiceIdsVal)) {
      storedVar.getMetadata().put(Variable.SERVICE_ID, mergedServiceIdsVal);
    } else {
      storedVar.getMetadata().remove(Variable.SERVICE_ID);
    }
  }

  private void validateDeploymentType(String storedDeploymentTypeField, String newDeploymentTypeField, String name) {
    if (isEmpty(storedDeploymentTypeField) || isEmpty(newDeploymentTypeField)) {
      return;
    }
    if (!storedDeploymentTypeField.equals(newDeploymentTypeField)) {
      throw new InvalidRequestException("The same Workflow variable name " + name
              + " cannot be used for InfraDefinitions using different DeploymentType. Change the name of the variable in one or more Workflow.",
          USER);
    }
  }

  private void validateEnvId(String storedEnvIdField, String newEnvIdField, String varName) {
    if (isEmpty(storedEnvIdField) || isEmpty(newEnvIdField)) {
      return;
    }
    if (!storedEnvIdField.equals(newEnvIdField)) {
      throw new InvalidRequestException("The same Workflow variable name " + varName
              + " cannot be used for InfraDefinitions using different Environment. Change the name of the variable in one or more Workflow.",
          USER);
    }
  }

  private String joinFieldValuesMetadata(String storedRelatedField, String newRelatedField) {
    if (isEmpty(storedRelatedField)) {
      return newRelatedField;
    }

    if (isEmpty(newRelatedField)) {
      return storedRelatedField;
    }

    if (storedRelatedField.equals(newRelatedField)) {
      return newRelatedField;
    }

    if (storedRelatedField.contains(",")) {
      Set<String> relatedFields = stream(storedRelatedField.split(",")).collect(toSet());
      if (relatedFields.contains(newRelatedField)) {
        return storedRelatedField;
      } else {
        return join(",", storedRelatedField, newRelatedField);
      }
    }

    return join(",", storedRelatedField, newRelatedField);
  }

  static void populateParentFields(Variable pipelineVariable, EntityType entityType, List<Variable> workflowVariables,
      String originalVarName, Map<String, String> pseWorkflowVariables) {
    if (workflowVariables == null) {
      return;
    }
    workflowVariables = workflowVariables.stream()
                            .filter(t
                                -> t.obtainEntityType() != null && t.getMetadata() != null
                                    && t.getMetadata().get(Variable.RELATED_FIELD) != null)
                            .collect(Collectors.toList());
    Map<String, String> parentFields = new HashMap<>();
    switch (entityType) {
      case SERVICE:
        handleServiceVariable(pipelineVariable, workflowVariables, originalVarName, pseWorkflowVariables);
        break;
      case INFRASTRUCTURE_MAPPING:
      case INFRASTRUCTURE_DEFINITION:
        // instead of parent fields they have envId and service Id which is set inside the method
        handleInfraPipelineVariable(pipelineVariable, workflowVariables, originalVarName, pseWorkflowVariables);
        break;
      case APPDYNAMICS_TIERID:
        parentFields =
            handleAppDynamicsTierIdVariable(pipelineVariable, workflowVariables, originalVarName, pseWorkflowVariables);
        break;
      case APPDYNAMICS_APPID:
        parentFields =
            handleAppDynamicsAppIdVariable(pipelineVariable, workflowVariables, originalVarName, pseWorkflowVariables);
        break;
      case ELK_INDICES:
        parentFields =
            handleElkIndicesVariable(pipelineVariable, workflowVariables, originalVarName, pseWorkflowVariables);
        break;
      case NEWRELIC_APPID:
        parentFields =
            handleNewRelicAppIdVariable(pipelineVariable, workflowVariables, originalVarName, pseWorkflowVariables);
        break;
      case NEWRELIC_MARKER_APPID:
        parentFields = handleNewRelicMarkerAppIdVariable(
            pipelineVariable, workflowVariables, originalVarName, pseWorkflowVariables);
        break;
      case SPLUNK_CONFIGID:
        parentFields =
            handleSplunkConfigIdVariable(pipelineVariable, workflowVariables, originalVarName, pseWorkflowVariables);
        break;
      default:
        log.info("no parent fields required to be set");
    }
    if (isNotEmpty(parentFields)) {
      if (pipelineVariable.getMetadata().get(Variable.PARENT_FIELDS) != null) {
        Map<String, String> existingParents =
            (Map<String, String>) pipelineVariable.getMetadata().get(Variable.PARENT_FIELDS);
        existingParents.putAll(parentFields);
      } else {
        pipelineVariable.getMetadata().put(Variable.PARENT_FIELDS, parentFields);
      }
    }
  }

  private static Map<String, String> handleNewRelicMarkerAppIdVariable(Variable pipelineVariable,
      List<Variable> workflowVariables, String originalVarName, Map<String, String> pseWorkflowVariables) {
    Map<String, String> parentFields = new HashMap<>();
    for (Variable var : workflowVariables) {
      if (NEWRELIC_MARKER_CONFIGID == var.obtainEntityType()) {
        if (var.getMetadata() != null && originalVarName.equals(var.getMetadata().get(Variable.RELATED_FIELD))) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue) && relatedVarValue != null) {
            parentFields.put("analysisServerConfigId", relatedVarValue);
          }
        }
      }
    }
    return parentFields;
  }

  private static Map<String, String> handleNewRelicAppIdVariable(Variable pipelineVariable,
      List<Variable> workflowVariables, String originalVarName, Map<String, String> pseWorkflowVariables) {
    Map<String, String> parentFields = new HashMap<>();
    for (Variable var : workflowVariables) {
      if (NEWRELIC_CONFIGID == var.obtainEntityType()) {
        if (var.getMetadata() != null && originalVarName.equals(var.getMetadata().get(Variable.RELATED_FIELD))) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue) && relatedVarValue != null) {
            parentFields.put("analysisServerConfigId", relatedVarValue);
          }
        }
      }
    }
    return parentFields;
  }

  private static Map<String, String> handleElkIndicesVariable(Variable pipelineVariable,
      List<Variable> workflowVariables, String originalVarName, Map<String, String> pseWorkflowVariables) {
    Map<String, String> parentFields = new HashMap<>();
    for (Variable var : workflowVariables) {
      if (ELK_CONFIGID == var.obtainEntityType()) {
        if (var.getMetadata() != null && originalVarName.equals(var.getMetadata().get(Variable.RELATED_FIELD))) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue) && relatedVarValue != null) {
            parentFields.put("analysisServerConfigId", relatedVarValue);
          }
        }
      }
    }
    return parentFields;
  }

  private static Map<String, String> handleSplunkConfigIdVariable(Variable pipelineVariable,
      List<Variable> workflowVariables, String originalVarName, Map<String, String> pseWorkflowVariables) {
    Map<String, String> parentFields = new HashMap<>();
    for (Variable var : workflowVariables) {
      if (SPLUNK_CONFIGID == var.obtainEntityType()) {
        if (var.getMetadata() != null && originalVarName.equals(var.getMetadata().get(Variable.RELATED_FIELD))) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue) && relatedVarValue != null) {
            parentFields.put("analysisServerConfigId", relatedVarValue);
          }
        }
      }
    }
    return parentFields;
  }

  private static Map<String, String> handleAppDynamicsAppIdVariable(Variable pipelineVariable,
      List<Variable> workflowVariables, String originalVarName, Map<String, String> pseWorkflowVariables) {
    Map<String, String> parentFields = new HashMap<>();
    for (Variable var : workflowVariables) {
      if (APPDYNAMICS_CONFIGID == var.obtainEntityType()) {
        if (var.getMetadata() != null && originalVarName.equals(var.getMetadata().get(Variable.RELATED_FIELD))) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue) && relatedVarValue != null) {
            parentFields.put("analysisServerConfigId", relatedVarValue);
          }
        }
      }
    }
    return parentFields;
  }

  private static Map<String, String> handleAppDynamicsTierIdVariable(Variable pipelineVariable,
      List<Variable> workflowVariables, String originalVarName, Map<String, String> pseWorkflowVariables) {
    Map<String, String> parentFields = new HashMap<>();
    for (Variable var : workflowVariables) {
      if (APPDYNAMICS_APPID == var.obtainEntityType()) {
        if (var.getMetadata() != null && originalVarName.equals(var.getMetadata().get(Variable.RELATED_FIELD))) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue) && relatedVarValue != null) {
            parentFields.put("applicationId", relatedVarValue);
          }
        }
      } else if (APPDYNAMICS_CONFIGID == var.obtainEntityType()) {
        if (var.getMetadata() != null && originalVarName.equals(var.getMetadata().get(Variable.RELATED_FIELD))) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue) && relatedVarValue != null) {
            parentFields.put("analysisServerConfigId", relatedVarValue);
          }
        }
      }
    }
    return parentFields;
  }

  private static void handleInfraPipelineVariable(Variable pipelineVariable, List<Variable> workflowVariables,
      String originalVarName, Map<String, String> pseWorkflowVariables) {
    for (Variable var : workflowVariables) {
      if (ENVIRONMENT == var.obtainEntityType()) {
        if (var.getMetadata() == null || var.getMetadata().get(Variable.RELATED_FIELD) == null) {
          continue;
        }
        String relatedFields = String.valueOf(var.getMetadata().get(Variable.RELATED_FIELD));
        Optional<String> relatedField =
            stream(relatedFields.split(",")).filter(t -> t.equals(originalVarName)).findFirst();
        if (relatedField.isPresent()) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue) && relatedVarValue != null) {
            pipelineVariable.getMetadata().put(Variable.ENV_ID, relatedVarValue);
          }
        }
      } else if (SERVICE == var.obtainEntityType()) {
        if (var.getMetadata() != null && originalVarName.equals(var.getMetadata().get(Variable.RELATED_FIELD))
            && pseWorkflowVariables.containsKey(var.getName())) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue) && relatedVarValue != null) {
            pipelineVariable.getMetadata().put(Variable.SERVICE_ID, relatedVarValue);
          }
        }
      }
    }
  }

  private static void handleServiceVariable(Variable pipelineVariable, List<Variable> workflowVariables,
      String originalVarName, Map<String, String> pseWorkflowVariables) {
    for (Variable var : workflowVariables) {
      if (INFRASTRUCTURE_DEFINITION == var.obtainEntityType()) {
        if (var.getMetadata() != null && originalVarName.equals(var.getMetadata().get(Variable.RELATED_FIELD))
            && pseWorkflowVariables.containsKey(var.getName())) {
          String relatedVarValue = pseWorkflowVariables.get(var.getName());
          if (!matchesVariablePattern(relatedVarValue)) {
            pipelineVariable.getMetadata().put(Variable.INFRA_ID, relatedVarValue);
          }
        }
      }
    }
  }

  private static void cloneRelatedFieldNameForRuntimeVars(
      Map<String, String> pseWorkflowVariables, Variable pipelineVariable) {
    if (pipelineVariable.getMetadata().get("relatedField") != null) {
      String relatedFieldOldValue = String.valueOf(pipelineVariable.getMetadata().get("relatedField"));
      if (isNotEmpty(relatedFieldOldValue) && !relatedFieldOldValue.equals("null")
          && pseWorkflowVariables.containsKey(relatedFieldOldValue)) {
        if (ExpressionEvaluator.matchesVariablePattern(pseWorkflowVariables.get(relatedFieldOldValue))) {
          String relatedFieldNewValue = getName(pseWorkflowVariables.get(relatedFieldOldValue));
          pipelineVariable.getMetadata().put(Variable.RELATED_FIELD, relatedFieldNewValue);
        } else {
          pipelineVariable.getMetadata().remove(Variable.RELATED_FIELD);
        }
      }
    }
  }

  private static void cloneRelatedFieldName(Map<String, String> pseWorkflowVariables, Variable pipelineVariable) {
    if (pipelineVariable.getMetadata().get("relatedField") != null) {
      String relatedFieldOldValue = String.valueOf(pipelineVariable.getMetadata().get("relatedField"));
      if (isNotEmpty(relatedFieldOldValue) && !relatedFieldOldValue.equals("null")) {
        if (ExpressionEvaluator.matchesVariablePattern(pseWorkflowVariables.get(relatedFieldOldValue))) {
          String relatedFieldNewValue = getName(pseWorkflowVariables.get(relatedFieldOldValue));
          pipelineVariable.getMetadata().put(Variable.RELATED_FIELD, relatedFieldNewValue);
        } else {
          pipelineVariable.getMetadata().remove(Variable.RELATED_FIELD);
        }
      }
    }
  }

  private static void setRelatedFieldEnvironment(
      List<Variable> workflowVariables, Map<String, String> pseWorkflowVariables, Variable pipelineVariable) {
    pipelineVariable.getMetadata().put(
        "relatedField", join(",", getInfraDefVariables(workflowVariables, pseWorkflowVariables)));
  }

  // Need to check. Might cause regression
  void updateRelatedFieldEnvironment(
      List<Variable> workflowVariables, Map<String, String> pseWorkflowVariables, Variable pipelineVariable) {
    String currentRelatedFields = (String) pipelineVariable.getMetadata().get("relatedField");
    pipelineVariable.getMetadata().put("relatedField",
        StringUtils.join(
            currentRelatedFields, ",", join(",", getInfraDefVariables(workflowVariables, pseWorkflowVariables))));
  }

  private static List<String> getInfraDefVariables(
      List<Variable> workflowVariables, Map<String, String> pseWorkflowVariables) {
    List<Variable> infraDefVariable =
        workflowVariables.stream()
            .filter(t -> t.obtainEntityType() != null && t.obtainEntityType() == INFRASTRUCTURE_DEFINITION)
            .collect(toList());
    List<String> infraVarNames = new ArrayList<>();
    for (Variable variable : infraDefVariable) {
      infraVarNames.add(getName(pseWorkflowVariables.get(variable.getName())));
    }
    return infraVarNames;
  }

  private void resolveEnvIds(List<String> envIds, Map<String, String> pseWorkflowVariables, Workflow workflow) {
    String envId = workflowService.resolveEnvironmentId(workflow, pseWorkflowVariables);
    if (envId != null && !envIds.contains(envId) && !matchesVariablePattern(envId)) {
      envIds.add(envId);
    }
  }

  private void resolveArtifactAndManifestNeededServicesOfWorkflowAndEnvIds(List<String> serviceIds, List<String> envIds,
      List<DeploymentType> deploymentTypes, Map<String, String> pseWorkflowVariables, Workflow workflow) {
    DeploymentMetadata deploymentMetadata =
        workflowService.fetchDeploymentMetadata(workflow.getAppId(), workflow, pseWorkflowVariables, serviceIds, envIds,
            Include.ARTIFACT_SERVICE, Include.ENVIRONMENT, Include.DEPLOYMENT_TYPE);
    if (deploymentMetadata != null) {
      if (deploymentMetadata.getArtifactRequiredServiceIds() != null) {
        deploymentMetadata.getArtifactRequiredServiceIds()
            .stream()
            .filter(serviceId -> !serviceIds.contains(serviceId))
            .forEach(serviceIds::add);
      }
      if (deploymentMetadata.getManifestRequiredServiceIds() != null) {
        deploymentMetadata.getManifestRequiredServiceIds()
            .stream()
            .filter(serviceId -> !serviceIds.contains(serviceId))
            .forEach(serviceIds::add);
      }
      if (deploymentMetadata.getEnvIds() != null) {
        deploymentMetadata.getEnvIds().stream().filter(envId -> !envIds.contains(envId)).forEach(envIds::add);
      }
      if (deploymentMetadata.getDeploymentTypes() != null) {
        deploymentMetadata.getDeploymentTypes()
            .stream()
            .filter(deploymentType -> !deploymentTypes.contains(deploymentType))
            .forEach(deploymentTypes::add);
      }
    }
  }

  @VisibleForTesting
  void validateWorkflowVariables(
      Workflow workflow, PipelineStageElement pse, PipelineStage pipelineStage, Set<String> invalidStages) {
    Map<String, String> pseWorkflowVariables = pse.getWorkflowVariables();
    Set<String> pseWorkflowVariableNames =
        isEmpty(pseWorkflowVariables) ? new HashSet<>() : pseWorkflowVariables.keySet();
    Set<String> workflowVariableNames = (workflow.getOrchestrationWorkflow().getUserVariables() == null)
        ? new HashSet<>()
        : (workflow.getOrchestrationWorkflow().getUserVariables().stream().map(Variable::getName).collect(toSet()));
    Set<String> missingVariables = new HashSet<>();
    for (String pseWorkflowVariable : pseWorkflowVariableNames) {
      if (!workflowVariableNames.contains(pseWorkflowVariable)) {
        pse.setValid(false);
        missingVariables.add(pseWorkflowVariable);
      }
    }

    if (!isEmpty(missingVariables)) {
      String errorMsg = String.format("Workflow Variable(s) \"%s\" updated or deleted after adding to the Pipeline",
          String.join("\", \"", missingVariables));
      pse.setValidationMessage(errorMsg);
      pipelineStage.setValid(false);
      pipelineStage.setValidationMessage(errorMsg);
      invalidStages.add(pse.getName());
      return;
    }

    if (isEmpty(workflowVariableNames)) {
      return;
    }

    Set<String> requiredWorkflowVariableNames =
        workflow.getOrchestrationWorkflow()
            .getUserVariables()
            .stream()
            .filter(variable -> (variable.isMandatory()) && (variable.getType() == VariableType.ENTITY))
            .map(Variable::getName)
            .collect(toSet());

    for (String workflowVariable : requiredWorkflowVariableNames) {
      if (!pseWorkflowVariableNames.contains(workflowVariable)) {
        pse.setValid(false);
        missingVariables.add(workflowVariable);
      }
    }

    if (!isEmpty(missingVariables)) {
      String errorMsg = String.format("Workflow Variable(s) \"%s\" added or updated after adding to the Pipeline",
          String.join("\", \"", missingVariables));
      pse.setValidationMessage(errorMsg);
      pipelineStage.setValid(false);
      pipelineStage.setValidationMessage(errorMsg);
      invalidStages.add(pse.getName());
    }
  }

  private boolean contains(List<Variable> pipelineVariables, String name) {
    return pipelineVariables.stream().anyMatch(
        variable -> variable != null && variable.getName() != null && variable.getName().equals(name));
  }

  private Variable getContainedVariable(List<Variable> pipelineVariables, String name) {
    if (pipelineVariables == null) {
      return null;
    }

    return pipelineVariables.stream()
        .filter(variable -> variable != null && variable.getName() != null && variable.getName().equals(name))
        .findFirst()
        .orElse(null);
  }

  @Override
  @ValidationGroups(Create.class)
  public Pipeline save(Pipeline pipeline) {
    validateTemplateExpressions(pipeline);
    validateUserGroupExpression(pipeline);

    validatePipelineNameForDuplicates(pipeline);
    ensurePipelineStageUuidAndParallelIndex(pipeline);
    checkUniquePipelineStepName(pipeline);
    checkUniqueApprovalPublishedVariable(pipeline);

    String accountId = appService.getAccountIdByAppId(pipeline.getAppId());
    pipeline.setAccountId(accountId);
    StaticLimitCheckerWithDecrement checker = (StaticLimitCheckerWithDecrement) limitCheckerFactory.getInstance(
        new Action(accountId, ActionType.CREATE_PIPELINE));

    return LimitEnforcementUtils.withLimitCheck(checker, () -> {
      Set<String> keywords = pipeline.generateKeywords();
      validatePipeline(pipeline, keywords);
      pipeline.setKeywords(trimmedLowercaseSet(keywords));

      wingsPersistence.save(pipeline);

      Set<String> currentUserGroups = getUserGroups(pipeline);

      // TODO: remove this when all the needed verification is done from validatePipeline
      new StateMachine(pipeline, workflowService.stencilMap(pipeline.getAppId()));

      yamlPushService.pushYamlChangeSet(accountId, null, pipeline, Type.CREATE, pipeline.isSyncFromGit(), false);
      try {
        updatePipelineReferenceInUserGroup(
            new HashSet<>(), currentUserGroups, accountId, pipeline.getAppId(), pipeline.getUuid());
      } catch (Exception e) {
        log.error("An error occurred when trying to reference this pipeline {} in userGroups ", pipeline.getUuid(), e);
      }

      if (!pipeline.isSample()) {
        eventPublishHelper.publishAccountEvent(
            accountId, AccountEvent.builder().accountEventType(AccountEventType.PIPELINE_CREATED).build(), true, true);
      }

      return pipeline;
    });
  }

  private void validatePipelineNameForDuplicates(Pipeline pipeline) {
    if (wingsPersistence.createQuery(Pipeline.class)
            .filter(PipelineKeys.appId, pipeline.getAppId())
            .filter(PipelineKeys.name, pipeline.getName())
            .getKey()
        != null) {
      throw new InvalidRequestException("Duplicate name " + pipeline.getName(), USER);
    }
  }

  @Override
  public List<String> obtainPipelineNamesReferencedByTemplatedEntity(String appId, String templatedEntityId) {
    List<String> referencedPipelines = new ArrayList<>();
    try (HIterator<Pipeline> pipelineHIterator =
             new HIterator<>(wingsPersistence.createQuery(Pipeline.class).filter(PipelineKeys.appId, appId).fetch())) {
      while (pipelineHIterator.hasNext()) {
        Pipeline pipeline = pipelineHIterator.next();
        // Templatized Id in workflow variables
        if (pipeline.getPipelineStages()
                .stream()
                .flatMap(pipelineStage -> pipelineStage.getPipelineStageElements().stream())
                .anyMatch(pse
                    -> pse.getWorkflowVariables() != null
                        && pse.getWorkflowVariables().values().contains(templatedEntityId))) {
          referencedPipelines.add(pipeline.getName());
        }
      }
    }
    return referencedPipelines;
  }

  @Override
  public DeploymentMetadata fetchDeploymentMetadata(String appId, String pipelineId,
      Map<String, String> pipelineVariables, List<String> artifactNeededServiceIds, List<String> envIds,
      DeploymentMetadata.Include... includeList) {
    return fetchDeploymentMetadata(
        appId, pipelineId, pipelineVariables, artifactNeededServiceIds, envIds, false, null, includeList);
  }

  @Override
  public DeploymentMetadata fetchDeploymentMetadata(String appId, String pipelineId,
      Map<String, String> pipelineVariables, List<String> artifactNeededServiceIds, List<String> envIds,
      boolean withDefaultArtifact, WorkflowExecution workflowExecution, DeploymentMetadata.Include... includeList) {
    Map<String, Workflow> workflowCache = new HashMap<>();
    Pipeline pipeline = readPipelineWithResolvedVariables(appId, pipelineId, pipelineVariables, false, workflowCache);
    return fetchDeploymentMetadata(appId, pipeline, artifactNeededServiceIds, envIds, withDefaultArtifact,
        workflowExecution, workflowCache, includeList);
  }

  @Override
  public DeploymentMetadata fetchDeploymentMetadata(
      String appId, Pipeline pipeline, Map<String, String> pipelineVariables) {
    readPipelineWithResolvedVariables(pipeline, pipelineVariables, null, false);
    return fetchDeploymentMetadata(appId, pipeline, null, null, false, null, null);
  }

  @Override
  public DeploymentMetadata fetchDeploymentMetadata(String appId, Pipeline pipeline,
      List<String> artifactNeededServiceIds, List<String> envIds, DeploymentMetadata.Include... includeList) {
    return fetchDeploymentMetadata(appId, pipeline, artifactNeededServiceIds, envIds, false, null, null, includeList);
  }

  private DeploymentMetadata fetchDeploymentMetadata(String appId, Pipeline pipeline,
      List<String> artifactNeededServiceIds, List<String> envIds, boolean withDefaultArtifact,
      WorkflowExecution workflowExecution, Map<String, Workflow> workflowCache,
      DeploymentMetadata.Include... includeList) {
    notNullCheck("Pipeline does not exist", pipeline, USER);
    if (artifactNeededServiceIds == null) {
      artifactNeededServiceIds = new ArrayList<>();
    }
    if (envIds == null) {
      envIds = new ArrayList<>();
    }
    DeploymentMetadata finalDeploymentMetadata = DeploymentMetadata.builder()
                                                     .artifactRequiredServiceIds(artifactNeededServiceIds)
                                                     .manifestRequiredServiceIds(new ArrayList<>())
                                                     .envIds(envIds)
                                                     .deploymentTypes(new ArrayList<>())
                                                     .artifactVariables(new ArrayList<>())
                                                     .manifestVariables(new ArrayList<>())
                                                     .build();

    boolean isBuildPipeline = false;
    if (workflowCache == null) {
      workflowCache = new HashMap<>();
    }
    for (PipelineStage pipelineStage : pipeline.getPipelineStages()) {
      for (PipelineStageElement pse : pipelineStage.getPipelineStageElements()) {
        if (!ENV_STATE.name().equals(pse.getType()) || pse.checkDisableAssertion()) {
          continue;
        }

        String workflowId = (String) pse.getProperties().get("workflowId");
        Workflow workflow;
        if (workflowCache.containsKey(workflowId)) {
          workflow = workflowCache.get(workflowId);
        } else {
          workflow = workflowService.readWorkflowWithoutServices(pipeline.getAppId(), workflowId);
          requireOrchestrationWorkflow(workflow);
          workflowCache.put(workflowId, workflow);
        }

        OrchestrationWorkflow orchestrationWorkflow = workflow.getOrchestrationWorkflow();
        DeploymentMetadata deploymentMetadata = workflowService.fetchDeploymentMetadata(appId, workflow,
            pse.getWorkflowVariables(), null, null, withDefaultArtifact, workflowExecution, includeList);
        if (deploymentMetadata == null) {
          continue;
        }

        if (!isBuildPipeline && BUILD == orchestrationWorkflow.getOrchestrationWorkflowType()) {
          // If pipeline is a build pipeline, don't get artifact variable metadata.
          isBuildPipeline = true;

          // Remove any existing artifact variables.
          finalDeploymentMetadata.setArtifactVariables(new ArrayList<>());

          // Remove ARTIFACT_SERVICE from includeList.
          Stream<Include> includeStream = isEmpty(includeList) ? stream(Include.values()) : stream(includeList);
          includeList = includeStream.filter(include -> Include.ARTIFACT_SERVICE != include).toArray(Include[] ::new);
        }

        mergeDeploymentMetadata(workflow, finalDeploymentMetadata, deploymentMetadata);
      }
    }

    return finalDeploymentMetadata;
  }

  private void mergeDeploymentMetadata(
      Workflow workflow, DeploymentMetadata finalDeploymentMetadata, DeploymentMetadata deploymentMetadata) {
    mergeLists(
        finalDeploymentMetadata.getArtifactRequiredServiceIds(), deploymentMetadata.getArtifactRequiredServiceIds());
    mergeLists(
        finalDeploymentMetadata.getManifestRequiredServiceIds(), deploymentMetadata.getManifestRequiredServiceIds());
    mergeLists(finalDeploymentMetadata.getEnvIds(), deploymentMetadata.getEnvIds());
    mergeLists(finalDeploymentMetadata.getDeploymentTypes(), deploymentMetadata.getDeploymentTypes());

    if (isNotEmpty(deploymentMetadata.getArtifactVariables())) {
      List<ArtifactVariable> finalArtifactVariables = finalDeploymentMetadata.getArtifactVariables();
      for (ArtifactVariable artifactVariable : deploymentMetadata.getArtifactVariables()) {
        mergeArtifactVariable(finalArtifactVariables, artifactVariable, workflow.getUuid());
      }
    }

    if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, workflow.getAccountId())
        && isNotEmpty(deploymentMetadata.getManifestVariables())) {
      List<ManifestVariable> finalManifestVariables = finalDeploymentMetadata.getManifestVariables();
      deploymentMetadata.getManifestVariables().forEach(
          manifestVariable -> mergeManifestVariable(finalManifestVariables, manifestVariable, workflow.getUuid()));
    }
  }

  private void mergeManifestVariable(
      List<ManifestVariable> finalManifestVariables, ManifestVariable manifestVariable, String workflowId) {
    if (manifestVariable == null) {
      return;
    }

    Optional<ManifestVariable> duplicateVariable =
        finalManifestVariables.stream()
            .filter(variable -> variable.getServiceId().equals(manifestVariable.getServiceId()))
            .findFirst();
    if (duplicateVariable.isPresent()) {
      duplicateVariable.get().getWorkflowIds().add(workflowId);
    } else {
      manifestVariable.setWorkflowIds(new ArrayList<>(Arrays.asList(workflowId)));
      finalManifestVariables.add(manifestVariable);
    }
  }

  private void mergeArtifactVariable(
      List<ArtifactVariable> finalArtifactVariables, ArtifactVariable artifactVariable, String workflowId) {
    if (artifactVariable == null) {
      return;
    }

    List<ArtifactVariable> duplicateArtifactVariables =
        finalArtifactVariables.stream().filter(av -> av.getName().equals(artifactVariable.getName())).collect(toList());
    if (isEmpty(duplicateArtifactVariables)) {
      addArtifactVariable(finalArtifactVariables, artifactVariable, workflowId);
      return;
    }

    boolean merged = false;
    for (ArtifactVariable duplicateArtifactVariable : duplicateArtifactVariables) {
      String duplicateServiceId = duplicateArtifactVariable.fetchAssociatedService();
      if (duplicateServiceId != null) {
        String serviceId = artifactVariable.fetchAssociatedService();
        if (serviceId != null && serviceId.equals(duplicateServiceId)) {
          if (!duplicateArtifactVariable.getWorkflowIds().contains(workflowId)) {
            duplicateArtifactVariable.getWorkflowIds().add(workflowId);
          }
          merged = true;
          break;
        }
      }
    }

    if (!merged) {
      addArtifactVariable(finalArtifactVariables, artifactVariable, workflowId);
    }
  }

  private void addArtifactVariable(
      List<ArtifactVariable> finalArtifactVariables, ArtifactVariable artifactVariable, String workflowId) {
    artifactVariable.setWorkflowIds(new ArrayList<>(Collections.singletonList(workflowId)));
    finalArtifactVariables.add(artifactVariable);
  }

  private static <T> void mergeLists(List<T> to, List<T> from) {
    // NOTE: to should not be null
    if (isEmpty(from)) {
      return;
    }

    for (T el : from) {
      if (!to.contains(el)) {
        to.add(el);
      }
    }
  }

  @VisibleForTesting
  void checkUniquePipelineStepName(Pipeline pipeline) {
    List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    if (pipelineStages == null) {
      return;
    }
    Set<String> pipelineStageNameSet = new HashSet<>();
    pipelineStages.stream()
        .map(pipelineStage -> pipelineStage.getPipelineStageElements().get(0))
        .filter(Objects::nonNull)
        .forEach(stageElement -> {
          if (pipelineStageNameSet.contains(stageElement.getName())) {
            throw new InvalidRequestException(format("Duplicate step name %s.", stageElement.getName()), USER);
          }
          pipelineStageNameSet.add(stageElement.getName());
        });
  }

  private void validatePipeline(Pipeline pipeline, Set<String> keywords) {
    List<Service> services = new ArrayList<>();
    List<String> serviceIds = new ArrayList<>();
    final List<PipelineStage> pipelineStages = pipeline.getPipelineStages();
    Set<String> parameterizedEnvIds = new HashSet<>();
    if (pipelineStages != null) {
      for (int i = 0; i < pipelineStages.size(); ++i) {
        PipelineStage pipelineStage = pipelineStages.get(i);
        for (PipelineStageElement stageElement : pipelineStage.getPipelineStageElements()) {
          if (!isValidPipelineStageName(stageElement.getName())) {
            throw new InvalidArgumentsException("Pipeline step name can only have a-z, A-Z, 0-9, -, (, ) and _", USER);
          }

          String accountId = appService.getAccountIdByAppId(pipeline.getAppId());
          if (!ENV_STATE.name().equals(stageElement.getType())) {
            continue;
          }
          if (isNullOrEmpty((String) stageElement.getProperties().get("workflowId"))) {
            throw new InvalidArgumentsException("Workflow can not be null for Environment state", USER);
          }
          Workflow workflow = workflowService.readWorkflow(
              pipeline.getAppId(), (String) stageElement.getProperties().get("workflowId"));
          if (workflow == null || workflow.getOrchestrationWorkflow() == null) {
            throw new InvalidArgumentsException("Workflow can not be null for Environment state", USER);
          }
          keywords.add(workflow.getName());
          keywords.add(workflow.getDescription());
          List<Service> resolvedServiceForWorkflow =
              resolveServices(services, serviceIds, stageElement.getWorkflowVariables(), workflow);
          if (stageElement.getRuntimeInputsConfig() != null) {
            pipelineServiceValidator.validateRuntimeInputsConfig(
                stageElement, accountId, workflow.getOrchestrationWorkflow().getUserVariables());
          }
          if (workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType() == ROLLING) {
            for (Service service : emptyIfNull(resolvedServiceForWorkflow)) {
              if (service.getDeploymentType() == DeploymentType.KUBERNETES && !service.isK8sV2()) {
                throw new InvalidRequestException(format("Rolling Type Workflow does not suport k8s-v1 "
                        + "service [%s]",
                    service.getName()));
              }
            }
          }

          String envId = workflowService.obtainTemplatedEnvironmentId(workflow, stageElement.getWorkflowVariables());
          if (envId != null && matchesVariablePattern(envId)) {
            parameterizedEnvIds.add(envId);
          }
        }
      }
    }
    if (parameterizedEnvIds.size() > 1) {
      throw new InvalidArgumentsException(
          "A pipeline may only have one environment expression across all workflows", USER);
    }
    keywords.addAll(services.stream().map(Service::getName).distinct().collect(toList()));
  }

  private boolean isValidPipelineStageName(String name) {
    if (isEmpty(name)) {
      return false;
    }
    return ALLOWED_CHARS_SET_PIPELINE_STAGE.containsAll(Sets.newHashSet(Lists.charactersOf(name)));
  }

  private boolean prunePipeline(String appId, String pipelineId) {
    // First lets make sure that we have persisted a job that will prone the descendant objects
    pruneQueue.send(new PruneEvent(Pipeline.class, appId, pipelineId));
    return wingsPersistence.delete(Pipeline.class, appId, pipelineId);
  }

  private void requireOrchestrationWorkflow(Workflow workflow) {
    notNullCheck("Workflow does not exist", workflow, USER);
    notNullCheck("Orchestration workflow does not exist", workflow.getOrchestrationWorkflow(), USER);
  }

  public Set<String> getUserGroups(Pipeline pipeline) {
    Map<String, Workflow> workflowCache = new HashMap<>();
    if (pipeline.getPipelineStages() == null) {
      return new HashSet<>();
    }
    Set<String> userGroups = new HashSet<>();
    for (PipelineStage bean : pipeline.getPipelineStages()) {
      PipelineStageElement stageElement = bean.getPipelineStageElements().get(0);
      if (StateType.APPROVAL.name().equals(stageElement.getType())) {
        Map<String, Object> properties = stageElement.getProperties();
        properties.forEach((name, value) -> {
          if (USER_GROUPS.equals(name) && value instanceof List && isNotEmpty((List<String>) value)) {
            userGroups.addAll((List<String>) value);
          }
        });
      } else {
        if (stageElement.getRuntimeInputsConfig() != null) {
          List<String> userGroupIds = stageElement.getRuntimeInputsConfig().getUserGroupIds();
          userGroups.addAll(userGroupIds);
        }
        String workflowId = (String) stageElement.getProperties().get("workflowId");
        Workflow workflow = getWorkflow(pipeline, workflowCache, workflowId);
        workflowCache.put(workflowId, workflow);
        List<String> userGroupVariableNames = workflow.getOrchestrationWorkflow()
                                                  .getUserVariables()
                                                  .stream()
                                                  .filter(v -> USER_GROUP.equals(v.obtainEntityType()))
                                                  .map(Variable::getName)
                                                  .collect(toList());
        if (isNotEmpty(stageElement.getWorkflowVariables()) && isNotEmpty(userGroupVariableNames)) {
          stageElement.getWorkflowVariables().forEach((variable, value) -> {
            if (userGroupVariableNames.contains(variable)) {
              if (!ExpressionEvaluator.matchesVariablePattern(value)) {
                if (value.contains(",")) {
                  userGroups.addAll(Arrays.asList(value.split(",")));
                } else {
                  userGroups.add(value);
                }
              }
            }
          });
        }
      }
    }
    return userGroups;
  }

  private void updatePipelineReferenceInUserGroup(Set<String> previousUserGroups, Set<String> currentUserGroups,
      String accountId, String appId, String pipelineId) {
    Set<String> parentsToRemove = Sets.difference(previousUserGroups, currentUserGroups);
    Set<String> parentsToAdd = Sets.difference(currentUserGroups, previousUserGroups);

    for (String id : parentsToRemove) {
      userGroupService.removeParentsReference(id, accountId, appId, pipelineId, EntityType.PIPELINE.name());
    }
    for (String id : parentsToAdd) {
      userGroupService.addParentsReference(id, accountId, appId, pipelineId, EntityType.PIPELINE.name());
    }
  }
}
