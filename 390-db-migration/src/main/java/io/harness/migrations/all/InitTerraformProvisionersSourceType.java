/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static software.wings.beans.InfrastructureProvisioner.INFRASTRUCTURE_PROVISIONER_TYPE_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migrations.Migration;

import software.wings.beans.TerraformInfrastructureProvisioner;
import software.wings.beans.TerraformSourceType;
import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.UpdateResults;

@OwnedBy(CDP)
@Slf4j
public class InitTerraformProvisionersSourceType implements Migration {
  @Inject protected WingsPersistence persistence;

  @Override
  public void migrate() {
    log.info("InitTerraformProvisionersSourceType migration started");
    UpdateResults updateResults = persistence.update(persistence.createQuery(TerraformInfrastructureProvisioner.class)
                                                         .filter(INFRASTRUCTURE_PROVISIONER_TYPE_KEY, "TERRAFORM")
                                                         .filter("sourceType", null),
        persistence.createUpdateOperations(TerraformInfrastructureProvisioner.class)
            .set("sourceType", TerraformSourceType.GIT));
    log.info("InitTerraformProvisionersSourceType migration finishes");
    log.info(String.format("InitTerraformProvisionersSourceType update count: %s, ", updateResults.getUpdatedCount()));
  }
}