/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.instrumentation;

import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static software.wings.security.AuthenticationFilter.API_KEY_HEADER;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import software.wings.audit.ApiKeyAuditDetails;
import software.wings.audit.AuditHeader;
import software.wings.beans.ApiKeyEntry;
import software.wings.common.AuditHelper;
import software.wings.graphql.utils.GraphQLConstants;
import software.wings.service.intfc.ApiKeyService;

import com.google.common.collect.ImmutableList;
import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLContext;
import graphql.execution.ExecutionContext;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.language.OperationDefinition;
import java.util.Collections;
import javax.servlet.http.HttpServletRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class QLAuditInstrumentationTest extends CategoryTest {
  @Mock private AuditHelper auditHelper;
  @Mock private ApiKeyService apiKeyService;
  @InjectMocks @Spy QLAuditInstrumentation qlAuditInstrumentation = new QLAuditInstrumentation();

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }
  private static class Mocks {
    ExecutionContext mockExecutionContext;
    GraphQLContext mockGraphQLContext;
    InstrumentationExecuteOperationParameters mockOperationParameters;
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_beginExecuteOperation() {
    final Mocks mockParams = getMocks("createapp", OperationDefinition.Operation.MUTATION);
    final HttpServletRequest mockHttpServletRequest = mock(HttpServletRequest.class);
    doReturn(new StringBuffer("https://app.harness.io/graphql")).when(mockHttpServletRequest).getRequestURL();
    doReturn(Collections.enumeration(ImmutableList.of("Authorization", "Cookies", API_KEY_HEADER)))
        .when(mockHttpServletRequest)
        .getHeaderNames();
    doReturn("cookie value").when(mockHttpServletRequest).getHeader("Cookies");
    doReturn("api_key_value").when(mockHttpServletRequest).getHeader(API_KEY_HEADER);
    doReturn("Bearer 12njdjksbkn").when(mockHttpServletRequest).getHeader("Authorization");
    final AuditHeader auditHeader = AuditHeader.Builder.anAuditHeader().build();
    doReturn(auditHeader).when(auditHelper).create(any(AuditHeader.class));

    final GraphQLContext graphQLContext = mockParams.mockGraphQLContext;

    graphQLContext.put(GraphQLConstants.HTTP_SERVLET_REQUEST, mockHttpServletRequest);
    graphQLContext.put(GraphQLConstants.GRAPHQL_QUERY_STRING,
        "mutation createapp{"
            + "}");
    graphQLContext.put("accountId", "accountid1");
    doReturn(ApiKeyEntry.builder().uuid("api_key_uuid_value").build())
        .when(apiKeyService)
        .getByKey("api_key_value", "accountid1");
    final InstrumentationContext<ExecutionResult> executionResultInstrumentationContext =
        qlAuditInstrumentation.beginExecuteOperation(mockParams.mockOperationParameters);

    assertThat(executionResultInstrumentationContext).isNotNull();
    verify(apiKeyService, times(1)).getByKey("api_key_value", "accountid1");

    final ArgumentCaptor<AuditHeader> auditHeaderArgumentCaptor = ArgumentCaptor.forClass(AuditHeader.class);
    verify(auditHelper, times(1)).create(auditHeaderArgumentCaptor.capture());
    final AuditHeader capturedAuditHeader = auditHeaderArgumentCaptor.getValue();
    final String headerString = capturedAuditHeader.getHeaderString();
    final ApiKeyAuditDetails apiKeyAuditDetails = capturedAuditHeader.getApiKeyAuditDetails();
    assertThat(headerString).contains("Authorization=********");
    assertThat(headerString).doesNotContain("api_key_value", "Bearer 12njdjksbkn");
    if (apiKeyAuditDetails != null) {
      assertThat(apiKeyAuditDetails.getApiKeyId()).isEqualTo("api_key_uuid_value");
    }
    doReturn(auditHeader).when(auditHelper).get();
    doNothing().when(auditHelper).finalizeAudit(any(AuditHeader.class), any());
    executionResultInstrumentationContext.onCompleted(
        new ExecutionResultImpl("{\"app\":{id:\"dddd\"}}", Collections.emptyList()), null);
    verify(auditHelper, times(1)).get();
    verify(auditHelper, times(1)).finalizeAudit(any(AuditHeader.class), any());
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_handleRequestCompletion() {
    final AuditHeader auditHeader = AuditHeader.Builder.anAuditHeader().build();

    doReturn(auditHeader).when(auditHelper).get();
    doNothing().when(auditHelper).finalizeAudit(any(AuditHeader.class), any());
    qlAuditInstrumentation.handleRequestCompletion(null, new RuntimeException("error"));
    verify(auditHelper, times(1)).get();
    verify(auditHelper, times(1)).finalizeAudit(any(AuditHeader.class), any());
  }

  private Mocks getMocks(String operationName, OperationDefinition.Operation operation) {
    final Mocks mocks = new Mocks();
    final ExecutionContext mockExecutionContext = mock(ExecutionContext.class);
    doReturn(new OperationDefinition(operationName, operation)).when(mockExecutionContext).getOperationDefinition();
    final GraphQLContext graphQLContext = spy(GraphQLContext.newContext().build());
    doReturn(graphQLContext).when(mockExecutionContext).getContext();
    final InstrumentationExecuteOperationParameters mockParams = mock(InstrumentationExecuteOperationParameters.class);
    doReturn(mockExecutionContext).when(mockParams).getExecutionContext();
    mocks.mockExecutionContext = mockExecutionContext;
    mocks.mockGraphQLContext = graphQLContext;
    mocks.mockOperationParameters = mockParams;
    return mocks;
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_shouldAudit() {
    assertThat(qlAuditInstrumentation.shouldAudit(
                   getMocks("getApp", OperationDefinition.Operation.QUERY).mockOperationParameters))
        .isFalse();

    assertThat(qlAuditInstrumentation.shouldAudit(
                   getMocks("createApp", OperationDefinition.Operation.MUTATION).mockOperationParameters))
        .isTrue();

    assertThat(qlAuditInstrumentation.shouldAudit(
                   getMocks("subscription", OperationDefinition.Operation.SUBSCRIPTION).mockOperationParameters))
        .isFalse();
  }
}
