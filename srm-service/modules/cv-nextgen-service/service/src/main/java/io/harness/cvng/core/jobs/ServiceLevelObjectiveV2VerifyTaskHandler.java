/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.jobs;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.ServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.SimpleServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.services.api.ServiceLevelObjectiveService;
import io.harness.mongo.iterator.MongoPersistenceIterator;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collections;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ServiceLevelObjectiveV2VerifyTaskHandler
    implements MongoPersistenceIterator.Handler<AbstractServiceLevelObjective> {
  @Inject ServiceLevelObjectiveService serviceLevelObjectiveService;

  @Override
  public void handle(AbstractServiceLevelObjective serviceLevelObjectiveV2) {
    log.info("Verifying service-level-objective-v2 for identifier " + serviceLevelObjectiveV2.getIdentifier());
    ProjectParams projectParams = ProjectParams.builder()
                                      .projectIdentifier(serviceLevelObjectiveV2.getProjectIdentifier())
                                      .orgIdentifier(serviceLevelObjectiveV2.getOrgIdentifier())
                                      .accountIdentifier(serviceLevelObjectiveV2.getAccountId())
                                      .build();
    ServiceLevelObjective serviceLevelObjective =
        serviceLevelObjectiveService.getEntity(projectParams, serviceLevelObjectiveV2.getIdentifier());
    if (Objects.isNull(serviceLevelObjective)) {
      log.error("[SLO Data Mismatch]: SLO with identifier " + serviceLevelObjectiveV2.getIdentifier() + " not found");
    }
    if (!isServiceLevelObjectiveV2EqualsServiceLevelObjective(serviceLevelObjectiveV2, serviceLevelObjective)) {
      log.error("[SLO Data Mismatch]: SLO " + serviceLevelObjective + "is different from it's v2 object"
          + serviceLevelObjectiveV2);
    }
  }

  private boolean isServiceLevelObjectiveV2EqualsServiceLevelObjective(
      AbstractServiceLevelObjective serviceLevelObjectiveV2, ServiceLevelObjective serviceLevelObjective) {
    SimpleServiceLevelObjective simpleServiceLevelObjective = (SimpleServiceLevelObjective) serviceLevelObjectiveV2;
    if (simpleServiceLevelObjective.isEnabled() != serviceLevelObjective.isEnabled()
        || simpleServiceLevelObjective.getTags()
            != (serviceLevelObjective.getTags() == null ? Collections.emptyList() : serviceLevelObjective.getTags())
        || simpleServiceLevelObjective.getNotificationRuleRefs() != serviceLevelObjective.getNotificationRuleRefs()
        || simpleServiceLevelObjective.getServiceLevelIndicatorType() != serviceLevelObjective.getType()
        || !simpleServiceLevelObjective.getSloTarget().equals(serviceLevelObjective.getSloTarget())
        || !simpleServiceLevelObjective.getUserJourneyIdentifiers().stream().findFirst().get().equals(
            serviceLevelObjective.getUserJourneyIdentifier())
        || !Objects.equals(simpleServiceLevelObjective.getName(), serviceLevelObjective.getName())
        || !Objects.equals(simpleServiceLevelObjective.getDesc(), serviceLevelObjective.getDesc())
        || !Objects.equals(
            simpleServiceLevelObjective.getSloTargetPercentage(), serviceLevelObjective.getSloTargetPercentage())
        || !Objects.equals(
            simpleServiceLevelObjective.getHealthSourceIdentifier(), serviceLevelObjective.getHealthSourceIdentifier())
        || !Objects.equals(
            simpleServiceLevelObjective.getServiceLevelIndicators(), serviceLevelObjective.getServiceLevelIndicators())
        || !Objects.equals(simpleServiceLevelObjective.getMonitoredServiceIdentifier(),
            serviceLevelObjective.getMonitoredServiceIdentifier())) {
      return false;
    }
    return true;
  }
}
