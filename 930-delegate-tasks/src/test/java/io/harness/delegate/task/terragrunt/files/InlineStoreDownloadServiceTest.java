/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.terragrunt.files;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.storeconfig.InlineFileConfig;
import io.harness.delegate.beans.storeconfig.InlineStoreDelegateConfig;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class InlineStoreDownloadServiceTest extends CategoryTest {
  private static final String OUTPUT_DIR = "./test-output-dir";
  private static final String ACCOUNT_ID = "accountId";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private LogCallback logCallback;

  @InjectMocks private InlineStoreDownloadService downloadService;

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownload() {
    try {
      InlineStoreDelegateConfig storeDelegateConfig =
          InlineStoreDelegateConfig.builder()
              .files(asList(InlineFileConfig.builder().name("file1.tfvars").content("file1-content").build(),
                  InlineFileConfig.builder().name("file2.tfvars").content("file2-content").build()))
              .build();

      downloadService.download(storeDelegateConfig, ACCOUNT_ID, OUTPUT_DIR, logCallback);
      Collection<File> files =
          FileUtils.listFiles(new File(OUTPUT_DIR), TrueFileFilter.INSTANCE, TrueFileFilter.INSTANCE);
      assertThat(files.size()).isEqualTo(2);
      assertThat(files.stream().map(File::getAbsolutePath))
          .containsExactlyInAnyOrder(Paths.get(OUTPUT_DIR, "file1.tfvars").toAbsolutePath().toString(),
              Paths.get(OUTPUT_DIR, "file2.tfvars").toAbsolutePath().toString());
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(OUTPUT_DIR);
    }
  }

  @Test
  @SneakyThrows
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchFiles() {
    try {
      InlineStoreDelegateConfig storeDelegateConfig =
          InlineStoreDelegateConfig.builder()
              .files(asList(InlineFileConfig.builder().name("file1-${UUID}.tfvars").content("file1-content").build(),
                  InlineFileConfig.builder().name("file2-${UUID}.tfvars").content("file2-content").build()))
              .build();

      FetchFilesResult result = downloadService.fetchFiles(storeDelegateConfig, ACCOUNT_ID, OUTPUT_DIR, logCallback);
      assertThat(result.getFiles().size()).isEqualTo(2);
      Stream<String> filesContent = result.getFiles().stream().map(this::sneakyReadFileString);
      assertThat(filesContent).containsExactlyInAnyOrder("file1-content", "file2-content");
      assertThat(result.getFiles()).doesNotContain("file1-${UUID}.tfvars", "file2-${UUID}.tfvars");
    } finally {
      FileIo.deleteDirectoryAndItsContentIfExists(OUTPUT_DIR);
    }
  }

  @SneakyThrows
  private String sneakyReadFileString(String filePath) {
    return FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
  }
}