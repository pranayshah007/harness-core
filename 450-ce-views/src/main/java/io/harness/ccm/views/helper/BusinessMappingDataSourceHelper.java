/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.businessMapping.entities.BusinessMapping;
import io.harness.ccm.views.businessMapping.entities.CostTarget;
import io.harness.ccm.views.businessMapping.entities.SharedCost;
import io.harness.ccm.views.businessMapping.service.intf.BusinessMappingService;
import io.harness.ccm.views.entities.ViewFieldIdentifier;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Singleton
@OwnedBy(CE)
public class BusinessMappingDataSourceHelper {
  @Inject private BusinessMappingService businessMappingService;

  public Set<ViewFieldIdentifier> getBusinessMappingViewFieldIdentifiers(
      final String accountId, final List<ViewRule> viewRules) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    for (final String businessMappingId : getBusinessMappingIds(viewRules)) {
      final BusinessMapping businessMapping = businessMappingService.get(businessMappingId, accountId);
      if (Objects.nonNull(businessMapping)) {
        if (!Lists.isNullOrEmpty(businessMapping.getCostTargets())) {
          viewFieldIdentifiers.addAll(getCostTargetViewFieldIdentifiers(businessMapping.getCostTargets()));
        }
        if (!Lists.isNullOrEmpty(businessMapping.getSharedCosts())) {
          viewFieldIdentifiers.addAll(getSharedCostViewFieldIdentifiers(businessMapping.getSharedCosts()));
        }
      }
    }
    return viewFieldIdentifiers;
  }

  private Set<ViewFieldIdentifier> getCostTargetViewFieldIdentifiers(final List<CostTarget> costTargets) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    costTargets.forEach(costTarget -> {
      if (Objects.nonNull(costTarget) && !Lists.isNullOrEmpty(costTarget.getRules())) {
        viewFieldIdentifiers.addAll(getViewRulesViewFieldIdentifiers(costTarget.getRules()));
      }
    });
    return viewFieldIdentifiers;
  }

  private Set<ViewFieldIdentifier> getSharedCostViewFieldIdentifiers(final List<SharedCost> sharedCosts) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    sharedCosts.forEach(sharedCost -> {
      if (Objects.nonNull(sharedCost) && !Lists.isNullOrEmpty(sharedCost.getRules())) {
        viewFieldIdentifiers.addAll(getViewRulesViewFieldIdentifiers(sharedCost.getRules()));
      }
    });
    return viewFieldIdentifiers;
  }

  private Set<ViewFieldIdentifier> getViewRulesViewFieldIdentifiers(final List<ViewRule> viewRules) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    viewRules.forEach(viewRule -> {
      if (Objects.nonNull(viewRule) && Objects.nonNull(viewRule.getViewConditions())) {
        viewRule.getViewConditions().forEach(viewCondition -> {
          final ViewIdCondition viewIdCondition = (ViewIdCondition) viewCondition;
          viewFieldIdentifiers.add(viewIdCondition.getViewField().getIdentifier());
        });
      }
    });
    return viewFieldIdentifiers;
  }

  private List<String> getBusinessMappingIds(final List<ViewRule> viewRules) {
    final List<String> businessMappingIds = new ArrayList<>();
    if (Objects.nonNull(viewRules)) {
      viewRules.forEach(viewRule -> {
        if (Objects.nonNull(viewRule.getViewConditions())) {
          viewRule.getViewConditions().forEach(viewCondition -> {
            final ViewIdCondition viewIdCondition = (ViewIdCondition) viewCondition;
            if (viewIdCondition.getViewField().getIdentifier() == ViewFieldIdentifier.BUSINESS_MAPPING) {
              businessMappingIds.add(viewIdCondition.getViewField().getFieldId());
            }
          });
        }
      });
    }
    return businessMappingIds;
  }
}
