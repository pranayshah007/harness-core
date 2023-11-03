/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.license.usage.mappers;

import static io.harness.rule.OwnerRule.SATHISH;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.license.usage.dto.ActiveDevelopersTrendCountDTO;
import io.harness.idp.license.usage.entities.ActiveDevelopersDailyCountEntity;
import io.harness.rule.Owner;

import java.util.Date;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class ActiveDevelopersDailyCountEntityMapperTest extends CategoryTest {
  static final String TEST_ACCOUNT_IDENTIFIER = "testAccount123";
  static final long TEST_USERS_COUNT = 3;
  static final String TEST_DATE_IN_STRING_FORMAT = "2023-10-26";
  static final Date TEST_DATE_IN_DATE_FORMAT = new Date();

  @Test
  @Owner(developers = SATHISH)
  @Category(UnitTests.class)
  public void testToDto() {
    ActiveDevelopersDailyCountEntity activeDevelopersDailyCountEntity =
        ActiveDevelopersDailyCountEntity.builder()
            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
            .dateInStringFormat(TEST_DATE_IN_STRING_FORMAT)
            .dateInDateFormat(TEST_DATE_IN_DATE_FORMAT)
            .count(TEST_USERS_COUNT)
            .build();

    ActiveDevelopersTrendCountDTO activeDevelopersTrendCountDTO =
        ActiveDevelopersDailyCountEntityMapper.toDto(activeDevelopersDailyCountEntity);

    assertNotNull(activeDevelopersTrendCountDTO);
    assertEquals(TEST_DATE_IN_STRING_FORMAT, activeDevelopersTrendCountDTO.getDate());
    assertEquals(TEST_USERS_COUNT, activeDevelopersTrendCountDTO.getCount());
  }
}
