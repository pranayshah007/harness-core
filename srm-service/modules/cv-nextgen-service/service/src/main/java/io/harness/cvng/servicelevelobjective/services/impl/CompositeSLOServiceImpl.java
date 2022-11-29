/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.services.impl;

import static io.harness.persistence.HQuery.excludeValidate;

import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.cvng.core.beans.sidekick.CompositeSLORecordsCleanupSideKickData;
import io.harness.cvng.core.services.api.SideKickService;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsRefDTO;
import io.harness.cvng.servicelevelobjective.entities.AbstractServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective;
import io.harness.cvng.servicelevelobjective.entities.CompositeServiceLevelObjective.CompositeServiceLevelObjectiveKeys;
import io.harness.cvng.servicelevelobjective.services.api.CompositeSLOService;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.UpdateOperations;

@Slf4j
public class CompositeSLOServiceImpl implements CompositeSLOService {
  @Inject HPersistence hPersistence;

  @Inject SideKickService sideKickService;

  @Inject Clock clock;

  @Override
  public boolean isReferencedInCompositeSLO(ProjectParams projectParams, String simpleServiceLevelObjectiveIdentifier) {
    return !getReferencedCompositeSLOs(projectParams, simpleServiceLevelObjectiveIdentifier).isEmpty();
  }

  @Override
  public List<CompositeServiceLevelObjective> getReferencedCompositeSLOs(
      ProjectParams projectParams, String simpleServiceLevelObjectiveIdentifier) {
    return hPersistence.createQuery(AbstractServiceLevelObjective.class, excludeValidate)
        .filter(CompositeServiceLevelObjectiveKeys.serviceLevelObjectivesDetails + "."
                + CompositeServiceLevelObjective.ServiceLevelObjectivesDetail.CompositeServiceLevelObjectiveDetailsKeys
                      .accountId,
            projectParams.getAccountIdentifier())
        .filter(CompositeServiceLevelObjectiveKeys.serviceLevelObjectivesDetails + "."
                + CompositeServiceLevelObjective.ServiceLevelObjectivesDetail.CompositeServiceLevelObjectiveDetailsKeys
                      .projectIdentifier,
            projectParams.getProjectIdentifier())
        .filter(CompositeServiceLevelObjectiveKeys.serviceLevelObjectivesDetails + "."
                + CompositeServiceLevelObjective.ServiceLevelObjectivesDetail.CompositeServiceLevelObjectiveDetailsKeys
                      .orgIdentifier,
            projectParams.getOrgIdentifier())
        .filter(CompositeServiceLevelObjectiveKeys.serviceLevelObjectivesDetails + "."
                + CompositeServiceLevelObjective.ServiceLevelObjectivesDetail.CompositeServiceLevelObjectiveDetailsKeys
                      .serviceLevelObjectiveRef,
            simpleServiceLevelObjectiveIdentifier)
        .asList()
        .stream()
        .map(CompositeServiceLevelObjective.class ::cast)
        .collect(Collectors.toList());
  }

  @Override
  public boolean shouldReset(
      AbstractServiceLevelObjective oldServiceLevelObjective, AbstractServiceLevelObjective newServiceLevelObjective) {
    List<ServiceLevelObjectiveDetailsRefDTO> addedServiceLevelObjectiveDetails = new ArrayList<>();
    List<ServiceLevelObjectiveDetailsRefDTO> deletedServiceLevelObjectiveDetails = new ArrayList<>();
    List<ServiceLevelObjectiveDetailsRefDTO> updatedServiceLevelObjectiveDetails = new ArrayList<>();
    getAddedDeletedAndUpdatedServiceLevelObjectiveDetailsList(oldServiceLevelObjective, newServiceLevelObjective,
        addedServiceLevelObjectiveDetails, deletedServiceLevelObjectiveDetails, updatedServiceLevelObjectiveDetails);
    return !addedServiceLevelObjectiveDetails.isEmpty();
  }

  @Override
  public boolean shouldRecalculate(
      AbstractServiceLevelObjective oldServiceLevelObjective, AbstractServiceLevelObjective newServiceLevelObjective) {
    List<ServiceLevelObjectiveDetailsRefDTO> addedServiceLevelObjectiveDetails = new ArrayList<>();
    List<ServiceLevelObjectiveDetailsRefDTO> deletedServiceLevelObjectiveDetails = new ArrayList<>();
    List<ServiceLevelObjectiveDetailsRefDTO> updatedServiceLevelObjectiveDetails = new ArrayList<>();
    getAddedDeletedAndUpdatedServiceLevelObjectiveDetailsList(oldServiceLevelObjective, newServiceLevelObjective,
        addedServiceLevelObjectiveDetails, deletedServiceLevelObjectiveDetails, updatedServiceLevelObjectiveDetails);
    return !deletedServiceLevelObjectiveDetails.isEmpty() || !updatedServiceLevelObjectiveDetails.isEmpty();
  }

  private void getAddedDeletedAndUpdatedServiceLevelObjectiveDetailsList(
      AbstractServiceLevelObjective oldServiceLevelObjective, AbstractServiceLevelObjective newServiceLevelObjective,
      List<ServiceLevelObjectiveDetailsRefDTO> addedServiceLevelObjectiveDetails,
      List<ServiceLevelObjectiveDetailsRefDTO> deletedServiceLevelObjectiveDetails,
      List<ServiceLevelObjectiveDetailsRefDTO> updatedServiceLevelObjectiveDetails) {
    CompositeServiceLevelObjective oldCompositeServiceLevelObjective =
        (CompositeServiceLevelObjective) oldServiceLevelObjective;
    CompositeServiceLevelObjective newCompositeServiceLevelObjective =
        (CompositeServiceLevelObjective) newServiceLevelObjective;
    List<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail> newServiceLevelObjectivesDetails =
        newCompositeServiceLevelObjective.getServiceLevelObjectivesDetails();
    List<CompositeServiceLevelObjective.ServiceLevelObjectivesDetail> oldServiceLevelObjectivesDetails =
        oldCompositeServiceLevelObjective.getServiceLevelObjectivesDetails();
    Map<ServiceLevelObjectiveDetailsRefDTO, Double> newServiceLevelObjectiveDetailsRefDTOtoWeightageMap =
        newServiceLevelObjectivesDetails.stream().collect(Collectors.toMap(
            CompositeServiceLevelObjective.ServiceLevelObjectivesDetail::getServiceLevelObjectiveDetailsRefDTO,
            CompositeServiceLevelObjective.ServiceLevelObjectivesDetail::getWeightagePercentage));
    Map<ServiceLevelObjectiveDetailsRefDTO, Double> oldServiceLevelObjectiveDetailsRefDTOtoWeightageMap =
        oldServiceLevelObjectivesDetails.stream().collect(Collectors.toMap(
            CompositeServiceLevelObjective.ServiceLevelObjectivesDetail::getServiceLevelObjectiveDetailsRefDTO,
            CompositeServiceLevelObjective.ServiceLevelObjectivesDetail::getWeightagePercentage));
    for (ServiceLevelObjectiveDetailsRefDTO serviceLevelObjectiveDetailsRefDTO :
        newServiceLevelObjectiveDetailsRefDTOtoWeightageMap.keySet()) {
      if (oldServiceLevelObjectiveDetailsRefDTOtoWeightageMap.containsKey(serviceLevelObjectiveDetailsRefDTO)) {
        if (!Objects.equals(oldServiceLevelObjectiveDetailsRefDTOtoWeightageMap.get(serviceLevelObjectiveDetailsRefDTO),
                newServiceLevelObjectiveDetailsRefDTOtoWeightageMap.get(serviceLevelObjectiveDetailsRefDTO))) {
          updatedServiceLevelObjectiveDetails.add(serviceLevelObjectiveDetailsRefDTO);
        }
      } else {
        addedServiceLevelObjectiveDetails.add(serviceLevelObjectiveDetailsRefDTO);
      }
    }
    for (ServiceLevelObjectiveDetailsRefDTO serviceLevelObjectiveDetailsRefDTO :
        oldServiceLevelObjectiveDetailsRefDTOtoWeightageMap.keySet()) {
      if (!newServiceLevelObjectiveDetailsRefDTOtoWeightageMap.containsKey(serviceLevelObjectiveDetailsRefDTO)) {
        deletedServiceLevelObjectiveDetails.add(serviceLevelObjectiveDetailsRefDTO);
      }
    }
  }
  @Override
  public void reset(CompositeServiceLevelObjective compositeServiceLevelObjective) {
    log.info("Reseting composite SLO with identifier {}:", compositeServiceLevelObjective.getIdentifier());
    UpdateOperations<CompositeServiceLevelObjective> updateOperations =
        hPersistence.createUpdateOperations(CompositeServiceLevelObjective.class)
            .inc(CompositeServiceLevelObjectiveKeys.version)
            .set(AbstractServiceLevelObjective.ServiceLevelObjectiveV2Keys.startedAt, System.currentTimeMillis());
    hPersistence.update(compositeServiceLevelObjective, updateOperations);
    String sloId = compositeServiceLevelObjective.getUuid();
    int sloVersion = compositeServiceLevelObjective.getVersion();
    long startTime = TimeUnit.MILLISECONDS.toMinutes(compositeServiceLevelObjective
                                                         .getCurrentTimeRange(LocalDateTime.ofInstant(clock.instant(),
                                                             compositeServiceLevelObjective.getZoneOffset()))
                                                         .getStartTime()
                                                         .toInstant(ZoneOffset.UTC)
                                                         .toEpochMilli());
    sideKickService.schedule(CompositeSLORecordsCleanupSideKickData.builder()
                                 .sloVersion(sloVersion)
                                 .sloId(sloId)
                                 .afterStartTime(startTime)
                                 .build(),
        clock.instant());
  }

  @Override
  public void recalculate(CompositeServiceLevelObjective compositeServiceLevelObjective) {
    log.info("Recalculating composite SLO with identifier {}:", compositeServiceLevelObjective.getIdentifier());
    UpdateOperations<CompositeServiceLevelObjective> updateOperations =
        hPersistence.createUpdateOperations(CompositeServiceLevelObjective.class)
            .inc(CompositeServiceLevelObjectiveKeys.version);
    hPersistence.update(compositeServiceLevelObjective, updateOperations);
  }
}
