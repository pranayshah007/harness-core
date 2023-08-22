/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class InfrastructureUtilsTest {
  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorFork8Direct() {
    String connectorRefValue = "docker";
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();
    Optional<ParameterField<String>> optionalHarnessImageConnector =
        InfrastructureUtils.getHarnessImageConnector(infrastructure);
    assertThat(true).isEqualTo(optionalHarnessImageConnector.isPresent());
    assertThat(connectorRefValue).isEqualTo(optionalHarnessImageConnector.get().getValue());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorForVM() {
    String connectorRefValue = "docker";
    Infrastructure infrastructure =
        VmInfraYaml.builder()
            .spec(VmPoolYaml.builder()
                      .spec(VmPoolYamlSpec.builder()
                                .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                                .build())
                      .build())
            .build();
    Optional<ParameterField<String>> optionalHarnessImageConnector =
        InfrastructureUtils.getHarnessImageConnector(infrastructure);
    assertThat(true).isEqualTo(optionalHarnessImageConnector.isPresent());
    assertThat(connectorRefValue).isEqualTo(optionalHarnessImageConnector.get().getValue());
  }
}
