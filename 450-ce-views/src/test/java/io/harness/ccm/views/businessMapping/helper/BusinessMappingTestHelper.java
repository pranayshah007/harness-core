/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.businessMapping.helper;

import static io.harness.ccm.views.businessMapping.entities.UnallocatedCost.UnallocatedCostBuilder;

import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.entities.SharedCost;
import io.harness.ccm.views.businessMapping.entities.SharedCostSplit;
import io.harness.ccm.views.businessMapping.entities.SharingStrategy;
import io.harness.ccm.views.businessMapping.entities.UnallocatedCost;
import io.harness.ccm.views.businessMapping.entities.UnallocatedCostStrategy;
import io.harness.ccm.views.entities.ViewCondition;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public final class BusinessMappingTestHelper {
  public static final String TEST_ID = UUID.randomUUID().toString();
  public static final String TEST_ACCOUNT_ID = "TEST_ACCOUNT_ID";
  public static final String TEST_NAME = "TEST_NAME";
  public static final String TEST_SEARCH_KEY = "SEARCH_KEY";
  public static final int TEST_LIMIT = 10;
  public static final int TEST_OFFSET = 0;

  private BusinessMappingTestHelper() {}

  public static BusinessMapping getBusinessMapping(final String uuid) {
    return BusinessMapping.builder()
        .uuid(uuid)
        .accountId(TEST_ACCOUNT_ID)
        .name(TEST_NAME)
        .costTargets(getCostTargets())
        .sharedCosts(getSharedCosts())
        .unallocatedCost(getUnallocatedCost(UnallocatedCostStrategy.DISPLAY_NAME))
        .build();
  }

  public static CostTarget getCostTarget(final String name, final List<ViewRule> rules) {
    return CostTarget.builder().name(name).rules(rules).build();
  }

  @NotNull
  public static List<CostTarget> getCostTargets() {
    final List<ViewRule> rules = getRules();
    return Stream.of(getCostTarget(TEST_NAME, rules), getCostTarget(TEST_NAME, rules)).collect(Collectors.toList());
  }

  public static SharedCost getSharedCost(final String name, final List<ViewRule> rules, final SharingStrategy strategy,
      final List<SharedCostSplit> splits) {
    return SharedCost.builder().name(name).rules(rules).strategy(strategy).splits(splits).build();
  }

  @NotNull
  public static List<SharedCost> getSharedCosts() {
    final List<ViewRule> rules = getRules();
    final List<SharedCostSplit> splits = getSharedCostSplits();
    return Stream
        .of(getSharedCost(TEST_NAME, rules, SharingStrategy.EQUAL, splits),
            getSharedCost(TEST_NAME, rules, SharingStrategy.PROPORTIONAL, splits))
        .collect(Collectors.toList());
  }

  public static SharedCostSplit getSharedCostSplit(final String costTargetName, final double percentageContribution) {
    return SharedCostSplit.builder()
        .costTargetName(costTargetName)
        .percentageContribution(percentageContribution)
        .build();
  }

  @NotNull
  public static List<SharedCostSplit> getSharedCostSplits() {
    return Stream.of(getSharedCostSplit(TEST_NAME, 10.0), getSharedCostSplit(TEST_NAME, 90.0))
        .collect(Collectors.toList());
  }

  @NotNull
  public static List<ViewRule> getRules() {
    final List<ViewCondition> viewConditions = Collections.singletonList(ViewIdCondition.builder().build());
    return Collections.singletonList(ViewRule.builder().viewConditions(viewConditions).build());
  }

  public static UnallocatedCost getUnallocatedCost(final UnallocatedCostStrategy unallocatedCostStrategy) {
    final UnallocatedCostBuilder unallocatedCostBuilder = UnallocatedCost.builder();
    switch (unallocatedCostStrategy) {
      case DISPLAY_NAME:
        unallocatedCostBuilder.label("TEST_LABEL");
        break;
      case SHARE:
        unallocatedCostBuilder.sharingStrategy(SharingStrategy.EQUAL).splits(getSharedCostSplits());
        break;
      default:
        break;
    }
    return unallocatedCostBuilder.strategy(unallocatedCostStrategy).build();
  }
}
