/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm.base;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.task.helm.HelmTaskHelperBase.RESOURCE_DIR_BASE;
import static io.harness.helm.HelmConstants.CHARTS_YAML_KEY;
import static io.harness.rule.OwnerRule.ABOSII;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfigType;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.helm.HelmChartManifestTaskService;
import io.harness.delegate.task.helm.HelmTaskHelperBase;
import io.harness.delegate.task.helm.beans.FetchHelmChartManifestRequest;
import io.harness.delegate.task.helm.response.HelmChartManifest;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.filesystem.AutoCloseableWorkingDirectory;
import io.harness.filesystem.FileIo;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.logging.LogCallback;
import io.harness.logging.NoopExecutionCallback;
import io.harness.rule.Owner;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import lombok.SneakyThrows;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(HarnessTeam.CDP)
public class HelmChartManifestTaskServiceTest extends CategoryTest {
  private static final String RES_BASE_PATH = "k8s/helm";
  private static final String RES_CHART_YAML_DEFAULT = "chart-default.yaml";
  private static final String RES_CHART_YAML_PARENT = "chart-parent.yaml";
  private static final String RES_CHART_YAML_GRAFANA = "chart-grafana.yaml";
  private static final String RES_CHART_YAML_FORWARD_COMPATIBILITY = "chart-forward-compatibility.yaml";

  private static final Map<String, Predicate<HelmChartManifest>> ASSERTION_BY_RESOURCE =
      ImmutableMap.of(RES_CHART_YAML_DEFAULT,
          manifest
          -> {
            assertThat(manifest.getApiVersion()).isEqualTo("v1");
            assertThat(manifest.getDescription()).isEqualTo("A Helm chart for Kubernetes");
            assertThat(manifest.getName()).isEqualTo("helm-chart-test");
            assertThat(manifest.getVersion()).isEqualTo("1.1.0");
            return true;
          },
          RES_CHART_YAML_GRAFANA,
          manifest
          -> {
            assertThat(manifest.getApiVersion()).isEqualTo("v2");
            assertThat(manifest.getDescription())
                .isEqualTo("The leading tool for querying and visualizing time series and metrics.");
            assertThat(manifest.getName()).isEqualTo("grafana");
            assertThat(manifest.getVersion()).isEqualTo("6.57.2");
            assertThat(manifest.getAppVersion()).isEqualTo("9.5.3");
            assertThat(manifest.getKubeVersion()).isEqualTo("^1.8.0-0");
            assertThat(manifest.getAnnotations())
                .isEqualTo(ImmutableMap.of("artifacthub.io/links",
                    "- name: Chart Source\n"
                        + "  url: https://github.com/grafana/helm-charts\n"
                        + "- name: Upstream Project\n"
                        + "  url: https://github.com/grafana/grafana\n"));
            assertThat(manifest.getType()).isEqualTo("application");
            return true;
          },
          RES_CHART_YAML_FORWARD_COMPATIBILITY,
          manifest -> {
            assertThat(manifest.getApiVersion()).isEqualTo("v2");
            assertThat(manifest.getDescription()).isEqualTo("A Helm chart for Kubernetes");
            assertThat(manifest.getName()).isEqualTo("helm-chart-test");
            assertThat(manifest.getVersion()).isEqualTo("1.1.0");
            return true;
          });

  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Mock private HelmTaskHelperBase helmTaskHelperBase;
  @Mock private GitFetchTaskHelper gitFetchTaskHelper;

  @InjectMocks private HelmChartManifestTaskService helmChartManifestTaskService;

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestHttpChart() {
    testFetchHelmRepo(
        HttpHelmStoreDelegateConfig.builder()
            .httpHelmConnector(HttpHelmConnectorDTO.builder().helmRepoUrl("https://helm.repo/charts").build())
            .build(),
        RES_CHART_YAML_DEFAULT, "");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestHttpChartSubChart() {
    testFetchHelmRepo(
        HttpHelmStoreDelegateConfig.builder()
            .httpHelmConnector(HttpHelmConnectorDTO.builder().helmRepoUrl("https://helm.repo/charts").build())
            .build(),
        RES_CHART_YAML_DEFAULT, "chart1");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestHttpChartCache() {
    testFetchHelmRepoWithCacheCheck(
        HttpHelmStoreDelegateConfig.builder()
            .httpHelmConnector(HttpHelmConnectorDTO.builder().helmRepoUrl("https://helm.repo/charts").build())
            .build(),
        RES_CHART_YAML_DEFAULT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestLocalRepo() {
    testFetchHelmChartManifestLocalRepo(null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestLocalRepoSubChart() {
    testFetchHelmChartManifestLocalRepo("charts/subchart");
  }

  @SneakyThrows
  private void testFetchHelmChartManifestLocalRepo(String subChart) {
    String localRepoDirectory = "./local/helm/repo";
    try (
        AutoCloseableWorkingDirectory _localRepoDirectory = new AutoCloseableWorkingDirectory(localRepoDirectory, 10)) {
      doReturn(true).when(helmTaskHelperBase).isHelmLocalRepoSet();
      doReturn("repoTest").when(helmTaskHelperBase).getRepoNameNG(any(StoreDelegateConfig.class));
      doReturn(localRepoDirectory)
          .when(helmTaskHelperBase)
          .getHelmLocalRepositoryCompletePath(anyString(), anyString(), anyString());
      doReturn(localRepoDirectory).when(helmTaskHelperBase).getHelmLocalRepositoryPath();
      doAnswer(invocation -> {
        final HelmChartManifestDelegateConfig config = invocation.getArgument(0);

        final Path chartYamlPath;
        if (isNotEmpty(subChart)) {
          chartYamlPath = Paths.get(localRepoDirectory, config.getChartName(), subChart, CHARTS_YAML_KEY);
        } else {
          chartYamlPath = Paths.get(localRepoDirectory, config.getChartName(), CHARTS_YAML_KEY);
        }

        FileIo.createDirectoryIfDoesNotExist(chartYamlPath.getParent());
        FileIo.writeFile(chartYamlPath, readResource(RES_CHART_YAML_DEFAULT).getBytes(StandardCharsets.UTF_8));
        return null;
      })
          .when(helmTaskHelperBase)
          .populateChartToLocalHelmRepo(
              any(HelmChartManifestDelegateConfig.class), anyLong(), any(LogCallback.class), anyString());

      testFetchHelmRepo(
          HttpHelmStoreDelegateConfig.builder()
              .httpHelmConnector(HttpHelmConnectorDTO.builder().helmRepoUrl("https://helm.repo/charts").build())
              .build(),
          RES_CHART_YAML_DEFAULT, subChart);
    }
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestS3Chart() {
    testFetchHelmRepo(S3HelmStoreDelegateConfig.builder()
                          .bucketName("test-bucket")
                          .region("us-east1")
                          .folderPath("test/carts")
                          .build(),
        RES_CHART_YAML_DEFAULT, null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestS3ChartSubChart() {
    testFetchHelmRepo(S3HelmStoreDelegateConfig.builder().bucketName("test-bucket").region("us-east1").build(),
        RES_CHART_YAML_DEFAULT, "charts/subchart1");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestS3ChartCache() {
    testFetchHelmRepoWithCacheCheck(
        S3HelmStoreDelegateConfig.builder().bucketName("test-bucket").region("us-east1").build(),
        RES_CHART_YAML_DEFAULT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestGcsChart() {
    testFetchHelmRepo(GcsHelmStoreDelegateConfig.builder().bucketName("test-bucket").folderPath("test/charts").build(),
        RES_CHART_YAML_DEFAULT, null);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestGcsChartSubChart() {
    testFetchHelmRepo(GcsHelmStoreDelegateConfig.builder().bucketName("test-bucket").build(), RES_CHART_YAML_DEFAULT,
        "repo/subcharts/subchart1");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestGcsChartCache() {
    testFetchHelmRepoWithCacheCheck(
        GcsHelmStoreDelegateConfig.builder().bucketName("test-bucket").build(), RES_CHART_YAML_DEFAULT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestGitChart() {
    final GitStoreDelegateConfig gitStoreConfig =
        GitStoreDelegateConfig.builder().path("helm/chart/test").gitConfigDTO(GitConfigDTO.builder().build()).build();
    testFetchHelmRepo(gitStoreConfig, RES_CHART_YAML_DEFAULT, "");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestGitChartSubchart() {
    final GitStoreDelegateConfig gitStoreConfig =
        GitStoreDelegateConfig.builder().path("helm/chart/test").gitConfigDTO(GitConfigDTO.builder().build()).build();
    testFetchHelmRepo(gitStoreConfig, RES_CHART_YAML_DEFAULT, "subchart");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestGitNoPath() {
    final GitStoreDelegateConfig gitStoreConfig =
        GitStoreDelegateConfig.builder().gitConfigDTO(GitConfigDTO.builder().build()).build();
    assertThatThrownBy(() -> testFetchHelmRepo(gitStoreConfig, RES_CHART_YAML_DEFAULT, "subchart"))
        .hasStackTraceContaining(
            "Unable to find Chart.yaml in helm chart package. Chart.yaml file is required for helm charts");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestGitChartCommitId() {
    final GitStoreDelegateConfig gitStoreConfig = GitStoreDelegateConfig.builder()
                                                      .path("helm/chart/test")
                                                      .gitConfigDTO(GitConfigDTO.builder().build())
                                                      .fetchType(FetchType.COMMIT)
                                                      .commitId("commitId")
                                                      .build();
    testFetchHelmRepoWithCacheCheck(gitStoreConfig, RES_CHART_YAML_DEFAULT);
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestGrafana() {
    testFetchHelmRepo(
        HttpHelmStoreDelegateConfig.builder()
            .httpHelmConnector(HttpHelmConnectorDTO.builder().helmRepoUrl("https://helm.repo/test/charts").build())
            .build(),
        RES_CHART_YAML_GRAFANA, "");
  }

  @Test
  @Owner(developers = ABOSII)
  @Category(UnitTests.class)
  public void testFetchHelmChartManifestForwardCompatibility() {
    testFetchHelmRepo(
        HttpHelmStoreDelegateConfig.builder()
            .httpHelmConnector(HttpHelmConnectorDTO.builder().helmRepoUrl("https://helm.repo/charts").build())
            .build(),
        RES_CHART_YAML_FORWARD_COMPATIBILITY, "");
  }

  private void testFetchHelmRepoWithCacheCheck(StoreDelegateConfig delegateConfig, String resourceFile) {
    testFetchHelmRepo(delegateConfig, resourceFile, "");
    validateCalledMethods(delegateConfig, 1);
    testFetchHelmRepo(delegateConfig, resourceFile, "");
    validateCalledMethods(delegateConfig, 1);
    testFetchHelmRepo(delegateConfig, resourceFile, "");
    validateCalledMethods(delegateConfig, 1);

    testFetchHelmRepo(delegateConfig, resourceFile, "subchart");
    validateCalledMethods(delegateConfig, 2);
    testFetchHelmRepo(delegateConfig, resourceFile, "subchart");
    validateCalledMethods(delegateConfig, 2);

    testFetchHelmRepo(delegateConfig, resourceFile, "charts/subchart");
    validateCalledMethods(delegateConfig, 3);
    testFetchHelmRepo(delegateConfig, resourceFile, "charts/subchart");
    validateCalledMethods(delegateConfig, 3);
  }

  @SneakyThrows
  private void testFetchHelmRepo(StoreDelegateConfig delegateConfig, String resourceFile, String subchart) {
    final String workingDirectory = Paths.get(RESOURCE_DIR_BASE, convertBase64UuidToCanonicalForm(generateUuid()))
                                        .normalize()
                                        .toAbsolutePath()
                                        .toString();
    final HelmChartManifestDelegateConfig helmChartConfig = HelmChartManifestDelegateConfig.builder()
                                                                .chartName("test-chart")
                                                                .chartVersion("1.0.1")
                                                                .storeDelegateConfig(delegateConfig)
                                                                .subChartPath(subchart)
                                                                .build();

    final FetchHelmChartManifestRequest request = FetchHelmChartManifestRequest.builder()
                                                      .accountId("accountId")
                                                      .workingDirectory(workingDirectory)
                                                      .timeoutInMillis(10000L)
                                                      .manifestDelegateConfig(helmChartConfig)
                                                      .build();

    try (AutoCloseableWorkingDirectory ignore = new AutoCloseableWorkingDirectory(workingDirectory, 10)) {
      setupFetchHelmChart(resourceFile);

      HelmChartManifest chartManifest = helmChartManifestTaskService.fetchHelmChartManifest(request);

      ASSERTION_BY_RESOURCE
          .getOrDefault(resourceFile,
              manifest -> {
                fail("Undeclared assertion for file %s. Fix test", resourceFile);
                return false;
              })
          .test(chartManifest);
    }

    assertThat(new File(workingDirectory)).doesNotExist();
  }

  @SneakyThrows
  private void setupFetchHelmChart(String resourceFile) {
    doAnswer(invocation -> {
      List<String> filePaths = invocation.getArgument(1);
      return FetchFilesResult.builder()
          .files(Collections.singletonList(
              GitFile.builder().filePath(filePaths.get(0)).fileContent(readResource(resourceFile)).build()))
          .build();
    })
        .when(gitFetchTaskHelper)
        .fetchFileFromRepo(any(GitStoreDelegateConfig.class), anyList(), anyString(), any(GitConfigDTO.class));

    doAnswer(invocation -> {
      HelmChartManifestDelegateConfig manifestConfig = invocation.getArgument(0);
      String destinationDirectory = invocation.getArgument(3);
      if (isNotEmpty(manifestConfig.getSubChartPath())) {
        saveChartYamlFileContentToFile(RES_CHART_YAML_PARENT, destinationDirectory, "");
      }

      saveChartYamlFileContentToFile(resourceFile, destinationDirectory, manifestConfig.getSubChartPath());
      return null;
    })
        .when(helmTaskHelperBase)
        .downloadHelmChart(
            any(HelmChartManifestDelegateConfig.class), anyLong(), any(NoopExecutionCallback.class), anyString());
  }

  @SneakyThrows
  private void validateCalledMethods(StoreDelegateConfig storeDelegateConfig, int times) {
    if (StoreDelegateConfigType.GIT == storeDelegateConfig.getType()) {
      verify(gitFetchTaskHelper, times(times))
          .fetchFileFromRepo(any(GitStoreDelegateConfig.class), anyList(), anyString(), any(GitConfigDTO.class));
    } else {
      verify(helmTaskHelperBase, times(times))
          .downloadHelmChart(
              any(HelmChartManifestDelegateConfig.class), anyLong(), any(NoopExecutionCallback.class), anyString());
    }
  }

  @SneakyThrows
  private void saveChartYamlFileContentToFile(String resourceFile, String destinationDirectory, String subPath) {
    Path outputPath = prepareOutputPath(destinationDirectory, subPath);
    assertThat(new File(destinationDirectory)).exists();
    String content = readResource(resourceFile);
    FileIo.writeFile(outputPath, content.getBytes(StandardCharsets.UTF_8));
  }

  @SneakyThrows
  private Path prepareOutputPath(String destinationDirectory, String subPath) {
    Path outputPath;
    if (isNotEmpty(subPath)) {
      outputPath = Paths.get(destinationDirectory, subPath, CHARTS_YAML_KEY);
    } else {
      outputPath = Paths.get(destinationDirectory, CHARTS_YAML_KEY);
    }

    FileIo.createDirectoryIfDoesNotExist(outputPath.getParent());
    return outputPath;
  }

  @SneakyThrows
  private static String readResource(String file) {
    ClassLoader classLoader = HelmChartManifestTaskServiceTest.class.getClassLoader();
    return Resources.toString(
        Objects.requireNonNull(classLoader.getResource(RES_BASE_PATH + "/" + file)), StandardCharsets.UTF_8);
  }
}