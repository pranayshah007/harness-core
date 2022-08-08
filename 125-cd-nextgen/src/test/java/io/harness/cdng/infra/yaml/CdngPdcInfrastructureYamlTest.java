/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.rule.OwnerRule.FILIP;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.rule.Owner;

import java.util.Arrays;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CdngPdcInfrastructureYamlTest extends CategoryTest {
  private PdcInfrastructure infrastructure =
      PdcInfrastructure.builder()
          .credentialsRef(ParameterField.createValueField("some-key-ref"))
          .connectorRef(ParameterField.createValueField("some-connector-ref"))
          .hostFilters(ParameterField.createValueField(Arrays.asList("host-1", "host-2")))
          .delegateSelectors(ParameterField.createValueField(
              Arrays.asList(new TaskSelectorYaml("selector-1"), new TaskSelectorYaml("selector-2"))))
          .build();

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testApplyOverrides() {
    PdcInfrastructure infrastructureNew =
        PdcInfrastructure.builder()
            .credentialsRef(ParameterField.createValueField("another-key-ref"))
            .connectorRef(ParameterField.createValueField("another-connector-ref"))
            .hostFilters(ParameterField.createValueField(Arrays.asList("host-3", "host-4")))
            .build();

    assertThat(infrastructure.applyOverrides(infrastructureNew))
        .extracting(PdcInfrastructure::getCredentialsRef, PdcInfrastructure::getConnectorRef,
            PdcInfrastructure::getHostFilters, PdcInfrastructure::getDelegateSelectors)
        .containsExactly(infrastructureNew.getCredentialsRef(), infrastructureNew.getConnectorRef(),
            infrastructureNew.getHostFilters(), infrastructure.getDelegateSelectors());
  }

  @Test
  @Owner(developers = FILIP)
  @Category(UnitTests.class)
  public void testExtractAndGetConnectorRefs() {
    assertThat(infrastructure.extractConnectorRefs().get(YAMLFieldNameConstants.CONNECTOR_REF).getValue())
        .isEqualTo("some-connector-ref");
  }
}
