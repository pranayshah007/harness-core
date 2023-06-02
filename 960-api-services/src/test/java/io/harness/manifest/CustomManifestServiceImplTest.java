/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.manifest;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.filesystem.FileIo.deleteDirectoryAndItsContentIfExists;
import static io.harness.rule.OwnerRule.ABOSII;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.ShellExecutionException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.rule.Owner;
import io.harness.shell.ExecuteCommandResponse;
import io.harness.shell.ScriptProcessExecutor;
import io.harness.shell.ShellExecutorConfig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class CustomManifestServiceImplTest extends CategoryTest {
  private static final String ACTIVITY_ID = "activityId";

  @Mock private ScriptProcessExecutor scriptProcessExecutor;
  @Mock private LogCallback logCallback;

  @Spy @InjectMocks private CustomManifestServiceImpl customManifestService;

  private String shellWorkingDirectory;
  private String testOutputDirectory;

  @Before
  public void setup() throws IOException {
    MockitoAnnotations.initMocks(this);
    doAnswer(invocation -> {
      ShellExecutorConfig config = invocation.getArgument(0, ShellExecutorConfig.class);
      shellWorkingDirectory = config.getWorkingDirectory();
      return scriptProcessExecutor;
    })
        .when(customManifestService)
        .createExecutor(any(ShellExecutorConfig.class), eq(logCallback));

    testOutputDirectory = Files.createTempDirectory("customMnanifestTestOutputDirectory").toString();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadCustomSource_ShellScriptFailed() {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("test script").filePaths(singletonList("file1.yaml")).build();

    doReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.FAILURE).build())
        .when(scriptProcessExecutor)
        .executeCommandString("test script", emptyList());

    assertThatThrownBy(
        () -> customManifestService.downloadCustomSource(customManifestSource, testOutputDirectory, logCallback))
        .isInstanceOf(ShellExecutionException.class)
        .hasMessageContaining("Custom shell script failed");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadCustomSource_FileIsMissing() {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("test script").filePaths(singletonList("file1.yaml")).build();
    doReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build())
        .when(scriptProcessExecutor)
        .executeCommandString("test script", emptyList());
    assertThatThrownBy(
        () -> customManifestService.downloadCustomSource(customManifestSource, testOutputDirectory, logCallback))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("file1.yaml' does not exist");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testDownloadCustomSource() throws IOException {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder()
            .script("test script")
            .filePaths(Arrays.asList("file1.yaml", "manifest", "templates/file4.yaml"))
            .build();

    doAnswer(invocation -> {
      File file1 = new File(Paths.get(shellWorkingDirectory, "file1.yaml").toString());
      File manifestFile1 = new File(Paths.get(shellWorkingDirectory, "manifest/file2.yaml").toString());
      File manifestFile2 = new File(Paths.get(shellWorkingDirectory, "manifest/file3.yaml").toString());
      File templatesFiles1 = new File(Paths.get(shellWorkingDirectory, "templates/file4.yaml").toString());
      FileUtils.write(file1, "content", Charset.defaultCharset());
      FileUtils.write(manifestFile1, "content", Charset.defaultCharset());
      FileUtils.write(manifestFile2, "content", Charset.defaultCharset());
      FileUtils.write(templatesFiles1, "content", Charset.defaultCharset());
      return ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build();
    })
        .when(scriptProcessExecutor)
        .executeCommandString("test script", Collections.emptyList());

    customManifestService.downloadCustomSource(customManifestSource, testOutputDirectory, logCallback);
    assertFilesExists(testOutputDirectory, "file1.yaml", "file2.yaml", "file3.yaml", "templates/file4.yaml");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchValues() throws IOException {
    String workingDirectory = Files.createTempDirectory("testFetchValues").toString();
    String absolutePath = Paths.get(workingDirectory, "absolute.yaml").toString();
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder()
            .script("test script")
            .filePaths(Arrays.asList("file1.yaml", "values/file2.yaml", absolutePath))
            .build();

    doAnswer(invocation -> {
      File file = new File(Paths.get(shellWorkingDirectory, "file1.yaml").toString());
      File valuesFile = new File(Paths.get(shellWorkingDirectory, "values/file2.yaml").toString());
      File absoluteFile = new File(absolutePath);

      FileUtils.write(file, "file1-content", Charset.defaultCharset());
      FileUtils.write(valuesFile, "file2-content", Charset.defaultCharset());
      FileUtils.write(absoluteFile, "absolute-content", Charset.defaultCharset());

      return ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build();
    })
        .when(scriptProcessExecutor)
        .executeCommandString("test script", Collections.emptyList());

    Collection<CustomSourceFile> result =
        customManifestService.fetchValues(customManifestSource, workingDirectory, ACTIVITY_ID, logCallback, true);
    assertThat(result.stream().map(CustomSourceFile::getFilePath))
        .containsExactlyInAnyOrder("file1.yaml", "values/file2.yaml", absolutePath);
    assertThat(result.stream().map(CustomSourceFile::getFileContent))
        .containsExactlyInAnyOrder("file1-content", "file2-content", "absolute-content");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchValues_emptyScript() throws IOException {
    shellWorkingDirectory = Files.createTempDirectory("testFetchValues").toString();
    String absolutePath = Paths.get(shellWorkingDirectory, "absolute.yaml").toString();
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script(null).filePaths(Arrays.asList(absolutePath)).build();

    File absoluteFile = new File(absolutePath);
    FileUtils.write(absoluteFile, "absolute-content", Charset.defaultCharset());

    Collection<CustomSourceFile> result =
        customManifestService.fetchValues(customManifestSource, shellWorkingDirectory, ACTIVITY_ID, logCallback, true);
    assertThat(result.stream().map(CustomSourceFile::getFilePath)).containsExactly(absolutePath);
    assertThat(result.stream().map(CustomSourceFile::getFileContent)).containsExactly("absolute-content");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void tesExecuteCustomSourceScriptSuccess() throws IOException {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("test script").filePaths(singletonList("file1.yaml")).build();
    doReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.SUCCESS).build())
        .when(scriptProcessExecutor)
        .executeCommandString("test script", emptyList());
    String resultWorkingDir =
        customManifestService.executeCustomSourceScript(ACTIVITY_ID, logCallback, customManifestSource, true);

    assertThat(resultWorkingDir).isNotNull();
    File file = new File(resultWorkingDir);
    assertThat(file.exists()).isTrue();
    assertThat(file.toString()).contains("manifestCustomSource");
    deleteDirectoryAndItsContentIfExists(resultWorkingDir);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void tesExecuteCustomSourceScriptSFail() throws IOException {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("test script").filePaths(singletonList("file1.yaml")).build();
    doReturn(ExecuteCommandResponse.builder().status(CommandExecutionStatus.FAILURE).build())
        .when(scriptProcessExecutor)
        .executeCommandString("test script", emptyList());

    assertThatThrownBy(
        () -> customManifestService.executeCustomSourceScript(ACTIVITY_ID, logCallback, customManifestSource, true))
        .isInstanceOf(ShellExecutionException.class)
        .hasMessageContaining("Custom shell script failed");
  }

  @After
  public void cleanup() throws IOException {
    if (isNotEmpty(testOutputDirectory)) {
      deleteDirectoryAndItsContentIfExists(testOutputDirectory);
    }

    if (isNotEmpty(shellWorkingDirectory)) {
      deleteDirectoryAndItsContentIfExists(shellWorkingDirectory);
    }
  }

  private static void assertFilesExists(String output, String... files) {
    Set<String> existingFiles = getFilesInDirectory(output, "");
    assertThat(existingFiles).containsExactlyInAnyOrder(files);
  }

  private static Set<String> getFilesInDirectory(String absolutePath, String directory) {
    File directoryFile = new File(absolutePath);
    Set<String> existingFiles = new HashSet<>();
    if (directoryFile.list() == null) {
      return existingFiles;
    }

    for (String file : directoryFile.list()) {
      String filePath = Paths.get(directory, file).toString();
      String fullPath = Paths.get(absolutePath, file).toString();
      if (new File(fullPath).isDirectory()) {
        existingFiles.addAll(getFilesInDirectory(fullPath, filePath));
      } else {
        existingFiles.add(filePath);
      }
    }

    return existingFiles;
  }
}
