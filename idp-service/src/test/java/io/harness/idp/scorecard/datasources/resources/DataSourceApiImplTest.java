/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datasources.resources;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.scorecard.datasources.service.DataSourceService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.DataPointsResponse;
import io.harness.spec.server.idp.v1.model.DataSource;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMap;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointsMapResponse;
import io.harness.spec.server.idp.v1.model.DataSourcesResponse;

import java.util.Collections;
import javax.ws.rs.core.Response;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@FieldDefaults(level = AccessLevel.PRIVATE)
@OwnedBy(HarnessTeam.IDP)
public class DataSourceApiImplTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock DataSourceService dataSourceService;

  @InjectMocks DataSourceApiImpl dataSourceApiImpl;

  private static final String TEST_DATA_SOURCE_NAME = "test-data-source-name";
  private static final String TEST_DATA_SOURCE_IDENTIFIER = "test-data-source-identifier";
  private static final String TEST_DATA_SOURCE_DESCRIPTION = "test-data-source-description";
  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-identifier";
  private static final String ERROR_MESSAGE_FOR_API_CALL = "Error : In Making API Call";
  private static final String TEST_DATA_POINT_IDENTIFIER = "test-data-point-identifier";
  private static final Boolean TEST_DATA_POINT_IS_CONDITIONAL = false;
  private static final String TEST_DATA_POINT_TYPE = "test-data-point-type";

  private static final String TEST_DATA_POINT_NAME = "test-data-point-name";

  private static final String TEST_DATA_POINT_DESCRIPTION = "test-data-point-description";

  private static final String TEST_DATA_POINT_INPUT_DESCRIPTION = "test-data-point-input-description";
  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllDataSourcesForAccount() {
    DataSource dataSource = getTestDataSource();

    when(dataSourceService.getAllDataSourcesDetailsForAnAccount(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(dataSource));
    Response response = dataSourceApiImpl.getAllDatasourcesForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(dataSource, ((DataSourcesResponse) response.getEntity()).getDataSources().get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetAllScorecardSummaryError() {
    when(dataSourceService.getAllDataSourcesDetailsForAnAccount(TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_FOR_API_CALL));
    Response response = dataSourceApiImpl.getAllDatasourcesForAccount(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_FOR_API_CALL, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetDataPointsForDataSource() {
    DataPoint dataPoint = getTestDataPoint();

    when(dataSourceService.getAllDataPointsDetailsForDataSource(TEST_ACCOUNT_IDENTIFIER, TEST_DATA_SOURCE_IDENTIFIER))
        .thenReturn(Collections.singletonList(dataPoint));
    Response response =
        dataSourceApiImpl.getDataPointsForDataSource(TEST_DATA_SOURCE_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(dataPoint, ((DataPointsResponse) response.getEntity()).getDataPoints().get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetDataPointsForDataSourceError() {
    when(dataSourceService.getAllDataPointsDetailsForDataSource(TEST_ACCOUNT_IDENTIFIER, TEST_DATA_SOURCE_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_FOR_API_CALL));
    Response response =
        dataSourceApiImpl.getDataPointsForDataSource(TEST_DATA_SOURCE_IDENTIFIER, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_FOR_API_CALL, ((ResponseMessage) response.getEntity()).getMessage());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetDataSourcesDataPointsMap() {
    DataSourceDataPointsMap dataSourceDataPointsMap = new DataSourceDataPointsMap();
    dataSourceDataPointsMap.setDataPoints(Collections.singletonList(getTestDataPoint()));
    dataSourceDataPointsMap.setDataSource(getTestDataSource());
    when(dataSourceService.getDataPointsForDataSources(TEST_ACCOUNT_IDENTIFIER))
        .thenReturn(Collections.singletonList(dataSourceDataPointsMap));
    Response response = dataSourceApiImpl.getDataSourcesDataPointsMap(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(dataSourceDataPointsMap,
        ((DataSourceDataPointsMapResponse) response.getEntity()).getDataSourceDataPointsMap().get(0));
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetDataSourcesDataPointsMapError() {
    when(dataSourceService.getDataPointsForDataSources(TEST_ACCOUNT_IDENTIFIER))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_FOR_API_CALL));
    Response response = dataSourceApiImpl.getDataSourcesDataPointsMap(TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_FOR_API_CALL, ((ResponseMessage) response.getEntity()).getMessage());
  }

  private DataPoint getTestDataPoint() {
    DataPoint dataPoint = new DataPoint();
    dataPoint.setDataPointIdentifier(TEST_DATA_POINT_IDENTIFIER);
    dataPoint.setIsConditional(TEST_DATA_POINT_IS_CONDITIONAL);
    dataPoint.setType(TEST_DATA_POINT_TYPE);
    dataPoint.setName(TEST_DATA_POINT_NAME);
    dataPoint.setDescription(TEST_DATA_POINT_DESCRIPTION);
    dataPoint.setConditionalInputDescription(TEST_DATA_POINT_INPUT_DESCRIPTION);
    return dataPoint;
  }

  private DataSource getTestDataSource() {
    DataSource dataSource = new DataSource();
    dataSource.setName(TEST_DATA_SOURCE_NAME);
    dataSource.setDescription(TEST_DATA_SOURCE_DESCRIPTION);
    dataSource.setIdentifier(TEST_DATA_SOURCE_IDENTIFIER);
    return dataSource;
  }
}
