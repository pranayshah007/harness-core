/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.businessmapping.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.views.businessmapping.entities.SharingStrategy.FIXED;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.businessmapping.dao.BusinessMappingDao;
import io.harness.ccm.views.businessmapping.entities.BusinessMapping;
import io.harness.ccm.views.businessmapping.entities.CostTarget;
import io.harness.ccm.views.businessmapping.entities.SharedCost;
import io.harness.ccm.views.businessmapping.entities.SharedCostSplit;
import io.harness.ccm.views.businessmapping.service.intf.BusinessMappingValidationService;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewField;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewIdOperator;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.exception.InvalidRequestException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
@OwnedBy(CE)
public class BusinessMappingValidationServiceImpl implements BusinessMappingValidationService {
  private static final String OTHERS = "Others";
  private static final String UNALLOCATED = "Unallocated";

  @Inject private BusinessMappingDao businessMappingDao;

  @Override
  public void validateBusinessMapping(final BusinessMapping businessMapping) {
    if (Objects.isNull(businessMapping)) {
      throw new InvalidRequestException("Cost Category can't be null");
    }
    validateCostBuckets(businessMapping);
    validateSharedBuckets(businessMapping);
    validateUnallocatedCostLabel(businessMapping);
  }

  @Override
  public void validateBusinessMappingName(final BusinessMapping businessMapping) {
    if (Objects.isNull(businessMapping)) {
      throw new InvalidRequestException("Cost Category can't be null");
    }
    if (isEmpty(businessMapping.getName())) {
      throw new InvalidRequestException("Cost Category name can't be null or empty");
    }
    if (isNamePresent(businessMapping.getName(), businessMapping.getAccountId())) {
      throw new InvalidRequestException("Cost category name already exists");
    }
  }

  private boolean isNamePresent(String name, String accountId) {
    return businessMappingDao.isNamePresent(name, accountId);
  }

  private void validateCostBuckets(final BusinessMapping businessMapping) {
    if (Lists.isNullOrEmpty(businessMapping.getCostTargets())) {
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
    if (isEmpty(costTarget.getName())) {
      throw new InvalidRequestException("cost bucket name can't be null or empty");
    }
    validateRules(costTarget.getRules(), costTarget.getName());
  }

  private void validateSharedBuckets(final BusinessMapping businessMapping) {
    if (!Lists.isNullOrEmpty(businessMapping.getSharedCosts())) {
      for (final SharedCost sharedCost : businessMapping.getSharedCosts()) {
        validateSharedBucket(sharedCost);
        if (sharedCost.getStrategy() == FIXED) {
          final Set<String> costBucketNames =
              businessMapping.getCostTargets().stream().map(CostTarget::getName).collect(Collectors.toSet());
          validateSharedCostSplits(sharedCost, costBucketNames);
        }
      }
    }
  }

  private void validateSharedBucket(final SharedCost sharedCost) {
    if (Objects.isNull(sharedCost)) {
      throw new InvalidRequestException("Shared cost bucket can't be null");
    }
    if (isEmpty(sharedCost.getName())) {
      throw new InvalidRequestException("shared cost bucket name can't be null or empty");
    }
    if (Objects.isNull(sharedCost.getStrategy())) {
      throw new InvalidRequestException(
          String.format("Invalid sharing strategy for shared cost bucket %s", sharedCost.getName()));
    }
    validateRules(sharedCost.getRules(), sharedCost.getName());
  }

  private void validateRules(final List<ViewRule> rules, final String bucketName) {
    if (Lists.isNullOrEmpty(rules)) {
      throw new InvalidRequestException(String.format("Rules must exist for bucket %s", bucketName));
    }
    for (final ViewRule viewRule : rules) {
      validateViewConditions(viewRule.getViewConditions(), bucketName);
    }
  }

  private void validateViewConditions(final List<ViewCondition> viewConditions, final String bucketName) {
    if (Lists.isNullOrEmpty(viewConditions)) {
      throw new InvalidRequestException(String.format("Conditions must exist for bucket %s", bucketName));
    }
    for (final ViewCondition viewCondition : viewConditions) {
      validateViewCondition(viewCondition, bucketName);
    }
  }

  private void validateViewCondition(final ViewCondition viewCondition, final String bucketName) {
    final ViewIdCondition viewIdCondition = (ViewIdCondition) viewCondition;
    validateViewField(viewIdCondition.getViewField(), bucketName);
    validateViewIdOperator(viewIdCondition.getViewOperator(), bucketName);
    validateViewConditionValues(viewIdCondition.getValues(), bucketName, viewIdCondition.getViewOperator());
  }

  private void validateViewField(final ViewField viewField, final String bucketName) {
    if (isEmpty(viewField.getFieldId())) {
      throw new InvalidRequestException(String.format("ViewFieldId must exist for bucket %s", bucketName));
    }
    if (Objects.isNull(viewField.getIdentifier())) {
      throw new InvalidRequestException(String.format("ViewFieldIdentifier must exist for bucket %s", bucketName));
    }
  }

  private void validateViewIdOperator(final ViewIdOperator viewIdOperator, final String bucketName) {
    if (Objects.isNull(viewIdOperator)) {
      throw new InvalidRequestException(String.format("ViewIdOperator must exist for bucket %s", bucketName));
    }
  }

  private void validateViewConditionValues(
      final List<String> viewConditionValues, final String bucketName, final ViewIdOperator viewIdOperator) {
    if (!(viewIdOperator == ViewIdOperator.NULL || viewIdOperator == ViewIdOperator.NOT_NULL)
        && isEmpty(viewConditionValues)) {
      throw new InvalidRequestException(String.format("ViewConditionValues must exist for bucket %s", bucketName));
    }
  }

  private void validateSharedCostSplits(final SharedCost sharedCost, final Set<String> costBucketNames) {
    if (Lists.isNullOrEmpty(sharedCost.getSplits())) {
      throw new InvalidRequestException(
          String.format("Splits must exist for shared cost bucket %s", sharedCost.getName()));
    }
    for (final SharedCostSplit sharedCostSplit : sharedCost.getSplits()) {
      validateSharedCostSplit(sharedCost, costBucketNames, sharedCostSplit);
    }
    validateSharedCostSplitTotalPercentage(sharedCost);
  }

  private void validateSharedCostSplit(
      final SharedCost sharedCost, final Set<String> costBucketNames, final SharedCostSplit sharedCostSplit) {
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

  private void validateSharedCostSplitTotalPercentage(final SharedCost sharedCost) {
    final double totalPercentage =
        sharedCost.getSplits().stream().mapToDouble(SharedCostSplit::getPercentageContribution).sum();
    if (Double.compare(totalPercentage, 100.0D) != 0) {
      throw new InvalidRequestException(
          String.format("Total split percentage is not equal to 100 for shared cost bucket %s", sharedCost.getName()));
    }
  }

  private void validateUnallocatedCostLabel(final BusinessMapping businessMapping) {
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

  @Override
  public boolean isInvalidBusinessMappingUnallocatedCostLabel(final BusinessMapping businessMapping) {
    return businessMapping != null && businessMapping.getUnallocatedCost() != null
        && (OTHERS.equals(businessMapping.getUnallocatedCost().getLabel())
            || UNALLOCATED.equals(businessMapping.getUnallocatedCost().getLabel()));
  }
}
