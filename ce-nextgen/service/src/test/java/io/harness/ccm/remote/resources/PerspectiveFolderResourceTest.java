/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.remote.resources;

import static io.harness.rule.OwnerRule.TRUNAPUSHPA;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.ccm.audittrails.events.PerspectiveFolderCreateEvent;
import io.harness.ccm.audittrails.events.PerspectiveFolderDeleteEvent;
import io.harness.ccm.audittrails.events.PerspectiveFolderUpdateEvent;
import io.harness.ccm.rbac.CCMRbacHelper;
import io.harness.ccm.remote.resources.perspectives.PerspectiveFolderResource;
import io.harness.ccm.views.dto.CreatePerspectiveFolderDTO;
import io.harness.ccm.views.dto.MovePerspectiveDTO;
import io.harness.ccm.views.entities.CEView;
import io.harness.ccm.views.entities.CEViewFolder;
import io.harness.ccm.views.entities.ViewState;
import io.harness.ccm.views.entities.ViewType;
import io.harness.ccm.views.graphql.QLCEView;
import io.harness.ccm.views.service.CEViewFolderService;
import io.harness.ccm.views.service.CEViewService;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.outbox.api.OutboxService;
import io.harness.rule.Owner;
import io.harness.telemetry.TelemetryReporter;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

@RunWith(MockitoJUnitRunner.class)
public class PerspectiveFolderResourceTest extends CategoryTest {
  private CEViewService ceViewService = mock(CEViewService.class);
  private CEViewFolderService ceViewFolderService = mock(CEViewFolderService.class);
  private CCMRbacHelper rbacHelper = mock(CCMRbacHelper.class);
  private TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
  private OutboxService outboxService = mock(OutboxService.class);
  private PerspectiveFolderResource perspectiveFolderResource;

  private final String ACCOUNT_ID = "ACCOUNT_ID";
  private final String PERSPECTIVE_ID = "PERSPECTIVE_ID";
  private final String FOLDER_ID = "FOLDER_ID";
  private final String PERSPECTIVE_NAME = "PERSPECTIVE_NAME";
  private final String FOLDER_NAME = "FOLDER_NAME";
  private final ViewState PERSPECTIVE_STATE = ViewState.DRAFT;
  private final ViewType VIEW_TYPE = ViewType.CUSTOMER;
  private final String perspectiveVersion = "v1";

  private CEView perspective;
  private CEViewFolder perspectiveFolder;
  private CEViewFolder perspectiveFolderDTO;
  private QLCEView qlceView;

  @Captor private ArgumentCaptor<PerspectiveFolderCreateEvent> perspectiveFolderCreateEventArgumentCaptor;
  @Captor private ArgumentCaptor<PerspectiveFolderUpdateEvent> perspectiveFolderUpdateEventArgumentCaptor;
  @Captor private ArgumentCaptor<PerspectiveFolderDeleteEvent> perspectiveFolderDeleteEventArgumentCaptor;

  @Mock private TelemetryReporter telemetryReporter;

  @Before
  public void setUp() throws IllegalAccessException, IOException {
    perspectiveFolder =
        CEViewFolder.builder().uuid(FOLDER_ID).name(FOLDER_NAME).accountId(ACCOUNT_ID).viewType(VIEW_TYPE).build();
    perspective = CEView.builder()
                      .uuid(PERSPECTIVE_ID)
                      .name(PERSPECTIVE_NAME)
                      .accountId(ACCOUNT_ID)
                      .viewState(PERSPECTIVE_STATE)
                      .viewType(VIEW_TYPE)
                      .viewVersion(perspectiveVersion)
                      .build();
    qlceView = QLCEView.builder().id(PERSPECTIVE_ID).name(PERSPECTIVE_NAME).folderId(FOLDER_ID).build();
    when(ceViewService.getAllViews(any(), any(), anyBoolean(), any())).thenReturn(Collections.singletonList(qlceView));
    when(ceViewFolderService.save(any())).thenReturn(perspectiveFolder);
    when(ceViewFolderService.getFolders(any(), anyString())).thenReturn(Collections.singletonList(perspectiveFolder));
    when(ceViewFolderService.getFolders(any(), anyList())).thenReturn(Collections.singletonList(perspectiveFolder));
    when(ceViewFolderService.updateFolder(any(), any())).thenReturn(perspectiveFolder);
    when(transactionTemplate.execute(any()))
        .thenAnswer(invocationOnMock
            -> invocationOnMock.getArgument(0, TransactionCallback.class)
                   .doInTransaction(new SimpleTransactionStatus()));
    when(ceViewFolderService.moveMultipleCEViews(any(), any(), any()))
        .thenReturn(Collections.singletonList(perspective));
    when(ceViewFolderService.delete(any(), any())).thenReturn(true);
    perspectiveFolderResource = new PerspectiveFolderResource(
        ceViewFolderService, ceViewService, telemetryReporter, transactionTemplate, outboxService, rbacHelper);
    perspectiveFolderDTO = perspectiveFolder.toDTO();
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testCreatePerspectiveFolder() {
    perspectiveFolderResource.create(
        ACCOUNT_ID, CreatePerspectiveFolderDTO.builder().ceViewFolder(perspectiveFolder).build());
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(perspectiveFolderCreateEventArgumentCaptor.capture());
    PerspectiveFolderCreateEvent perspectiveFolderCreateEvent = perspectiveFolderCreateEventArgumentCaptor.getValue();
    assertThat(perspectiveFolderDTO).isEqualTo(perspectiveFolderCreateEvent.getPerspectiveFolderDTO());
    verify(ceViewFolderService).save(perspectiveFolder);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetFolders() {
    Set<String> perspectiveFolders = new HashSet<>();
    perspectiveFolders.add(FOLDER_ID);
    when(rbacHelper.checkFolderIdsGivenPermission(any(), any(), any(), any(), any())).thenReturn(perspectiveFolders);
    ResponseDTO<List<CEViewFolder>> response = perspectiveFolderResource.getFolders(ACCOUNT_ID, "");
    assertThat(response.getData().size()).isEqualTo(1);
    assertThat(response.getData().get(0)).isEqualTo(perspectiveFolder);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testGetPerspectivesInAFolder() {
    ResponseDTO<List<QLCEView>> response = perspectiveFolderResource.getPerspectives(ACCOUNT_ID, FOLDER_ID);
    assertThat(response.getData().size()).isEqualTo(1);
    assertThat(response.getData().get(0)).isEqualTo(qlceView);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void testUpdate() {
    ResponseDTO<CEViewFolder> response = perspectiveFolderResource.updateFolder(ACCOUNT_ID, perspectiveFolder);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(perspectiveFolderUpdateEventArgumentCaptor.capture());
    PerspectiveFolderUpdateEvent perspectiveFolderUpdateEvent = perspectiveFolderUpdateEventArgumentCaptor.getValue();
    assertThat(perspectiveFolderDTO).isEqualTo(perspectiveFolderUpdateEvent.getPerspectiveFolderDTO());
    assertThat(response.getData()).isEqualTo(perspectiveFolder);
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void movePerspectives() {
    Set<String> perspectiveFolders = new HashSet<>();
    perspectiveFolders.add(FOLDER_ID);
    when(rbacHelper.checkFolderIdsGivenPermission(any(), any(), any(), any(), any())).thenReturn(perspectiveFolders);
    ResponseDTO<List<CEView>> response = perspectiveFolderResource.movePerspectives(ACCOUNT_ID,
        MovePerspectiveDTO.builder()
            .perspectiveIds(Collections.singletonList(PERSPECTIVE_ID))
            .newFolderId(FOLDER_ID)
            .build());
    assertThat(response.getData()).isEqualTo(Collections.singletonList(perspective));
  }

  @Test
  @Owner(developers = TRUNAPUSHPA)
  @Category(UnitTests.class)
  public void deleteFolder() {
    ResponseDTO<Boolean> response = perspectiveFolderResource.delete(ACCOUNT_ID, FOLDER_ID);
    verify(transactionTemplate, times(1)).execute(any());
    verify(outboxService, times(1)).save(perspectiveFolderDeleteEventArgumentCaptor.capture());
    PerspectiveFolderDeleteEvent perspectiveFolderDeleteEvent = perspectiveFolderDeleteEventArgumentCaptor.getValue();
    assertThat(perspectiveFolderDTO).isEqualTo(perspectiveFolderDeleteEvent.getPerspectiveFolderDTO());
    assertThat(response.getData()).isEqualTo(true);
  }
}
