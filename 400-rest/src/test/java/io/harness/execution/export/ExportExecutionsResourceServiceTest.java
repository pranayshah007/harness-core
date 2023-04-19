/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.execution.export;

import static io.harness.rule.OwnerRule.GARVIT;
import static io.harness.rule.OwnerRule.VIKAS_S;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.exception.ExportExecutionsException;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.export.request.ExportExecutionsRequest;
import io.harness.execution.export.request.ExportExecutionsRequestHelper;
import io.harness.execution.export.request.ExportExecutionsRequestService;
import io.harness.execution.export.request.ExportExecutionsRequestSummary;
import io.harness.execution.export.request.ExportExecutionsUserParams;
import io.harness.execution.export.request.RequestTestUtils;
import io.harness.limits.ActionType;
import io.harness.limits.ConfiguredLimit;
import io.harness.limits.configuration.LimitConfigurationService;
import io.harness.limits.impl.model.StaticLimit;
import io.harness.rule.Owner;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.security.UserGroup;
import software.wings.service.intfc.UserGroupService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class ExportExecutionsResourceServiceTest extends CategoryTest {
  private static final String ACCOUNT_ID = "aid";
  private static final String REQUEST_ID = "rid";

  private final String validId = "valid_uid";
  private final String invalidId = "invalid_uid";
  private final UserGroup validUserGroup = UserGroup.builder().uuid(validId).build();
  private final List<String> listWithAllValidUserGroupIds = Collections.singletonList(validId);
  private final List<String> listWithOnlyInvalidGroupIds = Collections.singletonList(invalidId);
  private final List<String> listWithBothInvalidAndValidGroupIds = Arrays.asList(invalidId, validId);
  private final List<UserGroup> validUsers = Arrays.asList(validUserGroup);

  @Mock private ExportExecutionsRequestService exportExecutionsRequestService;
  @Mock private ExportExecutionsFileService exportExecutionsFileService;
  @Mock private ExportExecutionsRequestHelper exportExecutionsRequestHelper;
  @Mock private UserGroupService userGroupService;
  @Mock private LimitConfigurationService limitConfigurationService;
  @Inject @InjectMocks private ExportExecutionsResourceService exportExecutionsResourceService;

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Before
  public void setup() {
    when(limitConfigurationService.getOrDefault(eq(ACCOUNT_ID), eq(ActionType.EXPORT_EXECUTIONS_REQUEST)))
        .thenReturn(new ConfiguredLimit(ACCOUNT_ID, new StaticLimit(25), ActionType.EXPORT_EXECUTIONS_REQUEST));
    when(exportExecutionsRequestService.getTotalRequestsInLastDay(ACCOUNT_ID)).thenReturn(5L);
    when(userGroupService.fetchUserGroupNamesFromIds(listWithOnlyInvalidGroupIds)).thenReturn(Collections.emptyList());
    when(userGroupService.fetchUserGroupNamesFromIds(listWithAllValidUserGroupIds)).thenReturn(validUsers);
    when(userGroupService.fetchUserGroupNamesFromIds(listWithBothInvalidAndValidGroupIds)).thenReturn(validUsers);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetLimitChecks() {
    Query<WorkflowExecution> query = mock(Query.class);
    exportExecutionsResourceService.getLimitChecks(ACCOUNT_ID, query);
    verify(exportExecutionsRequestService, times(1)).prepareLimitChecks(eq(ACCOUNT_ID), eq(query));
  }

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testWhenNotifyOnlyTriggeringUserIsTrue() {
    Query<WorkflowExecution> query = mock(Query.class);
    // With notifyOnlyTriggeringUser true and userGroupIds not set export should work.
    ExportExecutionsUserParams userParamsWithGroupIdsNotSet =
        ExportExecutionsUserParams.builder().notifyOnlyTriggeringUser(true).build();
    exportExecutionsResourceService.export(ACCOUNT_ID, query, userParamsWithGroupIdsNotSet);
    verify(exportExecutionsRequestService, times(1))
        .queueExportExecutionRequest(eq(ACCOUNT_ID), eq(query), eq(userParamsWithGroupIdsNotSet));
    verify(exportExecutionsRequestService, times(1)).get(eq(ACCOUNT_ID), any());
    verify(exportExecutionsRequestHelper, times(1)).prepareSummary(any());

    // With notifyOnlyTriggeringUser true and not empty userGroupIds InvalidRequestException should be thrown
    // even if all group Ids are valid.
    ExportExecutionsUserParams userParamsWithValidUserIds = ExportExecutionsUserParams.builder()
                                                                .notifyOnlyTriggeringUser(true)
                                                                .userGroupIds(listWithAllValidUserGroupIds)
                                                                .build();
    assertThatThrownBy(() -> exportExecutionsResourceService.export(ACCOUNT_ID, null, userParamsWithValidUserIds))
        .isInstanceOf(InvalidRequestException.class);

    // With notifyOnlyTriggeringUser true and not empty userGroupIds InvalidRequestException should be thrown
    // even if all group Ids are mix of valid and invalid.
    ExportExecutionsUserParams userParamsWithBothValidAndInvalidUserIds =
        ExportExecutionsUserParams.builder()
            .notifyOnlyTriggeringUser(true)
            .userGroupIds(listWithBothInvalidAndValidGroupIds)
            .build();
    assertThatThrownBy(
        () -> exportExecutionsResourceService.export(ACCOUNT_ID, null, userParamsWithBothValidAndInvalidUserIds))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void testWhenNotifyOnlyTriggeringUserIsFalse() {
    Query<WorkflowExecution> query = mock(Query.class);

    // With both notifyOnlyTriggeringUser and empty userGroupIds export should work.
    ExportExecutionsUserParams userParamsWithEmptyGroupIds =
        ExportExecutionsUserParams.builder().notifyOnlyTriggeringUser(false).build();
    exportExecutionsResourceService.export(ACCOUNT_ID, query, userParamsWithEmptyGroupIds);
    verify(exportExecutionsRequestService, times(1))
        .queueExportExecutionRequest(eq(ACCOUNT_ID), eq(query), eq(userParamsWithEmptyGroupIds));
    verify(exportExecutionsRequestService, times(1)).get(eq(ACCOUNT_ID), any());
    verify(exportExecutionsRequestHelper, times(1)).prepareSummary(any());

    // With notifyOnlyTriggeringUser false and not empty userGroupIds export should work.
    ExportExecutionsUserParams userParamsWithValidUserIds = ExportExecutionsUserParams.builder()
                                                                .notifyOnlyTriggeringUser(false)
                                                                .userGroupIds(listWithAllValidUserGroupIds)
                                                                .build();
    exportExecutionsResourceService.export(ACCOUNT_ID, query, userParamsWithValidUserIds);
    verify(exportExecutionsRequestService, times(1))
        .queueExportExecutionRequest(eq(ACCOUNT_ID), eq(query), eq(userParamsWithValidUserIds));
    verify(exportExecutionsRequestService, times(2)).get(eq(ACCOUNT_ID), any());
    verify(exportExecutionsRequestHelper, times(2)).prepareSummary(any());
  }

  // InvalidRequestException should be thrown if a subset of Provided groups Ids are invalid.
  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void exportExecutionSomeInvalidGroupIdsTest() {
    Query<WorkflowExecution> query = mock(Query.class);
    ExportExecutionsUserParams userParamsWithValidAndInvalidUsers =
        ExportExecutionsUserParams.builder()
            .notifyOnlyTriggeringUser(false)
            .userGroupIds(listWithBothInvalidAndValidGroupIds)
            .build();
    assertThatThrownBy(
        () -> exportExecutionsResourceService.export(ACCOUNT_ID, query, userParamsWithValidAndInvalidUsers))
        .isInstanceOf(InvalidRequestException.class);
  }

  // InvalidRequestException should be thrown if all Provided group Ids are invalid.
  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void exportExecutionAllInvalidGroupIdsTest() {
    Query<WorkflowExecution> query = mock(Query.class);
    ExportExecutionsUserParams userParams1 = ExportExecutionsUserParams.builder()
                                                 .notifyOnlyTriggeringUser(false)
                                                 .userGroupIds(listWithOnlyInvalidGroupIds)
                                                 .build();
    assertThatThrownBy(() -> exportExecutionsResourceService.export(ACCOUNT_ID, query, userParams1))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testExport() {
    Query<WorkflowExecution> query = mock(Query.class);
    ExportExecutionsUserParams userParams = ExportExecutionsUserParams.builder()
                                                .notifyOnlyTriggeringUser(false)
                                                .userGroupIds(listWithAllValidUserGroupIds)
                                                .build();

    exportExecutionsResourceService.export(ACCOUNT_ID, query, userParams);
    verify(exportExecutionsRequestService, times(1))
        .queueExportExecutionRequest(eq(ACCOUNT_ID), eq(query), eq(userParams));
    verify(exportExecutionsRequestService, times(1)).get(eq(ACCOUNT_ID), any());
    verify(exportExecutionsRequestHelper, times(1)).prepareSummary(any());
  }

  // InvalidRequestException should be thrown if number of export executions requests from this account
  // exceed configured its daily limit.
  @Test
  @Owner(developers = VIKAS_S)
  @Category(UnitTests.class)
  public void exportExecutionLimitTest() {
    Query<WorkflowExecution> query = mock(Query.class);
    ExportExecutionsUserParams userParams = ExportExecutionsUserParams.builder()
                                                .notifyOnlyTriggeringUser(false)
                                                .userGroupIds(listWithAllValidUserGroupIds)
                                                .build();
    when(exportExecutionsRequestService.getTotalRequestsInLastDay(ACCOUNT_ID)).thenReturn(25L);
    assertThatThrownBy(() -> exportExecutionsResourceService.export(ACCOUNT_ID, query, userParams))
        .isInstanceOf(InvalidRequestException.class);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testGetStatusJson() throws IOException {
    ExportExecutionsRequest request = RequestTestUtils.prepareExportExecutionsRequest();
    when(exportExecutionsRequestService.get(ACCOUNT_ID, REQUEST_ID)).thenReturn(request);
    when(exportExecutionsRequestHelper.prepareSummary(request))
        .thenReturn(ExportExecutionsRequestSummary.builder().requestId(request.getUuid()).build());
    String json = exportExecutionsResourceService.getStatusJson(ACCOUNT_ID, REQUEST_ID);
    assertThat(json).isNotNull();
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> map = mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    assertThat(map).isNotNull();
    assertThat(map.keySet()).containsExactlyInAnyOrder("requestId", "totalExecutions");
    assertThat(map.get("requestId")).isEqualTo(request.getUuid());
    assertThat(map.get("totalExecutions")).isEqualTo(0);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testDownloadFile() throws IOException {
    when(exportExecutionsRequestService.get(ACCOUNT_ID, REQUEST_ID))
        .thenReturn(RequestTestUtils.prepareExportExecutionsRequest());
    Throwable throwable = catchThrowable(() -> exportExecutionsResourceService.downloadFile(ACCOUNT_ID, REQUEST_ID));
    assertThat(throwable).isInstanceOf(WebApplicationException.class);
    assertThat(((WebApplicationException) throwable).getResponse().getStatus())
        .isEqualTo(Response.Status.ACCEPTED.getStatusCode());

    ExportExecutionsRequest request =
        RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.READY);
    request.setFileId(null);
    when(exportExecutionsRequestService.get(ACCOUNT_ID, REQUEST_ID)).thenReturn(request);
    throwable = catchThrowable(() -> exportExecutionsResourceService.downloadFile(ACCOUNT_ID, REQUEST_ID));
    assertThat(throwable).isInstanceOf(ExportExecutionsException.class);

    when(exportExecutionsRequestService.get(ACCOUNT_ID, REQUEST_ID))
        .thenReturn(RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.READY));
    StreamingOutput streamingOutput = exportExecutionsResourceService.downloadFile(ACCOUNT_ID, REQUEST_ID);
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
    streamingOutput.write(byteArrayOutputStream);
    verify(exportExecutionsFileService).downloadFileToStream(eq(RequestTestUtils.FILE_ID), any());

    when(exportExecutionsRequestService.get(ACCOUNT_ID, REQUEST_ID))
        .thenReturn(RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.FAILED));
    throwable = catchThrowable(() -> exportExecutionsResourceService.downloadFile(ACCOUNT_ID, REQUEST_ID));
    assertThat(throwable).isInstanceOf(WebApplicationException.class);
    assertThat(((WebApplicationException) throwable).getResponse().getStatus())
        .isEqualTo(Response.Status.GONE.getStatusCode());

    when(exportExecutionsRequestService.get(ACCOUNT_ID, REQUEST_ID))
        .thenReturn(RequestTestUtils.prepareExportExecutionsRequest(ExportExecutionsRequest.Status.EXPIRED));
    throwable = catchThrowable(() -> exportExecutionsResourceService.downloadFile(ACCOUNT_ID, REQUEST_ID));
    assertThat(throwable).isInstanceOf(WebApplicationException.class);
    assertThat(((WebApplicationException) throwable).getResponse().getStatus())
        .isEqualTo(Response.Status.GONE.getStatusCode());
  }
}
