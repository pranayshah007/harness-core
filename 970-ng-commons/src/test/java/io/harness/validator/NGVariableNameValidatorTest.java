/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.validator;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.rule.OwnerRule.ARCHIT;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
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

@OwnedBy(CDC)
public class NGVariableNameValidatorTest extends CategoryTest {
  private Validator validator;

  @Builder
  static class EntityNameValidatorTestStructure {
    @NGVariableName String name;
  }

  @Before
  public void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testEntityNameValidator_For_NullValue_And_EmptyString() {
    assertEquals("Null name should not be allowed", 1,
        validator.validate(EntityNameValidatorTestStructure.builder().build()).size());
    assertEquals("Null name should not be allowed", 1,
        validator.validate(EntityNameValidatorTestStructure.builder().name(" ").build()).size());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testEntityNameValidatorForReservedKeywords() {
    assertEquals("name should not be equal to a reserved keyword", 1,
        validator.validate(EntityNameValidatorTestStructure.builder().name("eq").build()).size());
    assertEquals("name should not be equal to a reserved keyword", 1,
        validator.validate(EntityNameValidatorTestStructure.builder().name("new").build()).size());
    assertEquals("name should not be equal to a reserved keyword", 1,
        validator.validate(EntityNameValidatorTestStructure.builder().name("lt").build()).size());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testEntityNameValidatorForWordsStartingWithDigit() {
    assertEquals("name should not start with digit", 1,
        validator.validate(EntityNameValidatorTestStructure.builder().name("123").build()).size());
    assertEquals("name should not start with digit", 1,
        validator.validate(EntityNameValidatorTestStructure.builder().name("1var").build()).size());
  }

  @Test
  @Owner(developers = ARCHIT)
  @Category(UnitTests.class)
  public void testEntityNameValidator() {
    for (int i = 0; i < 5000; i++) {
      String name = generateRandomAsciiString();
      int violationsCount = validator.validate(EntityNameValidatorTestStructure.builder().name(name).build()).size();
      if (isValidEntityName(name)) {
        assertEquals("name : " + name, 0, violationsCount);
      } else {
        assertTrue("name : " + name, violationsCount > 0);
      }
    }

    // value with DOT
    int violationsCount = validator.validate(EntityNameValidatorTestStructure.builder().name("a.b").build()).size();
    assertEquals(violationsCount, 0);
  }

  private static String generateRandomAsciiString() {
    String random = RandomStringUtils.randomAscii(100);
    return random.substring(0, new Random().nextInt(100));
  }

  private static boolean isValidEntityName(String name) {
    if (isBlank(name)) {
      return false;
    }

    if (Character.isDigit(name.charAt(0))) {
      return false;
    }

    boolean containsNotAllowedChars = false;
    for (String s : NGVariableNameValidator.NOT_ALLOWED_STRING_SET_DEFAULT) {
      containsNotAllowedChars = name.equals(s);
      if (containsNotAllowedChars) {
        break;
      }
    }

    return name.length() <= NGVariableNameValidator.MAX_ALLOWED_LENGTH
        && Sets.newHashSet(Lists.charactersOf(NGVariableNameValidator.ALLOWED_CHARS_STRING_DEFAULT))
               .containsAll(Lists.charactersOf(name))
        && !containsNotAllowedChars;
  }
}
