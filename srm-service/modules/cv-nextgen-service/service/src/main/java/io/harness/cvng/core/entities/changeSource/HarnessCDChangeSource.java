/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.changeSource;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class HarnessCDChangeSource extends ChangeSource {
  public static class UpdatableCDNGChangeSourceEntity
      extends UpdatableChangeSourceEntity<HarnessCDChangeSource, HarnessCDChangeSource> {
    @Override
    public void setUpdateOperations(
        UpdateOperations<HarnessCDChangeSource> updateOperations, HarnessCDChangeSource harnessCDChangeSource) {
      setCommonOperations(updateOperations, harnessCDChangeSource);
    }
  }
}
