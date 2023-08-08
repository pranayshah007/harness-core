/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.scorecard.checks.mappers;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.idp.scorecard.checks.entity.CheckEntity;
import io.harness.spec.server.idp.v1.model.CheckDetails;

import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.IDP)
@UtilityClass
public class CheckDetailsMapper {
  public static final String DOT_SEPARATOR = ".";
  public static final String SPACE_SEPARATOR = " ";

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
    checkDetails.setLabels(checkEntity.getLabels());
    return checkDetails;
  }

  public CheckEntity fromDTO(CheckDetails checkDetails, String accountIdentifier) {
    String expression = checkDetails.getRules()
                            .stream()
                            .map(rule
                                -> rule.getDataSourceIdentifier() + DOT_SEPARATOR + rule.getDataPointIdentifier()
                                    + rule.getOperator() + rule.getValue())
                            .collect(Collectors.joining(SPACE_SEPARATOR
                                + (checkDetails.getRuleStrategy() == CheckDetails.RuleStrategyEnum.ALL_OF ? "&&" : "||")
                                + SPACE_SEPARATOR));
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
        .labels(checkDetails.getLabels())
        .build();
  }
}
