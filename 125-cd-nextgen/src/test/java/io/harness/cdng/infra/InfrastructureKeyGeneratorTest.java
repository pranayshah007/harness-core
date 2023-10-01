/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.rule.OwnerRule.ABHINAV2;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.InfrastructureKeyGenerator.InfraKey;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.rule.Owner;
import io.harness.steps.environment.EnvironmentOutcome;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class InfrastructureKeyGeneratorTest extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV2)
  @Category(UnitTests.class)
  public void testInfraKey() {
    ServiceStepOutcome serviceStepOutcome = ServiceStepOutcome.builder().identifier("ABC").build();
    EnvironmentOutcome environmentOutcome = EnvironmentOutcome.builder().identifier("DEF").build();

    InfraKey infraKey = InfrastructureKeyGenerator.createInfraKey(serviceStepOutcome, environmentOutcome, "G", "H");
    String fullInfraKey =
        InfrastructureKeyGenerator.createFullInfraKey(serviceStepOutcome, environmentOutcome, "G", "H");
    String infraKeyValue = infraKey.getKey();
    String infraKeyShort = infraKey.getShortKey();
    assertThat(StringUtils.getCommonPrefix(infraKeyValue, infraKeyShort)).isEqualTo(infraKeyShort);
    assertThat(infraKeyValue).isEqualTo("80064c5b9075322a1eac0ecbdb3df0aa6f108a58");
    assertThat(infraKeyShort).isEqualTo("80064c");
    assertThat(fullInfraKey).isEqualTo("80064c5b9075322a1eac0ecbdb3df0aa6f108a58");
  }
}
