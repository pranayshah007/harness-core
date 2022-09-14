/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.cvng.servicelevelobjective.entities;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.List;
import javax.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("Composite")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "CompositeServiceLevelObjectiveKeys")
@EqualsAndHashCode(callSuper = true)
public class CompositeServiceLevelObjective extends AbstractServiceLevelObjective {
  @Size(max = 20) List<ServiceLevelObjectivesDetail> serviceLevelObjectivesDetails;

  public static class ServiceLevelObjectivesDetail {
    String serviceLevelObjectiveRef;
    Double weightagePercentage;
  }
}
