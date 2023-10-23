/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.infra.yaml;

import static io.harness.rule.OwnerRule.ACHYUTH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.CDP)
public class CdngInfraYamlTest extends CategoryTest {
  private K8sGcpInfrastructure k8sGcpInfrastructure = K8sGcpInfrastructure.builder()
                                                          .connectorRef(ParameterField.createValueField("ConnectorDisconnectHandler"))
                                                          .namespace(ParameterField.createValueField("default"))
                                                          .cluster(ParameterField.createValueField("my-cluster"))
                                                          .releaseName(ParameterField.createValueField("rel-123"))
                                                          .build();

  private K8SDirectInfrastructure k8SDirectInfrastructure =
      K8SDirectInfrastructure.builder()
          .connectorRef(ParameterField.createValueField("connectorDirect"))
          .namespace(ParameterField.createValueField("defaultDirect"))
          .releaseName(ParameterField.createValueField("rel-Direct-123"))
          .build();
  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testApplyOverRides() {
    K8sGcpInfrastructure k8sGcpInfrastructureNew = K8sGcpInfrastructure.builder()
                                                       .connectorRef(ParameterField.createValueField("connector1"))
                                                       .namespace(ParameterField.createValueField("default1"))
                                                       .cluster(ParameterField.createValueField("my-cluster-1"))
                                                       .releaseName(ParameterField.createValueField("rel-1234"))
                                                       .build();
    assertThat(k8sGcpInfrastructure.applyOverrides(k8sGcpInfrastructureNew).getConnectorReference().getValue())
        .isEqualTo("connector1");

    K8SDirectInfrastructure k8SDirectInfrastructureNew =
        K8SDirectInfrastructure.builder()
            .connectorRef(ParameterField.createValueField("connectorDirect2"))
            .namespace(ParameterField.createValueField("defaultDirect2"))
            .releaseName(ParameterField.createValueField("rel-Direct-1234"))
            .build();
    assertThat(k8SDirectInfrastructure.applyOverrides(k8SDirectInfrastructureNew).getInfrastructureKeyValues())
        .isEqualTo(new String[] {"connectorDirect2", "defaultDirect2"});
  }
  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testExtractAndGetConnectorRefs() {
    assertThat(k8sGcpInfrastructure.extractConnectorRefs().get("connectorRef").getValue()).isEqualTo("ConnectorDisconnectHandler");

    assertThat(k8SDirectInfrastructure.getConnectorRef().getValue()).isEqualTo("connectorDirect");
  }
}
