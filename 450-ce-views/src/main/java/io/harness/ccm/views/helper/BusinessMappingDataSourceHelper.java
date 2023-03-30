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
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewGroupBy;
import io.harness.ccm.views.graphql.QLCEViewRule;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.fabric8.utils.Lists;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Singleton
@OwnedBy(CE)
public class BusinessMappingDataSourceHelper {
  @Inject private BusinessMappingService businessMappingService;

  public Set<ViewFieldIdentifier> getBusinessMappingViewFieldIdentifiersFromIdFilters(
      final List<QLCEViewFilter> idFilters) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    for (final String businessMappingId : getBusinessMappingIdsFromIdFilters(idFilters)) {
      final BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
      viewFieldIdentifiers.addAll(getBusinessMappingViewFieldIdentifiers(businessMapping));
    }
    return viewFieldIdentifiers;
  }

  public Set<ViewFieldIdentifier> getBusinessMappingViewFieldIdentifiersFromRuleFilters(
      final List<QLCEViewRule> ruleFilters) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    ruleFilters.forEach(ruleFilter -> {
      if (Objects.nonNull(ruleFilter) && !Lists.isNullOrEmpty(ruleFilter.getConditions())) {
        viewFieldIdentifiers.addAll(getBusinessMappingViewFieldIdentifiersFromIdFilters(ruleFilter.getConditions()));
      }
    });
    return viewFieldIdentifiers;
  }

  public Set<ViewFieldIdentifier> getBusinessMappingViewFieldIdentifiersFromGroupBys(
      final List<QLCEViewGroupBy> groupBys) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    for (final String businessMappingId : getBusinessMappingIdsFromGroupBys(groupBys)) {
      final BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
      viewFieldIdentifiers.addAll(getBusinessMappingViewFieldIdentifiers(businessMapping));
    }
    return viewFieldIdentifiers;
  }

  public Set<ViewFieldIdentifier> getBusinessMappingViewFieldIdentifiersFromViewRules(final List<ViewRule> viewRules) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    for (final String businessMappingId : getBusinessMappingIdsFromViewRules(viewRules)) {
      final BusinessMapping businessMapping = businessMappingService.get(businessMappingId);
      viewFieldIdentifiers.addAll(getBusinessMappingViewFieldIdentifiers(businessMapping));
    }
    return viewFieldIdentifiers;
  }

  public Set<ViewFieldIdentifier> getBusinessMappingViewFieldIdentifiersFromViewRules(
      final String accountId, final List<ViewRule> viewRules) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    for (final String businessMappingId : getBusinessMappingIdsFromViewRules(viewRules)) {
      final BusinessMapping businessMapping = businessMappingService.get(businessMappingId, accountId);
      viewFieldIdentifiers.addAll(getBusinessMappingViewFieldIdentifiers(businessMapping));
    }
    return viewFieldIdentifiers;
  }

  public Set<ViewFieldIdentifier> getBusinessMappingViewFieldIdentifiers(final BusinessMapping businessMapping) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    if (Objects.nonNull(businessMapping)) {
      if (!Lists.isNullOrEmpty(businessMapping.getCostTargets())) {
        viewFieldIdentifiers.addAll(getCostTargetViewFieldIdentifiers(businessMapping.getCostTargets()));
      }
      if (!Lists.isNullOrEmpty(businessMapping.getSharedCosts())) {
        viewFieldIdentifiers.addAll(getSharedCostViewFieldIdentifiers(businessMapping.getSharedCosts()));
      }
    }
    return viewFieldIdentifiers;
  }

  public Set<ViewFieldIdentifier> getCostTargetViewFieldIdentifiers(final List<CostTarget> costTargets) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    if (Objects.nonNull(costTargets)) {
      costTargets.forEach(costTarget -> {
        if (Objects.nonNull(costTarget) && !Lists.isNullOrEmpty(costTarget.getRules())) {
          viewFieldIdentifiers.addAll(getViewRulesViewFieldIdentifiers(costTarget.getRules()));
        }
      });
    }
    return viewFieldIdentifiers;
  }

  public Set<ViewFieldIdentifier> getSharedCostViewFieldIdentifiers(final List<SharedCost> sharedCosts) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    if (Objects.nonNull(sharedCosts)) {
      sharedCosts.forEach(sharedCost -> {
        if (Objects.nonNull(sharedCost) && !Lists.isNullOrEmpty(sharedCost.getRules())) {
          viewFieldIdentifiers.addAll(getViewRulesViewFieldIdentifiers(sharedCost.getRules()));
        }
      });
    }
    return viewFieldIdentifiers;
  }

  public List<ViewRule> getBusinessMappingRules(BusinessMapping businessMapping, QLCEViewFilter businessMappingFilter) {
    List<ViewRule> viewRules = new ArrayList<>();
    List<CostTarget> costTargets = businessMapping.getCostTargets();
    boolean addSharedCostRules = false;
    switch (businessMappingFilter.getOperator()) {
      case EQUALS:
      case IN:
        List<String> values = Arrays.asList(businessMappingFilter.getValues());
        for (CostTarget costTarget : costTargets) {
          if (values.contains(costTarget.getName())) {
            viewRules.addAll(costTarget.getRules());
            addSharedCostRules = true;
          }
        }
        break;
      case NOT_IN:
        List<String> notInValues = Arrays.asList(businessMappingFilter.getValues());
        for (CostTarget costTarget : costTargets) {
          if (!notInValues.contains(costTarget.getName())) {
            viewRules.addAll(costTarget.getRules());
            addSharedCostRules = true;
          }
        }
        break;
      case LIKE:
      case SEARCH:
        String searchString = businessMappingFilter.getValues()[0].toLowerCase(Locale.ROOT);
        for (CostTarget costTarget : costTargets) {
          if (searchString.contains(costTarget.getName().toLowerCase(Locale.ROOT))) {
            viewRules.addAll(costTarget.getRules());
            addSharedCostRules = true;
          }
        }
        break;
      case NOT_NULL:
        costTargets.forEach(costTarget -> viewRules.addAll(costTarget.getRules()));
        addSharedCostRules = true;
        break;
      case NULL:
      default:
    }
    if (addSharedCostRules) {
      viewRules.addAll(getSharedCostTargetRules(businessMapping));
    }
    return viewRules;
  }

  public List<ViewRule> getSharedCostTargetRules(BusinessMapping businessMapping) {
    List<ViewRule> viewRules = new ArrayList<>();
    List<SharedCost> sharedCostTargets = businessMapping.getSharedCosts();
    if (sharedCostTargets != null) {
      sharedCostTargets.forEach(sharedCostTarget -> viewRules.addAll(sharedCostTarget.getRules()));
    }
    return viewRules;
  }

  private Set<ViewFieldIdentifier> getViewRulesViewFieldIdentifiers(final List<ViewRule> viewRules) {
    final Set<ViewFieldIdentifier> viewFieldIdentifiers = new HashSet<>();
    viewRules.forEach(viewRule -> {
      if (Objects.nonNull(viewRule) && Objects.nonNull(viewRule.getViewConditions())) {
        viewRule.getViewConditions().forEach(viewCondition -> {
          final ViewIdCondition viewIdCondition = (ViewIdCondition) viewCondition;
          final ViewFieldIdentifier viewFieldIdentifier = viewIdCondition.getViewField().getIdentifier();
          if (viewFieldIdentifier != ViewFieldIdentifier.COMMON && viewFieldIdentifier != ViewFieldIdentifier.LABEL) {
            viewFieldIdentifiers.add(viewIdCondition.getViewField().getIdentifier());
          }
        });
      }
    });
    return viewFieldIdentifiers;
  }

  private Set<String> getBusinessMappingIdsFromViewRules(final List<ViewRule> viewRules) {
    final Set<String> businessMappingIds = new HashSet<>();
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

  private Set<String> getBusinessMappingIdsFromIdFilters(final List<QLCEViewFilter> idFilters) {
    final Set<String> businessMappingIds = new HashSet<>();
    if (Objects.nonNull(idFilters)) {
      idFilters.forEach(idFilter -> {
        if (idFilter.getField().getIdentifier() == ViewFieldIdentifier.BUSINESS_MAPPING) {
          businessMappingIds.add(idFilter.getField().getFieldId());
        }
      });
    }
    return businessMappingIds;
  }

  private Set<String> getBusinessMappingIdsFromGroupBys(final List<QLCEViewGroupBy> groupBys) {
    final Set<String> businessMappingIds = new HashSet<>();
    if (Objects.nonNull(groupBys)) {
      groupBys.forEach(groupBy -> {
        if (Objects.nonNull(groupBy) && Objects.nonNull(groupBy.getEntityGroupBy())
            && groupBy.getEntityGroupBy().getIdentifier() == ViewFieldIdentifier.BUSINESS_MAPPING) {
          businessMappingIds.add(groupBy.getEntityGroupBy().getFieldId());
        }
      });
    }
    return businessMappingIds;
  }
}
