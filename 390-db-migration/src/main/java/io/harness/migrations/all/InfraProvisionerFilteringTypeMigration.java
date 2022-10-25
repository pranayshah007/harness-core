/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageRequest.UNLIMITED;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.beans.InfrastructureMappingBlueprint.NodeFilteringType.AWS_INSTANCE_FILTER;

import io.harness.beans.PageRequest;
import io.harness.exception.utils.ExceptionUtils;
import io.harness.migrations.Migration;

import software.wings.beans.InfrastructureMappingBlueprint;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InfraProvisionerFilteringTypeMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    // We could implement a more intelligent logic like batching etc.
    // But it is probably not needed right now, as we have very few provisioners created
    log.info("Getting all provisioners to migrate blue prints");
    PageRequest<InfrastructureProvisioner> request = aPageRequest().withLimit(UNLIMITED).build();
    List<InfrastructureProvisioner> provisioners =
        wingsPersistence.query(InfrastructureProvisioner.class, request).getResponse();
    if (isNotEmpty(provisioners)) {
      provisioners.forEach(provisioner -> {
        try {
          List<InfrastructureMappingBlueprint> blueprints = provisioner.getMappingBlueprints();
          if (isNotEmpty(blueprints)) {
            blueprints.forEach(blueprint -> { blueprint.setNodeFilteringType(AWS_INSTANCE_FILTER); });
            wingsPersistence.saveAndGet(InfrastructureProvisioner.class, provisioner);
          }
        } catch (Exception ex) {
          log.error(ExceptionUtils.getMessage(ex), ex);
        }
      });
    }
    log.info("Completed update of blue prints");
  }
}
