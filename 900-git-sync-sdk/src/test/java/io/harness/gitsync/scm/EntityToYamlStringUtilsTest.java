/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.scm;

import static io.harness.delegate.beans.connector.ConnectorType.KUBERNETES_CLUSTER;
import static io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialType.INHERIT_FROM_DELEGATE;
import static io.harness.rule.OwnerRule.ABHINAV;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.SampleBean;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesClusterConfigDTO;
import io.harness.delegate.beans.connector.k8Connector.KubernetesCredentialDTO;
import io.harness.gitsync.beans.YamlDTO;
import io.harness.ng.core.utils.NGYamlUtils;
import io.harness.rule.Owner;

import java.io.IOException;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@OwnedBy(HarnessTeam.DX)
public class EntityToYamlStringUtilsTest extends CategoryTest {
  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetYamlString() throws IOException {
    final SampleBean sampleBean = SampleBean.builder()
                                      .accountIdentifier("accountid")
                                      .test1("test1")
                                      .orgIdentifier("orgid")
                                      .projectIdentifier("projid")
                                      .build();
    final String yamlString = NGYamlUtils.getYamlString(sampleBean);
    String yaml = IOUtils.resourceToString("testYaml.yaml", UTF_8, this.getClass().getClassLoader());
    assertThat(yaml).isEqualTo(yamlString);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetYamlStringNew() throws IOException {
    final SampleBean sampleBean = SampleBean.builder()
                                      .accountIdentifier("accountid")
                                      .test1("test1")
                                      .orgIdentifier("orgid")
                                      .projectIdentifier("projid")
                                      .build();
    final String yamlString = NGYamlUtils.getYamlString(TestYamlClass.builder().a("as").sampleBean(sampleBean).build());
    String yaml = IOUtils.resourceToString("testYaml1.yaml", UTF_8, this.getClass().getClassLoader());
    assertThat(yaml).isEqualTo(yamlString);
  }

  @Test
  @Owner(developers = ABHINAV)
  @Category(UnitTests.class)
  public void testGetYamlStringNew_1() throws IOException {
    ConnectorInfoDTO connectorInfo =
        ConnectorInfoDTO.builder()
            .name("ConnectorDisconnectHandler")
            .identifier("identifier")
            .connectorType(KUBERNETES_CLUSTER)
            .connectorConfig(KubernetesClusterConfigDTO.builder()
                                 .credential(KubernetesCredentialDTO.builder()
                                                 .kubernetesCredentialType(INHERIT_FROM_DELEGATE)
                                                 .config(null)
                                                 .build())
                                 .build())
            .build();
    ConnectorDTO connectorRequest = ConnectorDTO.builder().connectorInfo(connectorInfo).build();
    final String yamlString = NGYamlUtils.getYamlString(connectorRequest);
    String yaml = IOUtils.resourceToString("testConnector.yaml", UTF_8, this.getClass().getClassLoader());
    assertThat(yaml).isEqualTo(yamlString);
  }

  @Data
  @Builder
  public static class TestYamlClass implements YamlDTO {
    String a;
    String b;
    List<String> c;
    SampleBean sampleBean;
  }
}
