/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.tas;

import static io.harness.cdng.manifest.ManifestConfigType.TAS_AUTOSCALER;
import static io.harness.cdng.manifest.ManifestConfigType.TAS_MANIFEST;
import static io.harness.cdng.manifest.ManifestConfigType.TAS_VARS;
import static io.harness.rule.OwnerRule.RISHABH;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.CDNGTestBase;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.common.beans.SetupAbstractionKeys;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.TasManifestOutcome;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.delegate.task.manifests.request.CustomManifestFetchConfig;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.k8s.K8sCommandUnitConstants;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.manifest.CustomManifestSource;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfCliVersionNG;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.rule.Owner;
import io.harness.steps.StepHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class TasStepHelperTest extends CDNGTestBase {
  @Mock private CDStepHelper cdStepHelper;
  @Mock private StepHelper stepHelper;
  @Mock private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Mock private TasBasicAppSetupStep tasBasicAppSetupStep;
  @Mock private TasRollingDeployStep tasRollingDeployStep;
  @InjectMocks private TasStepHelper tasStepHelper;

  public static String MANIFEST_YML = "applications:\n"
      + "- name: test-tas\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances: ((INSTANCES))\n";
  public static String COMMAND_SCRIPT = "## Performing cf login\n"
      + "\n"
      + "\n"
      + "cf login\n"
      + "\n"
      + "## Get apps\n"
      + "cf apps";
  public static String COMMAND_SCRIPT_WITHOUT_COMMENTS = "cf login\n"
      + "cf apps";
  public static String MANIFEST_YML_WITH_ROUTES = "applications:\n"
      + "- name: test-tas\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances: ((INSTANCES))\n"
      + "  routes:\n"
      + "    - route: route1\n"
      + "    - route: route2";
  public static String NO_ROUTE_TRUE_MANIFEST_YML = "applications:\n"
      + "- name: test-tas\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances: ((INSTANCES))\n"
      + "  no-route: true\n";
  public static String NO_ROUTE_FALSE_MANIFEST_YML = "applications:\n"
      + "- name: test-tas\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances: ((INSTANCES))\n"
      + "  no-route: false\n";
  public static String MANIFEST_YML_WITH_VARS_NAME = "applications:\n"
      + "- name: ((PCF_APP_NAME))\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances: ((INSTANCES))\n";
  private final String INVALID_MANIFEST_YML = "applications:\n"
      + "  - name: ((PCF_APP_NAME))\n"
      + "  memory: ((PCF_APP_MEMORY))\n"
      + "  instances: 2\n"
      + "  random-route: true\n";
  private final String NO_YML = "";
  public static String VARS_YML_1 = "MY: order\n"
      + "PCF_APP_NAME: test-tas\n"
      + "INSTANCES: 3\n"
      + "ROUTE: route1";
  public static String VARS_YML_2 = "env: prod\n"
      + "test: yes";
  public static String AUTOSCALAR_YML = "---\n"
      + "instance_limits:\n"
      + "  min: 1\n"
      + "  max: 2\n"
      + "rules:\n"
      + "- rule_type: \"http_latency\"\n"
      + "  rule_sub_type: \"avg_99th\"\n"
      + "  threshold:\n"
      + "    min: 100\n"
      + "    max: 200\n"
      + "scheduled_limit_changes:\n"
      + "- recurrence: 10\n"
      + "  executes_at: \"2032-01-01T00:00:00Z\"\n"
      + "  instance_limits:\n"
      + "    min: 1\n"
      + "    max: 2";
  private final String NOT_VAR = "APP_NAME: ${app.name}__${service.name}__${env.name}\n"
      + "APP_MEMORY: 750M\n"
      + "INSTANCES";

  private Ambiance getAmbiance() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put(SetupAbstractionKeys.accountId, "account");
    setupAbstractions.put(SetupAbstractionKeys.orgIdentifier, "org");
    setupAbstractions.put(SetupAbstractionKeys.projectIdentifier, "project");

    return Ambiance.newBuilder()
        .putAllSetupAbstractions(setupAbstractions)
        .setStageExecutionId("stageExecutionId")
        .build();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetManifestType() {
    LogCallback logCallback = Mockito.mock(LogCallback.class);
    assertThat(tasStepHelper.getManifestType(MANIFEST_YML, null, logCallback)).isEqualTo(TAS_MANIFEST);
    assertThat(tasStepHelper.getManifestType(AUTOSCALAR_YML, null, logCallback)).isEqualTo(TAS_AUTOSCALER);
    assertThat(tasStepHelper.getManifestType(VARS_YML_1, null, logCallback)).isEqualTo(TAS_VARS);
    assertThat(tasStepHelper.getManifestType(NOT_VAR, null, logCallback)).isNull();
    assertThat(tasStepHelper.getManifestType(INVALID_MANIFEST_YML, null, logCallback)).isNull();
    assertThat(tasStepHelper.getManifestType(NO_YML, null, logCallback)).isNull();
    Mockito.verify(logCallback, Mockito.times(6)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFetchTasApplicationName() {
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder().manifestYml(MANIFEST_YML).build();
    assertThat(tasStepHelper.fetchTasApplicationName(tasManifestsPackage)).isEqualTo("test-tas");
    TasManifestsPackage tasManifestsPackageWithoutVars =
        TasManifestsPackage.builder().manifestYml(MANIFEST_YML_WITH_VARS_NAME).build();
    assertThatThrownBy(() -> tasStepHelper.fetchTasApplicationName(tasManifestsPackageWithoutVars))
        .hasMessage("No Valid Variable file Found, please verify var file is present and has valid structure");
    TasManifestsPackage tasManifestsPackageWithVars = TasManifestsPackage.builder()
                                                          .manifestYml(MANIFEST_YML_WITH_VARS_NAME)
                                                          .variableYmls(List.of(VARS_YML_1))
                                                          .build();
    assertThat(tasStepHelper.fetchTasApplicationName(tasManifestsPackageWithVars)).isEqualTo("test-tas");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testShouldExecuteStoreFetchCustomRemote() {
    TasManifestOutcome tasManifestOutcome =
        TasManifestOutcome.builder().store(CustomRemoteStoreConfig.builder().build()).build();
    TasStepPassThroughData tasStepPassThroughData = TasStepPassThroughData.builder().build();
    tasStepHelper.shouldExecuteStoreFetch(tasStepPassThroughData, tasManifestOutcome);
    assertThat(tasStepPassThroughData.getShouldExecuteCustomFetch()).isTrue();
    assertThat(tasStepPassThroughData.getShouldExecuteGitStoreFetch()).isFalse();
    assertThat(tasStepPassThroughData.getShouldExecuteHarnessStoreFetch()).isFalse();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testShouldExecuteStoreFetchBitBucket() {
    TasManifestOutcome tasManifestOutcome =
        TasManifestOutcome.builder().store(BitbucketStore.builder().build()).build();
    TasStepPassThroughData tasStepPassThroughData = TasStepPassThroughData.builder().build();
    tasStepHelper.shouldExecuteStoreFetch(tasStepPassThroughData, tasManifestOutcome);
    assertThat(tasStepPassThroughData.getShouldExecuteCustomFetch()).isFalse();
    assertThat(tasStepPassThroughData.getShouldExecuteGitStoreFetch()).isTrue();
    assertThat(tasStepPassThroughData.getShouldExecuteHarnessStoreFetch()).isFalse();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testShouldExecuteStoreFetchGitLab() {
    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().store(GitLabStore.builder().build()).build();
    TasStepPassThroughData tasStepPassThroughData = TasStepPassThroughData.builder().build();
    tasStepHelper.shouldExecuteStoreFetch(tasStepPassThroughData, tasManifestOutcome);
    assertThat(tasStepPassThroughData.getShouldExecuteCustomFetch()).isFalse();
    assertThat(tasStepPassThroughData.getShouldExecuteGitStoreFetch()).isTrue();
    assertThat(tasStepPassThroughData.getShouldExecuteHarnessStoreFetch()).isFalse();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testShouldExecuteStoreFetchGitHub() {
    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().store(GithubStore.builder().build()).build();
    TasStepPassThroughData tasStepPassThroughData = TasStepPassThroughData.builder().build();
    tasStepHelper.shouldExecuteStoreFetch(tasStepPassThroughData, tasManifestOutcome);
    assertThat(tasStepPassThroughData.getShouldExecuteCustomFetch()).isFalse();
    assertThat(tasStepPassThroughData.getShouldExecuteGitStoreFetch()).isTrue();
    assertThat(tasStepPassThroughData.getShouldExecuteHarnessStoreFetch()).isFalse();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testShouldExecuteStoreFetchGit() {
    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().store(GitStore.builder().build()).build();
    TasStepPassThroughData tasStepPassThroughData = TasStepPassThroughData.builder().build();
    tasStepHelper.shouldExecuteStoreFetch(tasStepPassThroughData, tasManifestOutcome);
    assertThat(tasStepPassThroughData.getShouldExecuteCustomFetch()).isFalse();
    assertThat(tasStepPassThroughData.getShouldExecuteGitStoreFetch()).isTrue();
    assertThat(tasStepPassThroughData.getShouldExecuteHarnessStoreFetch()).isFalse();
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testShouldExecuteStoreFetchHarness() {
    TasManifestOutcome tasManifestOutcome = TasManifestOutcome.builder().store(HarnessStore.builder().build()).build();
    TasStepPassThroughData tasStepPassThroughData = TasStepPassThroughData.builder().build();
    tasStepHelper.shouldExecuteStoreFetch(tasStepPassThroughData, tasManifestOutcome);
    assertThat(tasStepPassThroughData.getShouldExecuteCustomFetch()).isFalse();
    assertThat(tasStepPassThroughData.getShouldExecuteGitStoreFetch()).isFalse();
    assertThat(tasStepPassThroughData.getShouldExecuteHarnessStoreFetch()).isTrue();
    assertThatThrownBy(()
                           -> tasStepHelper.shouldExecuteStoreFetch(
                               tasStepPassThroughData, TasManifestOutcome.builder().identifier("test").build()))
        .hasMessage("Store is null for manifest: test");
    assertThatThrownBy(
        ()
            -> tasStepHelper.shouldExecuteStoreFetch(tasStepPassThroughData,
                TasManifestOutcome.builder().identifier("test").store(OciHelmChartConfig.builder().build()).build()))
        .hasMessage("Manifest store type: OciHelmChart is not supported yet");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetCommandUnitsForTanzuCommand() {
    assertThat(tasStepHelper.getCommandUnitsForTanzuCommand(TasStepPassThroughData.builder().build()))
        .isEqualTo(asList(CfCommandUnitConstants.FetchCommandScript, CfCommandUnitConstants.Pcfplugin,
            CfCommandUnitConstants.Wrapup));
    assertThat(tasStepHelper.getCommandUnitsForTanzuCommand(
                   TasStepPassThroughData.builder().shouldExecuteCustomFetch(true).build()))
        .isEqualTo(asList(CfCommandUnitConstants.FetchCommandScript, CfCommandUnitConstants.FetchCustomFiles,
            CfCommandUnitConstants.Pcfplugin, CfCommandUnitConstants.Wrapup));
    assertThat(tasStepHelper.getCommandUnitsForTanzuCommand(
                   TasStepPassThroughData.builder().shouldExecuteGitStoreFetch(true).build()))
        .isEqualTo(asList(CfCommandUnitConstants.FetchCommandScript, K8sCommandUnitConstants.FetchFiles,
            CfCommandUnitConstants.Pcfplugin, CfCommandUnitConstants.Wrapup));
    assertThat(tasStepHelper.getCommandUnitsForTanzuCommand(
                   TasStepPassThroughData.builder().shouldExecuteHarnessStoreFetch(true).build()))
        .isEqualTo(asList(CfCommandUnitConstants.FetchCommandScript, CfCommandUnitConstants.FetchFiles,
            CfCommandUnitConstants.Pcfplugin, CfCommandUnitConstants.Wrapup));
    assertThat(tasStepHelper.getCommandUnitsForTanzuCommand(TasStepPassThroughData.builder()
                                                                .shouldExecuteCustomFetch(true)
                                                                .shouldExecuteGitStoreFetch(true)
                                                                .shouldExecuteHarnessStoreFetch(true)
                                                                .build()))
        .isEqualTo(asList(CfCommandUnitConstants.FetchCommandScript, CfCommandUnitConstants.FetchFiles,
            CfCommandUnitConstants.FetchCustomFiles, K8sCommandUnitConstants.FetchFiles,
            CfCommandUnitConstants.Pcfplugin, CfCommandUnitConstants.Wrapup));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetCommandUnits() {
    assertThat(tasStepHelper.getCommandUnits(tasBasicAppSetupStep, TasStepPassThroughData.builder().build()))
        .isEqualTo(asList(
            CfCommandUnitConstants.VerifyManifests, CfCommandUnitConstants.PcfSetup, CfCommandUnitConstants.Wrapup));
    assertThat(tasStepHelper.getCommandUnits(
                   tasBasicAppSetupStep, TasStepPassThroughData.builder().shouldExecuteCustomFetch(true).build()))
        .isEqualTo(asList(CfCommandUnitConstants.FetchCustomFiles, CfCommandUnitConstants.VerifyManifests,
            CfCommandUnitConstants.PcfSetup, CfCommandUnitConstants.Wrapup));
    assertThat(tasStepHelper.getCommandUnits(
                   tasBasicAppSetupStep, TasStepPassThroughData.builder().shouldExecuteGitStoreFetch(true).build()))
        .isEqualTo(asList(K8sCommandUnitConstants.FetchFiles, CfCommandUnitConstants.VerifyManifests,
            CfCommandUnitConstants.PcfSetup, CfCommandUnitConstants.Wrapup));
    assertThat(tasStepHelper.getCommandUnits(
                   tasBasicAppSetupStep, TasStepPassThroughData.builder().shouldExecuteHarnessStoreFetch(true).build()))
        .isEqualTo(asList(CfCommandUnitConstants.FetchFiles, CfCommandUnitConstants.VerifyManifests,
            CfCommandUnitConstants.PcfSetup, CfCommandUnitConstants.Wrapup));
    assertThat(tasStepHelper.getCommandUnits(tasBasicAppSetupStep,
                   TasStepPassThroughData.builder()
                       .shouldExecuteCustomFetch(true)
                       .shouldExecuteGitStoreFetch(true)
                       .shouldExecuteHarnessStoreFetch(true)
                       .build()))
        .isEqualTo(asList(CfCommandUnitConstants.FetchFiles, CfCommandUnitConstants.FetchCustomFiles,
            K8sCommandUnitConstants.FetchFiles, CfCommandUnitConstants.VerifyManifests, CfCommandUnitConstants.PcfSetup,
            CfCommandUnitConstants.Wrapup));
    assertThat(tasStepHelper.getCommandUnits(tasRollingDeployStep,
                   TasStepPassThroughData.builder()
                       .shouldExecuteCustomFetch(true)
                       .shouldExecuteGitStoreFetch(true)
                       .shouldExecuteHarnessStoreFetch(true)
                       .build()))
        .isEqualTo(asList(CfCommandUnitConstants.FetchFiles, CfCommandUnitConstants.FetchCustomFiles,
            K8sCommandUnitConstants.FetchFiles, CfCommandUnitConstants.VerifyManifests, CfCommandUnitConstants.Deploy,
            CfCommandUnitConstants.Wrapup));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testBuildCustomManifestFetchConfig() {
    assertThat(tasStepHelper.buildCustomManifestFetchConfig(
                   "id", true, false, asList("path1", "path2"), "script", "accountId"))
        .isEqualTo(CustomManifestFetchConfig.builder()
                       .key("id")
                       .required(true)
                       .defaultSource(false)
                       .customManifestSource(CustomManifestSource.builder()
                                                 .script("script")
                                                 .filePaths(asList("path1", "path2"))
                                                 .accountId("accountId")
                                                 .build())
                       .build());
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testCfCliVersionNGMapper() {
    assertThat(tasStepHelper.cfCliVersionNGMapper(CfCliVersionNG.V7)).isEqualTo(CfCliVersion.V7);
    assertThatThrownBy(() -> tasStepHelper.cfCliVersionNGMapper(null)).hasMessage("CF CLI Version can't be null");
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testGetRouteMaps() {
    assertThat(tasStepHelper.getRouteMaps(NO_ROUTE_TRUE_MANIFEST_YML, new ArrayList<>())).isEqualTo(emptyList());
    assertThat(tasStepHelper.getRouteMaps(NO_ROUTE_TRUE_MANIFEST_YML, asList("temp-route1", "temp-route2")))
        .isEqualTo(emptyList());
    assertThat(tasStepHelper.getRouteMaps(NO_ROUTE_FALSE_MANIFEST_YML, asList("temp-route1", "temp-route2")))
        .isEqualTo(asList("temp-route1", "temp-route2"));
    assertThat(tasStepHelper.getRouteMaps(NO_ROUTE_FALSE_MANIFEST_YML, new ArrayList<>())).isEqualTo(emptyList());
    assertThat(tasStepHelper.getRouteMaps(MANIFEST_YML, asList("temp-route1", "temp-route2")))
        .isEqualTo(asList("temp-route1", "temp-route2"));
    assertThat(tasStepHelper.getRouteMaps(MANIFEST_YML, new ArrayList<>())).isEqualTo(emptyList());
    assertThat(tasStepHelper.getRouteMaps(MANIFEST_YML_WITH_ROUTES, asList("temp-route1", "temp-route2")))
        .isEqualTo(asList("route1", "route2", "temp-route1", "temp-route2"));
    assertThat(tasStepHelper.getRouteMaps(MANIFEST_YML_WITH_ROUTES, new ArrayList<>()))
        .isEqualTo(asList("route1", "route2"));
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testFetchMaxCountFromManifest() {
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder().manifestYml(MANIFEST_YML).build();
    assertThatThrownBy(() -> tasStepHelper.fetchMaxCountFromManifest(tasManifestsPackage))
        .hasMessage("No Valid Variable file Found, please verify var file is present and has valid structure");

    tasManifestsPackage.setVariableYmls(List.of(VARS_YML_1));
    assertThat(tasStepHelper.fetchMaxCountFromManifest(tasManifestsPackage)).isEqualTo(3);

    tasManifestsPackage.setManifestYml(MANIFEST_YML.replace("((INSTANCES))", "2"));
    assertThat(tasStepHelper.fetchMaxCountFromManifest(tasManifestsPackage)).isEqualTo(2);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testRemoveCommentedLineFromScript() {
    assertThat(tasStepHelper.removeCommentedLineFromScript(COMMAND_SCRIPT)).isEqualTo(COMMAND_SCRIPT_WITHOUT_COMMENTS);
  }

  @Test
  @Owner(developers = RISHABH)
  @Category(UnitTests.class)
  public void testAddToPcfManifestPackageByType() {
    LogCallback logCallback = Mockito.mock(LogCallback.class);
    TasManifestsPackage tasManifestsPackage = TasManifestsPackage.builder().variableYmls(new ArrayList<>()).build();

    tasStepHelper.addToPcfManifestPackageByType(tasManifestsPackage, MANIFEST_YML, null, logCallback);
    assertThat(tasManifestsPackage.getManifestYml()).isEqualTo(MANIFEST_YML);
    assertThat(tasManifestsPackage.getAutoscalarManifestYml()).isNull();
    assertThat(tasManifestsPackage.getVariableYmls()).isEqualTo(emptyList());

    assertThatThrownBy(
        () -> tasStepHelper.addToPcfManifestPackageByType(tasManifestsPackage, MANIFEST_YML, null, logCallback))
        .hasMessage("Only one Tas Manifest Yml is supported");
    assertThat(tasManifestsPackage.getManifestYml()).isEqualTo(MANIFEST_YML);
    assertThat(tasManifestsPackage.getAutoscalarManifestYml()).isNull();
    assertThat(tasManifestsPackage.getVariableYmls()).isEqualTo(emptyList());

    tasStepHelper.addToPcfManifestPackageByType(tasManifestsPackage, AUTOSCALAR_YML, null, logCallback);
    assertThat(tasManifestsPackage.getManifestYml()).isEqualTo(MANIFEST_YML);
    assertThat(tasManifestsPackage.getAutoscalarManifestYml()).isEqualTo(AUTOSCALAR_YML);
    assertThat(tasManifestsPackage.getVariableYmls()).isEqualTo(emptyList());

    assertThatThrownBy(
        () -> tasStepHelper.addToPcfManifestPackageByType(tasManifestsPackage, AUTOSCALAR_YML, null, logCallback))
        .hasMessage("Only one AutoScalar Yml is supported");
    assertThat(tasManifestsPackage.getManifestYml()).isEqualTo(MANIFEST_YML);
    assertThat(tasManifestsPackage.getAutoscalarManifestYml()).isEqualTo(AUTOSCALAR_YML);
    assertThat(tasManifestsPackage.getVariableYmls()).isEqualTo(emptyList());

    tasStepHelper.addToPcfManifestPackageByType(tasManifestsPackage, VARS_YML_1, null, logCallback);
    assertThat(tasManifestsPackage.getManifestYml()).isEqualTo(MANIFEST_YML);
    assertThat(tasManifestsPackage.getAutoscalarManifestYml()).isEqualTo(AUTOSCALAR_YML);
    assertThat(tasManifestsPackage.getVariableYmls()).isEqualTo(List.of(VARS_YML_1));

    tasStepHelper.addToPcfManifestPackageByType(tasManifestsPackage, VARS_YML_2, null, logCallback);
    assertThat(tasManifestsPackage.getManifestYml()).isEqualTo(MANIFEST_YML);
    assertThat(tasManifestsPackage.getAutoscalarManifestYml()).isEqualTo(AUTOSCALAR_YML);
    assertThat(tasManifestsPackage.getVariableYmls()).isEqualTo(asList(VARS_YML_1, VARS_YML_2));

    tasStepHelper.addToPcfManifestPackageByType(tasManifestsPackage, NOT_VAR, null, logCallback);
    assertThat(tasManifestsPackage.getManifestYml()).isEqualTo(MANIFEST_YML);
    assertThat(tasManifestsPackage.getAutoscalarManifestYml()).isEqualTo(AUTOSCALAR_YML);
    assertThat(tasManifestsPackage.getVariableYmls()).isEqualTo(asList(VARS_YML_1, VARS_YML_2));
    Mockito.verify(logCallback, Mockito.times(2)).saveExecutionLog(anyString(), eq(LogLevel.WARN));
  }
}
