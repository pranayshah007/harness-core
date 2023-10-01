/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.plugin;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.harness.app.beans.entities.PluginMetadataConfig;
import io.harness.category.element.UnitTests;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.repositories.PluginMetadataRepository;
import io.harness.repositories.PluginMetadataStatusRepository;
import io.harness.rule.Owner;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class PluginMetadataPublisherTest {
  private static final int WAIT_TIME = 2;
  private static final String PLUGIN_METADATA_PUBLISHER = "PLUGIN_METADATA_PUBLISHER";

  PluginMetadataRepository pluginMetadataRepository = mock(PluginMetadataRepository.class);
  PluginMetadataStatusRepository pluginMetadataStatusRepository = mock(PluginMetadataStatusRepository.class);
  PersistentLocker persistentLocker = mock(PersistentLocker.class);

  PluginMetadataPublisher pluginMetadataPublisher;

  @Before
  public void setUp() {
    pluginMetadataPublisher = spy(PluginMetadataPublisher.class);
    pluginMetadataPublisher.persistentLocker = persistentLocker;
    pluginMetadataPublisher.pluginMetadataStatusRepository = pluginMetadataStatusRepository;
    pluginMetadataPublisher.pluginMetadataRepository = pluginMetadataRepository;
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testLockNotAcquire() {
    when(persistentLocker.tryToAcquireLock(PLUGIN_METADATA_PUBLISHER, Duration.ofMinutes(WAIT_TIME))).thenReturn(null);
    doReturn(null).when(persistentLocker).tryToAcquireLock(PLUGIN_METADATA_PUBLISHER, Duration.ofMinutes(WAIT_TIME));
    pluginMetadataPublisher.publish();
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testLockAcquired() {
    AcquiredLock<?> acquiredLock = mock(AcquiredLock.class);
    when(persistentLocker.tryToAcquireLock(PLUGIN_METADATA_PUBLISHER, Duration.ofMinutes(WAIT_TIME)))
        .thenReturn(acquiredLock);
    when(pluginMetadataStatusRepository.find()).thenReturn(null);
    PluginMetadataConfig config = mock(PluginMetadataConfig.class);
    when(pluginMetadataStatusRepository.save(any())).thenReturn(config);

    pluginMetadataPublisher.publish();
  }
}
