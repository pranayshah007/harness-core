/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.prune;

import static io.harness.exception.WingsException.ExecutionContext.MANAGER;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.prune.PruneEvent.MAX_RETRIES;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.CauseCollection;
import io.harness.exception.ExceptionLogger;
import io.harness.exception.WingsException;
import io.harness.globalcontex.PurgeGlobalContextData;
import io.harness.manage.GlobalContextManager;
import io.harness.queue.QueueConsumer;
import io.harness.queue.QueueListener;

import software.wings.beans.Activity;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.infrastructure.Host;
import software.wings.beans.template.Template;
import software.wings.dl.WingsPersistence;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.ActivityService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.HarnessTagService;
import software.wings.service.intfc.HostService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;
import software.wings.service.intfc.InfrastructureProvisionerService;
import software.wings.service.intfc.PipelineService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.WorkflowService;
import software.wings.service.intfc.template.TemplateService;

import com.google.inject.Inject;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(HarnessTeam.CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PruneEntityListener extends QueueListener<PruneEvent> {
  @Inject private WingsPersistence wingsPersistence;

  @Inject private ActivityService activityService;
  @Inject private AppService appService;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private EnvironmentService environmentService;
  @Inject private HostService hostService;
  @Inject private InfrastructureMappingService infrastructureMappingService;
  @Inject private InfrastructureProvisionerService infrastructureProvisionerService;
  @Inject private PipelineService pipelineService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private WorkflowService workflowService;
  @Inject private ExecutorService executorService;
  @Inject private HarnessTagService harnessTagService;
  @Inject private InfrastructureDefinitionService infrastructureDefinitionService;
  @Inject private SettingsService settingsService;
  @Inject private ApplicationManifestService applicationManifestService;
  @Inject private TemplateService templateService;

  @Inject
  public PruneEntityListener(QueueConsumer<PruneEvent> queueConsumer) {
    super(queueConsumer, true);
  }

  public static <T> void pruneDescendingEntities(Iterable<T> descendingServices, Consumer<T> lambda) {
    CauseCollection causeCollection = new CauseCollection();
    boolean succeeded = true;
    for (T descending : descendingServices) {
      try {
        log.info("Pruning descending entities for {} ", descending.getClass());
        lambda.accept(descending);
      } catch (WingsException exception) {
        succeeded = false;
        ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
      } catch (RuntimeException e) {
        succeeded = false;
        causeCollection.addCause(e);
      }
    }
    if (!succeeded) {
      throw new WingsException(causeCollection.getCause());
    }
  }

  private boolean prune(Class clz, String appId, String entityId, boolean syncFromGit) {
    log.info("Pruning Entity {} {} for appId {}", clz.getCanonicalName(), entityId, appId);
    if (clz.equals(Application.class)) {
      if (!appId.equals(entityId)) {
        log.warn("Prune job is incorrectly initialized with entityId: " + entityId + " and appId: " + appId
            + " being different for the application class");
        return true;
      }
    }

    boolean pruneTagLinks = false;

    try {
      if (clz.equals(Activity.class)) {
        activityService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(Application.class)) {
        pruneTagLinks = true;
        appService.pruneDescendingEntities(appId);
      } else if (clz.equals(ArtifactStream.class)) {
        artifactStreamService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(Environment.class)) {
        pruneTagLinks = true;
        environmentService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(Host.class)) {
        hostService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(InfrastructureMapping.class)) {
        infrastructureMappingService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(InfrastructureDefinition.class)) {
        infrastructureDefinitionService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(Pipeline.class)) {
        pruneTagLinks = true;
        pipelineService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(Service.class)) {
        pruneTagLinks = true;
        serviceResourceService.pruneDescendingEntities(appId, entityId, syncFromGit);
      } else if (clz.equals(Workflow.class)) {
        pruneTagLinks = true;
        workflowService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(InfrastructureProvisioner.class)) {
        pruneTagLinks = true;
        infrastructureProvisionerService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(SettingAttribute.class)) {
        pruneTagLinks = true;
        settingsService.pruneBySettingAttribute(appId, entityId);
      } else if (clz.equals(ApplicationManifest.class)) {
        applicationManifestService.pruneDescendingEntities(appId, entityId);
      } else if (clz.equals(Template.class)) {
        templateService.pruneDescendingEntities(appId, entityId);
      } else {
        log.error("Unsupported class [{}] was scheduled for pruning.", clz.getCanonicalName());
      }

      if (pruneTagLinks) {
        if (!appId.equals(GLOBAL_APP_ID)) {
          harnessTagService.pruneTagLinks(appService.getAccountIdByAppId(appId), entityId);
        } // TODO: ASR check how to handle the feature flag enabled case
      }
    } catch (WingsException exception) {
      ExceptionLogger.logProcessedMessages(exception, MANAGER, log);
      return false;
    } catch (RuntimeException e) {
      log.error("", e);
      return false;
    }
    return true;
  }

  @Override
  public void onMessage(PruneEvent message) {
    try {
      Class clz = Class.forName(message.getEntityClass());

      if (wingsPersistence.get(clz, message.getEntityId()) != null) {
        // if it is the first try, give it at least one more chance
        if (message.getRetries() == MAX_RETRIES) {
          throw new WingsException("The object still exist, lets try later");
        }
        log.warn("This warning should be happening very rarely. If you see this often, please investigate.\n"
            + "The only case this warning should show is if there was a crash or network disconnect in the race of "
            + "the prune job schedule and the parent entity deletion.");

      } else {
        GlobalContextManager.upsertGlobalContextRecord(PurgeGlobalContextData.builder().build());
        if (!prune(clz, message.getAppId(), message.getEntityId(), message.isSyncFromGit())) {
          throw new WingsException("The prune failed this time");
        }
      }
    } catch (ClassNotFoundException ignore) {
      // ignore events for objects that no longer exists
    }
  }

  @Override
  protected void requeue(PruneEvent message) {
    getQueueConsumer().requeue(
        message.getId(), message.getRetries() - 1, Date.from(OffsetDateTime.now().plusHours(1).toInstant()));
  }
}
