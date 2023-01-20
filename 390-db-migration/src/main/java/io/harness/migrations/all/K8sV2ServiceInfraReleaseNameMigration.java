/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.InfrastructureMapping.INFRA_MAPPING_TYPE_KEY;
import static software.wings.beans.InfrastructureMapping.SERVICE_ID_KEY;
import static software.wings.beans.InfrastructureMappingType.AZURE_KUBERNETES;
import static software.wings.beans.InfrastructureMappingType.DIRECT_KUBERNETES;
import static software.wings.beans.InfrastructureMappingType.GCP_KUBERNETES;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.AzureKubernetesInfrastructureMapping;
import software.wings.beans.ContainerInfrastructureMapping;
import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.beans.GcpKubernetesInfrastructureMapping;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class K8sV2ServiceInfraReleaseNameMigration implements Migration {
  private static final String RELEASE_NAME_KEY = "releaseName";
  private static final String INFRA_KUBERNETES_INFRAID_EXPRESSION = "${infra.kubernetes.infraId}";

  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Running K8sV2ServiceInfraReleaseNameMigration");
    migrateServiceInfra();
    log.info("Completed K8sV2ServiceInfraReleaseNameMigration");
  }

  private void migrateServiceInfra() {
    List<Service> servicesList = new ArrayList<>();

    log.info("Fetching k8v2 services");
    try (HIterator<Service> services = new HIterator<>(
             wingsPersistence.createQuery(Service.class, excludeAuthority).filter(ServiceKeys.isK8sV2, true).fetch())) {
      while (services.hasNext()) {
        servicesList.add(services.next());
      }
    }

    List<String> allowedInfraMappingTypes = new ArrayList<>();
    allowedInfraMappingTypes.add(AZURE_KUBERNETES.name());
    allowedInfraMappingTypes.add(DIRECT_KUBERNETES.name());
    allowedInfraMappingTypes.add(GCP_KUBERNETES.name());

    log.info("Migrating service Infra for k8v2");
    for (Service service : servicesList) {
      if (!service.isK8sV2()) {
        continue;
      }

      try (HIterator<InfrastructureMapping> infrastructureMappingHIterator =
               new HIterator<>(wingsPersistence.createQuery(InfrastructureMapping.class, excludeAuthority)
                                   .filter(SERVICE_ID_KEY, service.getUuid())
                                   .field(INFRA_MAPPING_TYPE_KEY)
                                   .in(allowedInfraMappingTypes)
                                   .fetch())) {
        for (InfrastructureMapping infraMapping : infrastructureMappingHIterator) {
          if (infraMapping instanceof AzureKubernetesInfrastructureMapping
              || infraMapping instanceof DirectKubernetesInfrastructureMapping
              || infraMapping instanceof GcpKubernetesInfrastructureMapping) {
            updateServiceInfraWithReleaseName((ContainerInfrastructureMapping) infraMapping);
          }
        }
      }
    }
  }

  private void updateServiceInfraWithReleaseName(ContainerInfrastructureMapping infraMapping) {
    if (isNotBlank(infraMapping.getReleaseName())) {
      return;
    }

    UpdateOperations<InfrastructureMapping> updateOperations =
        wingsPersistence.createUpdateOperations(InfrastructureMapping.class)
            .set(RELEASE_NAME_KEY, INFRA_KUBERNETES_INFRAID_EXPRESSION);

    Query<InfrastructureMapping> query = wingsPersistence.createQuery(InfrastructureMapping.class)
                                             .filter(InfrastructureMapping.ID_KEY2, infraMapping.getUuid());

    wingsPersistence.update(query, updateOperations);
  }
}
