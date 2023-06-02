/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.persistence.artifact.Artifact.Status;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactView;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ContentStatus;
import software.wings.persistence.artifact.ArtifactFile;
import software.wings.service.intfc.ownership.OwnedByArtifactStream;

import dev.morphia.query.Query;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * The Interface ArtifactService.
 */
@OwnedBy(CDC)
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public interface ArtifactService extends OwnedByArtifactStream {
  /**
   * List artifacts sorted by build no.
   *
   * @param pageRequest  the page request
   * @return the page response
   */
  PageResponse<Artifact> listArtifactsForService(PageRequest<Artifact> pageRequest);

  /***
   * List artifacts sorted by build no.
   * @param appId
   * @param serviceId
   * @param pageRequest
   * @return
   */
  PageResponse<Artifact> listArtifactsForService(
      @NotEmpty String appId, String serviceId, @NotNull PageRequest<Artifact> pageRequest);

  // List artifacts for artifact streams having collection enabled, sorted by build no.
  PageResponse<Artifact> listArtifactsForServiceWithCollectionEnabled(
      @NotEmpty String appId, String serviceId, @NotNull PageRequest<Artifact> pageRequest);

  /***
   * List artifacts sorted by build no.
   * @param serviceId
   * @param pageRequest
   * @return
   */
  PageResponse<Artifact> listArtifactsForService(String serviceId, @NotNull PageRequest<Artifact> pageRequest);

  /**
   * Creates the artifact and validates artifact type
   *
   * @param artifact the artifact
   * @return the artifact
   */
  Artifact create(@Valid Artifact artifact);

  Artifact create(Artifact artifact, boolean skipDuplicateCheck);

  Artifact create(Artifact artifact, ArtifactStream artifactStream, boolean skipDuplicateCheck);

  /**
   * Update.
   *
   * @param artifact the artifact
   * @return the artifact
   */
  Artifact update(@Valid Artifact artifact);

  void updateMetadataAndRevision(String artifactId, String accountId, Map<String, String> newMetadata, String revision);

  /**
   * Update status.
   *
   * @param artifactId the artifact id
   * @param accountId  the account id
   * @param status     the status
   */
  void updateStatus(String artifactId, String accountId, Status status);

  /**
   * Update status.
   *
   * @param artifactId the artifact id
   * @param accountId  the account id
   * @param status     the status
   */
  void updateStatus(String artifactId, String accountId, Status status, String errorMessage);

  /***
   * Update status and content status
   * @param artifactId
   * @param accountId
   * @param status
   * @param contentStatus
   */
  void updateStatus(String artifactId, String accountId, Status status, ContentStatus contentStatus);

  /***
   * Update status
   * @param artifactId
   * @param accountId
   * @param status
   * @param contentStatus
   * @param errorMessage
   */
  void updateStatus(
      String artifactId, String accountId, Status status, ContentStatus contentStatus, String errorMessage);

  /**
   * Update artifact source name
   * @param artifactStream
   */
  void updateArtifactSourceName(ArtifactStream artifactStream);

  void updateLastUpdatedAt(String artifactId, String accountId);

  /**
   * Adds the artifact file.
   *
   * @param artifactId    the artifact id
   * @param accountId     the account id
   * @param artifactFiles the artifact files
   */
  void addArtifactFile(String artifactId, String accountId, List<ArtifactFile> artifactFiles);

  /**
   * Download.
   *
   * @param accountId  the account id
   * @param artifactId the artifact id
   * @return the file
   */
  File download(String accountId, String artifactId);

  /**
   * Gets artifact.
   *
   * @param artifactId the artifact id
   * @return the artifact
   */
  Artifact get(String artifactId);

  /**
   * Gets artifact.
   *
   * @param accountId  the account id
   * @param artifactId the artifact id
   * @return the artifact
   */
  Artifact get(String accountId, String artifactId);

  /**
   * Get artifact.
   *
   * @param artifactId   the artifact id
   * @param appId        the app id
   * @return the artifact
   */
  ArtifactView getWithServices(String artifactId, String appId);

  Artifact getWithSource(String artifactId);
  /**
   * Soft delete.
   *
   * @param appId      the app id
   * @param artifactId the artifact id
   * @return the artifact
   */
  boolean delete(String appId, String artifactId);

  /**
   * Soft delete a list of artifacts.
   *
   * @param artifacts the artifacts
   */
  void deleteArtifacts(List<Artifact> artifacts);

  Artifact fetchLatestArtifactForArtifactStream(ArtifactStream artifactStream);

  Artifact fetchLastCollectedApprovedArtifactForArtifactStream(ArtifactStream artifactStream);

  Artifact fetchLastCollectedApprovedArtifactSorted(ArtifactStream artifactStream);

  Artifact fetchLastCollectedArtifact(ArtifactStream artifactStream);

  Artifact getArtifactByBuildNumber(ArtifactStream artifactStream, String buildNumber, boolean regex);

  Artifact getArtifactByBuildNumber(String accountId, String artifactStreamId, String buildNumber, boolean regex);

  Artifact getArtifactByBuildNumberAndSourceName(
      ArtifactStream artifactStream, String buildNumber, boolean regex, String artifactSourceName);

  /**
   * Starts Artifact collection and returns
   * @param accountId
   * @param artifactId
   */
  Artifact startArtifactCollection(String accountId, String artifactId);

  /**
   * Gets content status if artifact does not have content status
   * @param artifact
   * @return
   */
  ContentStatus getArtifactContentStatus(Artifact artifact);

  /**
   * Delete by artifact stream.
   *
   * @param retentionSize the size of the artifacts to be retained
   */
  void deleteArtifacts(int retentionSize);

  boolean deleteArtifactsByUniqueKey(ArtifactStream artifactStream, ArtifactStreamAttributes artifactStreamAttributes,
      Collection<String> artifactKeys);

  Query<Artifact> prepareArtifactWithMetadataQuery(ArtifactStream artifactStream, boolean hitSecondary);

  Query<Artifact> prepareCleanupQuery(ArtifactStream artifactStream);

  void deleteWhenArtifactSourceNameChanged(ArtifactStream artifactStream);

  void deleteByArtifactStreamId(String appId, String artifactStreamId);

  List<Artifact> listByIds(String accountId, Collection<String> artifactIds);

  List<Artifact> listByAccountId(String accountId);

  List<Artifact> listByAppId(String appId);

  List<ArtifactFile> fetchArtifactFiles(String artifactId);

  List<Artifact> listArtifactsByArtifactStreamId(String accountId, String artifactStreamId);
  List<Artifact> listArtifactsByArtifactStreamId(String accountId, String artifactStreamId, String buildNo);
}
