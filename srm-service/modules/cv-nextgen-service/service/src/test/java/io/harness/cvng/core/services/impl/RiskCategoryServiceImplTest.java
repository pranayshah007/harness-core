/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.core.beans.RiskCategory;
import io.harness.cvng.core.beans.monitoredService.RiskCategoryDTO;
import io.harness.cvng.core.services.api.RiskCategoryService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class RiskCategoryServiceImplTest extends CvNextGenTestBase {
  BuilderFactory builderFactory = BuilderFactory.getDefault();

  @Inject private RiskCategoryService riskCategoryService;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
  }

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testRiskCategoriesService() {
    List<RiskCategoryDTO> riskCategoriesList = riskCategoryService.getRiskCategoriesDTO();
    assertThat(riskCategoriesList.size()).isEqualTo(RiskCategory.values().length);
  }
}
