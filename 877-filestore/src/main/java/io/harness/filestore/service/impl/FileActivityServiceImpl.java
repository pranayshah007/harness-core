/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.filestore.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.filestore.service.FileActivityService;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.ng.core.filestore.dto.FileDTO;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDP)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class FileActivityServiceImpl implements FileActivityService {
  private static final String CREATION_DESCRIPTION = "File Created";
  private static final String UPDATE_DESCRIPTION = "File Updated";

  private final NGActivityService ngActivityService;

  @Override
  public void createFileCreationActivity(String accountIdentifier, FileDTO fileDTO) {
    NGActivityDTO creationActivity =
        createFileNGActivityDTO(accountIdentifier, fileDTO, CREATION_DESCRIPTION, NGActivityType.ENTITY_CREATION);
    ngActivityService.save(creationActivity);
  }

  @Override
  public void createFileUpdateActivity(String accountIdentifier, FileDTO fileDTO) {
    NGActivityDTO creationActivity =
        createFileNGActivityDTO(accountIdentifier, fileDTO, UPDATE_DESCRIPTION, NGActivityType.ENTITY_UPDATE);
    ngActivityService.save(creationActivity);
  }

  @Override
  public void deleteAllActivities(String accountIdentifier, String fileFQN) {
    ngActivityService.deleteAllActivitiesOfAnEntity(accountIdentifier, fileFQN, EntityType.FILES);
  }

  private NGActivityDTO createFileNGActivityDTO(
      String accountIdentifier, FileDTO file, String activityDescription, NGActivityType type) {
    EntityDetail referredEntity = getFileEntityDetail(accountIdentifier, file);
    return NGActivityDTO.builder()
        .accountIdentifier(accountIdentifier)
        .activityStatus(NGActivityStatus.SUCCESS)
        .description(activityDescription)
        .referredEntity(referredEntity)
        .type(type)
        .activityTime(System.currentTimeMillis())
        .build();
  }

  private EntityDetail getFileEntityDetail(String accountIdentifier, FileDTO file) {
    IdentifierRef entityRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(
        file.getIdentifier(), accountIdentifier, file.getOrgIdentifier(), file.getProjectIdentifier());
    return EntityDetail.builder().type(EntityType.FILES).name(file.getName()).entityRef(entityRef).build();
  }
}
