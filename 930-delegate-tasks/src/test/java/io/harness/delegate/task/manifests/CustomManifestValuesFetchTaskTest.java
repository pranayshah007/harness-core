/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.manifests;

import static io.harness.rule.OwnerRule.ABOSII;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.DelegateTaskPackage;
import io.harness.delegate.beans.DelegateTaskResponse;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.task.TaskParameters;
import io.harness.delegate.task.manifests.request.CustomManifestFetchConfig;
import io.harness.delegate.task.manifests.request.CustomManifestValuesFetchParams;
import io.harness.delegate.task.manifests.response.CustomManifestValuesFetchResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.manifest.CustomManifestService;
import io.harness.manifest.CustomManifestSource;
import io.harness.manifest.CustomSourceFile;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import java.io.FileNotFoundException;
import java.nio.file.AccessDeniedException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class CustomManifestValuesFetchTaskTest extends CategoryTest {
  private static final String COMMAND_UNIT_NAME = "FetchFiles";
  private static final String ACTIVITY_ID = "activityId";
  private static final String APP_ID = "appId";
  private static final String ACCOUNT_ID = "accountId";

  private static final CustomManifestSource SAMPLE_1 = customSource("sample_1", singletonList("file1.yaml"));
  private static final CustomManifestSource SAMPLE_2 = customSource("sample_2", asList("file2.yaml", "file3.yaml"));
  private static final CustomManifestSource SAMPLE_3 = customSource("", singletonList("file4.yaml"));
  private static final CustomManifestSource MISSING_FILE = customSource("missing", singletonList("no-file"));
  private static final CustomManifestSource NOT_ACCESSIBLE = customSource("denied", singletonList("denied"));
  private static final CustomManifestSource EXECUTION_EXCEPTION = customSource("invalid", emptyList());
  private static final Collection<CustomSourceFile> SAMPLE_1_RESULT = customSourceFiles("file1.yaml");
  private static final Collection<CustomSourceFile> SAMPLE_2_RESULT = customSourceFiles("file2.yaml", "file3.yaml");
  private static final Collection<CustomSourceFile> SAMPLE_3_RESULT = customSourceFiles("file4.yaml");

  DelegateTaskPackage delegateTaskPackage = DelegateTaskPackage.builder().data(TaskData.builder().build()).build();
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock Consumer<DelegateTaskResponse> consumer;
  @Mock BooleanSupplier preExecute;

  @Mock LogCallback logCallback;
  @Mock CustomManifestService customManifestService;
  @Captor ArgumentCaptor<String> workingDirectoryCaptor;

  @InjectMocks
  CustomManifestValuesFetchTask fetchTask =
      new CustomManifestValuesFetchTask(delegateTaskPackage, logStreamingTaskClient, consumer, preExecute);

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(logCallback).when(logStreamingTaskClient).obtainLogCallback(anyString());

    doReturn(SAMPLE_1_RESULT)
        .when(customManifestService)
        .fetchValues(eq(SAMPLE_1), workingDirectoryCaptor.capture(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doReturn(SAMPLE_2_RESULT)
        .when(customManifestService)
        .fetchValues(eq(SAMPLE_2), workingDirectoryCaptor.capture(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doReturn(SAMPLE_3_RESULT)
        .when(customManifestService)
        .fetchValues(eq(SAMPLE_3), workingDirectoryCaptor.capture(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doThrow(new FileNotFoundException())
        .when(customManifestService)
        .fetchValues(eq(MISSING_FILE), workingDirectoryCaptor.capture(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doThrow(new AccessDeniedException("file not accessible"))
        .when(customManifestService)
        .fetchValues(eq(NOT_ACCESSIBLE), workingDirectoryCaptor.capture(), eq(ACTIVITY_ID), eq(logCallback), eq(true));
    doThrow(new RuntimeException("something wen wrong"))
        .when(customManifestService)
        .fetchValues(
            eq(EXECUTION_EXCEPTION), workingDirectoryCaptor.capture(), eq(ACTIVITY_ID), eq(logCallback), eq(true));

    doAnswer(invocation -> invocation.getMethod().getName() + new Random().nextInt())
        .when(customManifestService)
        .getWorkingDirectory();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunEmpty() {
    CustomManifestValuesFetchParams taskParams =
        CustomManifestValuesFetchParams.builder().fetchFilesList(emptyList()).commandUnitName("Fetch files").build();
    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getValuesFilesContentMap()).isEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunSingle() {
    CustomManifestValuesFetchParams taskParams = taskParams(singletonList(CustomManifestFetchConfig.builder()
                                                                              .key("Sample")
                                                                              .customManifestSource(SAMPLE_1)
                                                                              .required(true)
                                                                              .defaultSource(false)
                                                                              .build()));

    CustomManifestValuesFetchResponse response = doRun(taskParams);

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getValuesFilesContentMap()).isEqualTo(ImmutableMap.of("Sample", SAMPLE_1_RESULT));

    verify(customManifestService, times(1)).cleanup(workingDirectoryCaptor.getValue());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunMultiple() {
    CustomManifestValuesFetchParams taskParams = taskParams(asList(CustomManifestFetchConfig.builder()
                                                                       .key("Sample_1")
                                                                       .customManifestSource(SAMPLE_1)
                                                                       .required(true)
                                                                       .defaultSource(false)
                                                                       .build(),
        CustomManifestFetchConfig.builder()
            .key("Sample_2")
            .customManifestSource(SAMPLE_2)
            .required(true)
            .defaultSource(false)
            .build()));
    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getValuesFilesContentMap())
        .isEqualTo(ImmutableMap.of("Sample_1", SAMPLE_1_RESULT, "Sample_2", SAMPLE_2_RESULT));

    verify(customManifestService, times(1)).cleanup(workingDirectoryCaptor.getValue());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunMultipleDefaultSourceAndEmptyScript() {
    CustomManifestValuesFetchParams taskParams = taskParams(asList(CustomManifestFetchConfig.builder()
                                                                       .key("Empty")
                                                                       .customManifestSource(SAMPLE_3)
                                                                       .required(true)
                                                                       .defaultSource(false)
                                                                       .build(),
        CustomManifestFetchConfig.builder()
            .key("Default")
            .customManifestSource(SAMPLE_1)
            .required(true)
            .defaultSource(true)
            .build()));
    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getValuesFilesContentMap())
        .isEqualTo(ImmutableMap.of("Empty", SAMPLE_3_RESULT, "Default", SAMPLE_1_RESULT));
    List<String> usedWorkingDirectories = workingDirectoryCaptor.getAllValues();
    assertThat(usedWorkingDirectories).hasSize(2);
    assertThat(usedWorkingDirectories).containsExactly(usedWorkingDirectories.get(0), usedWorkingDirectories.get(0));

    verify(customManifestService, times(1)).cleanup(workingDirectoryCaptor.getValue());
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunMultipleMissingAndNotRequired() {
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
            .defaultSource(false)
            .build()));

    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getValuesFilesContentMap()).isEqualTo(ImmutableMap.of("Sample", SAMPLE_1_RESULT));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunMultipleAccessDeniedAndNotRequired() {
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

    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getValuesFilesContentMap()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunMultipleExecutionException() {
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
    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getValuesFilesContentMap()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunMultipleMissingAndRequired() {
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
            .defaultSource(false)
            .build()));

    CustomManifestValuesFetchResponse response = doRun(taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getValuesFilesContentMap()).isNullOrEmpty();
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testRunUnhandledException() {
    CustomManifestValuesFetchTask spyFetchTask = spy(fetchTask);
    CustomManifestValuesFetchParams taskParams = taskParams(emptyList());

    doThrow(new RuntimeException("Unhandled exception")).when(spyFetchTask).fetchValues(taskParams);

    CustomManifestValuesFetchResponse response = (CustomManifestValuesFetchResponse) spyFetchTask.run(taskParams);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(response.getValuesFilesContentMap()).isNullOrEmpty();
  }

  private CustomManifestValuesFetchResponse doRun(TaskParameters taskParams) {
    return (CustomManifestValuesFetchResponse) fetchTask.run(taskParams);
  }

  private static CustomManifestValuesFetchParams taskParams(List<CustomManifestFetchConfig> fetchConfigList) {
    return CustomManifestValuesFetchParams.builder()
        .fetchFilesList(fetchConfigList)
        .activityId(ACTIVITY_ID)
        .appId(APP_ID)
        .accountId(ACCOUNT_ID)
        .commandUnitName(COMMAND_UNIT_NAME)
        .build();
  }

  private static CustomManifestSource customSource(String script, List<String> filePathList) {
    return CustomManifestSource.builder().script(script).filePaths(filePathList).build();
  }

  private static Collection<CustomSourceFile> customSourceFiles(String... files) {
    return Arrays.stream(files)
        .map(file -> CustomSourceFile.builder().fileContent(file).filePath(file).build())
        .collect(Collectors.toList());
  }
}
