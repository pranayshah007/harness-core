/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt.files;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.InlineStoreDelegateConfig;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class TerragruntDownloadServiceTest extends CategoryTest {
  @Mock private LogCallback logCallback;
  @Mock private GitStoreDownloadService gitStoreDownloadService;
  @Mock private InlineStoreDownloadService inlineStoreDownloadService;

  @InjectMocks private TerragruntDownloadService downloadService;

  @InjectMocks @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadGit() {
    final GitStoreDelegateConfig storeDelegateConfig = GitStoreDelegateConfig.builder().build();
    downloadService.download(storeDelegateConfig, "accountId", "output", logCallback);
    verify(gitStoreDownloadService).download(storeDelegateConfig, "accountId", "output", logCallback);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadInline() {
    final InlineStoreDelegateConfig storeDelegateConfig = InlineStoreDelegateConfig.builder().build();
    downloadService.download(storeDelegateConfig, "accountId", "output", logCallback);
    verify(inlineStoreDownloadService).download(storeDelegateConfig, "accountId", "output", logCallback);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchFilesGit() {
    final GitStoreDelegateConfig storeDelegateConfig = GitStoreDelegateConfig.builder().build();
    downloadService.fetchFiles(storeDelegateConfig, "accountId", "output", logCallback);
    verify(gitStoreDownloadService).fetchFiles(storeDelegateConfig, "accountId", "output", logCallback);
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchFilesInline() {
    final InlineStoreDelegateConfig storeDelegateConfig = InlineStoreDelegateConfig.builder().build();
    downloadService.fetchFiles(storeDelegateConfig, "accountId", "output", logCallback);
    verify(inlineStoreDownloadService).fetchFiles(storeDelegateConfig, "accountId", "output", logCallback);
  }
}