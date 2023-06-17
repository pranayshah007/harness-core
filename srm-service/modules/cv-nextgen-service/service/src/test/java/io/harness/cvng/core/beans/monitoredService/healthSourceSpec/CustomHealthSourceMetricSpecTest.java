/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.beans.monitoredService.healthSourceSpec;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.ANJAN;
import static io.harness.rule.OwnerRule.DHRUVX;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.harness.CvNextGenTestBase;
import io.harness.category.element.UnitTests;
import io.harness.cvng.BuilderFactory;
import io.harness.cvng.beans.CVMonitoringCategory;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.core.beans.CustomHealthMetricDefinition;
import io.harness.cvng.core.beans.CustomHealthRequestDefinition;
import io.harness.cvng.core.beans.HealthSourceMetricDefinition;
import io.harness.cvng.core.beans.HealthSourceQueryType;
import io.harness.cvng.core.beans.RiskProfile;
import io.harness.cvng.core.beans.monitoredService.HealthSource;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.CustomHealthSourceMetricSpec;
import io.harness.cvng.core.beans.monitoredService.healthSouceSpec.MetricResponseMapping;
import io.harness.cvng.core.entities.AnalysisInfo;
import io.harness.cvng.core.entities.CVConfig;
import io.harness.cvng.core.entities.CustomHealthMetricCVConfig;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.MetricPackService;
import io.harness.delegate.beans.connector.customhealthconnector.CustomHealthMethod;
import io.harness.exception.DataFormatException;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class CustomHealthSourceMetricSpecTest extends CvNextGenTestBase {
  List<CustomHealthMetricDefinition> customHealthSourceSpecs;
  CustomHealthSourceMetricSpec customHealthSourceSpec;
  String groupName = "group_1";
  String metricName1 = "metric_1";

  String metricName2 = "metric_2";
  String identifier = "1234_identifier";
  String accountId;
  String orgIdentifier;
  String projectIdentifier;
  String environmentRef;
  String serviceRef;
  String name = "customhealthsource";
  BuilderFactory builderFactory;
  String monitoredServiceIdentifier = generateUuid();
  MetricResponseMapping responseMapping;
  @Inject MetricPackService metricPackService;

  private static final String INVALID_DATA_PATH_ERROR_MESSAGE = "Json paths do not match.";
  private static final String INVALID_CHARACTER_ERROR_MESSAGE = "Incorrect json path for %s";
  private static final String NO_ARRAY_FOUND_ERROR_MESSAGE = "No array found in json path for %s.";
  private static final String MISSING_KEY_ERROR_MESSAGE = "Can not derive relative path. Missing key.";
  private static final String EMPTY_JSON_PATH = "Json path for %s is empty or null.";
  private static final String PATH_TYPE_METRIC_VALUE = "metric value";
  private static final String PATH_TYPE_TIMESTAMP = "timestamp";
  private static final String PATH_TYPE_SERVICE_INSTANCE = "service instance";

  @Before
  public void setup() {
    builderFactory = BuilderFactory.getDefault();
    accountId = builderFactory.getContext().getAccountId();
    orgIdentifier = builderFactory.getContext().getOrgIdentifier();
    projectIdentifier = builderFactory.getContext().getProjectIdentifier();
    environmentRef = builderFactory.getContext().getEnvIdentifier();
    serviceRef = builderFactory.getContext().getServiceIdentifier();
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[*].metrics.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forCreate() {
    CustomHealthMetricCVConfig existingCVConfig = builderFactory.customHealthMetricCVConfigBuilder(
        Arrays.asList("metric_3", "metric_4"), true, false, true, responseMapping, "group",
        HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    MetricPack.MetricDefinition metricDefinition1 =
        MetricPack.MetricDefinition.builder().name("metric1").thresholds(new ArrayList<>()).included(true).build();
    List<CustomHealthMetricCVConfig> addedConfigs = new ArrayList<>();
    HashSet<MetricPack.MetricDefinition> hashSet = new HashSet<>();
    hashSet.add(metricDefinition1);

    CustomHealthMetricCVConfig metricCVConfig = builderFactory.customHealthMetricCVConfigBuilder(
        Arrays.asList(metricName1), true, false, false, responseMapping, groupName, HealthSourceQueryType.HOST_BASED,
        CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);
    metricCVConfig.setMetricPack(MetricPack.builder()
                                     .category(CVMonitoringCategory.PERFORMANCE)
                                     .dataSourceType(DataSourceType.CUSTOM_HEALTH_METRIC)
                                     .accountId(accountId)
                                     .projectIdentifier(projectIdentifier)
                                     .identifier("Performance")
                                     .metrics(hashSet)
                                     .build());
    addedConfigs.add(metricCVConfig);

    compareMetricDefinition(((CustomHealthMetricCVConfig) result.getAdded().get(0)).getMetricInfos().get(0),
        addedConfigs.get(0).getMetricInfos().get(0));
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forDelete() {
    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition2 =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .metricName("metric_2")
            .sli(AnalysisInfo.SLI.builder().enabled(true).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(false).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(true).build())
            .metricResponseMapping(responseMapping)
            .requestDefinition(CustomHealthRequestDefinition.builder().method(CustomHealthMethod.POST).build())

            .build();

    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList(metricName1), false, true, true, responseMapping,
            groupName, HealthSourceQueryType.SERVICE_BASED, CustomHealthMethod.GET, CVMonitoringCategory.ERRORS, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    List<CustomHealthMetricCVConfig> deletedConfigs = new ArrayList<>();
    deletedConfigs.add(
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList(metricName2), false, true, true, responseMapping,
            groupName, HealthSourceQueryType.SERVICE_BASED, CustomHealthMethod.GET, CVMonitoringCategory.ERRORS, null));

    assertThat(result.getDeleted()).isEqualTo(deletedConfigs);
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forUpdate() {
    CustomHealthMetricDefinition customHealthMetricDefinition = customHealthSourceSpec.getMetricDefinitions().get(0);
    customHealthMetricDefinition.setQueryType(HealthSourceQueryType.SERVICE_BASED);
    customHealthMetricDefinition.getRequestDefinition().setRequestBody("post body");
    customHealthMetricDefinition.getRequestDefinition().setMethod(CustomHealthMethod.POST);

    CustomHealthMetricCVConfig existingCVConfig = builderFactory.customHealthMetricCVConfigBuilder(
        Arrays.asList(metricName1), false, true, true, responseMapping, groupName, HealthSourceQueryType.SERVICE_BASED,
        CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    List<CustomHealthMetricCVConfig> updatedConfigs = new ArrayList<>();
    updatedConfigs.add(builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList(metricName1), false, true, false,
        responseMapping, groupName, HealthSourceQueryType.SERVICE_BASED, CustomHealthMethod.POST,
        CVMonitoringCategory.PERFORMANCE, "post body"));

    compareMetricDefinition(((CustomHealthMetricCVConfig) result.getUpdated().get(0)).getMetricInfos().get(0),
        updatedConfigs.get(0).getMetricInfos().get(0));
  }

  @Test
  @Owner(developers = ANJAN)
  @Category(UnitTests.class)
  public void testGetCVConfigs() {
    CustomHealthMetricDefinition customHealthMetricDefinition =
        CustomHealthMetricDefinition.builder()
            .queryType(HealthSourceQueryType.HOST_BASED)
            .requestDefinition(CustomHealthRequestDefinition
                                   .builder()

                                   .method(CustomHealthMethod.GET)
                                   .build())
            .metricName("metric_2")
            .metricResponseMapping(responseMapping)
            .identifier("2132_identifier")
            .analysis(
                HealthSourceMetricDefinition.AnalysisDTO.builder()
                    .liveMonitoring(
                        HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO.builder().enabled(false).build())
                    .deploymentVerification(HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
                                                .enabled(true)
                                                .build())
                    .build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().enabled(false).build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build())
            .build();

    CustomHealthMetricDefinition customHealthMetricDefinition2 =
        CustomHealthMetricDefinition.builder()
            .queryType(HealthSourceQueryType.HOST_BASED)
            .requestDefinition(CustomHealthRequestDefinition.builder().method(CustomHealthMethod.POST).build())

            .metricName("metric_3")
            .metricResponseMapping(responseMapping)
            .identifier("43534_identifier")
            .analysis(
                HealthSourceMetricDefinition.AnalysisDTO.builder()
                    .liveMonitoring(
                        HealthSourceMetricDefinition.AnalysisDTO.LiveMonitoringDTO.builder().enabled(false).build())
                    .deploymentVerification(HealthSourceMetricDefinition.AnalysisDTO.DeploymentVerificationDTO.builder()
                                                .enabled(true)
                                                .build())
                    .build())
            .sli(HealthSourceMetricDefinition.SLIDTO.builder().enabled(false).build())
            .riskProfile(RiskProfile.builder().category(CVMonitoringCategory.INFRASTRUCTURE).build())
            .build();

    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .metricName(metricName1)
            .metricResponseMapping(responseMapping)

            .requestDefinition(CustomHealthRequestDefinition.builder().method(CustomHealthMethod.GET).build())
            .sli(AnalysisInfo.SLI.builder().enabled(true).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(false).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(true).build())
            .build();

    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition2 =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .metricName("metric_2")
            .metricResponseMapping(responseMapping)
            .requestDefinition(CustomHealthRequestDefinition.builder()
                                   .method(CustomHealthMethod.GET)

                                   .build())
            .sli(AnalysisInfo.SLI.builder().enabled(true).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(false).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(true).build())
            .build();

    CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition metricDefinition3 =
        CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition.builder()
            .metricName("metric_3")
            .metricResponseMapping(responseMapping)
            .requestDefinition(CustomHealthRequestDefinition.builder().method(CustomHealthMethod.POST).build())
            .sli(AnalysisInfo.SLI.builder().enabled(true).build())
            .deploymentVerification(AnalysisInfo.DeploymentVerification.builder().enabled(false).build())
            .liveMonitoring(AnalysisInfo.LiveMonitoring.builder().enabled(true).build())
            .build();

    customHealthSourceSpec.getMetricDefinitions().add(customHealthMetricDefinition);
    customHealthSourceSpec.getMetricDefinitions().add(customHealthMetricDefinition2);

    CustomHealthMetricCVConfig singleMetricDefinition =
        CustomHealthMetricCVConfig.builder()
            .groupName(groupName)
            .metricDefinitions(new ArrayList<CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition>() {
              { add(metricDefinition); }
            })
            .orgIdentifier(orgIdentifier)
            .category(CVMonitoringCategory.INFRASTRUCTURE)
            .enabled(false)
            .queryType(HealthSourceQueryType.HOST_BASED)
            .queryType(HealthSourceQueryType.HOST_BASED)
            .isDemo(false)
            .projectIdentifier(projectIdentifier)
            .identifier(identifier)
            .monitoringSourceName("customhealthSource")
            .build();
    CustomHealthMetricCVConfig multipleMetricDefinitions =
        CustomHealthMetricCVConfig.builder()
            .groupName(groupName)
            .metricDefinitions(new ArrayList<CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition>() {
              {
                add(metricDefinition2);
                add(metricDefinition3);
              }
            })
            .queryType(HealthSourceQueryType.HOST_BASED)
            .orgIdentifier(orgIdentifier)
            .queryType(HealthSourceQueryType.HOST_BASED)
            .category(CVMonitoringCategory.INFRASTRUCTURE)
            .enabled(false)
            .isDemo(false)
            .projectIdentifier(projectIdentifier)
            .identifier(identifier)
            .monitoringSourceName("customhealthSource")
            .build();
    List<CustomHealthMetricCVConfig> configs =
        customHealthSourceSpec
            .getCVConfigs(accountId, orgIdentifier, projectIdentifier, environmentRef, serviceRef,
                monitoredServiceIdentifier, identifier, name)
            .values()
            .stream()
            .collect(Collectors.toList());
    if (configs.get(0).getMetricInfos().size() == 2) {
      assertThat(configs.get(0).getMetricInfos()).isEqualTo(multipleMetricDefinitions.getMetricInfos());
      assertThat(configs.get(1).getMetricInfos()).isEqualTo(singleMetricDefinition.getMetricInfos());
    } else {
      assertThat(configs.get(0).getMetricInfos()).isEqualTo(singleMetricDefinition.getMetricInfos());
      assertThat(configs.get(1).getMetricInfos()).isEqualTo(multipleMetricDefinitions.getMetricInfos());
    }
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_extraArrayInMetricValuePath() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue.[*].";
    String timestampValueJSONPath = "$.[*].metrics.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(INVALID_CHARACTER_ERROR_MESSAGE, PATH_TYPE_METRIC_VALUE));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_extraArrayInTimestampValuePath() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[*].metrics.[*].metricTsValue.[*].";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(INVALID_CHARACTER_ERROR_MESSAGE, PATH_TYPE_TIMESTAMP));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_extraArrayInServiceInstanceValuePath() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[*].metrics.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath.[*].";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(INVALID_CHARACTER_ERROR_MESSAGE, PATH_TYPE_SERVICE_INSTANCE));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_inequalMetricListPath() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[*].metric.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(DataFormatException.class)
        .hasMessage(INVALID_DATA_PATH_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_inequalServiceInstanceListPath() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$..[*].metrics.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(DataFormatException.class)
        .hasMessage(INVALID_DATA_PATH_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_invalidMetricValuePath() {
    String metricValueJSONPath = "$.[*].metric.[].metricValue";
    String timestampValueJSONPath = "$.[*].metric.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(INVALID_CHARACTER_ERROR_MESSAGE, PATH_TYPE_METRIC_VALUE));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_invalidTimestampValuePath() {
    String metricValueJSONPath = "$.[*].metric.[*].metricValue";
    String timestampValueJSONPath = "$.[].metric.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(INVALID_CHARACTER_ERROR_MESSAGE, PATH_TYPE_TIMESTAMP));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_invalidServiceInstanceValuePath() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[*].metric.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(NO_ARRAY_FOUND_ERROR_MESSAGE, PATH_TYPE_SERVICE_INSTANCE));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_invalidTimestampPath() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[].metric.[].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(NO_ARRAY_FOUND_ERROR_MESSAGE, PATH_TYPE_TIMESTAMP));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_invalidMetricListPath() {
    String metricValueJSONPath = "$.[].metrics.[].metricValue";
    String timestampValueJSONPath = "$.[*].metric.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(NO_ARRAY_FOUND_ERROR_MESSAGE, PATH_TYPE_METRIC_VALUE));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_missingServiceInstanceListPath() {
    String metricValueJSONPath = ".[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[*].metrics.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(DataFormatException.class)
        .hasMessage(MISSING_KEY_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_invalidJsonPath_missingMetricListPath() {
    String metricValueJSONPath = "$.[*]..[*].metricValue";
    String timestampValueJSONPath = "$.[*].metric.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(DataFormatException.class)
        .hasMessage(MISSING_KEY_ERROR_MESSAGE);
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_missingServiceInstancePath() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[*].metrics.[*].metricTsValue";
    String serviceInstanceValueJSONPath = null;
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(EMPTY_JSON_PATH, PATH_TYPE_SERVICE_INSTANCE));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_missingMetricValuePath() {
    String metricValueJSONPath = null;
    String timestampValueJSONPath = "$.[*].metrics.[*].metricTsValue";
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(EMPTY_JSON_PATH, PATH_TYPE_METRIC_VALUE));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_missingTimestampPath() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricTsValue";
    String timestampValueJSONPath = null;
    String serviceInstanceValueJSONPath = "$.[*].metricPath";
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.HOST_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig =
        builderFactory.customHealthMetricCVConfigBuilder(Arrays.asList("metric_3"), true, false, true, responseMapping,
            "group", HealthSourceQueryType.HOST_BASED, CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    assertThatThrownBy(()
                           -> customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
                               projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier,
                               "1234234_iden", "healthsource", existingCVConfigs, metricPackService))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(String.format(EMPTY_JSON_PATH, PATH_TYPE_TIMESTAMP));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_forCreate_forService() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[*].metrics.[*].metricTsValue";
    String serviceInstanceValueJSONPath = null;
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1), identifier,
        HealthSourceQueryType.SERVICE_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig = builderFactory.customHealthMetricCVConfigBuilder(
        Arrays.asList("metric_4"), true, false, true, responseMapping, "group", HealthSourceQueryType.SERVICE_BASED,
        CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    MetricPack.MetricDefinition metricDefinition1 =
        MetricPack.MetricDefinition.builder().name("metric5").thresholds(new ArrayList<>()).included(true).build();
    List<CustomHealthMetricCVConfig> addedConfigs = new ArrayList<>();
    HashSet<MetricPack.MetricDefinition> hashSet = new HashSet<>();
    hashSet.add(metricDefinition1);

    CustomHealthMetricCVConfig metricCVConfig = builderFactory.customHealthMetricCVConfigBuilder(
        Arrays.asList(metricName1), true, false, false, responseMapping, groupName, HealthSourceQueryType.SERVICE_BASED,
        CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);
    metricCVConfig.setMetricPack(MetricPack.builder()
                                     .category(CVMonitoringCategory.PERFORMANCE)
                                     .dataSourceType(DataSourceType.CUSTOM_HEALTH_METRIC)
                                     .accountId(accountId)
                                     .projectIdentifier(projectIdentifier)
                                     .identifier("Performance")
                                     .metrics(hashSet)
                                     .build());
    addedConfigs.add(metricCVConfig);

    compareMetricDefinition(((CustomHealthMetricCVConfig) result.getAdded().get(0)).getMetricInfos().get(0),
        addedConfigs.get(0).getMetricInfos().get(0));
  }

  @Test
  @Owner(developers = DHRUVX)
  @Category(UnitTests.class)
  public void testGetCVConfigUpdateResult_multipleMetricsInSameGroup() {
    String metricValueJSONPath = "$.[*].metrics.[*].metricValue";
    String timestampValueJSONPath = "$.[*].metrics.[*].metricTsValue";
    String serviceInstanceValueJSONPath = null;
    responseMapping = MetricResponseMapping.builder()
                          .metricValueJsonPath(metricValueJSONPath)
                          .timestampJsonPath(timestampValueJSONPath)
                          .serviceInstanceJsonPath(serviceInstanceValueJSONPath)
                          .build();
    customHealthSourceSpec = builderFactory.customHealthMetricSourceSpecBuilder(metricValueJSONPath,
        timestampValueJSONPath, serviceInstanceValueJSONPath, groupName, Arrays.asList(metricName1, metricName2),
        identifier, HealthSourceQueryType.SERVICE_BASED, CVMonitoringCategory.PERFORMANCE, true, false, false);
    customHealthSourceSpecs = customHealthSourceSpec.getMetricDefinitions();
    CustomHealthMetricCVConfig existingCVConfig = builderFactory.customHealthMetricCVConfigBuilder(
        Arrays.asList("metric_4"), true, false, true, responseMapping, "group", HealthSourceQueryType.SERVICE_BASED,
        CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);

    List<CVConfig> existingCVConfigs = new ArrayList<>();
    existingCVConfigs.add(existingCVConfig);

    HealthSource.CVConfigUpdateResult result = customHealthSourceSpec.getCVConfigUpdateResult(accountId, orgIdentifier,
        projectIdentifier, environmentRef, serviceRef, monitoredServiceIdentifier, "1234234_iden", "healthsource",
        existingCVConfigs, metricPackService);

    MetricPack.MetricDefinition metricDefinition1 =
        MetricPack.MetricDefinition.builder().name("metric5").thresholds(new ArrayList<>()).included(true).build();
    List<CustomHealthMetricCVConfig> addedConfigs = new ArrayList<>();
    HashSet<MetricPack.MetricDefinition> hashSet = new HashSet<>();
    hashSet.add(metricDefinition1);

    CustomHealthMetricCVConfig metricCVConfig = builderFactory.customHealthMetricCVConfigBuilder(
        Arrays.asList(metricName1), true, false, false, responseMapping, groupName, HealthSourceQueryType.SERVICE_BASED,
        CustomHealthMethod.GET, CVMonitoringCategory.PERFORMANCE, null);
    metricCVConfig.setMetricPack(MetricPack.builder()
                                     .category(CVMonitoringCategory.PERFORMANCE)
                                     .dataSourceType(DataSourceType.CUSTOM_HEALTH_METRIC)
                                     .accountId(accountId)
                                     .projectIdentifier(projectIdentifier)
                                     .identifier("Performance")
                                     .metrics(hashSet)
                                     .build());
    addedConfigs.add(metricCVConfig);

    compareMetricDefinition(((CustomHealthMetricCVConfig) result.getAdded().get(0)).getMetricInfos().get(0),
        addedConfigs.get(0).getMetricInfos().get(0));
    assertThat(((CustomHealthMetricCVConfig) result.getAdded().get(0)).getMetricPack().getMetrics().size())
        .isEqualTo(2);
    List<String> metricNames = ((CustomHealthMetricCVConfig) result.getAdded().get(0))
                                   .getMetricPack()
                                   .getMetrics()
                                   .stream()
                                   .map(MetricPack.MetricDefinition::getName)
                                   .collect(Collectors.toList());
    assertThat(metricNames).contains(metricName1, metricName2);
  }

  private void compareMetricDefinition(CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition def1,
      CustomHealthMetricCVConfig.CustomHealthCVConfigMetricDefinition def2) {
    assertThat(def1.getMetricType()).isEqualTo(def2.getMetricType());
    assertThat(def1.getRequestDefinition()).isEqualTo(def2.getRequestDefinition());
    assertThat(def1.getMetricName()).isEqualTo(def2.getMetricName());
    assertThat(def1.getSli()).isEqualTo(def2.getSli());
    assertThat(def1.getMetricResponseMapping().getMetricValueJsonPath())
        .isEqualTo(def2.getMetricResponseMapping().getMetricValueJsonPath());
    assertThat(def1.getMetricResponseMapping().getTimestampJsonPath())
        .isEqualTo(def2.getMetricResponseMapping().getTimestampJsonPath());
    assertThat(def1.getMetricResponseMapping().getServiceInstanceJsonPath())
        .isEqualTo(def2.getMetricResponseMapping().getServiceInstanceJsonPath());
  }
}
