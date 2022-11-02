/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.service.impl;

import static io.harness.ccm.views.businessMapping.entities.UnallocatedCostStrategy.DISPLAY_NAME;

import io.harness.ccm.views.businessMapping.dao.BusinessMappingDao;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.entities.UnallocatedCost;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.helper.AwsAccountFieldHelper;
import io.harness.ccm.views.helper.BusinessMappingDataSourceHelper;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

public class BusinessMappingServiceImpl implements BusinessMappingService {
  @Inject private BusinessMappingDao businessMappingDao;
  @Inject private AwsAccountFieldHelper awsAccountFieldHelper;
  @Inject private BusinessMappingDataSourceHelper businessMappingDataSourceHelper;

  private static final String OTHERS = "Others";
  private static final String UNALLOCATED = "Unallocated";

  @Override
  public BusinessMapping save(BusinessMapping businessMapping) {
    validateBusinessMapping(businessMapping);
    return businessMappingDao.save(businessMapping);
  }

  @Override
  public BusinessMapping get(String uuid, String accountId) {
    final BusinessMapping businessMapping = businessMappingDao.get(uuid, accountId);
    modifyBusinessMapping(businessMapping);
    return businessMapping;
  }

  @Override
  public boolean isNamePresent(String name, String accountId) {
    return businessMappingDao.isNamePresent(name, accountId);
  }

  @Override
  public BusinessMapping get(String uuid) {
    return businessMappingDao.get(uuid);
  }

  @Override
  public BusinessMapping update(BusinessMapping businessMapping) {
    updateBusinessMapping(businessMapping);
    return businessMappingDao.update(businessMapping);
  }

  @Override
  public boolean delete(String uuid, String accountIdentifier) {
    return businessMappingDao.delete(uuid, accountIdentifier);
  }

  @Override
  public List<BusinessMapping> list(String accountId) {
    List<BusinessMapping> businessMappings = businessMappingDao.findByAccountId(accountId);
    businessMappings.forEach(this::modifyBusinessMapping);
    businessMappings.sort(Comparator.comparing(BusinessMapping::getLastUpdatedAt).reversed());
    return businessMappings;
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
    Collections.sort(costTargetNames, String.CASE_INSENSITIVE_ORDER);

    return costTargetNames;
  }

  private void validateBusinessMapping(final BusinessMapping businessMapping) {
    // TODO: Validate if Business Mapping already exists or not
    updateBusinessMapping(businessMapping);
  }

  @Override
  public boolean isInvalidBusinessMappingUnallocatedCostLabel(final BusinessMapping businessMapping) {
    return businessMapping != null && businessMapping.getUnallocatedCost() != null
        && businessMapping.getUnallocatedCost().getLabel() != null
        && (businessMapping.getUnallocatedCost().getLabel().equals(OTHERS)
            || businessMapping.getUnallocatedCost().getLabel().equals(UNALLOCATED));
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
