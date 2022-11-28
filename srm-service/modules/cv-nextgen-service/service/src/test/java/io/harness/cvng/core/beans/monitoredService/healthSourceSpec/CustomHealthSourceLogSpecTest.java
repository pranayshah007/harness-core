/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANJAN;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.customhealth.TimestampInfo;
import io.harness.cvng.core.beans.CustomHealthLogDefinition;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CustomHealthSourceLogSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CustomHealthLogCVConfig;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthSourceLogSpecTest extends CvNextGenTestBase {
  List<CustomHealthLogDefinition> customHealthLogDefinitions;
  CustomHealthSourceLogSpec customHealthSourceSpec;
  String queryName = "errorQuery";
  String timestampValueJSONPath = "json.path.to.timestampValue";
  String queryValueJSONPath = "json.path.to.message";
  String identifier = "1234_identifier";
  String urlPath = "http://urlPath.com";
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String environmentRef;
  String serviceRef;
  String name = "customhealthlogsource";
  BuilderFactory builderFactory;
  String monitoredServiceIdentifier = generateUuid();
  MetricResponseMapping responseMapping;
  @Inject MetricPackService metricPackService;

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    environmentRef = builderFactory.getContext().getEnvIdentifier();
    serviceRef = builderFactory.getContext().getServiceIdentifier();
    customHealthSourceSpec =
        builderFactory.customHealthLogSourceSpecBuilder(queryName, queryValueJSONPath, urlPath, timestampValueJSONPath);
    customHealthLogDefinitions = customHealthSourceSpec.getLogDefinitions();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forCreate() {
    CustomHealthLogCVConfig existingCVConfigURL = builderFactory.customHealthLogCVConfigBuilder(
        "sdfsdf", "sdf", "error query", "existing_query1", "fsdfd", null, CustomHealthMethod.GET);
    CustomHealthLogCVConfig existingCVConfigMethod = builderFactory.customHealthLogCVConfigBuilder(
        "sdfsdf", "sdf", "another query", "existing_query2", "fsdfd", "{}", CustomHealthMethod.POST);

    CustomHealthLogCVConfig addedCVConfig = builderFactory.customHealthLogCVConfigBuilder(
        queryValueJSONPath, timestampValueJSONPath, "", queryName, urlPath, null, CustomHealthMethod.GET);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfigURL);
    existingCVConfigs.add(existingCVConfigMethod);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    assertThat(result.getAdded().get(0).toString()).isEqualTo(addedCVConfig.toString());
    assertThat(result.getAdded().size()).isEqualTo(1);
    assertThat(result.getDeleted().size()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forUpdate() {
    CustomHealthLogDefinition addedLogDefinitionURL =
        CustomHealthLogDefinition.builder()
            .logMessageJsonPath("sdfsdf")
            .timestampJsonPath("sdf")
            .queryName(queryName)
            .requestDefinition(CustomHealthRequestDefinition.builder()
                                   .method(CustomHealthMethod.GET)
                                   .urlPath("https://url.com?start-time=start_time&end-time=end_time")
                                   .endTimeInfo(TimestampInfo.builder()
                                                    .placeholder("end_time")
                                                    .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                                    .build())
                                   .startTimeInfo(TimestampInfo.builder()
                                                      .placeholder("start_time")
                                                      .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                                      .build())
                                   .build())
            .build();

    CustomHealthLogCVConfig customHealthLogCVConfig = builderFactory.customHealthLogCVConfigBuilder("sdfsdf", "sdf", "",
        queryName, addedLogDefinitionURL.getRequestDefinition().getUrlPath(), null, CustomHealthMethod.GET);
    customHealthLogCVConfig.getRequestDefinition().setEndTimeInfo(
        TimestampInfo.builder()
            .placeholder("end_time")
            .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
            .build());
    customHealthLogCVConfig.getRequestDefinition().setStartTimeInfo(
        TimestampInfo.builder()
            .placeholder("start_time")
            .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
            .build());

    List<CustomHealthLogDefinition> logDefinitions = new ArrayList<>();
    logDefinitions.add(addedLogDefinitionURL);
    customHealthSourceSpec.setLogDefinitions(logDefinitions);

    CustomHealthLogCVConfig existingCVConfig = builderFactory.customHealthLogCVConfigBuilder(
        "sdfsdf", "sdf", "error query", queryName, "fsdfd", "{}", CustomHealthMethod.POST);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    assertThat(result.getUpdated().get(0).toString()).isEqualTo(customHealthLogCVConfig.toString());
    assertThat(result.getAdded().size()).isEqualTo(0);
    assertThat(result.getUpdated().size()).isEqualTo(1);
    assertThat(result.getDeleted().size()).isEqualTo(0);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forDelete() {
    CustomHealthLogDefinition addedLogDefinitionURL =
        CustomHealthLogDefinition.builder()
            .logMessageJsonPath("sdfsdf")
            .timestampJsonPath("sdf")
            .queryName("vbccn")
            .requestDefinition(CustomHealthRequestDefinition.builder()
                                   .method(CustomHealthMethod.GET)
                                   .urlPath("https://url.com?start-time=start_time&end-time=end_time")
                                   .endTimeInfo(TimestampInfo.builder()
                                                    .placeholder("end_time")
                                                    .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                                    .build())
                                   .startTimeInfo(TimestampInfo.builder()
                                                      .placeholder("start_time")
                                                      .timestampFormat(TimestampInfo.TimestampFormat.MILLISECONDS)
                                                      .build())
                                   .build())
            .build();

    customHealthSourceSpec.getLogDefinitions().add(addedLogDefinitionURL);

    CustomHealthLogCVConfig existingCVConfig = builderFactory.customHealthLogCVConfigBuilder(
        "sdfsdf", "sdf", "error query", "uioiuoo9", "fsdfd", "{}", CustomHealthMethod.POST);
    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    assertThat(result.getDeleted().get(0).toString()).isEqualTo(existingCVConfig.toString());
    assertThat(result.getAdded().size()).isEqualTo(2);
    assertThat(result.getUpdated().size()).isEqualTo(0);
    assertThat(result.getDeleted().size()).isEqualTo(1);
  }
}
