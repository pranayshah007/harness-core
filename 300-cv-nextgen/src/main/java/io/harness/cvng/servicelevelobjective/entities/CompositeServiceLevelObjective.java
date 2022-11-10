/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsRefDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("Composite")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "CompositeServiceLevelObjectiveKeys")
@EqualsAndHashCode(callSuper = true)
public class CompositeServiceLevelObjective extends AbstractServiceLevelObjective {
  public CompositeServiceLevelObjective() {
    super.setType(ServiceLevelObjectiveType.COMPOSITE);
  }
  private int version;

  @Size(min = 2, max = 20) List<ServiceLevelObjectivesDetail> serviceLevelObjectivesDetails;

  @Data
  @Builder
  public static class ServiceLevelObjectivesDetail {
    String accountId;
    String orgIdentifier;
    String projectIdentifier;
    String serviceLevelObjectiveRef;
    Double weightagePercentage;
  }

  @Override
  public Optional<String> mayBeGetMonitoredServiceIdentifier() {
    return Optional.empty();
  }

  public static class CompositeServiceLevelObjectiveUpdatableEntity
      extends AbstractServiceLevelObjectiveUpdatableEntity<CompositeServiceLevelObjective> {
    @Override
    public void setUpdateOperations(UpdateOperations<CompositeServiceLevelObjective> updateOperations,
        CompositeServiceLevelObjective compositeServiceLevelObjective) {
      setCommonOperations(updateOperations, compositeServiceLevelObjective);
      updateOperations.set(CompositeServiceLevelObjectiveKeys.serviceLevelObjectivesDetails,
          compositeServiceLevelObjective.getServiceLevelObjectivesDetails());
      updateOperations.inc(CompositeServiceLevelObjectiveKeys.version);
    }
  }

  public boolean shouldReset(AbstractServiceLevelObjective serviceLevelObjective) {
    List<ServiceLevelObjectiveDetailsRefDTO> addedServiceLevelObjectiveDetails = new ArrayList<>();
    List<ServiceLevelObjectiveDetailsRefDTO> deletedServiceLevelObjectiveDetails = new ArrayList<>();
    List<ServiceLevelObjectiveDetailsRefDTO> updatedServiceLevelObjectiveDetails = new ArrayList<>();
    getAddedDeletedAndUpdatedServiceLevelObjectiveDetailsList(serviceLevelObjective, addedServiceLevelObjectiveDetails,
        deletedServiceLevelObjectiveDetails, updatedServiceLevelObjectiveDetails);
    return !addedServiceLevelObjectiveDetails.isEmpty();
  }

  public boolean shouldRecalculate(AbstractServiceLevelObjective serviceLevelObjective) {
    List<ServiceLevelObjectiveDetailsRefDTO> addedServiceLevelObjectiveDetails = new ArrayList<>();
    List<ServiceLevelObjectiveDetailsRefDTO> deletedServiceLevelObjectiveDetails = new ArrayList<>();
    List<ServiceLevelObjectiveDetailsRefDTO> updatedServiceLevelObjectiveDetails = new ArrayList<>();
    getAddedDeletedAndUpdatedServiceLevelObjectiveDetailsList(serviceLevelObjective, addedServiceLevelObjectiveDetails,
        deletedServiceLevelObjectiveDetails, updatedServiceLevelObjectiveDetails);
    return !deletedServiceLevelObjectiveDetails.isEmpty() || !updatedServiceLevelObjectiveDetails.isEmpty();
  }

  private void getAddedDeletedAndUpdatedServiceLevelObjectiveDetailsList(
      AbstractServiceLevelObjective serviceLevelObjective,
      List<ServiceLevelObjectiveDetailsRefDTO> addedServiceLevelObjectiveDetails,
      List<ServiceLevelObjectiveDetailsRefDTO> deletedServiceLevelObjectiveDetails,
      List<ServiceLevelObjectiveDetailsRefDTO> updatedServiceLevelObjectiveDetails) {
    CompositeServiceLevelObjective compositeServiceLevelObjective =
        (CompositeServiceLevelObjective) serviceLevelObjective;
    List<ServiceLevelObjectivesDetail> newServiceLevelObjectivesDetails =
        compositeServiceLevelObjective.getServiceLevelObjectivesDetails();
    List<ServiceLevelObjectivesDetail> oldServiceLevelObjectivesDetails = this.getServiceLevelObjectivesDetails();
    Map<ServiceLevelObjectiveDetailsRefDTO, Double> newServiceLevelObjectiveDetailsRefDTOtoWeightageMap =
        newServiceLevelObjectivesDetails.stream().collect(Collectors.toMap(serviceLevelObjectiveDetailsDTO
            -> ServiceLevelObjectiveDetailsRefDTO.builder()
                   .accountId(serviceLevelObjectiveDetailsDTO.getAccountId())
                   .orgIdentifier(serviceLevelObjectiveDetailsDTO.getOrgIdentifier())
                   .projectIdentifier(serviceLevelObjectiveDetailsDTO.getProjectIdentifier())
                   .serviceLevelObjectiveRef(serviceLevelObjectiveDetailsDTO.getServiceLevelObjectiveRef())
                   .build(),
            ServiceLevelObjectivesDetail::getWeightagePercentage));
    Map<ServiceLevelObjectiveDetailsRefDTO, Double> oldServiceLevelObjectiveDetailsRefDTOtoWeightageMap =
        oldServiceLevelObjectivesDetails.stream().collect(Collectors.toMap(serviceLevelObjectiveDetailsDTO
            -> ServiceLevelObjectiveDetailsRefDTO.builder()
                   .accountId(serviceLevelObjectiveDetailsDTO.getAccountId())
                   .orgIdentifier(serviceLevelObjectiveDetailsDTO.getOrgIdentifier())
                   .projectIdentifier(serviceLevelObjectiveDetailsDTO.getProjectIdentifier())
                   .serviceLevelObjectiveRef(serviceLevelObjectiveDetailsDTO.getServiceLevelObjectiveRef())
                   .build(),
            ServiceLevelObjectivesDetail::getWeightagePercentage));
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
}
