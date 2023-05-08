/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.servicelevelobjective.beans.slospec;

import io.harness.cvng.servicelevelobjective.beans.CompositeSLOFormulaType;
import io.harness.cvng.servicelevelobjective.beans.SLIEvaluationType;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveDetailsDTO;
import io.harness.cvng.servicelevelobjective.beans.ServiceLevelObjectiveType;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@FieldDefaults(level = AccessLevel.PRIVATE)
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompositeServiceLevelObjectiveSpec extends ServiceLevelObjectiveSpec {
  @Size(min = 2, max = 20) @Valid @NotNull List<ServiceLevelObjectiveDetailsDTO> serviceLevelObjectivesDetails;
  SLIEvaluationType evaluationType;

  CompositeSLOFormulaType sloFormulaType;

  @Override
  public ServiceLevelObjectiveType getType() {
    return ServiceLevelObjectiveType.COMPOSITE;
  }

  public SLIEvaluationType getEvaluationType() {
    if (this.evaluationType == null) {
      return SLIEvaluationType.WINDOW;
    }
    return evaluationType;
  }

  public CompositeSLOFormulaType getSloFormulaType() {
    if (this.sloFormulaType == null && getEvaluationType() == SLIEvaluationType.WINDOW) {
      return CompositeSLOFormulaType.WEIGHTED_AVERAGE;
    }
    return sloFormulaType;
  }
}
