/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.DataCollectionRequest;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.client.VerificationManagerService;
import io.harness.cvng.core.beans.params.ProjectParams;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.rule.Owner;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class CloudWatchServiceImplTest extends CvNextGenTestBase {
  @Inject private CloudWatchService cloudWatchService;
  @Inject OnboardingService onboardingService;
  @Mock VerificationManagerService verificationManagerService;
  @Mock NextGenService nextGenService;
  private String connectorIdentifier;
  private BuilderFactory builderFactory;
  private ProjectParams projectParams;

  @Before
  public void setup() throws IllegalAccessException {
    builderFactory = BuilderFactory.getDefault();
    projectParams = builderFactory.getProjectParams();
    connectorIdentifier = generateUuid();
    FieldUtils.writeField(cloudWatchService, "onboardingService", onboardingService, true);
    FieldUtils.writeField(onboardingService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(onboardingService, "verificationManagerService", verificationManagerService, true);

    when(nextGenService.get(anyString(), anyString(), anyString(), anyString()))
        .then(invocation
            -> Optional.of(ConnectorInfoDTO.builder().connectorConfig(AwsConnectorDTO.builder().build()).build()));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testFetchRegions() {
    List<String> regions = cloudWatchService.fetchRegions();
    assertThat(regions).isNotNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testFetchSampleData() throws IOException {
    String responseObject = readResource("sample-metric-data.json");
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(responseObject);

    Map sampleDataResponse = cloudWatchService.fetchSampleData(builderFactory.getProjectParams(), connectorIdentifier,
        generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid());

    assertThat(sampleDataResponse).isNotNull();
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testFetchSampleData_unexpectedMultipleTimeSeriesResponse() throws IOException {
    String responseObject = readResource("sample-metric-data-multiple-time-series.json");
    when(verificationManagerService.getDataCollectionResponse(
             anyString(), anyString(), anyString(), any(DataCollectionRequest.class)))
        .thenReturn(responseObject);

    assertThatThrownBy(()
                           -> cloudWatchService.fetchSampleData(builderFactory.getProjectParams(), connectorIdentifier,
                               generateUuid(), generateUuid(), generateUuid(), generateUuid(), generateUuid()))
        .hasMessageContaining("Single time-series expected.");
  }

  private String readResource(String fileName) throws IOException {
    return Resources.toString(CloudWatchServiceImplTest.class.getResource("/cloudwatch/" + fileName), Charsets.UTF_8);
  }
}
