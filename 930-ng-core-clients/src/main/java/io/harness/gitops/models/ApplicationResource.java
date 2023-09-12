/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.gitops.models;

import io.harness.data.structure.HarnessStringUtils;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApplicationResource {
  @JsonProperty("agentIdentifier") public String agentIdentifier;
  @JsonProperty("repoIdentifier") public String repoIdentifier;
  @JsonProperty("clusterIdentifier") public String clusterIdentifier;
  @JsonProperty("name") public String name;
  @JsonProperty("stale") public Boolean stale;
  @JsonProperty("app") public App app;

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class App {
    @JsonProperty("metadata") public ApplicationMetadata metadata;
    @JsonProperty("spec") public ApplicationSpec spec;
    @JsonProperty("status") public ApplicationStatus status;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ApplicationMetadata {
    @JsonProperty("name") public String name;
    @JsonProperty("namespace") public String namespace;
    @JsonProperty("ownerReferences") public List<OwnerReference> ownerReferences;
    @JsonProperty("labels") public Label labels;

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OwnerReference {
      @JsonProperty("apiVersion") public String apiVersion;
      @JsonProperty("kind") public String kind;
      @JsonProperty("name") public String name;
    }
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ApplicationSpec {
    @JsonProperty("source") public Source source;
    @JsonProperty("sources") public List<Source> sources;
    @JsonProperty("destination") public Destination destination;
    @JsonProperty("project") public String project;
    @JsonProperty("ignoreDifferences") public List<IgnoreDifferences> ignoreDifferences;
    @JsonProperty("info") public List<Info> info;
    @JsonProperty("syncPolicy") public SyncPolicy syncPolicy;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Source {
    @JsonProperty("chart") public String chart;
    @JsonProperty("directory") public Directory directory;
    @JsonProperty("plugin") public Plugin plugin;
    @JsonProperty("ref") public String ref;
    @JsonProperty("repoURL") public String repoURL;
    @JsonProperty("path") public String path;
    @JsonProperty("targetRevision") public String targetRevision;
    @JsonProperty("helm") HelmSource helm;
    @JsonProperty("kustomize") KustomizeSource kustomize;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HelmSource {
    @JsonProperty("fileParameters") List<HelmSourceFileParameters> fileParameters;
    @JsonProperty("ignoreMissingValueFiles") boolean ignoreMissingValueFiles;
    @JsonProperty("parameters") List<HelmSourceParameters> parameters;
    @JsonProperty("passCredentials") boolean passCredentials;
    @JsonProperty("releaseName") String releaseName;
    @JsonProperty("skipCrds") boolean skipCrds;
    @JsonProperty("valueFiles") List<String> valueFiles;
    @JsonProperty("values") String values;
    @JsonProperty("valuesObject") Object valuesObject;
    @JsonProperty("version") String version;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HelmSourceParameters {
    @JsonProperty("name") String name;
    @JsonProperty("value") String value;
    @JsonProperty("forceString") boolean forceString;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class HelmSourceFileParameters {
    @JsonProperty("name") String name;
    @JsonProperty("path") String path;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class KustomizeSource {
    @JsonProperty("commonAnnotations") Object commonAnnotations;
    @JsonProperty("commonAnnotationsEnvsubst") boolean commonAnnotationsEnvsubst;
    @JsonProperty("commonLabels") Object commonLabels;
    @JsonProperty("forceCommonAnnotations") boolean forceCommonAnnotations;
    @JsonProperty("forceCommonLabels") boolean forceCommonLabels;
    @JsonProperty("images") List<String> images;
    @JsonProperty("namePrefix") String namePrefix;
    @JsonProperty("nameSuffix") String nameSuffix;
    @JsonProperty("namespace") String namespace;
    @JsonProperty("replicas") List<Replicas> replicas;
    @JsonProperty("version") String version;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Replicas {
    @JsonProperty("count") String count;
    @JsonProperty("name") String name;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Directory {
    @JsonProperty("exclude") String exclude;
    @JsonProperty("include") String include;
    @JsonProperty("jsonnet") Jsonnet jsonnet;
    @JsonProperty("recurse") boolean recurse;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Jsonnet {
    @JsonProperty("extVars") List<JsonnetItems> extVars;
    @JsonProperty("libs") List<String> libs;
    @JsonProperty("tlas") List<JsonnetItems> tlas;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class JsonnetItems {
    @JsonProperty("code") boolean code;
    @JsonProperty("name") String name;
    @JsonProperty("value") String value;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Plugin {
    @JsonProperty("env") List<Env> env;
    @JsonProperty("name") String name;
    @JsonProperty("parameters") List<PluginParameters> parameters;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Env {
    @JsonProperty("name") String name;
    @JsonProperty("value") String value;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class PluginParameters {
    @JsonProperty("array") List<String> array;
    @JsonProperty("map") Object map;
    @JsonProperty("name") String name;
    @JsonProperty("string") String string;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Destination {
    @JsonProperty("server") public String server;
    @JsonProperty("namespace") public String namespace;
    @JsonProperty("name") public String name;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SyncPolicy {
    @JsonProperty("automated") public Automated automated;

    @Data
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Automated {}
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class ApplicationStatus {
    @JsonProperty("resources") public List<Resource> resources;
    @JsonProperty("sync") public Sync sync;
    @JsonProperty("health") public Health health;
    @JsonProperty("operationState") public OperationState operationState;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Sync {
    @JsonProperty("status") public String status;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class OperationState {
    @JsonProperty("phase") public String phase;
    @JsonProperty("message") public String message;
    @JsonProperty("syncResult") public SyncResult syncResult;
    @JsonProperty("startedAt") public String startedAt;
    @JsonProperty("finishedAt") public String finishedAt;
    @JsonProperty("startedAtTs") public String startedAtTs;
    @JsonProperty("finishedAtTs") public String finishedAtTs;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Health {
    @JsonProperty("status") public String status;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Resource {
    @JsonProperty("group") public String group;
    @JsonProperty("version") public String version;
    @JsonProperty("kind") public String kind;
    @JsonProperty("namespace") public String namespace;
    @JsonProperty("name") public String name;
    @JsonProperty("status") public String status;
    @JsonProperty("health") public Health health;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class SyncResult {
    @JsonProperty("resources") public List<Resource> resources;
    @JsonProperty("revision") public String revision;
    @JsonProperty("source") public Source source;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Label {
    @JsonProperty("harness.io/serviceRef") public String serviceRef;
    @JsonProperty("harness.io/envRef") public String envRef;
    @JsonProperty("harness.io/buildRef") public String buildRef;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class IgnoreDifferences {
    @JsonProperty("group") public String group;
    @JsonProperty("kind") public String kind;
    @JsonProperty("name") public String name;
    @JsonProperty("namespace") public String namespace;
    @JsonProperty("jsonPointers") public List<String> jsonPointers;
    @JsonProperty("jqPathExpressions") public List<String> jqPathExpressions;
    @JsonProperty("managedFieldsManagers") public List<String> managedFieldsManagers;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Info {
    @JsonProperty("name") public String name;
    @JsonProperty("value") public String value;
  }

  public String getSyncOperationPhase() {
    if (getApp().getStatus().getOperationState() == null) {
      return "";
    }
    return getApp().getStatus().getOperationState().getPhase();
  }

  public ApplicationResource.SyncPolicy getSyncPolicy() {
    return getApp().getSpec().getSyncPolicy();
  }

  public Instant getLastSyncStartedAt() {
    if (getApp().getStatus().getOperationState() == null
        || getApp().getStatus().getOperationState().getStartedAt() == null) {
      return null;
    }
    return Instant.parse(getApp().getStatus().getOperationState().getStartedAt());
  }

  public String getHealthStatus() {
    return getApp().getStatus().getHealth().getStatus();
  }

  public String getSyncMessage() {
    if (getApp().getStatus().getOperationState() == null) {
      return "";
    }
    return getApp().getStatus().getOperationState().getMessage();
  }

  public String getTargetRevision() {
    return getApp().getSpec().getSource().getTargetRevision();
  }

  public List<ApplicationResource.Resource> getResources() {
    return getApp().getStatus().getResources();
  }

  public String getEnvironmentRef() {
    return getLabels() == null ? "" : HarnessStringUtils.emptyIfNull(getLabels().getEnvRef());
  }

  public String getServiceRef() {
    return getLabels() == null ? "" : HarnessStringUtils.emptyIfNull(getLabels().getServiceRef());
  }

  public Label getLabels() {
    return getApp().getMetadata().getLabels();
  }
}
