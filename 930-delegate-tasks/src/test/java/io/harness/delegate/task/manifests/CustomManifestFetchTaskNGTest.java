/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.manifests;

import static io.harness.eraro.ErrorCode.GIT_ERROR;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ACHYUTH;
import static io.harness.rule.OwnerRule.TARUN_UBA;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.exception.TaskNGDataException;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.helm.CustomManifestFetchTaskHelper;
import io.harness.delegate.task.k8s.K8sTaskHelperBase;
import io.harness.delegate.task.manifests.request.CustomManifestFetchConfig;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.eraro.Level;
import io.harness.exception.AccessDeniedException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.filesystem.FileIo;
import io.harness.logging.LogCallback;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestSource;
import io.harness.manifest.CustomSourceFile;
import io.harness.rule.Owner;

import software.wings.exception.ShellScriptException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CustomManifestFetchTaskNGTest extends CategoryTest {
  DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder().data(TaskData.builder().build()).build();
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private ExecutorService executorService;
  @Mock Consumer<DelegateTaskResponse> consumer;
  @Mock BooleanSupplier preExecute;

  @Mock CustomManifestService customManifestService;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private DelegateFileManagerBase delegateFileManagerBase;
  @Mock private CustomManifestFetchTaskHelper customManifestFetchTaskHelper;

  @Captor ArgumentCaptor<String> workingDirectoryCaptor;

  @InjectMocks
  CustomManifestFetchTaskNG fetchTask =
      new CustomManifestFetchTaskNG(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  private static final String COMMAND_UNIT_NAME = "FetchFiles";
  private static final String ACTIVITY_ID = "activityId";
  private static final String APP_ID = "appId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String DEFAULT_DIR = "DEFAULT_DIR";
  private static final String INNER_DIR = "INNER_DIR";
  private static final String TEMP_DIR = "TEMP_DIR";
  private static final Map<String, Collection<CustomSourceFile>> valuesFilesContentMap =
      singletonMap("Service", singletonList(CustomSourceFile.builder().build()));
  //  private static final String tmpDir = Paths.get("tmp").toFile().getAbsolutePath();
  private static final String ABSOLUTE_DIR = Paths.get("tmp").toFile().getAbsolutePath() + "/myChart1/";
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(executorService).when(logStreamingTaskClient).obtainTaskProgressExecutor();
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunValueFetchTaskOnly() {
    CustomManifestValuesFetchParams taskParams =
        createTaskParams(singletonList(CustomManifestFetchConfig.builder().key("value").build()), null);

    CustomManifestValuesFetchResponse expectedFetchResponse = CustomManifestValuesFetchResponse.builder()
                                                                  .commandExecutionStatus(SUCCESS)
                                                                  .valuesFilesContentMap(valuesFilesContentMap)
                                                                  .build();
    doReturn(expectedFetchResponse)
        .when(customManifestFetchTaskHelper)
        .fetchValuesTask(eq(taskParams), any(LogCallback.class), eq(null), eq(false));

    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response.getValuesFilesContentMap()).isEqualTo(expectedFetchResponse.getValuesFilesContentMap());
    assertThat(response.getCommandExecutionStatus()).isEqualTo(expectedFetchResponse.getCommandExecutionStatus());
    assertThat(response.getUnitProgressData().getUnitProgresses()).isNotNull();
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunValueFetchTaskOnlyException() {
    CustomManifestValuesFetchParams taskParams = createTaskParams(emptyList(), null);
    doThrow(new NullPointerException("generated exception"))
        .when(customManifestFetchTaskHelper)
        .fetchValuesTask(eq(taskParams), any(LogCallback.class), eq(null), eq(false));

    assertThatThrownBy(() -> doRun(taskParams))
        .extracting(ex -> ((TaskNGDataException) ex).getCause().getMessage())
        .isEqualTo("generated exception");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunFetchTaskNoScriptExeption() {
    CustomManifestValuesFetchParams taskParams = createTaskParams(emptyList(), CustomManifestSource.builder().build());
    assertThatThrownBy(() -> doRun(taskParams))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Script can not be null for custom manifest source");
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunFetchTask() throws IOException {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("test script").filePaths(singletonList(INNER_DIR)).build();
    CustomManifestValuesFetchParams taskParams =
        createTaskParams(singletonList(CustomManifestFetchConfig.builder().key("value").build()), customManifestSource);
    CustomManifestValuesFetchResponse fetchValueFileResponse = CustomManifestValuesFetchResponse.builder()
                                                                   .commandExecutionStatus(SUCCESS)
                                                                   .valuesFilesContentMap(valuesFilesContentMap)
                                                                   .build();
    FileIo.createDirectoryIfDoesNotExist(DEFAULT_DIR + "/" + INNER_DIR);
    FileIo.createDirectoryIfDoesNotExist(TEMP_DIR);
    Files.createFile(Paths.get(DEFAULT_DIR, INNER_DIR, "test1.yaml"));

    doReturn(DEFAULT_DIR)
        .when(customManifestService)
        .executeCustomSourceScript(
            eq(taskParams.getActivityId()), any(LogCallback.class), eq(customManifestSource), eq(false));

    doReturn(TEMP_DIR).when(customManifestService).getWorkingDirectory();

    doReturn(fetchValueFileResponse)
        .when(customManifestFetchTaskHelper)
        .fetchValuesTask(eq(taskParams), any(LogCallback.class), eq(DEFAULT_DIR), eq(false));
    doReturn(DelegateFile.Builder.aDelegateFile().withFileId("FILE_ID").build())
        .when(delegateFileManagerBase)
        .uploadAsFile(any(DelegateFile.class), any(File.class));

    CustomManifestValuesFetchResponse response = doRun(taskParams);

    ArgumentCaptor<DelegateFile> fileArgumentCaptor = ArgumentCaptor.forClass(DelegateFile.class);
    verify(delegateFileManagerBase, times(1)).uploadAsFile(fileArgumentCaptor.capture(), any(File.class));
    DelegateFile fileArgumentCaptorValue = fileArgumentCaptor.getValue();
    assertThat(fileArgumentCaptorValue.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(fileArgumentCaptorValue.getBucket()).isEqualTo(FileBucket.CUSTOM_MANIFEST);
    assertThat(fileArgumentCaptorValue.getFileName())
        .isEqualTo(format("zippedCustomManifestFiles%s", taskParams.getActivityId()));

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getValuesFilesContentMap()).isEqualTo(valuesFilesContentMap);

    // clean up
    FileIo.deleteDirectoryAndItsContentIfExists(DEFAULT_DIR + "/" + INNER_DIR);
    FileIo.deleteDirectoryAndItsContentIfExists(TEMP_DIR);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunFetchManifestFilesTaskFail() throws IOException {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("test script").filePaths(singletonList("test1.yaml")).build();
    CustomManifestValuesFetchParams taskParams =
        createTaskParams(singletonList(CustomManifestFetchConfig.builder().key("value").build()), customManifestSource);

    doThrow(new ShellScriptException("shell script failed", GIT_ERROR, Level.ERROR, WingsException.USER))
        .when(customManifestService)
        .executeCustomSourceScript(
            eq(taskParams.getActivityId()), any(LogCallback.class), eq(customManifestSource), eq(false));

    assertThatThrownBy(() -> doRun(taskParams))
        .extracting(ex -> ((TaskNGDataException) ex).getCause().getMessage())
        .isEqualTo("shell script failed");

    verify(delegateFileManagerBase, never()).uploadAsFile(any(DelegateFile.class), any(File.class));
    verify(customManifestService, never()).fetchValues(any(), any(), any(), any(), eq(true));
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testRunZipAndUploadTaskFail() throws IOException {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("test script").filePaths(singletonList(INNER_DIR)).build();
    CustomManifestValuesFetchParams taskParams =
        createTaskParams(singletonList(CustomManifestFetchConfig.builder().key("value").build()), customManifestSource);

    FileIo.createDirectoryIfDoesNotExist(DEFAULT_DIR + "/" + INNER_DIR);
    FileIo.createDirectoryIfDoesNotExist(TEMP_DIR);
    Files.createFile(Paths.get(DEFAULT_DIR, INNER_DIR, "test1.yaml"));

    doReturn(TEMP_DIR).when(customManifestService).getWorkingDirectory();
    doReturn(DEFAULT_DIR)
        .when(customManifestService)
        .executeCustomSourceScript(
            eq(taskParams.getActivityId()), any(LogCallback.class), eq(customManifestSource), eq(false));
    doThrow(new AccessDeniedException("access denied", WingsException.USER))
        .when(delegateFileManagerBase)
        .uploadAsFile(any(), any());
    assertThatThrownBy(() -> doRun(taskParams))
        .extracting(ex -> ((TaskNGDataException) ex).getCause().getMessage())
        .isEqualTo("access denied");
  }

  @Test
  @Owner(developers = TARUN_UBA)
  @Category(UnitTests.class)
  public void testRunFetchTaskInAbsoluteDirectory() throws IOException {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("Test script").filePaths(singletonList(INNER_DIR)).build();
    CustomManifestValuesFetchParams taskParams =
        createTaskParams(singletonList(CustomManifestFetchConfig.builder().key("value").build()), customManifestSource);
    CustomManifestValuesFetchResponse fetchValueFileResponse = CustomManifestValuesFetchResponse.builder()
                                                                   .commandExecutionStatus(SUCCESS)
                                                                   .valuesFilesContentMap(valuesFilesContentMap)
                                                                   .build();

    FileIo.createDirectoryIfDoesNotExist(TEMP_DIR);
    FileIo.createDirectoryIfDoesNotExist(ABSOLUTE_DIR + "/" + INNER_DIR);

    doReturn(ABSOLUTE_DIR)
        .when(customManifestService)
        .executeCustomSourceScript(
            eq(taskParams.getActivityId()), any(LogCallback.class), eq(customManifestSource), eq(false));

    doReturn(TEMP_DIR).when(customManifestService).getWorkingDirectory();

    doReturn(fetchValueFileResponse)
        .when(customManifestFetchTaskHelper)
        .fetchValuesTask(eq(taskParams), any(LogCallback.class), eq(ABSOLUTE_DIR), eq(false));
    doReturn(DelegateFile.Builder.aDelegateFile().withFileId("FILE_ID").build())
        .when(delegateFileManagerBase)
        .uploadAsFile(any(DelegateFile.class), any(File.class));

    CustomManifestValuesFetchResponse response = doRun(taskParams);

    ArgumentCaptor<DelegateFile> fileArgumentCaptor = ArgumentCaptor.forClass(DelegateFile.class);
    verify(delegateFileManagerBase, times(1)).uploadAsFile(fileArgumentCaptor.capture(), any(File.class));
    DelegateFile fileArgumentCaptorValue = fileArgumentCaptor.getValue();
    assertThat(fileArgumentCaptorValue.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(fileArgumentCaptorValue.getBucket()).isEqualTo(FileBucket.CUSTOM_MANIFEST);
    assertThat(fileArgumentCaptorValue.getFileName())
        .isEqualTo(format("zippedCustomManifestFiles%s", taskParams.getActivityId()));

    assertThat(response.getCommandExecutionStatus()).isEqualTo(SUCCESS);
    assertThat(response.getValuesFilesContentMap()).isEqualTo(valuesFilesContentMap);

    // clean up
    FileIo.deleteDirectoryAndItsContentIfExists(ABSOLUTE_DIR + "/" + INNER_DIR);
    FileIo.deleteDirectoryAndItsContentIfExists(TEMP_DIR);
  }

  private static CustomManifestValuesFetchParams createTaskParams(
      List<CustomManifestFetchConfig> fetchConfigList, CustomManifestSource customManifestSource) {
    return CustomManifestValuesFetchParams.builder()
        .fetchFilesList(fetchConfigList)
        .activityId(ACTIVITY_ID)
        .appId(APP_ID)
        .accountId(ACCOUNT_ID)
        .commandUnitName(COMMAND_UNIT_NAME)
        .customManifestSource(customManifestSource)
        .build();
  }

  private CustomManifestValuesFetchResponse doRun(TaskParameters taskParams) {
    return (CustomManifestValuesFetchResponse) fetchTask.run(taskParams);
  }
}
