/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security;

import static io.harness.rule.OwnerRule.RAMA;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.WingsException;
import io.harness.rule.Owner;

import software.wings.beans.AuthToken;
import software.wings.beans.User;
import software.wings.service.intfc.AuthService;

import java.net.URI;
import java.util.Arrays;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class AuthResponseFilterTest extends CategoryTest {
  @Mock AuthService authService = mock(AuthService.class);
  @InjectMocks AuthResponseFilter authResponseFilter;

  @Before
  public void setUp() throws IllegalAccessException {
    authResponseFilter = new AuthResponseFilter();
    FieldUtils.writeField(authResponseFilter, "authService", authService, true);
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testUpdateCustomDashboard() {
    testCustomDashboardActions("PUT");
  }

  @Test
  @Owner(developers = RAMA)
  @Category(UnitTests.class)
  public void testDeleteCustomDashboard() {
    testCustomDashboardActions("DELETE");
  }

  private void testCustomDashboardActions(String httpMethod) {
    try {
      ContainerRequestContext requestContext = mock(ContainerRequestContext.class);
      ContainerResponseContext responseContext = mock(ContainerResponseContext.class);

      UriInfo uriInfo = mock(UriInfo.class);
      URI mockURI = URI.create("/api/custom-dashboard");
      MultivaluedMap<String, String> parameters = new MultivaluedHashMap();
      parameters.put("accountId", Arrays.asList(ACCOUNT_ID));
      when(uriInfo.getQueryParameters()).thenReturn(parameters);
      when(uriInfo.getAbsolutePath()).thenReturn(mockURI);
      when(requestContext.getUriInfo()).thenReturn(uriInfo);
      when(requestContext.getMethod()).thenReturn(httpMethod);

      AuthToken authToken = new AuthToken(ACCOUNT_ID, "testUser", 0L);
      authToken.setUser(mock(User.class));
      when(authService.validateToken(anyString())).thenReturn(authToken);

      authResponseFilter.filter(requestContext, responseContext);
      verify(authService).evictUserPermissionCacheForAccount(anyString(), anyBoolean());
    } catch (WingsException e) {
      fail(e.getMessage(), WingsException.class);
    }
  }
}
