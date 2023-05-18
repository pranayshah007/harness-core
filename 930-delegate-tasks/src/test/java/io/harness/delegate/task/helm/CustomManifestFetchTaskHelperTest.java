/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.helm.CustomManifestFetchTaskHelper.unzipManifestFiles;
import static io.harness.delegate.task.helm.CustomManifestFetchTaskHelper.zipManifestDirectory;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.NAMAN_TALAYCHA;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.readFileToString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.task.manifests.request.CustomManifestFetchConfig;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestSource;
import io.harness.manifest.CustomSourceFile;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.zip.ZipInputStream;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
public class CustomManifestFetchTaskHelperTest extends CategoryTest {
  @Mock CustomManifestService customManifestService;
  @Mock LogCallback logCallback;
  @Mock DelegateFileManagerBase delegateFileManagerBase;

  @InjectMocks private CustomManifestFetchTaskHelper manifestFetchTaskHelper = spy(CustomManifestFetchTaskHelper.class);

  private static final String ACTIVITY_ID = "activityId";
  private static final String APP_ID = "appId";
  private static final String ACCOUNT_ID = "accountId";

  private static final CustomManifestSource SAMPLE_1 = customSource("sample_1", singletonList("file1.yaml"));
  private static final CustomManifestSource SAMPLE_2 = customSource("sample_2", asList("file2.yaml", "file3.yaml"));
  private static final CustomManifestSource SAMPLE_3 = customSource("", singletonList("file4.yaml"));
  private static final CustomManifestSource SAMPLE_EMPTY_SCRIPT = customSource("", singletonList("file4.yaml"));

  private static final CustomManifestSource MISSING_FILE = customSource("missing", singletonList("no-file"));
  private static final CustomManifestSource NOT_ACCESSIBLE = customSource("denied", singletonList("denied"));
  private static final CustomManifestSource EXECUTION_EXCEPTION = customSource("invalid", emptyList());
  private static final Collection<CustomSourceFile> SAMPLE_1_RESULT = customSourceFiles("file1.yaml");
  private static final Collection<CustomSourceFile> SAMPLE_1_READ_FILE_RESULT = customSourceFiles("read-file1.yaml");

  private static final Collection<CustomSourceFile> SAMPLE_2_RESULT = customSourceFiles("file2.yaml", "file3.yaml");
  private static final Collection<CustomSourceFile> SAMPLE_3_RESULT = customSourceFiles("file4.yaml");
  private static final Collection<CustomSourceFile> SAMPLE_EMPTY_SCRIPT_RESULT = customSourceFiles("read-file4.yaml");

  private static final String DEFAULT_DIRECTORY = "DEFAULT_DIRECTORY";

  @Before
  public void setup() throws IOException {
    initMocks(this);
    doReturn(SAMPLE_1_RESULT)
        .when(customManifestService)
        .fetchValues(eq(SAMPLE_1), anyString(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doReturn(SAMPLE_2_RESULT)
        .when(customManifestService)
        .fetchValues(eq(SAMPLE_2), anyString(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doReturn(SAMPLE_EMPTY_SCRIPT_RESULT)
        .when(customManifestService)
        .readFilesContent(eq(DEFAULT_DIRECTORY), eq(singletonList("file4.yaml")));
    doReturn(SAMPLE_1_READ_FILE_RESULT)
        .when(customManifestService)
        .readFilesContent(eq(DEFAULT_DIRECTORY), eq(singletonList("file1.yaml")));
    doThrow(new AccessDeniedException("file not accessible"))
        .when(customManifestService)
        .fetchValues(eq(NOT_ACCESSIBLE), anyString(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doThrow(new RuntimeException("something went wrong"))
        .when(customManifestService)
        .fetchValues(eq(EXECUTION_EXCEPTION), anyString(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doThrow(new FileNotFoundException())
        .when(customManifestService)
        .fetchValues(eq(MISSING_FILE), anyString(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doReturn("WORK_DIR").when(customManifestService).getWorkingDirectory();
  }

  @Test
  @Owner(developers = NAMAN_TALAYCHA)
  @Category(UnitTests.class)
  public void testZipAndUnzipOperationsWithHelmIgnore() throws IOException {
    final String destDirPath = "./repository/helm/dest/ACTIVITY_ID";
    final String sourceDirPath = "./repository/helm/source/manifests";
    final File zipDir = new File("./repository/helm/zip/ACTIVITY_ID");
    final File zipFile = new File(format("%s/destZipFile.zip", zipDir.getPath()));

    FileIo.createDirectoryIfDoesNotExist(destDirPath);
    FileIo.createDirectoryIfDoesNotExist(sourceDirPath);
    Files.createFile(Paths.get(sourceDirPath, "test1.yaml"));
    Files.createFile(Paths.get(sourceDirPath, "test2.yaml"));
    Files.createFile(Paths.get(sourceDirPath, ".helmignore"));
    FileIo.createDirectoryIfDoesNotExist(zipDir.getPath());
    Files.write(Paths.get(sourceDirPath, "test1.yaml"), "test script 1".getBytes());
    Files.write(Paths.get(sourceDirPath, "test2.yaml"), "test script 2".getBytes());

    // test zip directory operation
    zipManifestDirectory(sourceDirPath, zipFile.getPath());
    File[] resultZippedFiles = zipDir.listFiles(file -> !file.isHidden());
    assertThat(resultZippedFiles).isNotNull();
    assertThat(resultZippedFiles).hasSize(1);
    assertThat(resultZippedFiles[0]).hasName("destZipFile.zip");
    assertThat(FileUtils.openInputStream(new File(resultZippedFiles[0].getPath()))).isNotNull();

    InputStream targetStream = FileUtils.openInputStream(new File(zipFile.getPath()));
    ZipInputStream zipTargetStream = new ZipInputStream(targetStream);
    File destDir = new File(destDirPath);

    // test unzip directory operation
    unzipManifestFiles(destDir, zipTargetStream);
    String[] unzippedFiles = destDir.list((dir, name) -> !dir.isHidden());
    assertThat(unzippedFiles).hasSize(1);
    assertThat(unzippedFiles[0]).contains("manifests");
    Path path = Paths.get(destDirPath, unzippedFiles[0]);
    File resultFile = new File(path.toString());
    File[] resultTestFiles = resultFile.listFiles(file -> !file.isHidden());
    assertThat(resultFile.list()).contains("test1.yaml", "test2.yaml", ".helmignore");

    List<String> filesContent = new ArrayList<>();
    filesContent.add(readFileToString(resultTestFiles[0], "UTF-8"));
    filesContent.add(readFileToString(resultTestFiles[1], "UTF-8"));
    assertThat(filesContent).containsExactlyInAnyOrder("test script 1", "test script 2");

    // clean up
    FileIo.deleteDirectoryAndItsContentIfExists(destDirPath);
    FileIo.deleteDirectoryAndItsContentIfExists(sourceDirPath);
    FileIo.deleteDirectoryAndItsContentIfExists(zipDir.getPath());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testZipAndUnzipOperations() throws IOException {
    final String destDirPath = "./repository/helm/dest/ACTIVITY_ID";
    final String sourceDirPath = "./repository/helm/source/manifests";
    final File zipDir = new File("./repository/helm/zip/ACTIVITY_ID");
    final File zipFile = new File(format("%s/destZipFile.zip", zipDir.getPath()));

    FileIo.createDirectoryIfDoesNotExist(destDirPath);
    FileIo.createDirectoryIfDoesNotExist(sourceDirPath);
    Files.createFile(Paths.get(sourceDirPath, "test1.yaml"));
    Files.createFile(Paths.get(sourceDirPath, "test2.yaml"));
    FileIo.createDirectoryIfDoesNotExist(zipDir.getPath());
    Files.write(Paths.get(sourceDirPath, "test1.yaml"), "test script 1".getBytes());
    Files.write(Paths.get(sourceDirPath, "test2.yaml"), "test script 2".getBytes());

    // test zip directory operation
    zipManifestDirectory(sourceDirPath, zipFile.getPath());
    File[] resultZippedFiles = zipDir.listFiles(file -> !file.isHidden());
    assertThat(resultZippedFiles).isNotNull();
    assertThat(resultZippedFiles).hasSize(1);
    assertThat(resultZippedFiles[0]).hasName("destZipFile.zip");
    assertThat(FileUtils.openInputStream(new File(resultZippedFiles[0].getPath()))).isNotNull();

    InputStream targetStream = FileUtils.openInputStream(new File(zipFile.getPath()));
    ZipInputStream zipTargetStream = new ZipInputStream(targetStream);
    File destDir = new File(destDirPath);

    // test unzip directory operation
    unzipManifestFiles(destDir, zipTargetStream);
    String[] unzippedFiles = destDir.list((dir, name) -> !dir.isHidden());
    assertThat(unzippedFiles).hasSize(1);
    assertThat(unzippedFiles[0]).contains("manifests");
    Path path = Paths.get(destDirPath, unzippedFiles[0]);
    File resultFile = new File(path.toString());
    File[] resultTestFiles = resultFile.listFiles(file -> !file.isHidden());
    assertThat(resultFile.list()).contains("test1.yaml", "test2.yaml");

    List<String> filesContent = new ArrayList<>();
    filesContent.add(readFileToString(resultTestFiles[0], "UTF-8"));
    filesContent.add(readFileToString(resultTestFiles[1], "UTF-8"));
    assertThat(filesContent).containsExactlyInAnyOrder("test script 1", "test script 2");

    // clean up
    FileIo.deleteDirectoryAndItsContentIfExists(destDirPath);
    FileIo.deleteDirectoryAndItsContentIfExists(sourceDirPath);
    FileIo.deleteDirectoryAndItsContentIfExists(zipDir.getPath());
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchValueEmptyFileList() throws IOException {
    CustomManifestValuesFetchParams taskParams =
        CustomManifestValuesFetchParams.builder().fetchFilesList(emptyList()).commandUnitName("Fetch files").build();
    CustomManifestValuesFetchResponse response =
        manifestFetchTaskHelper.fetchValuesTask(taskParams, logCallback, DEFAULT_DIRECTORY, true);
    verify(customManifestService, never()).fetchValues(any(), any(), any(), any(), eq(true));
    verify(customManifestService, never()).readFilesContent(any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getValuesFilesContentMap()).isEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testFetchValueSuccess() throws IOException {
    CustomManifestValuesFetchParams taskParams = taskParams(asList(CustomManifestFetchConfig.builder()
                                                                       .key("Empty")
                                                                       .customManifestSource(SAMPLE_EMPTY_SCRIPT)
                                                                       .required(false)
                                                                       .defaultSource(false)
                                                                       .build(),
        CustomManifestFetchConfig.builder()
            .key("SourceManifest")
            .customManifestSource(SAMPLE_1)
            .required(true)
            .defaultSource(true)
            .build(),
        CustomManifestFetchConfig.builder()
            .key("ValueOverride")
            .customManifestSource(SAMPLE_2)
            .required(false)
            .defaultSource(false)
            .build()));

    CustomManifestValuesFetchResponse response =
        manifestFetchTaskHelper.fetchValuesTask(taskParams, logCallback, DEFAULT_DIRECTORY, true);
    verify(customManifestService, times(1)).fetchValues(any(), any(), any(), any(), eq(true));
    verify(customManifestService, times(2)).readFilesContent(any(), any());

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getValuesFilesContentMap())
        .isEqualTo(ImmutableMap.of("Empty", SAMPLE_EMPTY_SCRIPT_RESULT, "SourceManifest", SAMPLE_1_READ_FILE_RESULT,
            "ValueOverride", SAMPLE_2_RESULT));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunMultipleMissingAndNotRequired() throws IOException {
    CustomManifestValuesFetchParams taskParams = taskParams(asList(CustomManifestFetchConfig.builder()
                                                                       .key("Missing")
                                                                       .customManifestSource(MISSING_FILE)
                                                                       .required(false)
                                                                       .defaultSource(false)
                                                                       .build(),
        CustomManifestFetchConfig.builder()
            .key("Sample")
            .customManifestSource(SAMPLE_1)
            .required(true)
            .defaultSource(true)
            .build()));

    CustomManifestValuesFetchResponse response =
        manifestFetchTaskHelper.fetchValuesTask(taskParams, logCallback, DEFAULT_DIRECTORY, true);

    verify(customManifestService, times(1)).fetchValues(any(), any(), any(), any(), eq(true));
    verify(customManifestService, times(1)).readFilesContent(any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getValuesFilesContentMap()).isEqualTo(ImmutableMap.of("Sample", SAMPLE_1_READ_FILE_RESULT));
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunMultipleAccessDeniedAndNotRequired() throws IOException {
    CustomManifestValuesFetchParams taskParams = taskParams(asList(CustomManifestFetchConfig.builder()
                                                                       .key("Missing")
                                                                       .customManifestSource(NOT_ACCESSIBLE)
                                                                       .required(false)
                                                                       .defaultSource(false)
                                                                       .build(),
        CustomManifestFetchConfig.builder()
            .key("Sample")
            .customManifestSource(SAMPLE_1)
            .required(true)
            .defaultSource(false)
            .build()));

    CustomManifestValuesFetchResponse response =
        manifestFetchTaskHelper.fetchValuesTask(taskParams, logCallback, DEFAULT_DIRECTORY, true);
    verify(customManifestService, times(1)).fetchValues(any(), any(), any(), any(), eq(true));
    verify(customManifestService, never()).readFilesContent(any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getValuesFilesContentMap()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunMultipleExecutionException() throws IOException {
    CustomManifestValuesFetchParams taskParams = taskParams(asList(CustomManifestFetchConfig.builder()
                                                                       .key("Exception")
                                                                       .customManifestSource(EXECUTION_EXCEPTION)
                                                                       .required(true)
                                                                       .defaultSource(false)
                                                                       .build(),
        CustomManifestFetchConfig.builder()
            .key("Sample")
            .customManifestSource(SAMPLE_1)
            .required(true)
            .defaultSource(false)
            .build()));
    CustomManifestValuesFetchResponse response =
        manifestFetchTaskHelper.fetchValuesTask(taskParams, logCallback, DEFAULT_DIRECTORY, true);
    verify(customManifestService, times(1)).fetchValues(any(), any(), any(), any(), eq(true));
    verify(customManifestService, never()).readFilesContent(any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getValuesFilesContentMap()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunMultipleMissingAndRequired() throws IOException {
    CustomManifestValuesFetchParams taskParams = taskParams(asList(CustomManifestFetchConfig.builder()
                                                                       .key("Missing")
                                                                       .customManifestSource(MISSING_FILE)
                                                                       .required(true)
                                                                       .defaultSource(false)
                                                                       .build(),
        CustomManifestFetchConfig.builder()
            .key("Sample")
            .customManifestSource(SAMPLE_1)
            .required(true)
            .defaultSource(true)
            .build()));

    CustomManifestValuesFetchResponse response =
        manifestFetchTaskHelper.fetchValuesTask(taskParams, logCallback, DEFAULT_DIRECTORY, true);
    verify(customManifestService, times(1)).fetchValues(any(), any(), any(), any(), eq(true));
    verify(customManifestService, never()).readFilesContent(any(), any());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getValuesFilesContentMap()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunUnhandledException() throws IOException {
    CustomManifestValuesFetchParams taskParams = taskParams(singletonList(CustomManifestFetchConfig.builder()
                                                                              .key("Sample")
                                                                              .customManifestSource(SAMPLE_1)
                                                                              .required(true)
                                                                              .defaultSource(false)
                                                                              .build()));

    doThrow(new RuntimeException("Unhandled exception"))
        .when(customManifestService)
        .fetchValues(any(), any(), any(), any(), eq(true));

    CustomManifestValuesFetchResponse response =
        manifestFetchTaskHelper.fetchValuesTask(taskParams, logCallback, DEFAULT_DIRECTORY, true);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getValuesFilesContentMap()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testDownloadAndUnzipCustomSourceManifestFiles() throws IOException {
    final String workingDirPath = "./repository/helm/work/ACTIVITY_ID";
    final String sourceDirPath = "./repository/helm/source/manifests";
    final String zipDirPath = "./repository/helm/zip/ACTIVITY_ID";
    final String zipFilePath = format("%s/destZipFile.zip", zipDirPath);
    final String fileId = "fileId";

    FileIo.createDirectoryIfDoesNotExist(workingDirPath);
    FileIo.createDirectoryIfDoesNotExist(sourceDirPath);
    Files.createFile(Paths.get(sourceDirPath, "test1.yaml"));
    Files.createFile(Paths.get(sourceDirPath, "test2.yaml"));
    FileIo.createDirectoryIfDoesNotExist(zipDirPath);

    zipManifestDirectory(sourceDirPath, zipFilePath);
    InputStream targetStream = FileUtils.openInputStream(new File(zipFilePath));

    doReturn(targetStream)
        .when(delegateFileManagerBase)
        .downloadByFileId(FileBucket.CUSTOM_MANIFEST, fileId, ACCOUNT_ID);

    manifestFetchTaskHelper.downloadAndUnzipCustomSourceManifestFiles(workingDirPath, fileId, ACCOUNT_ID);

    File destFile = new File(workingDirPath);
    assertThat(destFile).exists();
    String[] unzippedFiles = destFile.list((dir, name) -> !dir.isHidden());
    assertThat(unzippedFiles).hasSize(1);
    assertThat(unzippedFiles[0]).contains("manifests");
    Path path = Paths.get(workingDirPath, unzippedFiles[0]);
    File file = new File(path.toString());
    assertThat(file.list()).contains("test1.yaml", "test2.yaml");
    deleteDirectoryAndItsContentIfExists(workingDirPath);
    deleteDirectoryAndItsContentIfExists(sourceDirPath);
    deleteDirectoryAndItsContentIfExists(zipDirPath);
  }

  private static CustomManifestValuesFetchParams taskParams(List<CustomManifestFetchConfig> fetchConfigList) {
    return CustomManifestValuesFetchParams.builder()
        .fetchFilesList(fetchConfigList)
        .activityId(ACTIVITY_ID)
        .appId(APP_ID)
        .accountId(ACCOUNT_ID)
        .build();
  }

  private static CustomManifestSource customSource(String script, List<String> filePathList) {
    return CustomManifestSource.builder().script(script).filePaths(filePathList).build();
  }

  private static Collection<CustomSourceFile> customSourceFiles(String... files) {
    return Arrays.stream(files)
        .map(file -> CustomSourceFile.builder().fileContent(file).filePath(file).build())
        .collect(toList());
  }
}
