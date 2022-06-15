/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing;

import static io.harness.rule.OwnerRule.AVMOHAN;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.mongo.IndexManager;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.common.base.VerifyException;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import java.time.Duration;
import lombok.val;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mongodb.morphia.Morphia;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.mock.env.MockEnvironment;

@RunWith(PowerMockRunner.class)
@PrepareForTest(IndexManager.class)
public class ApplicationReadyListenerTest extends CategoryTest {
  private ApplicationReadyListener listener;

  @Mock private HPersistence hPersistence;
  @Mock private TimeScaleDBService timeScaleDBService;
  @Mock private Morphia morphia;
  @Mock private IndexManager indexManager;
  @Mock private TimeLimiter timeLimiter;

  @Before
  public void setUp() throws Exception {
    MockEnvironment env = new MockEnvironment();
    listener = new ApplicationReadyListener(timeScaleDBService, hPersistence, morphia, indexManager, timeLimiter, env);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldFailIfTsdbNotConnectable() throws Exception {
    doReturn(false).when(timeScaleDBService).isValid();
    assertThatThrownBy(() -> listener.ensureTimescaleConnectivity())
        .withFailMessage("Unable to connect to timescale db")
        .isInstanceOf(VerifyException.class);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPassIfTsdbConnectable() throws Exception {
    doReturn(true).when(timeScaleDBService).isValid();
    assertThatCode(() -> listener.ensureTimescaleConnectivity()).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPassIfTsDbNotConnectableButEnsureTimescaleFalse() throws Exception {
    MockEnvironment env = new MockEnvironment();
    env.setProperty("ensure-timescale", "false");
    val listener =
        new ApplicationReadyListener(timeScaleDBService, hPersistence, morphia, indexManager, timeLimiter, env);
    assertThatCode(listener::ensureTimescaleConnectivity).doesNotThrowAnyException();
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldFailIfMongoConnectivityRuntimeError() throws Exception {
    doReturn(Duration.ofSeconds(5)).when(hPersistence).healthExpectedResponseTimeout();
    HTimeLimiterMocker.mockCallInterruptible(timeLimiter, ofSeconds(5)).thenThrow(new RuntimeException("unknown"));
    assertThatThrownBy(() -> listener.ensureMongoConnectivity())
        .withFailMessage("unknown")
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldFailIfMongoConnectivityTimeoutError() throws Exception {
    doReturn(Duration.ofSeconds(5)).when(hPersistence).healthExpectedResponseTimeout();
    HTimeLimiterMocker.mockCallInterruptible(timeLimiter, ofSeconds(5))
        .thenThrow(new UncheckedTimeoutException("timed out"));
    assertThatThrownBy(() -> listener.ensureMongoConnectivity())
        .withFailMessage("timed out")
        .isInstanceOf(UncheckedTimeoutException.class);
  }

  @Test
  @Owner(developers = AVMOHAN)
  @Category(UnitTests.class)
  public void shouldPassIfMongoConnectivityDoesNotThrow() throws Exception {
    doReturn(Duration.ofSeconds(5)).when(hPersistence).healthExpectedResponseTimeout();
    doNothing().when(hPersistence).isHealthy();
    HTimeLimiterMocker.mockCallInterruptible(timeLimiter, ofSeconds(5)).thenReturn(null);
    assertThatCode(() -> listener.ensureMongoConnectivity()).doesNotThrowAnyException();
  }
}
