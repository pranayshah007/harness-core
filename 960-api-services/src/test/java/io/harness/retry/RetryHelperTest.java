/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.retry;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.UUIDGenerator.generateUuid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;
import io.harness.rule.OwnerRule;

import io.github.resilience4j.retry.Retry;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

@OwnedBy(CDP)
public class RetryHelperTest extends CategoryTest {
  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testRetrySuccess() {
    final HelloWorldService helloWorldService = Mockito.mock(HelloWorldService.class);
    doThrow(new BaseException("exception")).doCallRealMethod().when(helloWorldService).sayHelloWorld();

    final Retry retry = RetryHelper.getExponentialRetry(generateUuid(), new Class[] {BaseException.class});
    final Supplier<String> stringSupplier = Retry.decorateSupplier(retry, () -> helloWorldService.sayHelloWorld());

    assertThat(stringSupplier.get()).isEqualTo("hello-world");
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testRetryNotNeeded() {
    final HelloWorldService helloWorldService = new HelloWorldService();
    final Retry retry = RetryHelper.getExponentialRetry(generateUuid(), new Class[] {Throwable.class});
    final Supplier<String> stringSupplier = Retry.decorateSupplier(retry, () -> helloWorldService.sayHelloWorld());

    assertThat(stringSupplier.get()).isEqualTo("hello-world");
    assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithoutRetryAttempt()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testRetrySuccessOnCauseMatch() {
    final HelloWorldService helloWorldService = Mockito.mock(HelloWorldService.class);
    doThrow(new BaseException("exception", new SocketException("io")))
        .doCallRealMethod()
        .when(helloWorldService)
        .sayHelloWorld();

    final Retry retry = RetryHelper.getExponentialRetry(generateUuid(), new Class[] {IOException.class});
    final Supplier<String> stringSupplier = Retry.decorateSupplier(retry, () -> helloWorldService.sayHelloWorld());

    assertThat(stringSupplier.get()).isEqualTo("hello-world");
    assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testRetrySuccessOnExceptionSubClassMatch() {
    final HelloWorldService helloWorldService = Mockito.mock(HelloWorldService.class);
    doThrow(new ExampleException("exception", new IOException()))
        .doCallRealMethod()
        .when(helloWorldService)
        .sayHelloWorld();

    final Retry retry = RetryHelper.getExponentialRetry(generateUuid(), new Class[] {BaseException.class});
    final Supplier<String> stringSupplier = Retry.decorateSupplier(retry, () -> helloWorldService.sayHelloWorld());

    assertThat(stringSupplier.get()).isEqualTo("hello-world");
    assertThat(retry.getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testRetryFailureOnExceptionNotMatched() {
    final HelloWorldService helloWorldService = Mockito.mock(HelloWorldService.class);
    doThrow(new BaseException("exception", new IOException()))
        .doCallRealMethod()
        .when(helloWorldService)
        .sayHelloWorld();

    final Retry retry = RetryHelper.getExponentialRetry(generateUuid(), new Class[] {ConnectException.class});
    final Supplier<String> stringSupplier = Retry.decorateSupplier(retry, () -> helloWorldService.sayHelloWorld());

    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> stringSupplier.get());
    assertThat(retry.getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(1);
  }

  @Test
  @Owner(developers = OwnerRule.YOGESH)
  @Category(UnitTests.class)
  public void testRetryFailureOnConsistentExceptions() {
    final HelloWorldService helloWorldService = Mockito.mock(HelloWorldService.class);
    doThrow(new BaseException("exception", new IOException())).when(helloWorldService).sayHelloWorld();

    final Retry retry = RetryHelper.getExponentialRetry(generateUuid(), new Class[] {BaseException.class});
    final Supplier<String> stringSupplier = Retry.decorateSupplier(retry, () -> helloWorldService.sayHelloWorld());

    assertThatExceptionOfType(BaseException.class).isThrownBy(() -> stringSupplier.get());
    assertThat(retry.getMetrics().getNumberOfFailedCallsWithRetryAttempt()).isEqualTo(1);
  }

  private class BaseException extends RuntimeException {
    BaseException(String msg) {
      super(msg);
    }
    BaseException(String msg, Throwable t) {
      super(msg, t);
    }
  }

  private class ExampleException extends BaseException {
    ExampleException(String msg, Throwable t) {
      super(msg, t);
    }
  }

  private class HelloWorldService {
    String sayHelloWorld() {
      return "hello-world";
    }
  }
}
