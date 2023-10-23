/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.nexus;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.nexus.NexusHelper.getBaseUrl;
import static io.harness.nexus.NexusHelper.isSuccessful;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.beans.BuildDetailsInternal;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.artifact.ArtifactFileMetadataInternal;
import io.harness.exception.HintException;
import io.harness.exception.InvalidArtifactServerException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.NexusRegistryException;
import io.harness.exception.WingsException;
import io.harness.network.Http;
import io.harness.nexus.model.IndexBrowserTreeNode;
import io.harness.nexus.model.IndexBrowserTreeViewResponse;

import software.wings.beans.artifact.ArtifactMetadataKeys;
import software.wings.common.AlphanumComparator;
import software.wings.utils.RepositoryFormat;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import org.jetbrains.annotations.NotNull;
import org.sonatype.nexus.rest.model.ContentListResource;
import org.sonatype.nexus.rest.model.ContentListResourceResponse;
import org.sonatype.nexus.rest.model.RepositoryListResource;
import org.sonatype.nexus.rest.model.RepositoryListResourceResponse;
import retrofit2.Call;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.converter.jaxb.JaxbConverterFactory;

@OwnedBy(CDC)
@Slf4j
public class NexusTwoClientImpl {
  public static Retrofit getRetrofit(NexusRequest nexusConfig, Converter.Factory converterFactory) {
    String baseUrl = NexusHelper.getBaseUrl(nexusConfig);
    return new Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(converterFactory)
        .client(Http.getOkHttpClient(baseUrl, nexusConfig.isCertValidationRequired()))
        .build();
  }

  public Map<String, String> getRepositories(NexusRequest nexusConfig, String repositoryFormat) throws IOException {
    log.info("Retrieving repositories");
    final Call<RepositoryListResourceResponse> request;
    if (nexusConfig.isHasCredentials()) {
      request =
          getRestClient(nexusConfig)
              .getAllRepositories(Credentials.basic(nexusConfig.getUsername(), new String(nexusConfig.getPassword())));
    } else {
      request = getRestClient(nexusConfig).getAllRepositories();
    }

    final Response<RepositoryListResourceResponse> response = request.execute();
    if (response.code() == 404) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Check if the Nexus URL & Nexus version are correct. Nexus URLs are different for different Nexus versions",
          "The Nexus URL or the version for the ConnectorDisconnectHandler is incorrect",
          new InvalidArtifactServerException("Invalid Nexus ConnectorDisconnectHandler details", USER));
    }
    if (isSuccessful(response)) {
      log.info("Retrieving repositories success");
      if (RepositoryFormat.maven.name().equals(repositoryFormat)) {
        return response.body()
            .getData()
            .stream()
            .filter(repositoryListResource -> "maven2".equals(repositoryListResource.getFormat()))
            .collect(toMap(RepositoryListResource::getId, RepositoryListResource::getName));
      } else if (RepositoryFormat.nuget.name().equals(repositoryFormat)
          || RepositoryFormat.npm.name().equals(repositoryFormat)) {
        return response.body()
            .getData()
            .stream()
            .filter(repositoryListResource -> repositoryFormat.equals(repositoryListResource.getFormat()))
            .collect(toMap(RepositoryListResource::getId, RepositoryListResource::getName));
      }
      return response.body().getData().stream().collect(
          toMap(RepositoryListResource::getId, RepositoryListResource::getName));
    }
    log.info("No repositories found returning empty map");
    return emptyMap();
  }

  private NexusRestClient getRestClient(NexusRequest nexusConfig) {
    return NexusHelper.getRetrofit(nexusConfig, JaxbConverterFactory.create()).create(NexusRestClient.class);
  }

  public List<BuildDetailsInternal> getVersions(NexusRequest nexusConfig, String repoId, String groupId,
      String artifactName, String extension, String classifier) {
    try {
      log.info("Retrieving versions for repoId {} groupId {} and artifactName {}", repoId, groupId, artifactName);
      String url = getIndexContentPathUrl(nexusConfig, repoId, getGroupId(groupId)) + artifactName + "/";
      final Response<IndexBrowserTreeViewResponse> response =
          getIndexBrowserTreeViewResponseResponse(getRestClient(nexusConfig), nexusConfig, url);
      List<String> versions = new ArrayList<>();
      Map<String, String> versionToArtifactUrls = new HashMap<>();
      Map<String, List<ArtifactFileMetadataInternal>> versionToArtifactDownloadUrls = new HashMap<>();
      if (isSuccessful(response)) {
        final List<IndexBrowserTreeNode> treeNodes = response.body().getData().getChildren();
        if (treeNodes != null) {
          existsVersion(treeNodes, nexusConfig, repoId, groupId, artifactName, extension, classifier);
          for (IndexBrowserTreeNode treeNode : treeNodes) {
            if (treeNode.getType().equals("A")) {
              List<IndexBrowserTreeNode> children = treeNode.getChildren();
              for (IndexBrowserTreeNode child : children) {
                if (child.getType().equals("V")) {
                  versions.add(child.getNodeName());
                  List<ArtifactFileMetadataInternal> artifactFileMetadata =
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
      return constructBuildDetails(repoId, groupId, artifactName, versions, versionToArtifactUrls,
          versionToArtifactDownloadUrls, extension, classifier);
    } catch (IOException | NexusRegistryException e) {
      throw NestedExceptionUtils.hintWithExplanationException(
          "Please check Nexus artifact configuration and verify that repository is valid.",
          String.format("Failed to retrieve artifact '%s'", artifactName), new NexusRegistryException(e.getMessage()));
    }
  }

  public boolean existsVersion(List<IndexBrowserTreeNode> treeNodes, NexusRequest nexusConfig, String repoId,
      String groupId, String artifactName, String extension, String classifier) throws IOException {
    if (isEmpty(extension) && isEmpty(classifier)) {
      return true;
    }
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
        throw new HintException(HintException.HINT_NEXUS_ISSUE,
            new InvalidRequestException("No versions found with specified extension/classifier", USER));
      }
    }
    return true;
  }

  public List<BuildDetailsInternal> constructBuildDetails(String repoId, String groupId, String artifactName,
      List<String> versions, Map<String, String> versionToArtifactUrls,
      Map<String, List<ArtifactFileMetadataInternal>> versionToArtifactDownloadUrls, String extension,
      String classifier) {
    log.info("Versions come from nexus server {}", versions);
    versions = versions.stream().sorted(new AlphanumComparator()).collect(toList());
    log.info("After sorting alphanumerically versions {}", versions);

    return versions.stream()
        .map(version -> {
          Map<String, String> metadata = new HashMap<>();
          metadata.put(ArtifactMetadataKeys.repositoryName, repoId);
          metadata.put(ArtifactMetadataKeys.nexusGroupId, groupId);
          metadata.put(ArtifactMetadataKeys.nexusArtifactId, artifactName);
          metadata.put(ArtifactMetadataKeys.version, version);
          if (isNotEmpty(extension)) {
            metadata.put(ArtifactMetadataKeys.extension, extension);
          }
          if (isNotEmpty(classifier)) {
            metadata.put(ArtifactMetadataKeys.classifier, classifier);
          }
          return BuildDetailsInternal.builder()
              .number(version)
              .revision(version)
              .buildUrl(versionToArtifactUrls.get(version))
              .metadata(metadata)
              .uiDisplayName("Version# " + version)
              .artifactFileMetadataList(versionToArtifactDownloadUrls.get(version))
              .build();
        })
        .collect(toList());
  }

  private List<ArtifactFileMetadataInternal> constructArtifactDownloadUrls(
      NexusRequest nexusConfig, IndexBrowserTreeNode child, String extension, String classifier) {
    List<ArtifactFileMetadataInternal> artifactUrls = new ArrayList<>();
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
                  artifactUrls.add(
                      ArtifactFileMetadataInternal.builder().fileName(artifactName).url(artifactUrl).build());
                }
              }
            }
          }
        }
      }
    }
    return artifactUrls;
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

  public List<BuildDetailsInternal> getVersions(String repositoryFormat, NexusRequest nexusConfig, String repositoryId,
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

  public BuildDetailsInternal getVersion(String repositoryFormat, NexusRequest nexusConfig, String repositoryId,
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

  private BuildDetailsInternal getVersionForNuGet(
      NexusRequest nexusConfig, String repositoryId, String packageName, String version) {
    try {
      List<ArtifactFileMetadataInternal> artifactFileMetadata =
          getArtifactDownloadMetadataForVersionForNuGet(nexusConfig, repositoryId, packageName, version);
      if (isNotEmpty(artifactFileMetadata)) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(ArtifactMetadataKeys.repositoryName, repositoryId);
        metadata.put(ArtifactMetadataKeys.nexusPackageName, packageName);
        metadata.put(ArtifactMetadataKeys.version, version);
        return BuildDetailsInternal.builder()
            .number(version)
            .revision(version)
            .buildUrl(artifactFileMetadata.get(0).getUrl())
            .metadata(metadata)
            .uiDisplayName("Version# " + version)
            .artifactFileMetadataList(artifactFileMetadata)
            .build();
      }
    } catch (IOException e) {
      log.error("Failed in getting artifact download urls", e);
    }
    return null;
  }

  private List<ArtifactFileMetadataInternal> getArtifactDownloadMetadataForVersionForNuGet(
      NexusRequest nexusConfig, String repositoryName, String packageName, String version) throws IOException {
    List<ArtifactFileMetadataInternal> artifactFileMetadata = new ArrayList<>();
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
        artifactFileMetadata.add(
            ArtifactFileMetadataInternal.builder().fileName(artifactName).url(artifactUrl).build());
      });
    }
    return artifactFileMetadata;
  }

  @NotNull
  private List<BuildDetailsInternal> getVersionsForNPM(
      NexusRequest nexusConfig, String repositoryId, String packageName) throws IOException {
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
    Map<String, List<ArtifactFileMetadataInternal>> versionToArtifactDownloadUrls = new HashMap<>();

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
              asList(ArtifactFileMetadataInternal.builder().fileName(artifactName).url(artifactUrl).build()));
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
          return BuildDetailsInternal.builder()
              .number(version)
              .revision(version)
              .buildUrl(versionToArtifactUrls.get(version))
              .metadata(metadata)
              .uiDisplayName("Version# " + version)
              .artifactFileMetadataList(versionToArtifactDownloadUrls.get(version))
              .build();
        })
        .collect(toList());
  }

  private BuildDetailsInternal getVersionForNPM(
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
      return BuildDetailsInternal.builder()
          .number(version)
          .revision(version)
          .buildUrl(artifactUrl)
          .metadata(metadata)
          .uiDisplayName("Version# " + version)
          .artifactFileMetadataList(
              asList(ArtifactFileMetadataInternal.builder().fileName(artifactName).url(artifactUrl).build()))
          .build();
    }
    return null;
  }

  private NexusRestClient getRestClientJacksonConverter(final NexusRequest nexusConfig) {
    return getRetrofit(nexusConfig, JacksonConverterFactory.create()).create(NexusRestClient.class);
  }
  @NotNull
  private List<BuildDetailsInternal> getVersionsForNuGet(NexusRequest nexusConfig, String repositoryId,
      String packageName, Set<String> collectedBuilds) throws IOException {
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
    Map<String, List<ArtifactFileMetadataInternal>> versionToArtifactDownloadUrls = new HashMap<>();
    if (isSuccessful(response)) {
      response.body().getData().forEach(content -> {
        versions.add(content.getText());
        try {
          // We skip the URL fetching for versions that are already collected
          if (EmptyPredicate.isEmpty(collectedBuilds) || !collectedBuilds.contains(content.getText())) {
            List<ArtifactFileMetadataInternal> artifactFileMetadata = getArtifactDownloadMetadataForVersionForNuGet(
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
          return BuildDetailsInternal.builder()
              .number(version)
              .revision(version)
              .buildUrl(versionToArtifactUrls.get(version))
              .metadata(metadata)
              .uiDisplayName("Version# " + version)
              .artifactFileMetadataList(versionToArtifactDownloadUrls.get(version))
              .build();
        })
        .collect(toList());
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
  private String getGroupId(String path) {
    return "/" + path.replace(".", "/") + "/";
  }
}
