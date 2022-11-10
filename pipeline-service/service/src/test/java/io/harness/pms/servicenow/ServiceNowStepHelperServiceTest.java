/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.servicenow;

import static io.harness.rule.OwnerRule.NAMANG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.servicenow.ServiceNowTaskNGResponse;
import io.harness.exception.ServiceNowException;
import io.harness.rule.Owner;
import io.harness.servicenow.ServiceNowImportSetResponseNG;
import io.harness.servicenow.ServiceNowImportSetTransformMapResult;
import io.harness.steps.servicenow.importset.ServiceNowImportSetOutcome;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;

public class ServiceNowStepHelperServiceTest extends CategoryTest {
  private static final String STAGING_TABLE = "STAGING_TABLE";
  private static final String IMPORT_SET = "IMPORT_SET";
  private static final String RECORD_URL =
      "https://harness.service-now.com/api/now/table/incident/a639e9ccdb4651909e7c2a5913961911";
  private static final String TARGET_TABLE = "incident";
  private static final String TRANSFORM_MAP = "Testing 2 transform maps";
  private static final String STATUS = "inserted";
  private static final String DISPLAY_NAME = "number";
  private static final String DISPLAY_VALUE = "INC0083151";
  private static final String ERROR_STATUS = "error";
  private static final String ERROR_MESSAGE = "No transform entry or scripts are defined; Target record not found";

  private ServiceNowImportSetTransformMapResult normalResult;
  private ServiceNowImportSetTransformMapResult errorResult;
  private ServiceNowImportSetTransformMapResult errorResultWhenNoTransformMap;
  private ServiceNowImportSetTransformMapResult invalidResultWithoutStatus;
  private ServiceNowImportSetTransformMapResult invalidResultWithoutTransformMap;

  @InjectMocks @Inject ServiceNowStepHelperServiceImpl serviceNowStepHelperService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    normalResult = ServiceNowImportSetTransformMapResult.builder()
                       .targetRecordURL(RECORD_URL)
                       .targetTable(TARGET_TABLE)
                       .transformMap(TRANSFORM_MAP)
                       .status(STATUS)
                       .displayName(DISPLAY_NAME)
                       .displayValue(DISPLAY_VALUE)
                       .build();
    errorResult = ServiceNowImportSetTransformMapResult.builder()
                      .transformMap(TRANSFORM_MAP)
                      .targetTable(TARGET_TABLE)
                      .status(ERROR_STATUS)
                      .errorMessage(ERROR_MESSAGE)
                      .build();
    errorResultWhenNoTransformMap = ServiceNowImportSetTransformMapResult.builder()
                                        .transformMap("")
                                        .status(ERROR_STATUS)
                                        .errorMessage(ERROR_MESSAGE)
                                        .build();
    invalidResultWithoutStatus =
        ServiceNowImportSetTransformMapResult.builder().transformMap("").errorMessage(ERROR_MESSAGE).build();
    invalidResultWithoutTransformMap =
        ServiceNowImportSetTransformMapResult.builder().status(ERROR_STATUS).errorMessage(ERROR_MESSAGE).build();
  }

  @Test
  @Owner(developers = NAMANG)
  @Category(UnitTests.class)
  public void testPrepareImportSetStepResponse() throws Exception {
    ServiceNowTaskNGResponse serviceNowTaskNGResponse =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(ServiceNowImportSetResponseNG.builder()
                                               .importSet(IMPORT_SET)
                                               .stagingTable(STAGING_TABLE)
                                               .serviceNowImportSetTransformMapResultList(Arrays.asList(
                                                   normalResult, errorResult, errorResultWhenNoTransformMap))
                                               .build())
            .build();
    ServiceNowImportSetOutcome stepOutcome = (ServiceNowImportSetOutcome) serviceNowStepHelperService
                                                 .prepareImportSetStepResponse(() -> serviceNowTaskNGResponse)
                                                 .getStepOutcomes()
                                                 .iterator()
                                                 .next()
                                                 .getOutcome();
    assertThat(stepOutcome.getImportSetNumber()).isEqualTo(IMPORT_SET);
    assertThat(stepOutcome.getStagingTable()).isEqualTo(STAGING_TABLE);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getTargetRecordURL()).isEqualTo(RECORD_URL);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getTargetTable()).isEqualTo(TARGET_TABLE);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getTransformMap()).isEqualTo(TRANSFORM_MAP);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getStatus()).isEqualTo(STATUS);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getDisplayName()).isEqualTo(DISPLAY_NAME);
    assertThat(stepOutcome.getTransformMapOutcomes().get(0).getDisplayValue()).isEqualTo(DISPLAY_VALUE);

    assertThat(stepOutcome.getTransformMapOutcomes().get(1).getTransformMap()).isEqualTo(TRANSFORM_MAP);
    assertThat(stepOutcome.getTransformMapOutcomes().get(1).getStatus()).isEqualTo(ERROR_STATUS);
    assertThat(stepOutcome.getTransformMapOutcomes().get(1).getErrorMessage()).isEqualTo(ERROR_MESSAGE);
    assertThat(stepOutcome.getTransformMapOutcomes().get(1).getTargetTable()).isEqualTo(TARGET_TABLE);

    assertThat(stepOutcome.getTransformMapOutcomes().get(2).getTransformMap()).isEqualTo("");
    assertThat(stepOutcome.getTransformMapOutcomes().get(2).getStatus()).isEqualTo(ERROR_STATUS);
    assertThat(stepOutcome.getTransformMapOutcomes().get(2).getErrorMessage())
        .isEqualTo(ERROR_MESSAGE + ", please ensure that transform map is defined corresponding to the staging table");

    // when import set is missing
    ServiceNowTaskNGResponse serviceNowTaskNGResponse1 =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(ServiceNowImportSetResponseNG.builder()
                                               .stagingTable(STAGING_TABLE)
                                               .serviceNowImportSetTransformMapResultList(Arrays.asList(
                                                   normalResult, errorResult, errorResultWhenNoTransformMap))
                                               .build())
            .build();
    assertThatThrownBy(() -> serviceNowStepHelperService.prepareImportSetStepResponse(() -> serviceNowTaskNGResponse1))
        .isInstanceOf(ServiceNowException.class);
    // when staging table is missing
    ServiceNowTaskNGResponse serviceNowTaskNGResponse2 =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(ServiceNowImportSetResponseNG.builder()
                                               .importSet(IMPORT_SET)
                                               .serviceNowImportSetTransformMapResultList(Arrays.asList(
                                                   normalResult, errorResult, errorResultWhenNoTransformMap))
                                               .build())
            .build();
    assertThatThrownBy(() -> serviceNowStepHelperService.prepareImportSetStepResponse(() -> serviceNowTaskNGResponse2))
        .isInstanceOf(ServiceNowException.class);
    // invalid transform mao response
    ServiceNowTaskNGResponse serviceNowTaskNGResponse3 =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(ServiceNowImportSetResponseNG.builder()
                                               .importSet(IMPORT_SET)
                                               .stagingTable(STAGING_TABLE)
                                               .serviceNowImportSetTransformMapResultList(
                                                   Collections.singletonList(invalidResultWithoutTransformMap))
                                               .build())
            .build();
    assertThatThrownBy(() -> serviceNowStepHelperService.prepareImportSetStepResponse(() -> serviceNowTaskNGResponse3))
        .isInstanceOf(ServiceNowException.class);

    ServiceNowTaskNGResponse serviceNowTaskNGResponse4 =
        ServiceNowTaskNGResponse.builder()
            .serviceNowImportSetResponseNG(
                ServiceNowImportSetResponseNG.builder()
                    .importSet(IMPORT_SET)
                    .stagingTable(STAGING_TABLE)
                    .serviceNowImportSetTransformMapResultList(Collections.singletonList(invalidResultWithoutStatus))
                    .build())
            .build();
    assertThatThrownBy(() -> serviceNowStepHelperService.prepareImportSetStepResponse(() -> serviceNowTaskNGResponse4))
        .isInstanceOf(ServiceNowException.class);
  }
}
