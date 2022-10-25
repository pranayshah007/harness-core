/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;

import io.harness.beans.PageRequest;
import io.harness.data.validator.EntityNameValidator;
import io.harness.exception.utils.ExceptionUtils;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Base;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.command.Command;
import software.wings.dl.WingsPersistence;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * A lot of code in this class seems repetitive.
 * We would ideally want to write templatized methods that would simply take a class as param and
 * migrate the bean / entity representing that class.
 * But we would need to call the "getName()" on the type parameter.
 * Now "getName()" is NOT defined in Base.
 * So we had 3 options
 * 1. Define "getName()" in base. But this might break something as Base is used in so many places.
 * 2. Define a new interface "Migratable", that has only one method "getName()" and then
 * all the entities we intend to migrate will have that method. But other than migration that interface
 * would be useless.
 * 3. Have repetitive code (like done here).
 * Finally we decided to go with option 3.
 */
@Slf4j
public abstract class EntityNameValidationMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  protected abstract boolean skipAccount(String accountId);

  @Builder
  @EqualsAndHashCode
  private static class SettingAttributesKey {
    String appId;
    String envId;
    String valueType;
    String name;
  }

  @Override
  public void migrate() {
    List<String> accountIdsToMigrate = getAccountIdsToMigrate();
    accountIdsToMigrate.forEach(this::migrateAccount);
  }

  private List<String> getAccountIdsToMigrate() {
    PageRequest<Account> request =
        aPageRequest().withLimit(UNLIMITED).addFieldsIncluded("uuid").addFilter("appId", EQ, GLOBAL_APP_ID).build();
    List<Account> accounts = wingsPersistence.query(Account.class, request).getResponse();
    return accounts.stream().map(Base::getUuid).collect(Collectors.toList());
  }

  private String getValidName(Set<String> namesAlreadyTaken, String currentName) {
    String proposedName = EntityNameValidator.getMappedString(currentName);
    if (!namesAlreadyTaken.contains(proposedName)) {
      namesAlreadyTaken.add(proposedName);
      return proposedName;
    } else {
      int i = 1;
      while (true) {
        String proposedNameAppended = String.format("%s_%d", proposedName, i);
        if (!namesAlreadyTaken.contains(proposedNameAppended)) {
          namesAlreadyTaken.add(proposedNameAppended);
          return proposedNameAppended;
        } else {
          i++;
        }
      }
    }
  }

  private void migrateAccount(String accountId) {
    if (skipAccount(accountId)) {
      log.info("Skipping account: " + accountId);
      return;
    }
    log.info("Migrating Account: " + accountId);
    migrateSettingAttributesOfAccount(accountId);
    PageRequest<Application> request = aPageRequest()
                                           .withLimit(UNLIMITED)
                                           .addFieldsIncluded("uuid", "name")
                                           .addFilter("accountId", EQ, accountId)
                                           .build();
    List<Application> applications = wingsPersistence.query(Application.class, request).getResponse();
    applications.sort((app1, app2) -> {
      String name1 = app1.getName();
      String mapped1 = EntityNameValidator.getMappedString(name1);
      String name2 = app2.getName();
      String mapped2 = EntityNameValidator.getMappedString(name2);
      if (!mapped1.equals(mapped2)) {
        return 0;
      }
      if (name1.equals(mapped1)) {
        return -1;
      }
      if (name2.equals(mapped2)) {
        return 1;
      }
      return 0;
    });
    Set<String> appNamesForAccount = Sets.newHashSet();
    applications.forEach(application -> {
      try {
        log.info("Migrating Application: " + application.getUuid());
        String newName = getValidName(appNamesForAccount, application.getName());
        if (!newName.equals(application.getName())) {
          wingsPersistence.updateField(Application.class, application.getUuid(), "name", newName);
        }
        migrateServicesOfApplication(application.getUuid());
        migrateEnvironmentsOfApplication(application.getUuid());
        migrateWorkflowsOfApplication(application.getUuid());
        migratePipelinesOfApplication(application.getUuid());
        migrateCommandsOfApplication(application.getUuid());
        log.info("Done with Application: " + application.getUuid());
      } catch (Exception ex) {
        log.error("Exception: " + ExceptionUtils.getMessage(ex) + " while migrating App: " + application.getUuid(), ex);
      }
    });
    log.info("Done with Account: " + accountId);
  }

  private String getValidSettingAttributeNames(
      Set<SettingAttributesKey> setAlreadyCreated, String currentName, String appId, String envId, String valueType) {
    String proposedName = EntityNameValidator.getMappedString(currentName);
    SettingAttributesKey key =
        SettingAttributesKey.builder().appId(appId).envId(envId).valueType(valueType).name(proposedName).build();
    if (!setAlreadyCreated.contains(key)) {
      setAlreadyCreated.add(key);
      return proposedName;
    } else {
      int i = 1;
      while (true) {
        String proposedNameAppended = String.format("%s_%d", proposedName, i);
        key = SettingAttributesKey.builder()
                  .appId(appId)
                  .envId(envId)
                  .valueType(valueType)
                  .name(proposedNameAppended)
                  .build();
        if (!setAlreadyCreated.contains(key)) {
          setAlreadyCreated.add(key);
          return proposedNameAppended;
        } else {
          i++;
        }
      }
    }
  }

  private void migrateSettingAttributesOfAccount(String accountId) {
    log.info("Migrating Setting Attributes of Account: " + accountId);
    PageRequest<SettingAttribute> request =
        aPageRequest().withLimit(UNLIMITED).addFilter("accountId", EQ, accountId).build();
    List<SettingAttribute> attributes = wingsPersistence.query(SettingAttribute.class, request).getResponse();
    Set<SettingAttributesKey> set = Sets.newHashSet();
    attributes.forEach(attribute -> {
      try {
        log.info("Migrating Setting Attribute: " + attribute.getUuid());
        String newName = getValidSettingAttributeNames(set, attribute.getName(), attribute.getAppId(),
            attribute.getEnvId(), attribute.getValue() != null ? attribute.getValue().getType() : null);
        if (!newName.equals(attribute.getName())) {
          wingsPersistence.updateField(SettingAttribute.class, attribute.getUuid(), "name", newName);
        }
        log.info("Done migrating Setting Attribute: " + attribute.getUuid());
      } catch (Exception ex) {
        log.error("Exception: " + ExceptionUtils.getMessage(ex)
                + " while migrating Setting Attribute of id: " + attribute.getUuid(),
            ex);
      }
    });
    log.info("Done with Setting Attributes of Account: " + accountId);
  }

  private void migrateServicesOfApplication(String appId) {
    PageRequest<Service> request =
        aPageRequest().withLimit(UNLIMITED).addFieldsIncluded("uuid", "name").addFilter("appId", EQ, appId).build();
    List<Service> services = wingsPersistence.query(Service.class, request).getResponse();
    Set<String> namesAlreadyTaken = Sets.newHashSet();
    services.sort((s1, s2) -> {
      String name1 = s1.getName();
      String mapped1 = EntityNameValidator.getMappedString(name1);
      String name2 = s2.getName();
      String mapped2 = EntityNameValidator.getMappedString(name2);
      if (!mapped1.equals(mapped2)) {
        return 0;
      }
      if (name1.equals(mapped1)) {
        return -1;
      }
      if (name2.equals(mapped2)) {
        return 1;
      }
      return 0;
    });
    services.forEach(service -> {
      try {
        log.info("Migrating Service: " + service.getUuid());
        String newName = getValidName(namesAlreadyTaken, service.getName());
        if (isNotEmpty(newName) && !newName.equals(service.getName())) {
          wingsPersistence.updateField(Service.class, service.getUuid(), "name", newName);
        }
        migrateArtifactStreamsOfService(service.getUuid(), appId);
        log.info("Done with Service: " + service.getUuid());
      } catch (Exception ex) {
        log.error("Exception: " + ExceptionUtils.getMessage(ex) + " while migrating service: " + service.getUuid(), ex);
      }
    });
  }

  private void migrateArtifactStreamsOfService(String serviceId, String appId) {
    PageRequest<ArtifactStream> request = aPageRequest()
                                              .withLimit(UNLIMITED)
                                              .addFieldsIncluded("uuid", "name")
                                              .addFilter("serviceId", EQ, serviceId)
                                              .addFilter("appId", EQ, appId)
                                              .build();
    List<ArtifactStream> artifactStreams = wingsPersistence.query(ArtifactStream.class, request).getResponse();
    Set<String> namesAlreadyTaken = Sets.newHashSet();
    artifactStreams.sort((as1, as2) -> {
      String name1 = as1.getName();
      String mapped1 = EntityNameValidator.getMappedString(name1);
      String name2 = as2.getName();
      String mapped2 = EntityNameValidator.getMappedString(name2);
      if (!mapped1.equals(mapped2)) {
        return 0;
      }
      if (name1.equals(mapped1)) {
        return -1;
      }
      if (name2.equals(mapped2)) {
        return 1;
      }
      return 0;
    });
    artifactStreams.forEach(artifactStream -> {
      try {
        log.info("Migrating Artifact Stream: " + artifactStream.getUuid());
        String newName = getValidName(namesAlreadyTaken, artifactStream.getName());
        if (isNotEmpty(newName) && !newName.equals(artifactStream.getName())) {
          wingsPersistence.updateField(ArtifactStream.class, artifactStream.getUuid(), "name", newName);
        }
        log.info("Done with Artifact Stream: " + artifactStream.getUuid());
      } catch (Exception ex) {
        log.error("Exception: " + ExceptionUtils.getMessage(ex)
                + " while migrating artifact stream: " + artifactStream.getUuid(),
            ex);
      }
    });
  }

  private void migrateEnvironmentsOfApplication(String appId) {
    PageRequest<Environment> request =
        aPageRequest().withLimit(UNLIMITED).addFieldsIncluded("uuid", "name").addFilter("appId", EQ, appId).build();
    List<Environment> environments = wingsPersistence.query(Environment.class, request).getResponse();
    Set<String> namesAlreadyTaken = Sets.newHashSet();
    environments.sort((e1, e2) -> {
      String name1 = e1.getName();
      String mapped1 = EntityNameValidator.getMappedString(name1);
      String name2 = e2.getName();
      String mapped2 = EntityNameValidator.getMappedString(name2);
      if (!mapped1.equals(mapped2)) {
        return 0;
      }
      if (name1.equals(mapped1)) {
        return -1;
      }
      if (name2.equals(mapped2)) {
        return 1;
      }
      return 0;
    });
    environments.forEach(environment -> {
      try {
        log.info("Migrating Environment: " + environment.getUuid());
        String newName = getValidName(namesAlreadyTaken, environment.getName());
        if (!newName.equals(environment.getName())) {
          wingsPersistence.updateField(Environment.class, environment.getUuid(), "name", newName);
        }
        migrateInfrastructureMappingOfEnvironment(environment.getUuid(), appId);
        log.info("Done with Environment: " + environment.getUuid());
      } catch (Exception ex) {
        log.error(
            "Exception: " + ExceptionUtils.getMessage(ex) + " while migrating environment: " + environment.getUuid(),
            ex);
      }
    });
  }

  private void migrateInfrastructureMappingOfEnvironment(String envId, String appId) {
    PageRequest<InfrastructureMapping> request = aPageRequest()
                                                     .withLimit(UNLIMITED)
                                                     .addFieldsIncluded("uuid", "name")
                                                     .addFilter("envId", EQ, envId)
                                                     .addFilter("appId", EQ, appId)
                                                     .build();
    List<InfrastructureMapping> infrastructureMappings =
        wingsPersistence.query(InfrastructureMapping.class, request).getResponse();
    Set<String> namesAlreadyTaken = Sets.newHashSet();
    infrastructureMappings.sort((iM1, iM2) -> {
      String name1 = iM1.getName();
      String mapped1 = EntityNameValidator.getMappedString(name1);
      String name2 = iM2.getName();
      String mapped2 = EntityNameValidator.getMappedString(name2);
      if (!mapped1.equals(mapped2)) {
        return 0;
      }
      if (name1.equals(mapped1)) {
        return -1;
      }
      if (name2.equals(mapped2)) {
        return 1;
      }
      return 0;
    });
    infrastructureMappings.forEach(infrastructureMapping -> {
      try {
        log.info("Migrating Infrastructure Mapping: " + infrastructureMapping.getUuid());
        String newName = getValidName(namesAlreadyTaken, infrastructureMapping.getName());
        if (isNotEmpty(newName) && !newName.equals(infrastructureMapping.getName())) {
          wingsPersistence.updateField(InfrastructureMapping.class, infrastructureMapping.getUuid(), "name", newName);
        }
        log.info("Done with Infrastructure Mapping: " + infrastructureMapping.getUuid());
      } catch (Exception ex) {
        log.error("Exception: " + ExceptionUtils.getMessage(ex)
                + " while migrating Infra mapping: " + infrastructureMapping.getUuid(),
            ex);
      }
    });
  }

  private void migrateWorkflowsOfApplication(String appId) {
    PageRequest<Workflow> request =
        aPageRequest().withLimit(UNLIMITED).addFieldsIncluded("uuid", "name").addFilter("appId", EQ, appId).build();
    List<Workflow> workflows = wingsPersistence.query(Workflow.class, request).getResponse();
    workflows.forEach(workflow -> {
      try {
        log.info("Migrating workflow: " + workflow.getUuid());
        String newName = EntityNameValidator.getMappedString(workflow.getName());
        if (!newName.equals(workflow.getName())) {
          wingsPersistence.updateField(Workflow.class, workflow.getUuid(), "name", newName);
        }
        log.info("Done with workflow: " + workflow.getUuid());
      } catch (Exception ex) {
        log.error("Exception: " + ExceptionUtils.getMessage(ex) + " while migrating workflow: " + workflow.getUuid());
      }
    });
  }

  private void migratePipelinesOfApplication(String appId) {
    PageRequest<Pipeline> request =
        aPageRequest().withLimit(UNLIMITED).addFieldsIncluded("uuid", "name").addFilter("appId", EQ, appId).build();
    List<Pipeline> pipelines = wingsPersistence.query(Pipeline.class, request).getResponse();
    pipelines.forEach(pipeline -> {
      try {
        log.info("Migrating pipeline: " + pipeline.getUuid());
        String newName = EntityNameValidator.getMappedString(pipeline.getName());
        if (!newName.equals(pipeline.getName())) {
          wingsPersistence.updateField(Pipeline.class, pipeline.getUuid(), "name", newName);
        }
        log.info("Done with pipeline: " + pipeline.getUuid());
      } catch (Exception ex) {
        log.error("Exception: " + ExceptionUtils.getMessage(ex) + " while migrating pipeline: " + pipeline.getName());
      }
    });
  }

  private void migrateCommandsOfApplication(String appId) {
    PageRequest<Command> request =
        aPageRequest().withLimit(UNLIMITED).addFieldsIncluded("uuid", "name").addFilter("appId", EQ, appId).build();
    List<Command> commands = wingsPersistence.query(Command.class, request).getResponse();
    commands.forEach(command -> {
      try {
        log.info("Migrating Command: " + command.getUuid());
        String newName = EntityNameValidator.getMappedString(command.getName());
        if (!newName.equals(command.getName())) {
          wingsPersistence.updateField(Command.class, command.getUuid(), "name", newName);
        }
        log.info("Done with Command: " + command.getUuid());
      } catch (Exception ex) {
        log.error("Exception: " + ExceptionUtils.getMessage(ex) + " while migrating command:" + command.getUuid(), ex);
      }
    });
  }
}
