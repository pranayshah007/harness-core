/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.helper;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.data.structure.EmptyPredicate;
import io.harness.encryption.Encryptable;
import io.harness.encryption.EncryptionReflectUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.reflection.ReflectionUtils;

import software.wings.annotation.EncryptableSetting;
import software.wings.settings.SettingValue;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

public class SettingValueHelper {
  private static final String USE_ENCRYPTED_VALUE_FLAG_FIELD_BASE = "useEncrypted";

  public static List<Field> getAllEncryptedFields(SettingValue obj) {
    if (!(obj instanceof EncryptableSetting)) {
      return Collections.emptyList();
    }

    return EncryptionReflectUtils.getEncryptedFields(obj.getClass())
        .stream()
        .filter(field -> {
          if (EncryptionReflectUtils.isSecretReference(field)) {
            String flagFiledName = USE_ENCRYPTED_VALUE_FLAG_FIELD_BASE + StringUtils.capitalize(field.getName());

            List<Field> declaredAndInheritedFields =
                ReflectionUtils.getDeclaredAndInheritedFields(obj.getClass(), f -> f.getName().equals(flagFiledName));
            if (isNotEmpty(declaredAndInheritedFields)) {
              Object flagFieldValue = ReflectionUtils.getFieldValue(obj, declaredAndInheritedFields.get(0));
              return flagFieldValue != null && (Boolean) flagFieldValue;
            }
          }

          return true;
        })
        .collect(Collectors.toList());
  }

  public static List<String> getAllEncryptedSecrets(SettingValue obj) {
    if (!(obj instanceof EncryptableSetting)) {
      return Collections.emptyList();
    }

    List<Field> encryptedFields = getAllEncryptedFields(obj);
    if (EmptyPredicate.isEmpty(encryptedFields)) {
      return Collections.emptyList();
    }

    List<String> encryptedSecrets = new ArrayList<>();
    for (Field encryptedField : encryptedFields) {
      Field encryptedRefField = EncryptionReflectUtils.getEncryptedRefField(encryptedField, (Encryptable) obj);
      encryptedRefField.setAccessible(true);
      try {
        String encryptedValue = (String) encryptedRefField.get(obj);
        encryptedSecrets.add(encryptedValue);
      } catch (IllegalAccessException e) {
        throw new InvalidRequestException("Unable to access encrypted field", e);
      }
    }
    return encryptedSecrets;
  }
}
