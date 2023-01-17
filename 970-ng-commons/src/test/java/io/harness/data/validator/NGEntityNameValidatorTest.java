/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.data.validator;

import static io.harness.rule.OwnerRule.JIMIT_GANDHI;
import static io.harness.rule.OwnerRule.KARAN;
import static io.harness.rule.OwnerRule.YUVRAJ;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Random;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import lombok.Builder;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class NGEntityNameValidatorTest extends CategoryTest {
  private Validator validator;

  private static final String ALLOWED_CHARS_STRING =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_. /";

  @Builder
  static class EntityNameValidatorWithDefaultValues {
    @NGEntityName String name;
  }

  @Before
  public void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testEntityNameValidator_For_NullValue_And_EmptyString() {
    assertEquals("Null name should not be allowed", 1,
        validator.validate(EntityNameValidatorWithDefaultValues.builder().build()).size());
    assertEquals("Null name should not be allowed", 1,
        validator.validate(EntityNameValidatorWithDefaultValues.builder().name(" ").build()).size());
  }

  @Test
  @Owner(developers = KARAN)
  @Category(UnitTests.class)
  public void testEntityNameValidator() {
    for (int i = 0; i < 5000; i++) {
      String name = generateRandomAsciiString(100);
      int violationsCount =
          validator.validate(EntityNameValidatorWithDefaultValues.builder().name(name).build()).size();
      if (isValidEntityName(name, 64)) {
        assertEquals("name : " + name, 0, violationsCount);
      } else {
        assertTrue("name : " + name, violationsCount > 0);
      }
    }
  }

  @Test
  @Owner(developers = YUVRAJ)
  @Category(UnitTests.class)
  public void testEntityNameValidatorForDot() {
    String name = "abc.abc";
    int violationsCount = validator.validate(EntityNameValidatorWithDefaultValues.builder().name(name).build()).size();
    assertEquals("name : " + name, 0, violationsCount);
  }

  private static String generateRandomAsciiString(int maxSize) {
    String random = RandomStringUtils.randomAscii(maxSize);
    return random.substring(0, new Random().nextInt(maxSize));
  }

  private static boolean isValidEntityName(String name, int maxSize) {
    return !isBlank(name) && name.length() <= maxSize
        && Sets.newHashSet(Lists.charactersOf(ALLOWED_CHARS_STRING)).containsAll(Lists.charactersOf(name));
  }

  @Builder
  static class EntityNameValidatorWithCustomMaxSize {
    @NGEntityName(maxLength = 128) String name;
  }

  @Test
  @Owner(developers = JIMIT_GANDHI)
  @Category(UnitTests.class)
  public void testCustomMaxLengthForEntityNameValidator() {
    for (int i = 0; i < 5000; i++) {
      String name = generateRandomAsciiString(200);
      int violationsCount =
          validator.validate(EntityNameValidatorWithCustomMaxSize.builder().name(name).build()).size();
      if (isValidEntityName(name, 128)) {
        assertEquals("name : " + name, 0, violationsCount);
      } else {
        assertTrue("name : " + name, violationsCount > 0);
      }
    }
  }
}
