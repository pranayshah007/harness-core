/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.service;

import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.idp.scorecard.checks.mappers.CheckDetailsMapper;
import io.harness.idp.scorecard.checks.mappers.CheckMapper;
import io.harness.idp.scorecard.checks.repositories.CheckRepository;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.CheckListItem;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class CheckServiceImpl implements CheckService {
  private final CheckRepository checkRepository;
  private final NGSettingsClient settingsClient;
  private final EntitySetupUsageClient entitySetupUsageClient;
  @Inject
  public CheckServiceImpl(
      CheckRepository checkRepository, NGSettingsClient settingsClient, EntitySetupUsageClient entitySetupUsageClient) {
    this.checkRepository = checkRepository;
    this.settingsClient = settingsClient;
    this.entitySetupUsageClient = entitySetupUsageClient;
  }

  @Override
  public void createCheck(CheckDetails checkDetails, String accountIdentifier) {
    checkRepository.save(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
  }

  @Override
  public void updateCheck(CheckDetails checkDetails, String accountIdentifier) {
    checkRepository.update(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
  }

  @Override
  public List<CheckListItem> getChecksByAccountId(boolean custom, String accountIdentifier) {
    List<CheckEntity> entities =
        checkRepository.findByAccountIdentifierAndIsCustomAndIsDeleted(accountIdentifier, custom, false);
    List<CheckListItem> checks = new ArrayList<>();
    entities.forEach(entity -> checks.add(CheckMapper.toDTO(entity)));
    return checks;
  }

  @Override
  public List<CheckEntity> getActiveChecks(String accountIdentifier, List<String> checkIdentifiers) {
    // TODO: include GLOBALACCOUNT as well
    return checkRepository.findByAccountIdentifierAndIsDeletedAndIdentifierIn(
        accountIdentifier, false, checkIdentifiers);
  }

  @Override
  public CheckDetails getCheckDetails(String accountIdentifier, String identifier, boolean custom) {
    String accountId = custom ? accountIdentifier : GLOBAL_ACCOUNT_ID;
    CheckEntity checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(accountId, identifier);
    return CheckDetailsMapper.toDTO(checkEntity);
  }

  @Override
  public List<CheckEntity> getChecksByAccountIdsAndIdentifiers(
      List<String> accountIdentifiers, Set<String> identifiers) {
    return checkRepository.findByAccountIdentifierInAndIdentifierIn(accountIdentifiers, identifiers);
  }

  @Override
  public void deleteCustomCheck(String accountIdentifier, String identifier, boolean forceDelete) {
    if (forceDelete && !isForceDeleteSettingEnabled(accountIdentifier)) {
      throw new InvalidRequestException(
          format("Parameter forceDelete cannot be true. Force deletion of secret is not enabled for this account [%s]",
              accountIdentifier));
    }
    if (!forceDelete) {
      validateCheckUsage(accountIdentifier, identifier);
    } else {
      UpdateResult updateResult = checkRepository.updateDeleted(accountIdentifier, identifier);
      if (updateResult.getModifiedCount() == 0) {
        throw new InvalidRequestException("Default checks cannot be deleted");
      }
    }
  }

  private void validateCheckUsage(String accountIdentifier, String checkIdentifier) {
    boolean isReferenced;
    IdentifierRef identifierRef = IdentifierRef.builder()
                                      .accountIdentifier(accountIdentifier)
                                      .orgIdentifier(null)
                                      .projectIdentifier(null)
                                      .identifier(checkIdentifier)
                                      .build();
    try {
      isReferenced = NGRestUtils.getResponse(entitySetupUsageClient.isEntityReferenced(
          accountIdentifier, identifierRef.getFullyQualifiedName(), EntityType.IDP_CHECK));
    } catch (Exception e) {
      log.info("Encountered exception while requesting the Entity Reference records for checkId {}, with exception",
          checkIdentifier, e);
      throw new UnexpectedException("Error while deleting the check");
    }
    if (isReferenced) {
      throw new ReferencedEntityException(
          format("Could not delete the check [%s] as it is referenced by other scorecards", checkIdentifier));
    }
  }

  protected boolean isForceDeleteSettingEnabled(String accountIdentifier) {
    return parseBoolean(NGRestUtils
                            .getResponse(settingsClient.getSetting(
                                SettingIdentifiers.ENABLE_FORCE_DELETE, accountIdentifier, null, null))
                            .getValue());
  }
}
