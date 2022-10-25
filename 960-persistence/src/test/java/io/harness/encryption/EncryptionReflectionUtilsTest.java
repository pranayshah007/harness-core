/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.encryption;

import static io.harness.rule.OwnerRule.UTKARSH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.reflection.util.ReflectionUtils;
import io.harness.rule.Owner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.Optional;
import lombok.experimental.FieldNameConstants;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldNameConstants(innerTypeName = "EncryptionReflectionUtilsTestKeys")
public class EncryptionReflectionUtilsTest extends CategoryTest {
  private static final String ANNOTATION_FIELD_NAME = "test_annotation";
  @Encrypted(fieldName = ANNOTATION_FIELD_NAME) private String value;
  @Encrypted(fieldName = "test", isReference = true) private String key;

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetEncryptedFieldTag() {
    Optional<Field> annotatedFieldOptional = Optional.ofNullable(
        ReflectionUtils.getFieldByName(EncryptionReflectionUtilsTest.class, EncryptionReflectionUtilsTestKeys.value));
    Field annotatedField = annotatedFieldOptional.<RuntimeException>orElseThrow(
        () -> { throw new RuntimeException("Field should not be null"); });
    String encryptedFieldTag = EncryptionReflectUtils.getEncryptedFieldTag(annotatedField);
    assertThat(encryptedFieldTag).isEqualTo(ANNOTATION_FIELD_NAME);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testGetFieldHavingFieldName() {
    Optional<Field> annotatedFieldOptional = Optional.ofNullable(
        ReflectionUtils.getFieldByName(EncryptionReflectionUtilsTest.class, EncryptionReflectionUtilsTestKeys.value));
    Field annotatedField = annotatedFieldOptional.<RuntimeException>orElseThrow(
        () -> { throw new RuntimeException("Field should not be null"); });
    Field field =
        EncryptionReflectUtils.getFieldHavingFieldName(Collections.singletonList(annotatedField), ANNOTATION_FIELD_NAME)
            .<RuntimeException>orElseThrow(() -> { throw new RuntimeException("Field should not be null"); });
    assertThat(field.getName()).isEqualTo(EncryptionReflectionUtilsTestKeys.value);
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testIsSecretReference_shouldReturnTrue() {
    Optional<Field> annotatedFieldOptional = Optional.ofNullable(
        ReflectionUtils.getFieldByName(EncryptionReflectionUtilsTest.class, EncryptionReflectionUtilsTestKeys.key));
    Field annotatedField = annotatedFieldOptional.<RuntimeException>orElseThrow(
        () -> { throw new RuntimeException("Field should not be null"); });
    boolean isReference = EncryptionReflectUtils.isSecretReference(annotatedField);
    assertThat(isReference).isTrue();
  }

  @Test
  @Owner(developers = UTKARSH)
  @Category(UnitTests.class)
  public void testIsSecretReference_shouldReturnFalse() {
    Optional<Field> annotatedFieldOptional = Optional.ofNullable(
        ReflectionUtils.getFieldByName(EncryptionReflectionUtilsTest.class, EncryptionReflectionUtilsTestKeys.value));
    Field annotatedField = annotatedFieldOptional.<RuntimeException>orElseThrow(
        () -> { throw new RuntimeException("Field should not be null"); });
    boolean isReference = EncryptionReflectUtils.isSecretReference(annotatedField);
    assertThat(isReference).isFalse();
  }
}
