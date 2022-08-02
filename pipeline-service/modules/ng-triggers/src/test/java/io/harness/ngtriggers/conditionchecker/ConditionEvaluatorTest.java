/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngtriggers.conditionchecker;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.ENDS_WITH_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.EQUALS_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.IN_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.NOT_EQUALS_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.NOT_IN_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.REGEX_OPERATOR;
import static io.harness.ngtriggers.conditionchecker.OperationEvaluator.STARTS_WITH_OPERATOR;
import static io.harness.rule.OwnerRule.NAMAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(PIPELINE)
public class ConditionEvaluatorTest extends CategoryTest {
  @Test
  @Owner(developers = NAMAN)
  @Category(UnitTests.class)
  public void testEvaluate() {
    assertThat(ConditionEvaluator.evaluate("test", "test", EQUALS_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("test", "test1", EQUALS_OPERATOR)).isFalse();

    assertThat(ConditionEvaluator.evaluate("test", "test1", NOT_EQUALS_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("test", "test", NOT_EQUALS_OPERATOR)).isFalse();

    assertThat(ConditionEvaluator.evaluate("prod_deploy", "prod", STARTS_WITH_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("prod_deploy", "qa", STARTS_WITH_OPERATOR)).isFalse();

    assertThat(ConditionEvaluator.evaluate("deploy_prod", "prod", ENDS_WITH_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("deploy_prod", "qa", ENDS_WITH_OPERATOR)).isFalse();

    assertThat(ConditionEvaluator.evaluate("prod", "prod, qa, stage", IN_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("prod", "qa, stage", IN_OPERATOR)).isFalse();
    assertThat(ConditionEvaluator.evaluate("prod,d", "\"prod,d\", qa, stage", IN_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("prod,\"d", "\"prod,\"\"d\", qa, stage", IN_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("master", "master, release/*", IN_OPERATOR)).isTrue();

    assertThat(ConditionEvaluator.evaluate("release/saas///", "master, release/saas/*", IN_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("release/saas", "master, release/saas/*", IN_OPERATOR)).isTrue();
    // using ^ is  redundant here, as there is only 1 regex, but used for covering test scenario
    assertThat(ConditionEvaluator.evaluate("release/saas/2401", "master, ^release/saas/.*", IN_OPERATOR)).isTrue();

    assertThat(ConditionEvaluator.evaluate("release/on-prem/2401", "master, release/saas/.*", IN_OPERATOR)).isFalse();
    assertThat(ConditionEvaluator.evaluate("release/on-prem/2401", "master, ^release/saas/.*", IN_OPERATOR)).isFalse();

    assertThat(ConditionEvaluator.evaluate("release/on-prem/2401", "master, release/.*", IN_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("release/on-prem/2401", "master, ^release/.*", IN_OPERATOR)).isTrue();

    assertThat(ConditionEvaluator.evaluate("master1", "master, release/saas/.*", IN_OPERATOR)).isFalse();

    assertThat(ConditionEvaluator.evaluate("prod", "qa, stage, uat", NOT_IN_OPERATOR)).isTrue();
    assertThat(ConditionEvaluator.evaluate("prod", "prod, qa, stage, uat", NOT_IN_OPERATOR)).isFalse();

    assertThat(
        ConditionEvaluator.evaluate("trigger recencycheck, unit-tests-1, unit-tests-2, unit-tests-3, author-metadata",
            "^.*trigger.*[, ](unit-tests-2$|unit-tests-2,.*)", REGEX_OPERATOR))
        .isTrue();
    assertThat(
        ConditionEvaluator.evaluate("trigger unit-tests-2, recencycheck, unit-tests-1, unit-tests-3, author-metadata",
            "^.*trigger.*[, ](unit-tests-2$|unit-tests-2,.*)", REGEX_OPERATOR))
        .isTrue();
    assertThat(
        ConditionEvaluator.evaluate("trigger recencycheck, unit-tests-1, unit-tests-3, author-metadata, unit-tests-2",
            "^.*trigger.*[, ](unit-tests-2$|unit-tests-2,.*)", REGEX_OPERATOR))
        .isTrue();
    assertThat(
        ConditionEvaluator.evaluate("trigger recencycheck, unit-tests-1, unit-tests-3, author-metadata, unit-tests-20",
            "^.*trigger.*[, ](unit-tests-2$|unit-tests-2,.*)", REGEX_OPERATOR))
        .isFalse();
    assertThat(
        ConditionEvaluator.evaluate("trigger unit-tests-22, recencycheck, unit-tests-1, unit-tests-3, author-metadata",
            "^.*trigger.*[, ](unit-tests-2$|unit-tests-2,.*)", REGEX_OPERATOR))
        .isFalse();

    assertThat(ConditionEvaluator.evaluate("create_image xyz\r\n", "^create_image.*", REGEX_OPERATOR)).isTrue();
  }
}
