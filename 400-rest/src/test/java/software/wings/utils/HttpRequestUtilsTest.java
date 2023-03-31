/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;

import static io.harness.rule.OwnerRule.FERNANDOD;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import javax.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.SPG)
public class HttpRequestUtilsTest {
  @Mock private HttpServletRequest request;

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHasApiKeyNotAcceptNullRequest() {
    Assertions.assertThatCode(() -> HttpRequestUtils.hasApiKey(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHasApiKeyFalseWhenValueIsEmpty() {
    when(request.getHeader(HttpRequestUtils.X_API_KEY)).thenReturn("");
    assertThat(HttpRequestUtils.hasApiKey(request)).isFalse();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHasApiKeyFalseWhenValueIsNull() {
    when(request.getHeader(HttpRequestUtils.X_API_KEY)).thenReturn(null);
    assertThat(HttpRequestUtils.hasApiKey(request)).isFalse();
  }

  // CURRENT RULE FROM AuthRuleFilter USES EmptyPredicate.isEmpty
  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHasApiKeyTrueWithSpaceContent() {
    when(request.getHeader(HttpRequestUtils.X_API_KEY)).thenReturn("   ");
    assertThat(HttpRequestUtils.hasApiKey(request)).isTrue();
  }

  @Test
  @Owner(developers = FERNANDOD)
  @Category(UnitTests.class)
  public void shouldHasApiKeyTrue() {
    when(request.getHeader(HttpRequestUtils.X_API_KEY)).thenReturn("any-content");
    assertThat(HttpRequestUtils.hasApiKey(request)).isTrue();
  }
}
