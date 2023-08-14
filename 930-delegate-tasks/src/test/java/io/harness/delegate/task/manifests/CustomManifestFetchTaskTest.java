/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.manifests;

import static io.harness.eraro.ErrorCode.GIT_ERROR;
import static io.harness.logging.CommandExecutionStatus.FAILURE;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.PRATYUSH;
import static io.harness.rule.OwnerRule.TATHAGAT;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateFile;
import io.harness.delegate.beans.DelegateFileManagerBase;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.FileBucket;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
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
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
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

@OwnedBy(HarnessTeam.CDP)
public class CustomManifestFetchTaskTest extends CategoryTest {
  DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder().data(TaskData.builder().build()).build();
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock Consumer<DelegateTaskResponse> consumer;
  @Mock BooleanSupplier preExecute;

  @Mock LogCallback logCallback;
  @Mock CustomManifestService customManifestService;
  @Mock private K8sTaskHelperBase k8sTaskHelperBase;
  @Mock private DelegateFileManagerBase delegateFileManagerBase;
  @Mock private CustomManifestFetchTaskHelper customManifestFetchTaskHelper;

  @Captor ArgumentCaptor<String> workingDirectoryCaptor;

  @InjectMocks
  CustomManifestFetchTask fetchTask =
      new CustomManifestFetchTask(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  private static final String COMMAND_UNIT_NAME = "FetchFiles";
  private static final String ACTIVITY_ID = "activityId";
  private static final String APP_ID = "appId";
  private static final String ACCOUNT_ID = "accountId";
  private static final String DEFAULT_DIR = "DEFAULT_DIR";
  private static final String TEMP_DIR = "TEMP_DIR";
  private static final String INNER_DIR = "INNER_DIR";

  private static final Map<String, Collection<CustomSourceFile>> valuesFilesContentMap =
      singletonMap("Service", singletonList(CustomSourceFile.builder().build()));

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(logCallback).when(logStreamingTaskClient).obtainLogCallback(anyString());
  }

  @Test
  @Owner(developers = TATHAGAT)
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
        .fetchValuesTask(taskParams, logCallback, null, true);

    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response).isEqualTo(expectedFetchResponse);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunValueFetchTaskOnlyException() {
    CustomManifestValuesFetchParams taskParams = createTaskParams(emptyList(), null);
    doThrow(new NullPointerException("generated exception"))
        .when(customManifestFetchTaskHelper)
        .fetchValuesTask(taskParams, logCallback, null, true);
    CustomManifestValuesFetchResponse response = doRun(taskParams);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("NullPointerException: generated exception");
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunFetchTaskNoScriptExeption() {
    CustomManifestValuesFetchParams taskParams = createTaskParams(emptyList(), CustomManifestSource.builder().build());
    assertThatThrownBy(() -> doRun(taskParams))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessageContaining("Script can not be null for custom manifest source");
  }

  @Test
  @Owner(developers = TATHAGAT)
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
        .executeCustomSourceScript(taskParams.getActivityId(), logCallback, customManifestSource, true);

    doReturn(TEMP_DIR).when(customManifestService).getWorkingDirectory();

    doReturn(fetchValueFileResponse)
        .when(customManifestFetchTaskHelper)
        .fetchValuesTask(taskParams, logCallback, DEFAULT_DIR, true);
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
  @Owner(developers = PRATYUSH)
  @Category(UnitTests.class)
  public void testRunFetchTaskWithManifestFile() throws IOException {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("test script").filePaths(singletonList("test1.yaml")).build();
    CustomManifestValuesFetchParams taskParams =
        createTaskParams(singletonList(CustomManifestFetchConfig.builder().key("value").build()), customManifestSource);
    CustomManifestValuesFetchResponse fetchValueFileResponse = CustomManifestValuesFetchResponse.builder()
                                                                   .commandExecutionStatus(SUCCESS)
                                                                   .valuesFilesContentMap(valuesFilesContentMap)
                                                                   .build();
    FileIo.createDirectoryIfDoesNotExist(DEFAULT_DIR);
    FileIo.createDirectoryIfDoesNotExist(TEMP_DIR);
    Files.createFile(Paths.get(DEFAULT_DIR, "test1.yaml"));

    doReturn(DEFAULT_DIR)
        .when(customManifestService)
        .executeCustomSourceScript(
            eq(taskParams.getActivityId()), any(LogCallback.class), eq(customManifestSource), eq(true));

    doReturn(TEMP_DIR).when(customManifestService).getWorkingDirectory();

    doReturn(fetchValueFileResponse)
        .when(customManifestFetchTaskHelper)
        .fetchValuesTask(eq(taskParams), any(LogCallback.class), eq(DEFAULT_DIR), eq(true));
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
    FileIo.deleteDirectoryAndItsContentIfExists(DEFAULT_DIR);
    FileIo.deleteDirectoryAndItsContentIfExists(TEMP_DIR);
  }

  @Test
  @Owner(developers = TATHAGAT)
  @Category(UnitTests.class)
  public void testRunFetchManifestFilesTaskFail() throws IOException {
    CustomManifestSource customManifestSource =
        CustomManifestSource.builder().script("test script").filePaths(singletonList("test1.yaml")).build();
    CustomManifestValuesFetchParams taskParams =
        createTaskParams(singletonList(CustomManifestFetchConfig.builder().key("value").build()), customManifestSource);

    doThrow(new ShellScriptException("shell script failed", GIT_ERROR, Level.ERROR, WingsException.USER))
        .when(customManifestService)
        .executeCustomSourceScript(taskParams.getActivityId(), logCallback, customManifestSource, true);

    CustomManifestValuesFetchResponse response = doRun(taskParams);
    ArgumentCaptor<String> logCaptor = ArgumentCaptor.forClass(String.class);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("GIT_ERROR");

    doThrow(new InvalidRequestException("auth failed"))
        .when(customManifestService)
        .executeCustomSourceScript(taskParams.getActivityId(), logCallback, customManifestSource, true);

    response = doRun(taskParams);
    verify(logCallback, times(2))
        .saveExecutionLog(logCaptor.capture(), any(LogLevel.class), any(CommandExecutionStatus.class));
    assertThat(logCaptor.getAllValues().get(0)).contains("Failed to execute custom manifest script");
    assertThat(logCaptor.getAllValues().get(1)).contains("Custom source script execution task failed");
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("INVALID_REQUEST");

    verify(delegateFileManagerBase, never()).uploadAsFile(any(DelegateFile.class), any(File.class));
    verify(customManifestService, never()).fetchValues(any(), any(), any(), any(), eq(true));
  }

  @Test
  @Owner(developers = TATHAGAT)
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
        .executeCustomSourceScript(taskParams.getActivityId(), logCallback, customManifestSource, true);
    doThrow(new AccessDeniedException("access denied", WingsException.USER))
        .when(delegateFileManagerBase)
        .uploadAsFile(any(), any());

    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(FAILURE);
    assertThat(response.getErrorMessage()).isEqualTo("ACCESS_DENIED");
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
