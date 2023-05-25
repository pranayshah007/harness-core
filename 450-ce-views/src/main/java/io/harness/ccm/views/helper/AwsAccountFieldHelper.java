/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;
import static io.harness.ccm.commons.constants.ViewFieldConstants.AWS_ACCOUNT_FIELD;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.service.intf.EntityMetadataService;
import io.harness.ccm.views.entities.ViewIdCondition;
import io.harness.ccm.views.entities.ViewRule;
import io.harness.ccm.views.graphql.QLCEViewFilter;
import io.harness.ccm.views.graphql.QLCEViewRule;

import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@OwnedBy(CE)
public class AwsAccountFieldHelper {
  @Inject private EntityMetadataService entityMetadataService;

  private static final Pattern ACCOUNT_ID_EXTRACT_PATTERN = Pattern.compile("\\((.*?)\\)");

  public static String mergeAwsAccountIdAndName(final String accountId, final String accountName) {
    String accountDetails = accountId;
    if (!Strings.isNullOrEmpty(accountName)) {
      accountDetails = accountName + " (" + accountId + ")";
    }
    return accountDetails;
  }

  public static String removeAwsAccountNameFromValue(final String value) {
    if (Objects.isNull(value)) {
      return null;
    }
    String accountId = value;
    final Matcher matcher = ACCOUNT_ID_EXTRACT_PATTERN.matcher(value);
    while (matcher.find()) {
      accountId = matcher.group(1);
    }
    return accountId;
  }

  public void removeAwsAccountNameFromAccountRules(final List<ViewRule> rules) {
    if (Objects.nonNull(rules)) {
      rules.forEach(viewRule -> {
        if (Objects.nonNull(viewRule.getViewConditions())) {
          viewRule.getViewConditions().forEach(viewCondition -> {
            final ViewIdCondition viewIdCondition = (ViewIdCondition) viewCondition;
            if (Objects.nonNull(viewIdCondition.getViewField())
                && AWS_ACCOUNT_FIELD.equals(viewIdCondition.getViewField().getFieldName())) {
              viewIdCondition.setValues(removeAwsAccountNameFromValues(viewIdCondition.getValues()));
            }
          });
        }
      });
    }
  }

  public static List<String> removeAwsAccountNameFromValues(final List<String> values) {
    return values.stream().map(AwsAccountFieldHelper::removeAwsAccountNameFromValue).collect(Collectors.toList());
  }

  public void mergeAwsAccountNameInAccountRules(final List<ViewRule> rules, final String accountId) {
    if (Objects.nonNull(rules)) {
      rules.forEach(viewRule -> {
        if (Objects.nonNull(viewRule.getViewConditions())) {
          viewRule.getViewConditions().forEach(viewCondition -> {
            final ViewIdCondition viewIdCondition = (ViewIdCondition) viewCondition;
            if (Objects.nonNull(viewIdCondition.getViewField())
                && AWS_ACCOUNT_FIELD.equals(viewIdCondition.getViewField().getFieldName())) {
              viewIdCondition.setValues(mergeAwsAccountNameWithValues(viewIdCondition.getValues(), accountId));
            }
          });
        }
      });
    }
  }

  public List<String> mergeAwsAccountNameWithValues(final List<String> values, final String accountId) {
    if (Objects.isNull(values)) {
      return Collections.emptyList();
    }
    final Map<String, String> entityIdToName =
        entityMetadataService.getEntityIdToNameMapping(values, accountId, AWS_ACCOUNT_FIELD);
    List<String> result = values;
    if (Objects.nonNull(entityIdToName)) {
      result = values.stream()
                   .map(value -> mergeAwsAccountIdAndName(value, entityIdToName.get(value)))
                   .collect(Collectors.toList());
    }
    return result;
  }

  public static List<QLCEViewFilter> removeAccountNameFromAWSAccountIdFilter(final List<QLCEViewFilter> idFilters) {
    final List<QLCEViewFilter> updatedIdFilters = new ArrayList<>();
    idFilters.forEach(idFilter -> {
      if (Objects.nonNull(idFilter.getField()) && AWS_ACCOUNT_FIELD.equals(idFilter.getField().getFieldName())
          && Objects.nonNull(idFilter.getValues())) {
        final String[] updatedValues = Arrays.stream(idFilter.getValues())
                                           .map(AwsAccountFieldHelper::removeAwsAccountNameFromValue)
                                           .toArray(String[] ::new);
        updatedIdFilters.add(QLCEViewFilter.builder()
                                 .field(idFilter.getField())
                                 .operator(idFilter.getOperator())
                                 .values(updatedValues)
                                 .build());
      } else {
        updatedIdFilters.add(idFilter);
      }
    });
    return updatedIdFilters;
  }

  public List<QLCEViewFilter> addAccountIdsByAwsAccountNameFilter(
      final List<QLCEViewFilter> idFilters, final String accountId) {
    final List<QLCEViewFilter> updatedIdFilters = new ArrayList<>();
    idFilters.forEach(idFilter -> {
      if (Objects.nonNull(idFilter.getField()) && AWS_ACCOUNT_FIELD.equals(idFilter.getField().getFieldName())
          && Objects.nonNull(idFilter.getValues()) && idFilter.getValues().length != 0
          && !idFilter.getValues()[0].isEmpty()) {
        final String[] updatedValues =
            Arrays.stream(idFilter.getValues())
                .map(value -> entityMetadataService.getAccountIdAndNameByAccountNameFilter(value, accountId).keySet())
                .flatMap(Set::stream)
                .distinct()
                .toArray(String[] ::new);
        updatedIdFilters.add(
            QLCEViewFilter.builder()
                .field(idFilter.getField())
                .operator(idFilter.getOperator())
                .values(Stream.concat(Arrays.stream(idFilter.getValues()), Arrays.stream(updatedValues))
                            .toArray(String[] ::new))
                .build());
      } else {
        updatedIdFilters.add(idFilter);
      }
    });
    return updatedIdFilters;
  }

  public static List<QLCEViewRule> removeAccountNameFromAWSAccountRuleFilter(final List<QLCEViewRule> ruleFilters) {
    final List<QLCEViewRule> updatedRuleFilters = new ArrayList<>();
    ruleFilters.forEach(ruleFilter -> {
      if (Objects.nonNull(ruleFilter.getConditions())) {
        final List<QLCEViewFilter> updatedConditions = new ArrayList<>();
        ruleFilter.getConditions().forEach(condition -> {
          if (Objects.nonNull(condition.getField()) && AWS_ACCOUNT_FIELD.equals(condition.getField().getFieldName())
              && Objects.nonNull(condition.getValues())) {
            final String[] updatedValues = Arrays.stream(condition.getValues())
                                               .map(AwsAccountFieldHelper::removeAwsAccountNameFromValue)
                                               .toArray(String[] ::new);
            updatedConditions.add(QLCEViewFilter.builder()
                                      .field(condition.getField())
                                      .operator(condition.getOperator())
                                      .values(updatedValues)
                                      .build());
          } else {
            updatedConditions.add(condition);
          }
        });
        updatedRuleFilters.add(QLCEViewRule.builder().conditions(updatedConditions).build());
      } else {
        updatedRuleFilters.add(ruleFilter);
      }
    });
    return updatedRuleFilters;
  }

  public List<String> spiltAndSortAWSAccountIdListBasedOnAccountName(final List<String> values) {
    List<String> accountIdsWithNames = new ArrayList<>();
    List<String> accountIdsWithoutNames = new ArrayList<>();
    for (String value : values) {
      if (value.endsWith(")")) {
        accountIdsWithNames.add(value);
      } else {
        accountIdsWithoutNames.add(value);
      }
    }
    accountIdsWithNames.sort(String.CASE_INSENSITIVE_ORDER);
    Collections.sort(accountIdsWithoutNames);
    accountIdsWithNames.addAll(accountIdsWithoutNames);
    return accountIdsWithNames;
  }
}
