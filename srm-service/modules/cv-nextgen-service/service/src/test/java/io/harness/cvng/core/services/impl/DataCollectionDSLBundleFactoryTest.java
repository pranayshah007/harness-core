/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.impl;

import static io.harness.rule.OwnerRule.ANSUMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataSourceType;
import io.harness.rule.Owner;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DataCollectionDSLBundleFactoryTest extends CvNextGenTestBase {
  public static final String DSL_KEYWORD_RETURN = "return";

  @Test
  @Owner(developers = ANSUMAN)
  @Category(UnitTests.class)
  public void testDSLValidForNextGenHealthSource() {
    List<DataSourceType> dataSourceTypes =
        Arrays.stream(DataSourceType.values()).filter(DataSourceType::isNextGenSpec).collect(Collectors.toList());
    for (DataSourceType dataSourceType : dataSourceTypes) {
      DataCollectionDSLBundle dataCollectionDSLBundle = DataCollectionDSLBundleFactory.readDSL(dataSourceType);
      assertThat(dataCollectionDSLBundle.getActualDataCollectionDSL()).isNotEmpty();
      assertThat(dataCollectionDSLBundle.getActualDataCollectionDSL()).contains(DSL_KEYWORD_RETURN);
      assertThat(dataCollectionDSLBundle.getSampleDataCollectionDSL()).isNotEmpty();
      assertThat(dataCollectionDSLBundle.getSampleDataCollectionDSL()).contains(DSL_KEYWORD_RETURN);
    }
  }
}
