/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.service;

import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.idp.scorecard.datapoints.entity.DataPointEntity;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.datasources.beans.entity.DataSourceEntity;
import io.harness.idp.scorecard.datasources.repositories.DataSourceRepository;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataSource;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMap;

import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.IDP)
public class DataSourceServiceImplTest extends CategoryTest {
  AutoCloseable openMocks;
  @Mock DataSourceRepository dataSourceRepository;

  @Mock DataPointService dataPointService;

  @InjectMocks DataSourceServiceImpl dataSourceServiceImpl;

  private static final String TEST_DATA_SOURCE_IDENTIFIER = " test-datasource-identifier";
  private static final String TEST_DATA_SOURCE_NAME = "test-datasource-name";
  private static final String TEST_DATA_SOURCE_DESCRIPTION = "test-datasource-description";
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-accountIdentifier";

  private static final String TEST_DATA_POINT_TYPE = "test-datapoint-type";
  private static final String TEST_DATA_POINT_NAME = "test-data-point-name";
  private static final String TEST_DATA_POINT_DESCRIPTION = "test-data-point=description";
  private static final Boolean TEST_DATA_POINT_IS_CONDITIONAL_VALUE = false;
  private static final String TEST_CONDITIONAL_INPUT_DESCRIPTION = "test-datapoint-input-description";
  private static final String TEST_DATAPOINT_IDENTIFIER = "test-datapoint-identifier";
  private static final String TEST_DATAPOINT_DETAILED_DESCRIPTION = "test-datapoint-detailed-description";

  private static final String TEST_DATA_SOURCE_LOCATION_IDENTIFIER = "test-data-source-location-identifier";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }
  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllDataSourcesDetailsForAnAccount() {
    DataSourceEntity dataSourceEntity = DataSourceEntity.builder()
                                            .name(TEST_DATA_SOURCE_NAME)
                                            .description(TEST_DATA_SOURCE_DESCRIPTION)
                                            .identifier(TEST_DATA_SOURCE_IDENTIFIER)
                                            .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                            .build();
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(Collections.singletonList(dataSourceEntity));
    List<DataSource> dataSourceList =
        dataSourceServiceImpl.getAllDataSourcesDetailsForAnAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(dataSourceEntity.getName(), dataSourceList.get(0).getName());
    assertEquals(dataSourceEntity.getDescription(), dataSourceList.get(0).getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllDataPointsDetailsForDataSource() {
    DataPoint dataPoint = new DataPoint();
    dataPoint.setName(TEST_DATA_POINT_NAME);
    dataPoint.setDataPointIdentifier(TEST_DATAPOINT_IDENTIFIER);
    dataPoint.setType(TEST_DATA_POINT_TYPE);
    dataPoint.setDescription(TEST_DATA_POINT_DESCRIPTION);
    dataPoint.setDetailedDescription(TEST_DATAPOINT_DETAILED_DESCRIPTION);
    dataPoint.setIsConditional(TEST_DATA_POINT_IS_CONDITIONAL_VALUE);
    dataPoint.setConditionalInputDescription(TEST_CONDITIONAL_INPUT_DESCRIPTION);

    when(dataPointService.getAllDataPointsDetailsForAccountAndDataSource(
             TEST_ACCOUNT_IDENTIFIER, TEST_DATA_SOURCE_IDENTIFIER))
        .thenReturn(Collections.singletonList(dataPoint));

    List<DataPoint> returnedDataPoints = dataSourceServiceImpl.getAllDataPointsDetailsForDataSource(
        TEST_ACCOUNT_IDENTIFIER, TEST_DATA_SOURCE_IDENTIFIER);
    assertEquals(returnedDataPoints.get(0).getDataPointIdentifier(), dataPoint.getDataPointIdentifier());
    assertEquals(returnedDataPoints.get(0).getName(), dataPoint.getName());
    assertEquals(returnedDataPoints.get(0).getDescription(), dataPoint.getDescription());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetDataPointsForDataSources() {
    DataPointEntity dataPointEntity = DataPointEntity.builder()
                                          .dataSourceIdentifier(TEST_DATA_SOURCE_IDENTIFIER)
                                          .accountIdentifier(TEST_ACCOUNT_IDENTIFIER)
                                          .conditionalInputValueDescription(TEST_CONDITIONAL_INPUT_DESCRIPTION)
                                          .detailedDescription(TEST_DATAPOINT_DETAILED_DESCRIPTION)
                                          .isConditional(TEST_DATA_POINT_IS_CONDITIONAL_VALUE)
                                          .identifier(TEST_DATAPOINT_IDENTIFIER)
                                          .dataSourceLocationIdentifier(TEST_DATA_SOURCE_LOCATION_IDENTIFIER)
                                          .type(DataPointEntity.Type.NUMBER)
                                          .build();
    DataSourceEntity dataSourceEntity = DataSourceEntity.builder()
                                            .name(TEST_DATA_SOURCE_NAME)
                                            .description(TEST_DATA_SOURCE_DESCRIPTION)
                                            .accountIdentifier(TEST_DATA_SOURCE_IDENTIFIER)
                                            .identifier(TEST_DATA_SOURCE_IDENTIFIER)
                                            .build();

    when(dataPointService.getAllDataPointsForAccount(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(dataPointEntity));
    when(dataSourceRepository.findAllByAccountIdentifierIn(addGlobalAccountIdentifierAlong(TEST_ACCOUNT_IDENTIFIER)))
        .thenReturn(Collections.singletonList(dataSourceEntity));

    List<DataSourceDataPointsMap> returnedData =
        dataSourceServiceImpl.getDataPointsForDataSources(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(returnedData.get(0).getDataSource().getName(), dataSourceEntity.getName());

    assertEquals(returnedData.get(0).getDataSource().getIdentifier(), dataSourceEntity.getIdentifier());

    assertEquals(returnedData.get(0).getDataPoints().get(0).getDataPointIdentifier(), dataPointEntity.getIdentifier());
    assertEquals(returnedData.get(0).getDataPoints().get(0).getName(), dataPointEntity.getName());
  }
}
