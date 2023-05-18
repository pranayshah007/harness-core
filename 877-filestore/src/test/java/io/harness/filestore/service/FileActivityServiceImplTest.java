/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.filestore.service.impl.FileActivityServiceImpl;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(CDP)
@RunWith(MockitoJUnitRunner.class)
public class FileActivityServiceImplTest extends CategoryTest {
  @Mock private NGActivityService ngActivityService;

  @InjectMocks private FileActivityServiceImpl fileActivityService;

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateFileCreationActivity() {
    FileDTO fileDTO = createFileDTO();
    fileActivityService.createFileCreationActivity(fileDTO.getAccountIdentifier(), fileDTO);

    ArgumentCaptor<NGActivityDTO> ngActivityDTOArgumentCaptor = ArgumentCaptor.forClass(NGActivityDTO.class);
    verify(ngActivityService, times(1)).save(ngActivityDTOArgumentCaptor.capture());

    NGActivityDTO ngActivityDTO = ngActivityDTOArgumentCaptor.getValue();
    assertThat(ngActivityDTO.getAccountIdentifier()).isEqualTo(fileDTO.getAccountIdentifier());
    assertThat(ngActivityDTO.getActivityStatus().name()).isEqualTo(NGActivityStatus.SUCCESS.name());
    assertThat(ngActivityDTO.getDescription()).isEqualTo("File Created");
    assertThat(ngActivityDTO.getReferredEntity().getName()).isEqualTo(fileDTO.getName());
    assertThat(ngActivityDTO.getReferredEntity().getType()).isEqualTo(EntityType.FILES);
    assertThat(ngActivityDTO.getReferredEntity().getEntityRef().getIdentifier()).isEqualTo(fileDTO.getIdentifier());
    assertThat(ngActivityDTO.getReferredEntity().getEntityRef().getAccountIdentifier())
        .isEqualTo(fileDTO.getAccountIdentifier());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateFileUpdateActivity() {
    FileDTO fileDTO = createFileDTO();
    fileActivityService.createFileUpdateActivity(fileDTO.getAccountIdentifier(), fileDTO);

    ArgumentCaptor<NGActivityDTO> ngActivityDTOArgumentCaptor = ArgumentCaptor.forClass(NGActivityDTO.class);
    verify(ngActivityService, times(1)).save(ngActivityDTOArgumentCaptor.capture());

    NGActivityDTO ngActivityDTO = ngActivityDTOArgumentCaptor.getValue();
    assertThat(ngActivityDTO.getAccountIdentifier()).isEqualTo(fileDTO.getAccountIdentifier());
    assertThat(ngActivityDTO.getActivityStatus().name()).isEqualTo(NGActivityStatus.SUCCESS.name());
    assertThat(ngActivityDTO.getDescription()).isEqualTo("File Updated");
    assertThat(ngActivityDTO.getReferredEntity().getName()).isEqualTo(fileDTO.getName());
    assertThat(ngActivityDTO.getReferredEntity().getType()).isEqualTo(EntityType.FILES);
    assertThat(ngActivityDTO.getReferredEntity().getEntityRef().getIdentifier()).isEqualTo(fileDTO.getIdentifier());
    assertThat(ngActivityDTO.getReferredEntity().getEntityRef().getAccountIdentifier())
        .isEqualTo(fileDTO.getAccountIdentifier());
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testDeleteAllActivities() {
    String accountIdentifier = "accountIdentifier";
    String fileFQN = "accountIdentifier/identifier";

    fileActivityService.deleteAllActivities(accountIdentifier, fileFQN);

    verify(ngActivityService, times(1))
        .deleteAllActivitiesOfAnEntity(eq(accountIdentifier), eq(fileFQN), eq(EntityType.FILES));
  }

  private FileDTO createFileDTO() {
    return FileDTO.builder()
        .accountIdentifier("accountIdentifier")
        .type(NGFileType.FILE)
        .identifier("identifier")
        .parentIdentifier("parentIdentifier")
        .name("name")
        .description("description")
        .mimeType("yaml")
        .build();
  }
}
