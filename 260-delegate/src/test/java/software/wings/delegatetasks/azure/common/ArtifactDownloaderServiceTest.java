/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.common;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.FileBucket;
import io.harness.rule.Owner;

import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.azure.common.context.ArtifactDownloaderContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class ArtifactDownloaderServiceTest {
  public static final String ARTIFACT_FILE_DIRECTORY = System.getProperty("java.io.tmpdir");
  public static final String FILE_UUID = "file-uuid";
  public static final String ACCOUNT_ID = "account-id";

  @Mock private DelegateFileManager delegateFileManager;
  @InjectMocks ArtifactDownloaderService artifactDownloaderService;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadArtifactFile() throws IOException, ExecutionException {
    InputStream mockArtifactFileStream = mock(InputStream.class);
    ArtifactDownloaderContext downloaderContext = getArtifactDownloaderContext();
    ArtifactStreamAttributes artifactStreamAttributes = downloaderContext.getArtifactStreamAttributes();

    when(delegateFileManager.downloadArtifactAtRuntime(artifactStreamAttributes, downloaderContext.getAccountId(),
             downloaderContext.getAppId(), downloaderContext.getActivityId(), downloaderContext.getCommandName(),
             artifactStreamAttributes.getRegistryHostName()))
        .thenReturn(mockArtifactFileStream);

    mockCopyArtifactFile(downloaderContext.getWorkingDirectory(), artifactStreamAttributes.getArtifactName());

    try (MockedStatic<IOUtils> ioMock = Mockito.mockStatic(IOUtils.class)) {
      File artifactFile = artifactDownloaderService.downloadArtifactFile(downloaderContext);
      ioMock.verify(() -> IOUtils.copy(any(InputStream.class), any(OutputStream.class)));

      assertThat(artifactFile).isNotNull();
      assertThat(artifactFile.getAbsolutePath()).startsWith(ARTIFACT_FILE_DIRECTORY);
      assertThat(artifactFile.getAbsolutePath()).endsWith(artifactStreamAttributes.getArtifactName());
    }
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadArtifactFileFromManager() throws IOException, ExecutionException {
    InputStream mockArtifactFileStream = mock(InputStream.class);
    ArtifactDownloaderContext downloaderContext = getArtifactDownloaderContext();
    ArtifactStreamAttributes artifactStreamAttributes = downloaderContext.getArtifactStreamAttributes();

    when(delegateFileManager.downloadArtifactByFileId(FileBucket.ARTIFACTS, FILE_UUID, ACCOUNT_ID))
        .thenReturn(mockArtifactFileStream);

    mockCopyArtifactFile(downloaderContext.getWorkingDirectory(), artifactStreamAttributes.getArtifactName());

    try (MockedStatic<IOUtils> ioMock = Mockito.mockStatic(IOUtils.class)) {
      File artifactFile = artifactDownloaderService.downloadArtifactFileFromManager(downloaderContext);
      ioMock.verify(() -> IOUtils.copy(any(InputStream.class), any(OutputStream.class)));

      assertThat(artifactFile).isNotNull();
      assertThat(artifactFile.getAbsolutePath()).startsWith(ARTIFACT_FILE_DIRECTORY);
      assertThat(artifactFile.getAbsolutePath()).endsWith(downloaderContext.getArtifactFiles().get(0).getName());
    }
  }

  private void mockCopyArtifactFile(File mockWorkingDirectory, String artifactName) {
    mockCreateArtifactFileInWorkingDirectory(mockWorkingDirectory, artifactName);
  }

  private void mockCreateArtifactFileInWorkingDirectory(File workingDirectory, String artifactName) {
    when(workingDirectory.getAbsolutePath()).thenReturn(ARTIFACT_FILE_DIRECTORY);
  }

  public ArtifactDownloaderContext getArtifactDownloaderContext() {
    ArtifactStreamAttributes streamAttributes = getArtifactStreamAttributes();
    File workingDirectory = mock(File.class);
    return ArtifactDownloaderContext.builder()
        .accountId(ACCOUNT_ID)
        .activityId("activity-id")
        .appId("app-id")
        .commandName("command-name")
        .artifactStreamAttributes(streamAttributes)
        .workingDirectory(workingDirectory)
        .artifactFiles(Collections.singletonList(getArtifactFile()))
        .build();
  }

  private ArtifactStreamAttributes getArtifactStreamAttributes() {
    return ArtifactStreamAttributes.builder().artifactName("artifactName").registryHostName("registryHostName").build();
  }

  private ArtifactFile getArtifactFile() {
    return ArtifactFile.Builder.anArtifactFile()
        .withFileUuid(FILE_UUID)
        .withName("artifact-name")
        .withFileName("file-name")
        .build();
  }
}
