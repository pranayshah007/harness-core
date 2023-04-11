/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.helm;

import static io.harness.rule.OwnerRule.ACHYUTH;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.helm.HelmCommandData;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.logging.NoopExecutionCallback;
import io.harness.rule.Owner;

import software.wings.beans.dto.HelmChartSpecification;
import software.wings.delegatetasks.validation.capabilities.HelmCommandRequest;
import software.wings.helpers.ext.helm.request.HelmInstallCommandRequest;
import software.wings.helpers.ext.helm.request.HelmRollbackCommandRequest;
import software.wings.helpers.ext.k8s.request.K8sDelegateManifestConfig;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class HelmCommandDataMapperTest extends CategoryTest {
  List<String> yamlList = Arrays.asList("yaml-file-1", "yaml-file-2");
  LogCallback logCallback = new NoopExecutionCallback();
  Map<HelmSubCommandType, String> valueMap = new HashMap<>();

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testHelmCommandDataMapperHelmInstallCmdRequest() {
    logCallback.saveExecutionLog("saving-log", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    valueMap.put(HelmSubCommandType.INSTALL, "install");

    HelmCommandRequest helmCommandRequest = HelmInstallCommandRequest.builder()
                                                .kubeConfigLocation("kube-config")
                                                .helmVersion(HelmVersion.V2)
                                                .releaseName("best-release-ever")
                                                .sourceRepoConfig(K8sDelegateManifestConfig.builder().build())
                                                .variableOverridesYamlFiles(yamlList)
                                                .executionLogCallback(logCallback)
                                                .workingDir("working-dir")
                                                .commandFlags("--debug")
                                                .repoName("my-repo")
                                                .chartSpecification(HelmChartSpecification.builder()
                                                                        .chartName("coolest-chart")
                                                                        .chartUrl("harness.io")
                                                                        .chartVersion("0.1.0")
                                                                        .build())
                                                .helmCommandFlag(HelmCommandFlag.builder().valueMap(valueMap).build())
                                                .namespace("default")
                                                .prevReleaseVersion(1)
                                                .newReleaseVersion(2)
                                                .timeoutInMillis(120)
                                                .build();

    HelmCommandData helmCommandData = HelmCommandDataMapper.getHelmCommandData(helmCommandRequest);

    assertThat(helmCommandData.getKubeConfigLocation()).isEqualTo("kube-config");
    assertThat(helmCommandData.getReleaseName()).isEqualTo("best-release-ever");
    assertThat(helmCommandData.getYamlFiles().get(0)).isEqualTo("yaml-file-1");
    assertThat(helmCommandData.getHelmVersion()).isEqualTo(HelmVersion.V2);
    assertThat(helmCommandData.getChartName()).isEqualTo("coolest-chart");
    assertThat(helmCommandData.getChartUrl()).isEqualTo("harness.io");
    assertThat(helmCommandData.getRepoName()).isEqualTo("my-repo");
    assertThat(helmCommandData.getNamespace()).isEqualTo("default");
    assertThat(helmCommandData.getValueMap().get(HelmSubCommandType.INSTALL)).isEqualTo("install");
    assertThat(helmCommandData.getLogCallback()).isEqualTo(logCallback);
  }

  @Test
  @Owner(developers = ACHYUTH)
  @Category(UnitTests.class)
  public void testHelmCommandDataMapperHelmRollbackCmdRequest() {
    logCallback.saveExecutionLog("saving-log", LogLevel.INFO, CommandExecutionStatus.SUCCESS);
    valueMap.put(HelmSubCommandType.ROLLBACK, "rollback");

    HelmCommandRequest helmCommandRequest = HelmRollbackCommandRequest.builder()
                                                .kubeConfigLocation("kube-config")
                                                .helmVersion(HelmVersion.V2)
                                                .releaseName("best-release-ever")
                                                .sourceRepoConfig(K8sDelegateManifestConfig.builder().build())
                                                .variableOverridesYamlFiles(yamlList)
                                                .executionLogCallback(logCallback)
                                                .workingDir("working-dir")
                                                .commandFlags("--fetch")
                                                .repoName("my-repo")
                                                .chartSpecification(HelmChartSpecification.builder()
                                                                        .chartName("coolest-chart")
                                                                        .chartUrl("harness.io")
                                                                        .chartVersion("0.1.0")
                                                                        .build())
                                                .helmCommandFlag(HelmCommandFlag.builder().valueMap(valueMap).build())
                                                .rollbackVersion(0)
                                                .prevReleaseVersion(1)
                                                .newReleaseVersion(2)
                                                .timeoutInMillis(120)
                                                .build();

    HelmCommandData helmCommandData = HelmCommandDataMapper.getHelmCommandData(helmCommandRequest);

    assertThat(helmCommandData.getRollBackVersion()).isEqualTo(0);
    assertThat(helmCommandData.getValueMap()).containsKey(HelmSubCommandType.ROLLBACK);
    assertThat(helmCommandData.getChartVersion()).isEqualTo("0.1.0");
    assertThat(helmCommandData.getWorkingDir()).isEqualTo("working-dir");
    assertThat(helmCommandData.getCommandFlags()).isEqualTo("--fetch");
  }
}
