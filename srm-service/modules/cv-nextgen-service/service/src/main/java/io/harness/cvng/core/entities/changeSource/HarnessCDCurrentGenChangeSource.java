/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.changeSource;

import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@JsonTypeName("HARNESS_CD_CURRENT_GEN")
@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "HarnessCDCurrentGenChangeSourceKeys")
@EqualsAndHashCode(callSuper = true)
public class HarnessCDCurrentGenChangeSource extends ChangeSource {
  @NotNull private String harnessApplicationId;
  @NotNull private String harnessServiceId;
  @NotNull private String harnessEnvironmentId;

  public static class UpdatableHarnessCDCurrentGenChangeSourceEntity
      extends UpdatableChangeSourceEntity<HarnessCDCurrentGenChangeSource, HarnessCDCurrentGenChangeSource> {
    @Override
    public void setUpdateOperations(UpdateOperations<HarnessCDCurrentGenChangeSource> updateOperations,
        HarnessCDCurrentGenChangeSource harnessCDCurrentGenChangeSource) {
      setCommonOperations(updateOperations, harnessCDCurrentGenChangeSource);
      updateOperations
          .set(HarnessCDCurrentGenChangeSourceKeys.harnessApplicationId,
              harnessCDCurrentGenChangeSource.getHarnessApplicationId())
          .set(HarnessCDCurrentGenChangeSourceKeys.harnessServiceId,
              harnessCDCurrentGenChangeSource.getHarnessServiceId())
          .set(HarnessCDCurrentGenChangeSourceKeys.harnessEnvironmentId,
              harnessCDCurrentGenChangeSource.getHarnessEnvironmentId());
    }
  }
}
