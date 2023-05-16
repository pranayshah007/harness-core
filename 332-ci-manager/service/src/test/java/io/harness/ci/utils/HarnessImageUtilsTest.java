/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.utils;

import static io.harness.rule.OwnerRule.RAGHAV_GUPTA;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.sweepingoutputs.K8StageInfraDetails;
import io.harness.beans.sweepingoutputs.VmStageInfraDetails;
import io.harness.beans.yaml.extended.infrastrucutre.Infrastructure;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.K8sDirectInfraYaml.K8sDirectInfraYamlSpec;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.apache.groovy.util.Maps;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class HarnessImageUtilsTest extends CIExecutionTestBase {
  private final Ambiance ambiance = Ambiance.newBuilder()
                                        .putAllSetupAbstractions(Maps.of("accountId", "accountId", "projectIdentifier",
                                            "projectIdentifier", "orgIdentifier", "orgIdentifier"))
                                        .build();
  private static final String connectorRefValue = "docker";

  @Mock private ConnectorUtils connectorUtils;
  @Mock private ConnectorDetails connectorDetails;
  @Mock private CIExecutionServiceConfig ciExecutionServiceConfig;
  @Inject private HarnessImageUtils harnessImageUtils;
  // private VmInfraInfo vmInfraInfo = VmInfraInfo.builder().poolId("test").build();
  private static final CIInitializeTaskParams.Type vmInfraInfo = CIInitializeTaskParams.Type.VM;

  @Before
  public void setUp() {
    on(harnessImageUtils).set("connectorUtils", connectorUtils);
    on(harnessImageUtils).set("ciExecutionServiceConfig", ciExecutionServiceConfig);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorForK8() {
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();
    when(connectorUtils.getConnectorDetails(any(), matches(connectorRefValue))).thenReturn(connectorDetails);
    when(connectorDetails.getIdentifier()).thenReturn(connectorRefValue);
    ConnectorDetails harnessImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(AmbianceUtils.getNgAccess(ambiance), infrastructure);
    assertNotNull(harnessImageConnector);
    assertThat(connectorRefValue).isEqualTo(harnessImageConnector.getIdentifier());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetDefaultHarnessImageConnectorForK8() {
    Infrastructure infrastructure = K8sDirectInfraYaml.builder().spec(K8sDirectInfraYamlSpec.builder().build()).build();
    when(ciExecutionServiceConfig.getDefaultInternalImageConnector()).thenReturn(connectorRefValue);
    when(connectorUtils.getDefaultInternalConnector(any())).thenReturn(connectorDetails);
    when(connectorDetails.getIdentifier()).thenReturn(connectorRefValue);
    ConnectorDetails harnessImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(AmbianceUtils.getNgAccess(ambiance), infrastructure);
    assertNotNull(harnessImageConnector);
    assertThat(connectorRefValue).isEqualTo(harnessImageConnector.getIdentifier());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorForK8WithDefaultAlsoPresent() {
    Infrastructure infrastructure =
        K8sDirectInfraYaml.builder()
            .spec(K8sDirectInfraYamlSpec.builder()
                      .harnessImageConnectorRef(ParameterField.createValueField(connectorRefValue))
                      .build())
            .build();
    when(ciExecutionServiceConfig.getDefaultInternalImageConnector()).thenReturn("default");
    when(connectorUtils.getConnectorDetails(any(), matches(connectorRefValue))).thenReturn(connectorDetails);
    when(connectorDetails.getIdentifier()).thenReturn(connectorRefValue);
    ConnectorDetails harnessImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(AmbianceUtils.getNgAccess(ambiance), infrastructure);
    assertNotNull(harnessImageConnector);
    assertThat(connectorRefValue).isEqualTo(harnessImageConnector.getIdentifier());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorForK8WithoutConnectorRef() {
    Infrastructure infrastructure = K8sDirectInfraYaml.builder().spec(K8sDirectInfraYamlSpec.builder().build()).build();
    ConnectorDetails harnessImageConnector =
        harnessImageUtils.getHarnessImageConnectorDetailsForK8(AmbianceUtils.getNgAccess(ambiance), infrastructure);
    assertNull(harnessImageConnector);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorForVM() {
    VmStageInfraDetails vmStageInfraDetails =
        VmStageInfraDetails.builder().infraInfo(vmInfraInfo).harnessImageConnectorRef(connectorRefValue).build();
    when(connectorUtils.getConnectorDetails(any(), matches(connectorRefValue))).thenReturn(connectorDetails);
    when(connectorDetails.getIdentifier()).thenReturn(connectorRefValue);
    ConnectorDetails harnessImageConnector = harnessImageUtils.getHarnessImageConnectorDetailsForVM(
        AmbianceUtils.getNgAccess(ambiance), vmStageInfraDetails);
    assertNotNull(harnessImageConnector);
    assertThat(connectorRefValue).isEqualTo(harnessImageConnector.getIdentifier());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetDefaultHarnessImageConnectorForVM() {
    VmStageInfraDetails vmStageInfraDetails = VmStageInfraDetails.builder().infraInfo(vmInfraInfo).build();
    when(ciExecutionServiceConfig.getDefaultInternalImageConnector()).thenReturn(connectorRefValue);
    when(connectorUtils.getDefaultInternalConnector(any())).thenReturn(connectorDetails);
    when(connectorDetails.getIdentifier()).thenReturn(connectorRefValue);
    ConnectorDetails harnessImageConnector = harnessImageUtils.getHarnessImageConnectorDetailsForVM(
        AmbianceUtils.getNgAccess(ambiance), vmStageInfraDetails);
    assertNotNull(harnessImageConnector);
    assertThat(connectorRefValue).isEqualTo(harnessImageConnector.getIdentifier());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorForVMWithDefaultAlsoPresent() {
    VmStageInfraDetails vmStageInfraDetails =
        VmStageInfraDetails.builder().infraInfo(vmInfraInfo).harnessImageConnectorRef(connectorRefValue).build();
    when(ciExecutionServiceConfig.getDefaultInternalImageConnector()).thenReturn("default");
    when(connectorUtils.getConnectorDetails(any(), matches(connectorRefValue))).thenReturn(connectorDetails);
    when(connectorDetails.getIdentifier()).thenReturn(connectorRefValue);
    ConnectorDetails harnessImageConnector = harnessImageUtils.getHarnessImageConnectorDetailsForVM(
        AmbianceUtils.getNgAccess(ambiance), vmStageInfraDetails);
    assertNotNull(harnessImageConnector);
    assertThat(connectorRefValue).isEqualTo(harnessImageConnector.getIdentifier());
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorForVMWithoutConnectorRef() {
    VmStageInfraDetails vmStageInfraDetails = VmStageInfraDetails.builder().infraInfo(vmInfraInfo).build();
    ConnectorDetails harnessImageConnector = harnessImageUtils.getHarnessImageConnectorDetailsForVM(
        AmbianceUtils.getNgAccess(ambiance), vmStageInfraDetails);
    assertNull(harnessImageConnector);
  }

  @Test
  @Owner(developers = RAGHAV_GUPTA)
  @Category(UnitTests.class)
  public void testGetHarnessImageConnectorFork8InfraDetails() {
    K8StageInfraDetails k8StageInfraDetails = K8StageInfraDetails.builder().build();
    assertThatThrownBy(()
                           -> harnessImageUtils.getHarnessImageConnectorDetailsForVM(
                               AmbianceUtils.getNgAccess(ambiance), k8StageInfraDetails))
        .isInstanceOf(InvalidRequestException.class);
  }
}
