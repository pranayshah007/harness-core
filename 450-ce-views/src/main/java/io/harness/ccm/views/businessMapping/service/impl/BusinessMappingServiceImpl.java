/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.views.businessMapping.entities.SharingStrategy.FIXED;
import static io.harness.ccm.views.businessMapping.entities.UnallocatedCostStrategy.DISPLAY_NAME;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.views.businessMapping.dao.BusinessMappingDao;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.BusinessMappingListDTO;
import io.harness.ccm.views.businessMapping.entities.CostCategorySortType;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.entities.SharedCost;
import io.harness.ccm.views.businessMapping.entities.SharedCostSplit;
import io.harness.ccm.views.businessMapping.entities.UnallocatedCost;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.BusinessMappingDataSourceHelper;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(CE)
public class BusinessMappingServiceImpl implements BusinessMappingService {
  private static final String OTHERS = "Others";
  private static final String UNALLOCATED = "Unallocated";
  private static final int MAX_LIMIT_VALUE = 10_000;

  @Inject private BusinessMappingDao businessMappingDao;
  @Inject private AwsAccountFieldHelper awsAccountFieldHelper;
  @Inject private BusinessMappingDataSourceHelper businessMappingDataSourceHelper;

  @Override
  public BusinessMapping save(BusinessMapping businessMapping) {
    validateSaveRequest(businessMapping);
    updateBusinessMapping(businessMapping);
    return businessMappingDao.save(businessMapping);
  }

  @Override
  public BusinessMapping get(String uuid, String accountId) {
    final BusinessMapping businessMapping = businessMappingDao.get(uuid, accountId);
    modifyBusinessMapping(businessMapping);
    return businessMapping;
  }

  @Override
  public BusinessMapping get(String uuid) {
    return businessMappingDao.get(uuid);
  }

  @Override
  public BusinessMapping update(BusinessMapping newBusinessMapping, BusinessMapping oldBusinessMapping) {
    validateUpdateRequest(newBusinessMapping, oldBusinessMapping);
    updateBusinessMapping(newBusinessMapping);
    return businessMappingDao.update(newBusinessMapping);
  }

  @Override
  public boolean delete(String uuid, String accountIdentifier) {
    return businessMappingDao.delete(uuid, accountIdentifier);
  }

  @Override
  public BusinessMappingListDTO list(String accountId, String searchKey, CostCategorySortType sortType,
      CCMSortOrder sortOrder, Integer limit, Integer offset) {
    final int modifiedLimit = Objects.isNull(limit) ? MAX_LIMIT_VALUE : Integer.min(limit, MAX_LIMIT_VALUE);
    final int modifiedOffset = Objects.isNull(offset) ? 0 : offset;
    final CostCategorySortType modifiedSortType = Objects.isNull(sortType) ? CostCategorySortType.LAST_EDIT : sortType;
    final CCMSortOrder modifiedSortOrder = Objects.isNull(sortOrder) ? CCMSortOrder.DESCENDING : sortOrder;
    List<BusinessMapping> businessMappings = businessMappingDao.findByAccountIdAndRegexNameWithLimitAndOffsetAndOrder(
        accountId, searchKey, modifiedSortType, modifiedSortOrder, modifiedLimit, modifiedOffset);
    businessMappings.forEach(this::modifyBusinessMapping);
    long totalCount = businessMappingDao.getCountByAccountIdAndRegexName(accountId, searchKey);
    return BusinessMappingListDTO.builder().businessMappings(businessMappings).totalCount(totalCount).build();
  }

  @Override
  public List<ViewField> getBusinessMappingViewFields(String accountId) {
    List<BusinessMapping> businessMappingList = businessMappingDao.findByAccountId(accountId);
    List<ViewField> viewFieldList = new ArrayList<>();
    for (BusinessMapping businessMapping : businessMappingList) {
      viewFieldList.add(ViewField.builder()
                            .fieldId(businessMapping.getUuid())
                            .fieldName(businessMapping.getName())
                            .identifier(ViewFieldIdentifier.BUSINESS_MAPPING)
                            .identifierName(ViewFieldIdentifier.BUSINESS_MAPPING.getDisplayName())
                            .build());
    }
    return viewFieldList;
  }

  @Override
  public List<String> getCostTargetNames(String businessMappingId, String accountId, String searchString) {
    BusinessMapping businessMapping = get(businessMappingId, accountId);
    List<String> costTargetNames = new ArrayList<>();
    if (businessMapping != null && businessMapping.getCostTargets() != null) {
      List<CostTarget> costTargets = businessMapping.getCostTargets();
      costTargetNames =
          costTargets.stream()
              .map(CostTarget::getName)
              .filter(name -> name.toLowerCase(Locale.ENGLISH).contains(searchString.toLowerCase(Locale.ENGLISH)))
              .collect(Collectors.toList());
    }
    if (businessMapping != null && businessMapping.getUnallocatedCost() != null) {
      UnallocatedCost unallocatedCost = businessMapping.getUnallocatedCost();
      if (unallocatedCost.getStrategy() == DISPLAY_NAME
          && unallocatedCost.getLabel()
                 .toLowerCase(Locale.ENGLISH)
                 .contains(searchString.toLowerCase(Locale.ENGLISH))) {
        costTargetNames.add(unallocatedCost.getLabel());
      }
    }
    costTargetNames.sort(String.CASE_INSENSITIVE_ORDER);

    return costTargetNames;
  }

  private void validateBusinessMapping(final BusinessMapping businessMapping) {
    if (Objects.isNull(businessMapping)) {
      throw new InvalidRequestException("Cost Category can't be null");
    }
    validateCostBuckets(businessMapping);
    validateSharedBuckets(businessMapping);
    validateUnallocatedCostLabel(businessMapping);
  }

  private void validateSaveRequest(final BusinessMapping businessMapping) {
    validateBusinessMapping(businessMapping);
    validateBusinessMappingName(businessMapping);
  }

  private void validateUpdateRequest(
      final BusinessMapping newBusinessMapping, final BusinessMapping oldBusinessMapping) {
    validateBusinessMapping(newBusinessMapping);
    if (!oldBusinessMapping.getName().equals(newBusinessMapping.getName())) {
      validateBusinessMappingName(newBusinessMapping);
    }
  }

  private void validateBusinessMappingName(BusinessMapping businessMapping) {
    if (isEmpty(businessMapping.getName())) {
      throw new InvalidRequestException("Cost Category name can't be null or empty");
    }
    if (isNamePresent(businessMapping.getName(), businessMapping.getAccountId())) {
      throw new InvalidRequestException("Cost category name already exists");
    }
  }

  private void validateUnallocatedCostLabel(BusinessMapping businessMapping) {
    if (Objects.isNull(businessMapping.getUnallocatedCost())) {
      throw new InvalidRequestException("Unallocated cost bucket can't be null");
    }
    if (isEmpty(businessMapping.getUnallocatedCost().getLabel())) {
      throw new InvalidRequestException("Unallocated cost bucket label can't be null or empty");
    }
    if (isInvalidBusinessMappingUnallocatedCostLabel(businessMapping)) {
      throw new InvalidRequestException("Unallocated cost bucket label does not allow Others or Unallocated");
    }
  }

  private boolean isNamePresent(String name, String accountId) {
    return businessMappingDao.isNamePresent(name, accountId);
  }

  @Override
  public boolean isInvalidBusinessMappingUnallocatedCostLabel(final BusinessMapping businessMapping) {
    return businessMapping != null && businessMapping.getUnallocatedCost() != null
        && businessMapping.getUnallocatedCost().getLabel() != null
        && (businessMapping.getUnallocatedCost().getLabel().equals(OTHERS)
            || businessMapping.getUnallocatedCost().getLabel().equals(UNALLOCATED));
  }

  private void validateCostBuckets(final BusinessMapping businessMapping) {
    if (Objects.nonNull(businessMapping) && Lists.isNullOrEmpty(businessMapping.getCostTargets())) {
      throw new InvalidRequestException("At least 1 cost bucket must exist");
    }
    for (final CostTarget costTarget : businessMapping.getCostTargets()) {
      validateCostBucket(costTarget);
    }
  }

  private void validateCostBucket(final CostTarget costTarget) {
    if (Objects.isNull(costTarget)) {
      throw new InvalidRequestException("Cost bucket can't be null");
    }
    if (Lists.isNullOrEmpty(costTarget.getRules())) {
      throw new InvalidRequestException(String.format("Rules must exist for cost bucket %s", costTarget.getName()));
    }
    if (isEmpty(costTarget.getName())) {
      throw new InvalidRequestException("cost bucket name can't be null or empty");
    }
  }

  private void validateSharedBuckets(final BusinessMapping businessMapping) {
    if (Objects.nonNull(businessMapping) && !Lists.isNullOrEmpty(businessMapping.getSharedCosts())) {
      for (final SharedCost sharedCost : businessMapping.getSharedCosts()) {
        validateSharedBucket(sharedCost);
        if (sharedCost.getStrategy() == FIXED) {
          Set<String> costBucketNames = new HashSet<>();
          if (!Lists.isNullOrEmpty(businessMapping.getCostTargets())) {
            costBucketNames =
                businessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toSet());
          }
          validateSharedCostSplits(sharedCost, costBucketNames);
        }
      }
    }
  }

  private void validateSharedBucket(final SharedCost sharedCost) {
    if (Objects.isNull(sharedCost)) {
      throw new InvalidRequestException("Shared cost bucket can't be null");
    }
    if (Lists.isNullOrEmpty(sharedCost.getRules())) {
      throw new InvalidRequestException(
          String.format("Rules must exist for shared cost bucket %s", sharedCost.getName()));
    }
    if (isEmpty(sharedCost.getName())) {
      throw new InvalidRequestException("shared cost bucket name can't be null or empty");
    }
  }

  private void validateSharedCostSplits(final SharedCost sharedCost, final Set<String> costBucketNames) {
    if (Lists.isNullOrEmpty(sharedCost.getSplits())) {
      throw new InvalidRequestException(
          String.format("Splits must exist for shared cost bucket %s", sharedCost.getName()));
    }
    for (final SharedCostSplit sharedCostSplit : sharedCost.getSplits()) {
      if (!costBucketNames.contains(sharedCostSplit.getCostTargetName())) {
        throw new InvalidRequestException(
            String.format("Split contains invalid %s cost bucket name for shared cost bucket %s", sharedCost.getName(),
                sharedCostSplit.getCostTargetName()));
      }
      if (Double.compare(sharedCostSplit.getPercentageContribution(), 0.0D) == -1) {
        throw new InvalidRequestException(
            String.format("Split percentage can't be less than zero for shared cost bucket %s", sharedCost.getName()));
      }
    }
    validateSharedCostTotalPercentage(sharedCost);
  }

  private void validateSharedCostTotalPercentage(final SharedCost sharedCost) {
    final double totalPercentage =
        sharedCost.getSplits().stream().mapToDouble(SharedCostSplit::getPercentageContribution).sum();
    if (Double.compare(totalPercentage, 100.0D) != 0) {
      throw new InvalidRequestException(
          String.format("Total split percentage is not equal to 100 for shared cost bucket %s", sharedCost.getName()));
    }
  }

  private void updateBusinessMapping(final BusinessMapping businessMapping) {
    if (Objects.nonNull(businessMapping.getCostTargets())) {
      businessMapping.getCostTargets().forEach(
          costTarget -> awsAccountFieldHelper.removeAwsAccountNameFromAccountRules(costTarget.getRules()));
    }
    if (Objects.nonNull(businessMapping.getSharedCosts())) {
      businessMapping.getSharedCosts().forEach(
          sharedCost -> awsAccountFieldHelper.removeAwsAccountNameFromAccountRules(sharedCost.getRules()));
    }
    businessMapping.setDataSources(
        new ArrayList<>(businessMappingDataSourceHelper.getBusinessMappingViewFieldIdentifiers(businessMapping)));
  }

  private void modifyBusinessMapping(final BusinessMapping businessMapping) {
    if (businessMapping != null) {
      if (Objects.nonNull(businessMapping.getCostTargets())) {
        businessMapping.getCostTargets().forEach(costTarget
            -> awsAccountFieldHelper.mergeAwsAccountNameInAccountRules(
                costTarget.getRules(), businessMapping.getAccountId()));
      }
      if (Objects.nonNull(businessMapping.getSharedCosts())) {
        businessMapping.getSharedCosts().forEach(sharedCost
            -> awsAccountFieldHelper.mergeAwsAccountNameInAccountRules(
                sharedCost.getRules(), businessMapping.getAccountId()));
      }
    }
  }
}
