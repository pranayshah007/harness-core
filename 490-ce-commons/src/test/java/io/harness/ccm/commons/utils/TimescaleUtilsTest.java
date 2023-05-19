/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.commons.utils;

import static io.harness.ccm.commons.utils.TimescaleUtils.retryRun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import com.google.common.collect.ImmutableSet;
import java.net.SocketException;
import java.util.concurrent.Callable;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class TimescaleUtilsTest extends CategoryTest {
  private static Integer count;
  private static final String CONNECTION_RESET = "Connection reset";
  private static final String INVALID_REQUEST = "Invalid request";

  @Mock private Callable<Integer> callable;

  @Before
  public void setup() {
    count = 0;
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testRetryRun() throws Exception {
    Callable<Integer> callable = () -> ++count;

    assertThat(count).isEqualTo(0);
    assertThat(retryRun(callable)).isEqualTo(1);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testRetriesThreeTimes() throws Exception {
    when(callable.call())
        .thenThrow(new SocketException(CONNECTION_RESET))
        .thenThrow(new SocketException(CONNECTION_RESET))
        .thenReturn(1);

    assertThat(retryRun(callable)).isEqualTo(1);

    verify(callable, times(3)).call();
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void testShouldThrowOn3rdExceptions() throws Exception {
    when(callable.call())
        .thenThrow(new SocketException(CONNECTION_RESET))
        .thenThrow(new SocketException(CONNECTION_RESET))
        .thenThrow(new InvalidRequestException(INVALID_REQUEST))
        .thenReturn(1);

    // also make sure that the exception we receive is the last one
    assertThatThrownBy(() -> retryRun(callable))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(INVALID_REQUEST);

    verify(callable, times(3)).call();
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void shouldRetryOnSelectedException() throws Exception {
    when(callable.call()).thenThrow(new SocketException(CONNECTION_RESET)).thenReturn(1);

    assertThat(retryRun(callable, 3, ImmutableSet.of(SocketException.class))).isEqualTo(1);

    verify(callable, times(2)).call();
  }

  @Test
  @Owner(developers = OwnerRule.UTSAV)
  @Category(UnitTests.class)
  public void shouldThrowOnUnknownException() throws Exception {
    when(callable.call()).thenThrow(new InvalidRequestException(INVALID_REQUEST)).thenReturn(1);

    assertThatThrownBy(() -> retryRun(callable, 3, ImmutableSet.of(SocketException.class)))
        .isExactlyInstanceOf(InvalidRequestException.class)
        .hasMessageContaining(INVALID_REQUEST);

    verify(callable, times(1)).call();
  }
}
