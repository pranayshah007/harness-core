/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.morphia;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.MigratedEntityMapping;
import io.harness.beans.MigrationAsyncTracker;
import io.harness.beans.SweepingOutput;
import io.harness.beans.terraform.TerraformPlanParam;

import software.wings.api.ServiceElement;
import software.wings.beans.TerraGroupProvisioners;
import software.wings.beans.entityinterface.ApplicationAccess;
import software.wings.beans.entityinterface.KeywordsAware;
import software.wings.beans.infrastructure.Host;
import software.wings.metrics.TimeSeriesMetricDefinition;
import software.wings.ngmigration.NGMigrationEntity;
import software.wings.service.intfc.customdeployment.CustomDeploymentTypeAware;

import java.util.Set;

@OwnedBy(CDP)
public class CgOrchestrationBeansMorphiaRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(CustomDeploymentTypeAware.class);
    set.add(ApplicationAccess.class);
    set.add(KeywordsAware.class);
    set.add(TerraGroupProvisioners.class);
    set.add(SweepingOutput.class);
    set.add(NGMigrationEntity.class);
    set.add(MigratedEntityMapping.class);
    set.add(Host.class);
    set.add(MigrationAsyncTracker.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    w.put("metrics.TimeSeriesMetricDefinition", TimeSeriesMetricDefinition.class);
    w.put("api.TerraformPlanParam", TerraformPlanParam.class);
    w.put("api.ServiceElement", ServiceElement.class);
  }
}
