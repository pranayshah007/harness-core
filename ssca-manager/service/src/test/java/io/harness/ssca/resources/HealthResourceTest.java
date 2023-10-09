/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.resources;

import static io.harness.rule.OwnerRule.ARPITJ;

import io.harness.SSCAManagerTestBase;
import io.harness.category.element.UnitTests;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.MockitoAnnotations;

public class HealthResourceTest extends SSCAManagerTestBase {
  private HealthResource healthResource;

  @Before
  public void setup() throws IllegalAccessException {
    MockitoAnnotations.initMocks(this);
    healthResource = new HealthResource();
  }

  @Test
  @Owner(developers = ARPITJ)
  @Category(UnitTests.class)
  public void testGet() throws Exception {
    RestResponse response = healthResource.get();
    Assertions.assertThat(response.getResponseMessages().get(0)).isEqualTo("ok");
  }
}
