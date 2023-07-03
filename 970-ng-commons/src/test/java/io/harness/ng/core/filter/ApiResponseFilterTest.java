/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.filter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.eraro.ErrorCode;
import io.harness.ng.core.Status;
import io.harness.ng.core.dto.ErrorDTO;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;
import io.harness.spec.server.commons.v1.model.ErrorResponse;

import com.google.inject.matcher.Matchers;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class ApiResponseFilterTest extends CategoryTest {
  ApiResponseFilter apiResponseFilter;
  @Mock private ContainerRequestContext requestContext;
  @Mock private ContainerResponseContext responseContext;
  @Mock private UriInfo uriInfo;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    apiResponseFilter = new ApiResponseFilter();
  }

  @Test
  @Owner(developers = OwnerRule.MANKRIT)
  @Category(UnitTests.class)
  public void checkResolveMessageUnauthorizedDefault() {
    doReturn("v1").when(uriInfo).getPath();
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(responseContext.getStatus()).thenReturn(401);
    ErrorDTO errorDTO = ErrorDTO.newError(Status.FAILURE, ErrorCode.USER_NOT_AUTHORIZED, "Unauthorized message");
    when(responseContext.getEntity()).thenReturn(errorDTO);

    ArgumentCaptor<ErrorResponse> captor = ArgumentCaptor.forClass(ErrorResponse.class);
    doNothing().when(responseContext).setEntity(captor.capture());

    apiResponseFilter.filter(requestContext, responseContext);

    ErrorResponse errorResponse = captor.getValue();
    assertEquals("Unauthorized message", errorResponse.getMessage());
  }

  @Test
  @Owner(developers = OwnerRule.MANKRIT)
  @Category(UnitTests.class)
  public void checkResolveMessageUnauthorized() {
    doReturn("v1").when(uriInfo).getPath();
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(responseContext.getStatus()).thenReturn(401);
    ErrorDTO errorDTO = ErrorDTO.newError(Status.FAILURE, ErrorCode.USER_NOT_AUTHORIZED, "");
    when(responseContext.getEntity()).thenReturn(errorDTO);

    ArgumentCaptor<ErrorResponse> captor = ArgumentCaptor.forClass(ErrorResponse.class);
    doNothing().when(responseContext).setEntity(captor.capture());

    apiResponseFilter.filter(requestContext, responseContext);

    ErrorResponse errorResponse = captor.getValue();
    assertEquals("Unauthorized.", errorResponse.getMessage());
  }

  @Test
  @Owner(developers = OwnerRule.MANKRIT)
  @Category(UnitTests.class)
  public void checkResolveMessageForbidden() {
    doReturn("v1").when(uriInfo).getPath();
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(responseContext.getStatus()).thenReturn(403);
    ErrorDTO errorDTO = ErrorDTO.newError(Status.FAILURE, ErrorCode.NG_ACCESS_DENIED, "");
    when(responseContext.getEntity()).thenReturn(errorDTO);

    ArgumentCaptor<ErrorResponse> captor = ArgumentCaptor.forClass(ErrorResponse.class);
    doNothing().when(responseContext).setEntity(captor.capture());

    apiResponseFilter.filter(requestContext, responseContext);

    ErrorResponse errorResponse = captor.getValue();
    assertEquals("Forbidden Request.", errorResponse.getMessage());
  }

  @Test
  @Owner(developers = OwnerRule.MANKRIT)
  @Category(UnitTests.class)
  public void checkResolveMessageNotFound() {
    doReturn("v1").when(uriInfo).getPath();
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(responseContext.getStatus()).thenReturn(404);
    ErrorDTO errorDTO = ErrorDTO.newError(Status.FAILURE, ErrorCode.ENTITY_NOT_FOUND, "");
    when(responseContext.getEntity()).thenReturn(errorDTO);

    ArgumentCaptor<ErrorResponse> captor = ArgumentCaptor.forClass(ErrorResponse.class);
    doNothing().when(responseContext).setEntity(captor.capture());

    apiResponseFilter.filter(requestContext, responseContext);

    ErrorResponse errorResponse = captor.getValue();
    assertEquals("Not Found.", errorResponse.getMessage());
  }

  @Test
  @Owner(developers = OwnerRule.MANKRIT)
  @Category(UnitTests.class)
  public void checkResolveMessageUnsupportedMediaType() {
    doReturn("v1").when(uriInfo).getPath();
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(responseContext.getStatus()).thenReturn(415);
    ErrorDTO errorDTO = ErrorDTO.newError(Status.FAILURE, ErrorCode.MEDIA_NOT_SUPPORTED, "");
    when(responseContext.getEntity()).thenReturn(errorDTO);

    ArgumentCaptor<ErrorResponse> captor = ArgumentCaptor.forClass(ErrorResponse.class);
    doNothing().when(responseContext).setEntity(captor.capture());

    apiResponseFilter.filter(requestContext, responseContext);

    ErrorResponse errorResponse = captor.getValue();
    assertEquals("Unsupported Media Type.", errorResponse.getMessage());
  }

  @Test
  @Owner(developers = OwnerRule.MANKRIT)
  @Category(UnitTests.class)
  public void checkResolveMessageError() {
    doReturn("v1").when(uriInfo).getPath();
    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(responseContext.getStatus()).thenReturn(500);
    ErrorDTO errorDTO = ErrorDTO.newError(Status.ERROR, ErrorCode.DEFAULT_ERROR_CODE, "");
    when(responseContext.getEntity()).thenReturn(errorDTO);

    ArgumentCaptor<ErrorResponse> captor = ArgumentCaptor.forClass(ErrorResponse.class);
    doNothing().when(responseContext).setEntity(captor.capture());

    apiResponseFilter.filter(requestContext, responseContext);

    ErrorResponse errorResponse = captor.getValue();
    assertEquals("Oops, something went wrong on our end. Please contact Harness Support.", errorResponse.getMessage());
  }

  @Test
  @Owner(developers = OwnerRule.VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldPassThroughNonV1APIs() {
    doReturn(uriInfo).when(requestContext).getUriInfo();
    doReturn("v2").when(uriInfo).getPath();
    apiResponseFilter.filter(requestContext, responseContext);
    verify(responseContext, times(0)).setEntity(Matchers.any());
  }
}
