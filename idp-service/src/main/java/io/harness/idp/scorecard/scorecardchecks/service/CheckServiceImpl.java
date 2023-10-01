/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.scorecardchecks.service;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.idp.common.CommonUtils.addGlobalAccountIdentifierAlong;
import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.GLOBAL_ACCOUNT_ID;

import static java.lang.Boolean.parseBoolean;
import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.EntityType;
import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.entitysetupusageclient.remote.EntitySetupUsageClient;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ReferencedEntityException;
import io.harness.exception.UnexpectedException;
import io.harness.idp.scorecard.datapoints.service.DataPointService;
import io.harness.idp.scorecard.scorecardchecks.entity.CheckEntity;
import io.harness.idp.scorecard.scorecardchecks.mappers.CheckDetailsMapper;
import io.harness.idp.scorecard.scorecardchecks.repositories.CheckRepository;
import io.harness.ngsettings.SettingIdentifiers;
import io.harness.ngsettings.client.remote.NGSettingsClient;
import io.harness.remote.client.NGRestUtils;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.DataPoint;
import io.harness.spec.server.idp.v1.model.Rule;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.IDP)
@Slf4j
public class CheckServiceImpl implements CheckService {
  private final CheckRepository checkRepository;
  private final NGSettingsClient settingsClient;
  private final EntitySetupUsageClient entitySetupUsageClient;
  private final DataPointService dataPointService;
  @Inject
  public CheckServiceImpl(CheckRepository checkRepository, NGSettingsClient settingsClient,
      EntitySetupUsageClient entitySetupUsageClient, DataPointService dataPointService) {
    this.checkRepository = checkRepository;
    this.settingsClient = settingsClient;
    this.entitySetupUsageClient = entitySetupUsageClient;
    this.dataPointService = dataPointService;
  }

  @Override
  public void createCheck(CheckDetails checkDetails, String accountIdentifier) {
    validateCheckSaveRequest(checkDetails, accountIdentifier);
    checkRepository.save(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
  }

  @Override
  public void updateCheck(CheckDetails checkDetails, String accountIdentifier) {
    validateCheckSaveRequest(checkDetails, accountIdentifier);
    CheckEntity checkEntity = checkRepository.update(CheckDetailsMapper.fromDTO(checkDetails, accountIdentifier));
    if (checkEntity == null) {
      throw new InvalidRequestException("Default checks cannot be updated");
    }
  }

  @Override
  public Page<CheckEntity> getChecksByAccountId(
      Boolean custom, String accountIdentifier, Pageable pageRequest, String searchTerm) {
    Criteria criteria = buildCriteriaForChecksList(accountIdentifier, custom, searchTerm);
    return checkRepository.findAll(criteria, pageRequest);
  }

  @Override
  public List<CheckEntity> getActiveChecks(String accountIdentifier, List<String> checkIdentifiers) {
    return checkRepository.findByAccountIdentifierInAndIsDeletedAndIdentifierIn(
        addGlobalAccountIdentifierAlong(accountIdentifier), false, checkIdentifiers);
  }

  @Override
  public CheckDetails getCheckDetails(String accountIdentifier, String identifier, Boolean custom) {
    CheckEntity checkEntity;
    if (Boolean.TRUE.equals(custom)) {
      checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(accountIdentifier, identifier);
    } else {
      checkEntity = checkRepository.findByAccountIdentifierAndIdentifier(GLOBAL_ACCOUNT_ID, identifier);
    }
    if (checkEntity == null) {
      throw new InvalidRequestException(String.format("Check details not found for checkId [%s]", identifier));
    }
    return CheckDetailsMapper.toDTO(checkEntity);
  }

  @Override
  public List<CheckEntity> getChecksByAccountIdAndIdentifiers(String accountIdentifier, Set<String> identifiers) {
    return checkRepository.findByAccountIdentifierInAndIdentifierIn(
        addGlobalAccountIdentifierAlong(accountIdentifier), identifiers);
  }

  @Override
  public void deleteCustomCheck(String accountIdentifier, String identifier, boolean forceDelete) {
    if (forceDelete && !isForceDeleteSettingEnabled(accountIdentifier)) {
      throw new InvalidRequestException(
          format("Parameter forceDelete cannot be true. Force deletion of check is not enabled for this account [%s]",
              accountIdentifier));
    }
    if (!forceDelete) {
      validateCheckUsage(accountIdentifier, identifier);
    }

    UpdateResult updateResult = checkRepository.updateDeleted(accountIdentifier, identifier);
    if (updateResult.getModifiedCount() == 0) {
      throw new InvalidRequestException("Default checks cannot be deleted");
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

  private Criteria buildCriteriaForChecksList(String accountIdentifier, Boolean custom, String searchTerm) {
    Criteria criteria = new Criteria();
    if (custom == null) {
      criteria.and(CheckEntity.CheckKeys.accountIdentifier).in(addGlobalAccountIdentifierAlong(accountIdentifier));
    } else {
      String accountId = custom ? accountIdentifier : GLOBAL_ACCOUNT_ID;
      criteria.and(CheckEntity.CheckKeys.accountIdentifier)
          .is(accountId)
          .and(CheckEntity.CheckKeys.isCustom)
          .is(custom);
    }

    if (isNotEmpty(searchTerm)) {
      criteria.andOperator(buildSearchCriteria(searchTerm));
    }

    criteria.and(CheckEntity.CheckKeys.isDeleted).is(false);
    return criteria;
  }

  private Criteria buildSearchCriteria(String searchTerm) {
    return new Criteria().orOperator(
        where(CheckEntity.CheckKeys.name).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        where(CheckEntity.CheckKeys.identifier)
            .regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS),
        where(CheckEntity.CheckKeys.tags).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
  }

  private void validateCheckSaveRequest(CheckDetails checkDetails, String accountIdentifier) {
    Map<String, DataPoint> dataPointMap = dataPointService.getDataPointsMap(accountIdentifier);
    for (Rule rule : checkDetails.getRules()) {
      String key = rule.getDataSourceIdentifier() + DOT_SEPARATOR + rule.getDataPointIdentifier();
      if (!dataPointMap.containsKey(key)) {
        throw new InvalidRequestException(format("Data point not found for dataSource: %s, dataPoint: %s",
            rule.getDataSourceIdentifier(), rule.getDataPointIdentifier()));
      }
      DataPoint dataPoint = dataPointMap.get(key);
      if (dataPoint.isIsConditional() && isEmpty(rule.getConditionalInputValue())) {
        throw new InvalidRequestException("Conditional input value is required");
      }
    }
  }
}
