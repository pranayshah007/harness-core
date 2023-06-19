/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.rule.OwnerRule.RAGHAV_MURALI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.service.impl.ApiKeyServiceImpl;
import software.wings.service.intfc.ApiKeyService;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ApiKeyServiceImplTest extends WingsBaseTest {
  ApiKeyService apiKeyService;

  @Before
  public void setup() throws IllegalAccessException {
    apiKeyService = new ApiKeyServiceImpl();
  }

  @Test
  @Owner(developers = RAGHAV_MURALI)
  @Category(UnitTests.class)
  public void validateGetAccountIdFromApiKey_noNullReturned() {
    String apikey =
        "OFdkRHJGM0JRUEdnWXcyYTNmMm92UTo6aFRRVjRHbFJxY0dmUUw5VDYyQXBCZE9kcWl0Rjh3blFNYW5PSXVZeTlaY01iWU1mTHFwV1dNM25EQ0R6NnVWYXFmMEZOMEJBZGJzWGRYMzM=";
    String accountId = apiKeyService.getAccountIdFromApiKey(apikey);
    assertThat(accountId).isNotNull();
    assertThat(accountId).isEqualTo("8WdDrF3BQPGgYw2a3f2ovQ");
  }
}
