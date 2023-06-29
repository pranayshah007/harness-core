/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.steps.task;

import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.manifest.steps.outcome.ManifestsOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.delegate.beans.ErrorNotifyResponseData;
import io.harness.delegate.beans.TaskData;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;
import io.harness.tasks.ResponseData;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class ManifestTaskServiceImplTest extends CategoryTest {
  private static final String MANIFEST_TYPE_1 = "manifest1";
  private static final String MANIFEST_TYPE_2 = "manifest2";

  private static final String MANIFEST_ID_1 = "manifest_id_1";
  private static final String MANIFEST_ID_2 = "manifest_id_2";
  private static final String MANIFEST_ID_3 = "manifest_id_3";

  private static final String TASK_ID_1 = "task_id_1";
  private static final String TASK_ID_2 = "task_id_2";

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private ManifestTaskHandler mockTaskHandler;

  @Mock private Map<String, ManifestTaskHandler> taskHandlersMap;

  @InjectMocks private ManifestTaskServiceImpl taskService;

  private final Ambiance ambiance = Ambiance.newBuilder().putSetupAbstractions("accountId", "test-account").build();

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIsSupported() {
    final ManifestOutcome manifest = createMockManifest(MANIFEST_TYPE_1);
    setupMockMapHandler(MANIFEST_TYPE_1);

    doReturn(true).when(mockTaskHandler).isSupported(ambiance, manifest);

    assertThat(taskService.isSupported(ambiance, manifest)).isTrue();
    verify(mockTaskHandler).isSupported(ambiance, manifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIsSupportedNegative() {
    final ManifestOutcome manifest = createMockManifest(MANIFEST_TYPE_1);
    setupMockMapHandler(MANIFEST_TYPE_1);

    doReturn(false).when(mockTaskHandler).isSupported(ambiance, manifest);

    assertThat(taskService.isSupported(ambiance, manifest)).isFalse();
    verify(mockTaskHandler).isSupported(ambiance, manifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testIsSupportedNoHandler() {
    final ManifestOutcome manifest = createMockManifest(MANIFEST_TYPE_1);

    assertThat(taskService.isSupported(ambiance, manifest)).isFalse();
    verify(mockTaskHandler, never()).isSupported(ambiance, manifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateTaskData() {
    final TaskData expectedTaskData = TaskData.builder().build();
    final ManifestOutcome manifest = createMockManifest(MANIFEST_TYPE_1);
    setupMockMapHandler(MANIFEST_TYPE_1);

    doReturn(Optional.of(expectedTaskData)).when(mockTaskHandler).createTaskData(ambiance, manifest);

    Optional<TaskData> result = taskService.createTaskData(ambiance, manifest);

    assertThat(result).isNotEmpty();
    assertThat(result.get()).isEqualTo(expectedTaskData);
    verify(mockTaskHandler).createTaskData(ambiance, manifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateTaskDataEmptyTaskData() {
    final ManifestOutcome manifest = createMockManifest(MANIFEST_TYPE_1);
    setupMockMapHandler(MANIFEST_TYPE_1);

    doReturn(Optional.empty()).when(mockTaskHandler).createTaskData(ambiance, manifest);

    Optional<TaskData> result = taskService.createTaskData(ambiance, manifest);

    assertThat(result).isEmpty();
    verify(mockTaskHandler).createTaskData(ambiance, manifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testCreateTaskDataNoTaskHandler() {
    final ManifestOutcome manifest = createMockManifest(MANIFEST_TYPE_1);

    doReturn(Optional.empty()).when(mockTaskHandler).createTaskData(ambiance, manifest);

    Optional<TaskData> result = taskService.createTaskData(ambiance, manifest);

    assertThat(result).isEmpty();
    verify(mockTaskHandler, never()).createTaskData(ambiance, manifest);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResponses() {
    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    final ResponseData response1 = mock(ResponseData.class);
    final ResponseData response2 = mock(ResponseData.class);
    final ManifestOutcome manifest1 = createMockManifest(MANIFEST_TYPE_1);
    final ManifestOutcome manifest2 = createMockManifest(MANIFEST_TYPE_1);
    final ManifestOutcome manifest3 = createMockManifest(MANIFEST_TYPE_2);
    final ManifestOutcome updatedManifest1 = createMockManifest(MANIFEST_TYPE_1);
    final ManifestOutcome updatedManifest2 = createMockManifest(MANIFEST_TYPE_1);

    manifestsOutcome.put(MANIFEST_ID_1, manifest1);
    manifestsOutcome.put(MANIFEST_ID_2, manifest2);
    manifestsOutcome.put(MANIFEST_ID_3, manifest3);

    final Map<String, ResponseData> responses = ImmutableMap.of(TASK_ID_1, response1, TASK_ID_2, response2);
    final Map<String, String> taskIdMapping = ImmutableMap.of(TASK_ID_1, MANIFEST_ID_1, TASK_ID_2, MANIFEST_ID_2);

    setupMockMapHandler(MANIFEST_TYPE_1);

    doReturn(Optional.of(updatedManifest1)).when(mockTaskHandler).updateManifestOutcome(response1, manifest1);
    doReturn(Optional.of(updatedManifest2)).when(mockTaskHandler).updateManifestOutcome(response2, manifest2);

    taskService.handleTaskResponses(responses, manifestsOutcome, taskIdMapping);

    assertThat(manifestsOutcome.size()).isEqualTo(3);
    assertThat(manifestsOutcome.get(MANIFEST_ID_1)).isEqualTo(updatedManifest1);
    assertThat(manifestsOutcome.get(MANIFEST_ID_2)).isEqualTo(updatedManifest2);
    assertThat(manifestsOutcome.get(MANIFEST_ID_3)).isEqualTo(manifest3);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResponsesMissingTaskIdMapping() {
    final ResponseData response1 = mock(ResponseData.class);
    final Map<String, ResponseData> responses = ImmutableMap.of(TASK_ID_2, response1);
    final ManifestOutcome manifest1 = createMockManifest(MANIFEST_TYPE_1);
    final Map<String, String> taskIdMapping = ImmutableMap.of(TASK_ID_1, MANIFEST_ID_1);
    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    manifestsOutcome.put(MANIFEST_ID_1, manifest1);
    setupMockMapHandler(MANIFEST_TYPE_1);

    taskService.handleTaskResponses(responses, manifestsOutcome, taskIdMapping);
    assertThat(manifestsOutcome.size()).isEqualTo(1);
    assertThat(manifestsOutcome.get(MANIFEST_ID_1)).isEqualTo(manifest1);
    verify(mockTaskHandler, never()).updateManifestOutcome(any(ResponseData.class), any(ManifestOutcome.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResponsesMissingManifestInTaskMapping() {
    final ResponseData response1 = mock(ResponseData.class);
    final Map<String, ResponseData> responses = ImmutableMap.of(TASK_ID_1, response1);
    final ManifestOutcome manifest1 = createMockManifest(MANIFEST_TYPE_1);
    final Map<String, String> taskIdMapping = ImmutableMap.of(TASK_ID_1, MANIFEST_ID_2);
    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    manifestsOutcome.put(MANIFEST_ID_1, manifest1);
    setupMockMapHandler(MANIFEST_TYPE_1);

    taskService.handleTaskResponses(responses, manifestsOutcome, taskIdMapping);
    assertThat(manifestsOutcome.size()).isEqualTo(1);
    assertThat(manifestsOutcome.get(MANIFEST_ID_1)).isEqualTo(manifest1);
    verify(mockTaskHandler, never()).updateManifestOutcome(any(ResponseData.class), any(ManifestOutcome.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResponsesNoTaskHandler() {
    final ResponseData response1 = mock(ResponseData.class);
    final Map<String, ResponseData> responses = ImmutableMap.of(TASK_ID_1, response1);
    final ManifestOutcome manifest1 = createMockManifest(MANIFEST_TYPE_1);
    final Map<String, String> taskIdMapping = ImmutableMap.of(TASK_ID_1, MANIFEST_ID_1);
    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    manifestsOutcome.put(MANIFEST_ID_1, manifest1);
    setupMockMapHandler(MANIFEST_TYPE_2);

    taskService.handleTaskResponses(responses, manifestsOutcome, taskIdMapping);
    assertThat(manifestsOutcome.size()).isEqualTo(1);
    assertThat(manifestsOutcome.get(MANIFEST_ID_1)).isEqualTo(manifest1);
    verify(mockTaskHandler, never()).updateManifestOutcome(any(ResponseData.class), any(ManifestOutcome.class));
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResponsesErrorWithException() {
    final InvalidRequestException exception = new InvalidRequestException("Test exception");
    final ErrorNotifyResponseData errorResponse = ErrorNotifyResponseData.builder().exception(exception).build();
    final Map<String, ResponseData> responses = ImmutableMap.of(TASK_ID_1, errorResponse);
    final Map<String, String> taskIdMapping = ImmutableMap.of(TASK_ID_1, MANIFEST_ID_1);
    final ManifestOutcome manifest1 = createMockManifest(MANIFEST_TYPE_1);
    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    manifestsOutcome.put(MANIFEST_ID_1, manifest1);
    setupMockMapHandler(MANIFEST_TYPE_1);

    assertThatThrownBy(() -> taskService.handleTaskResponses(responses, manifestsOutcome, taskIdMapping))
        .isSameAs(exception);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testHandleTaskResponsesErrorWithErrorMessage() {
    final String errorMessage = "Something went wrong";
    ErrorNotifyResponseData errorResponse = ErrorNotifyResponseData.builder().errorMessage(errorMessage).build();
    final Map<String, ResponseData> responses = ImmutableMap.of(TASK_ID_1, errorResponse);
    final Map<String, String> taskIdMapping = ImmutableMap.of(TASK_ID_1, MANIFEST_ID_1);
    final ManifestOutcome manifest1 = createMockManifest(MANIFEST_TYPE_1);
    final ManifestsOutcome manifestsOutcome = new ManifestsOutcome();
    manifestsOutcome.put(MANIFEST_ID_1, manifest1);
    setupMockMapHandler(MANIFEST_TYPE_1);

    assertThatThrownBy(() -> taskService.handleTaskResponses(responses, manifestsOutcome, taskIdMapping))
        .hasStackTraceContaining(errorMessage);
  }

  private ManifestOutcome createMockManifest(String type) {
    ManifestOutcome manifestOutcome = mock(ManifestOutcome.class);
    doReturn(type).when(manifestOutcome).getType();
    return manifestOutcome;
  }

  private void setupMockMapHandler(String type) {
    doReturn(true).when(taskHandlersMap).containsKey(type);
    doReturn(mockTaskHandler).when(taskHandlersMap).get(type);
  }
}