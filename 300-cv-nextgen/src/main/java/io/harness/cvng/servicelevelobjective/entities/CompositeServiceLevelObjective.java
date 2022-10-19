/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveV2DTO;
import io.harness.cvng.servicelevelobjective.beans.slospec.CompositeServiceLevelObjectiveSpec;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import java.util.Optional;
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
    String serviceLevelObjectiveRef;
    Double weightagePercentage;
  }

  @Override
  public Optional<String> mayBeGetMonitoredServiceIdentifier() {
    return Optional.empty();
  }

  public static class CompositeServiceLevelObjectiveUpdatableEntity
      extends AbstractServiceLevelObjectiveUpdatableEntity<CompositeServiceLevelObjective, ServiceLevelObjectiveV2DTO> {
    @Override
    public void setUpdateOperations(UpdateOperations<CompositeServiceLevelObjective> updateOperations,
        ServiceLevelObjectiveV2DTO serviceLevelObjectiveV2DTO) {
      setCommonOperations(updateOperations, serviceLevelObjectiveV2DTO);
      updateOperations.set(CompositeServiceLevelObjectiveKeys.serviceLevelObjectivesDetails,
          ((CompositeServiceLevelObjectiveSpec) serviceLevelObjectiveV2DTO.getSpec())
              .getServiceLevelObjectivesDetails());
      updateOperations.inc(CompositeServiceLevelObjectiveKeys.version);
    }
  }
}
