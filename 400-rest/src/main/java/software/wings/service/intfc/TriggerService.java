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
import io.harness.beans.WorkflowType;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.WebHookToken;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.ManifestSummary;
import software.wings.beans.instance.dashboard.ArtifactSummary;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.TriggerExecution;
import software.wings.beans.trigger.WebhookEventType;
import software.wings.beans.trigger.WebhookParameters;
import software.wings.beans.trigger.WebhookSource;
import software.wings.helpers.ext.trigger.response.TriggerResponse;
import software.wings.persistence.artifact.Artifact;
import software.wings.service.intfc.ownership.OwnedByApplication;
import software.wings.service.intfc.ownership.OwnedByApplicationManifest;
import software.wings.service.intfc.ownership.OwnedByArtifactStream;
import software.wings.service.intfc.ownership.OwnedByPipeline;
import software.wings.service.intfc.ownership.OwnedByWorkflow;

import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * Created by sgurubelli on 10/26/17.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public interface TriggerService
    extends OwnedByApplication, OwnedByPipeline, OwnedByArtifactStream, OwnedByWorkflow, OwnedByApplicationManifest {
  /**
   * List.
   *
   * @param pageRequest the req
   * @return the page response
   */
  PageResponse<Trigger> list(PageRequest<Trigger> pageRequest, boolean withTags, String tagFilter);

  /**
   * Get artifact stream.
   *
   * @param appId     the app id
   * @param triggerId the id
   * @return the artifact stream
   */
  Trigger get(String appId, String triggerId);

  /**
   * Create artifact stream.
   *
   * @param trigger the artifact stream
   * @return the artifact stream
   */
  @ValidationGroups(Create.class) Trigger save(@Valid Trigger trigger);

  /**
   * Update artifact stream.
   *
   * @param trigger the artifact stream
   * @param migration
   * @return the artifact stream
   */
  @ValidationGroups(Update.class) Trigger update(@Valid Trigger trigger, boolean migration);

  /**
   * Delete.
   *
   * @param appId     the app id
   * @param triggerId the id
   * @return true, if successful
   */
  boolean delete(@NotEmpty String appId, @NotEmpty String triggerId);

  boolean delete(@NotEmpty String appId, @NotEmpty String triggerId, boolean syncFromGit);

  /**
   * Generate web hook token web hook token.
   *
   * @param appId     the app id
   * @param triggerId the stream id
   * @return the web hook token
   */
  WebHookToken generateWebHookToken(String appId, String triggerId);

  void triggerExecutionPostArtifactCollectionAsync(String appId, String artifactStreamId, List<Artifact> artifacts);

  void triggerExecutionPostManifestCollectionAsync(String appId, String appManifestId, List<HelmChart> helmChart);

  void triggerExecutionPostArtifactCollectionAsync(
      String accountId, String appId, String artifactStreamId, List<Artifact> artifacts);

  /**
   * Trigger post pipeline completion async
   *
   * @param appId
   * @param pipelineId
   */
  void triggerExecutionPostPipelineCompletionAsync(String appId, String pipelineId);

  /***
   * Trigger
   * @param trigger
   * @param scheduledFireTime
   */
  void triggerScheduledExecutionAsync(Trigger trigger, Date scheduledFireTime);

  WorkflowExecution triggerExecutionByWebHook(String appId, String webHookToken,
      Map<String, ArtifactSummary> serviceBuildNumbers, Map<String, ManifestSummary> serviceManifestMapping,
      TriggerExecution triggerExecution, Map<String, String> parameters);

  /**
   * Triggers that have actions on Pipeline
   *
   * @param appId
   * @param pipelineId
   * @return List<Trigger></Trigger>
   */
  List<Trigger> getTriggersHasPipelineAction(String appId, String pipelineId);

  /**
   * Triggers that have actions on Pipeline
   *
   * @param appId
   * @param workflowId
   * @return List<Trigger></Trigger>
   */
  List<Trigger> getTriggersHasWorkflowAction(String appId, String workflowId);

  /**
   * Triggers that have actions on Artifact Stream
   *
   * @param appId
   * @param artifactStreamId
   * @return List<Trigger></Trigger>
   */
  List<Trigger> getTriggersHasArtifactStreamAction(String appId, String artifactStreamId);

  /**
   * Updates by App to resync the filled names with the updated values
   *
   * @param appId
   */
  void updateByApp(String appId);

  /**
   * Updates by ArtifactStream to resync the filled names with the updated values
   *
   * @param artifactStreamId
   */
  void updateByArtifactStream(String artifactStreamId);

  /**
   * Gets the cron expression
   *
   * @param expression
   * @return
   */
  String getCronDescription(String expression);

  /**
   * Get trigger
   * @param token
   * @return
   */
  Trigger getTriggerByWebhookToken(String token);

  /***
   * Trigger execution by webhook
   * @param trigger
   * @param parameters
   * @param triggerExecution
   * @return
   */
  WorkflowExecution triggerExecutionByWebHook(
      Trigger trigger, Map<String, String> parameters, TriggerExecution triggerExecution);

  /***
   *
   * @param appId
   * @param workflowId
   * @param workflowType
   * @param webhookSource
   * @param eventType
   * @return List of webhook parameters
   */
  WebhookParameters listWebhookParameters(String appId, String workflowId, WorkflowType workflowType,
      WebhookSource webhookSource, WebhookEventType eventType);

  /**
   * Trigger execution by service infra
   * @param appId
   * @param infraMappingId
   */
  boolean triggerExecutionByServiceInfra(String appId, String infraMappingId);

  List<String> obtainTriggerNamesReferencedByTemplatedEntityId(String appId, @NotEmpty String entityId);

  void handleTriggerTaskResponse(
      @NotEmpty String appId, @NotEmpty String triggerExecutionId, TriggerResponse triggerResponse);

  void authorize(Trigger trigger, boolean existing);

  void authorizeAppAccess(List<String> appIds);

  boolean triggerActionExists(Trigger trigger);
}
