/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.data.validator;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.VIKAS;
import static io.harness.utils.UuidUtils.base64StrToUuid;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.data.validator.uuid.Uuid;
import io.harness.rule.Owner;
import io.harness.utils.UuidUtils;

import java.util.UUID;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class UuidValidatorTest extends CategoryTest {
  @Builder
  static class UuidValidatorTestStructure {
    @Uuid String str;
  }

  @Test
  @Owner(developers = VIKAS)
  @Category(UnitTests.class)
  public void testUuid() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    final Validator validator = factory.getValidator();

    // Some random string
    assertThat(validator.validate(UuidValidatorTestStructure.builder().str("abcd").build())).isNotEmpty();

    // Random UUID
    String accountId = UUID.randomUUID().toString();
    assertThat(validator.validate(UuidValidatorTestStructure.builder().str(accountId).build())).isEmpty();

    // Specific UUID
    assertThat(
        validator.validate(UuidValidatorTestStructure.builder().str("cdaed56d-8712-414d-b346-01905d0026fe").build()))
        .isEmpty();

    // Specific Base64 encoded UUID
    String base64Str = "za7VbYcSQU2zRgGQXQAm/g"; // a base64 encoded UUID
    String decodedUUIDStr = base64StrToUuid(base64Str);
    assertThat(decodedUUIDStr).isEqualTo("cdaed56d-8712-414d-b346-01905d0026fe");
    assertThat(validator.validate(UuidValidatorTestStructure.builder().str(base64Str).build())).isEmpty();

    // Random Base64 encoded UUID that is URL-safe
    String base64encodedUuid = generateUuid();
    assertThat(validator.validate(UuidValidatorTestStructure.builder().str(base64encodedUuid).build())).isEmpty();

    // Specific Base64 encoded UUID that is URL-safe
    String base64encodedUrlSafeUuid = "sXfoYJRPTOiIaqpICi_aUg";
    assertThat(validator.validate(UuidValidatorTestStructure.builder().str(base64encodedUrlSafeUuid).build()))
        .isEmpty();

    String uuidType1 = "efee4cba-9d5f-11e9-a2a3-2a2ae2dbcce4";
    assertThat(UuidUtils.isValidUuidStr(uuidType1)).isTrue();

    String uuidType4 = "3bcd1e59-1dab-4f6f-a374-17b8e2339f64";
    assertThat(UuidUtils.isValidUuidStr(uuidType4)).isTrue();
  }
}
