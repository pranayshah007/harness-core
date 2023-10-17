/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.execution.integrationstage;

import static io.harness.rule.OwnerRule.SAHITHI;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.category.element.UnitTests;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;

public class BuildJobEnvInfoBuilderTest extends CIExecutionTestBase {
  public static final String ACCOUNT_ID = "accountId";
  @InjectMocks BuildJobEnvInfoBuilder buildJobEnvInfoBuilder;
  @Mock CIFeatureFlagService ciFeatureFlagService;

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getVmTimeout() {
    int response =
        buildJobEnvInfoBuilder.getTimeout(VmInfraYaml.builder().type(Infrastructure.Type.VM).build(), ACCOUNT_ID);
    assertThat(response).isEqualTo(900 * 1000L);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void getKubernetisTimeoutDefault() {
    String connectorRefValue = "docker";
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();
    int response = buildJobEnvInfoBuilder.getTimeout(infrastructure, ACCOUNT_ID);
    assertThat(response).isEqualTo(600 * 1000L);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void getKubernetisTimeoutWhenGivenInitTimeout() {
    String connectorRefValue = "docker";

    ParameterField<String> timeout = new ParameterField<>();

    timeout.setValue("120s");
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .initTimeout(timeout)
                      .build())
            .build();
    int response = buildJobEnvInfoBuilder.getTimeout(infrastructure, ACCOUNT_ID);
    assertThat(response).isEqualTo(120 * 1000L);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void getKubernetisTimeoutWhenGivenOstypeWindows() {
    String connectorRefValue = "docker";

    ParameterField<OSType> osTypeParameterField = new ParameterField<>();
    osTypeParameterField.setValue(OSType.Windows);
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .os(osTypeParameterField)
                      .build())
            .build();
    int response = buildJobEnvInfoBuilder.getTimeout(infrastructure, ACCOUNT_ID);
    assertThat(response).isEqualTo(900 * 1000L);
  }

  @Test
  @Owner(developers = SAHITHI)
  @Category(UnitTests.class)
  public void getKubernetisTimeoutWhenQueueEnabled() {
    String connectorRefValue = "docker";
    when(ciFeatureFlagService.isEnabled(eq(FeatureName.QUEUE_CI_EXECUTIONS_CONCURRENCY), any())).thenReturn(true);
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();
    int response = buildJobEnvInfoBuilder.getTimeout(infrastructure, ACCOUNT_ID);
    assertThat(response).isEqualTo(36000 * 1000L);
  }
}
