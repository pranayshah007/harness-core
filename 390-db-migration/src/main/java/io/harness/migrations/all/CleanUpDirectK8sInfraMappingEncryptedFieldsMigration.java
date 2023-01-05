/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static java.util.Arrays.asList;

import io.harness.migrations.Migration;

import software.wings.beans.DirectKubernetesInfrastructureMapping;
import software.wings.dl.WingsPersistence;

import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import dev.morphia.query.Criteria;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanUpDirectK8sInfraMappingEncryptedFieldsMigration implements Migration {
  static final List<String> ENCRYPTED_FIELDS = asList("encryptedPassword", "encryptedCaCert", "encryptedClientCert",
      "encryptedClientKey", "encryptedClientKeyPassphrase", "encryptedServiceAccountToken");

  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Start - Clean up software.wings.beans.DirectKubernetesInfrastructureMapping encrypted fields");

    try {
      Query<DirectKubernetesInfrastructureMapping> filterQuery =
          wingsPersistence.createQuery(DirectKubernetesInfrastructureMapping.class).disableValidation();
      UpdateOperations<DirectKubernetesInfrastructureMapping> updates =
          wingsPersistence.createUpdateOperations(DirectKubernetesInfrastructureMapping.class).disableValidation();

      filterQuery.or(Iterables.toArray(
          ENCRYPTED_FIELDS.stream().map(filterQuery::criteria).map(FieldEnd::exists).collect(Collectors.toSet()),
          Criteria.class));
      ENCRYPTED_FIELDS.forEach(updates::unset);

      UpdateResults updateResults = wingsPersistence.update(filterQuery, updates);
      log.info("Cleaned up encrypted fields for {} entities", updateResults.getUpdatedCount());
    } catch (Exception e) {
      log.error("Error running migration CleanUpDirectK8sInfraMappingEncryptedFieldsMigration", e);
    }

    log.info("Completed - Clean up software.wings.beans.DirectKubernetesInfrastructureMapping encrypted fields");
  }
}
