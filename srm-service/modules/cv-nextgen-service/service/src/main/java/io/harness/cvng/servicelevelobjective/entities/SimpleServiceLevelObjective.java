/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.entities;

import io.harness.cvng.servicelevelobjective.beans.ServiceLevelIndicatorType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("Simple")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "SimpleServiceLevelObjectiveKeys")
@EqualsAndHashCode(callSuper = true)
public class SimpleServiceLevelObjective extends AbstractServiceLevelObjective {
  public SimpleServiceLevelObjective() {
    super.setType(ServiceLevelObjectiveType.SIMPLE);
  }
  @NotNull String healthSourceIdentifier;
  @NotNull String monitoredServiceIdentifier;
  @NotNull @Size(min = 1) List<String> serviceLevelIndicators;
  ServiceLevelIndicatorType serviceLevelIndicatorType;

  @Override
  public Optional<String> mayBeGetMonitoredServiceIdentifier() {
    return Optional.ofNullable(monitoredServiceIdentifier);
  }

  public static class SimpleServiceLevelObjectiveUpdatableEntity
      extends AbstractServiceLevelObjectiveUpdatableEntity<SimpleServiceLevelObjective> {
    @Override
    public void setUpdateOperations(UpdateOperations<SimpleServiceLevelObjective> updateOperations,
        SimpleServiceLevelObjective simpleServiceLevelObjective) {
      setCommonOperations(updateOperations, simpleServiceLevelObjective);
      updateOperations
          .set(SimpleServiceLevelObjectiveKeys.healthSourceIdentifier,
              simpleServiceLevelObjective.getHealthSourceIdentifier())
          .set(SimpleServiceLevelObjectiveKeys.monitoredServiceIdentifier,
              simpleServiceLevelObjective.getMonitoredServiceIdentifier())
          .set(SimpleServiceLevelObjectiveKeys.serviceLevelIndicatorType,
              simpleServiceLevelObjective.getServiceLevelIndicatorType());
    }
  }
}
