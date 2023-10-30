/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities.changeSource;

import dev.morphia.query.UpdateOperations;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
@FieldNameConstants(innerTypeName = "CustomChangeSourceKeys")
@EqualsAndHashCode(callSuper = true)
public class CustomChangeSource extends ChangeSource {
  String authorizationToken;
  public static class UpdatableCustomChangeSourceEntity
      extends UpdatableChangeSourceEntity<CustomChangeSource, CustomChangeSource> {
    @Override
    public void setUpdateOperations(UpdateOperations<CustomChangeSource> updateOperations, CustomChangeSource dto) {
      setCommonOperations(updateOperations, dto);

      if (dto.getAuthorizationToken() == null) {
        updateOperations.unset(CustomChangeSourceKeys.authorizationToken);
      } else {
        updateOperations.set(CustomChangeSourceKeys.authorizationToken, dto.getAuthorizationToken());
      }
    }
  }
}
