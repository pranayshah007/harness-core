/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.trigger;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.BYPASS_HELM_FETCH;
import static io.harness.beans.FeatureName.GITHUB_WEBHOOK_AUTHENTICATION;
import static io.harness.beans.OrchestrationWorkflowType.BUILD;
import static io.harness.beans.WorkflowType.ORCHESTRATION;
import static io.harness.beans.WorkflowType.PIPELINE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.ListUtils.trimStrings;
import static io.harness.exception.WingsException.ExecutionContext.MANAGER;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.ExpressionEvaluator.containsVariablePattern;
import static io.harness.expression.ExpressionEvaluator.matchesVariablePattern;
import static io.harness.govern.Switch.unhandled;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;
import static io.harness.validation.PersistenceValidator.duplicateCheck;
import static io.harness.validation.Validator.equalCheck;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.ExecutionCredential.ExecutionType.SSH;
import static software.wings.beans.SSHExecutionCredential.Builder.aSSHExecutionCredential;
import static software.wings.beans.trigger.ArtifactSelection.Type.ARTIFACT_SOURCE;
import static software.wings.beans.trigger.ArtifactSelection.Type.LAST_COLLECTED;
import static software.wings.beans.trigger.ArtifactSelection.Type.LAST_DEPLOYED;
import static software.wings.beans.trigger.ArtifactSelection.Type.PIPELINE_SOURCE;
import static software.wings.beans.trigger.ArtifactSelection.Type.WEBHOOK_VARIABLE;
import static software.wings.beans.trigger.TriggerConditionType.NEW_MANIFEST;
import static software.wings.beans.trigger.TriggerConditionType.SCHEDULED;
import static software.wings.beans.trigger.TriggerConditionType.WEBHOOK;
import static software.wings.beans.trigger.WebHookTriggerCondition.WEBHOOK_SECRET;
import static software.wings.beans.trigger.WebhookSource.BITBUCKET;
import static software.wings.beans.trigger.WebhookSource.GITHUB;
import static software.wings.beans.trigger.WebhookSource.GITLAB;
import static software.wings.service.impl.trigger.TriggerServiceHelper.notNullCheckWorkflow;
import static software.wings.service.impl.trigger.TriggerServiceHelper.overrideTriggerVariables;
import static software.wings.service.impl.trigger.TriggerServiceHelper.validateAndSetCronExpression;
import static software.wings.service.impl.workflow.WorkflowServiceTemplateHelper.getServiceWorkflowVariables;
import static software.wings.service.impl.workflow.WorkflowServiceTemplateHelper.getTemplatizedEnvVariableName;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofSeconds;
import static java.util.Objects.isNull;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.CreatedByType;
import io.harness.beans.EncryptedData;
import io.harness.beans.EncryptedData.EncryptedDataKeys;
import io.harness.beans.EncryptedDataParent;
import io.harness.beans.ExecutionStatus;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.WorkflowType;
import io.harness.data.structure.EmptyPredicate;
import io.harness.distribution.idempotence.IdempotentId;
import io.harness.distribution.idempotence.IdempotentLock;
import io.harness.distribution.idempotence.IdempotentResult;
import io.harness.distribution.idempotence.UnableToRegisterIdempotentOperationException;
import io.harness.exception.DeploymentFreezeException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;
import io.harness.logging.ExceptionLogger;
import io.harness.scheduler.PersistentScheduler;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Event.Type;
import software.wings.beans.ExecutionArgs;
import software.wings.beans.GitConfig;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.Variable;
import software.wings.beans.VariableType;
import software.wings.beans.WebHookToken;
import software.wings.beans.Workflow;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.ManifestSummary;
import software.wings.beans.artifact.Artifact;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.deployment.DeploymentMetadata;
import software.wings.beans.deployment.DeploymentMetadata.Include;
import software.wings.beans.instance.dashboard.ArtifactSummary;
import software.wings.beans.trigger.ArtifactSelection;
import software.wings.beans.trigger.ArtifactTriggerCondition;
import software.wings.beans.trigger.ManifestSelection;
import software.wings.beans.trigger.ManifestSelection.ManifestSelectionType;
import software.wings.beans.trigger.ManifestTriggerCondition;
import software.wings.beans.trigger.NewInstanceTriggerCondition;
import software.wings.beans.trigger.PipelineTriggerCondition;
import software.wings.beans.trigger.ScheduledTriggerCondition;
import software.wings.beans.trigger.ServiceInfraWorkflow;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.beans.trigger.TriggerCondition;
import software.wings.beans.trigger.TriggerConditionType;
import software.wings.beans.trigger.TriggerExecution;
import software.wings.beans.trigger.TriggerExecution.Status;
import software.wings.beans.trigger.WebHookTriggerCondition;
import software.wings.beans.trigger.WebhookEventType;
import software.wings.beans.trigger.WebhookParameters;
import software.wings.beans.trigger.WebhookSource;
import software.wings.common.MongoIdempotentRegistry;
import software.wings.delegatetasks.buildsource.ArtifactStreamLogContext;
import software.wings.delegatetasks.manifest.ApplicationManifestLogContext;
import software.wings.dl.WingsPersistence;
import software.wings.helpers.ext.trigger.response.TriggerDeploymentNeededResponse;
import software.wings.helpers.ext.trigger.response.TriggerResponse;
import software.wings.infra.InfrastructureDefinition;
import software.wings.scheduler.ScheduledTriggerJob;
import software.wings.service.ArtifactStreamHelper;
import software.wings.service.impl.AppLogContext;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.impl.TriggerLogContext;
import software.wings.service.impl.deployment.checks.DeploymentFreezeUtils;
import software.wings.service.impl.workflow.WorkflowServiceTemplateHelper;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactCollectionService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ResourceLookupService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.WorkflowExecutionService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.service.intfc.trigger.TriggerExecutionService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingVariableTypes;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import org.quartz.TriggerKey;

@OwnedBy(CDC)
@Singleton
@ValidateOnExecution
@Slf4j
@TargetModule(HarnessModule._815_CG_TRIGGERS)
public class TriggerServiceImpl implements TriggerService {
  private static final long MIN_INTERVAL = 300;
  public static final String TRIGGER_SLOWNESS_ERROR_MESSAGE = "Trigger rejected due to slowness in the product";
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ExecutorService executorService;
  @Inject private WorkflowExecutionService workflowExecutionService;
  @Inject private ArtifactService artifactService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject @Named("AsyncArtifactCollectionService") private ArtifactCollectionService artifactCollectionServiceAsync;
  @Inject private AppService appService;
  @Inject private PipelineService pipelineService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private WorkflowService workflowService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject @Named("BackgroundJobScheduler") private PersistentScheduler jobScheduler;
  @Inject private ScheduledTriggerHandler scheduledTriggerHandler;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private HarnessTagService harnessTagService;
  @Inject private YamlPushService yamlPushService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private HelmChartService helmChartService;
  @Inject private DeploymentFreezeUtils deploymentFreezeUtils;

  @Value
  @Builder
  public static class TriggerIdempotentResult implements IdempotentResult {
    private String triggerUuid;
  }

  @Inject private MongoIdempotentRegistry<TriggerIdempotentResult> idempotentRegistry;
  @Inject private TriggerServiceHelper triggerServiceHelper;
  @Inject private EnvironmentService environmentService;
  @Inject private WebhookTriggerProcessor webhookTriggerProcessor;
  @Inject private TriggerExecutionService triggerExecutionService;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private ResourceLookupService resourceLookupService;
  @Inject private TriggerAuthHandler triggerAuthHandler;
  @Inject private ArtifactStreamHelper artifactStreamHelper;
  @Inject private SettingsService settingsService;

  @Override
  public PageResponse<Trigger> list(PageRequest<Trigger> pageRequest, boolean withTags, String tagFilter) {
    PageResponse<Trigger> response =
        resourceLookupService.listWithTagFilters(pageRequest, tagFilter, EntityType.TRIGGER, withTags);
    return postProcessTriggers(response);
  }

  private PageResponse<Trigger> postProcessTriggers(PageResponse<Trigger> response) {
    if (response != null && isNotEmpty(response.getResponse())) {
      Set<String> serviceIds = new HashSet<>();
      String appId = "";
      for (Trigger trigger : response.getResponse()) {
        if (isNotEmpty(trigger.getArtifactSelections())) {
          for (ArtifactSelection artifactSelection : trigger.getArtifactSelections()) {
            if (artifactSelection.getType() == WEBHOOK_VARIABLE && artifactSelection.getArtifactStreamId() != null) {
              ArtifactStream artifactStream = artifactStreamService.get(artifactSelection.getArtifactStreamId());
              if (artifactStream != null && artifactStream.isArtifactStreamParameterized()) {
                artifactSelection.setUiDisplayName(artifactStream.getName() + " (requires values on runtime)");
              }
            }
          }
        }
        if (trigger.getCondition() != null && trigger.getCondition().getConditionType() == NEW_MANIFEST) {
          appId = trigger.getAppId();
          ManifestTriggerCondition triggerCondition = (ManifestTriggerCondition) trigger.getCondition();
          serviceIds.add(triggerCondition.getServiceId());
        }
      }
      if (isNotEmpty(serviceIds)) {
        Map<String, String> mapServiceIdToServiceName = serviceResourceService.getServiceNames(appId, serviceIds);
        for (Trigger trigger : response.getResponse()) {
          if (trigger.getCondition() != null && trigger.getCondition().getConditionType() == NEW_MANIFEST) {
            ManifestTriggerCondition triggerCondition = (ManifestTriggerCondition) trigger.getCondition();
            String serviceName = mapServiceIdToServiceName.get(triggerCondition.getServiceId());
            if (serviceName == null) {
              triggerCondition.setServiceName(triggerCondition.getServiceId());
            } else {
              triggerCondition.setServiceName(serviceName);
            }
          }
        }
      }
    }
    return response;
  }

  @Override
  public Trigger get(String appId, String triggerId) {
    Trigger trigger = wingsPersistence.getWithAppId(Trigger.class, appId, triggerId);
    if (isNull(trigger)) {
      return null;
    }
    TriggerCondition condition = trigger.getCondition();
    if (condition.getConditionType() == NEW_MANIFEST) {
      ManifestTriggerCondition manifestTriggerCondition = (ManifestTriggerCondition) condition;
      String name = serviceResourceService.getName(appId, manifestTriggerCondition.getServiceId());
      manifestTriggerCondition.setServiceName(name);
    }
    return trigger;
  }

  @Override
  public Trigger save(Trigger trigger) {
    String accountId = appService.getAccountIdByAppId(trigger.getAppId());
    trigger.setAccountId(accountId);
    validateInput(trigger, null);
    updateNextIterations(trigger);
    Trigger savedTrigger =
        duplicateCheck(() -> wingsPersistence.saveAndGet(Trigger.class, trigger), "name", trigger.getName());
    if (trigger.getCondition().getConditionType() == SCHEDULED) {
      scheduledTriggerHandler.wakeup();
      ScheduledTriggerJob.add(jobScheduler, accountId, savedTrigger.getAppId(), savedTrigger.getUuid(), trigger);
      jobScheduler.pauseJob(trigger.getUuid(), ScheduledTriggerJob.GROUP);
    }

    if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, trigger.getAccountId())) {
      addSecretParent(savedTrigger);
    }
    if (featureFlagService.isEnabled(FeatureName.TRIGGER_YAML, accountId)) {
      yamlPushService.pushYamlChangeSet(accountId, null, savedTrigger, Type.CREATE, trigger.isSyncFromGit(), false);
    } else {
      // TODO: Once this flag is enabled for all accounts, this can be removed
      auditServiceHelper.reportForAuditingUsingAppId(trigger.getAppId(), null, trigger, Type.CREATE);
    }
    return savedTrigger;
  }

  private void addSecretParent(Trigger trigger) {
    if (trigger.getCondition().getConditionType() == WEBHOOK) {
      WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
      if (webHookTriggerCondition.getWebHookSecret() != null) {
        Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                         .filter(EncryptedDataKeys.uuid, webHookTriggerCondition.getWebHookSecret());
        UpdateOperations<EncryptedData> updateOperations =
            wingsPersistence.createUpdateOperations(EncryptedData.class)
                .addToSet(EncryptedDataKeys.parents,
                    new EncryptedDataParent(trigger.getUuid(), SettingVariableTypes.TRIGGER, WEBHOOK_SECRET));

        wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
      }
    }
  }

  @Override
  public Trigger update(Trigger trigger, boolean migration) {
    Trigger existingTrigger = wingsPersistence.getWithAppId(Trigger.class, trigger.getAppId(), trigger.getUuid());
    notNullCheck("Trigger was deleted ", existingTrigger, USER);
    equalCheck(trigger.getWorkflowType(), existingTrigger.getWorkflowType());

    String accountId = isEmpty(existingTrigger.getAccountId())
        ? appService.getAccountIdByAppId(existingTrigger.getAppId())
        : existingTrigger.getAccountId();
    trigger.setAccountId(accountId);

    validateInput(trigger, existingTrigger);

    updateNextIterations(trigger);
    Trigger updatedTrigger =
        duplicateCheck(() -> wingsPersistence.saveAndGet(Trigger.class, trigger), "name", trigger.getName());
    addOrUpdateCronForScheduledJob(trigger, existingTrigger);

    if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, updatedTrigger.getAccountId())) {
      updateSecretParent(existingTrigger, updatedTrigger);
    }

    boolean isRename = !existingTrigger.getName().equals(trigger.getName());
    if (!migration) {
      if (featureFlagService.isEnabled(FeatureName.TRIGGER_YAML, accountId)) {
        yamlPushService.pushYamlChangeSet(
            accountId, existingTrigger, updatedTrigger, Type.UPDATE, trigger.isSyncFromGit(), isRename);
      } else {
        // TODO: Once this flag is enabled for all accounts, this can be removed
        auditServiceHelper.reportForAuditingUsingAppId(
            updatedTrigger.getAppId(), existingTrigger, updatedTrigger, Type.UPDATE);
      }
    }

    return updatedTrigger;
  }

  private void updateSecretParent(Trigger oldTrigger, Trigger newTrigger) {
    if (isSecretIdChanged(oldTrigger.getCondition(), newTrigger.getCondition())) {
      removeSecretParent(oldTrigger);
      addSecretParent(newTrigger);
    }
  }

  private boolean isSecretIdChanged(TriggerCondition oldTriggerCondition, TriggerCondition newTriggerCondition) {
    return newTriggerCondition.getConditionType() != WEBHOOK || oldTriggerCondition.getConditionType() != WEBHOOK
        || ((WebHookTriggerCondition) oldTriggerCondition).getWebHookSecret() == null
        || ((WebHookTriggerCondition) newTriggerCondition).getWebHookSecret() == null
        || !((WebHookTriggerCondition) oldTriggerCondition)
                .getWebHookSecret()
                .equals(((WebHookTriggerCondition) newTriggerCondition).getWebHookSecret());
  }

  @Override
  public boolean delete(String appId, String triggerId) {
    return delete(appId, triggerId, false);
  }

  @Override
  public boolean delete(String appId, String triggerId, boolean syncFromGit) {
    Trigger trigger = get(appId, triggerId);
    boolean answer = triggerServiceHelper.delete(triggerId);

    String accountId = appService.getAccountIdByAppId(appId);
    if (answer) {
      harnessTagService.pruneTagLinks(accountId, triggerId);
      if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, trigger.getAccountId())) {
        removeSecretParent(trigger);
      }
    }
    if (featureFlagService.isEnabled(FeatureName.TRIGGER_YAML, accountId) && (trigger != null)) {
      yamlPushService.pushYamlChangeSet(accountId, trigger, null, Type.DELETE, syncFromGit, false);
    } else {
      // TODO: Once this flag is enabled for all accounts, this can be removed
      auditServiceHelper.reportDeleteForAuditing(trigger.getAppId(), trigger);
    }
    return answer;
  }

  private void removeSecretParent(Trigger trigger) {
    if (trigger.getCondition().getConditionType() == WEBHOOK) {
      WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
      if (webHookTriggerCondition.getWebHookSecret() != null) {
        Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                         .filter(EncryptedDataKeys.uuid, webHookTriggerCondition.getWebHookSecret());
        UpdateOperations<EncryptedData> updateOperations =
            wingsPersistence.createUpdateOperations(EncryptedData.class)
                .removeAll(EncryptedDataKeys.parents,
                    new EncryptedDataParent(trigger.getUuid(), SettingVariableTypes.TRIGGER, WEBHOOK_SECRET));

        wingsPersistence.findAndModify(query, updateOperations, new FindAndModifyOptions());
      }
    }
  }

  @Override
  public WebHookToken generateWebHookToken(String appId, String triggerId) {
    Trigger trigger = wingsPersistence.getWithAppId(Trigger.class, appId, triggerId);
    notNullCheck("Trigger was deleted", trigger, USER);
    return generateWebHookToken(trigger, null);
  }

  @Override
  public void pruneByApplication(String appId) {
    wingsPersistence.createQuery(Trigger.class).filter(Trigger.APP_ID_KEY2, appId).asList().forEach(trigger -> {
      delete(appId, trigger.getUuid());
    });
  }

  @Override
  public void pruneByPipeline(String appId, String pipelineId) {
    List<Trigger> triggers = triggerServiceHelper.getTriggersByWorkflow(appId, pipelineId);
    triggers.forEach(trigger -> delete(appId, trigger.getUuid()));

    triggerServiceHelper.getPipelineCompletionTriggers(appId, pipelineId)
        .forEach(trigger -> delete(appId, trigger.getUuid()));
  }

  @Override
  public void pruneByWorkflow(String appId, String workflowId) {
    List<Trigger> triggers = triggerServiceHelper.getTriggersByWorkflow(appId, workflowId);
    triggers.forEach(trigger -> delete(appId, trigger.getUuid()));
  }

  @Override
  public void pruneByArtifactStream(String appId, String artifactStreamId) {
    for (Trigger trigger : triggerServiceHelper.getNewArtifactTriggers(appId, artifactStreamId)) {
      delete(appId, trigger.getUuid());
    }
  }

  @Override
  public void pruneByApplicationManifest(String appId, String applicationManifestId) {
    for (Trigger trigger : triggerServiceHelper.getNewManifestConditionTriggers(appId, applicationManifestId)) {
      delete(appId, trigger.getUuid());
    }
  }

  @Override
  public List<String> obtainTriggerNamesReferencedByTemplatedEntityId(String appId, String entityId) {
    return triggerServiceHelper.checkTemplatedEntityReferenced(appId, entityId);
  }

  @Override
  public void triggerExecutionPostArtifactCollectionAsync(
      String appId, String artifactStreamId, List<Artifact> artifacts) {
    executorService.execute(() -> triggerExecutionPostArtifactCollection(appId, artifactStreamId, artifacts));
  }

  @Override
  public void triggerExecutionPostManifestCollectionAsync(
      String appId, String appManifestId, List<HelmChart> helmCharts) {
    if (isEmpty(helmCharts)) {
      return;
    }
    executorService.execute(() -> triggerExecutionPostManifestCollection(appId, appManifestId, helmCharts));
  }

  private void triggerExecutionPostManifestCollection(String appId, String appManifestId, List<HelmChart> helmCharts) {
    try (AutoLogContext ignore1 = new AppLogContext(appId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new ApplicationManifestLogContext(appManifestId, OVERRIDE_ERROR)) {
      List<Trigger> triggers = triggerServiceHelper.getNewManifestConditionTriggers(appId, appManifestId);
      if (isNotEmpty(triggers)) {
        for (Trigger trigger : triggers) {
          executeNewManifestTrigger(appManifestId, helmCharts, trigger);
        }
      }
    }
  }

  private void executeNewManifestTrigger(String appManifestId, List<HelmChart> helmCharts, Trigger trigger) {
    try (AutoLogContext ignore2 = new TriggerLogContext(trigger.getUuid(), OVERRIDE_ERROR)) {
      log.info("Trigger found with name {} and Id {} for appManifestId {}", trigger.getName(), trigger.getUuid(),
          appManifestId);
      if (trigger.isDisabled()) {
        log.info(TRIGGER_SLOWNESS_ERROR_MESSAGE);
        return;
      }
    }
    ManifestTriggerCondition manifestTriggerCondition = (ManifestTriggerCondition) trigger.getCondition();
    List<HelmChart> deployingHelmCharts = new ArrayList<>();
    HelmChart matchingHelmChart =
        fetchLatestMatchingHelmChart(helmCharts, trigger, manifestTriggerCondition, deployingHelmCharts);
    if (matchingHelmChart != null) {
      deployingHelmCharts.add(matchingHelmChart);
    }
    if (isNotEmpty(deployingHelmCharts)) {
      log.info("Selecting the latest version from matched versions {}", deployingHelmCharts.get(0));
      List<Artifact> deployingArtifacts = new ArrayList<>();
      if (isNotEmpty(trigger.getArtifactSelections()) || isNotEmpty(trigger.getManifestSelections())) {
        log.info("Manifest selections found collecting manifest as per app manifest selections");
        addArtifactsAndHelmChartsFromSelections(trigger.getAppId(), trigger, deployingArtifacts, deployingHelmCharts);
      }
      try {
        triggerDeployment(deployingArtifacts, deployingHelmCharts, null, trigger);
      } catch (WingsException exception) {
        ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
      }
    } else {
      log.info("None of the manifests {} match with the given filter", helmCharts);
    }
  }

  private HelmChart fetchLatestMatchingHelmChart(List<HelmChart> helmCharts, Trigger trigger,
      ManifestTriggerCondition manifestTriggerCondition, List<HelmChart> deployingHelmCharts) {
    if (isBlank(manifestTriggerCondition.getVersionRegex())) {
      log.info("No manifest version filter set. Triggering with the last collected manifest {}",
          helmCharts.get(0).getUuid());
      return helmCharts.get(0);
    } else {
      return helmCharts.stream()
          .filter(helmChart
              -> triggerServiceHelper.checkManifestMatchesFilter(
                  trigger.getUuid(), helmChart, manifestTriggerCondition.getVersionRegex()))
          .findFirst()
          .orElse(null);
    }
  }

  @Override
  public void triggerExecutionPostArtifactCollectionAsync(
      String accountId, String appId, String artifactStreamId, List<Artifact> artifacts) {
    executorService.execute(() -> {
      if (featureFlagService.isEnabled(FeatureName.TRIGGER_FOR_ALL_ARTIFACTS, accountId)) {
        List<Artifact> nonDuplicates = artifacts.stream().filter(t -> !t.isDuplicate()).collect(toList());
        triggerExecutionPostArtifactCollectionForAllArtifacts(appId, artifactStreamId, nonDuplicates);
      } else {
        Artifact lastArtifact = artifacts.get(artifacts.size() - 1);
        if (lastArtifact.isDuplicate()) {
          log.info("Skipping trigger as the last collected Artifact is Duplicate, Artifact was already collected: "
              + lastArtifact.getBuildNo());
          return;
        }
        triggerExecutionPostArtifactCollection(appId, artifactStreamId, artifacts);
      }
    });
  }

  void triggerExecutionPostArtifactCollectionForAllArtifacts(
      String appId, String artifactStreamId, List<Artifact> collectedArtifacts) {
    if (isEmpty(collectedArtifacts)) {
      return;
    }
    triggerServiceHelper.getNewArtifactTriggers(appId, artifactStreamId).forEach(trigger -> {
      log.info("Trigger found with name {} and Id {} for artifactStreamId {}", trigger.getName(), trigger.getUuid(),
          artifactStreamId);
      ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
      List<Artifact> artifacts = new ArrayList<>();
      if (isEmpty(artifactTriggerCondition.getArtifactFilter())) {
        log.info("No artifact filter set. Triggering with all artifacts");
        artifacts.addAll(collectedArtifacts);
      } else {
        log.info("Artifact filter {} set. Going over all the artifacts to find the matched artifacts",
            artifactTriggerCondition.getArtifactFilter());
        List<Artifact> matchedArtifacts =
            collectedArtifacts.stream()
                .filter(artifact
                    -> triggerServiceHelper.checkArtifactMatchesArtifactFilter(trigger.getUuid(), artifact,
                        artifactTriggerCondition.getArtifactFilter(), artifactTriggerCondition.isRegex()))
                .collect(Collectors.toList());
        if (isNotEmpty(matchedArtifacts)) {
          log.info("Matched artifacts size {}", matchedArtifacts.size());
          artifacts.addAll(matchedArtifacts);
        } else {
          log.info("Artifacts {} not matched with the given artifact filter", artifacts);
        }
      }
      if (isEmpty(artifacts)) {
        log.warn(
            "Skipping execution - artifact does not match with the given filter. So, skipping the complete deployment {}",
            artifactTriggerCondition);
        return;
      }
      List<Artifact> artifactsFromSelections = new ArrayList<>();
      List<HelmChart> helmCharts = new ArrayList<>();
      if (isNotEmpty(trigger.getArtifactSelections()) || isNotEmpty(trigger.getManifestSelections())) {
        log.info("Artifact selections found collecting artifacts as per artifactStream selections");
        addArtifactsAndHelmChartsFromSelections(trigger.getAppId(), trigger, artifactsFromSelections, helmCharts);
      }
      if (isNotEmpty(artifacts)) {
        log.info("The artifacts  set for the trigger {} are {}", trigger.getUuid(),
            artifacts.stream().map(Artifact::getUuid).collect(toList()));
        // Triggering the execution only when FROM_TRIGGERING_ARTIFACT is selected.
        // We don't need to trigger for all artifacts when some other artifact selection is chosen
        if (triggeringArtifactSelected(trigger)) {
          for (Artifact artifact : artifacts) {
            log.info("Triggering deployment with artifact {}", artifact.getUuid());
            try {
              List<Artifact> selectedArtifacts = new ArrayList<>(artifactsFromSelections);
              selectedArtifacts.add(artifact);
              triggerDeployment(selectedArtifacts, helmCharts, null, trigger);
            } catch (WingsException exception) {
              exception.addContext(Application.class, trigger.getAppId());
              exception.addContext(ArtifactStream.class, artifactStreamId);
              exception.addContext(Trigger.class, trigger.getUuid());
              ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
            }
          }
        } else {
          log.info("Triggering deployment with the given selections");
          try {
            List<Artifact> selectedArtifacts = new ArrayList<>(artifactsFromSelections);
            triggerDeployment(selectedArtifacts, helmCharts, null, trigger);
          } catch (WingsException exception) {
            exception.addContext(Application.class, trigger.getAppId());
            exception.addContext(ArtifactStream.class, artifactStreamId);
            exception.addContext(Trigger.class, trigger.getUuid());
            ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
          }
        }
      } else {
        log.info("No Artifacts matched. Hence Skipping the deployment");
        return;
      }
    });
  }

  /**
   * Checks if from triggering artifact source is selected for the service associated with triggering artifact.
   *
   * @param trigger The trigger which needs to be executed. It should be an on new Artifact Trigger
   * @return True if the workflow/pipeline needs triggering artifact, false otherwise
   */
  private boolean triggeringArtifactSelected(Trigger trigger) {
    ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
    String artifactStreamId = artifactTriggerCondition.getArtifactStreamId();
    if (isEmpty(trigger.getArtifactSelections())) {
      return false;
    }
    String serviceIdInCondition =
        artifactStreamServiceBindingService.getServiceId(trigger.getAppId(), artifactStreamId, true);
    return trigger.getArtifactSelections().stream().anyMatch(artifactSelection
        -> artifactSelection.getType() == ARTIFACT_SOURCE
            && artifactSelection.getServiceId().equals(serviceIdInCondition));
  }

  @Override
  public void triggerExecutionPostPipelineCompletionAsync(String appId, String sourcePipelineId) {
    executorService.submit(() -> triggerExecutionPostPipelineCompletion(appId, sourcePipelineId));
  }

  @Override
  public void triggerScheduledExecutionAsync(Trigger trigger, Date scheduledFireTime) {
    executorService.submit(() -> triggerScheduledExecution(trigger, scheduledFireTime));
  }

  @Override
  public WorkflowExecution triggerExecutionByWebHook(String appId, String webHookToken,
      Map<String, ArtifactSummary> serviceArtifactMapping, Map<String, ManifestSummary> serviceManifestMapping,
      TriggerExecution triggerExecution, Map<String, String> parameters) {
    List<Artifact> artifacts = new ArrayList<>();
    List<HelmChart> helmCharts = new ArrayList<>();
    Trigger trigger = triggerServiceHelper.getTrigger(appId, webHookToken);
    log.info(
        "Received WebHook request  for the Trigger {} with Service Build Numbers {}, Service Manifest mapping {}  and parameters {}",
        trigger.getUuid(), serviceArtifactMapping, serviceManifestMapping, parameters);
    if (isNotEmpty(serviceArtifactMapping) || isNotEmpty(serviceManifestMapping)) {
      addArtifactsAndHelmChartsFromVersionsOfWebHook(
          trigger, serviceArtifactMapping, artifacts, helmCharts, serviceManifestMapping);
    }
    addArtifactsAndHelmChartsFromSelections(appId, trigger, artifacts, helmCharts);
    return triggerDeployment(artifacts, helmCharts, parameters, triggerExecution, trigger);
  }

  @Override
  public Trigger getTriggerByWebhookToken(String token) {
    return wingsPersistence.createQuery(Trigger.class).filter(TriggerKeys.webHookToken, token).get();
  }

  @Override
  public WorkflowExecution triggerExecutionByWebHook(
      Trigger trigger, Map<String, String> parameters, TriggerExecution triggerExecution) {
    return triggerDeployment(null, new ArrayList<>(), parameters, triggerExecution, trigger);
  }

  @Override
  public WebhookParameters listWebhookParameters(String appId, String workflowId, WorkflowType workflowType,
      WebhookSource webhookSource, WebhookEventType eventType) {
    return WebhookParameters.builder()
        .params(new ArrayList<>())
        .expressions(WebhookParameters.suggestExpressions(webhookSource, eventType))
        .build();
  }

  @Override
  public List<Trigger> getTriggersHasPipelineAction(String appId, String pipelineId) {
    return getTriggersHasWorkflowAction(appId, pipelineId);
  }

  @Override
  public List<Trigger> getTriggersHasWorkflowAction(String appId, String workflowId) {
    return triggerServiceHelper.getTriggersHasWorkflowAction(appId, workflowId);
  }

  @Override
  public List<Trigger> getTriggersHasArtifactStreamAction(String appId, String artifactStreamId) {
    return triggerServiceHelper.getTriggersHasArtifactStreamAction(appId, artifactStreamId);
  }

  @Override
  public void updateByApp(String appId) {
    triggerServiceHelper.getTriggersByApp(appId).forEach(trigger -> update(trigger, false));
  }

  @Override
  public void updateByArtifactStream(String artifactStreamId) {
    triggerServiceHelper.getTriggersByArtifactStream(artifactStreamId).forEach(trigger -> update(trigger, false));
  }

  @Override
  public String getCronDescription(String cronExpression) {
    return TriggerServiceHelper.getCronDescription(cronExpression);
  }

  private void triggerExecutionPostArtifactCollection(
      String appId, String artifactStreamId, List<Artifact> collectedArtifacts) {
    try (AutoLogContext ignore1 = new AppLogContext(appId, OVERRIDE_ERROR);
         AutoLogContext ignore3 = new ArtifactStreamLogContext(artifactStreamId, OVERRIDE_ERROR)) {
      triggerServiceHelper.getNewArtifactTriggers(appId, artifactStreamId).forEach(trigger -> {
        try (AutoLogContext ignore2 = new TriggerLogContext(trigger.getUuid(), OVERRIDE_ERROR)) {
          log.info("Trigger found with name {} and Id {} for artifactStreamId {}", trigger.getName(), trigger.getUuid(),
              artifactStreamId);
          if (trigger.isDisabled()) {
            log.info(TRIGGER_SLOWNESS_ERROR_MESSAGE);
            return;
          }
          ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
          List<Artifact> artifacts = new ArrayList<>();
          if (isEmpty(artifactTriggerCondition.getArtifactFilter())) {
            log.info("No artifact filter set. Triggering with the collected artifact {}",
                collectedArtifacts.get(collectedArtifacts.size() - 1).getUuid());
            artifacts.add(collectedArtifacts.get(collectedArtifacts.size() - 1));
          } else {
            log.info("Artifact filter {} set. Going over all the artifacts to find the matched artifacts",
                artifactTriggerCondition.getArtifactFilter());
            List<Artifact> matchedArtifacts =
                collectedArtifacts.stream()
                    .filter(artifact
                        -> triggerServiceHelper.checkArtifactMatchesArtifactFilter(trigger.getUuid(), artifact,
                            artifactTriggerCondition.getArtifactFilter(), artifactTriggerCondition.isRegex()))
                    .collect(Collectors.toList());
            if (isNotEmpty(matchedArtifacts)) {
              log.info("Matched artifacts {}. Selecting the latest artifact",
                  matchedArtifacts.get(matchedArtifacts.size() - 1));
              artifacts.add(matchedArtifacts.get(matchedArtifacts.size() - 1));
            } else {
              log.info("Artifacts {} not matched with the given artifact filter", artifacts);
            }
          }
          if (isEmpty(artifacts)) {
            log.warn(
                "Skipping execution - artifact does not match with the given filter. So, skipping the complete deployment {}",
                artifactTriggerCondition);
            return;
          }
          String accountId = appService.getAccountIdByAppId(appId);
          boolean preferArtifactSelectionOverTriggeringArtifact =
              featureFlagService.isEnabled(FeatureName.ON_NEW_ARTIFACT_TRIGGER_WITH_LAST_COLLECTED_FILTER, accountId);
          if (preferArtifactSelectionOverTriggeringArtifact) {
            List<Artifact> artifactsFromSelection = new ArrayList<>();
            List<HelmChart> helmCharts = new ArrayList<>();
            addArtifactsFromSelectionsTriggeringArtifactSource(
                trigger.getAppId(), trigger, artifactsFromSelection, artifacts);

            if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, accountId)) {
              addHelmChartsFromSelections(appId, trigger, helmCharts);
            }
            if (isNotEmpty(artifactsFromSelection)) {
              log.info("The artifacts  set for the trigger {} are {}", trigger.getUuid(),
                  artifactsFromSelection.stream().map(Artifact::getUuid).collect(toList()));
              try {
                triggerDeployment(artifactsFromSelection, helmCharts, null, trigger);
              } catch (WingsException exception) {
                ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
              }
            } else {
              log.info("No Artifacts matched. Hence Skipping the deployment");
              return;
            }
          } else {
            List<HelmChart> helmCharts = new ArrayList<>();
            if (isNotEmpty(trigger.getArtifactSelections()) || isNotEmpty(trigger.getManifestSelections())) {
              log.info("Artifact selections found collecting artifacts as per artifactStream selections");
              addArtifactsAndHelmChartsFromSelections(trigger.getAppId(), trigger, artifacts, helmCharts);
            }
            if (isNotEmpty(artifacts)) {
              log.info("The artifacts  set for the trigger {} are {}", trigger.getUuid(),
                  artifacts.stream().map(Artifact::getUuid).collect(toList()));
              try {
                triggerDeployment(artifacts, helmCharts, null, trigger);
              } catch (WingsException exception) {
                exception.addContext(Application.class, trigger.getAppId());
                exception.addContext(ArtifactStream.class, artifactStreamId);
                exception.addContext(Trigger.class, trigger.getUuid());
                ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
              }
            } else {
              log.info("No Artifacts matched. Hence Skipping the deployment");
              return;
            }
          }
        }
      });
    }
  }

  @VisibleForTesting
  void addArtifactsFromSelectionsTriggeringArtifactSource(
      String appId, Trigger trigger, List<Artifact> artifactSelections, List<Artifact> triggeringArtifacts) {
    if (isEmpty(trigger.getArtifactSelections())) {
      log.info(
          "Artifact selections not found for trigger with name: {}, triggerId: {} used to trigger {}. Collecting all artifacts.",
          trigger.getName(), trigger.getUuid(),
          trigger.getWorkflowType() == PIPELINE ? trigger.getPipelineName() : trigger.getWorkflowName());
      artifactSelections.addAll(triggeringArtifacts);
      return;
    }
    log.info("Artifact selections found collecting artifacts as per artifactStream selections");
    trigger.getArtifactSelections().forEach(artifactSelection -> {
      if (artifactSelection.getType() == LAST_COLLECTED) {
        addLastCollectedArtifact(appId, artifactSelection, artifactSelections);
      } else if (artifactSelection.getType() == LAST_DEPLOYED) {
        addLastDeployedArtifacts(
            appId, artifactSelection.getWorkflowId(), artifactSelection.getServiceId(), artifactSelections);
      } else {
        addTriggeringArtifacts(artifactSelections, artifactSelection.getServiceId(), triggeringArtifacts);
      }
    });
  }

  private void addTriggeringArtifacts(
      List<Artifact> artifactSelections, String serviceId, List<Artifact> triggeringArtifacts) {
    artifactSelections.addAll(triggeringArtifacts.stream()
                                  .filter(artifact1 -> artifact1.getServiceIds().contains(serviceId))
                                  .collect(toList()));
  }

  private void triggerExecutionPostPipelineCompletion(String appId, String sourcePipelineId) {
    triggerServiceHelper.getTriggersMatchesWorkflow(appId, sourcePipelineId).forEach(trigger -> {
      if (trigger.isDisabled()) {
        log.info(TRIGGER_SLOWNESS_ERROR_MESSAGE);
        return;
      }
      List<ArtifactSelection> artifactSelections = trigger.getArtifactSelections();
      List<ManifestSelection> manifestSelections = trigger.getManifestSelections();
      boolean helmArtifactEnabled =
          featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, trigger.getAccountId());
      if (isEmpty(artifactSelections) && isEmpty(manifestSelections)) {
        log.info("No artifactSelection configuration setup found. Executing pipeline {} from source pipeline {}",
            trigger.getWorkflowId(), sourcePipelineId);
        List<Artifact> lastDeployedArtifacts = getLastDeployedArtifacts(appId, sourcePipelineId, null);
        if (isEmpty(lastDeployedArtifacts)) {
          log.warn(
              "No last deployed artifacts found. Triggering execution {} without artifacts", trigger.getWorkflowId());
        }
        List<HelmChart> helmCharts = new ArrayList<>();
        if (helmArtifactEnabled) {
          addLastDeployedHelmCharts(appId, sourcePipelineId, null, helmCharts);
        }
        triggerPostPipelineCompletionDeployment(sourcePipelineId, trigger, lastDeployedArtifacts, helmCharts);
      } else {
        List<Artifact> artifacts = new ArrayList<>();
        if (isNotEmpty(artifactSelections)
            && artifactSelections.stream().anyMatch(
                artifactSelection -> artifactSelection.getType() == PIPELINE_SOURCE)) {
          log.info("Adding last deployed artifacts from source pipeline {} ", sourcePipelineId);
          addLastDeployedArtifacts(appId, sourcePipelineId, null, artifacts);
        }
        List<HelmChart> helmCharts = new ArrayList<>();
        if (helmArtifactEnabled && isNotEmpty(manifestSelections)) {
          Set<String> serviceIds =
              manifestSelections.stream()
                  .filter(manifestSelection -> manifestSelection.getType() == ManifestSelectionType.PIPELINE_SOURCE)
                  .map(ManifestSelection::getServiceId)
                  .collect(toSet());
          if (isNotEmpty(serviceIds)) {
            addLastDeployedHelmCharts(appId, sourcePipelineId, serviceIds, helmCharts);
          }
        }
        addArtifactsAndHelmChartsFromSelections(trigger.getAppId(), trigger, artifacts, helmCharts);
        triggerPostPipelineCompletionDeployment(sourcePipelineId, trigger, artifacts, helmCharts);
      }
    });
  }

  private void triggerPostPipelineCompletionDeployment(
      String sourcePipelineId, Trigger trigger, List<Artifact> artifacts, List<HelmChart> helmCharts) {
    try {
      triggerDeployment(artifacts, helmCharts, null, trigger);
    } catch (WingsException ex) {
      ex.addContext(Application.class, trigger.getAppId());
      ex.addContext(Pipeline.class, sourcePipelineId);
      ex.addContext(Trigger.class, trigger.getUuid());
      ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
    }
  }

  private void triggerScheduledExecution(Trigger trigger, Date scheduledFireTime) {
    IdempotentId idempotentid = new IdempotentId(trigger.getUuid() + ":" + scheduledFireTime.getTime());
    try (IdempotentLock<TriggerIdempotentResult> idempotent =
             idempotentRegistry.create(idempotentid, ofSeconds(10), ofSeconds(1), ofHours(1))) {
      if (idempotent.alreadyExecuted()) {
        return;
      }
      if (trigger.isDisabled()) {
        log.info(TRIGGER_SLOWNESS_ERROR_MESSAGE);
        return;
      }

      log.info("Received scheduled trigger for appId {} and Trigger Id {} with the scheduled fire time {} ",
          trigger.getAppId(), trigger.getUuid(), scheduledFireTime.getTime());
      List<Artifact> artifacts = new ArrayList<>();
      List<HelmChart> helmCharts = new ArrayList<>();
      addArtifactsAndHelmChartsFromSelections(trigger.getAppId(), trigger, artifacts, helmCharts);

      ScheduledTriggerCondition scheduledTriggerCondition = (ScheduledTriggerCondition) trigger.getCondition();
      if (!scheduledTriggerCondition.isOnNewArtifactOnly() || isEmpty(trigger.getArtifactSelections())) {
        triggerScheduledDeployment(trigger, artifacts, helmCharts);
      } else {
        List<Artifact> lastDeployedArtifacts =
            getLastDeployedArtifacts(trigger.getAppId(), trigger.getWorkflowId(), null);
        List<String> lastDeployedArtifactIds =
            lastDeployedArtifacts.stream().map(Artifact::getUuid).distinct().collect(toList());
        List<String> artifactIds = artifacts.stream().map(Artifact::getUuid).distinct().collect(toList());
        if (lastDeployedArtifactIds.containsAll(artifactIds)) {
          log.info("No new version of artifacts found from the last successful execution "
                  + "of pipeline/ workflow {}. So, not triggering execution",
              trigger.getWorkflowId());
        } else {
          log.info("New version of artifacts found from the last successful execution "
                  + "of pipeline/ workflow {}. So, triggering  execution",
              trigger.getWorkflowId());
          triggerScheduledDeployment(trigger, artifacts, helmCharts);
        }
      }
      log.info("Scheduled trigger for appId {} and Trigger Id {} complete", trigger.getAppId(), trigger.getUuid());
      idempotent.succeeded(TriggerIdempotentResult.builder().triggerUuid(trigger.getUuid()).build());
    } catch (UnableToRegisterIdempotentOperationException e) {
      log.error("Failed to trigger scheduled trigger {}", trigger.getName(), e);
    }
  }

  private void triggerScheduledDeployment(Trigger trigger, List<Artifact> artifacts, List<HelmChart> helmCharts) {
    try {
      triggerDeployment(artifacts, helmCharts, null, trigger);
    } catch (WingsException ex) {
      ex.addContext(Application.class, trigger.getAppId());
      ex.addContext(Trigger.class, trigger.getUuid());
      ExceptionLogger.logProcessedMessages(ex, MANAGER, log);
    }
  }

  private void addArtifactsAndHelmChartsFromSelections(
      String appId, Trigger trigger, List<Artifact> artifacts, List<HelmChart> helmCharts) {
    if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, trigger.getAccountId())) {
      addHelmChartsFromSelections(appId, trigger, helmCharts);
    }
    if (isEmpty(trigger.getArtifactSelections())) {
      return;
    }
    trigger.getArtifactSelections().forEach(artifactSelection -> {
      if (artifactSelection.getType() == LAST_COLLECTED) {
        addLastCollectedArtifact(appId, artifactSelection, artifacts);
      } else if (artifactSelection.getType() == LAST_DEPLOYED) {
        addLastDeployedArtifacts(appId, artifactSelection.getWorkflowId(), artifactSelection.getServiceId(), artifacts);
      }
    });
  }

  private void addLastCollectedArtifact(String appId, ArtifactSelection artifactSelection, List<Artifact> artifacts) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactSelection.getArtifactStreamId());
    notNullCheck("ArtifactStream was deleted", artifactStream, USER);
    Artifact lastCollectedArtifact;
    reCollectArtifactsForLastCollectedSelection(artifactSelection, appId);
    if (isEmpty(artifactSelection.getArtifactFilter())) {
      lastCollectedArtifact = artifactService.fetchLastCollectedArtifact(artifactStream);
      if (lastCollectedArtifact != null
          && artifactStreamServiceBindingService.listArtifactStreamIds(appId, artifactSelection.getServiceId())
                 .contains(lastCollectedArtifact.getArtifactStreamId())) {
        artifacts.add(lastCollectedArtifact);
      }
    } else {
      lastCollectedArtifact = artifactService.getArtifactByBuildNumber(
          artifactStream, artifactSelection.getArtifactFilter(), artifactSelection.isRegex());
      if (lastCollectedArtifact != null) {
        artifacts.add(lastCollectedArtifact);
      }
    }
  }

  private void reCollectArtifactsForLastCollectedSelection(ArtifactSelection artifactSelection, String appId) {
    triggerServiceHelper.collectArtifactsForSelection(artifactSelection, appId);
  }

  private void addLastDeployedArtifacts(String appId, String workflowId, String serviceId, List<Artifact> artifacts) {
    log.info("Adding last deployed artifacts for appId {}, workflowid {}", appId, workflowId);
    artifacts.addAll(getLastDeployedArtifacts(appId, workflowId, serviceId));
  }

  private List<Artifact> getLastDeployedArtifacts(String appId, String workflowId, String serviceId) {
    List<Artifact> lastDeployedArtifacts = workflowExecutionService.obtainLastGoodDeployedArtifacts(appId, workflowId);
    if (lastDeployedArtifacts != null && serviceId != null) {
      List<String> artifactStreamIds = artifactStreamServiceBindingService.listArtifactStreamIds(appId, serviceId);
      if (isEmpty(artifactStreamIds)) {
        lastDeployedArtifacts = new ArrayList<>();
      } else {
        lastDeployedArtifacts = lastDeployedArtifacts.stream()
                                    .filter(artifact1 -> artifactStreamIds.contains(artifact1.getArtifactStreamId()))
                                    .collect(toList());
      }
    }
    return lastDeployedArtifacts == null ? new ArrayList<>() : lastDeployedArtifacts;
  }

  private void addHelmChartsFromSelections(String appId, Trigger trigger, List<HelmChart> helmCharts) {
    if (isEmpty(trigger.getManifestSelections())) {
      return;
    }
    trigger.getManifestSelections().forEach(manifestSelection -> {
      if (manifestSelection.getType() == ManifestSelectionType.LAST_COLLECTED) {
        addLastCollectedHelmChart(appId, manifestSelection, helmCharts);
      } else if (manifestSelection.getType() == ManifestSelectionType.LAST_DEPLOYED) {
        addLastDeployedHelmCharts(appId,
            trigger.getWorkflowType() == ORCHESTRATION ? manifestSelection.getWorkflowId()
                                                       : manifestSelection.getPipelineId(),
            Collections.singleton(manifestSelection.getServiceId()), helmCharts);
      }
    });
  }

  private void addLastDeployedHelmCharts(
      String appId, String workflowId, Set<String> serviceIds, List<HelmChart> helmCharts) {
    log.info("Adding last deployed helm charts for appId {}, workflowid {}", appId, workflowId);
    List<HelmChart> lastDeployedHelmCharts =
        workflowExecutionService.obtainLastGoodDeployedHelmCharts(appId, workflowId);
    if (isNotEmpty(lastDeployedHelmCharts)) {
      helmCharts.addAll(serviceIds == null ? lastDeployedHelmCharts
                                           : lastDeployedHelmCharts.stream()
                                                 .filter(helmChart -> serviceIds.contains(helmChart.getServiceId()))
                                                 .collect(toList()));
    }
  }

  private void addLastCollectedHelmChart(
      String appId, ManifestSelection manifestSelection, List<HelmChart> helmCharts) {
    ApplicationManifest appManifest = applicationManifestService.getById(appId, manifestSelection.getAppManifestId());
    notNullCheck("Application Manifest was deleted", appManifest, USER);
    helmCharts.add(isEmpty(manifestSelection.getVersionRegex())
            ? helmChartService.getLastCollectedManifest(appManifest.getAccountId(), appManifest.getUuid())
            : helmChartService.getLastCollectedManifestMatchingRegex(
                appManifest.getAccountId(), appManifest.getUuid(), manifestSelection.getVersionRegex()));
  }

  @VisibleForTesting
  WorkflowExecution triggerDeployment(
      List<Artifact> artifacts, List<HelmChart> helmCharts, TriggerExecution triggerExecution, Trigger trigger) {
    return triggerDeployment(artifacts, helmCharts, null, triggerExecution, trigger);
  }

  private WorkflowExecution triggerDeployment(List<Artifact> artifacts, List<HelmChart> helmCharts,
      Map<String, String> parameters, TriggerExecution triggerExecution, Trigger trigger) {
    ExecutionArgs executionArgs = new ExecutionArgs();

    if (isNotEmpty(artifacts)) {
      executionArgs.setArtifacts(
          artifacts.stream().filter(triggerServiceHelper.distinctByKey(Artifact::getUuid)).collect(toList()));
    }
    if (isNotEmpty(helmCharts)) {
      executionArgs.setHelmCharts(helmCharts.stream()
                                      .filter(Objects::nonNull)
                                      .filter(triggerServiceHelper.distinctByKey(HelmChart::getUuid))
                                      .collect(toList()));
    }
    executionArgs.setOrchestrationId(trigger.getWorkflowId());
    executionArgs.setExecutionCredential(aSSHExecutionCredential().withExecutionType(SSH).build());
    executionArgs.setWorkflowType(trigger.getWorkflowType());
    executionArgs.setExcludeHostsWithSameArtifact(trigger.isExcludeHostsWithSameArtifact());
    executionArgs.setCreatedByType(CreatedByType.TRIGGER);
    executionArgs.setContinueWithDefaultValues(trigger.isContinueWithDefaultValues());

    if (parameters != null) {
      executionArgs.setWorkflowVariables(parameters);
    }

    WorkflowExecution workflowExecution;
    try {
      if (ORCHESTRATION == trigger.getWorkflowType()) {
        workflowExecution = triggerOrchestrationDeployment(trigger, executionArgs, triggerExecution);
      } else {
        workflowExecution = triggerPipelineDeployment(trigger, triggerExecution, executionArgs);
      }
      return workflowExecution;
    } catch (DeploymentFreezeException dfe) {
      log.warn(dfe.getMessage());
      // TODO: Notification Handling for rejected triggers
      if (!dfe.isMasterFreeze()) {
        Map<String, String> placeholderValues = triggerServiceHelper.getPlaceholderValues(trigger.getAccountId(),
            trigger.getAppId(), trigger.getName(), trigger.getWorkflowId(), trigger.getWorkflowType());
        deploymentFreezeUtils.sendTriggerRejectedNotification(
            trigger.getAccountId(), trigger.getAppId(), dfe.getDeploymentFreezeIds(), placeholderValues);
      }
      throw dfe;
    }
  }

  private WorkflowExecution triggerPipelineDeployment(
      Trigger trigger, TriggerExecution triggerExecution, ExecutionArgs executionArgs) {
    WorkflowExecution workflowExecution;
    String accountId = appService.getAccountIdByAppId(trigger.getAppId());
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Triggering  execution of appId {} with  pipeline id {} , trigger type {}", trigger.getAppId(),
          trigger.getWorkflowId(), trigger.getCondition().getConditionType().name());
    }
    resolveTriggerPipelineVariables(trigger, executionArgs);
    executionArgs.setPipelineId(trigger.getWorkflowId());
    if (webhookTriggerProcessor.checkFileContentOptionSelected(trigger)) {
      log.info("Check file content option selected. Invoking delegate task to verify the file content.");
      TriggerExecution lastTriggerExecution = webhookTriggerProcessor.fetchLastExecutionForContentChanged(trigger);
      if (lastTriggerExecution == null) {
        triggerExecution.setExecutionArgs(executionArgs);
        triggerExecution.setStatus(Status.SUCCESS);
        triggerExecutionService.save(triggerExecution);
        workflowExecution =
            workflowExecutionService.triggerEnvExecution(trigger.getAppId(), null, executionArgs, trigger);
      } else {
        triggerExecution.setExecutionArgs(executionArgs);
        webhookTriggerProcessor.initiateTriggerContentChangeDelegateTask(
            trigger, lastTriggerExecution, triggerExecution, trigger.getAppId());
        workflowExecution = WorkflowExecution.builder().status(ExecutionStatus.NEW).build();
      }
    } else {
      workflowExecution =
          workflowExecutionService.triggerEnvExecution(trigger.getAppId(), null, executionArgs, trigger);
    }
    log.info(
        "Pipeline execution of appId {} with  pipeline id {} triggered", trigger.getAppId(), trigger.getWorkflowId());
    return workflowExecution;
  }

  private void resolveTriggerPipelineVariables(Trigger trigger, ExecutionArgs executionArgs) {
    Pipeline pipeline = pipelineService.readPipeline(trigger.getAppId(), trigger.getWorkflowId(), true);
    notNullCheck("Pipeline was deleted or does not exist", pipeline, USER);

    Map<String, String> triggerWorkflowVariableValues =
        overrideTriggerVariables(trigger, executionArgs, pipeline.getPipelineVariables());

    List<Variable> pipelineVariables = pipeline.getPipelineVariables();
    validateWorkflowVariable(triggerWorkflowVariableValues, pipelineVariables);
    String envId = null;
    String templatizedEnvName = getTemplatizedEnvVariableName(pipelineVariables);
    if (templatizedEnvName != null) {
      log.info("One of the environment is parameterized in the pipeline and Variable name {}", templatizedEnvName);
      String envNameOrId = triggerWorkflowVariableValues.get(templatizedEnvName);
      if (envNameOrId == null
          && pipelineVariables.stream()
                 .filter(v -> Boolean.TRUE.equals(v.getRuntimeInput()))
                 .noneMatch(v -> v.getName().equals(templatizedEnvName))) {
        String msg = "Pipeline contains environment as variable [" + templatizedEnvName
            + "]. However, there is no mapping associated in the trigger."
            + " Please update the trigger";
        throw new WingsException(msg, USER);
      }
      envId = resolveEnvId(trigger, envNameOrId);
      triggerWorkflowVariableValues.put(templatizedEnvName, envId);
    }

    resolveServices(trigger, triggerWorkflowVariableValues, pipelineVariables);

    resolveInfraDefinitions(trigger.getAppId(), triggerWorkflowVariableValues, envId, pipelineVariables);

    executionArgs.setWorkflowVariables(triggerWorkflowVariableValues);

    if (pipeline.isHasBuildWorkflow() || pipeline.isEnvParameterized()) {
      // TODO: Once artifact needed serviceIds implemented for templated pipeline then this logic has to be modified
      return;
    }
    List<String> artifactNeededServiceIds = isEmpty(pipeline.getServices())
        ? new ArrayList<>()
        : pipeline.getServices().stream().map(Service::getUuid).collect(toList());

    validateRequiredArtifacts(trigger, executionArgs, artifactNeededServiceIds);
  }

  private void resolveServices(Trigger trigger, Map<String, String> triggerVariableValues, List<Variable> variables) {
    List<String> serviceWorkflowVariables = getServiceWorkflowVariables(variables);
    for (String serviceVarName : serviceWorkflowVariables) {
      String serviceIdOrName = triggerVariableValues.get(serviceVarName);
      if (serviceIdOrName == null
          && variables.stream()
                 .filter(v -> Boolean.TRUE.equals(v.getRuntimeInput()))
                 .anyMatch(v -> v.getName().equals(serviceVarName))) {
        continue;
      }
      notNullCheck("There is no corresponding Workflow Variable associated to service", serviceIdOrName);
      log.info("Checking  service {} can be found by id first.", serviceIdOrName);
      Service service = serviceResourceService.get(trigger.getAppId(), serviceIdOrName, false);
      if (service == null) {
        log.info("Service does not exist by Id, checking if environment {} can be found by name.", serviceIdOrName);
        service = serviceResourceService.getServiceByName(trigger.getAppId(), serviceIdOrName, false);
      }
      notNullCheck("Service [" + serviceIdOrName + "] does not exist", service, USER);
      triggerVariableValues.put(serviceVarName, service.getUuid());
    }
  }

  private String resolveEnvId(Trigger trigger, String envNameOrId) {
    Environment environment;
    if (StringUtils.isBlank(envNameOrId)) {
      return envNameOrId;
    }
    log.info("Checking  environment {} can be found by id first.", envNameOrId);
    environment = environmentService.get(trigger.getAppId(), envNameOrId);
    if (environment == null) {
      log.info("Environment does not exist by Id, checking if environment {} can be found by name.", envNameOrId);
      environment = environmentService.getEnvironmentByName(trigger.getAppId(), envNameOrId, false);
    }
    notNullCheck("Resolved environment [" + envNameOrId
            + "] does not exist. Please ensure the environment variable mapped to the right payload value in the trigger",
        environment, USER);

    return environment.getUuid();
  }

  void resolveInfraDefinitions(
      String appId, Map<String, String> triggerWorkflowVariableValues, String envId, List<Variable> variables) {
    for (Variable variable : WorkflowServiceTemplateHelper.getInfraDefCompleteWorkflowVariables(variables)) {
      String infraEnvId = null;
      String infraDefVarName = variable.getName();
      String infraDefIdOrName = triggerWorkflowVariableValues.get(infraDefVarName);

      if (isNotEmpty(variable.getMetadata()) && variable.getMetadata().get(Variable.ENV_ID) != null) {
        infraEnvId = variable.getMetadata().get(Variable.ENV_ID).toString();
      } else {
        infraEnvId = envId;
      }

      if (isEmpty(infraDefVarName) || matchesVariablePattern(infraDefIdOrName)) {
        String infraMappingVarName = infraDefVarName.replace("InfraDefinition", "ServiceInfra");
        String infraMappingIdOrName = triggerWorkflowVariableValues.get(infraMappingVarName);
        InfrastructureMapping infrastructureMapping = getInfrastructureMapping(appId, infraEnvId, infraMappingIdOrName);
        notNullCheck(
            "Service Infrastructure [" + infraMappingIdOrName + "] does not exist", infrastructureMapping, USER);
        triggerWorkflowVariableValues.put(infraDefVarName, infrastructureMapping.getInfrastructureDefinitionId());
      } else {
        if (infraDefIdOrName.contains(",")) {
          if (!variable.isAllowMultipleValues()) {
            throw new InvalidRequestException(
                "Multiple values provided for infra var { " + infraDefVarName + " }, but variable only allows one");
          }
          String finalInfraValue = handleMultiInfra(appId, infraEnvId, infraDefIdOrName);
          triggerWorkflowVariableValues.put(infraDefVarName, finalInfraValue);
        } else {
          InfrastructureDefinition infrastructureDefinition =
              getInfrastructureDefinition(appId, infraEnvId, infraDefIdOrName);
          if (infrastructureDefinition == null) {
            InfrastructureMapping infrastructureMapping = getInfrastructureMapping(appId, infraEnvId, infraDefIdOrName);
            notNullCheck(
                "Service Infrastructure [" + infraDefIdOrName + "] does not exist", infrastructureMapping, USER);
            triggerWorkflowVariableValues.put(infraDefVarName, infrastructureMapping.getInfrastructureDefinitionId());
          } else {
            triggerWorkflowVariableValues.put(infraDefVarName, infrastructureDefinition.getUuid());
          }
        }
      }
    }
  }

  private String handleMultiInfra(String appId, String infraEnvId, String infraDefIdOrName) {
    String[] variableValues = infraDefIdOrName.trim().split("\\s*,\\s*");
    List<String> finalValues = new ArrayList<>();
    for (String variableValue : variableValues) {
      InfrastructureDefinition infrastructureDefinition = getInfrastructureDefinition(appId, infraEnvId, variableValue);
      notNullCheck("Infrastructure Definition [" + variableValue + "] does not exist", infrastructureDefinition, USER);
      finalValues.add(infrastructureDefinition.getUuid());
    }
    return String.join(",", finalValues);
  }

  InfrastructureDefinition getInfrastructureDefinition(String appId, String envId, String infraDefIdOrName) {
    InfrastructureDefinition infrastructureDefinition = infrastructureDefinitionService.get(appId, infraDefIdOrName);
    if (infrastructureDefinition == null) {
      log.info("InfraDefinition does not exist by Id, checking if infra definition {} can be found by name.",
          infraDefIdOrName);
      infrastructureDefinition = infrastructureDefinitionService.getInfraDefByName(appId, envId, infraDefIdOrName);
    }
    return infrastructureDefinition;
  }

  InfrastructureMapping getInfrastructureMapping(String appId, String envId, String infraDefIdOrName) {
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraDefIdOrName);
    if (infrastructureMapping == null) {
      log.info(
          "Service Infrastructure does not exist by Id, checking if service infrastructure {} can be found by name.",
          infraDefIdOrName);
      infrastructureMapping = infrastructureMappingService.getInfraMappingByName(appId, envId, infraDefIdOrName);
    }
    return infrastructureMapping;
  }

  private WorkflowExecution triggerOrchestrationDeployment(
      Trigger trigger, ExecutionArgs executionArgs, TriggerExecution triggerExecution) {
    String accountId = appService.getAccountIdByAppId(trigger.getAppId());
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, OVERRIDE_ERROR)) {
      log.info("Triggering workflow execution of appId {} with with workflow id {} , trigger type {}",
          trigger.getAppId(), trigger.getWorkflowId(), trigger.getCondition().getConditionType().name());
    }

    Workflow workflow = workflowService.readWorkflow(trigger.getAppId(), trigger.getWorkflowId());
    notNullCheck("Workflow was deleted", workflow, USER);
    notNullCheck("Orchestration Workflow not present", workflow.getOrchestrationWorkflow(), USER);

    Map<String, String> triggerWorkflowVariableValues =
        overrideTriggerVariables(trigger, executionArgs, workflow.getOrchestrationWorkflow().getUserVariables());

    List<Variable> workflowVariables = workflow.getOrchestrationWorkflow().getUserVariables();
    validateWorkflowVariable(executionArgs.getWorkflowVariables(), workflowVariables);

    String envId = null;
    if (BUILD == workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType()) {
      executionArgs.setArtifacts(new ArrayList<>());
    } else {
      if (workflow.checkEnvironmentTemplatized()) {
        String templatizedEnvName = getTemplatizedEnvVariableName(workflowVariables);
        String envNameOrId = triggerWorkflowVariableValues.get(templatizedEnvName);
        notNullCheck(
            "Workflow Environment is templatized. However, there is no corresponding mapping associated in the trigger. "
                + " Please update the trigger",
            envNameOrId, USER);
        envId = resolveEnvId(trigger, envNameOrId);
        triggerWorkflowVariableValues.put(templatizedEnvName, envId);
      } else {
        envId = workflow.getEnvId();
      }
      notNullCheck("Environment  [" + envId + "] might have been deleted", envId, USER);

      resolveServices(trigger, triggerWorkflowVariableValues, workflowVariables);
      resolveInfraDefinitions(trigger.getAppId(), triggerWorkflowVariableValues, envId, workflowVariables);

      /* Fetch the deployment data to find out the required entity types */
      DeploymentMetadata deploymentMetadata = workflowService.fetchDeploymentMetadata(
          trigger.getAppId(), workflow, triggerWorkflowVariableValues, null, null, Include.ARTIFACT_SERVICE);

      // Fetch the service
      List<String> artifactNeededServiceIds =
          deploymentMetadata == null ? new ArrayList<>() : deploymentMetadata.getArtifactRequiredServiceIds();
      validateRequiredArtifacts(trigger, executionArgs, artifactNeededServiceIds);
    }

    executionArgs.setWorkflowVariables(triggerWorkflowVariableValues);
    // Validate if the file path content changed
    log.info("Triggering workflow execution of appId {} with workflow id {} triggered", trigger.getAppId(),
        trigger.getWorkflowId());
    WorkflowExecution workflowExecution;
    if (webhookTriggerProcessor.checkFileContentOptionSelected(trigger)) {
      TriggerExecution lastTriggerExecution = webhookTriggerProcessor.fetchLastExecutionForContentChanged(trigger);
      if (lastTriggerExecution == null) {
        triggerExecution.setStatus(Status.SUCCESS);
        triggerExecution.setExecutionArgs(executionArgs);
        triggerExecution.setEnvId(envId);
        triggerExecutionService.save(triggerExecution);

        workflowExecution =
            workflowExecutionService.triggerEnvExecution(trigger.getAppId(), envId, executionArgs, trigger);
      } else {
        log.info("Check file content option selected. Invoking delegate task to verify the file content.");
        triggerExecution.setExecutionArgs(executionArgs);
        webhookTriggerProcessor.initiateTriggerContentChangeDelegateTask(
            trigger, lastTriggerExecution, triggerExecution, trigger.getAppId());
        workflowExecution = WorkflowExecution.builder().status(ExecutionStatus.NEW).build();
      }
    } else {
      workflowExecution =
          workflowExecutionService.triggerEnvExecution(trigger.getAppId(), envId, executionArgs, trigger);
    }
    return workflowExecution;
  }

  @VisibleForTesting
  void validateWorkflowVariable(Map<String, String> nameToVariableValueMap, List<Variable> workflowVariables) {
    if (isEmpty(nameToVariableValueMap)) {
      return;
    }
    for (Variable variable : workflowVariables) {
      List<String> allowedValues = variable.getAllowedList();
      if (isNotEmpty(allowedValues)) {
        String variableValue = nameToVariableValueMap.get(variable.getName());
        if (isNotEmpty(variableValue) && !allowedValues.contains(variableValue)) {
          throw new InvalidRequestException(String.format(
              "Trigger rejected because passed workflow variable value %s was not present in allowed values list [%s]",
              variableValue, String.join(",", allowedValues)));
        }
      }
    }
  }

  private void validateRequiredArtifacts(
      Trigger trigger, ExecutionArgs executionArgs, List<String> artifactNeededServiceIds) {
    // Artifact serviceIds
    List<String> collectedArtifactServiceIds = triggerServiceHelper.obtainCollectedArtifactServiceIds(executionArgs);
    String accountId = appService.getAccountIdByAppId(trigger.getAppId());
    if (isEmpty(artifactNeededServiceIds) && isNotEmpty(collectedArtifactServiceIds)) {
      WorkflowType workflowType = trigger.getWorkflowType() == null ? PIPELINE : trigger.getWorkflowType();
      StringBuilder msg = new StringBuilder(128);
      msg.append("Trigger [")
          .append(trigger.getName())
          .append("] rejected. Reason: ")
          .append(PIPELINE == workflowType ? "Pipeline" : "Workflow")
          .append(" [")
          .append(trigger.fetchWorkflowOrPipelineName())
          .append("] does not need artifacts. However, trigger received with the artifacts");

      if (featureFlagService.isEnabled(FeatureName.REJECT_TRIGGER_IF_ARTIFACTS_NOT_MATCH, accountId)) {
        log.warn(msg.toString());
        throw new WingsException(msg.toString());
      }
    }
    List<String> missingServiceIds = new ArrayList<>();
    for (String artifactNeededServiceId : artifactNeededServiceIds) {
      if (collectedArtifactServiceIds.contains(artifactNeededServiceId)) {
        collectedArtifactServiceIds.remove(artifactNeededServiceId);
      } else {
        missingServiceIds.add(artifactNeededServiceId);
      }
    }
    if (isNotEmpty(missingServiceIds)) {
      log.info(
          "Artifact needed serviceIds {} do not match with the collected artifact serviceIds {}. Rejecting the trigger {} execution",
          artifactNeededServiceIds, collectedArtifactServiceIds, trigger.getUuid());
      List<String> missingServiceNames =
          serviceResourceService.fetchServiceNamesByUuids(trigger.getAppId(), missingServiceIds);

      if (featureFlagService.isEnabled(FeatureName.REJECT_TRIGGER_IF_ARTIFACTS_NOT_MATCH, accountId)) {
        log.warn("Trigger rejected. Reason: Artifacts are missing for service name(s) {}", missingServiceNames);
        String message = "Trigger [" + trigger.getName()
            + " ] rejected. Reason: Artifacts are missing for service name(s)" + missingServiceNames;
        throw new WingsException(message, USER);
      }
    }
    if (isNotEmpty(collectedArtifactServiceIds)) {
      WorkflowType workflowType = trigger.getWorkflowType() == null ? PIPELINE : trigger.getWorkflowType();
      StringBuilder msg =
          new StringBuilder("Trigger rejected. Reason: More artifacts received than required artifacts for ");
      msg.append(PIPELINE == workflowType ? "Pipeline" : "Workflow")
          .append(" [")
          .append(trigger.fetchWorkflowOrPipelineName())
          .append(']');

      if (featureFlagService.isEnabled(FeatureName.REJECT_TRIGGER_IF_ARTIFACTS_NOT_MATCH, accountId)) {
        log.warn(msg.toString());
        throw new WingsException(msg.toString());
      }
    }
  }

  @Override
  public boolean triggerExecutionByServiceInfra(String appId, String infraMappingId) {
    log.info("Received the trigger execution for appId {} and infraMappingId {}", appId, infraMappingId);
    InfrastructureMapping infrastructureMapping = infrastructureMappingService.get(appId, infraMappingId);
    if (infrastructureMapping == null) {
      throw new InvalidRequestException("Infrastructure Mapping" + infraMappingId + " does not exist", USER);
    }

    List<ServiceInfraWorkflow> serviceInfraWorkflows =
        triggerServiceHelper.getServiceInfraWorkflows(appId, infraMappingId);

    List<String> serviceIds = Collections.singletonList(infrastructureMapping.getServiceId());
    List<String> envIds = Collections.singletonList(infrastructureMapping.getEnvId());

    serviceInfraWorkflows.forEach((ServiceInfraWorkflow serviceInfraWorkflow) -> {
      if (serviceInfraWorkflow.getWorkflowType() == null || serviceInfraWorkflow.getWorkflowType() == ORCHESTRATION) {
        log.info("Retrieving the last workflow execution for workflowId {} and infraMappingId {}",
            serviceInfraWorkflow.getWorkflowId(), infraMappingId);
        WorkflowExecution workflowExecution = workflowExecutionService.fetchWorkflowExecution(
            appId, serviceIds, envIds, serviceInfraWorkflow.getWorkflowId());
        if (workflowExecution == null) {
          log.warn("No Last workflow execution found for workflowId {} and infraMappingId {}",
              serviceInfraWorkflow.getWorkflowId(), serviceInfraWorkflow.getInfraMappingId());
        } else {
          log.info("Triggering workflow execution {}  for appId {} and infraMappingId {}", workflowExecution.getUuid(),
              workflowExecution.getWorkflowId(), infraMappingId);
          // TODO: Refactor later
          if (workflowExecution.getExecutionArgs() != null) {
            workflowExecution.getExecutionArgs().setCreatedByType(CreatedByType.TRIGGER);
          }
          workflowExecutionService.triggerEnvExecution(
              appId, workflowExecution.getEnvId(), workflowExecution.getExecutionArgs(), null);
        }
      }
    });
    return true;
  }

  @Override
  public void handleTriggerTaskResponse(String appId, String triggerExecutionId, TriggerResponse triggerResponse) {
    log.info("Received the call back from delegate with the task response {}", triggerResponse);
    TriggerExecution triggerExecution = triggerExecutionService.get(appId, triggerExecutionId);
    notNullCheck("Trigger execution might have pruned", triggerExecution);
    Trigger trigger = get(triggerExecution.getAppId(), triggerExecution.getTriggerId());
    notNullCheck("Trigger might have been deleted", trigger);
    ExecutionStatus taskStatus = triggerResponse.getExecutionStatus();
    if (ExecutionStatus.SUCCESS == taskStatus) {
      if (triggerResponse instanceof TriggerDeploymentNeededResponse) {
        TriggerDeploymentNeededResponse triggerDeploymentNeededResponse =
            (TriggerDeploymentNeededResponse) triggerResponse;
        if (triggerDeploymentNeededResponse.isDeploymentNeeded()) {
          try {
            log.info("File path content changed for the trigger {}.", trigger.getUuid());
            if (triggerExecution.getExecutionArgs() != null) {
              triggerExecution.getExecutionArgs().setCreatedByType(CreatedByType.TRIGGER);
            }
            switch (trigger.getWorkflowType()) {
              case ORCHESTRATION:
                log.info("Starting deployment for the workflow {}", trigger.getWorkflowId());
                workflowExecutionService.triggerEnvExecution(
                    trigger.getAppId(), triggerExecution.getEnvId(), triggerExecution.getExecutionArgs(), trigger);
                triggerExecutionService.updateStatus(appId, triggerExecutionId, Status.SUCCESS, "File content changed");
                break;
              case PIPELINE:
                log.info("Starting deployment for the pipeline {}", trigger.getWorkflowId());
                if (triggerExecution.getExecutionArgs() != null) {
                  triggerExecution.getExecutionArgs().setPipelineId(trigger.getWorkflowId());
                }
                workflowExecutionService.triggerEnvExecution(
                    trigger.getAppId(), null, triggerExecution.getExecutionArgs(), trigger);
                triggerExecutionService.updateStatus(appId, triggerExecutionId, Status.SUCCESS, "File content changed");
                break;
              default:
                unhandled(triggerExecution.getWorkflowType());
            }
          } catch (WingsException exception) {
            // Update trigger Status Failed
            triggerExecutionService.updateStatus(
                appId, triggerExecutionId, Status.FAILED, ExceptionUtils.getMessage(exception));
            exception.addContext(Application.class, trigger.getAppId());
            exception.addContext(TriggerExecution.class, triggerExecution.getUuid());
            exception.addContext(Trigger.class, trigger.getUuid());
            ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
          } catch (Exception exception) {
            triggerExecutionService.updateStatus(
                appId, triggerExecutionId, Status.FAILED, ExceptionUtils.getMessage(exception));
            log.error("Exception occurred while starting deployment of the trigger execution {}",
                triggerExecution.getUuid(), exception);
          }
        } else {
          log.info("File  content not changed for the trigger {}. Skipping the execution", trigger.getUuid());
          triggerExecutionService.updateStatus(
              appId, triggerExecutionId, Status.SUCCESS, "File content not changed. Skipped deployment");
        }
      } else {
        log.error("Wrong Response {} Received from trigger callback", triggerResponse);
      }
    } else {
      triggerExecutionService.updateStatus(appId, triggerExecutionId, Status.FAILED, triggerResponse.getErrorMsg());
    }
  }

  private boolean needValidationForWebHook(WebHookTriggerCondition webhookTriggerCondition) {
    return (webhookTriggerCondition.getEventTypes() != null
               && webhookTriggerCondition.getEventTypes().contains(WebhookEventType.PUSH))
        && ((webhookTriggerCondition.getWebhookSource() == GITHUB)
            || (webhookTriggerCondition.getWebhookSource() == GITLAB));
  }

  private boolean validateWebhookTriggerCondition(Trigger trigger, Trigger existingTrigger) {
    if (existingTrigger == null) {
      return true;
    }

    WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();

    if (existingTrigger.getCondition().getConditionType() != WEBHOOK) {
      return !webHookTriggerCondition.isCheckFileContentChanged() && webHookTriggerCondition.getGitConnectorId() == null
          && webHookTriggerCondition.getBranchName() == null && webHookTriggerCondition.getFilePaths() == null;
    }

    WebHookTriggerCondition existingWebHookTriggerCondition = (WebHookTriggerCondition) existingTrigger.getCondition();

    if (needValidationForWebHook(webHookTriggerCondition)) {
      boolean isRepoNameSame = true;
      boolean isCheckFileContentChangedSame = webHookTriggerCondition.isCheckFileContentChanged()
          == existingWebHookTriggerCondition.isCheckFileContentChanged();

      if (isCheckFileContentChangedSame) {
        GitConfig gitConfig =
            settingsService.fetchGitConfigFromConnectorId(webHookTriggerCondition.getGitConnectorId());
        if (gitConfig != null && GitConfig.UrlType.ACCOUNT == gitConfig.getUrlType()) {
          isRepoNameSame =
              Objects.equals(webHookTriggerCondition.getRepoName(), existingWebHookTriggerCondition.getRepoName());
        }
      }

      return isCheckFileContentChangedSame && isRepoNameSame
          && Objects.equals(
              webHookTriggerCondition.getGitConnectorId(), existingWebHookTriggerCondition.getGitConnectorId())
          && Objects.equals(webHookTriggerCondition.getBranchName(), existingWebHookTriggerCondition.getBranchName())
          && Objects.equals(webHookTriggerCondition.getFilePaths(), existingWebHookTriggerCondition.getFilePaths());
    }

    return true;
  }

  private void validateAndSetTriggerCondition(Trigger trigger, Trigger existingTrigger) {
    switch (trigger.getCondition().getConditionType()) {
      case NEW_ARTIFACT:
        validateAndSetNewArtifactCondition(trigger);
        break;
      case PIPELINE_COMPLETION:
        PipelineTriggerCondition triggerCondition = (PipelineTriggerCondition) trigger.getCondition();
        triggerCondition.setPipelineName(
            pipelineService.fetchPipelineName(trigger.getAppId(), triggerCondition.getPipelineId()));
        break;
      case WEBHOOK:
        WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
        WebHookToken webHookToken = generateWebHookToken(trigger, getExistingWebhookToken(existingTrigger));
        webHookTriggerCondition.setWebHookToken(webHookToken);
        validateWebHookSecret(trigger, webHookTriggerCondition);
        if (BITBUCKET == webHookTriggerCondition.getWebhookSource()
            && isNotEmpty(webHookTriggerCondition.getActions())) {
          throw new InvalidRequestException("Actions not supported for Bit Bucket", USER);
        }
        trigger.setWebHookToken(webHookTriggerCondition.getWebHookToken().getWebHookToken());
        if (!validateWebhookTriggerCondition(trigger, existingTrigger)) {
          throw new InvalidRequestException(
              "Deploy if files has changed, Git Connector, Branch Name, Repo Name and File Paths cannot be changed on updating Trigger");
        }
        if (webHookTriggerCondition.isCheckFileContentChanged()) {
          log.info("File paths to watch selected");
          List<String> filePaths = trimStrings(webHookTriggerCondition.getFilePaths());
          if (isEmpty(filePaths)) {
            throw new InvalidRequestException("At least one file path is required to check content changed");
          }
          if (isEmpty(webHookTriggerCondition.getGitConnectorId())) {
            throw new InvalidRequestException("Git connector is required to check content changed");
          }
          String branchName = StringUtils.trim(webHookTriggerCondition.getBranchName());
          if (isEmpty(branchName)) {
            throw new InvalidRequestException("Branch name is required to check content changed");
          } else {
            webHookTriggerCondition.setBranchName(branchName);
          }

          GitConfig gitConfig =
              settingsService.fetchGitConfigFromConnectorId(webHookTriggerCondition.getGitConnectorId());
          if (GitConfig.UrlType.ACCOUNT == gitConfig.getUrlType()) {
            String repoName = StringUtils.trim(webHookTriggerCondition.getRepoName());
            if (isEmpty(repoName)) {
              throw new InvalidRequestException("Repo name is required to check content changed");
            } else {
              webHookTriggerCondition.setRepoName(repoName);
            }
          }

          if (isEmpty(webHookTriggerCondition.getEventTypes())
              || !webHookTriggerCondition.getEventTypes().contains(WebhookEventType.PUSH)) {
            throw new InvalidRequestException("File content check supported only for PUSH events");
          }
        } else {
          // Clear the content
          webHookTriggerCondition.setFilePaths(null);
          webHookTriggerCondition.setBranchName(null);
        }
        break;
      case SCHEDULED:
        ScheduledTriggerCondition scheduledTriggerCondition = (ScheduledTriggerCondition) trigger.getCondition();
        notNullCheck("CronExpression", scheduledTriggerCondition.getCronExpression(), USER);
        break;
      case NEW_INSTANCE:
        NewInstanceTriggerCondition newInstanceTriggerCondition = (NewInstanceTriggerCondition) trigger.getCondition();
        notNullCheck("NewInstanceTriggerCondition", newInstanceTriggerCondition, USER);
        validateAndSetServiceInfraWorkflows(trigger);
        break;
      case NEW_MANIFEST:
        validateAndSetNewManifestCondition(trigger);
        break;
      default:
        throw new InvalidRequestException("Invalid trigger condition type", USER);
    }
  }

  private void validateWebHookSecret(Trigger trigger, WebHookTriggerCondition webHookTriggerCondition) {
    if (webHookTriggerCondition.getWebHookSecret() == null) {
      return;
    }
    if (featureFlagService.isEnabled(GITHUB_WEBHOOK_AUTHENTICATION, trigger.getAccountId())) {
      if (webHookTriggerCondition.getWebHookSecret() != null
          && !GITHUB.equals(webHookTriggerCondition.getWebhookSource())) {
        throw new InvalidRequestException("WebHook Secret is only supported with Github repository", USER);
      }

      EncryptedData encryptedData =
          wingsPersistence.get(EncryptedData.class, webHookTriggerCondition.getWebHookSecret());
      notNullCheck(
          "No encrypted record found for webhook secret in Trigger: " + trigger.getName(), encryptedData, USER);
    } else {
      if (webHookTriggerCondition.getWebHookSecret() != null) {
        throw new InvalidRequestException("Please enable feature flag to authenticate your webhook sources");
      }
    }
  }

  private void validateAndSetNewManifestCondition(Trigger trigger) {
    ManifestTriggerCondition manifestTriggerCondition = (ManifestTriggerCondition) trigger.getCondition();
    String appId = trigger.getAppId();
    ApplicationManifest applicationManifest =
        applicationManifestService.getById(appId, manifestTriggerCondition.getAppManifestId());
    notNullCheck("Application Manifest must exist for the given service", applicationManifest, USER);
    manifestTriggerCondition.setAppManifestName(applicationManifest.getName());
    if (!featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, applicationManifest.getAccountId())) {
      throw new InvalidRequestException("Invalid trigger condition type", USER);
    }
    if (!Boolean.TRUE.equals(applicationManifest.getPollForChanges())) {
      throw new InvalidRequestException("Cannot select service for which poll for manifest is not enabled", USER);
    }
    try {
      if (isNotEmpty(manifestTriggerCondition.getVersionRegex())) {
        compile(manifestTriggerCondition.getVersionRegex());
      }
    } catch (PatternSyntaxException pe) {
      throw new InvalidRequestException("Invalid versionRegex, Please provide a valid regex", USER);
    }
    Service service = serviceResourceService.get(appId, applicationManifest.getServiceId());
    notNullCheck("Service does not exist", service, USER);
    manifestTriggerCondition.setServiceId(service.getUuid());
    manifestTriggerCondition.setServiceName(service.getName());
  }

  private WebHookToken getExistingWebhookToken(Trigger existingTrigger) {
    WebHookToken existingWebhookToken = null;
    if (existingTrigger != null && existingTrigger.getCondition().getConditionType() == WEBHOOK) {
      WebHookTriggerCondition existingTriggerCondition = (WebHookTriggerCondition) existingTrigger.getCondition();
      existingWebhookToken = existingTriggerCondition.getWebHookToken();
    }
    return existingWebhookToken;
  }

  private void validateAndSetNewArtifactCondition(Trigger trigger) {
    ArtifactTriggerCondition artifactTriggerCondition = (ArtifactTriggerCondition) trigger.getCondition();
    String artifactFilter = artifactTriggerCondition.getArtifactFilter();
    if (isNotEmpty(artifactFilter)) {
      validateArtifactFilter(artifactTriggerCondition.isRegex(), artifactFilter);
    }
    ArtifactStream artifactStream = artifactStreamService.get(artifactTriggerCondition.getArtifactStreamId());
    notNullCheck("Artifact Source is mandatory for New Artifact Condition Trigger", artifactStream, USER);
    if (artifactStream.isArtifactStreamParameterized()) {
      throw new InvalidRequestException(
          "Cannot select parameterized artifact source for New Artifact Condition Trigger");
    }
    Service service =
        artifactStreamServiceBindingService.getService(trigger.getAppId(), artifactStream.getUuid(), false);
    notNullCheck("Service does not exist", service, USER);
    artifactTriggerCondition.setArtifactSourceName(artifactStream.getSourceName() + " (" + service.getName() + ")");
  }

  private void validateArtifactFilter(Boolean isRegex, String artifactFilter) {
    if (isRegex) {
      try {
        compile(artifactFilter);
      } catch (PatternSyntaxException pe) {
        throw new WingsException("Invalid Build/Tag Filter, Please provide a valid regex", USER);
      }
    } else {
      try {
        compile(artifactFilter.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));
      } catch (PatternSyntaxException pe) {
        throw new WingsException("Invalid Build/Tag Filter", USER);
      }
    }
  }

  private void validateAndSetArtifactSelections(Trigger trigger, List<Service> services) {
    List<ArtifactSelection> artifactSelections = trigger.getArtifactSelections();
    if (isEmpty(artifactSelections)) {
      return;
    }

    artifactSelections.forEach(artifactSelection -> {
      switch (artifactSelection.getType()) {
        case LAST_DEPLOYED:
          validateAndSetLastDeployedArtifactSelection(trigger, artifactSelection);
          break;
        case LAST_COLLECTED:
          if (isEmpty(artifactSelection.getArtifactStreamId())) {
            throw new WingsException("Artifact Source cannot be empty for Last collected type");
          }
          ArtifactStream artifactStream = artifactStreamService.get(artifactSelection.getArtifactStreamId());
          notNullCheck("Artifact Source does not exist", artifactStream, USER);
          if (artifactStream.isArtifactStreamParameterized()) {
            throw new InvalidRequestException("Cannot select parameterized artifact source for last collected type");
          }
          setArtifactSourceName(trigger, artifactSelection);
          break;
        case WEBHOOK_VARIABLE:
          WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
          if (webHookTriggerCondition.getWebhookSource() == null) {
            if (isNotEmpty(artifactSelection.getArtifactStreamId())) {
              setArtifactSourceName(trigger, artifactSelection);
            }
          }
          break;
        case ARTIFACT_SOURCE:
        case PIPELINE_SOURCE:
          break;
        default:
          throw new InvalidRequestException("Invalid artifact selection type", USER);
      }
      setServiceName(trigger, services, artifactSelection);
    });
  }

  private void validateContinueWithDefault(Trigger trigger, Pipeline pipeline) {
    if (isNotEmpty(pipeline.getPipelineVariables())) {
      Set<String> reqVariables = pipeline.getPipelineVariables()
                                     .stream()
                                     .filter(Variable::isMandatory)
                                     .map(Variable::getName)
                                     .collect(toSet());
      Map<String, String> wfVars = trigger.getWorkflowVariables();
      if ((!reqVariables.isEmpty() && isEmpty(wfVars)) || !wfVars.keySet().containsAll(reqVariables)) {
        throw new InvalidRequestException("default value absent for mandatory variable(s)", USER);
      }
      if (reqVariables.stream().anyMatch(key -> !wfVars.containsKey(key) || isEmpty(wfVars.get(key)))) {
        throw new InvalidRequestException("default value absent for mandatory variable(s)", USER);
      }
    }
  }

  private void validateAndSetManifestSelections(Trigger trigger, List<Service> services) {
    List<ManifestSelection> manifestSelections = trigger.getManifestSelections();
    if (isEmpty(manifestSelections)) {
      return;
    }

    manifestSelections.forEach(manifestSelection -> {
      setServiceName(trigger, services, manifestSelection);
      switch (manifestSelection.getType()) {
        case LAST_DEPLOYED:
          validateAndSetLastDeployedManifestSelection(trigger, manifestSelection);
          break;
        case LAST_COLLECTED:
          setAppManifestIdInSelection(trigger, manifestSelection);
          break;
        case WEBHOOK_VARIABLE:
          WebHookTriggerCondition webHookTriggerCondition = (WebHookTriggerCondition) trigger.getCondition();
          if (webHookTriggerCondition.getWebhookSource() == null) {
            setAppManifestIdInSelection(trigger, manifestSelection);
          }
          break;
        case FROM_APP_MANIFEST:
        case PIPELINE_SOURCE:
          break;
        default:
          throw new InvalidRequestException("Invalid manifest selection type", USER);
      }
    });
  }

  private void setAppManifestIdInSelection(Trigger trigger, ManifestSelection manifestSelection) {
    if (isNotEmpty(manifestSelection.getAppManifestId())) {
      return;
    }
    ApplicationManifest applicationManifest =
        applicationManifestService.getManifestByServiceId(trigger.getAppId(), manifestSelection.getServiceId());
    notNullCheck("Application Manifest does not exist", applicationManifest, USER);
    manifestSelection.setAppManifestId(applicationManifest.getUuid());
  }

  private void setArtifactSourceName(Trigger trigger, ArtifactSelection artifactSelection) {
    ArtifactStream artifactStream;
    Service service;
    artifactStream = artifactStreamService.get(artifactSelection.getArtifactStreamId());
    notNullCheck("Artifact Source does not exist", artifactStream, USER);
    service = artifactStreamServiceBindingService.getService(trigger.getAppId(), artifactStream.getUuid(), false);
    notNullCheck("Service might have been deleted", service, USER);
    artifactSelection.setArtifactSourceName(artifactStream.getSourceName() + " (" + service.getName() + ")");
  }

  private void setServiceName(Trigger trigger, List<Service> services, ArtifactSelection artifactSelection) {
    Map<String, String> serviceIdNames = services.stream().collect(toMap(Service::getUuid, Service::getName));
    Service service;
    if (serviceIdNames.get(artifactSelection.getServiceId()) == null) {
      service = serviceResourceService.get(trigger.getAppId(), artifactSelection.getServiceId(), false);
      notNullCheck("Service might have been deleted", service, USER);
      artifactSelection.setServiceName(service.getName());
    } else {
      artifactSelection.setServiceName(serviceIdNames.get(artifactSelection.getServiceId()));
    }
  }

  private void setServiceName(Trigger trigger, List<Service> services, ManifestSelection manifestSelection) {
    Map<String, String> serviceIdNames = services.stream().collect(toMap(Service::getUuid, Service::getName));
    Service service;
    if (serviceIdNames.get(manifestSelection.getServiceId()) == null) {
      service = serviceResourceService.get(trigger.getAppId(), manifestSelection.getServiceId(), false);
      notNullCheck("Service might have been deleted", service, USER);
      manifestSelection.setServiceName(service.getName());
    } else {
      manifestSelection.setServiceName(serviceIdNames.get(manifestSelection.getServiceId()));
    }
  }

  private void validateAndSetLastDeployedArtifactSelection(Trigger trigger, ArtifactSelection artifactSelection) {
    if (isBlank(artifactSelection.getWorkflowId())) {
      throw new InvalidRequestException("Workflow/Pipeline cannot be empty for Last deployed type", USER);
    }
    if (ORCHESTRATION == trigger.getWorkflowType()) {
      artifactSelection.setWorkflowName(
          workflowService.fetchWorkflowName(trigger.getAppId(), artifactSelection.getWorkflowId()));
    } else {
      artifactSelection.setWorkflowName(
          pipelineService.fetchPipelineName(trigger.getAppId(), artifactSelection.getWorkflowId()));
    }
  }

  private void validateAndSetLastDeployedManifestSelection(Trigger trigger, ManifestSelection manifestSelection) {
    if (ORCHESTRATION == trigger.getWorkflowType()) {
      if (isBlank(manifestSelection.getWorkflowId())) {
        throw new InvalidRequestException("Workflow/Pipeline cannot be empty for Last deployed type", USER);
      }
      manifestSelection.setWorkflowName(
          workflowService.fetchWorkflowName(trigger.getAppId(), manifestSelection.getWorkflowId()));
    } else {
      if (isBlank(manifestSelection.getPipelineId())) {
        throw new InvalidRequestException("Workflow/Pipeline cannot be empty for Last deployed type", USER);
      }
      manifestSelection.setPipelineName(
          pipelineService.fetchPipelineName(trigger.getAppId(), manifestSelection.getPipelineId()));
    }
  }

  private void validateAndSetServiceInfraWorkflows(Trigger trigger) {
    List<ServiceInfraWorkflow> serviceInfraWorkflows = trigger.getServiceInfraWorkflows();
    if (serviceInfraWorkflows == null) {
      throw new WingsException("ServiceInfra and Workflow Mapping can not be empty.", USER);
    } else {
      serviceInfraWorkflows.forEach(serviceInfraWorkflow -> {
        InfrastructureMapping infrastructureMapping =
            infrastructureMappingService.get(trigger.getAppId(), serviceInfraWorkflow.getInfraMappingId());
        notNullCheck("ServiceInfraStructure", infrastructureMapping, USER);
        serviceInfraWorkflow.setInfraMappingName(infrastructureMapping.getName());
        Workflow workflow = workflowService.readWorkflow(trigger.getAppId(), serviceInfraWorkflow.getWorkflowId());
        notNullCheck("Workflow", workflow, USER);
        if (workflow.isTemplatized()) {
          serviceInfraWorkflow.setWorkflowName(workflow.getName() + " (TEMPLATE)");
        } else {
          serviceInfraWorkflow.setWorkflowName(workflow.getName());
        }
      });
    }
  }

  private void validateInput(Trigger trigger, Trigger existingTrigger) {
    List<Service> services;
    if (PIPELINE == trigger.getWorkflowType()) {
      Pipeline executePipeline = pipelineService.readPipeline(trigger.getAppId(), trigger.getWorkflowId(), true);
      trigger.setWorkflowName(executePipeline.getName());
      services = executePipeline.getServices();

      if (!TriggerConditionType.WEBHOOK.equals(trigger.getCondition().getConditionType())
          && trigger.getWorkflowVariables() != null) {
        validateVariablesOfEntityTypeForNonWebhookTriggers(
            executePipeline.getPipelineVariables(), trigger.getWorkflowVariables());
      }

      validateAndSetArtifactSelections(trigger, services);
      if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, trigger.getAccountId())) {
        validateAndSetManifestSelections(trigger, services);
      }
      if (trigger.isContinueWithDefaultValues()) {
        validateContinueWithDefault(trigger, executePipeline);
      }
    } else if (ORCHESTRATION == trigger.getWorkflowType()) {
      Workflow workflow = workflowService.readWorkflow(trigger.getAppId(), trigger.getWorkflowId());
      notNullCheckWorkflow(workflow);
      if (workflow.isTemplatized()) {
        trigger.setWorkflowName(workflow.getName() + " (TEMPLATE)");
      } else {
        trigger.setWorkflowName(workflow.getName());
      }
      services = workflow.getServices();

      if (!TriggerConditionType.WEBHOOK.equals(trigger.getCondition().getConditionType())
          && workflow.getOrchestrationWorkflow() != null && trigger.getWorkflowVariables() != null) {
        validateVariablesOfEntityTypeForNonWebhookTriggers(
            workflow.getOrchestrationWorkflow().getUserVariables(), trigger.getWorkflowVariables());
      }

      validateAndSetArtifactSelections(trigger, services);
      if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, trigger.getAccountId())) {
        validateAndSetManifestSelections(trigger, services);
      }
    }
    validateAndSetTriggerCondition(trigger, existingTrigger);
    validateAndSetCronExpression(trigger);
  }

  // Values of individual variables is stored in triggerVariables and Entity types are stored in
  // pipelineWorkflowVariables. We need both.
  private void validateVariablesOfEntityTypeForNonWebhookTriggers(
      List<Variable> pipelineWorkflowVariables, Map<String, String> triggerVariables) {
    if (isNotEmpty(pipelineWorkflowVariables)) {
      for (Variable v : pipelineWorkflowVariables) {
        if (VariableType.ENTITY.equals(v.getType()) && containsVariablePattern(triggerVariables.get(v.getName()))) {
          throw new InvalidRequestException(
              "Expressions are not allowed for Entity Variables for Workflow Variables. Offending value: "
              + triggerVariables.get(v.getName()));
        }
      }
    }
  }

  @VisibleForTesting
  public WebHookToken generateWebHookToken(Trigger trigger, WebHookToken existingToken) {
    List<Service> services = null;
    boolean artifactOrManifestNeeded = true;
    Map<String, String> parameters = new LinkedHashMap<>();
    List<Variable> variables = new ArrayList<>();
    if (PIPELINE == trigger.getWorkflowType()) {
      Pipeline pipeline = pipelineService.readPipelineWithResolvedVariables(
          trigger.getAppId(), trigger.getWorkflowId(), trigger.getWorkflowVariables());
      services = pipeline.getServices();
      if (pipeline.isHasBuildWorkflow()) {
        artifactOrManifestNeeded = false;
      }
      variables = pipeline.getPipelineVariables();
      addVariables(parameters, variables);
    } else if (ORCHESTRATION == trigger.getWorkflowType()) {
      Workflow workflow = workflowService.readWorkflow(trigger.getAppId(), trigger.getWorkflowId());
      services = workflow.getServices();
      Map<String, String> workflowVariables = trigger.getWorkflowVariables();
      if (isNotEmpty(workflowVariables)) {
        if (BUILD == workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType()) {
          artifactOrManifestNeeded = false;
        } else {
          if (workflow.getOrchestrationWorkflow().isServiceTemplatized()) {
            services = workflowService.getResolvedServices(workflow, workflowVariables);
          }
        }
      }
      variables = workflow.getOrchestrationWorkflow().getUserVariables();
      addVariables(parameters, variables);
    }
    List<String> templatizedServiceInTriggers = getTemplatizedServiceVarNames(trigger, variables);
    return triggerServiceHelper.constructWebhookToken(
        trigger, existingToken, services, artifactOrManifestNeeded, parameters, templatizedServiceInTriggers);
  }

  private List<String> getTemplatizedServiceVarNames(Trigger trigger, List<Variable> variables) {
    Map<String, String> variableValuesInTrigger = trigger.getWorkflowVariables();
    List<String> templatizedServiceVars = new ArrayList<>();
    List<Variable> serviceVars =
        variables.stream().filter(t -> EntityType.SERVICE == t.obtainEntityType()).collect(toList());
    for (Variable serviceVar : serviceVars) {
      String value = variableValuesInTrigger.get(serviceVar.getName());
      if (isEmpty(value) || containsVariablePattern(value)) {
        templatizedServiceVars.add(serviceVar.getName());
      }
    }
    return templatizedServiceVars;
  }

  private void addVariables(Map<String, String> parameters, List<Variable> variables) {
    if (isNotEmpty(variables)) {
      variables.forEach(variable -> { parameters.put(variable.getName(), variable.getName() + "_placeholder"); });
    }
  }

  private void addOrUpdateCronForScheduledJob(Trigger trigger, Trigger existingTrigger) {
    if (existingTrigger.getCondition().getConditionType() == SCHEDULED) {
      if (trigger.getCondition().getConditionType() == SCHEDULED) {
        scheduledTriggerHandler.wakeup();
        TriggerKey triggerKey = new TriggerKey(trigger.getUuid(), ScheduledTriggerJob.GROUP);
        jobScheduler.rescheduleJob(triggerKey, ScheduledTriggerJob.getQuartzTrigger(trigger));
        jobScheduler.pauseJob(trigger.getUuid(), ScheduledTriggerJob.GROUP);
      } else {
        jobScheduler.deleteJob(existingTrigger.getUuid(), ScheduledTriggerJob.GROUP);
      }
    } else if (trigger.getCondition().getConditionType() == SCHEDULED) {
      scheduledTriggerHandler.wakeup();
      String accountId = appService.getAccountIdByAppId(trigger.getAppId());
      ScheduledTriggerJob.add(jobScheduler, accountId, trigger.getAppId(), trigger.getUuid(), trigger);
      jobScheduler.pauseJob(trigger.getUuid(), ScheduledTriggerJob.GROUP);
    }
  }

  private void updateNextIterations(Trigger trigger) {
    trigger.setNextIterations(new ArrayList<>());
    if (trigger.getCondition().getConditionType() == SCHEDULED) {
      trigger.recalculateNextIterations(TriggerKeys.nextIterations, true, 0);
      List<Long> nextIterations = trigger.getNextIterations();
      if (!trigger.isDisabled() && EmptyPredicate.isEmpty(trigger.getNextIterations())) {
        throw new InvalidRequestException(
            "Given cron expression doesn't evaluate to a valid time. Please check the expression provided");
      }
      nextIterations = trigger.getNextIterations();
      if (nextIterations.size() > 1 && ((nextIterations.get(1) - nextIterations.get(0)) / 1000 < MIN_INTERVAL)) {
        throw new InvalidRequestException(
            "Deployments must be triggered at intervals greater than or equal to 5 minutes. Cron Expression should evaluate to time intervals of at least "
            + MIN_INTERVAL + " seconds.");
      }
    }
  }

  private void collectArtifacts(Trigger trigger, Map<String, ArtifactSummary> serviceArtifactMapping,
      List<Artifact> artifacts, Map<String, String> services) {
    boolean collectedArtifactsFromPayload = false;
    for (ArtifactSelection artifactSelection : trigger.getArtifactSelections()) {
      if (artifactSelection.getType() == WEBHOOK_VARIABLE) {
        if (isNotEmpty(artifactSelection.getArtifactStreamId())) {
          ArtifactSummary artifactSummary = serviceArtifactMapping.get(artifactSelection.getServiceId());
          if (artifactSummary == null) {
            throw new InvalidRequestException("Artifact Service not matching with Trigger Service ["
                + services.get(artifactSelection.getServiceId()) + "]");
          }
          String buildNumber = artifactSummary.getBuildNo();
          if (isBlank(buildNumber)) {
            throw new InvalidRequestException("Build Number is Mandatory", USER);
          } else {
            Artifact artifact;
            if (artifactSummary.getArtifactParameters() == null) {
              artifact = getAlreadyCollectedOrCollectNewArtifactForBuildNumber(
                  trigger.getAppId(), artifactSelection.getArtifactStreamId(), buildNumber);

            } else {
              artifact = getAlreadyCollectedOrCollectNewArtifactForBuildNumber(trigger.getAppId(),
                  artifactSelection.getArtifactStreamId(), buildNumber, artifactSummary.getArtifactParameters());
            }
            if (artifact != null) {
              artifacts.add(artifact);
            }
          }
        } else {
          // Prepare artifact Stream BuildNumber Mapping
          if (!collectedArtifactsFromPayload) {
            List<Artifact> artifactList =
                collectArtifactsFromPayload(trigger.getAppId(), serviceArtifactMapping, services);
            collectedArtifactsFromPayload = true;
            artifacts.addAll(artifactList);
          }
        }
      }
    }

    try {
      if (!collectedArtifactsFromPayload && isNotEmpty(serviceArtifactMapping)
          && (trigger.getCondition().getConditionType() == WEBHOOK)) {
        collectArtifactsForTemplatizedService(trigger, serviceArtifactMapping, artifacts, services);
      }
    } catch (Exception e) {
      log.error("failed to process artifacts and services from webhook", e);
    }
  }

  private void collectArtifactsForTemplatizedService(Trigger trigger,
      Map<String, ArtifactSummary> serviceArtifactMapping, List<Artifact> artifacts, Map<String, String> services) {
    Map<String, ArtifactSummary> filteredServiceArtifactMapping =
        serviceArtifactMapping.entrySet()
            .stream()
            .filter(map -> {
              String serviceId = map.getKey();
              for (ArtifactSelection artifactSelection : trigger.getArtifactSelections()) {
                if (artifactSelection.getServiceId().equals(serviceId)) {
                  return false;
                }
              }
              return true;
            })
            .collect(Collectors.toMap(map -> map.getKey(), map -> map.getValue()));

    if (isNotEmpty(filteredServiceArtifactMapping)) {
      List<Artifact> artifactList =
          collectArtifactsFromPayload(trigger.getAppId(), filteredServiceArtifactMapping, services);
      artifacts.addAll(artifactList);
    }
  }

  private List<Artifact> collectArtifactsFromPayload(
      String appId, Map<String, ArtifactSummary> serviceArtifactMapping, Map<String, String> services) {
    ArtifactStream artifactStream;
    List<Artifact> artifacts = new ArrayList<>();
    for (Entry<String, ArtifactSummary> serviceArtifactSummaryEntry : serviceArtifactMapping.entrySet()) {
      ArtifactSummary artifactSummary = serviceArtifactSummaryEntry.getValue();
      if (isNotEmpty(artifactSummary.getName())) {
        artifactStream = artifactStreamService.getArtifactStreamByName(
            appId, serviceArtifactSummaryEntry.getKey(), artifactSummary.getName());
      } else {
        List<String> artifactStreamIds =
            artifactStreamService.fetchArtifactStreamIdsForService(appId, serviceArtifactSummaryEntry.getKey());
        if (isEmpty(artifactStreamIds)) {
          throw new InvalidRequestException("No artifact sources defined for the service ["
              + services.get(serviceArtifactSummaryEntry.getKey()) + "]");
        }
        if (artifactStreamIds.size() > 1) {
          throw new InvalidRequestException("More than one artifact source defined for the service ["
              + serviceArtifactSummaryEntry.getKey() + "]. Please provide artifact source name");
        }
        artifactStream = artifactStreamService.get(artifactStreamIds.get(0));
      }
      if (artifactStream == null) {
        throw new InvalidRequestException("Artifact Source [" + artifactSummary.getName()
                + "] does not exist for the Service +[" + services.get(serviceArtifactSummaryEntry.getKey()) + "]",
            USER);
      }
      if (artifactStream.isArtifactStreamParameterized()) {
        throw new InvalidRequestException(
            "Parameterized artifact stream found in service however parameter values not provided", USER);
      }
      Artifact artifact = getAlreadyCollectedOrCollectNewArtifactForBuildNumber(
          appId, artifactStream.getUuid(), artifactSummary.getBuildNo());
      if (artifact != null) {
        artifacts.add(artifact);
      }
    }

    return artifacts;
  }

  private Artifact getAlreadyCollectedOrCollectNewArtifactForBuildNumber(
      String appId, String artifactStreamId, String buildNumber) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    notNullCheck("Artifact Source doesn't exist", artifactStream, USER);
    Artifact collectedArtifactForBuildNumber =
        artifactService.getArtifactByBuildNumber(artifactStream, buildNumber, false);

    return collectedArtifactForBuildNumber != null
        ? collectedArtifactForBuildNumber
        : collectNewArtifactForBuildNumber(appId, artifactStream, buildNumber);
  }

  private HelmChart getAlreadyCollectedHelmChartOrCollectNewForVersionNumber(
      String appId, String appManifestId, String versionNumber) {
    ApplicationManifest appManifest = applicationManifestService.getById(appId, appManifestId);
    notNullCheck("Application Manifest doesn't exist", appManifest, USER);
    HelmChart helmChart =
        helmChartService.getManifestByVersionNumber(appManifest.getAccountId(), appManifestId, versionNumber);

    if (helmChart == null) {
      if (featureFlagService.isEnabled(BYPASS_HELM_FETCH, appManifest.getAccountId())) {
        return helmChartService.createHelmChartWithVersionForAppManifest(appManifest, versionNumber);
      }
      helmChart = helmChartService.fetchByChartVersion(
          appManifest.getAccountId(), appId, appManifest.getServiceId(), appManifest.getUuid(), versionNumber);
    }
    notNullCheck("Helm chart with given version number doesn't exist", helmChart, USER);
    return helmChart;
  }

  private Artifact collectNewArtifactForBuildNumber(String appId, ArtifactStream artifactStream, String buildNumber) {
    Artifact artifact = artifactCollectionServiceAsync.collectNewArtifacts(appId, artifactStream, buildNumber);
    if (artifact != null) {
      log.info("Artifact {} collected for the build number {} of stream id {}", artifact, buildNumber,
          artifactStream.getUuid());
    } else {
      log.warn(
          "Artifact collection invoked. However, Artifact not yet collected for the build number {} of stream id {}",
          buildNumber, artifactStream.getUuid());
    }
    return artifact;
  }

  private Artifact getAlreadyCollectedOrCollectNewArtifactForBuildNumber(
      String appId, String artifactStreamId, String buildNumber, Map<String, Object> artifactVariables) {
    ArtifactStream artifactStream = artifactStreamService.get(artifactStreamId);
    notNullCheck("Artifact Source doesn't exist", artifactStream, USER);
    artifactStreamHelper.resolveArtifactStreamRuntimeValues(artifactStream, artifactVariables);
    artifactStream.setSourceName(artifactStream.generateSourceName());
    Artifact collectedArtifactForBuildNumber = artifactService.getArtifactByBuildNumberAndSourceName(
        artifactStream, buildNumber, false, artifactStream.getSourceName());

    return collectedArtifactForBuildNumber != null
        ? collectedArtifactForBuildNumber
        : collectNewArtifactForBuildNumber(appId, artifactStream, buildNumber, artifactVariables);
  }

  private Artifact collectNewArtifactForBuildNumber(
      String appId, ArtifactStream artifactStream, String buildNumber, Map<String, Object> artifactVariables) {
    Artifact artifact =
        artifactCollectionServiceAsync.collectNewArtifacts(appId, artifactStream, buildNumber, artifactVariables);
    if (artifact != null) {
      log.info("Artifact {} collected for the build number {} of stream id {}", artifact, buildNumber,
          artifactStream.getUuid());
    } else {
      log.warn(
          "Artifact collection invoked. However, Artifact not yet collected for the build number {} of stream id {}",
          buildNumber, artifactStream.getUuid());
    }
    return artifact;
  }

  private void addArtifactsAndHelmChartsFromVersionsOfWebHook(Trigger trigger,
      Map<String, ArtifactSummary> serviceArtifactMapping, List<Artifact> artifacts, List<HelmChart> helmCharts,
      Map<String, ManifestSummary> serviceManifestMapping) {
    Map<String, String> services;
    if (ORCHESTRATION == trigger.getWorkflowType()) {
      services = resolveWorkflowServices(trigger);
    } else {
      Pipeline pipeline = pipelineService.readPipeline(trigger.getAppId(), trigger.getWorkflowId(), true);
      services = pipeline.getServices().stream().collect(toMap(Service::getUuid, Service::getName));
    }
    collectArtifacts(trigger, serviceArtifactMapping, artifacts, services);
    if (featureFlagService.isEnabled(FeatureName.HELM_CHART_AS_ARTIFACT, trigger.getAccountId())) {
      collectHelmCharts(trigger, serviceManifestMapping, helmCharts);
    }
  }

  private void collectHelmCharts(
      Trigger trigger, Map<String, ManifestSummary> serviceManifestMapping, List<HelmChart> helmCharts) {
    if (isEmpty(serviceManifestMapping)) {
      return;
    }
    helmCharts.addAll(collectHelmChartsForTemplatizedServices(
        trigger.getAppId(), trigger.getManifestSelections(), serviceManifestMapping));
    if (isEmpty(trigger.getManifestSelections())) {
      return;
    }

    trigger.getManifestSelections()
        .stream()
        .filter(manifestSelection -> ManifestSelectionType.WEBHOOK_VARIABLE.equals(manifestSelection.getType()))
        .forEach(manifestSelection -> {
          if (!serviceManifestMapping.containsKey(manifestSelection.getServiceId())) {
            throw new InvalidRequestException(
                "Service " + manifestSelection.getServiceId() + " requires manifests", USER);
          }
          String versionNo = serviceManifestMapping.get(manifestSelection.getServiceId()).getVersionNo();
          if (isBlank(versionNo)) {
            throw new InvalidRequestException("Version Number is Mandatory", USER);
          }
          helmCharts.add(getAlreadyCollectedHelmChartOrCollectNewForVersionNumber(
              trigger.getAppId(), manifestSelection.getAppManifestId(), versionNo));
        });
  }

  private List<HelmChart> collectHelmChartsForTemplatizedServices(
      String appId, List<ManifestSelection> manifestSelections, Map<String, ManifestSummary> serviceManifestMapping) {
    List<String> serviceIdsInManifestSelections = isEmpty(manifestSelections)
        ? new ArrayList<>()
        : manifestSelections.stream().map(ManifestSelection::getServiceId).collect(toList());
    List<String> filteredServiceIds = serviceManifestMapping.keySet()
                                          .stream()
                                          .filter(serviceId -> !serviceIdsInManifestSelections.contains(serviceId))
                                          .collect(toList());
    return filteredServiceIds.stream()
        .map(serviceId -> getHelmChartByVersionForService(appId, serviceManifestMapping, serviceId))
        .collect(toList());
  }

  @NotNull
  private HelmChart getHelmChartByVersionForService(
      String appId, Map<String, ManifestSummary> serviceManifestMapping, String serviceId) {
    String appManifestName = serviceManifestMapping.get(serviceId).getAppManifestName();
    ApplicationManifest applicationManifest;
    if (isEmpty(appManifestName)) {
      List<ApplicationManifest> applicationManifests = applicationManifestService.listAppManifests(appId, serviceId);
      if (isEmpty(applicationManifests)) {
        throw new InvalidRequestException("Application manifest not present for the service in payload: " + serviceId);
      }
      if (applicationManifests.size() > 1) {
        throw new InvalidRequestException("Application Manifest name has to be provided for service " + serviceId);
      }
      applicationManifest = applicationManifests.get(0);
    } else {
      applicationManifest = applicationManifestService.getAppManifestByName(appId, null, serviceId, appManifestName);
    }
    notNullCheck(
        "Application manifest not present for the service in payload: " + serviceId, applicationManifest, USER);
    if (!Boolean.TRUE.equals(applicationManifest.getPollForChanges())) {
      throw new InvalidRequestException("Polling not enabled for service: " + serviceId);
    }
    HelmChart helmChart = helmChartService.getManifestByVersionNumber(applicationManifest.getAccountId(),
        applicationManifest.getUuid(), serviceManifestMapping.get(serviceId).getVersionNo());
    notNullCheck("Helm chart with given version number doesn't exist: " + serviceManifestMapping.get(serviceId)
            + "for service" + serviceId,
        helmChart, USER);
    return helmChart;
  }

  private Map<String, String> resolveWorkflowServices(Trigger trigger) {
    Workflow workflow = workflowService.readWorkflow(trigger.getAppId(), trigger.getWorkflowId());
    if (workflow == null) {
      throw new WingsException("Workflow " + trigger.fetchWorkflowOrPipelineName() + " does not exist.", USER);
    }
    List<Service> workflowServices = workflow.getServices();
    if (BUILD != workflow.getOrchestrationWorkflow().getOrchestrationWorkflowType()
        && workflow.getOrchestrationWorkflow().isServiceTemplatized()) {
      workflowServices = workflowService.getResolvedServices(workflow, trigger.getWorkflowVariables());
    }
    return isEmpty(workflowServices) ? new HashMap<>()
                                     : workflowServices.stream().collect(toMap(Service::getUuid, Service::getName));
  }

  @Override
  public boolean triggerActionExists(Trigger trigger) {
    WorkflowType workflowType = trigger.getWorkflowType();
    if (PIPELINE == workflowType) {
      return pipelineService.pipelineExists(trigger.getAppId(), trigger.getWorkflowId());
    } else if (WorkflowType.ORCHESTRATION == workflowType) {
      return workflowService.workflowExists(trigger.getAppId(), trigger.getWorkflowId());
    }

    return true;
  }

  @Override
  public void authorizeAppAccess(List<String> appIds) {
    if (isEmpty(appIds)) {
      return;
    }
    String appId = appIds.stream().filter(EmptyPredicate::isNotEmpty).findAny().orElse(null);
    if (appId == null) {
      return;
    }
    String accountId = appService.getAccountIdByAppId(appId);
    triggerAuthHandler.authorizeAppAccess(appIds, accountId);
  }

  @Override
  public void authorize(Trigger trigger, boolean existing) {
    WorkflowType workflowType = trigger.getWorkflowType();
    try {
      triggerAuthHandler.authorizeWorkflowOrPipeline(trigger.getAppId(), trigger.getWorkflowId(), existing);
    } catch (WingsException ex) {
      throw new WingsException("User does not have deployment execution permission on "
              + (workflowType == PIPELINE ? "Pipeline" : "Workflow"),
          USER);
    }
    boolean envParamaterized;
    List<Variable> variables;
    if (PIPELINE == workflowType) {
      Pipeline pipeline = pipelineService.readPipeline(trigger.getAppId(), trigger.getWorkflowId(), true);
      notNullCheck("Pipeline does not exist", pipeline, USER);
      envParamaterized = pipeline.isEnvParameterized();
      variables = pipeline.getPipelineVariables();
    } else if (ORCHESTRATION == workflowType) {
      Workflow workflow = workflowService.readWorkflow(trigger.getAppId(), trigger.getWorkflowId());
      notNullCheck("Workflow does not exist", workflow, USER);
      notNullCheck("Orchestration workflow does not exist", workflow.getOrchestrationWorkflow(), USER);
      envParamaterized = workflow.checkEnvironmentTemplatized();
      variables = workflow.getOrchestrationWorkflow().getUserVariables();

    } else {
      log.error("WorkflowType {} not supported", workflowType);
      throw new WingsException("Workflow Type [" + workflowType + "] not supported", USER);
    }
    if (envParamaterized) {
      validateAndAuthorizeEnvironment(trigger, existing, variables);
    }
  }

  private void validateAndAuthorizeEnvironment(Trigger trigger, boolean existing, List<Variable> variables) {
    String templatizedEnvVariableName = WorkflowServiceTemplateHelper.getTemplatizedEnvVariableName(variables);
    if (isNotEmpty(templatizedEnvVariableName)) {
      Map<String, String> workflowVariables = trigger.getWorkflowVariables();
      if (isEmpty(workflowVariables)) {
        if (existing) {
          return;
        }
        throw new WingsException("Please select a value for entity type variables.", USER);
      }
      String envId = workflowVariables.get(templatizedEnvVariableName);
      if (isEmpty(envId)) {
        if (existing
            || variables.stream()
                   .filter(variable -> Boolean.TRUE.equals(variable.getRuntimeInput()))
                   .anyMatch(v -> v.getName().equals(templatizedEnvVariableName))) {
          return;
        }
        throw new WingsException("Environment is parameterized. Please select a value in the format ${varName}.", USER);
      }
      triggerAuthHandler.authorizeEnvironment(trigger, envId);
    }
  }
}
