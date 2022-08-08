/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import io.harness.data.structure.EmptyPredicate;
import io.harness.expression.ExpressionEvaluator;
import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Application.ApplicationKeys;
import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.ServiceVariableType;
import software.wings.beans.artifact.ArtifactStreamBinding;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MigrateArtifactStreamBindingsToServiceVariable implements Migration {
  // NOTE: Pre-requisite: MigrateServiceLevelArtifactStreamsToConnectorLevel
  private static final String ACCOUNT_ID = "zEaak-FLS425IEO7OLzMUg"; // TODO: change this to reflect correct account
  private static final String ARTIFACT_VARIABLE_NAME = ExpressionEvaluator.DEFAULT_ARTIFACT_VARIABLE_NAME;

  @Inject private WingsPersistence wingsPersistence;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;

  @Override
  @SuppressWarnings("deprecation")
  public void migrate() {
    log.info("Migration Started - move artifact stream bindings to service variable");
    Account account = wingsPersistence.get(Account.class, ACCOUNT_ID);
    if (account == null) {
      log.info("Specified account not found. Not migrating artifact stream bindings to service variable.");
      return;
    }

    migrateAccount(account.getUuid());
    log.info("Migration Completed - move artifact stream bindings to service variable");
  }

  private void migrateAccount(String accountId) {
    // Prefetch applications for this account.
    Set<String> appIds = wingsPersistence.createQuery(Application.class)
                             .filter(ApplicationKeys.accountId, accountId)
                             .project(ApplicationKeys.uuid, true)
                             .asList()
                             .stream()
                             .map(Application::getUuid)
                             .collect(Collectors.toSet());
    if (EmptyPredicate.isEmpty(appIds)) {
      log.info("No applications found for account " + accountId
          + ". Not migrating artifact stream bindings to service variable.");
      return;
    }

    List<ServiceVariable> serviceVariables = wingsPersistence.createQuery(ServiceVariable.class)
                                                 .field(ServiceVariableKeys.appId)
                                                 .in(appIds)
                                                 .filter(ServiceVariableKeys.entityType, EntityType.SERVICE)
                                                 .filter(ServiceVariableKeys.name, ARTIFACT_VARIABLE_NAME)
                                                 .project(ServiceVariableKeys.entityId, true)
                                                 .project(ServiceVariableKeys.type, true)
                                                 .project(ServiceVariableKeys.allowedList, true)
                                                 .asList();
    Map<String, ServiceVariable> serviceVariableMap = new HashMap<>();
    for (ServiceVariable serviceVariable : serviceVariables) {
      serviceVariableMap.put(serviceVariable.getEntityId(), serviceVariable);
    }

    try (HIterator<Service> serviceHIterator = new HIterator<>(wingsPersistence.createQuery(Service.class)
                                                                   .field(ServiceKeys.appId)
                                                                   .in(appIds)
                                                                   .project(ServiceKeys.appId, true)
                                                                   .project(ServiceKeys.artifactStreamIds, true)
                                                                   .fetch())) {
      for (Service service : serviceHIterator) {
        if (EmptyPredicate.isEmpty(service.getArtifactStreamIds())) {
          continue;
        }

        ArtifactStreamBinding artifactStreamBinding =
            ArtifactStreamBinding.builder()
                .name(ARTIFACT_VARIABLE_NAME)
                .artifactStreams(service.getArtifactStreamIds()
                                     .stream()
                                     .map(artifactStreamId
                                         -> ArtifactStreamSummary.builder().artifactStreamId(artifactStreamId).build())
                                     .collect(Collectors.toList()))
                .build();

        String serviceId = service.getUuid();
        try {
          if (serviceVariableMap.containsKey(serviceId)) {
            ServiceVariable serviceVariable = serviceVariableMap.get(serviceId);
            if (ServiceVariableType.ARTIFACT != serviceVariable.getType()) {
              // A non-artifact service variable exists with the same name. Logging and skipping.
              log.info(
                  "Service variable with name " + ARTIFACT_VARIABLE_NAME + " already exists for service: " + serviceId);
              continue;
            }

            // Artifact variable already exists for this service
            if (areListEqual(serviceVariable.getAllowedList(), service.getArtifactStreamIds())) {
              continue;
            }

            artifactStreamServiceBindingService.update(
                service.getAppId(), service.getUuid(), ARTIFACT_VARIABLE_NAME, artifactStreamBinding);
          } else {
            artifactStreamServiceBindingService.create(service.getAppId(), service.getUuid(), artifactStreamBinding);
          }
        } catch (Exception e) {
          log.error("Migration Error - could not migrate service: [{}]", serviceId, e);
        }
      }
    }
  }

  private static boolean areListEqual(List<String> list1, List<String> list2) {
    if (EmptyPredicate.isEmpty(list1) || EmptyPredicate.isEmpty(list2)) {
      return EmptyPredicate.isEmpty(list1) && EmptyPredicate.isEmpty(list2);
    }

    Set<String> set1 = new HashSet<>(list1);
    Set<String> set2 = new HashSet<>(list2);
    return set1.equals(set2);
  }
}
