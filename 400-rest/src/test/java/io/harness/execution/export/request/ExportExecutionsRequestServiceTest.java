/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export.request;

import static io.harness.rule.OwnerRule.GARVIT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.beans.CreatedByType;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExportExecutionsException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.export.request.ExportExecutionsRequest.ExportExecutionsRequestKeys;
import io.harness.execution.export.request.ExportExecutionsRequest.OutputFormat;
import io.harness.execution.export.request.ExportExecutionsRequest.Status;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.WorkflowExecution;

import com.google.inject.Inject;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;

public class ExportExecutionsRequestServiceTest extends WingsBaseTest {
  @Inject @InjectMocks private ExportExecutionsRequestService exportExecutionsRequestService;
  @Inject private HPersistence persistence;

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGet() {
    assertThatThrownBy(
        () -> exportExecutionsRequestService.get(RequestTestUtils.ACCOUNT_ID, RequestTestUtils.REQUEST_ID))
        .isInstanceOf(InvalidRequestException.class);

    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest(Status.READY);
    persistence.save(request);

    ExportExecutionsRequest savedRequest =
        exportExecutionsRequestService.get(RequestTestUtils.ACCOUNT_ID, RequestTestUtils.REQUEST_ID);
    assertThat(savedRequest).isNotNull();
    assertThat(savedRequest.getUuid()).isEqualTo(RequestTestUtils.REQUEST_ID);
    assertThat(savedRequest.getStatus()).isEqualTo(Status.READY);
    assertThat(savedRequest.getFileId()).isEqualTo(RequestTestUtils.FILE_ID);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetExpired() {
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest(Status.READY);
    request.setExpiresAt(System.currentTimeMillis() - 1000);
    persistence.save(request);

    ExportExecutionsRequest savedRequest =
        exportExecutionsRequestService.get(RequestTestUtils.ACCOUNT_ID, RequestTestUtils.REQUEST_ID);
    assertThat(savedRequest).isNotNull();
    assertThat(savedRequest.getUuid()).isEqualTo(RequestTestUtils.REQUEST_ID);
    assertThat(savedRequest.getStatus()).isEqualTo(Status.EXPIRED);
    assertThat(savedRequest.getFileId()).isNull();
  }

  @Test
  @Owner(developers = GARVIT)

  @Category(UnitTests.class)
  public void testQueueExportExecutionRequest() {
    saveWorkflowExecution();
    String requestId = exportExecutionsRequestService.queueExportExecutionRequest(RequestTestUtils.ACCOUNT_ID,
        persistence.createQuery(WorkflowExecution.class),
        ExportExecutionsUserParams.builder()
            .notifyOnlyTriggeringUser(false)
            .userGroupIds(asList("ug1", "ug2"))
            .build());

    ExportExecutionsRequest request = fetchRequest(requestId);
    assertThat(request).isNotNull();
    assertThat(request.getStatus()).isEqualTo(Status.QUEUED);
    assertThat(request.getOutputFormat()).isEqualTo(OutputFormat.JSON);
    assertThat(request.getQuery()).isNotNull();
    assertThat(request.isNotifyOnlyTriggeringUser()).isFalse();
    assertThat(request.getUserGroupIds()).containsExactly("ug1", "ug2");
    assertThat(request.getExpiresAt()).isNotNull();
    assertThat(request.getCreatedByType()).isEqualTo(CreatedByType.USER);

    requestId = exportExecutionsRequestService.queueExportExecutionRequest(RequestTestUtils.ACCOUNT_ID,
        persistence.createQuery(WorkflowExecution.class),
        ExportExecutionsUserParams.builder()
            .notifyOnlyTriggeringUser(true)
            .userGroupIds(asList("ug1", "ug2"))
            .createdByType(CreatedByType.API_KEY)
            .build());

    request = fetchRequest(requestId);
    assertThat(request).isNotNull();
    assertThat(request.getStatus()).isEqualTo(Status.QUEUED);
    assertThat(request.getOutputFormat()).isEqualTo(OutputFormat.JSON);
    assertThat(request.getQuery()).isNotNull();
    assertThat(request.isNotifyOnlyTriggeringUser()).isTrue();
    assertThat(request.getUserGroupIds()).isNull();
    assertThat(request.getExpiresAt()).isNotNull();
    assertThat(request.getCreatedByType()).isEqualTo(CreatedByType.API_KEY);
  }

  @Test
  @Owner(developers = GARVIT)

  @Category(UnitTests.class)
  public void testPrepareLimitChecks() {
    saveWorkflowExecution();
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    persistence.save(request);

    ExportExecutionsRequestLimitChecks limitChecks = exportExecutionsRequestService.prepareLimitChecks(
        RequestTestUtils.ACCOUNT_ID, persistence.createQuery(WorkflowExecution.class));
    assertThat(limitChecks).isNotNull();
    assertThat(limitChecks.getQueuedRequests()).isNotNull();
    assertThat(limitChecks.getQueuedRequests().getValue()).isEqualTo(1);
    assertThat(limitChecks.getExecutionCount()).isNotNull();
    assertThat(limitChecks.getExecutionCount().getValue()).isEqualTo(1);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testReadyRequest() {
    String fileId = "new_fid";
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    assertThatThrownBy(() -> exportExecutionsRequestService.readyRequest(request, fileId))
        .isInstanceOf(ExportExecutionsException.class);

    String requestId = persistence.save(request);
    exportExecutionsRequestService.readyRequest(request, fileId);
    assertThat(request.getStatus()).isEqualTo(Status.READY);
    assertThat(request.getFileId()).isEqualTo(fileId);

    ExportExecutionsRequest savedRequest = fetchRequest(requestId);
    assertThat(savedRequest.getStatus()).isEqualTo(Status.READY);
    assertThat(savedRequest.getFileId()).isEqualTo(fileId);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testFailRequest() {
    String errorMsg = "new_errMsg";
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    String requestId = persistence.save(request);

    exportExecutionsRequestService.failRequest(request, errorMsg);
    assertThat(request.getFileId()).isNull();
    assertThat(request.getStatus()).isEqualTo(Status.FAILED);
    assertThat(request.getErrorMessage()).isEqualTo(errorMsg);

    ExportExecutionsRequest savedRequest = fetchRequest(requestId);
    assertThat(savedRequest.getFileId()).isNull();
    assertThat(savedRequest.getStatus()).isEqualTo(Status.FAILED);
    assertThat(savedRequest.getErrorMessage()).isEqualTo(errorMsg);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testExpireRequest() {
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest(Status.READY);
    String requestId = persistence.save(request);

    String oldFileId = exportExecutionsRequestService.expireRequest(request);
    assertThat(request.getFileId()).isNull();
    assertThat(request.getStatus()).isEqualTo(Status.EXPIRED);
    assertThat(oldFileId).isEqualTo(RequestTestUtils.FILE_ID);

    ExportExecutionsRequest savedRequest = fetchRequest(requestId);
    assertThat(savedRequest.getFileId()).isNull();
    assertThat(savedRequest.getStatus()).isEqualTo(Status.EXPIRED);
  }

  private ExportExecutionsRequest fetchRequest(String requestId) {
    return persistence.createQuery(ExportExecutionsRequest.class)
        .filter(ExportExecutionsRequestKeys.uuid, requestId)
        .get();
  }

  private void saveWorkflowExecution() {
    persistence.save(WorkflowExecution.builder()
                         .accountId(RequestTestUtils.ACCOUNT_ID)
                         .appId("appId")
                         .status(ExecutionStatus.SUCCESS)
                         .endTs(System.currentTimeMillis() - 10 * 6 * 1000)
                         .build());
  }
}
