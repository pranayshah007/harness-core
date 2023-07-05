/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.views.businessmapping.entities.UnallocatedCostStrategy.DISPLAY_NAME;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.CCMSortOrder;
import io.harness.ccm.views.businessmapping.dao.BusinessMappingDao;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.BusinessMappingListDTO;
import io.harness.ccm.views.businessmapping.entities.CostCategorySortType;
import io.harness.ccm.views.businessmapping.entities.CostTarget;
import io.harness.ccm.views.businessmapping.entities.UnallocatedCost;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingValidationService;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.BusinessMappingDataSourceHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(CE)
public class BusinessMappingServiceImpl implements BusinessMappingService {
  private static final int MAX_LIMIT_VALUE = 10_000;

  @Inject private BusinessMappingDao businessMappingDao;
  @Inject private AwsAccountFieldHelper awsAccountFieldHelper;
  @Inject private BusinessMappingDataSourceHelper businessMappingDataSourceHelper;
  @Inject private BusinessMappingValidationService businessMappingValidationService;

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
  public Set<String> getBusinessMappingIds(String accountId) {
    List<BusinessMapping> businessMappings = businessMappingDao.findBusinessMappingIdsByAccountId(accountId);
    if (businessMappings != null) {
      return businessMappings.stream().map(businessMapping -> businessMapping.getUuid()).collect(Collectors.toSet());
    }
    return Collections.emptySet();
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

  private void validateSaveRequest(final BusinessMapping businessMapping) {
    businessMappingValidationService.validateBusinessMapping(businessMapping);
    businessMappingValidationService.validateBusinessMappingName(businessMapping);
  }

  private void validateUpdateRequest(
      final BusinessMapping newBusinessMapping, final BusinessMapping oldBusinessMapping) {
    businessMappingValidationService.validateBusinessMapping(newBusinessMapping);
    if (!oldBusinessMapping.getName().equals(newBusinessMapping.getName())) {
      businessMappingValidationService.validateBusinessMappingName(newBusinessMapping);
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
