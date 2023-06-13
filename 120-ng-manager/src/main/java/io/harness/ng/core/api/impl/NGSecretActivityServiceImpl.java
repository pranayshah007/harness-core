/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.api.impl;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EntityReference;
import io.harness.beans.IdentifierRef;
import io.harness.ng.core.EntityDetail;
import io.harness.ng.core.activityhistory.NGActivityStatus;
import io.harness.ng.core.activityhistory.NGActivityType;
import io.harness.ng.core.activityhistory.dto.NGActivityDTO;
import io.harness.ng.core.activityhistory.service.NGActivityService;
import io.harness.ng.core.api.NGSecretActivityService;
import io.harness.ng.core.dto.secrets.SecretDTOV2;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class NGSecretActivityServiceImpl implements NGSecretActivityService {
  public static final String CREATION_DESCRIPTION = "Secret Created";
  public static final String UPDATE_DESCRIPTION = "Secret Updated";
  private final NGActivityService ngActivityService;

  @Override
  public void create(String accountIdentifier, SecretDTOV2 secret, NGActivityType ngActivityType) {
    if (ngActivityType == NGActivityType.ENTITY_CREATION) {
      createSecretCreationActivity(accountIdentifier, secret);
    } else if (ngActivityType == NGActivityType.ENTITY_UPDATE) {
      createSecretUpdateActivity(accountIdentifier, secret);
    }
  }

  private EntityDetail getSecretEntityDetail(String accountIdentifier, SecretDTOV2 secret) {
    IdentifierRef entityRef = IdentifierRefHelper.getIdentifierRefFromEntityIdentifiers(
        secret.getIdentifier(), accountIdentifier, secret.getOrgIdentifier(), secret.getProjectIdentifier());
    return EntityDetail.builder().type(EntityType.SECRETS).name(secret.getName()).entityRef(entityRef).build();
  }

  private NGActivityDTO createNGActivityObject(
      String accountIdentifier, SecretDTOV2 secret, String activityDescription, NGActivityType type) {
    EntityDetail referredEntity = getSecretEntityDetail(accountIdentifier, secret);
    return NGActivityDTO.builder()
        .accountIdentifier(accountIdentifier)
        .activityStatus(NGActivityStatus.SUCCESS)
        .description(activityDescription)
        .referredEntity(referredEntity)
        .type(type)
        .activityTime(System.currentTimeMillis())
        .build();
  }

  private void createSecretCreationActivity(String accountIdentifier, SecretDTOV2 secret) {
    NGActivityDTO creationActivity =
        createNGActivityObject(accountIdentifier, secret, CREATION_DESCRIPTION, NGActivityType.ENTITY_CREATION);
    ngActivityService.save(creationActivity);
  }

  private void createSecretUpdateActivity(String accountIdentifier, SecretDTOV2 secret) {
    NGActivityDTO creationActivity =
        createNGActivityObject(accountIdentifier, secret, UPDATE_DESCRIPTION, NGActivityType.ENTITY_UPDATE);
    ngActivityService.save(creationActivity);
  }

  @Override
  public void deleteAllActivities(String accountIdentifier, String secretFQN) {
    ngActivityService.deleteAllActivitiesOfAnEntity(accountIdentifier, secretFQN, EntityType.SECRETS);
  }

  @Override
  public void incrementSecretUsageCount(EntityReference entityRef) {}
}
