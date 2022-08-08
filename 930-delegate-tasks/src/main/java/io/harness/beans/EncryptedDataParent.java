/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.encryption.EncryptionReflectUtils;
import io.harness.exception.UnexpectedException;
import io.harness.reflection.ReflectionUtils;

import software.wings.settings.SettingVariableTypes;

import java.lang.reflect.Field;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@OwnedBy(PL)
@Value
@Builder
@AllArgsConstructor
@EqualsAndHashCode
@FieldNameConstants(innerTypeName = "EncryptedDataParentKeys")
public class EncryptedDataParent {
  String id;
  SettingVariableTypes type;
  String fieldName;

  public static EncryptedDataParent createParentRef(@NotNull String objectId, @NotNull Class objectClass,
      @NotNull String fieldName, @NotNull SettingVariableTypes type) {
    Field encryptedField = ReflectionUtils.getFieldByName(objectClass, fieldName);
    if (encryptedField == null) {
      throw new UnexpectedException(String.format("Field %s does not exist in the %s", fieldName, objectClass));
    }
    String fieldKey = EncryptionReflectUtils.getEncryptedFieldTag(encryptedField);
    return new EncryptedDataParent(objectId, type, fieldKey);
  }
}
