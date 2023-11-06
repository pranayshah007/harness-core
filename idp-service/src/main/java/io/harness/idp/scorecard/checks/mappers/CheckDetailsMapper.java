/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.mappers;

import static io.harness.idp.common.Constants.DOT_SEPARATOR;
import static io.harness.idp.common.Constants.SPACE_SEPARATOR;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.spec.server.idp.v1.model.CheckDetails;
import io.harness.spec.server.idp.v1.model.Rule;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class CheckDetailsMapper {
  public CheckDetails toDTO(CheckEntity checkEntity) {
    CheckDetails checkDetails = new CheckDetails();
    checkDetails.setName(checkEntity.getName());
    checkDetails.setIdentifier(checkEntity.getIdentifier());
    checkDetails.setDescription(checkEntity.getDescription());
    checkDetails.setExpression(checkEntity.getExpression());
    checkDetails.setCustom(checkEntity.isCustom());
    checkDetails.defaultBehaviour(checkEntity.getDefaultBehaviour());
    checkDetails.setFailMessage(checkEntity.getFailMessage());
    checkDetails.setRuleStrategy(checkEntity.getRuleStrategy());
    checkDetails.setRules(checkEntity.getRules());
    checkDetails.setTags(checkEntity.getTags());
    return checkDetails;
  }

  public CheckEntity fromDTO(CheckDetails checkDetails, String accountIdentifier) {
    String expression = constructDisplayExpression(checkDetails.getRules(), checkDetails.getRuleStrategy());
    return CheckEntity.builder()
        .accountIdentifier(accountIdentifier)
        .identifier(checkDetails.getIdentifier())
        .name(checkDetails.getName())
        .description(checkDetails.getDescription())
        .expression(expression)
        .isCustom(checkDetails.isCustom())
        .defaultBehaviour(checkDetails.getDefaultBehaviour())
        .failMessage(checkDetails.getFailMessage())
        .ruleStrategy(checkDetails.getRuleStrategy())
        .rules(checkDetails.getRules())
        .tags(checkDetails.getTags())
        .build();
  }

  public static String constructExpressionFromRules(
      List<Rule> rules, CheckDetails.RuleStrategyEnum ruleStrategy, String dpValueSuffix, boolean getLhsOnly) {
    return rules.stream()
        .map(rule -> CheckDetailsMapper.getExpression(rule, dpValueSuffix, getLhsOnly))
        .collect(Collectors.joining(SPACE_SEPARATOR
            + (ruleStrategy.equals(CheckDetails.RuleStrategyEnum.ALL_OF) ? "&&" : "||") + SPACE_SEPARATOR));
  }

  String getExpression(Rule rule, String dpValueSuffix, boolean getLhsOnly) {
    StringBuilder expressionBuilder = new StringBuilder(rule.getDataSourceIdentifier())
                                          .append(DOT_SEPARATOR)
                                          .append("\"")
                                          .append(rule.getIdentifier())
                                          .append("\"");

    if (StringUtils.isNotBlank(dpValueSuffix)) {
      expressionBuilder.append(DOT_SEPARATOR);
      expressionBuilder.append(dpValueSuffix);
    }

    if (!getLhsOnly) {
      expressionBuilder.append(rule.getOperator());
      expressionBuilder.append(rule.getValue());
    }

    return expressionBuilder.toString();
  }

  private static String constructDisplayExpression(List<Rule> rules, CheckDetails.RuleStrategyEnum ruleStrategy) {
    return rules.stream()
        .map(CheckDetailsMapper::getDisplayExpression)
        .collect(Collectors.joining(SPACE_SEPARATOR
            + (ruleStrategy.equals(CheckDetails.RuleStrategyEnum.ALL_OF) ? "&&" : "||") + SPACE_SEPARATOR));
  }

  private static String getDisplayExpression(Rule rule) {
    return rule.getDataSourceIdentifier() + DOT_SEPARATOR + rule.getDataPointIdentifier() + rule.getOperator()
        + rule.getValue();
  }
}
