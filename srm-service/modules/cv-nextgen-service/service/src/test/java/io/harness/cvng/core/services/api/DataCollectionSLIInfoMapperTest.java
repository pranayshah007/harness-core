/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.rule.OwnerRule.ABHIJITH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.models.VerificationType;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DataCollectionSLIInfoMapperTest extends CvNextGenTestBase {
  List<DataSourceType> timeSeriesDataTypesWithoutSLICapability = Arrays.asList(DataSourceType.KUBERNETES);

  @Inject private Map<DataSourceType, DataCollectionSLIInfoMapper> dataSourceTypeDataCollectionInfoMapperMap;

  @Test
  @Owner(developers = ABHIJITH)
  @Category(UnitTests.class)
  @Ignore("Will fix with the next PR")
  public void testSLIInfoMapperBindingIsAddedForAllTimeSeriesTypes() {
    Arrays.stream(DataSourceType.values())
        .filter(type -> !timeSeriesDataTypesWithoutSLICapability.contains(type))
        .filter(type -> type.getVerificationType().equals(VerificationType.TIME_SERIES))
        .forEach(type -> assertThat(dataSourceTypeDataCollectionInfoMapperMap).containsKey(type));
  }
}
