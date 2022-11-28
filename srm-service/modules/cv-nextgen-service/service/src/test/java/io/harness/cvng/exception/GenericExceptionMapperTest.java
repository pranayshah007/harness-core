/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.exception;

import static io.harness.rule.OwnerRule.NEMANJA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.ExposeInternalException;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;

import io.swagger.annotations.Api;
import javax.ws.rs.GET;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class GenericExceptionMapperTest extends CategoryTest {
  private GenericExceptionMapper<?> genericExceptionMapper;
  @Mock private ResourceInfo resourceInfo;
  @Before
  public void setup() throws IllegalAccessException {
    initMocks(this);
    genericExceptionMapper = new GenericExceptionMapper<>();
    FieldUtils.writeField(genericExceptionMapper, "resourceInfo", resourceInfo, true);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void toResponse_withExposeExceptionAnnotationAndWhenResourceInfoNull()
      throws NoSuchMethodException, IllegalAccessException {
    FieldUtils.writeField(genericExceptionMapper, "resourceInfo", null, true);
    RestResponse<?> restResponse = new RestResponse<>();
    restResponse.getResponseMessages().add(
        ResponseMessage.builder()
            .code(ErrorCode.DEFAULT_ERROR_CODE)
            .level(Level.ERROR)
            .message("An error has occurred. Please contact the Harness support team.")
            .build());
    testExposeException(ResourceWithAnnotation.class, new RuntimeException("exception"), restResponse);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void toResponse_withoutExposeExceptionAnnotation() throws NoSuchMethodException {
    RestResponse<?> restResponse = new RestResponse<>();
    restResponse.getResponseMessages().add(
        ResponseMessage.builder()
            .code(ErrorCode.DEFAULT_ERROR_CODE)
            .level(Level.ERROR)
            .message("An error has occurred. Please contact the Harness support team.")
            .build());
    testExposeException(ResourceWithoutAnnotation.class, new RuntimeException("exception"), restResponse);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void toResponse_withExposeExceptionAnnotationOnType() throws NoSuchMethodException {
    RestResponse<?> restResponse = new RestResponse<>();
    restResponse.getResponseMessages().add(ResponseMessage.builder()
                                               .code(ErrorCode.DEFAULT_ERROR_CODE)
                                               .level(Level.ERROR)
                                               .message("java.lang.RuntimeException: exception from test")
                                               .build());
    testExposeException(ResourceWithAnnotation.class, new RuntimeException("exception from test"), restResponse);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void toResponse_withExposeExceptionAnnotationOnTypeWithStackTrace() throws NoSuchMethodException {
    Throwable exception = new RuntimeException("exception from test");
    RestResponse<?> restResponse = new RestResponse<>();
    restResponse.getResponseMessages().add(ResponseMessage.builder()
                                               .code(ErrorCode.DEFAULT_ERROR_CODE)
                                               .level(Level.ERROR)
                                               .exception(exception)
                                               .message("java.lang.RuntimeException: exception from test")
                                               .build());
    testExposeException(ResourceWithAnnotationStackTraceEnabled.class, exception, restResponse);
  }

  @Test
  @Owner(developers = NEMANJA)
  @Category(UnitTests.class)
  public void toResponse_withExposeExceptionAnnotationOnMethodWithStackTrace() throws NoSuchMethodException {
    Throwable exception = new RuntimeException("exception from test");
    RestResponse<?> expectedRestResponse = new RestResponse<>();
    expectedRestResponse.getResponseMessages().add(ResponseMessage.builder()
                                                       .code(ErrorCode.DEFAULT_ERROR_CODE)
                                                       .level(Level.ERROR)
                                                       .exception(exception)
                                                       .message("java.lang.RuntimeException: exception from test")
                                                       .build());
    testExposeException(ResourceWithAnnotationOnMethod.class, exception, expectedRestResponse);
  }

  public void testExposeException(Class resourceClass, Throwable exception, RestResponse<?> expectation)
      throws NoSuchMethodException {
    when(resourceInfo.getResourceClass()).thenReturn(resourceClass);
    when(resourceInfo.getResourceMethod()).thenReturn(resourceClass.getMethod("method"));
    Response response = genericExceptionMapper.toResponse(exception);
    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(response.getMediaType().toString()).isEqualTo(MediaType.APPLICATION_JSON);
    assertThat(((RestResponse) response.getEntity()).getResponseMessages())
        .isEqualTo(expectation.getResponseMessages());
  }

  @Api
  private class ResourceWithoutAnnotation {
    @GET
    public void method() {}
  }

  @Api
  @ExposeInternalException
  private class ResourceWithAnnotation {
    @GET
    public void method() {}
  }

  @Api
  @ExposeInternalException(withStackTrace = true)
  private class ResourceWithAnnotationStackTraceEnabled {
    @GET
    public void method() {}
  }

  @Api
  private class ResourceWithAnnotationOnMethod {
    @GET
    @ExposeInternalException(withStackTrace = true)
    public void method() {}
  }
}
