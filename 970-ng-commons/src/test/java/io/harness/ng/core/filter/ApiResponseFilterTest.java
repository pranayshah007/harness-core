/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.filter;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.inject.matcher.Matchers;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.UriInfo;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class ApiResponseFilterTest extends CategoryTest {
  ApiResponseFilter apiResponseFilter = new ApiResponseFilter();

  @Test
  @Owner(developers = OwnerRule.VAIBHAV_SI)
  @Category(UnitTests.class)
  public void shouldPassThroughNonV1APIs() {
    ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
    ContainerResponseContext responseContext = mock(ContainerResponseContext.class);
    UriInfo uriInfo = mock(UriInfo.class);

    doReturn(uriInfo).when(requestContext).getUriInfo();
    doReturn("v2").when(uriInfo).getPath();
    apiResponseFilter.filter(requestContext, responseContext);
    verify(responseContext, times(0)).setEntity(Matchers.any());
  }
}
