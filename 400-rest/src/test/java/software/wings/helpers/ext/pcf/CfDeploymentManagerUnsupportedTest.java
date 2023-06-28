/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.pcf;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfConfig;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.command.ExecutionLogCallback;

import java.util.ArrayList;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

@OwnedBy(CDP)
public class CfDeploymentManagerUnsupportedTest extends WingsBaseTest {
  private final CfDeploymentManagerUnsupported deploymentManager = new CfDeploymentManagerUnsupported();
  private final CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
  @Mock private ExecutionLogCallback mockLogCallback;

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetOrganizations() {
    assertThatThrownBy(() -> deploymentManager.getOrganizations(cfRequestConfig))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetSpacesForOrganization() {
    assertThatThrownBy(() -> deploymentManager.getSpacesForOrganization(cfRequestConfig))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCreateApplication() {
    assertThatThrownBy(
        () -> deploymentManager.createApplication(CfCreateApplicationRequestData.builder().build(), mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testResizeApplication() {
    assertThatThrownBy(() -> deploymentManager.resizeApplication(cfRequestConfig, mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testDeleteApplication() {
    assertThatThrownBy(() -> deploymentManager.deleteApplication(cfRequestConfig))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testStopApplication() {
    assertThatThrownBy(() -> deploymentManager.stopApplication(cfRequestConfig))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetApplicationByName() {
    assertThatThrownBy(() -> deploymentManager.getApplicationByName(cfRequestConfig))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testUnmapRouteMapForApplication() {
    assertThatThrownBy(
        () -> deploymentManager.unmapRouteMapForApplication(cfRequestConfig, new ArrayList<>(), mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testMapRouteMapForApplication() {
    assertThatThrownBy(
        () -> deploymentManager.mapRouteMapForApplication(cfRequestConfig, new ArrayList<>(), mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetDeployedServicesWithNonZeroInstances() {
    assertThatThrownBy(() -> deploymentManager.getDeployedServicesWithNonZeroInstances(cfRequestConfig, ""))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetPreviousReleases() {
    assertThatThrownBy(() -> deploymentManager.getPreviousReleases(cfRequestConfig, ""))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetRouteMaps() {
    assertThatThrownBy(() -> deploymentManager.getRouteMaps(cfRequestConfig))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCheckConnectivity() {
    assertThat(deploymentManager.checkConnectivity(CfConfig.builder().build(), false))
        .isEqualTo("FAILED: connection timed out");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCreateRouteMap() {
    assertThatThrownBy(() -> deploymentManager.createRouteMap(cfRequestConfig, "", "", "", true, true, 1))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testPerformConfigureAutoscalar() {
    assertThatThrownBy(()
                           -> deploymentManager.performConfigureAutoscalar(
                               CfAppAutoscalarRequestData.builder().build(), mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testChangeAutoscalarState() {
    assertThatThrownBy(()
                           -> deploymentManager.changeAutoscalarState(
                               CfAppAutoscalarRequestData.builder().build(), mockLogCallback, true))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCheckIfAppHasAutoscalarAttached() {
    assertThatThrownBy(()
                           -> deploymentManager.checkIfAppHasAutoscalarAttached(
                               CfAppAutoscalarRequestData.builder().build(), mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testUpSizeApplicationWithSteadyStateCheck() {
    assertThatThrownBy(() -> deploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testIsActiveApplication() {
    assertThatThrownBy(() -> deploymentManager.isActiveApplication(cfRequestConfig, mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSetEnvironmentVariableForAppStatus() {
    assertThatThrownBy(
        () -> deploymentManager.setEnvironmentVariableForAppStatus(cfRequestConfig, true, mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testUnsetEnvironmentVariableForAppStatus() {
    assertThatThrownBy(() -> deploymentManager.unsetEnvironmentVariableForAppStatus(cfRequestConfig, mockLogCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }
}
