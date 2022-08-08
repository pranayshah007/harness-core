/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.rule.OwnerRule.ABHISHEK;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.infra.yaml.Infrastructure;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDC)
public class InfrastructurePlanCreatorHelperTest extends CategoryTest {
  @Test
  @Owner(developers = ABHISHEK)
  @Category(UnitTests.class)
  public void testSetInfraIdentifierAndName_Infrastructure() {
    Infrastructure k8SDirectInfrastructure = K8SDirectInfrastructure.builder().build();
    InfrastructurePlanCreatorHelper.setInfraIdentifierAndName(k8SDirectInfrastructure, "Identifier", "Name");
    assertThat(((K8SDirectInfrastructure) k8SDirectInfrastructure).getInfraIdentifier()).isEqualTo("Identifier");
    assertThat(((K8SDirectInfrastructure) k8SDirectInfrastructure).getInfraName()).isEqualTo("Name");
  }
}
