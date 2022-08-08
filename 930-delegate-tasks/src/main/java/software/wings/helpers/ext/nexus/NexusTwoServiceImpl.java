/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.helpers.ext.nexus;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.nexus.NexusHelper.getBaseUrl;
import static io.harness.nexus.NexusHelper.isSuccessful;
import static io.harness.threading.Morpheus.quietSleep;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;
import static software.wings.helpers.ext.nexus.NexusServiceImpl.getRetrofit;

import static java.lang.String.format;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.datacollection.utils.EmptyPredicate;
import io.harness.delegate.beans.artifact.ArtifactFileMetadata;
import io.harness.delegate.task.ListNotifyResponseData;
import io.harness.exception.ArtifactServerException;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.nexus.NexusRequest;
import io.harness.nexus.NexusRestClient;
import io.harness.nexus.model.IndexBrowserTreeNode;
import io.harness.nexus.model.IndexBrowserTreeViewResponse;
import io.harness.nexus.model.Project;
import io.harness.stream.StreamUtils;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.common.AlphanumComparator;
import software.wings.delegatetasks.collect.artifacts.ArtifactCollectionCommonTaskHelper;
import software.wings.helpers.ext.artifactory.FolderPath;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.utils.RepositoryFormat;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.net.ssl.HttpsURLConnection;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.sonatype.nexus.rest.model.ContentListResource;
import org.sonatype.nexus.rest.model.ContentListResourceResponse;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.simplexml.SimpleXmlConverterFactory;

/**
 * Created by sgurubelli on 11/18/17.
 */
@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._960_API_SERVICES)
public class NexusTwoServiceImpl {
  @Inject private ExecutorService executorService;
  @Inject private ArtifactCollectionCommonTaskHelper artifactCollectionCommonTaskHelper;
  @Inject private CGNexusHelper CGNexusHelper;

  public List<String> collectPackageNames(NexusRequest nexusConfig, String repoId, List<String> packageNames)
      throws IOException {
    Call<ContentListResourceResponse> request;
    if (nexusConfig.isHasCredentials()) {
      request = getRestClient(nexusConfig)
                    .getRepositoryContents(
                        Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())), repoId);
    } else {
      request = getRestClient(nexusConfig).getRepositoryContents(repoId);
    }

    final Response<ContentListResourceResponse> response = request.execute();
    if (isSuccessful(response)) {
      response.body().getData().forEach(content -> packageNames.add(content.getText()));
    }
    return packageNames;
  }

  public List<String> collectGroupIds(NexusRequest nexusConfig, String repoId, List<String> groupIds,
      String repositoryFormat) throws ExecutionException, InterruptedException, IOException {
    if (repositoryFormat == null || repositoryFormat.equals(RepositoryFormat.maven.name())) {
      return fetchMavenGroupIds(nexusConfig, repoId, groupIds);
    } else if (repositoryFormat.equals(RepositoryFormat.nuget.name())
        || repositoryFormat.equals(RepositoryFormat.npm.name())) {
      return collectPackageNames(nexusConfig, repoId, groupIds);
    }
    return groupIds;
  }

  private List<String> fetchMavenGroupIds(NexusRequest nexusConfig, String repoId, List<String> groupIds)
      throws InterruptedException, ExecutionException {
    NexusRestClient nexusRestClient = getRestClient(nexusConfig);
    Queue<Future> futures = new ConcurrentLinkedQueue<>();
    Stack<FolderPath> paths = new Stack<>();
    paths.addAll(getFolderPaths(nexusRestClient, nexusConfig, repoId, ""));
    while (isNotEmpty(paths) || isNotEmpty(futures)) {
      while (isNotEmpty(paths)) {
        FolderPath folderPath = paths.pop();
        String path = folderPath.getPath();
        if (folderPath.isFolder()) {
          traverseInParallel(nexusRestClient, nexusConfig, repoId, path, futures, paths);
        } else {
          // Strip out the version
          String[] pathElems = folderPath.getPath().substring(1).split("/");
          if (pathElems.length >= 1) {
            groupIds.add(getGroupId(Arrays.stream(pathElems).limit(pathElems.length - 1).collect(toList())));
          }
        }
      }
      while (!futures.isEmpty() && futures.peek().isDone()) {
        futures.poll().get();
      }
      quietSleep(ofMillis(20)); // avoid busy wait
    }
    return groupIds;
  }

  private void traverseInParallel(NexusRestClient nexusRestClient, NexusRequest nexusConfig, String repoKey,
      String path, Queue<Future> futures, Stack<FolderPath> paths) {
    futures.add(
        executorService.submit(() -> paths.addAll(getFolderPaths(nexusRestClient, nexusConfig, repoKey, path))));
  }

  public List<String> getArtifactNames(NexusRequest nexusConfig, String repoId, String path) throws IOException {
    log.info("Retrieving Artifact Names");
    final List<String> artifactNames = new ArrayList<>();
    final String url = getIndexContentPathUrl(nexusConfig, repoId, getGroupId(path));
    final Response<IndexBrowserTreeViewResponse> response =
        getIndexBrowserTreeViewResponseResponse(getRestClient(nexusConfig), nexusConfig, url);
    if (isSuccessful(response)) {
      final List<IndexBrowserTreeNode> treeNodes = response.body().getData().getChildren();
      if (treeNodes != null) {
        treeNodes.forEach((IndexBrowserTreeNode treeNode) -> {
          if (treeNode.getType().equals("A")) {
            artifactNames.add(treeNode.getNodeName());
          }
        });
      }
    }
    log.info("Retrieving Artifact Names success");
    return artifactNames;
  }

  public List<String> getArtifactPaths(NexusRequest nexusConfig, String repoId) throws IOException {
    final Call<ContentListResourceResponse> request =
        getRestClient(nexusConfig)
            .getRepositoryContents(
                Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())), repoId);
    return getArtifactPaths(request);
  }

  private List<String> getArtifactPaths(Call<ContentListResourceResponse> request) throws IOException {
    final Response<ContentListResourceResponse> response = request.execute();
    final List<String> artifactPaths = new ArrayList<>();
    if (isSuccessful(response)) {
      response.body().getData().forEach(artifact -> artifactPaths.add(artifact.getRelativePath()));
    }
    return artifactPaths;
  }

  public List<String> getArtifactPaths(NexusRequest nexusConfig, String repoId, String name) throws IOException {
    name = name.charAt(0) == '/' ? name.substring(1) : name;
    final Call<ContentListResourceResponse> request =
        getRestClient(nexusConfig)
            .getRepositoryContents(
                Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())), repoId, name);
    return getArtifactPaths(request);
  }

  public List<BuildDetails> getVersions(NexusRequest nexusConfig, String repoId, String groupId, String artifactName,
      String extension, String classifier) throws IOException {
    log.info("Retrieving versions for repoId {} groupId {} and artifactName {}", repoId, groupId, artifactName);
    String url = getIndexContentPathUrl(nexusConfig, repoId, getGroupId(groupId)) + artifactName + "/";
    final Response<IndexBrowserTreeViewResponse> response =
        getIndexBrowserTreeViewResponseResponse(getRestClient(nexusConfig), nexusConfig, url);
    List<String> versions = new ArrayList<>();
    Map<String, String> versionToArtifactUrls = new HashMap<>();
    Map<String, List<ArtifactFileMetadata>> versionToArtifactDownloadUrls = new HashMap<>();
    if (isSuccessful(response)) {
      final List<IndexBrowserTreeNode> treeNodes = response.body().getData().getChildren();
      if (treeNodes != null) {
        for (IndexBrowserTreeNode treeNode : treeNodes) {
          if (treeNode.getType().equals("A")) {
            List<IndexBrowserTreeNode> children = treeNode.getChildren();
            for (IndexBrowserTreeNode child : children) {
              if (child.getType().equals("V")) {
                versions.add(child.getNodeName());
                List<ArtifactFileMetadata> artifactFileMetadata =
                    constructArtifactDownloadUrls(nexusConfig, child, extension, classifier);
                if (isNotEmpty(artifactFileMetadata)) {
                  versionToArtifactUrls.put(child.getNodeName(), artifactFileMetadata.get(0).getUrl());
                }
                versionToArtifactDownloadUrls.put(child.getNodeName(), artifactFileMetadata);
              }
            }
          }
        }
      }
    }
    return CGNexusHelper.constructBuildDetails(repoId, groupId, artifactName, versions, versionToArtifactUrls,
        versionToArtifactDownloadUrls, extension, classifier);
  }

  public boolean existsVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName,
      String extension, String classifier) throws IOException {
    if (isEmpty(extension) && isEmpty(classifier)) {
      return true;
    }
    log.info(
        "Checking if versions exist for repoId: {} groupId: {} and artifactName: {}", repoId, groupId, artifactName);
    String url = getIndexContentPathUrl(nexusConfig, repoId, getGroupId(groupId)) + artifactName + "/";
    final Response<IndexBrowserTreeViewResponse> response =
        getIndexBrowserTreeViewResponseResponse(getRestClient(nexusConfig), nexusConfig, url);
    if (isSuccessful(response)) {
      final List<IndexBrowserTreeNode> treeNodes = response.body().getData().getChildren();
      if (treeNodes != null) {
        for (IndexBrowserTreeNode treeNode : treeNodes) {
          if (treeNode.getType().equals("A")) {
            List<IndexBrowserTreeNode> children = treeNode.getChildren();
            for (IndexBrowserTreeNode child : children) {
              if (child.getType().equals("V")) {
                log.info("Checking if required artifacts exist for version: " + child.getNodeName());
                String relativePath = getGroupId(groupId) + artifactName + '/' + child.getNodeName() + '/';
                relativePath = relativePath.charAt(0) == '/' ? relativePath.substring(1) : relativePath;
                Call<ContentListResourceResponse> request;
                if (nexusConfig.isHasCredentials()) {
                  request = getRestClient(nexusConfig)
                                .getRepositoryContents(
                                    Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
                                    repoId, relativePath);
                } else {
                  request = getRestClient(nexusConfig).getRepositoryContents(repoId, relativePath);
                }

                final Response<ContentListResourceResponse> contentResponse = request.execute();
                if (isSuccessful(contentResponse)) {
                  if (isNotEmpty(contentResponse.body().getData())) {
                    for (ContentListResource contentListResource : contentResponse.body().getData()) {
                      if (contentListResource.isLeaf()) {
                        if (isNotEmpty(extension) && isNotEmpty(classifier)) {
                          if (contentListResource.getText().endsWith('-' + classifier + '.' + extension)) {
                            return true;
                          }
                        } else if (isNotEmpty(extension)) {
                          if (contentListResource.getText().endsWith(extension)) {
                            return true;
                          }
                        } else if (isNotEmpty(classifier)) {
                          int index = contentListResource.getText().lastIndexOf('.');
                          if (index != -1) {
                            if (contentListResource.getText().substring(0, index).endsWith(classifier)) {
                              return true;
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }
            }
            throw new ArtifactServerException("No versions found with specified extension/ classifier", null, USER);
          }
        }
      }
    }
    return true;
  }

  @SuppressWarnings("squid:S00107")
  public List<BuildDetails> getVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName,
      String extension, String classifier, String buildNo) throws IOException {
    log.info(
        "Retrieving version {} for repoId {} groupId {} and artifactName {}", buildNo, repoId, groupId, artifactName);
    String url = getIndexContentPathUrl(nexusConfig, repoId, getGroupId(groupId)) + artifactName + "/" + buildNo + "/";
    final Response<IndexBrowserTreeViewResponse> response =
        getIndexBrowserTreeViewResponseResponse(getRestClient(nexusConfig), nexusConfig, url);
    List<String> versions = new ArrayList<>();
    Map<String, String> versionToArtifactUrls = new HashMap<>();
    Map<String, List<ArtifactFileMetadata>> versionToArtifactDownloadUrls = new HashMap<>();
    if (isSuccessful(response)) {
      final List<IndexBrowserTreeNode> treeNodes = response.body().getData().getChildren();
      if (treeNodes != null) {
        for (IndexBrowserTreeNode treeNode : treeNodes) {
          if (treeNode.getType().equals("A")) {
            List<IndexBrowserTreeNode> children = treeNode.getChildren();
            for (IndexBrowserTreeNode child : children) {
              if (child.getType().equals("V")) {
                versions.add(child.getNodeName());
                List<ArtifactFileMetadata> artifactFileMetadata =
                    constructArtifactDownloadUrls(nexusConfig, child, extension, classifier);
                if (isNotEmpty(artifactFileMetadata)) {
                  versionToArtifactUrls.put(child.getNodeName(), artifactFileMetadata.get(0).getUrl());
                }
                versionToArtifactDownloadUrls.put(child.getNodeName(), artifactFileMetadata);
              }
            }
          }
        }
      }
    }
    return CGNexusHelper.constructBuildDetails(repoId, groupId, artifactName, versions, versionToArtifactUrls,
        versionToArtifactDownloadUrls, extension, classifier);
  }

  public List<BuildDetails> getVersions(String repositoryFormat, NexusRequest nexusConfig, String repositoryId,
      String packageName, Set<String> collectedBuilds) throws IOException {
    switch (repositoryFormat) {
      case "nuget":
        return getVersionsForNuGet(nexusConfig, repositoryId, packageName, collectedBuilds);
      case "npm":
        return getVersionsForNPM(nexusConfig, repositoryId, packageName);
      default:
        throw new WingsException("Unsupported format for Nexus 3.x", USER);
    }
  }

  public BuildDetails getVersion(String repositoryFormat, NexusRequest nexusConfig, String repositoryId,
      String packageName, String buildNo) throws IOException {
    switch (repositoryFormat) {
      case "nuget":
        return getVersionForNuGet(nexusConfig, repositoryId, packageName, buildNo);
      case "npm":
        return getVersionForNPM(nexusConfig, repositoryId, packageName, buildNo);
      default:
        throw new InvalidRequestException("Unsupported format for Nexus 2.x");
    }
  }

  @NotNull
  private List<BuildDetails> getVersionsForNuGet(NexusRequest nexusConfig, String repositoryId, String packageName,
      Set<String> collectedBuilds) throws IOException {
    log.info("Retrieving versions for NuGet repositoryId {} for packageName {}", repositoryId, packageName);
    Call<ContentListResourceResponse> request;
    NexusRestClient nexusRestClient = getRestClient(nexusConfig);
    if (nexusConfig.isHasCredentials()) {
      request = nexusRestClient.getRepositoryContents(
          Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())), repositoryId,
          packageName);
    } else {
      request = nexusRestClient.getRepositoryContentsWithoutCredentials(repositoryId, packageName);
    }
    List<String> versions = new ArrayList<>();
    final Response<ContentListResourceResponse> response = request.execute();
    Map<String, String> versionToArtifactUrls = new HashMap<>();
    Map<String, List<ArtifactFileMetadata>> versionToArtifactDownloadUrls = new HashMap<>();
    if (isSuccessful(response)) {
      response.body().getData().forEach(content -> {
        versions.add(content.getText());
        try {
          // We skip the URL fetching for versions that are already collected
          if (EmptyPredicate.isEmpty(collectedBuilds) || !collectedBuilds.contains(content.getText())) {
            List<ArtifactFileMetadata> artifactFileMetadata = getArtifactDownloadMetadataForVersionForNuGet(
                nexusConfig, repositoryId, packageName, content.getText());
            versionToArtifactDownloadUrls.put(content.getText(), artifactFileMetadata);
            if (isNotEmpty(artifactFileMetadata)) {
              versionToArtifactUrls.put(content.getText(), artifactFileMetadata.get(0).getUrl());
            }
          }
        } catch (IOException e) {
          log.info("Failed in getting artifact download urls");
        }
      });
    }
    log.info("Versions order come from nexus server {}", versions);
    List<String> sortedVersions = versions.stream().sorted(new AlphanumComparator()).collect(toList());
    log.info("After sorting alphanumerically versions {}", versions);

    return sortedVersions.stream()
        .map(version -> {
          Map<String, String> metadata = new HashMap<>();
          metadata.put(ArtifactMetadataKeys.repositoryName, repositoryId);
          metadata.put(ArtifactMetadataKeys.nexusPackageName, packageName);
          metadata.put(ArtifactMetadataKeys.version, version);
          return aBuildDetails()
              .withNumber(version)
              .withRevision(version)
              .withBuildUrl(versionToArtifactUrls.get(version))
              .withMetadata(metadata)
              .withUiDisplayName("Version# " + version)
              .withArtifactDownloadMetadata(versionToArtifactDownloadUrls.get(version))
              .build();
        })
        .collect(toList());
  }

  private List<ArtifactFileMetadata> getArtifactDownloadMetadataForVersionForNuGet(
      NexusRequest nexusConfig, String repositoryName, String packageName, String version) throws IOException {
    List<ArtifactFileMetadata> artifactFileMetadata = new ArrayList<>();
    log.info(
        "Retrieving artifacts of NuGet Repository {}, Package {} of Version {}", repositoryName, packageName, version);
    Call<ContentListResourceResponse> request;
    if (nexusConfig.isHasCredentials()) {
      request = getRestClient(nexusConfig)
                    .getRepositoryContents(
                        Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
                        repositoryName, packageName + "/" + version);
    } else {
      request = getRestClient(nexusConfig)
                    .getRepositoryContentsWithoutCredentials(repositoryName, packageName + "/" + version);
    }
    final Response<ContentListResourceResponse> response = request.execute();
    if (isSuccessful(response)) {
      response.body().getData().forEach(content -> {
        final String artifactName = content.getText();
        if (artifactName.endsWith("pom") || artifactName.endsWith("md5") || artifactName.endsWith("sha1")) {
          return;
        }
        final String artifactUrl = content.getResourceURI();
        log.info("Artifact Download Url {}", artifactUrl);
        artifactFileMetadata.add(ArtifactFileMetadata.builder().fileName(artifactName).url(artifactUrl).build());
      });
    }
    return artifactFileMetadata;
  }

  private BuildDetails getVersionForNuGet(
      NexusRequest nexusConfig, String repositoryId, String packageName, String version) {
    try {
      List<ArtifactFileMetadata> artifactFileMetadata =
          getArtifactDownloadMetadataForVersionForNuGet(nexusConfig, repositoryId, packageName, version);
      if (isNotEmpty(artifactFileMetadata)) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(ArtifactMetadataKeys.repositoryName, repositoryId);
        metadata.put(ArtifactMetadataKeys.nexusPackageName, packageName);
        metadata.put(ArtifactMetadataKeys.version, version);
        return aBuildDetails()
            .withNumber(version)
            .withRevision(version)
            .withBuildUrl(artifactFileMetadata.get(0).getUrl())
            .withMetadata(metadata)
            .withUiDisplayName("Version# " + version)
            .withArtifactDownloadMetadata(artifactFileMetadata)
            .build();
      }
    } catch (IOException e) {
      log.error("Failed in getting artifact download urls", e);
    }
    return null;
  }

  @NotNull
  private List<BuildDetails> getVersionsForNPM(NexusRequest nexusConfig, String repositoryId, String packageName)
      throws IOException {
    log.info("Retrieving versions for NPM repositoryId {} for packageName {}", repositoryId, packageName);
    Call<JsonNode> request;
    if (nexusConfig.isHasCredentials()) {
      request = getRestClientJacksonConverter(nexusConfig)
                    .getVersions(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
                        repositoryId, packageName);
    } else {
      request = getRestClientJacksonConverter(nexusConfig).getVersionsWithoutCredentials(repositoryId, packageName);
    }
    List<String> versions = new ArrayList<>();
    final Response<JsonNode> response = request.execute();
    Map<String, String> versionToArtifactUrls = new HashMap<>();
    Map<String, List<ArtifactFileMetadata>> versionToArtifactDownloadUrls = new HashMap<>();

    if (isSuccessful(response)) {
      JsonNode resultNode = response.body().at("/versions");
      if (resultNode != null) {
        Iterator<JsonNode> iterator = resultNode.elements();
        while (iterator.hasNext()) {
          JsonNode next = iterator.next();
          versions.add(next.at("/version").textValue());
          final String artifactUrl = next.at("/dist/tarball").asText();
          versionToArtifactUrls.put(next.at("/version").textValue(), artifactUrl);
          log.info("Artifact Download Url {}", artifactUrl);
          final String artifactName = artifactUrl.substring(artifactUrl.lastIndexOf('/') + 1);
          versionToArtifactDownloadUrls.put(next.at("/version").textValue(),
              asList(ArtifactFileMetadata.builder().fileName(artifactName).url(artifactUrl).build()));
        }
      }
    }
    log.info("Versions order come from nexus server {}", versions);
    List<String> sortedVersions = versions.stream().sorted(new AlphanumComparator()).collect(toList());
    log.info("After sorting alphanumerically versions {}", versions);

    return sortedVersions.stream()
        .map(version -> {
          Map<String, String> metadata = new HashMap<>();
          metadata.put(ArtifactMetadataKeys.repositoryName, repositoryId);
          metadata.put(ArtifactMetadataKeys.nexusPackageName, packageName);
          metadata.put(ArtifactMetadataKeys.version, version);
          metadata.put(ArtifactMetadataKeys.url, versionToArtifactUrls.get(version));
          return aBuildDetails()
              .withNumber(version)
              .withRevision(version)
              .withBuildUrl(versionToArtifactUrls.get(version))
              .withMetadata(metadata)
              .withUiDisplayName("Version# " + version)
              .withArtifactDownloadMetadata(versionToArtifactDownloadUrls.get(version))
              .build();
        })
        .collect(toList());
  }

  private BuildDetails getVersionForNPM(
      NexusRequest nexusConfig, String repositoryId, String packageName, String version) throws IOException {
    log.info("Retrieving version {} for NPM repositoryId {} for packageName {}", version, repositoryId, packageName);
    Call<JsonNode> request;
    if (nexusConfig.isHasCredentials()) {
      request = getRestClientJacksonConverter(nexusConfig)
                    .getVersion(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
                        repositoryId, packageName, version);
    } else {
      request =
          getRestClientJacksonConverter(nexusConfig).getVersionWithoutCredentials(repositoryId, packageName, version);
    }
    final Response<JsonNode> response = request.execute();
    if (isSuccessful(response) && response.body().at("/version").asText().equals(version)) {
      String artifactUrl = response.body().at("/dist/tarball").asText();
      log.info("Artifact Download Url {}", artifactUrl);
      final String artifactName = artifactUrl.substring(artifactUrl.lastIndexOf('/') + 1);
      Map<String, String> metadata = new HashMap<>();
      metadata.put(ArtifactMetadataKeys.repositoryName, repositoryId);
      metadata.put(ArtifactMetadataKeys.nexusPackageName, packageName);
      metadata.put(ArtifactMetadataKeys.version, version);
      metadata.put(ArtifactMetadataKeys.url, artifactUrl);
      return aBuildDetails()
          .withNumber(version)
          .withRevision(version)
          .withBuildUrl(artifactUrl)
          .withMetadata(metadata)
          .withUiDisplayName("Version# " + version)
          .withArtifactDownloadMetadata(
              asList(ArtifactFileMetadata.builder().fileName(artifactName).url(artifactUrl).build()))
          .build();
    }
    return null;
  }

  private List<ArtifactFileMetadata> constructArtifactDownloadUrls(
      NexusRequest nexusConfig, IndexBrowserTreeNode child, String extension, String classifier) {
    List<ArtifactFileMetadata> artifactUrls = new ArrayList<>();
    if (child.getChildren() != null) {
      List<IndexBrowserTreeNode> artifacts = child.getChildren();
      if (artifacts != null) {
        for (IndexBrowserTreeNode artifact : artifacts) {
          if (!artifact.getNodeName().endsWith("pom")) {
            String artifactName = artifact.getNodeName();
            if (!artifactName.endsWith("pom")) {
              if (classifier == null || artifactName.contains(classifier)) {
                String artifactUrl = constructArtifactDownloadUrl(nexusConfig, artifact, extension, classifier);
                if (isEmpty(extension) || artifactName.endsWith(extension)) {
                  if (log.isDebugEnabled()) {
                    log.debug("Artifact Url:" + artifactUrl + " for artifact filename: " + artifactName);
                  }
                  artifactUrls.add(ArtifactFileMetadata.builder().fileName(artifactName).url(artifactUrl).build());
                }
              }
            }
          }
        }
      }
    }
    return artifactUrls;
  }

  public BuildDetails getLatestVersion(NexusRequest nexusConfig, String repoId, String groupId, String artifactName) {
    log.info("Retrieving the latest version for repo {} group {} and artifact {}", repoId, groupId, artifactName);
    Project project = getPomModel(nexusConfig, repoId, groupId, artifactName, "LATEST");
    String version = project.getVersion() != null ? project.getVersion() : project.getParent().getVersion();
    log.info("Retrieving the latest version {}", project);
    return aBuildDetails().withNumber(version).withRevision(version).withUiDisplayName("Version# " + version).build();
  }

  public Pair<String, InputStream> downloadArtifact(NexusRequest nexusConfig,
      ArtifactStreamAttributes artifactStreamAttributes, Map<String, String> artifactMetadata, String delegateId,
      String taskId, String accountId, ListNotifyResponseData notifyResponseData) throws IOException {
    final String repositoryName = artifactStreamAttributes.getRepositoryName().contains("${")
        ? artifactMetadata.get(ArtifactMetadataKeys.repositoryName)
        : artifactStreamAttributes.getRepositoryName();
    final String repositoryFormat = artifactStreamAttributes.getRepositoryFormat();
    final String version = artifactMetadata.get(ArtifactMetadataKeys.buildNo);
    if (repositoryFormat == null || repositoryFormat.equals(RepositoryFormat.maven.name())) {
      final String groupId = artifactStreamAttributes.getGroupId().contains("${")
          ? artifactMetadata.get(ArtifactMetadataKeys.nexusGroupId)
          : artifactStreamAttributes.getGroupId();
      final String artifactId = artifactStreamAttributes.getArtifactName().contains("${")
          ? artifactMetadata.get(ArtifactMetadataKeys.nexusArtifactId)
          : artifactStreamAttributes.getArtifactName();
      log.info("Downloading artifact of repo {} group {} artifact {} and version {}", repositoryName, groupId,
          artifactId, version);
      final String url =
          getIndexContentPathUrl(nexusConfig, repositoryName, getGroupId(groupId) + artifactId + "/" + version + "/");
      log.info("Index Content Url {}", url);
      final Response<IndexBrowserTreeViewResponse> response =
          getIndexBrowserTreeViewResponseResponse(getRestClient(nexusConfig), nexusConfig, url);
      String extension;
      String classifier;
      if (isSuccessful(response)) {
        if (isNotEmpty(artifactStreamAttributes.getExtension())
            && artifactStreamAttributes.getExtension().contains("${")) {
          extension = artifactMetadata.get(ArtifactMetadataKeys.extension);
        } else {
          extension = artifactStreamAttributes.getExtension();
        }
        if (isNotEmpty(artifactStreamAttributes.getClassifier())
            && artifactStreamAttributes.getClassifier().contains("${")) {
          classifier = artifactMetadata.get(ArtifactMetadataKeys.classifier);
        } else {
          classifier = artifactStreamAttributes.getClassifier();
        }
        return getUrlInputStream(nexusConfig, response.body().getData().getChildren(), delegateId, taskId, accountId,
            notifyResponseData, extension, classifier);
      }
    } else if (repositoryFormat.equals(RepositoryFormat.nuget.name())) {
      final String packageName = artifactStreamAttributes.getNexusPackageName().contains("${")
          ? artifactMetadata.get(ArtifactMetadataKeys.nexusPackageName)
          : artifactStreamAttributes.getNexusPackageName();
      log.info("Retrieving artifacts of NuGet Repository {}, Package {} of Version {}", repositoryName, packageName,
          version);
      Call<ContentListResourceResponse> request;
      if (nexusConfig.isHasCredentials()) {
        request = getRestClient(nexusConfig)
                      .getRepositoryContents(
                          Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
                          repositoryName, packageName + "/" + version);
      } else {
        request = getRestClient(nexusConfig)
                      .getRepositoryContentsWithoutCredentials(repositoryName, packageName + "/" + version);
      }
      final Response<ContentListResourceResponse> response = request.execute();
      if (isSuccessful(response)) {
        response.body().getData().forEach(content -> {
          final String artifactName = content.getText();
          if (artifactName.endsWith("pom") || artifactName.endsWith("md5") || artifactName.endsWith("sha1")) {
            return;
          }
          final String artifactUrl = content.getResourceURI();
          log.info("Artifact Download Url {}", artifactUrl);
          downloadArtifactByUrl(
              nexusConfig, delegateId, taskId, accountId, notifyResponseData, artifactName, artifactUrl);
        });
      }
    } else if (repositoryFormat.equals(RepositoryFormat.npm.name())) {
      log.info("Retrieving artifacts of NPM Repository {}, Package {} of Version {}", repositoryName,
          artifactStreamAttributes.getNexusPackageName(), version);
      final String artifactUrl = artifactMetadata.get(ArtifactMetadataKeys.url);
      log.info("Artifact Download Url {}", artifactUrl);
      final String artifactName = artifactUrl.substring(artifactUrl.lastIndexOf('/') + 1);
      downloadArtifactByUrl(nexusConfig, delegateId, taskId, accountId, notifyResponseData, artifactName, artifactUrl);
    }
    return null;
  }

  private void downloadArtifactByUrl(NexusRequest nexusConfig, String delegateId, String taskId, String accountId,
      ListNotifyResponseData notifyResponseData, String artifactName, String artifactUrl) {
    Pair<String, InputStream> pair = downloadArtifactByUrl(nexusConfig, artifactName, artifactUrl);
    try {
      artifactCollectionCommonTaskHelper.addDataToResponse(
          pair, artifactUrl, notifyResponseData, delegateId, taskId, accountId);
    } catch (IOException e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private Pair<String, InputStream> getUrlInputStream(NexusRequest nexusConfig, List<IndexBrowserTreeNode> treeNodes,
      String delegateId, String taskId, String accountId, ListNotifyResponseData res, String extension,
      String classifier) {
    for (IndexBrowserTreeNode treeNode : treeNodes) {
      for (IndexBrowserTreeNode child : treeNode.getChildren()) {
        if (child.getType().equals("V")) {
          List<IndexBrowserTreeNode> artifacts = child.getChildren();
          if (artifacts != null) {
            for (IndexBrowserTreeNode artifact : artifacts) {
              String artifactName = artifact.getNodeName();
              if (!artifactName.endsWith("pom")) {
                String artifactUrl = constructArtifactDownloadUrl(nexusConfig, artifact, extension, classifier);
                if (log.isDebugEnabled()) {
                  log.debug("Artifact Url:" + artifactUrl);
                }
                if (isNotEmpty(extension)) {
                  int index = artifactName.lastIndexOf('.');
                  // to avoid running into ArrayIndexOutOfBoundsException
                  if (index >= 0 && index < artifactName.length() - 1) {
                    artifactName = artifactName.replace(artifactName.substring(index + 1), extension);
                  }
                }
                downloadArtifactByUrl(nexusConfig, delegateId, taskId, accountId, res, artifactName, artifactUrl);
              }
            }
          }
        }
      }
    }
    return null;
  }

  @NotNull
  private String constructArtifactDownloadUrl(
      NexusRequest nexusConfig, IndexBrowserTreeNode artifact, String extension, String classifier) {
    StringBuilder artifactUrl = new StringBuilder(getBaseUrl(nexusConfig));
    artifactUrl.append("service/local/artifact/maven/content?r=")
        .append(artifact.getRepositoryId())
        .append("&g=")
        .append(artifact.getGroupId())
        .append("&a=")
        .append(artifact.getArtifactId())
        .append("&v=")
        .append(artifact.getVersion());
    if (isNotEmpty(extension) || isNotEmpty(classifier)) {
      if (isNotEmpty(artifact.getPackaging())) { // currently we are honoring the packaging specified in pom.xml
        artifactUrl.append("&p=").append(artifact.getPackaging());
      }
      if (isNotEmpty(extension)) {
        artifactUrl.append("&e=").append(extension);
      }
      if (isNotEmpty(classifier)) {
        artifactUrl.append("&c=").append(classifier);
      }
    } else {
      if (isNotEmpty(artifact.getPackaging())) {
        artifactUrl.append("&p=").append(artifact.getPackaging());
      }
      if (isNotEmpty(artifact.getExtension())) {
        artifactUrl.append("&e=").append(artifact.getExtension());
      }
      if (isNotEmpty(artifact.getClassifier())) {
        artifactUrl.append("&c=").append(artifact.getClassifier());
      }
    }
    return artifactUrl.toString();
  }

  private Project getPomModel(
      NexusRequest nexusConfig, String repoType, String groupId, String artifactName, String version) {
    String url = getBaseUrl(nexusConfig) + "service/local/artifact/maven";
    Map<String, String> queryParams = new LinkedHashMap<>();
    queryParams.put("r", repoType);
    queryParams.put("g", groupId);
    queryParams.put("a", artifactName);
    queryParams.put("v", isBlank(version) ? "LATEST" : version);
    Call<Project> request;

    if (nexusConfig.isHasCredentials()) {
      request = getRestClient(nexusConfig)
                    .getPomModel(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())),
                        url, queryParams);
    } else {
      request = getRestClient(nexusConfig).getPomModel(url, queryParams);
    }
    try {
      final Response<Project> response = request.execute();
      if (isSuccessful(response)) {
        return response.body();
      } else {
        log.error("Error while getting the latest version from Nexus url {} and queryParams {}. Reason:{}", url,
            queryParams, response.message());
        throw new InvalidRequestException(response.message());
      }
    } catch (IOException e) {
      log.error("Error occurred while retrieving pom model from url " + url, e);
    }
    return new Project();
  }

  private Response<IndexBrowserTreeViewResponse> getIndexBrowserTreeViewResponseResponse(
      NexusRestClient nexusRestClient, NexusRequest nexusConfig, String url) throws IOException {
    Call<IndexBrowserTreeViewResponse> request;
    if (nexusConfig.isHasCredentials()) {
      request = nexusRestClient.getIndexContentByUrl(
          Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())), url);
    } else {
      request = nexusRestClient.getIndexContentByUrl(url);
    }
    return request.execute();
  }

  private String getIndexContentPathUrl(NexusRequest nexusConfig, String repoId, String path) {
    return getBaseUrl(nexusConfig) + "service/local/repositories/" + repoId + "/index_content" + path;
  }

  private List<FolderPath> getFolderPaths(
      NexusRestClient nexusRestClient, NexusRequest nexusConfig, String repoKey, String repoPath) {
    // Add first level paths
    List<FolderPath> folderPaths = new ArrayList<>();
    try {
      Response<IndexBrowserTreeViewResponse> response;
      if (isEmpty(repoPath)) {
        final Call<IndexBrowserTreeViewResponse> request;
        if (nexusConfig.isHasCredentials()) {
          request = nexusRestClient.getIndexContent(
              Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())), repoKey);
        } else {
          request = nexusRestClient.getIndexContent(repoKey);
        }
        response = request.execute();
      } else {
        response = getIndexBrowserTreeViewResponseResponse(
            nexusRestClient, nexusConfig, getIndexContentPathUrl(nexusConfig, repoKey, repoPath));
      }
      // for the very first call, if the response is a 403 we want to throw exception
      if (response.code() == 403 && repoPath.equals("")) {
        isSuccessful(response);
      }
      // for all other calls, parse response only if its not a 403
      if (response.code() != 403 && isSuccessful(response)) {
        List<IndexBrowserTreeNode> treeNodes = response.body().getData().getChildren();
        if (treeNodes != null) {
          treeNodes.forEach(treeNode -> {
            if (treeNode.getType().equals("G")) {
              folderPaths.add(FolderPath.builder().repo(repoKey).path(treeNode.getPath()).folder(true).build());
            } else {
              folderPaths.add(FolderPath.builder().repo(repoKey).path(treeNode.getPath()).folder(false).build());
            }
          });
        }
      }
    } catch (final IOException e) {
      throw new InvalidRequestException("Error occurred while retrieving Repository Group Ids from Nexus server "
              + nexusConfig.getNexusUrl() + " for repository " + repoKey + " under path " + repoPath,
          e);
    }
    return folderPaths;
  }

  private String getGroupId(String path) {
    return "/" + path.replace(".", "/") + "/";
  }

  private String getGroupId(List<String> pathElems) {
    StringBuilder groupIdBuilder = new StringBuilder();
    for (int i = 0; i < pathElems.size(); i++) {
      groupIdBuilder.append(pathElems.get(i));
      if (i != pathElems.size() - 1) {
        groupIdBuilder.append('.');
      }
    }
    return groupIdBuilder.toString();
  }

  private NexusRestClient getRestClient(final NexusRequest nexusConfig) {
    return getRetrofit(nexusConfig, SimpleXmlConverterFactory.createNonStrict()).create(NexusRestClient.class);
  }

  private NexusRestClient getRestClientJacksonConverter(final NexusRequest nexusConfig) {
    return getRetrofit(nexusConfig, JacksonConverterFactory.create()).create(NexusRestClient.class);
  }

  @SuppressWarnings({"squid:S3510"})
  public Pair<String, InputStream> downloadArtifactByUrl(
      NexusRequest nexusConfig, String artifactName, String artifactUrl) {
    String credentials = null;
    try {
      if (nexusConfig.isHasCredentials()) {
        log.info("Artifact: {}, ArtifactUrl: {}, Username: {}", artifactName, artifactUrl, nexusConfig.getUsername());
        credentials = Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword()));
      }

      URL url = new URL(artifactUrl);
      URLConnection conn = url.openConnection();
      if (credentials != null) {
        conn.setRequestProperty("Authorization", credentials);
      }
      if (conn instanceof HttpsURLConnection) {
        HttpsURLConnection conn1 = (HttpsURLConnection) conn;
        conn1.setHostnameVerifier((hostname, session) -> true);
        conn1.setSSLSocketFactory(Http.getSslContext().getSocketFactory());
        return ImmutablePair.of(artifactName, conn1.getInputStream());
      } else {
        return ImmutablePair.of(artifactName, conn.getInputStream());
      }
    } catch (IOException ex) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(ex), ex);
    }
  }

  public long getFileSize(NexusRequest nexusConfig, String artifactName, String artifactUrl) {
    log.info("Getting file size for artifact at path {}", artifactUrl);
    long size;
    Pair<String, InputStream> pair = downloadArtifactByUrl(nexusConfig, artifactName, artifactUrl);
    if (pair == null) {
      throw new InvalidArtifactServerException(format("Failed to get file size for artifact: %s", artifactUrl));
    }
    try {
      size = StreamUtils.getInputStreamSize(pair.getRight());
      pair.getRight().close();
    } catch (IOException e) {
      throw new InvalidArtifactServerException(ExceptionUtils.getMessage(e), e);
    }
    log.info(format("Computed file size [%d] bytes for artifact Path: %s", size, artifactUrl));
    return size;
  }
}
