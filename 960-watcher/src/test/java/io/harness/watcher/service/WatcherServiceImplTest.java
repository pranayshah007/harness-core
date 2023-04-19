/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.watcher.service;

import static io.harness.delegate.beans.DelegateConfiguration.Action.SELF_DESTRUCT;
import static io.harness.rule.OwnerRule.MARKO;
import static io.harness.rule.OwnerRule.SANJA;
import static io.harness.rule.OwnerRule.VUK;
import static io.harness.rule.OwnerRule.XIN;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.concurent.HTimeLimiterMocker;
import io.harness.delegate.beans.DelegateConfiguration;
import io.harness.rest.RestResponse;
import io.harness.rule.Owner;
import io.harness.watcher.app.WatcherConfiguration;

import com.google.common.base.Charsets;
import com.google.common.util.concurrent.TimeLimiter;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@OwnedBy(HarnessTeam.DEL)
public class WatcherServiceImplTest {
  @Mock private TimeLimiter timeLimiter;
  @Mock private WatcherConfiguration watcherConfiguration;
  @InjectMocks @Spy private WatcherServiceImpl watcherService;

  private static final String TEST_RESOURCE_PATH = "960-watcher/src/test/resources/service/";
  private static final String DELEGATE_CHECK_LOCATION = "DELEGATE_CHECK_LOCATION";
  private static final String INVALID_UPGRADE_VERSION = "1070400";
  private static final String CURRENT_VERSION = "1.0.70400";
  private static final String VALID_FIVE_DIGITS_VERSION = "1.0.70500";
  private static final String VALID_FIVE_DIGITS_WITH_HYPHEN = "1.0.70500-001";
  private static final String VALID_SIX_DIGITS_VERSION = "1.0.703000";
  private static final String VALID_SIX_DIGITS_VERSION_WITH_HYPHEN = "1.0.703000-000";

  // Do not remove, identifies the use of powermock.mockito for the unused dependency check

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldDiskFullFalse() {
    boolean fullDisk = watcherService.isDiskFull();

    assertThat(fullDisk).isFalse();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldDiskFullAfterFreeSpaceFalse() throws IllegalAccessException {
    FieldUtils.writeField(watcherService, "lastAvailableDiskSpace", new AtomicLong(13L), true);

    when(watcherService.getDiskFreeSpace()).thenReturn(14L);

    boolean fullDisk = watcherService.isDiskFull();

    assertThat(fullDisk).isFalse();
  }

  @Test
  @Owner(developers = VUK)
  @Category(UnitTests.class)
  public void shouldDiskFullFalseTrue() throws IllegalAccessException {
    FieldUtils.writeField(watcherService, "lastAvailableDiskSpace", new AtomicLong(13L), true);

    when(watcherService.getDiskFreeSpace()).thenReturn(13L);

    boolean fullDisk = watcherService.isDiskFull();

    assertThat(fullDisk).isTrue();
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldMigrateConfigDelegateDefaultFreemium() throws IOException {
    List<String> configDelegateSourceLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-delegate.yml"), Charsets.UTF_8);
    List<String> configDelegateExpectedLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-delegate-migrated-2.yml"), Charsets.UTF_8);
    List<String> resultLines = new ArrayList<>();

    boolean updated = watcherService.updateConfigFileContentsWithNewUrls(
        configDelegateSourceLines, resultLines, "https://app.harness.io/gratis/api", "config-delegate.yml");

    assertThat(updated).isTrue();

    String contentsActual = StringUtils.join('\n', resultLines);
    String contentsExpected = StringUtils.join('\n', configDelegateExpectedLines);

    assertThat(contentsActual).isEqualTo(contentsExpected);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldMigrateConfigWatcherDefaultFreemium() throws IOException {
    List<String> configWatcherSourceLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-watcher.yml"), Charsets.UTF_8);
    List<String> configWatcherExpectedLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-watcher-migrated-2.yml"), Charsets.UTF_8);
    List<String> resultLines = new ArrayList<>();

    boolean updated = watcherService.updateConfigFileContentsWithNewUrls(
        configWatcherSourceLines, resultLines, "https://app.harness.io/gratis/api", "config-watcher.yml");
    assertThat(updated).isTrue();

    String contentsActual = StringUtils.join('\n', resultLines);
    String contentsExpected = StringUtils.join('\n', configWatcherExpectedLines);

    assertThat(contentsActual).isEqualTo(contentsExpected);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldMigrateConfigDelegateProd() throws IOException {
    List<String> configDelegateSourceLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-delegate.yml"), Charsets.UTF_8);
    List<String> configDelegateExpectedLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-delegate-migrated-1.yml"), Charsets.UTF_8);
    List<String> resultLines = new ArrayList<>();

    boolean updated = watcherService.updateConfigFileContentsWithNewUrls(
        configDelegateSourceLines, resultLines, "https://app.harness.io/api", "config-delegate.yml");
    assertThat(updated).isTrue();

    String contentsActual = StringUtils.join('\n', resultLines);
    String contentsExpected = StringUtils.join('\n', configDelegateExpectedLines);

    assertThat(contentsActual).isEqualTo(contentsExpected);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldMigrateConfigWatcherProd() throws IOException {
    List<String> configWatcherSourceLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-watcher.yml"), Charsets.UTF_8);
    List<String> configWatcherExpectedLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-watcher-migrated-1.yml"), Charsets.UTF_8);
    List<String> resultLines = new ArrayList<>();

    boolean updated = watcherService.updateConfigFileContentsWithNewUrls(
        configWatcherSourceLines, resultLines, "https://app.harness.io/api", "config-watcher.yml");
    assertThat(updated).isTrue();

    String contentsActual = StringUtils.join('\n', resultLines);
    String contentsExpected = StringUtils.join('\n', configWatcherExpectedLines);

    assertThat(contentsActual).isEqualTo(contentsExpected);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldMigrateConfigDelegateProdMissingConfigs() throws IOException {
    List<String> configDelegateSourceLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-delegate-no-grpc.yml"), Charsets.UTF_8);
    List<String> configDelegateExpectedLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-delegate-migrated-added-grpc.yml"), Charsets.UTF_8);
    List<String> resultLines = new ArrayList<>();

    boolean updated = watcherService.updateConfigFileContentsWithNewUrls(
        configDelegateSourceLines, resultLines, "https://app.harness.io/api", "config-delegate.yml");
    assertThat(updated).isTrue();

    String contentsActual = StringUtils.join('\n', resultLines);
    String contentsExpected = StringUtils.join('\n', configDelegateExpectedLines);

    assertThat(contentsActual).isEqualTo(contentsExpected);
  }

  @Test
  @Owner(developers = SANJA)
  @Category(UnitTests.class)
  public void shouldMigrateConfigWatcherProdMissingConfigs() throws IOException {
    List<String> configWatcherSourceLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-watcher-no-grpc.yml"), Charsets.UTF_8);
    List<String> configWatcherExpectedLines =
        FileUtils.readLines(new File(TEST_RESOURCE_PATH + "config-watcher-migrated-added-grpc.yml"), Charsets.UTF_8);
    List<String> resultLines = new ArrayList<>();

    boolean updated = watcherService.updateConfigFileContentsWithNewUrls(
        configWatcherSourceLines, resultLines, "https://app.harness.io/api", "config-watcher.yml");
    assertThat(updated).isTrue();

    String contentsActual = StringUtils.join('\n', resultLines);
    String contentsExpected = StringUtils.join('\n', configWatcherExpectedLines);

    assertThat(contentsActual).isEqualTo(contentsExpected);
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDownloadRunScriptsBeforeRestartingDelegateAndWatcherWhenDiskFull() throws IllegalAccessException {
    FieldUtils.writeField(watcherService, "lastAvailableDiskSpace", new AtomicLong(Long.MAX_VALUE), true);

    boolean downloadSuccesful = watcherService.downloadRunScriptsBeforeRestartingDelegateAndWatcher();

    assertThat(downloadSuccesful).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDownloadRunScriptsBeforeRestartingDelegateAndWatcherWhenNoAvailableDelegateVersions()
      throws IllegalAccessException {
    FieldUtils.writeField(watcherService, "lastAvailableDiskSpace", new AtomicLong(-1L), true);

    boolean downloadSuccesful = watcherService.downloadRunScriptsBeforeRestartingDelegateAndWatcher();

    assertThat(downloadSuccesful).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDownloadRunScriptsBeforeRestartingDelegateAndWatcherWithDelegateVersions() throws Exception {
    FieldUtils.writeField(watcherService, "lastAvailableDiskSpace", new AtomicLong(-1L), true);
    DelegateConfiguration delegateConfiguration =
        DelegateConfiguration.builder().delegateVersions(Arrays.asList("1", "2")).build();

    RestResponse<DelegateConfiguration> restResponse =
        RestResponse.Builder.aRestResponse().withResource(delegateConfiguration).build();

    HTimeLimiterMocker.mockCallInterruptible(timeLimiter, ofSeconds(30)).thenReturn(restResponse);

    boolean downloadSuccesful = watcherService.downloadRunScriptsBeforeRestartingDelegateAndWatcher();

    assertThat(downloadSuccesful).isTrue();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testDownloadRunScriptsBeforeRestartingDelegateAndWatcherWithIOException() throws Exception {
    FieldUtils.writeField(watcherService, "lastAvailableDiskSpace", new AtomicLong(-1L), true);
    DelegateConfiguration delegateConfiguration =
        DelegateConfiguration.builder().delegateVersions(Arrays.asList("1", "2")).build();

    RestResponse<DelegateConfiguration> restResponse =
        RestResponse.Builder.aRestResponse().withResource(delegateConfiguration).build();
    HTimeLimiterMocker.mockCallInterruptible(timeLimiter, ofSeconds(15)).thenReturn(restResponse);
    ExecutionException ioException = new ExecutionException(new IOException("test"));
    HTimeLimiterMocker.mockCallInterruptible(timeLimiter, ofMinutes(1)).thenAnswer(invocation -> {
      throw ioException;
    });

    boolean downloadSuccesful = watcherService.downloadRunScriptsBeforeRestartingDelegateAndWatcher();
    assertThat(downloadSuccesful).isFalse();

    HTimeLimiterMocker.mockCallInterruptible(timeLimiter, ofMinutes(1)).thenAnswer(invocation -> {
      throw new Exception();
    });
    downloadSuccesful = watcherService.downloadRunScriptsBeforeRestartingDelegateAndWatcher();
    assertThat(downloadSuccesful).isFalse();
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testSwitchStorage() {
    try {
      watcherService.switchStorage();
    } catch (Exception ex) {
      fail(ex.getMessage());
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testRestartDelegateToUpgradeJre() {
    try {
      FieldUtils.writeField(watcherService, "clock", Clock.systemDefaultZone(), true);
      watcherService.restartDelegateToUpgradeJre("oracle", "openjdk");
    } catch (Exception ex) {
      fail(ex.getMessage());
    }
  }

  @Test
  @Owner(developers = MARKO)
  @Category(UnitTests.class)
  public void testFindExpectedDelegateVersions() throws Exception {
    DelegateConfiguration delegateConfiguration =
        DelegateConfiguration.builder().delegateVersions(Arrays.asList("1", "2")).build();

    RestResponse<DelegateConfiguration> restResponse =
        RestResponse.Builder.aRestResponse().withResource(delegateConfiguration).build();

    RestResponse<DelegateConfiguration> selfDestructRestResponse =
        RestResponse.Builder.aRestResponse()
            .withResource(DelegateConfiguration.builder().action(SELF_DESTRUCT).build())
            .build();

    HTimeLimiterMocker.mockCallInterruptible(timeLimiter, ofSeconds(30))
        .thenReturn(null)
        .thenReturn(restResponse)
        .thenReturn(selfDestructRestResponse);

    List<String> expectedDelegateVersions = watcherService.findExpectedDelegateVersions();
    assertThat(expectedDelegateVersions).isNull();

    expectedDelegateVersions = watcherService.findExpectedDelegateVersions();
    assertThat(expectedDelegateVersions).containsExactlyInAnyOrder("1", "2");
  }

  private File getFileFromResources(String fileName) {
    ClassLoader classLoader = getClass().getClassLoader();

    URL resource = classLoader.getResource(fileName);
    if (resource == null) {
      throw new IllegalArgumentException("file is not found!");
    } else {
      return new File(resource.getFile());
    }
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testCheckForWatcherUpgradeValidFiveDigitsVersion() throws Exception {
    when(watcherConfiguration.isDoUpgrade()).thenReturn(true);
    when(watcherConfiguration.getDelegateCheckLocation()).thenReturn(DELEGATE_CHECK_LOCATION);
    doReturn(VALID_FIVE_DIGITS_VERSION).when(watcherService).fetchLatestWatcherVersion();
    when(watcherService.getVersion()).thenReturn(CURRENT_VERSION);
    doReturn(true).when(watcherService).downloadRunScriptsBeforeRestartingDelegateAndWatcher();

    watcherService.checkForWatcherUpgrade();

    verify(watcherService).upgradeWatcher(CURRENT_VERSION, VALID_FIVE_DIGITS_VERSION);
    verify(watcherService, times(3)).getVersion();
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testCheckForWatcherUpgradeValidFiveDigitsWithHyphenVersion() throws Exception {
    when(watcherConfiguration.isDoUpgrade()).thenReturn(true);
    when(watcherConfiguration.getDelegateCheckLocation()).thenReturn(DELEGATE_CHECK_LOCATION);
    doReturn(VALID_FIVE_DIGITS_WITH_HYPHEN).when(watcherService).fetchLatestWatcherVersion();
    when(watcherService.getVersion()).thenReturn(CURRENT_VERSION);
    doReturn(true).when(watcherService).downloadRunScriptsBeforeRestartingDelegateAndWatcher();

    watcherService.checkForWatcherUpgrade();

    verify(watcherService).upgradeWatcher(CURRENT_VERSION, VALID_FIVE_DIGITS_VERSION);
    verify(watcherService, times(3)).getVersion();
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testCheckForWatcherUpgradeValidSixDigitsVersion() throws Exception {
    when(watcherConfiguration.isDoUpgrade()).thenReturn(true);
    when(watcherConfiguration.getDelegateCheckLocation()).thenReturn(DELEGATE_CHECK_LOCATION);
    doReturn(VALID_SIX_DIGITS_VERSION).when(watcherService).fetchLatestWatcherVersion();
    when(watcherService.getVersion()).thenReturn(CURRENT_VERSION);
    doReturn(true).when(watcherService).downloadRunScriptsBeforeRestartingDelegateAndWatcher();

    watcherService.checkForWatcherUpgrade();

    verify(watcherService).upgradeWatcher(CURRENT_VERSION, VALID_SIX_DIGITS_VERSION);
    verify(watcherService, times(3)).getVersion();
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testCheckForWatcherUpgradeValidSixDigitsWithHyphenVersion() throws Exception {
    when(watcherConfiguration.isDoUpgrade()).thenReturn(true);
    when(watcherConfiguration.getDelegateCheckLocation()).thenReturn(DELEGATE_CHECK_LOCATION);
    doReturn(VALID_SIX_DIGITS_VERSION_WITH_HYPHEN).when(watcherService).fetchLatestWatcherVersion();
    when(watcherService.getVersion()).thenReturn(CURRENT_VERSION);
    doReturn(true).when(watcherService).downloadRunScriptsBeforeRestartingDelegateAndWatcher();

    watcherService.checkForWatcherUpgrade();

    verify(watcherService).upgradeWatcher(CURRENT_VERSION, VALID_SIX_DIGITS_VERSION);
    verify(watcherService, times(3)).getVersion();
  }

  @Test
  @Owner(developers = XIN)
  @Category(UnitTests.class)
  public void testCheckForWatcherUpgradeInvalidVersion() throws Exception {
    when(watcherConfiguration.isDoUpgrade()).thenReturn(true);
    when(watcherConfiguration.getDelegateCheckLocation()).thenReturn(DELEGATE_CHECK_LOCATION);

    watcherService.checkForWatcherUpgrade();

    verify(watcherService, never()).upgradeWatcher(CURRENT_VERSION, INVALID_UPGRADE_VERSION);
    verify(watcherService, never()).getVersion();
  }
}
