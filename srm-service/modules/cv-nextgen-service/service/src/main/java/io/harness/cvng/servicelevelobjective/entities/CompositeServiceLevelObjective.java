/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.CompositeSLOFormulaType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsRefDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

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

  @Size(min = 2, max = 20, message = "A minimum of 2 simple SLO's and a maximum of 20 simple SLO's can be referenced.")
  List<ServiceLevelObjectivesDetail> serviceLevelObjectivesDetails;

  private CompositeSLOFormulaType compositeSLOFormulaType;

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "CompositeServiceLevelObjectiveDetailsKeys")
  @EqualsAndHashCode()
  public static class ServiceLevelObjectivesDetail {
    String accountId;
    String orgIdentifier;
    String projectIdentifier;
    String serviceLevelObjectiveRef;
    @FieldNameConstants.Exclude Double weightagePercentage;

    public ServiceLevelObjectiveDetailsRefDTO getServiceLevelObjectiveDetailsRefDTO() {
      return ServiceLevelObjectiveDetailsRefDTO.builder()
          .accountId(accountId)
          .orgIdentifier(orgIdentifier)
          .projectIdentifier(projectIdentifier)
          .serviceLevelObjectiveRef(serviceLevelObjectiveRef)
          .build();
    }
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
    }
  }

  public CompositeSLOFormulaType getCompositeSLOFormulaType() {
    if (this.compositeSLOFormulaType == null) {
      return CompositeSLOFormulaType.WEIGHTED_AVERAGE;
    }
    return compositeSLOFormulaType;
  }
}
