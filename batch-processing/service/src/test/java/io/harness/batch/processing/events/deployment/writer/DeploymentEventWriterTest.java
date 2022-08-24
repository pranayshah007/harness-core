/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.events.deployment.writer;

import static io.harness.rule.OwnerRule.HITESH;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.batch.processing.ccm.CCMJobConstants;
import io.harness.batch.processing.ccm.CostEventSource;
import io.harness.batch.processing.ccm.CostEventType;
import io.harness.batch.processing.events.timeseries.data.CostEventData;
import io.harness.batch.processing.events.timeseries.service.intfc.CostEventService;
import io.harness.category.element.UnitTests;
import io.harness.ccm.commons.beans.HarnessServiceInfo;
import io.harness.rule.Owner;

import software.wings.api.DeploymentSummary;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameter;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.StepExecution;

@RunWith(MockitoJUnitRunner.class)
public class DeploymentEventWriterTest extends CategoryTest {
  @InjectMocks private DeploymentEventWriter deploymentEventWriter;

  @Mock private StepExecution stepExecution;
  @Mock private CostEventService costEventService;
  @Mock private CloudToHarnessMappingService cloudToHarnessMappingService;

  private static final String APP_ID = "appId";
  private static final String ENV_ID = "envId";
  private static final String ENV_NAME = "envName";
  private static final String SERVICE_ID_TWO = "serviceIdTwo";
  private static final String SERVICE_ID = "serviceId";
  private static final String ACCOUNT_ID = "account_id";
  private static final String SERVICE_NAME = "serviceName";
  private static final String SERVICE_NAME_TWO = "serviceNameTwo";
  private static final String INFRA_MAPPING_ID = "infraMappingId";
  private static final String INFRA_MAPPING_ID_TWO = "infraMappingIdTwo";
  private static final String CLOUD_PROVIDER_ID = "cloudProviderId";
  private static final String DEPLOYMENT_UID = "deploymentUId";
  private static final String DEPLOYMENT_UID_TWO = "deploymentUIdTwo";

  private final Instant NOW = Instant.now();
  private final long START_TIME_MILLIS = NOW.minus(1, ChronoUnit.HOURS).toEpochMilli();
  private final long END_TIME_MILLIS = NOW.toEpochMilli();

  @Before
  public void setup() {
    Map<String, JobParameter> parameters = new HashMap<>();
    parameters.put(CCMJobConstants.JOB_START_DATE, new JobParameter(String.valueOf(START_TIME_MILLIS), true));
    parameters.put(CCMJobConstants.JOB_END_DATE, new JobParameter(String.valueOf(END_TIME_MILLIS), true));
    parameters.put(CCMJobConstants.ACCOUNT_ID, new JobParameter(ACCOUNT_ID, true));
    JobParameters jobParameters = new JobParameters(parameters);

    when(stepExecution.getJobExecution()).thenReturn(new JobExecution(START_TIME_MILLIS, jobParameters));
    deploymentEventWriter.beforeStep(stepExecution);
  }

  @Test
  @Owner(developers = HITESH)
  @Category(UnitTests.class)
  public void testWriteDeploymentEvents() {
    when(cloudToHarnessMappingService.getDeploymentSummary(ACCOUNT_ID, String.valueOf(0),
             Instant.ofEpochMilli(START_TIME_MILLIS), Instant.ofEpochMilli(END_TIME_MILLIS)))
        .thenReturn(deploymentSummaryList());
    when(cloudToHarnessMappingService.getDeploymentSummary(ACCOUNT_ID, String.valueOf(1),
             Instant.ofEpochMilli(START_TIME_MILLIS), Instant.ofEpochMilli(END_TIME_MILLIS)))
        .thenReturn(emptyList());
    when(cloudToHarnessMappingService.getServiceName(any(), any())).thenReturn(serviceName());
    when(cloudToHarnessMappingService.getEnvName(any(), any())).thenReturn(envName());
    when(cloudToHarnessMappingService.getHarnessServiceInfoList(any())).thenReturn(harnessServiceInfoList());

    deploymentEventWriter.write(emptyList());

    ArgumentCaptor<List> costEventDataArgumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(costEventService).create(costEventDataArgumentCaptor.capture());
    List value = costEventDataArgumentCaptor.getValue();
    CostEventData costEventData = (CostEventData) value.get(0);
    assertThat(costEventData.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(costEventData.getAppId()).isEqualTo(APP_ID);
    assertThat(costEventData.getServiceId()).isEqualTo(SERVICE_ID);
    assertThat(costEventData.getEnvId()).isEqualTo(ENV_ID);
    assertThat(costEventData.getCloudProviderId()).isEqualTo(CLOUD_PROVIDER_ID);
    assertThat(costEventData.getDeploymentId()).isEqualTo(DEPLOYMENT_UID);
    assertThat(costEventData.getCostEventType()).isEqualTo(CostEventType.DEPLOYMENT.name());
    assertThat(costEventData.getCostEventSource()).isEqualTo(CostEventSource.HARNESS_CD.name());
    assertThat(costEventData.getStartTimestamp()).isEqualTo(START_TIME_MILLIS);
    assertThat(costEventData.getEventDescription()).isEqualTo("Service serviceName got deployed to envName.");

    CostEventData costEventDataSecond = (CostEventData) value.get(1);
    assertThat(costEventDataSecond.getAppId()).isEqualTo(APP_ID);
    assertThat(costEventDataSecond.getEnvId()).isEqualTo(ENV_ID);
    assertThat(costEventDataSecond.getCloudProviderId()).isEqualTo(CLOUD_PROVIDER_ID);
    assertThat(costEventDataSecond.getDeploymentId()).isEqualTo(DEPLOYMENT_UID_TWO);
    assertThat(costEventDataSecond.getServiceId()).isEqualTo(SERVICE_ID_TWO);
    assertThat(costEventDataSecond.getEnvId()).isEqualTo(ENV_ID);
    assertThat(costEventDataSecond.getEventDescription()).isEqualTo("Service serviceNameTwo got deployed to envName.");
  }

  private List<DeploymentSummary> deploymentSummaryList() {
    DeploymentSummary deploymentSummaryFirst = DeploymentSummary.builder()
                                                   .infraMappingId(INFRA_MAPPING_ID)
                                                   .uuid(DEPLOYMENT_UID)
                                                   .deployedAt(START_TIME_MILLIS)
                                                   .build();
    DeploymentSummary deploymentSummarySecond = DeploymentSummary.builder()
                                                    .infraMappingId(INFRA_MAPPING_ID_TWO)
                                                    .uuid(DEPLOYMENT_UID_TWO)
                                                    .deployedAt(START_TIME_MILLIS)
                                                    .build();
    return Arrays.asList(deploymentSummaryFirst, deploymentSummarySecond);
  }

  private Map<String, String> serviceName() {
    return Collections.unmodifiableMap(new HashMap<String, String>() {
      {
        put(SERVICE_ID, SERVICE_NAME);
        put(SERVICE_ID_TWO, SERVICE_NAME_TWO);
      }
    });
  }

  private Map<String, String> envName() {
    return Collections.unmodifiableMap(new HashMap<String, String>() {
      { put(ENV_ID, ENV_NAME); }
    });
  }

  private List<HarnessServiceInfo> harnessServiceInfoList() {
    HarnessServiceInfo harnessServiceInfoFirst =
        new HarnessServiceInfo(SERVICE_ID, APP_ID, CLOUD_PROVIDER_ID, ENV_ID, INFRA_MAPPING_ID, DEPLOYMENT_UID);

    HarnessServiceInfo harnessServiceInfoSecond = new HarnessServiceInfo(
        SERVICE_ID_TWO, APP_ID, CLOUD_PROVIDER_ID, ENV_ID, INFRA_MAPPING_ID_TWO, DEPLOYMENT_UID_TWO);

    return Arrays.asList(harnessServiceInfoFirst, harnessServiceInfoSecond);
  }
}
