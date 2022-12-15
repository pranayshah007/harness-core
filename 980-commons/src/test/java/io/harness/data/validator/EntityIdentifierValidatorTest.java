/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.data.validator;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.data.validator.EntityIdentifierValidator.NOT_ALLOWED_WORDS;
import static io.harness.rule.OwnerRule.KANHAIYA;
import static io.harness.rule.OwnerRule.SHREYAS;
import static io.harness.rule.OwnerRule.VIKAS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import java.util.Random;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import lombok.Builder;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PL)
public class EntityIdentifierValidatorTest extends CategoryTest {
  private Validator validator;

  @Builder
  static class EntityIdentifierValidatorTestStructure {
    @EntityIdentifier String identifier;
  }

  @Builder
  static class EntityScopedIdentifierValidatorTestStructure {
    @EntityIdentifier(allowScoped = true) String identifier;
  }

  @Before
  public void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testEntityIdentifierValidator_For_NotAllowedWords() {
    int violationCount =
        NOT_ALLOWED_WORDS.stream()
            .mapToInt(identifier
                -> validator.validate(EntityIdentifierValidatorTestStructure.builder().identifier(identifier).build())
                       .size())
            .sum();

    assertEquals(
        "Violation count should be same as NOT_ALLOWED_WORDS set size", NOT_ALLOWED_WORDS.size(), violationCount);
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testEntityIdentifierValidator_For_NullValue_And_EmptyString() {
    assertEquals("Null identifier should not be allowed", 1,
        validator.validate(EntityIdentifierValidatorTestStructure.builder().build()).size());
    assertEquals("Null identifier should not be allowed", 1,
        validator.validate(EntityIdentifierValidatorTestStructure.builder().identifier(" ").build()).size());
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testEntityIdentifierValidator() {
    assertEquals("Null identifier should not be allowed", 1,
        validator.validate(EntityIdentifierValidatorTestStructure.builder().build()).size());

    EntityIdentifierValidator entityIdentifierValidator = new EntityIdentifierValidator();
    for (int i = 0; i < 5000; i++) {
      String identifier = generateRandomAsciiString();
      int violationsCount =
          validator.validate(EntityIdentifierValidatorTestStructure.builder().identifier(identifier).build()).size();
      if (isValidEntityIdentifier(identifier, entityIdentifierValidator)) {
        assertEquals("identifier : " + identifier, 0, violationsCount);
      } else {
        assertTrue("identifier : " + identifier, violationsCount > 0);
      }
    }
  }

  @Test
  @Owner(developers = KANHAIYA)
  @Category(UnitTests.class)
  public void testEntityIdentifierValidatorWithScopeAllowed() {
    assertEquals(0,
        validator
            .validate(EntityScopedIdentifierValidatorTestStructure.builder().identifier("account.identifier").build())
            .size());
    assertEquals(0,
        validator.validate(EntityScopedIdentifierValidatorTestStructure.builder().identifier("org.identifier").build())
            .size());
    assertEquals(1,
        validator
            .validate(EntityScopedIdentifierValidatorTestStructure.builder().identifier("scoped.identifier").build())
            .size());
  }

  private static String generateRandomAsciiString() {
    String random = RandomStringUtils.randomAscii(100);
    return random.substring(0, new Random().nextInt(100));
  }

  private static boolean isValidEntityIdentifier(
      String identifier, EntityIdentifierValidator entityIdentifierValidator) {
    if (isBlank(identifier)) {
      return false;
    }

    return entityIdentifierValidator.matchesIdentifierPattern(identifier)
        && entityIdentifierValidator.hasAllowedWords(identifier);
  }

  private static boolean isLetterOrDigit(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
  }

  @Test
  @Owner(developers = SHREYAS)
  @Category(UnitTests.class)
  public void testEntityIdentifierValidator_ForStringStartingWith$() {
    String identifier = "$abc";
    EntityIdentifierValidatorTestStructure entityIdentifierValidatorTestStructure =
        EntityIdentifierValidatorTestStructure.builder().identifier(identifier).build();
    assertEquals(1, validator.validate(entityIdentifierValidatorTestStructure).size());
    assertEquals("can be 64 characters long and can only contain alphanumeric, underscore and $ characters,"
            + " and not start with a number or $",
        validator.validate(entityIdentifierValidatorTestStructure).stream().findAny().get().getMessage());
  }
}
