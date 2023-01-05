/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.audit.ResourceType.APPLICATION;
import static software.wings.audit.ResourceType.ENVIRONMENT;
import static software.wings.audit.ResourceType.PIPELINE;
import static software.wings.audit.ResourceType.PROVISIONER;
import static software.wings.audit.ResourceType.SERVICE;
import static software.wings.audit.ResourceType.SETTING;
import static software.wings.audit.ResourceType.TEMPLATE;
import static software.wings.audit.ResourceType.TEMPLATE_FOLDER;
import static software.wings.audit.ResourceType.TRIGGER;
import static software.wings.audit.ResourceType.USER_GROUP;
import static software.wings.audit.ResourceType.WORKFLOW;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.data.structure.UUIDGenerator;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.CGConstants;
import software.wings.beans.Environment;
import software.wings.beans.Environment.EnvironmentKeys;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.InfrastructureProvisioner.InfrastructureProvisionerKeys;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.ResourceLookup;
import software.wings.beans.ResourceLookup.ResourceLookupKeys;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.SettingAttribute.SettingAttributeKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.security.UserGroup;
import software.wings.beans.security.UserGroup.UserGroupKeys;
import software.wings.beans.template.Template;
import software.wings.beans.template.Template.TemplateKeys;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.template.TemplateFolder.TemplateFolderKeys;
import software.wings.beans.trigger.Trigger;
import software.wings.beans.trigger.Trigger.TriggerKeys;
import software.wings.dl.WingsPersistence;
import software.wings.settings.SettingValue;

import com.google.inject.Inject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import dev.morphia.Morphia;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * This migration will create an entry for any auditable entity into ResourceLookup collection
 * if not already present.
 * This migration can be run again, in a case, where there are some entities not present in lookup collection.
 * This may happen when any entity is created but somehow creating resourceLookup record fails.
 */
@Slf4j
public class ResourceLookupMigration implements Migration {
  @Inject WingsPersistence wingsPersistence;
  @Inject Morphia morphia;
  private Map<String, ResourceLookup> resourceLookupMapForAllEntities = new HashMap<>();
  private Set<String> existingResourceLookupEntityIds = new HashSet<>();

  // This map will be used as lookup accountId from appId.
  // e.g. trigger doesn't store accountId, but has only appId.
  // some time back, there was issue, that services did not have accountId stamped.
  private Map<String, String> appIdToAccId = new HashMap<>();

  @Override
  public void migrate() {
    log.info("Starting ResourceLookupMigration migration for all accounts.");
    try {
      // existing ResourceLoop records
      initializeExistingResourceLookupSet();

      addExistingApplicationsToResourceMap();

      addExistingServicesToResourceMap();

      addExistingEnvironmentsToResourceMap();

      addExistingWorkflowsToResourceMap();

      addExistingPipelinesToResourceMap();

      addExistingTriggerToResourceMap();

      addExistingProvisionersToResourceMap();

      addExistingTemplatesToResourceMap();

      addExistingTemplateFoldersToResourceMap();

      // addExistingencryptedRecordsToResourceMap();

      addExistingSettingAttributesToResourceMap();

      addExistingUserGroupsToResourceMap();

      if (resourceLookupMapForAllEntities.size() > 0) {
        persistInDb();
      }

    } catch (Exception ex) {
      log.error("ResourceLookupMigration migration failed.", ex);
    }
  }

  private void persistInDb() {
    bulkSaveResourceLookupRecords();
    resourceLookupMapForAllEntities.clear();
  }

  private void addExistingSettingAttributesToResourceMap() {
    try (HIterator<SettingAttribute> settingAttributeIterator =
             new HIterator<>(wingsPersistence.createQuery(SettingAttribute.class, excludeAuthority)
                                 .project(SettingAttributeKeys.accountId, true)
                                 .project(SettingAttributeKeys.name, true)
                                 .project(SettingAttributeKeys.value, true)
                                 .fetch())) {
      while (settingAttributeIterator.hasNext()) {
        SettingAttribute settingAttribute = settingAttributeIterator.next();
        SettingValue settingValue = settingAttribute.getValue();
        if (SETTING.name().equals(settingValue.fetchResourceCategory())) {
          continue;
        }

        addResourceLookupRecord(settingAttribute.getAccountId(), CGConstants.GLOBAL_APP_ID, settingAttribute.getUuid(),
            settingAttribute.getName(), settingValue.fetchResourceCategory());
      }
    }
  }

  private void addExistingUserGroupsToResourceMap() {
    try (HIterator<UserGroup> userGroupIterator =
             new HIterator<>(wingsPersistence.createQuery(UserGroup.class, excludeAuthority)
                                 .project(UserGroupKeys.accountId, true)
                                 .project(UserGroupKeys.name, true)
                                 .project("uuid", true)
                                 .fetch())) {
      while (userGroupIterator.hasNext()) {
        UserGroup userGroup = userGroupIterator.next();
        addResourceLookupRecord(userGroup.getAccountId(), CGConstants.GLOBAL_APP_ID, userGroup.getUuid(),
            userGroup.getName(), USER_GROUP.name());
      }
    }
  }

  private void addExistingTemplateFoldersToResourceMap() {
    try (HIterator<TemplateFolder> templateFolderIterator =
             new HIterator<>(wingsPersistence.createQuery(TemplateFolder.class, excludeAuthority)
                                 .project(TemplateFolderKeys.accountId, true)
                                 .project(TemplateFolderKeys.appId, true)
                                 .project(TemplateFolderKeys.uuid, true)
                                 .project(TemplateFolderKeys.name, true)
                                 .fetch())) {
      TemplateFolder templateFolder = null;
      while (templateFolderIterator.hasNext()) {
        templateFolder = templateFolderIterator.next();
        String accountId = fetchAccountId(templateFolder.getAccountId(), templateFolder.getAppId());
        if (isNotBlank(accountId)) {
          addResourceLookupRecord(accountId, templateFolder.getAppId(), templateFolder.getUuid(),
              templateFolder.getName(), TEMPLATE_FOLDER.name());
        }
      }
    }
  }

  private void addExistingTemplatesToResourceMap() {
    try (HIterator<Template> templateIterator =
             new HIterator<>(wingsPersistence.createQuery(Template.class, excludeAuthority)
                                 .project(TemplateKeys.accountId, true)
                                 .project(TemplateKeys.appId, true)
                                 .project(TemplateKeys.uuid, true)
                                 .project(TemplateKeys.name, true)
                                 .fetch())) {
      Template template = null;
      while (templateIterator.hasNext()) {
        template = templateIterator.next();
        String accountId = fetchAccountId(template.getAccountId(), template.getAppId());
        if (isNotBlank(accountId)) {
          addResourceLookupRecord(
              accountId, template.getAppId(), template.getUuid(), template.getName(), TEMPLATE.name());
        }
      }
    }
  }

  private void addExistingProvisionersToResourceMap() {
    try (HIterator<InfrastructureProvisioner> provisionerIterator =
             new HIterator<>(wingsPersistence.createQuery(InfrastructureProvisioner.class, excludeAuthority)
                                 .project(InfrastructureProvisionerKeys.uuid, true)
                                 .project(InfrastructureProvisionerKeys.name, true)
                                 .project(InfrastructureProvisionerKeys.appId, true)
                                 .project(InfrastructureProvisionerKeys.accountId, true)
                                 .fetch())) {
      InfrastructureProvisioner infrastructureProvisioner = null;
      while (provisionerIterator.hasNext()) {
        infrastructureProvisioner = provisionerIterator.next();
        String accountId =
            fetchAccountId(infrastructureProvisioner.getAccountId(), infrastructureProvisioner.getAppId());
        if (isNotBlank(accountId)) {
          addResourceLookupRecord(accountId, infrastructureProvisioner.getAppId(), infrastructureProvisioner.getUuid(),
              infrastructureProvisioner.getName(), PROVISIONER.name());
        }
      }
    }
  }

  private void addExistingTriggerToResourceMap() {
    try (HIterator<Trigger> triggerIterator =
             new HIterator<>(wingsPersistence.createQuery(Trigger.class, excludeAuthority)
                                 .project(TriggerKeys.uuid, true)
                                 .project(TriggerKeys.name, true)
                                 .project(TriggerKeys.appId, true)
                                 .fetch())) {
      Trigger trigger = null;
      while (triggerIterator.hasNext()) {
        trigger = triggerIterator.next();
        String accountId = fetchAccountId(null, trigger.getAppId());
        if (isNotBlank(accountId)) {
          addResourceLookupRecord(accountId, trigger.getAppId(), trigger.getUuid(), trigger.getName(), TRIGGER.name());
        }
      }
    }
  }

  private void addExistingPipelinesToResourceMap() {
    try (HIterator<Pipeline> pipelineIterator =
             new HIterator<>(wingsPersistence.createQuery(Pipeline.class, excludeAuthority)
                                 .project(PipelineKeys.uuid, true)
                                 .project(PipelineKeys.name, true)
                                 .project(PipelineKeys.appId, true)
                                 .project(PipelineKeys.accountId, true)
                                 .fetch())) {
      Pipeline pipeline = null;
      while (pipelineIterator.hasNext()) {
        pipeline = pipelineIterator.next();
        String accountId = fetchAccountId(pipeline.getAccountId(), pipeline.getAppId());
        if (isNotBlank(accountId)) {
          addResourceLookupRecord(
              pipeline.getAccountId(), pipeline.getAppId(), pipeline.getUuid(), pipeline.getName(), PIPELINE.name());
        }
      }
    }
  }

  private void addExistingWorkflowsToResourceMap() {
    try (HIterator<Workflow> workflowIterator =
             new HIterator<>(wingsPersistence.createQuery(Workflow.class, excludeAuthority)
                                 .project(WorkflowKeys.uuid, true)
                                 .project(WorkflowKeys.name, true)
                                 .project(WorkflowKeys.appId, true)
                                 .project(WorkflowKeys.accountId, true)
                                 .fetch())) {
      Workflow workflow = null;
      while (workflowIterator.hasNext()) {
        workflow = workflowIterator.next();
        String accountId = fetchAccountId(workflow.getAccountId(), workflow.getAppId());
        if (isNotBlank(accountId)) {
          addResourceLookupRecord(
              workflow.getAccountId(), workflow.getAppId(), workflow.getUuid(), workflow.getName(), WORKFLOW.name());
        }
      }
    }
  }

  private void addExistingEnvironmentsToResourceMap() {
    try (HIterator<Environment> envIterator =
             new HIterator<>(wingsPersistence.createQuery(Environment.class, excludeAuthority)
                                 .project(EnvironmentKeys.uuid, true)
                                 .project(EnvironmentKeys.name, true)
                                 .project(EnvironmentKeys.appId, true)
                                 .project(EnvironmentKeys.accountId, true)
                                 .fetch())) {
      Environment environment = null;
      while (envIterator.hasNext()) {
        environment = envIterator.next();
        String accountId = fetchAccountId(environment.getAccountId(), environment.getAppId());
        if (isNotBlank(accountId)) {
          addResourceLookupRecord(environment.getAccountId(), environment.getAppId(), environment.getUuid(),
              environment.getName(), ENVIRONMENT.name());
        }
      }
    }
  }

  private void addExistingServicesToResourceMap() {
    try (HIterator<Service> serviceIterator =
             new HIterator<>(wingsPersistence.createQuery(Service.class, excludeAuthority)
                                 .project(ServiceKeys.uuid, true)
                                 .project(ServiceKeys.name, true)
                                 .project(ServiceKeys.appId, true)
                                 .project(ServiceKeys.accountId, true)
                                 .fetch())) {
      Service service = null;
      while (serviceIterator.hasNext()) {
        service = serviceIterator.next();
        String accountId = fetchAccountId(service.getAccountId(), service.getAppId());
        if (isNotBlank(accountId)) {
          addResourceLookupRecord(
              service.getAccountId(), service.getAppId(), service.getUuid(), service.getName(), SERVICE.name());
        }
      }
    }
  }

  private void addExistingApplicationsToResourceMap() {
    try (HIterator<Application> appIterator =
             new HIterator<>(wingsPersistence.createQuery(Application.class, excludeAuthority)
                                 .project(ApplicationKeys.uuid, true)
                                 .project(ApplicationKeys.name, true)
                                 .project(ApplicationKeys.accountId, true)
                                 .fetch())) {
      Application application = null;
      while (appIterator.hasNext()) {
        application = appIterator.next();
        addResourceLookupRecord(application.getAccountId(), application.getUuid(), application.getUuid(),
            application.getName(), APPLICATION.name());
        appIdToAccId.put(application.getUuid(), application.getAccountId());
      }
    }
  }

  private void initializeExistingResourceLookupSet() {
    try (HIterator<ResourceLookup> resourceLookupIterator =
             new HIterator<>(wingsPersistence.createQuery(ResourceLookup.class, excludeAuthority)
                                 .project(ResourceLookupKeys.resourceId, true)
                                 .fetch())) {
      while (resourceLookupIterator.hasNext()) {
        ResourceLookup resourceLookup = resourceLookupIterator.next();
        existingResourceLookupEntityIds.add(resourceLookup.getResourceId());
      }
    }
  }

  private String fetchAccountId(String accountId, String appId) {
    return isNotBlank(accountId) ? accountId : appIdToAccId.get(appId);
  }

  private void addResourceLookupRecord(
      String accountId, String appId, String resourceId, String resourceName, String type) {
    resourceLookupMapForAllEntities.put(resourceId,
        ResourceLookup.builder()
            .accountId(accountId)
            .appId(appId)
            .resourceId(resourceId)
            .resourceType(type)
            .resourceName(resourceName)
            .build());

    if (resourceLookupMapForAllEntities.size() == 100) {
      persistInDb();
    }
  }

  private void bulkSaveResourceLookupRecords() {
    final DBCollection collection = wingsPersistence.getCollection(ResourceLookup.class);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
    int i = 1;

    for (Map.Entry<String, ResourceLookup> lookupEntry : resourceLookupMapForAllEntities.entrySet()) {
      // Entry already exists in ResourceLookup collection
      if (existingResourceLookupEntityIds.contains(lookupEntry.getKey())) {
        continue;
      }

      if (i % 1000 == 0) {
        bulkWriteOperation.execute();
        bulkWriteOperation = collection.initializeUnorderedBulkOperation();
        log.info("ResourceLookup: {} updated", i);
      }
      ++i;

      lookupEntry.getValue().setUuid(UUIDGenerator.generateUuid());
      lookupEntry.getValue().setCreatedAt(System.currentTimeMillis());
      bulkWriteOperation.insert(morphia.toDBObject(lookupEntry.getValue()));
    }

    if (i % 1000 != 1) {
      bulkWriteOperation.execute();
      log.info("ResourceLookup: {} updated", i);
    }
  }
}
