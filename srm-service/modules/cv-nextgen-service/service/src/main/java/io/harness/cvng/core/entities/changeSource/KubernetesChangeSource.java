/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.changeSource;

import io.harness.mongo.index.FdIndex;

import javax.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.mongodb.morphia.query.UpdateOperations;

@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class KubernetesChangeSource extends ChangeSource {
  @NotNull @FdIndex String connectorIdentifier;

  public static class UpdatableKubernetesChangeSourceEntity
      extends UpdatableChangeSourceEntity<KubernetesChangeSource, KubernetesChangeSource> {
    @Override
    public void setUpdateOperations(UpdateOperations<KubernetesChangeSource> updateOperations,
        KubernetesChangeSource harnessKubernetesChangeSource) {
      setCommonOperations(updateOperations, harnessKubernetesChangeSource);
      /* NO-OP should not update fields here */
    }
  }
}
