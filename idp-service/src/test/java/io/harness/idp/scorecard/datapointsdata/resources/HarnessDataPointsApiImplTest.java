/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.datapointsdata.resources;

import static io.harness.rule.OwnerRule.DEVESH;

import static junit.framework.TestCase.assertEquals;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.idp.common.Constants;
import io.harness.idp.scorecard.datapointsdata.resource.HarnessDataPointsApiImpl;
import io.harness.idp.scorecard.datapointsdata.service.DataPointDataValueService;
import io.harness.rule.Owner;
import io.harness.spec.server.idp.v1.model.DataPointInputValues;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfo;
import io.harness.spec.server.idp.v1.model.DataSourceDataPointInfoRequest;
import io.harness.spec.server.idp.v1.model.DataSourceLocationInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
public class HarnessDataPointsApiImplTest extends CategoryTest {
  AutoCloseable openMocks;

  @Mock DataPointDataValueService dataPointDataValueService;

  @InjectMocks HarnessDataPointsApiImpl harnessDataPointsApiImpl;

  private static final String TEST_DATA_POINT_IDENTIFIER = "test-data-point-identifier";
  private static final String TEST_DATA_POINT_VALUE = "test-data-point-value";

  private static final String TEST_ACCOUNT_IDENTIFIER = "test-account-identifier";
  private static final String TEST_DATA_SOURCE_IDENTIFIER = "harness";
  private static final String DATA_POINT_VALUE = "test-value";
  private static final String DATA_POINT_ERROR_MESSAGE = "test-error_message";
  private static final String ERROR_MESSAGE_FOR_API_CALL = "Error : In Making API Call";

  @Before
  public void setUp() {
    openMocks = MockitoAnnotations.openMocks(this);
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetDataSourceDataPointValues() {
    DataSourceDataPointInfoRequest dataSourceDataPointInfoRequest = new DataSourceDataPointInfoRequest();
    DataSourceDataPointInfo dataSourceDataPointInfo = new DataSourceDataPointInfo();
    DataSourceLocationInfo dataSourceLocationInfo = new DataSourceLocationInfo();

    DataPointInputValues dataPointInputValues = new DataPointInputValues();
    dataPointInputValues.setDataPointIdentifier(TEST_DATA_POINT_IDENTIFIER);
    dataPointInputValues.setValues(Collections.singletonList(TEST_DATA_POINT_VALUE));

    dataSourceLocationInfo.setDataPoints(Collections.singletonList(dataPointInputValues));
    dataSourceDataPointInfo.setDataSourceLocation(dataSourceLocationInfo);
    dataSourceDataPointInfoRequest.setRequest(dataSourceDataPointInfo);

    Map<String, Object> returnedObject = new HashMap<>();
    Map<String, String> dataPointInfo = new HashMap<>();
    dataPointInfo.put(Constants.DATA_POINT_VALUE_KEY, DATA_POINT_VALUE);
    dataPointInfo.put(Constants.ERROR_MESSAGE_KEY, DATA_POINT_ERROR_MESSAGE);
    returnedObject.put(TEST_DATA_POINT_IDENTIFIER, dataPointInfo);

    when(dataPointDataValueService.getDataPointDataValues(
             TEST_ACCOUNT_IDENTIFIER, TEST_DATA_SOURCE_IDENTIFIER, dataSourceDataPointInfoRequest.getRequest()))
        .thenReturn(returnedObject);
    Response response =
        harnessDataPointsApiImpl.getHarnessDataPointValues(dataSourceDataPointInfoRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
    assertEquals(returnedObject, response.getEntity());
  }

  @Test
  @Owner(developers = DEVESH)
  @Category(UnitTests.class)
  public void testGetDataSourceDataPointValuesError() {
    DataSourceDataPointInfoRequest dataSourceDataPointInfoRequest = new DataSourceDataPointInfoRequest();
    DataSourceDataPointInfo dataSourceDataPointInfo = new DataSourceDataPointInfo();
    DataSourceLocationInfo dataSourceLocationInfo = new DataSourceLocationInfo();

    DataPointInputValues dataPointInputValues = new DataPointInputValues();
    dataPointInputValues.setDataPointIdentifier(TEST_DATA_POINT_IDENTIFIER);
    dataPointInputValues.setValues(Collections.singletonList(TEST_DATA_POINT_VALUE));

    dataSourceLocationInfo.setDataPoints(Collections.singletonList(dataPointInputValues));
    dataSourceDataPointInfo.setDataSourceLocation(dataSourceLocationInfo);
    dataSourceDataPointInfoRequest.setRequest(dataSourceDataPointInfo);

    Map<String, Object> returnedObject = new HashMap<>();
    Map<String, String> dataPointInfo = new HashMap<>();
    dataPointInfo.put(Constants.DATA_POINT_VALUE_KEY, DATA_POINT_VALUE);
    dataPointInfo.put(Constants.ERROR_MESSAGE_KEY, DATA_POINT_ERROR_MESSAGE);
    returnedObject.put(TEST_DATA_POINT_IDENTIFIER, dataPointInfo);

    when(dataPointDataValueService.getDataPointDataValues(
             TEST_ACCOUNT_IDENTIFIER, TEST_DATA_SOURCE_IDENTIFIER, dataSourceDataPointInfoRequest.getRequest()))
        .thenThrow(new InvalidRequestException(ERROR_MESSAGE_FOR_API_CALL));
    Response response =
        harnessDataPointsApiImpl.getHarnessDataPointValues(dataSourceDataPointInfoRequest, TEST_ACCOUNT_IDENTIFIER);
    assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    assertEquals(ERROR_MESSAGE_FOR_API_CALL, ((ResponseMessage) response.getEntity()).getMessage());
  }
}
