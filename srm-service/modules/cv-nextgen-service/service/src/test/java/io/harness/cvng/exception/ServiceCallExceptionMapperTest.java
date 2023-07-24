/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.exception;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.client.ServiceCallException;
import io.harness.cvng.exception.mapper.ServiceCallExceptionMapper;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.ResponseMessage;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.rule.Owner;

import java.util.Collections;
import javax.ws.rs.core.Response;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ServiceCallExceptionMapperTest extends CategoryTest {
  private ServiceCallExceptionMapper serviceCallExceptionMapper;

  @Before
  public void setup() {
    serviceCallExceptionMapper = new ServiceCallExceptionMapper();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testToResponse_withDataCollectionException() {
    ResponseMessage responseMessage =
        ResponseMessage.builder().message("error message").code(ErrorCode.DATA_COLLECTION_ERROR).build();
    ServiceCallException serviceCallException = new ServiceCallException(responseMessage.getCode(), 400,
        responseMessage.getMessage(), "error body", Collections.singletonList(responseMessage));
    Response response = serviceCallExceptionMapper.toResponse(serviceCallException);
    assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(ErrorDTO.class);
    ErrorDTO errorDTO = (ErrorDTO) response.getEntity();
    assertThat(errorDTO.getCode()).isEqualTo(ErrorCode.DATA_COLLECTION_ERROR);
    assertThat(errorDTO.getMessage()).isEqualTo("ServiceCallException: Response code: 400, Message: error message");
    assertThat(errorDTO.getMetadata()).isNull();
    assertThat(errorDTO.getStatus()).isEqualTo(Status.ERROR);
    assertThat(errorDTO.getResponseMessages()).hasSize(1);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testToResponse() {
    ResponseMessage responseMessage =
        ResponseMessage.builder().message("error message").code(ErrorCode.UNKNOWN_ERROR).build();
    ServiceCallException serviceCallException = new ServiceCallException(responseMessage.getCode(), 500,
        responseMessage.getMessage(), "error body", Collections.singletonList(responseMessage));
    Response response = serviceCallExceptionMapper.toResponse(serviceCallException);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(ErrorDTO.class);
    ErrorDTO errorDTO = (ErrorDTO) response.getEntity();
    assertThat(errorDTO.getCode()).isEqualTo(ErrorCode.UNKNOWN_ERROR);
    assertThat(errorDTO.getMessage()).isEqualTo("ServiceCallException: Response code: 500, Message: error message");
    assertThat(errorDTO.getMetadata()).isNull();
    assertThat(errorDTO.getStatus()).isEqualTo(Status.ERROR);
    assertThat(errorDTO.getResponseMessages()).hasSize(1);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testToResponse_withNoErrorCode() {
    ServiceCallException serviceCallException = new ServiceCallException(400, "error message", "error body");
    Response response = serviceCallExceptionMapper.toResponse(serviceCallException);
    assertThat(response.getStatus()).isEqualTo(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode());
    assertThat(response.getEntity()).isInstanceOf(ErrorDTO.class);
    ErrorDTO errorDTO = (ErrorDTO) response.getEntity();
    assertThat(errorDTO.getCode()).isEqualTo(ErrorCode.UNKNOWN_ERROR);
    assertThat(errorDTO.getMessage()).isEqualTo("ServiceCallException: Response code: 400, Message: error message");
    assertThat(errorDTO.getMetadata()).isNull();
    assertThat(errorDTO.getStatus()).isEqualTo(Status.ERROR);
    assertThat(errorDTO.getResponseMessages()).isNull();
  }
}
