/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.filter;

import static io.harness.annotations.dev.HarnessTeam.SPG;
import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.category.element.UnitTests;
import io.harness.data.structure.UUIDGenerator;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.exception.AccountMigratedException;
import software.wings.service.intfc.ApiKeyService;
import software.wings.utils.JerseyFilterUtils;

import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(SPG)
@RunWith(MockitoJUnitRunner.class)
public class DisableFirstGenFilterTest {
  private static final String TEST_ACCOUNT_ID = UUIDGenerator.generateUuid();

  @InjectMocks private DisableFirstGenFilter filter;

  @Mock private FeatureFlagService ffService;
  @Mock private ApiKeyService apiKeyService;
  @Mock private ResourceInfo resourceInfo;

  @Mock private ContainerRequestContext requestContext;
  @Mock private UriInfo uriInfo;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSkipWhenMissingAccountId() {
    prepareExecution(null, true);

    assertThatCode(() -> filter.filter(requestContext)).doesNotThrowAnyException();

    verify(ffService, never()).isEnabled(FeatureName.CDS_DISABLE_FIRST_GEN_CD, TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSkipWhenIsDelegateRequest() {
    prepareExecution("accountId", true);

    try (MockedStatic<JerseyFilterUtils> mockedStatic = mockStatic(JerseyFilterUtils.class)) {
      mockedStatic.when(() -> JerseyFilterUtils.isDelegateRequest(requestContext, resourceInfo)).thenReturn(true);
      mockedStatic.when(() -> JerseyFilterUtils.isNextGenManagerRequest(resourceInfo)).thenReturn(false);

      assertThatCode(() -> filter.filter(requestContext)).doesNotThrowAnyException();
    }

    verify(ffService, never()).isEnabled(FeatureName.CDS_DISABLE_FIRST_GEN_CD, TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldSkipWhenIsNextGenManagerRequest() {
    prepareExecution("accountId", true);

    try (MockedStatic<JerseyFilterUtils> mockedStatic = mockStatic(JerseyFilterUtils.class)) {
      mockedStatic.when(() -> JerseyFilterUtils.isDelegateRequest(requestContext, resourceInfo)).thenReturn(false);
      mockedStatic.when(() -> JerseyFilterUtils.isNextGenManagerRequest(resourceInfo)).thenReturn(true);

      assertThatCode(() -> filter.filter(requestContext)).doesNotThrowAnyException();
    }

    verify(ffService, never()).isEnabled(FeatureName.CDS_DISABLE_FIRST_GEN_CD, TEST_ACCOUNT_ID);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldIdentifyMigratedAccount() {
    prepareExecution("accountId", false);

    try (MockedStatic<JerseyFilterUtils> mockedStatic = mockStatic(JerseyFilterUtils.class)) {
      mockedStatic.when(() -> JerseyFilterUtils.isDelegateRequest(requestContext, resourceInfo)).thenReturn(false);
      mockedStatic.when(() -> JerseyFilterUtils.isNextGenManagerRequest(resourceInfo)).thenReturn(false);

      assertThatCode(() -> filter.filter(requestContext))
          .isInstanceOf(WebApplicationException.class)
          .getCause()
          .isInstanceOf(AccountMigratedException.class);
    }
  }

  private void prepareExecution(String parameterName, boolean skipFF) {
    MultivaluedMap<String, String> queryParameters = new MultivaluedHashMap<>();
    MultivaluedMap<String, String> pathParameters = new MultivaluedHashMap<>();
    if (parameterName != null) {
      queryParameters.put(parameterName, List.of(TEST_ACCOUNT_ID));
    }

    when(requestContext.getUriInfo()).thenReturn(uriInfo);
    when(uriInfo.getQueryParameters()).thenReturn(queryParameters);
    when(uriInfo.getPathParameters()).thenReturn(pathParameters);

    if (!skipFF) {
      when(ffService.isEnabled(FeatureName.CDS_DISABLE_FIRST_GEN_CD, TEST_ACCOUNT_ID)).thenReturn(true);
    }
  }
}
