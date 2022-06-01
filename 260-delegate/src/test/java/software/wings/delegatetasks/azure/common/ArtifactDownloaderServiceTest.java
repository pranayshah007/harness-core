/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks.azure.common;

import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.powermock.api.mockito.PowerMockito.doReturn;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.TargetModule;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.FileBucket;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.artifact.ArtifactFile;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.delegatetasks.DelegateFileManager;
import software.wings.delegatetasks.azure.common.context.ArtifactDownloaderContext;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Collections;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({File.class, FileOutputStream.class, IOUtils.class, ArtifactDownloaderService.class})
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
@PowerMockIgnore({"javax.security.*", "javax.net.*"})
public class ArtifactDownloaderServiceTest extends WingsBaseTest {
  public static final String ARTIFACT_FILE_DIRECTORY = "/working-directory-abs-path/any-random-uuid/";
  public static final String FILE_UUID = "file-uuid";
  public static final String ACCOUNT_ID = "account-id";

  @Mock private DelegateFileManager delegateFileManager;
  @InjectMocks ArtifactDownloaderService artifactDownloaderService;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadArtifactFile() throws Exception {
    InputStream mockArtifactFileStream = mock(InputStream.class);
    ArtifactDownloaderContext downloaderContext = getArtifactDownloaderContext();
    ArtifactStreamAttributes artifactStreamAttributes = downloaderContext.getArtifactStreamAttributes();

    doReturn(mockArtifactFileStream)
        .when(delegateFileManager)
        .downloadArtifactAtRuntime(artifactStreamAttributes, downloaderContext.getAccountId(),
            downloaderContext.getAppId(), downloaderContext.getActivityId(), downloaderContext.getCommandName(),
            artifactStreamAttributes.getRegistryHostName());

    mockCopyArtifactFile(
        mockArtifactFileStream, downloaderContext.getWorkingDirectory(), artifactStreamAttributes.getArtifactName());

    File artifactFile = artifactDownloaderService.downloadArtifactFile(downloaderContext);

    assertThat(artifactFile).isNotNull();
    assertThat(artifactFile.getAbsolutePath())
        .isEqualTo(ARTIFACT_FILE_DIRECTORY.concat(artifactStreamAttributes.getArtifactName()));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDownloadArtifactFileFromManager() throws Exception {
    InputStream mockArtifactFileStream = mock(InputStream.class);
    ArtifactDownloaderContext downloaderContext = getArtifactDownloaderContext();
    ArtifactStreamAttributes artifactStreamAttributes = downloaderContext.getArtifactStreamAttributes();

    doReturn(mockArtifactFileStream)
        .when(delegateFileManager)
        .downloadArtifactByFileId(FileBucket.ARTIFACTS, FILE_UUID, ACCOUNT_ID);

    mockCopyArtifactFile(
        mockArtifactFileStream, downloaderContext.getWorkingDirectory(), artifactStreamAttributes.getArtifactName());

    File artifactFile = artifactDownloaderService.downloadArtifactFileFromManager(downloaderContext);

    assertThat(artifactFile).isNotNull();
    assertThat(artifactFile.getAbsolutePath())
        .isEqualTo(ARTIFACT_FILE_DIRECTORY.concat(artifactStreamAttributes.getArtifactName()));
  }

  private void mockCopyArtifactFile(InputStream mockArtifactFileStream, File mockWorkingDirectory, String artifactName)
      throws Exception {
    mockCreateArtifactFileInWorkingDirectory(mockWorkingDirectory, artifactName);

    FileOutputStream mockFileOutputStream = mock(FileOutputStream.class);
    PowerMockito.whenNew(FileOutputStream.class).withAnyArguments().thenReturn(mockFileOutputStream);

    PowerMockito.mockStatic(IOUtils.class);
    PowerMockito.when(IOUtils.copy(mockArtifactFileStream, mockFileOutputStream)).thenAnswer(invocationOnMock -> 1024);
  }

  private void mockCreateArtifactFileInWorkingDirectory(File workingDirectory, String artifactName) throws Exception {
    doReturn(ARTIFACT_FILE_DIRECTORY).when(workingDirectory).getAbsolutePath();

    File mockArtifactFile = mock(File.class);
    String artifactFilePath = ARTIFACT_FILE_DIRECTORY.concat(artifactName);
    doReturn(artifactFilePath).when(mockArtifactFile).getAbsolutePath();

    whenNew(File.class).withArguments(anyString()).thenReturn(mockArtifactFile);
    doReturn(true).when(mockArtifactFile).createNewFile();
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
    return ArtifactFile.Builder.anArtifactFile().withFileUuid(FILE_UUID).withFileName("file-name").build();
  }
}
