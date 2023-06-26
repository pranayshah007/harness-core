/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.helm;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.beans.storeconfig.StoreDelegateConfigType.GIT;
import static io.harness.helm.HelmConstants.CHARTS_YAML_KEY;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.connector.scm.adapter.ScmConnectorMapper;
import io.harness.delegate.beans.connector.scm.genericgitconnector.GitConfigDTO;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GitStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.task.git.GitFetchTaskHelper;
import io.harness.delegate.task.helm.beans.FetchHelmChartManifestRequest;
import io.harness.delegate.task.helm.response.HelmChartManifest;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.filesystem.FileIo;
import io.harness.git.model.FetchFilesResult;
import io.harness.helm.HelmChartYaml;
import io.harness.helm.HelmChartYamlMapper;
import io.harness.logging.NoopExecutionCallback;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class HelmChartManifestTaskService {
  private static final long CHART_CACHE_EXPIRE_AFTER_ACCESS_H = 1L;
  private static final long CHART_CACHE_EXPIRE_AFTER_WRITE_H = 2L;

  @Inject private HelmTaskHelperBase helmTaskHelperBase;

  @Inject private GitFetchTaskHelper gitFetchTaskHelper;

  private final Cache<HelmChartKey, HelmChartManifest> cache =
      CacheBuilder.newBuilder()
          .expireAfterAccess(Duration.ofHours(CHART_CACHE_EXPIRE_AFTER_ACCESS_H))
          .expireAfterWrite(Duration.ofHours(CHART_CACHE_EXPIRE_AFTER_WRITE_H))
          .build();

  public HelmChartManifest fetchHelmChartManifest(FetchHelmChartManifestRequest request) throws Exception {
    HelmChartMetadata metadata = createMetadata(request.getManifestDelegateConfig());
    Optional<HelmChartKey> helmChartKey = metadata.toHelmChartKey();
    if (helmChartKey.isPresent()) {
      return cache.get(helmChartKey.get(), () -> fetchAndReadHelmChartYaml(request, metadata));
    }

    return fetchAndReadHelmChartYaml(request, metadata);
  }

  private HelmChartManifest fetchAndReadHelmChartYaml(
      FetchHelmChartManifestRequest request, HelmChartMetadata metadata) {
    final File helmChartYamlFile;
    final HelmChartManifestDelegateConfig manifestConfig = request.getManifestDelegateConfig();
    try {
      helmChartYamlFile = fetchHelmChartYaml(
          manifestConfig, request.getWorkingDirectory(), request.getAccountId(), request.getTimeoutInMillis());
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      log.error("Failed to fetch helm chart", e);
      throw new InvalidRequestException("Failed to fetch helm chart: " + e.getMessage());
    }

    try (InputStream stream = new FileInputStream(helmChartYamlFile)) {
      HelmChartYaml helmChartYaml = HelmChartYamlMapper.deserialize(stream);
      return HelmChartManifest.create(helmChartYaml, metadata.toMap());
    } catch (FileNotFoundException | NoSuchFileException e) {
      String helmVersion = isNotEmpty(manifestConfig.getChartVersion())
          ? format("%s:%s", manifestConfig.getChartName(), manifestConfig.getHelmVersion())
          : manifestConfig.getChartName();
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Check if provided %s helm package is a valid helm chart", helmVersion),
          "Unable to find Chart.yaml in helm chart package. Chart.yaml file is required for helm charts",
          new InvalidArgumentsException(Pair.of(helmVersion, "Missing Chart.yaml file in helm chart package")));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private File fetchHelmChartYaml(HelmChartManifestDelegateConfig manifestConfig, String destinationDirectory,
      String accountId, long timeout) throws Exception {
    // TODO (abosii-harness) Ideally all the fetch logic should be handled in one place. Currently there is
    //  a different logic for K8s & Native Helm, and the behavior may be different between swimlanes
    if (GIT == manifestConfig.getStoreDelegateConfig().getType()) {
      fetchHelmChartYamlFromGit(manifestConfig, accountId, destinationDirectory);
    } else {
      helmTaskHelperBase.decryptEncryptedDetails(manifestConfig);
      fetchHelmChartYamlFromHelmRepo(manifestConfig, destinationDirectory, timeout);
    }

    String chartYamlSubpath;
    if (isNotEmpty(manifestConfig.getSubChartPath())) {
      chartYamlSubpath = Paths.get(manifestConfig.getSubChartPath(), CHARTS_YAML_KEY).toString();
    } else {
      chartYamlSubpath = CHARTS_YAML_KEY;
    }

    return Paths.get(destinationDirectory, chartYamlSubpath).toFile();
  }

  private void fetchHelmChartYamlFromGit(
      HelmChartManifestDelegateConfig manifestConfig, String accountId, String destinationDirectory) throws Exception {
    GitStoreDelegateConfig gitStoreDelegateConfig = (GitStoreDelegateConfig) manifestConfig.getStoreDelegateConfig();
    gitFetchTaskHelper.decryptGitConfig(gitStoreDelegateConfig);
    if (isEmpty(gitStoreDelegateConfig.getPaths())) {
      return;
    }

    final String chartPath = gitStoreDelegateConfig.getPaths().get(0);
    GitConfigDTO gitConfigDTO = ScmConnectorMapper.toGitConfigDTO(gitStoreDelegateConfig.getGitConfigDTO());
    String chartYamlPath = getChartYamlPath(manifestConfig, chartPath);

    FetchFilesResult result = gitFetchTaskHelper.fetchFileFromRepo(
        gitStoreDelegateConfig, Collections.singletonList(chartYamlPath), accountId, gitConfigDTO);
    if (isEmpty(result.getFiles())) {
      return;
    }

    String chartYamlContent = result.getFiles().get(0).getFileContent();
    if (isEmpty(chartYamlContent)) {
      return;
    }

    String chartYamlOutputPath = getChartYamlPath(manifestConfig, destinationDirectory);
    FileIo.createDirectoryIfDoesNotExist(Paths.get(chartYamlOutputPath).getParent());
    FileIo.waitForDirectoryToBeAccessibleOutOfProcess(chartYamlPath, 10);
    FileIo.writeFile(
        getChartYamlPath(manifestConfig, destinationDirectory), chartYamlContent.getBytes(StandardCharsets.UTF_8));
  }

  private void fetchHelmChartYamlFromHelmRepo(HelmChartManifestDelegateConfig manifestConfig,
      String destinationDirectory, long timeoutInMillis) throws Exception {
    String chartName = manifestConfig.getChartName();
    String repoName = helmTaskHelperBase.getRepoNameNG(manifestConfig.getStoreDelegateConfig());
    String chartVersion = manifestConfig.getChartVersion();
    if (helmTaskHelperBase.isHelmLocalRepoSet()) {
      String parentDir = helmTaskHelperBase.getHelmLocalRepositoryCompletePath(repoName, chartName, chartVersion);
      helmTaskHelperBase.populateChartToLocalHelmRepo(
          manifestConfig, timeoutInMillis, new NoopExecutionCallback(), parentDir);
      String localChartDirectory = HelmTaskHelperBase.getChartDirectory(parentDir, chartName);
      File chartYamlFile = new File(getChartYamlPath(manifestConfig, localChartDirectory));
      if (!chartYamlFile.exists()) {
        return;
      }

      File destinationFile = new File(getChartYamlPath(manifestConfig, destinationDirectory));
      FileUtils.copyFile(chartYamlFile, destinationFile);
    } else {
      helmTaskHelperBase.downloadHelmChart(
          manifestConfig, timeoutInMillis, new NoopExecutionCallback(), destinationDirectory);
    }
  }

  private String getChartYamlPath(HelmChartManifestDelegateConfig manifestConfig, String basePath) {
    if (isNotEmpty(manifestConfig.getSubChartPath())) {
      return Paths.get(basePath, manifestConfig.getSubChartPath(), CHARTS_YAML_KEY).toString();
    }

    return Paths.get(basePath, CHARTS_YAML_KEY).toString();
  }

  private static HelmChartMetadata createMetadata(HelmChartManifestDelegateConfig config) {
    var metadataBuilder = HelmChartMetadata.builder()
                              .chartName(config.getChartName())
                              .chartVersion(config.getChartVersion())
                              .subChartPath(config.getSubChartPath());

    switch (config.getStoreDelegateConfig().getType()) {
      case HTTP_HELM:
        HttpHelmStoreDelegateConfig httpHelm = (HttpHelmStoreDelegateConfig) config.getStoreDelegateConfig();
        metadataBuilder.url(httpHelm.getHttpHelmConnector().getHelmRepoUrl());
        metadataBuilder.cacheRepoUrl(httpHelm.getHttpHelmConnector().getHelmRepoUrl());
        break;
      case S3_HELM:
        S3HelmStoreDelegateConfig s3Helm = (S3HelmStoreDelegateConfig) config.getStoreDelegateConfig();
        metadataBuilder.region(s3Helm.getRegion())
            .bucketName(s3Helm.getBucketName())
            .basePath(s3Helm.getFolderPath())
            .cacheRepoUrl(format("s3://%s/%s/%s", s3Helm.getRegion(), s3Helm.getBucketName(), s3Helm.getFolderPath()));
        break;
      case GCS_HELM:
        GcsHelmStoreDelegateConfig gcsHelm = (GcsHelmStoreDelegateConfig) config.getStoreDelegateConfig();
        metadataBuilder.bucketName(gcsHelm.getBucketName())
            .basePath(gcsHelm.getFolderPath())
            .cacheRepoUrl(format("gcs://%s/%s", gcsHelm.getBucketName(), gcsHelm.getFolderPath()));
        break;
      case OCI_HELM:
        OciHelmStoreDelegateConfig ociHelm = (OciHelmStoreDelegateConfig) config.getStoreDelegateConfig();
        metadataBuilder.url(ociHelm.getOciHelmConnector().getHelmRepoUrl())
            .basePath(ociHelm.getBasePath())
            .cacheRepoUrl(format("%s/%s", ociHelm.getOciHelmConnector().getHelmRepoUrl(), ociHelm.getBasePath()));
        break;
      case GIT:
        GitStoreDelegateConfig gitConfig = (GitStoreDelegateConfig) config.getStoreDelegateConfig();
        metadataBuilder.url(gitConfig.getGitConfigDTO().getUrl());
        if (FetchType.COMMIT == gitConfig.getFetchType()) {
          metadataBuilder.commitId(gitConfig.getCommitId());
          metadataBuilder.chartVersion(gitConfig.getCommitId());
          if (isNotEmpty(gitConfig.getPaths())) {
            metadataBuilder.cacheRepoUrl(format("%s/%s/%s", gitConfig.getGitConfigDTO().getUrl(),
                gitConfig.getPaths().get(0), gitConfig.getCommitId()));
          }
        } else {
          metadataBuilder.branch(gitConfig.getBranch());
        }

        if (isNotEmpty(gitConfig.getPaths())) {
          metadataBuilder.basePath(gitConfig.getPaths().get(0));
          metadataBuilder.chartName(gitConfig.getPaths().get(0));
        }
        break;
      default:
        // do nothing
    }

    return metadataBuilder.build();
  }

  @Value
  @Builder
  @EqualsAndHashCode
  private static class HelmChartKey {
    String repoUrl;
    String chartName;
    String chartVersion;
    String subChartPath;
  }

  @Value
  @Builder
  @EqualsAndHashCode
  @FieldNameConstants(innerTypeName = "MetadataKeys")
  private static class HelmChartMetadata {
    String url;
    String chartName;
    String chartVersion;
    String subChartPath;
    String bucketName;
    String basePath;
    String region;
    String commitId;
    String branch;
    String cacheRepoUrl;

    public Optional<HelmChartKey> toHelmChartKey() {
      if (isEmpty(cacheRepoUrl) || isEmpty(chartVersion)) {
        return Optional.empty();
      }

      return Optional.of(HelmChartKey.builder()
                             .repoUrl(cacheRepoUrl)
                             .chartName(chartName)
                             .chartVersion(chartVersion)
                             .subChartPath(subChartPath)
                             .build());
    }

    public Map<String, String> toMap() {
      Map<String, String> metadataMap = new HashMap<>();
      addNotEmpty(metadataMap, MetadataKeys.url, url);
      addNotEmpty(metadataMap, MetadataKeys.basePath, basePath);
      addNotEmpty(metadataMap, MetadataKeys.bucketName, bucketName);
      addNotEmpty(metadataMap, MetadataKeys.commitId, commitId);
      addNotEmpty(metadataMap, MetadataKeys.branch, branch);
      return metadataMap;
    }

    private static void addNotEmpty(Map<String, String> map, String key, String value) {
      if (isNotEmpty(value)) {
        map.put(key, value);
      }
    }
  }
}
