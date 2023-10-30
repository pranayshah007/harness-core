/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.service.impl;

import static io.harness.rule.OwnerRule.SAHILDEEP;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.views.businessmapping.dao.BusinessMappingDao;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class BusinessMappingValidationServiceImplTest extends CategoryTest {
  @Mock private BusinessMappingDao businessMappingDao;
  @InjectMocks private BusinessMappingValidationServiceImpl businessMappingValidationService;

  @Before
  public void setUp() {}

  @Test(expected = InvalidRequestException.class)
  @Owner(developers = SAHILDEEP)
  @Category(UnitTests.class)
  public void testNullBusinessMapping() {
    businessMappingValidationService.validateBusinessMapping(null);
  }
}
