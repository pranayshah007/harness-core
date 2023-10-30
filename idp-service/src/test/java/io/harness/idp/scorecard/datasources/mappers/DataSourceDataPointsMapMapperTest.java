/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package io.harness.idp.scorecard.datasources.mappers;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataSource;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMap;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceDataPointsMapMapperTest {
  private static final String TEST_DATA_SOURCE_IDENTIFIER = " test-datasource-identifier";
  private static final String TEST_DATA_SOURCE_NAME = "test-datasource-name";
  private static final String TEST_DATA_SOURCE_DESCRIPTION = "test-datasource-description";

  private static final String TEST_DATA_POINT_TYPE = "test-datapoint-type";
  private static final String TEST_DATA_POINT_NAME = "test-data-point-name";
  private static final String TEST_DATA_POINT_DESCRIPTION = "test-data-point=description";
  private static final Boolean TEST_DATA_POINT_IS_CONDITIONAL_VALUE = false;
  private static final String TEST_CONDITIONAL_INPUT_DESCRIPTION = "test-datapoint-input-description";
  private static final String TEST_DATAPOINT_IDENTIFIER = "test-datapoint-identifier";
  private static final String TEST_DATAPOINT_DETAILED_DESCRIPTION = "test-datapoint-detailed-description";

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testToDTO() {
    DataSource dataSource = new DataSource();
    dataSource.setIdentifier(TEST_DATA_SOURCE_IDENTIFIER);
    dataSource.setName(TEST_DATA_SOURCE_NAME);
    dataSource.setDescription(TEST_DATA_SOURCE_DESCRIPTION);

    DataPoint dataPoint = new DataPoint();
    dataPoint.setName(TEST_DATA_POINT_NAME);
    dataPoint.setDataPointIdentifier(TEST_DATAPOINT_IDENTIFIER);
    dataPoint.setType(TEST_DATA_POINT_TYPE);
    dataPoint.setDescription(TEST_DATA_POINT_DESCRIPTION);
    dataPoint.setDetailedDescription(TEST_DATAPOINT_DETAILED_DESCRIPTION);
    dataPoint.setIsConditional(TEST_DATA_POINT_IS_CONDITIONAL_VALUE);
    dataPoint.setConditionalInputDescription(TEST_CONDITIONAL_INPUT_DESCRIPTION);
    List<DataPoint> listOfDataPoints = new ArrayList<>();
    listOfDataPoints.add(dataPoint);

    DataSourceDataPointsMap dataSourceDataPointsMap = DataSourceDataPointsMapMapper.toDto(dataSource, listOfDataPoints);
    DataSource mappedDataSource = dataSourceDataPointsMap.getDataSource();
    assertEquals(dataSource.getIdentifier(), mappedDataSource.getIdentifier());
    assertEquals(dataSource.getName(), mappedDataSource.getName());
    assertEquals(dataSource.getDescription(), mappedDataSource.getDescription());

    DataPoint mappedDataPoint = dataSourceDataPointsMap.getDataPoints().get(0);
    assertEquals(dataPoint.getDataPointIdentifier(), mappedDataPoint.getDataPointIdentifier());
    assertEquals(dataPoint.getDescription(), mappedDataPoint.getDescription());
    assertEquals(dataPoint.getName(), mappedDataPoint.getName());
  }
}
