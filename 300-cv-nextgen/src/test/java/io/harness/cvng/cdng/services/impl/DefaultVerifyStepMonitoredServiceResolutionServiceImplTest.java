/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.cdng.beans.DefaultMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpec.MonitoredServiceSpecType;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class DefaultVerifyStepMonitoredServiceResolutionServiceImplTest extends CvNextGenTestBase {
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private DefaultVerifyStepMonitoredServiceResolutionServiceImpl defaultService;
  @Inject private MetricPackService metricPackService;

  private BuilderFactory builderFactory;
  private String accountId;
  private String projectIdentifier;
  private String orgIdentifier;
  private String serviceIdentifier;
  private String envIdentifier;
  private MonitoredServiceNode monitoredServiceNode;
  private DefaultMonitoredServiceSpec defaultMonitoredServiceSpec;
  private MonitoredServiceDTO monitoredServiceDTO;
  private ServiceEnvironmentParams serviceEnvironmentParams;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    serviceIdentifier = builderFactory.getContext().getServiceIdentifier();
    envIdentifier = builderFactory.getContext().getEnvIdentifier();
    monitoredServiceDTO = builderFactory.monitoredServiceDTOBuilder().build();
    defaultMonitoredServiceSpec = builderFactory.getDefaultMonitoredServiceSpecBuilder().build();
    monitoredServiceNode = getDefaultMonitoredServiceNode();
    serviceEnvironmentParams = getServiceEnvironmentParams();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceIdentifier_monitoredServiceExists() {
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    MonitoredServiceResponse monitoredServiceResponse =
        monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    String expectedIdentifier = monitoredServiceResponse.getMonitoredServiceDTO().getIdentifier();
    String actualIdentifier = defaultService.getResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode)
                                  .getMonitoredServiceIdentifier();
    assertThat(actualIdentifier).isEqualTo(expectedIdentifier);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceIdentifier_monitoredServiceDoesNotExist() {
    String actualIdentifier = defaultService.getResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode)
                                  .getMonitoredServiceIdentifier();
    assertThat(actualIdentifier).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigs_monitoredServiceExists() {
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<CVConfig> actualCvConfigs =
        defaultService.getResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode).getCvConfigs();
    assertThat(actualCvConfigs).hasSize(1);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigs_monitoredServiceDoesNotExist() {
    List<CVConfig> actualCvConfigs =
        defaultService.getResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode).getCvConfigs();
    assertThat(actualCvConfigs).isNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigs_healthSourcesDoNotExist() {
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceDTO.getSources().setHealthSources(Collections.emptySet());
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<CVConfig> actualCvConfigs =
        defaultService.getResolvedCVConfigInfo(serviceEnvironmentParams, monitoredServiceNode).getCvConfigs();
    assertThat(actualCvConfigs).isEmpty();
  }

  private MonitoredServiceNode getDefaultMonitoredServiceNode() {
    MonitoredServiceNode monitoredServiceNode = new MonitoredServiceNode();
    monitoredServiceNode.setSpec(defaultMonitoredServiceSpec);
    monitoredServiceNode.setType(MonitoredServiceSpecType.DEFAULT.name());
    return monitoredServiceNode;
  }

  private ServiceEnvironmentParams getServiceEnvironmentParams() {
    return ServiceEnvironmentParams.builder()
        .serviceIdentifier(serviceIdentifier)
        .environmentIdentifier(envIdentifier)
        .orgIdentifier(orgIdentifier)
        .accountIdentifier(accountId)
        .projectIdentifier(projectIdentifier)
        .build();
  }
}
