/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.cdng.services.impl;

import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.cdng.beans.CVNGStepInfo;
import io.harness.cvng.cdng.beans.DefaultMonitoredServiceSpec;
import io.harness.cvng.cdng.beans.MonitoredServiceNode;
import io.harness.cvng.cdng.beans.MonitoredServiceSpecType;
import io.harness.cvng.cdng.services.api.CDStageMetaDataService;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceDTO;
import io.harness.cvng.core.beans.monitoredService.MonitoredServiceResponse;
import io.harness.cvng.core.beans.params.ServiceEnvironmentParams;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.cvng.core.services.api.monitoredService.MonitoredServiceService;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.ng.core.dto.CDStageMetaDataDTO;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.filter.creation.beans.FilterCreationContext;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class DefaultPipelineStepMonitoredServiceResolutionServiceImplTest extends CvNextGenTestBase {
  @Inject private MonitoredServiceService monitoredServiceService;
  @Inject private DefaultPipelineStepMonitoredServiceResolutionServiceImpl defaultService;
  @Inject private MetricPackService metricPackService;
  @Mock private CDStageMetaDataService mockedCdStageMetaDataService;
  @Mock private MonitoredServiceService mockedMonitoredServiceService;

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
  private Ambiance ambiance = mock(Ambiance.class);

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
    String actualIdentifier =
        defaultService.fetchAndPersistResolvedCVConfigInfo(ambiance, serviceEnvironmentParams, monitoredServiceNode)
            .getMonitoredServiceIdentifier();
    assertThat(actualIdentifier).isEqualTo(expectedIdentifier);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetMonitoredServiceIdentifier_monitoredServiceDoesNotExist() {
    String actualIdentifier =
        defaultService.fetchAndPersistResolvedCVConfigInfo(ambiance, serviceEnvironmentParams, monitoredServiceNode)
            .getMonitoredServiceIdentifier();
    assertThat(actualIdentifier).isNotNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigs_monitoredServiceExists() {
    metricPackService.createDefaultMetricPackAndThresholds(accountId, orgIdentifier, projectIdentifier);
    monitoredServiceService.create(builderFactory.getContext().getAccountId(), monitoredServiceDTO);
    List<CVConfig> actualCvConfigs =
        defaultService.fetchAndPersistResolvedCVConfigInfo(ambiance, serviceEnvironmentParams, monitoredServiceNode)
            .getCvConfigs();
    assertThat(actualCvConfigs).hasSize(1);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigs_monitoredServiceDoesNotExist() {
    List<CVConfig> actualCvConfigs =
        defaultService.fetchAndPersistResolvedCVConfigInfo(ambiance, serviceEnvironmentParams, monitoredServiceNode)
            .getCvConfigs();
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
        defaultService.fetchAndPersistResolvedCVConfigInfo(ambiance, serviceEnvironmentParams, monitoredServiceNode)
            .getCvConfigs();
    assertThat(actualCvConfigs).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetReferredEntities_serviceEnvRefAreAbsent() throws IOException, IllegalAccessException {
    FilterCreationContext filterCreationContext = createFilterCreationContext();
    ResponseDTO<CDStageMetaDataDTO> responseDTO = ResponseDTO.newResponse();
    responseDTO.setData(CDStageMetaDataDTO.builder().build());
    when(mockedCdStageMetaDataService.getServiceAndEnvironmentRef(any())).thenReturn(responseDTO);

    List<EntityDetailProtoDTO> referredEntities = defaultService.getReferredEntities(
        filterCreationContext, new CVNGStepInfo(), builderFactory.getProjectParams());
    assertThat(referredEntities).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetReferredEntities_serviceEnvRefListSizeIsMoreThan1() throws IOException, IllegalAccessException {
    FilterCreationContext filterCreationContext = createFilterCreationContext();
    ResponseDTO<CDStageMetaDataDTO> responseDTO = ResponseDTO.newResponse();
    responseDTO.setData(
        CDStageMetaDataDTO.builder()
            .serviceEnvRef(CDStageMetaDataDTO.ServiceEnvRef.builder().serviceRef("s").environmentRef("e").build())
            .serviceEnvRef(CDStageMetaDataDTO.ServiceEnvRef.builder().serviceRef("s").environmentRef("e").build())
            .build());
    when(mockedCdStageMetaDataService.getServiceAndEnvironmentRef(any())).thenReturn(responseDTO);

    List<EntityDetailProtoDTO> referredEntities = defaultService.getReferredEntities(
        filterCreationContext, new CVNGStepInfo(), builderFactory.getProjectParams());
    assertThat(referredEntities).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetReferredEntities_cdStageMetaDataIsAbsent() throws IOException, IllegalAccessException {
    FilterCreationContext filterCreationContext = createFilterCreationContext();
    ResponseDTO<CDStageMetaDataDTO> responseDTO = ResponseDTO.newResponse();
    when(mockedCdStageMetaDataService.getServiceAndEnvironmentRef(any())).thenReturn(responseDTO);

    List<EntityDetailProtoDTO> referredEntities = defaultService.getReferredEntities(
        filterCreationContext, new CVNGStepInfo(), builderFactory.getProjectParams());
    assertThat(referredEntities).isEmpty();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetReferredEntities_serviceEnvRefListSizeIs1() throws IOException, IllegalAccessException {
    FilterCreationContext filterCreationContext = createFilterCreationContext();
    ResponseDTO<CDStageMetaDataDTO> responseDTO = ResponseDTO.newResponse();
    responseDTO.setData(
        CDStageMetaDataDTO.builder()
            .serviceEnvRef(CDStageMetaDataDTO.ServiceEnvRef.builder().serviceRef("s").environmentRef("e").build())
            .build());
    when(mockedCdStageMetaDataService.getServiceAndEnvironmentRef(any())).thenReturn(responseDTO);

    List<EntityDetailProtoDTO> referredEntities = defaultService.getReferredEntities(
        filterCreationContext, new CVNGStepInfo(), builderFactory.getProjectParams());
    assertThat(referredEntities).hasSize(1);
  }

  private FilterCreationContext createFilterCreationContext() throws IOException, IllegalAccessException {
    FieldUtils.writeField(defaultService, "cdStageMetaDataService", mockedCdStageMetaDataService, true);
    FieldUtils.writeField(defaultService, "monitoredServiceService", mockedMonitoredServiceService, true);
    when(mockedMonitoredServiceService.getApplicationMonitoredServiceDTO(any())).thenReturn(monitoredServiceDTO);

    String yaml = getResource("pipeline/stage-yaml-node.yaml");
    YamlField yamlField = YamlUtils.readTree(yaml);
    return FilterCreationContext.builder().currentField(yamlField).build();
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
