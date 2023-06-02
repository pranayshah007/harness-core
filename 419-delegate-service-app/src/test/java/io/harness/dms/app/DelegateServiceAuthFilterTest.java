/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.dms.app;

import static io.harness.agent.AgentGatewayConstants.HEADER_AGENT_MTLS_AUTHORITY;
import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.rule.OwnerRule.ANUPAM;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.service.intfc.DelegateAuthService;

import java.io.IOException;
import java.util.Arrays;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(DEL)
@RunWith(MockitoJUnitRunner.class)
public class DelegateServiceAuthFilterTest {
  private static final String FQDN = "agent.some-fqdn.harness.io";
  private static final String ACCOUNT_ID = "ACCOUNT_ID";

  private static final String JWT_TOKEN = "jwt";

  private static final String AUTH_HEADER = "Delegate " + JWT_TOKEN;

  private static final String DELEGATE_TOKEN_NAME = "defaultDelegateToken";

  @Mock DelegateAuthService delegateAuthService = mock(DelegateAuthService.class);

  @InjectMocks DelegateServiceAuthFilter authenticationFilter;

  ContainerRequestContext context = mock(ContainerRequestContext.class);

  @Before
  public void setUp() {
    authenticationFilter = new DelegateServiceAuthFilter(delegateAuthService);
    authenticationFilter = spy(authenticationFilter);
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn(AUTH_HEADER);
    when(context.getHeaderString("delegateId")).thenReturn("delegateId");
    when(context.getHeaderString("delegateTokenName")).thenReturn(DELEGATE_TOKEN_NAME);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testDelegateRequestAuthentication() throws IOException {
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Delegate jwt");
    doReturn(true).when(authenticationFilter).delegateAPI();
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    doNothing()
        .when(delegateAuthService)
        .validateDelegateToken(null, JWT_TOKEN, "delegateId", DELEGATE_TOKEN_NAME, null, true);
    UriInfo uriInfo = mock(UriInfo.class);
    when(uriInfo.getPathParameters()).thenReturn(new MultivaluedHashMap<>());
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    when(context.getUriInfo()).thenReturn(uriInfo);
    authenticationFilter.filter(context);
    verify(delegateAuthService, times(1))
        .validateDelegateToken(null, JWT_TOKEN, "delegateId", DELEGATE_TOKEN_NAME, null, true);
  }

  @Test
  @Owner(developers = ANUPAM)
  @Category(UnitTests.class)
  public void testDelegateRequestAuthenticationWithMtls() throws IOException {
    when(context.getHeaderString(HttpHeaders.AUTHORIZATION)).thenReturn("Delegate jwt");
    doReturn(false).when(authenticationFilter).authenticationExemptedRequests(any(ContainerRequestContext.class));
    when(context.getHeaderString(HEADER_AGENT_MTLS_AUTHORITY)).thenReturn(FQDN);
    doReturn(true).when(authenticationFilter).delegateAPI();
    doNothing()
        .when(delegateAuthService)
        .validateDelegateToken(ACCOUNT_ID, JWT_TOKEN, "delegateId", DELEGATE_TOKEN_NAME, FQDN, true);
    UriInfo uriInfo = mock(UriInfo.class);
    MultivaluedHashMap<String, String> queryParams = new MultivaluedHashMap<>();
    queryParams.put("accountId", Arrays.asList(ACCOUNT_ID));
    when(uriInfo.getPathParameters()).thenReturn(queryParams);
    when(uriInfo.getQueryParameters()).thenReturn(new MultivaluedHashMap<>());
    when(context.getUriInfo()).thenReturn(uriInfo);
    authenticationFilter.filter(context);
    verify(delegateAuthService, times(1))
        .validateDelegateToken(ACCOUNT_ID, JWT_TOKEN, "delegateId", DELEGATE_TOKEN_NAME, FQDN, true);
  }
}
