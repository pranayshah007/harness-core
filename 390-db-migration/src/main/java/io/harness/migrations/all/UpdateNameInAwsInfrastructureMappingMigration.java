/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.Application.ApplicationKeys;
import static software.wings.beans.InfrastructureMapping.InfrastructureMappingKeys;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Application;
import software.wings.beans.AwsInfrastructureMapping;
import software.wings.beans.AwsInfrastructureMapping.AwsInfrastructureMappingKeys;
import software.wings.beans.AwsInstanceFilter;
import software.wings.beans.InfrastructureMapping;
import software.wings.dl.WingsPersistence;
import software.wings.infra.AwsInstanceInfrastructure;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.impl.infrastructuredefinition.InfrastructureDefinitionHelper;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateNameInAwsInfrastructureMappingMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private InfrastructureDefinitionHelper infrastructureDefinitionHelper;

  private static final String DEBUG_LINE = "AWS_INFRA_STRUCTURE_NAME_MIGRATION:";

  @Override
  public void migrate() {
    log.info("{} Starting migration", DEBUG_LINE);

    List<String> accountIds = fetchAccountIds();

    for (String accountId : accountIds) {
      log.info("{} AccountId: {}", DEBUG_LINE, accountId);
      try (HIterator<Application> applications = new HIterator<>(
               wingsPersistence.createQuery(Application.class).filter(ApplicationKeys.accountId, accountId).fetch())) {
        while (applications.hasNext()) {
          Application application = applications.next();
          log.info("{} AppId: {}", DEBUG_LINE, application.getUuid());
          try (HIterator<InfrastructureMapping> infraMappings =
                   new HIterator<>(wingsPersistence.createQuery(InfrastructureMapping.class)
                                       .disableValidation()
                                       .filter(InfrastructureMappingKeys.appId, application.getUuid())
                                       .filter("className", "software.wings.beans.AwsInfrastructureMapping")
                                       .fetch())) {
            while (infraMappings.hasNext()) {
              InfrastructureMapping infrastructureMapping = infraMappings.next();
              if (!(infrastructureMapping instanceof AwsInfrastructureMapping)) {
                continue;
              }
              updateInfraMapping((AwsInfrastructureMapping) infrastructureMapping);
            }
          }
        }
      }
    }

    log.info("{} Ended migration", DEBUG_LINE);
  }

  private List<String> fetchAccountIds() {
    List<InfrastructureMapping> infrastructureMappings =
        wingsPersistence.createQuery(InfrastructureMapping.class)
            .disableValidation()
            .filter("className", "software.wings.beans.AwsInfrastructureMapping")
            .project("accountId", true)
            .asList();
    return infrastructureMappings.stream()
        .map(InfrastructureMapping::getAccountId)
        .distinct()
        .collect(Collectors.toList());
  }

  private void updateInfraMapping(@Nonnull AwsInfrastructureMapping infrastructureMapping) {
    try {
      AwsInstanceInfrastructure infrastructure =
          AwsInstanceInfrastructure.builder()
              .region(infrastructureMapping.getRegion())
              .awsInstanceFilter(infrastructureMapping.getAwsInstanceFilter())
              .autoScalingGroupName(infrastructureMapping.getAutoScalingGroupName())
              .provisionInstances(infrastructureMapping.isProvisionInstances())
              .build();

      String newName = infrastructureDefinitionHelper.getNameFromInfraDefinition(
          InfrastructureDefinition.builder()
              .appId(infrastructureMapping.getAppId())
              .envId(infrastructureMapping.getEnvId())
              .uuid(infrastructureMapping.getInfrastructureDefinitionId())
              .infrastructure(infrastructure)
              .build(),
          infrastructureMapping.getServiceId());
      String currentName = infrastructureMapping.getName();

      Map<String, Object> updatedFields = new HashMap<>();

      if (!newName.equals(currentName)) {
        updatedFields.put(InfrastructureMappingKeys.name, newName);
        updatedFields.put(InfrastructureMappingKeys.nameBk, currentName);
      }

      // The issue is on first save(), if empty awsInstanceFilter object is there, we are not adding it in database. On
      // subsequent updates, we are able to add!
      if (infrastructureMapping.getAwsInstanceFilter() == null && !infrastructureMapping.isProvisionInstances()) {
        updatedFields.put(AwsInfrastructureMappingKeys.awsInstanceFilter, AwsInstanceFilter.builder().build());
      }

      if (isNotEmpty(updatedFields)) {
        wingsPersistence.updateFields(AwsInfrastructureMapping.class, infrastructureMapping.getUuid(), updatedFields);
        log.info("{} name updated: {} from: {} to: {}", DEBUG_LINE, infrastructureMapping.getUuid(),
            infrastructureMapping.getName(), newName);
      }

    } catch (Exception ex) {
      log.warn("{} Failed to update name for {}", DEBUG_LINE, infrastructureMapping.getUuid(), ex);
    }
  }
}
