/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.scheduler;

import static io.harness.rule.OwnerRule.ROHIT_KUMAR;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.rule.Owner;

import software.wings.service.intfc.instance.stats.collector.StatsCollector;

import com.google.inject.Inject;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class InstanceStatsCollectorJobTest extends CategoryTest {
  public static final String ACCOUNTID = "accountid";
  public static final String APPID_1 = "appid1";

  @Mock private PersistentLocker persistentLocker;
  @Mock private StatsCollector statsCollector;

  @InjectMocks @Inject @Spy InstanceStatsCollectorJob instanceStatsCollectorJob;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }
  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_createStats() {
    doReturn(mock(AcquiredLock.class))
        .when(persistentLocker)
        .waitToAcquireLock(any(Class.class), anyString(), any(Duration.class), any(Duration.class));
    doReturn(true).when(statsCollector).createStats(anyString());
    doReturn(true).when(statsCollector).createServerlessStats(anyString());
    instanceStatsCollectorJob.createStats(ACCOUNTID);
    verify(statsCollector, times(1)).createServerlessStats(ACCOUNTID);
    verify(statsCollector, times(1)).createStats(ACCOUNTID);
  }

  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_createStats_disabled() {
    doReturn(mock(AcquiredLock.class))
        .when(persistentLocker)
        .waitToAcquireLock(any(Class.class), anyString(), any(Duration.class), any(Duration.class));
    doReturn(false).when(statsCollector).createStats(anyString());
    doReturn(false).when(statsCollector).createServerlessStats(anyString());
    instanceStatsCollectorJob.createStats(ACCOUNTID);
    verify(statsCollector, times(1)).createServerlessStats(ACCOUNTID);
    verify(statsCollector, times(1)).createStats(ACCOUNTID);
  }
  @Test
  @Owner(developers = ROHIT_KUMAR)
  @Category(UnitTests.class)
  public void test_createStats_nolock() {
    doReturn(null)
        .when(persistentLocker)
        .waitToAcquireLock(any(Class.class), anyString(), any(Duration.class), any(Duration.class));
    doReturn(true).when(statsCollector).createStats(anyString());
    doReturn(true).when(statsCollector).createServerlessStats(anyString());
    instanceStatsCollectorJob.createStats(ACCOUNTID);
    verify(statsCollector, times(0)).createServerlessStats(ACCOUNTID);
    verify(statsCollector, times(0)).createStats(ACCOUNTID);
  }
}
