/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.ARTIFACT_STREAM_METADATA_ONLY;
import static io.harness.beans.FeatureName.SPG_ALLOW_FILTER_BY_PATHS_GCS;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.delegate.beans.FileBucket.ARTIFACTS;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HPersistence.DEFAULT_STORE;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.Base.CREATED_AT_KEY;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;
import static software.wings.collect.CollectEvent.Builder.aCollectEvent;
import static software.wings.persistence.artifact.Artifact.ContentStatus.DELETED;
import static software.wings.persistence.artifact.Artifact.ContentStatus.DOWNLOADED;
import static software.wings.persistence.artifact.Artifact.ContentStatus.DOWNLOADING;
import static software.wings.persistence.artifact.Artifact.ContentStatus.METADATA_ONLY;
import static software.wings.persistence.artifact.Artifact.ContentStatus.NOT_DOWNLOADED;
import static software.wings.persistence.artifact.Artifact.Status.APPROVED;
import static software.wings.persistence.artifact.Artifact.Status.ERROR;
import static software.wings.persistence.artifact.Artifact.Status.FAILED;
import static software.wings.persistence.artifact.Artifact.Status.QUEUED;
import static software.wings.persistence.artifact.Artifact.Status.READY;
import static software.wings.persistence.artifact.Artifact.Status.REJECTED;
import static software.wings.persistence.artifact.Artifact.Status.RUNNING;
import static software.wings.persistence.artifact.Artifact.Status.WAITING;
import static software.wings.service.impl.artifact.ArtifactCollectionUtils.getArtifactKeyFn;
import static software.wings.utils.ArtifactType.DOCKER;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HIterator;
import io.harness.queue.QueuePublisher;
import io.harness.validation.Create;
import io.harness.validation.Update;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.ArtifactView;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.collect.CollectEvent;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;
import software.wings.persistence.artifact.Artifact.ContentStatus;
import software.wings.persistence.artifact.Artifact.Status;
import software.wings.persistence.artifact.ArtifactFile;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.FileService;
import software.wings.service.intfc.SettingsService;
import software.wings.utils.ArtifactType;
import software.wings.utils.DelegateArtifactCollectionUtils;
import software.wings.utils.RepositoryFormat;
import software.wings.utils.RepositoryType;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterators;
import com.google.common.io.Files;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import com.mongodb.ReadPreference;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.executable.ValidateOnExecution;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

/**
 * The Class ArtifactServiceImpl.
 */
@OwnedBy(CDC)
@Singleton
@ValidateOnExecution
@Slf4j
public class ArtifactServiceImpl implements ArtifactService {
  public static final int DELETE_BATCH_SIZE = 100;
  /**
   * The Auto downloaded.
   */
  List<String> autoDownloaded =
      asList(ArtifactStreamType.DOCKER.name(), ArtifactStreamType.ECR.name(), ArtifactStreamType.GCR.name(),
          ArtifactStreamType.ACR.name(), ArtifactStreamType.AMAZON_S3.name(), ArtifactStreamType.AMI.name());

  private static final String DEFAULT_ARTIFACT_FILE_NAME = "ArtifactFile";

  public static final int ARTIFACT_RETENTION_SIZE = 25;

  @Inject private WingsPersistence wingsPersistence;
  @Inject private FileService fileService;
  @Inject private QueuePublisher<CollectEvent> collectQueue;
  @Inject private ArtifactStreamService artifactStreamService;
  @Inject private AppService appService;
  @Inject private ExecutorService executorService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;
  @Inject private SettingsService settingsService;
  @Inject private BuildSourceService buildSourceService;
  @Inject private FeatureFlagService featureFlagService;

  @Override
  public PageResponse<Artifact> listArtifactsForService(PageRequest<Artifact> pageRequest) {
    PageResponse<Artifact> pageResponse = wingsPersistence.query(Artifact.class, pageRequest);
    Map<String, List<Artifact>> groupByArtifactStream =
        pageResponse.getResponse().stream().collect(Collectors.groupingBy(Artifact::getArtifactStreamId));
    List<Artifact> artifacts = new ArrayList<>();
    for (Entry<String, List<Artifact>> artifactStreamEntry : groupByArtifactStream.entrySet()) {
      if (artifactStreamEntry.getKey() != null) {
        ArtifactStream artifactStream =
            Preconditions.checkNotNull(wingsPersistence.get(ArtifactStream.class, artifactStreamEntry.getKey()),
                "Artifact stream has been deleted");
        artifactStreamEntry.getValue().forEach(artifact -> artifact.setArtifactStreamName(artifactStream.getName()));
        if (featureFlagService.isEnabled(SPG_ALLOW_FILTER_BY_PATHS_GCS, artifactStream.getAccountId())
            && ArtifactStreamType.GCS.name().equals(artifactStream.getArtifactStreamType())) {
          artifacts.addAll(buildSourceService.listArtifactByArtifactStreamAndFilterPath(
              artifactStreamEntry.getValue().stream().collect(toList()), artifactStream));
        } else {
          artifacts.addAll(artifactStreamEntry.getValue().stream().collect(toList()));
        }
      }
    }
    pageResponse.setResponse(artifacts);
    return pageResponse;
  }

  @Override
  public PageResponse<Artifact> listArtifactsForService(
      String appId, String serviceId, PageRequest<Artifact> pageRequest) {
    if (GLOBAL_APP_ID.equals(appId)) {
      return listArtifactsForService(serviceId, pageRequest);
    }

    if (serviceId != null) {
      List<String> artifactStreamIds = artifactStreamServiceBindingService.listArtifactStreamIds(appId, serviceId);
      if (isNotEmpty(artifactStreamIds)) {
        pageRequest.addFilter(ArtifactKeys.artifactStreamId, IN, artifactStreamIds.toArray());
      } else {
        return aPageResponse().withResponse(new ArrayList<Artifact>()).build();
      }
    }

    return listArtifactsForService(pageRequest);
  }

  @Override
  public PageResponse<Artifact> listArtifactsForServiceWithCollectionEnabled(
      String appId, String serviceId, PageRequest<Artifact> pageRequest) {
    if (isEmpty(serviceId)) {
      throw new InvalidRequestException("ServiceId is required");
    }

    List<String> projections = new ArrayList<>();
    projections.add(ArtifactStreamKeys.uuid);
    projections.add(ArtifactStreamKeys.collectionEnabled);
    List<ArtifactStream> artifactStreams =
        artifactStreamService.getArtifactStreamsForService(appId, serviceId, projections);

    List<String> artifactStreamIds = new ArrayList<>();
    for (ArtifactStream artifactStream : artifactStreams) {
      if (!Boolean.FALSE.equals(artifactStream.getCollectionEnabled())) {
        artifactStreamIds.add(artifactStream.getUuid());
      }
    }

    if (isNotEmpty(artifactStreamIds)) {
      pageRequest.addFilter(ArtifactKeys.artifactStreamId, IN, artifactStreamIds.toArray());
      return listArtifactsForService(pageRequest);
    } else {
      return aPageResponse().withResponse(new ArrayList<Artifact>()).build();
    }
  }

  @Override
  public PageResponse<Artifact> listArtifactsForService(String serviceId, PageRequest<Artifact> pageRequest) {
    if (serviceId != null) {
      List<String> artifactStreamIds = artifactStreamServiceBindingService.listArtifactStreamIds(serviceId);
      if (isNotEmpty(artifactStreamIds)) {
        pageRequest.addFilter(ArtifactKeys.artifactStreamId, IN, artifactStreamIds.toArray());
      } else {
        return aPageResponse().withResponse(new ArrayList<Artifact>()).build();
      }
    }

    return listArtifactsForService(pageRequest);
  }

  @Override
  @ValidationGroups(Create.class)
  public Artifact create(@Valid Artifact artifact) {
    return create(artifact, false);
  }

  @Override
  public Artifact create(Artifact artifact, boolean skipDuplicateCheck) {
    return create(artifact, null, skipDuplicateCheck);
  }

  @Override
  public Artifact create(Artifact artifact, ArtifactStream concreteArtifactStream, boolean skipDuplicateCheck) {
    String appId = artifact.fetchAppId();
    if (appId != null && !appId.equals(GLOBAL_APP_ID) && !appService.exist(appId)) {
      throw new InvalidArgumentsException("App does not exist: " + appId, USER);
    }
    ArtifactStream artifactStream;
    if (concreteArtifactStream == null) {
      artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
    } else {
      artifactStream = concreteArtifactStream;
    }
    notNullCheck("Artifact Stream", artifactStream, USER);
    artifact.setArtifactSourceName(artifactStream.getSourceName());
    setAccountId(artifact);
    setArtifactStatus(artifact, artifactStream);
    artifact.setServiceIds(artifactStreamServiceBindingService.listServiceIds(artifactStream.getUuid()));

    if (!skipDuplicateCheck) {
      ArtifactStreamAttributes artifactStreamAttributes =
          artifactCollectionUtils.getArtifactStreamAttributes(artifactStream, false);
      Artifact savedArtifact = getArtifactByUniqueKey(artifactStream, artifactStreamAttributes, artifact);
      if (savedArtifact != null) {
        log.info(
            "Skipping creation of duplicate artifact for artifact stream: [{}], saved artifact: [{}] with status {} and build number {}",
            artifactStream.getUuid(), savedArtifact.getUuid(), savedArtifact.getStatus(), artifact.getBuildNo());
        savedArtifact.setDuplicate(true);
        return savedArtifact;
      }
      updateArtifactIdentity(artifactStream, artifactStreamAttributes, artifact);
    }

    String key = wingsPersistence.save(artifact);
    Artifact savedArtifact = wingsPersistence.get(Artifact.class, key);
    if (savedArtifact.getStatus() == QUEUED) {
      log.info("Sending event to collect artifact {} ", savedArtifact.getUuid());
      collectQueue.send(aCollectEvent().withArtifact(savedArtifact).build());
    }
    executorService.submit(() -> deleteArtifactsWithContents(ARTIFACT_RETENTION_SIZE, artifactStream));
    return savedArtifact;
  }

  private void updateArtifactIdentity(
      ArtifactStream artifactStream, ArtifactStreamAttributes artifactStreamAttributes, Artifact artifact) {
    StringBuilder stringBuilder = new StringBuilder();
    String artifactStreamType = artifactStream.getArtifactStreamType();
    Function<Artifact, String> keyFn = getArtifactKeyFn(artifactStreamType, artifactStreamAttributes);
    stringBuilder.append(keyFn.apply(artifact)).append('_').append(generateUuid());
    artifact.setBuildIdentity(stringBuilder.toString());
  }

  private Artifact getArtifactByUniqueKey(
      ArtifactStream artifactStream, ArtifactStreamAttributes artifactStreamAttributes, Artifact artifact) {
    Query<Artifact> artifactQuery = wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                                        .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid());
    String artifactStreamType = artifactStream.getArtifactStreamType();
    String key;
    String value;
    if (AMI.name().equals(artifactStreamType)) {
      key = ArtifactKeys.revision;
      value = artifact.getRevision();
    } else if (DelegateArtifactCollectionUtils.isGenericArtifactStream(artifactStreamType, artifactStreamAttributes)) {
      key = ArtifactKeys.metadata_artifactPath;
      value = artifact.getArtifactPath();
    } else {
      key = ArtifactKeys.metadata_buildNo;
      value = artifact.getBuildNo();
    }

    if (value == null) {
      return null;
    }
    return artifactQuery.filter(key, value).get();
  }

  @Override
  public boolean deleteArtifactsByUniqueKey(ArtifactStream artifactStream,
      ArtifactStreamAttributes artifactStreamAttributes, Collection<String> artifactKeys) {
    if (isEmpty(artifactKeys)) {
      return true;
    }

    Query<Artifact> artifactQuery = wingsPersistence.createQuery(Artifact.class)
                                        .filter(ArtifactKeys.accountId, artifactStream.getAccountId())
                                        .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid());
    String artifactStreamType = artifactStream.getArtifactStreamType();
    String key;
    if (AMI.name().equals(artifactStreamType)) {
      key = ArtifactKeys.revision;
    } else if (DelegateArtifactCollectionUtils.isGenericArtifactStream(artifactStreamType, artifactStreamAttributes)) {
      key = ArtifactKeys.metadata_artifactPath;
    } else {
      key = ArtifactKeys.metadata_buildNo;
    }

    return wingsPersistence.delete(artifactQuery.field(key).in(artifactKeys));
  }

  private void setAccountId(Artifact artifact) {
    if (isEmpty(artifact.getAccountId())) {
      if (artifact.fetchAppId() != null && !artifact.fetchAppId().equals(GLOBAL_APP_ID)) {
        artifact.setAccountId(appService.getAccountIdByAppId(artifact.fetchAppId()));
      } else {
        if (artifact.getSettingId() != null) {
          artifact.setAccountId(settingsService.fetchAccountIdBySettingId(artifact.getSettingId()));
        }
      }
    }
  }

  private void setArtifactStatus(Artifact artifact, ArtifactStream artifactStream) {
    if (metadataOnlyBehindFlag(featureFlagService, artifactStream.getAccountId(), artifactStream.isMetadataOnly())
        || autoDownloaded.contains(artifactStream.getArtifactStreamType())) {
      artifact.setContentStatus(METADATA_ONLY);
      artifact.setStatus(APPROVED);
      return;
    }

    String appId = artifact.fetchAppId();
    if (NEXUS.name().equals(artifactStream.getArtifactStreamType())) {
      if (appId != null && !appId.equals(GLOBAL_APP_ID)) {
        if (((NexusArtifactStream) artifactStream).getRepositoryType() == null) { // for backward compatibility
          artifact.setContentStatus(
              getArtifactType(appId, artifactStream.getUuid()) == DOCKER ? METADATA_ONLY : NOT_DOWNLOADED);
        } else if (((NexusArtifactStream) artifactStream).getRepositoryType().equals(RepositoryType.docker.name())) {
          artifact.setContentStatus(METADATA_ONLY);
        } else {
          artifact.setContentStatus(NOT_DOWNLOADED); // for nexus 3 - nuget and maven2
        }
        artifact.setStatus(APPROVED);
        return;
      } else {
        artifact.setContentStatus(
            RepositoryFormat.docker.name().equals(((NexusArtifactStream) artifactStream).getRepositoryFormat())
                ? METADATA_ONLY
                : NOT_DOWNLOADED);
        artifact.setStatus(APPROVED);
        return;
      }
    }

    if (ARTIFACTORY.name().equals(artifactStream.getArtifactStreamType())) {
      if (appId != null && !appId.equals(GLOBAL_APP_ID)) {
        if (getArtifactType(appId, artifactStream.getUuid()) == DOCKER) {
          artifact.setContentStatus(METADATA_ONLY);
          artifact.setStatus(APPROVED);
          return;
        }
        artifact.setStatus(QUEUED);
        return;
      } else {
        if (RepositoryType.docker.name().equals(((ArtifactoryArtifactStream) artifactStream).getRepositoryType())) {
          artifact.setContentStatus(METADATA_ONLY);
          artifact.setStatus(APPROVED);
          return;
        }
        artifact.setStatus(QUEUED);
        return;
      }
    }
    artifact.setStatus(QUEUED);
  }

  // TODO: ASR: remove this method after migration
  private ArtifactType getArtifactType(String appId, String artifactStreamId) {
    return artifactStreamServiceBindingService.getService(appId, artifactStreamId, true).getArtifactType();
  }

  @Override
  @ValidationGroups(Update.class)
  public Artifact update(@Valid Artifact artifact) {
    wingsPersistence.update(wingsPersistence.createQuery(Artifact.class)
                                .filter(ArtifactKeys.accountId, artifact.getAccountId())
                                .filter(ArtifactKeys.uuid, artifact.getUuid()),
        wingsPersistence.createUpdateOperations(Artifact.class).set("displayName", artifact.getDisplayName()));
    return wingsPersistence.get(Artifact.class, artifact.getUuid());
  }

  @Override
  public void updateMetadataAndRevision(
      String artifactId, String accountId, Map<String, String> newMetadata, String revision) {
    Query<Artifact> query = wingsPersistence.createQuery(Artifact.class)
                                .filter(ID_KEY, artifactId)
                                .filter(ArtifactKeys.accountId, accountId);
    UpdateOperations<Artifact> ops = wingsPersistence.createUpdateOperations(Artifact.class);
    if (newMetadata != null) {
      ops.set(ArtifactKeys.metadata, newMetadata);
    }
    if (revision != null) {
      ops.set(ArtifactKeys.revision, revision);
    }
    wingsPersistence.update(query, ops);
  }

  @Override
  public void updateStatus(String artifactId, String accountId, Status status) {
    Query<Artifact> query = wingsPersistence.createQuery(Artifact.class)
                                .filter(ID_KEY, artifactId)
                                .filter(ArtifactKeys.accountId, accountId);
    UpdateOperations<Artifact> ops = wingsPersistence.createUpdateOperations(Artifact.class);
    ops.set(ArtifactKeys.status, status);
    wingsPersistence.update(query, ops);
  }

  @Override
  public void updateStatus(String artifactId, String accountId, Status status, String errorMessage) {
    Query<Artifact> query = wingsPersistence.createQuery(Artifact.class)
                                .filter(ID_KEY, artifactId)
                                .filter(ArtifactKeys.accountId, accountId);
    UpdateOperations<Artifact> ops = wingsPersistence.createUpdateOperations(Artifact.class);
    setUnset(ops, ArtifactKeys.status, status);
    setUnset(ops, ArtifactKeys.errorMessage, errorMessage);
    wingsPersistence.update(query, ops);
  }

  @Override
  public void updateStatus(String artifactId, String accountId, Status status, ContentStatus contentStatus) {
    Query<Artifact> query = wingsPersistence.createQuery(Artifact.class)
                                .filter(ID_KEY, artifactId)
                                .filter(ArtifactKeys.accountId, accountId);
    UpdateOperations<Artifact> ops = wingsPersistence.createUpdateOperations(Artifact.class);
    setUnset(ops, ArtifactKeys.status, status);
    setUnset(ops, ArtifactKeys.contentStatus, contentStatus);
    wingsPersistence.update(query, ops);
  }

  @Override
  public void updateStatus(
      String artifactId, String accountId, Status status, ContentStatus contentStatus, String errorMessage) {
    Query<Artifact> query = wingsPersistence.createQuery(Artifact.class)
                                .filter(ID_KEY, artifactId)
                                .filter(ArtifactKeys.accountId, accountId);
    UpdateOperations<Artifact> ops = wingsPersistence.createUpdateOperations(Artifact.class);
    setUnset(ops, ArtifactKeys.status, status);
    setUnset(ops, ArtifactKeys.contentStatus, contentStatus);
    setUnset(ops, ArtifactKeys.errorMessage, errorMessage);
    wingsPersistence.update(query, ops);
  }

  @Override
  public void updateArtifactSourceName(ArtifactStream artifactStream) {
    Query<Artifact> query = prepareArtifactWithMetadataQuery(artifactStream, false);
    UpdateOperations<Artifact> ops = wingsPersistence.createUpdateOperations(Artifact.class);
    ops.set("artifactSourceName", artifactStream.getSourceName());
    wingsPersistence.update(query, ops);
  }

  @Override
  public void updateLastUpdatedAt(String artifactId, String accountId) {
    Query<Artifact> query = wingsPersistence.createQuery(Artifact.class)
                                .filter(ID_KEY, artifactId)
                                .filter(ArtifactKeys.accountId, accountId);
    UpdateOperations<Artifact> ops = wingsPersistence.createUpdateOperations(Artifact.class);
    ops.set(ArtifactKeys.lastUpdatedAt, System.currentTimeMillis());
    wingsPersistence.update(query, ops);
  }

  @Override
  public void addArtifactFile(String artifactId, String accountId, List<ArtifactFile> artifactFile) {
    log.info("Adding artifactFiles for artifactId {}", artifactId);
    Query<Artifact> query = wingsPersistence.createQuery(Artifact.class)
                                .filter(ArtifactKeys.accountId, accountId)
                                .filter(ArtifactKeys.uuid, artifactId);
    UpdateOperations<Artifact> ops =
        wingsPersistence.createUpdateOperations(Artifact.class).addAll(ArtifactKeys.artifactFiles, artifactFile, false);
    wingsPersistence.update(query, ops);
  }

  @Override
  public File download(String accountId, String artifactId) {
    Artifact artifact = get(accountId, artifactId);
    if (artifact == null || artifact.getStatus() != READY || isEmpty(artifact.getArtifactFiles())) {
      return null;
    }

    ArtifactFile artifactFile = artifact.getArtifactFiles().get(0);

    File tempDir = Files.createTempDir();
    String fileName = Optional.ofNullable(artifactFile.getName()).orElse(DEFAULT_ARTIFACT_FILE_NAME);

    File file = new File(tempDir, fileName);

    fileService.download(artifactFile.getFileUuid(), file, ARTIFACTS);
    return file;
  }

  @Override
  public Artifact get(String artifactId) {
    return wingsPersistence.get(Artifact.class, artifactId);
  }

  @Override
  public Artifact get(String accountId, String artifactId) {
    return wingsPersistence.createQuery(Artifact.class)
        .filter(ArtifactKeys.accountId, accountId)
        .filter(ArtifactKeys.uuid, artifactId)
        .get();
  }

  @SneakyThrows
  @Override
  public ArtifactView getWithServices(String artifactId, String appId) {
    Artifact artifact = wingsPersistence.get(Artifact.class, artifactId);
    ArtifactView artifactView = new ArtifactView();
    BeanUtils.copyProperties(artifactView, artifact);
    artifactView.setServices(artifactStreamServiceBindingService.listServices(appId, artifact.getArtifactStreamId()));
    return artifactView;
  }

  @Override
  public Artifact getWithSource(String artifactId) {
    Artifact artifact = wingsPersistence.get(Artifact.class, artifactId);
    if (artifact != null) {
      artifact.setSource(
          artifactStreamService.fetchArtifactSourceProperties(artifact.getAccountId(), artifact.getArtifactStreamId()));
    }
    return artifact;
  }

  @Override
  public boolean delete(String accountId, String artifactId) {
    Artifact artifact = get(accountId, artifactId);
    if (artifact == null) {
      return true;
    }

    if (isNotEmpty(artifact.getArtifactFiles())) {
      List<String> artifactIds = asList(artifactId);
      List<String> artifactFileUuids = collectArtifactFileIds(artifact);
      deleteArtifacts(artifactIds.toArray(), artifactFileUuids);
    } else {
      wingsPersistence.delete(accountId, Artifact.class, artifactId);
    }
    return true;
  }

  public void deleteArtifacts(List<Artifact> artifacts) {
    List<String> artifactIds = new ArrayList<>();
    List<String> artifactIdsWithFiles = new ArrayList<>();
    List<String> artifactFileIds = new ArrayList<>();
    List<String> allArtifactIds = new ArrayList<>();

    for (Artifact artifact : artifacts) {
      if (isNotEmpty(artifact.getArtifactFiles())) {
        artifactIdsWithFiles.add(artifact.getUuid());
        List<String> ids = collectArtifactFileIds(artifact);
        if (isNotEmpty(ids)) {
          artifactFileIds.addAll(ids);
        }
      } else {
        artifactIds.add(artifact.getUuid());
      }
    }
    allArtifactIds.addAll(artifactIdsWithFiles);
    allArtifactIds.addAll(artifactIds);
    if (isNotEmpty(allArtifactIds)) {
      deleteArtifacts(allArtifactIds.toArray(), artifactFileIds);
    }
  }

  @Override
  public void pruneByArtifactStream(String appId, String artifactStreamId) {
    // TODO: ASR: update with accountId
    deleteArtifactsByQuery(wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                               .project(ArtifactKeys.artifactFiles, true)
                               .filter(ArtifactKeys.artifactStreamId, artifactStreamId));
  }

  private void deleteArtifactsByQuery(Query<Artifact> artifactQuery) {
    List<String> artifactIds = new ArrayList<>();
    List<String> artifactIdsWithFiles = new ArrayList<>();
    List<String> artifactFileIds = new ArrayList<>();
    List<String> allArtifactIds = new ArrayList<>();

    try (HIterator<Artifact> iterator = new HIterator<>(artifactQuery.fetch())) {
      for (Artifact artifact : iterator) {
        if (isNotEmpty(artifact.getArtifactFiles())) {
          artifactIdsWithFiles.add(artifact.getUuid());
          List<String> ids = collectArtifactFileIds(artifact);
          if (isNotEmpty(ids)) {
            artifactFileIds.addAll(ids);
          }
        } else {
          artifactIds.add(artifact.getUuid());
        }
      }
    }
    allArtifactIds.addAll(artifactIdsWithFiles);
    allArtifactIds.addAll(artifactIds);
    if (isNotEmpty(allArtifactIds)) {
      deleteArtifacts(allArtifactIds.toArray(), artifactFileIds);
    }
  }

  private List<String> collectArtifactFileIds(Artifact artifact) {
    return artifact.getArtifactFiles()
        .stream()
        .filter(artifactFile -> artifactFile.getFileUuid() != null)
        .map(ArtifactFile::getFileUuid)
        .collect(Collectors.toList());
  }

  @Override
  public Artifact fetchLatestArtifactForArtifactStream(ArtifactStream artifactStream) {
    return getArtifact(artifactStream, asList(QUEUED, RUNNING, REJECTED, WAITING, READY, APPROVED, FAILED));
  }

  private Artifact getArtifact(ArtifactStream artifactStream, List<Status> statuses) {
    // TODO: ASR: update with accountId
    Query<Artifact> artifactQuery = wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                                        .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid());
    // For the custom artifact stream name as set artifact source name. Name can be changed so, it can not be unique
    if (CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      return artifactQuery.order("-createdAt").field(ArtifactKeys.status).hasAnyOf(statuses).get();
    }
    // For the custom artifact stream name as set artifact source name. Name can be changed so, it can not be unique
    artifactQuery.filter(ArtifactKeys.artifactSourceName, artifactStream.getSourceName());
    return artifactQuery.order("-createdAt").field(ArtifactKeys.status).hasAnyOf(statuses).get();
  }

  @Override
  public Artifact fetchLastCollectedApprovedArtifactForArtifactStream(ArtifactStream artifactStream) {
    return getArtifact(artifactStream, asList(READY, APPROVED));
  }

  @Override
  public Artifact fetchLastCollectedApprovedArtifactSorted(ArtifactStream artifactStream) {
    // Try to get 100 artifacts and sort them in application code.
    PageRequest<Artifact> pageRequest = aPageRequest()
                                            .addFilter(ArtifactKeys.accountId, EQ, artifactStream.getAccountId())
                                            .addFilter(ArtifactKeys.artifactStreamId, EQ, artifactStream.getUuid())
                                            .addFilter(ArtifactKeys.status, IN, READY, APPROVED)
                                            .withLimit("100")
                                            .build();
    List<Artifact> artifacts = listArtifactsForService(pageRequest);
    return isEmpty(artifacts) ? null : artifacts.get(0);
  }

  @Override
  public Artifact fetchLastCollectedArtifact(ArtifactStream artifactStream) {
    return getArtifact(artifactStream, asList(READY, QUEUED, RUNNING, WAITING, APPROVED));
  }

  @Override
  public Artifact getArtifactByBuildNumber(ArtifactStream artifactStream, String buildNumber, boolean regex) {
    // TODO: ASR: update with accountId
    Query<Artifact> artifactQuery = wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                                        .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid());

    return artifactQuery.filter(ArtifactKeys.metadata_buildNo, regex ? compile(buildNumber) : buildNumber)
        .order("-createdAt")
        .disableValidation()
        .get();
  }

  @Override
  public Artifact getArtifactByBuildNumber(
      String accountId, String artifactStreamId, String buildNumber, boolean regex) {
    Query<Artifact> artifactQuery = wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                                        .filter(ArtifactKeys.accountId, accountId)
                                        .filter(ArtifactKeys.artifactStreamId, artifactStreamId);

    return artifactQuery.filter(ArtifactKeys.metadata_buildNo, regex ? compile(buildNumber) : buildNumber)
        .order("-createdAt")
        .disableValidation()
        .get();
  }

  @Override
  public Artifact getArtifactByBuildNumberAndSourceName(
      ArtifactStream artifactStream, String buildNumber, boolean regex, String artifactSourceName) {
    // TODO: ASR: update with accountId
    Query<Artifact> artifactQuery = wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                                        .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid());

    return artifactQuery.filter(ArtifactKeys.artifactSourceName, artifactSourceName)
        .filter(ArtifactKeys.metadata_buildNo, regex ? compile(buildNumber) : buildNumber)
        .order("-createdAt")
        .disableValidation()
        .get();
  }

  @Override
  public Artifact startArtifactCollection(String accountId, String artifactId) {
    log.info("Start collecting artifact {} of accountId {}", artifactId, accountId);
    Artifact artifact = wingsPersistence.get(Artifact.class, artifactId);
    if (artifact == null) {
      throw new WingsException(
          "Artifact [" + artifactId + "] for the accountId [" + accountId + "] does not exist", USER);
    }
    if (RUNNING == artifact.getStatus() || QUEUED == artifact.getStatus()) {
      log.info(
          "Artifact Metadata collection for artifactId {} of the accountId {} is in progress or queued. Returning.",
          artifactId, accountId);
      return artifact;
    }

    if (artifact.getContentStatus() == null && !isEmpty(artifact.getArtifactFiles())) {
      log.info(
          "Artifact {} content status empty. It means it is already downloaded. Updating artifact content status as DOWNLOADED",
          artifactId);
      updateStatus(artifactId, artifact.getAccountId(), APPROVED, DOWNLOADED);
      return artifact;
    }

    if ((METADATA_ONLY == artifact.getContentStatus()) || (DOWNLOADING == artifact.getContentStatus())
        || (DOWNLOADED == artifact.getContentStatus())) {
      log.info("Artifact content for artifactId {} of the accountId {} is either downloaded or in progress. Returning.",
          artifactId, accountId);
      return artifact;
    }

    log.info("Sending event to collect artifact {} ", artifact.getUuid());
    collectQueue.send(aCollectEvent().withArtifact(artifact).build());

    return artifact;
  }

  @Override
  public ContentStatus getArtifactContentStatus(Artifact artifact) {
    if (artifact.getContentStatus() != null) {
      return artifact.getContentStatus();
    }
    ArtifactStream artifactStream = artifactStreamService.get(artifact.getArtifactStreamId());
    if (artifactStream == null) {
      log.info("ArtifactStream of artifact {} was deleted", artifact.getUuid());
      artifact = wingsPersistence.get(Artifact.class, artifact.getUuid());
      if (artifact == null) {
        return DELETED;
      }
      if (artifact.getContentStatus() == null) {
        if (!isEmpty(artifact.getArtifactFiles())) {
          updateStatus(artifact.getUuid(), artifact.getAccountId(), APPROVED, DOWNLOADED);
          return DOWNLOADED;
        } else {
          updateStatus(artifact.getUuid(), artifact.getAccountId(), APPROVED, METADATA_ONLY);
          return METADATA_ONLY;
        }
      }
      return artifact.getContentStatus();
    }
    setArtifactStatus(artifact, artifactStream);
    return artifact.getContentStatus();
  }

  @Override
  public void deleteArtifacts(int retentionSize) {
    try (HIterator<ArtifactStream> artifactStreams =
             new HIterator<>(wingsPersistence.createQuery(ArtifactStream.class)
                                 .project(ArtifactStreamKeys.artifactStreamType, true)
                                 .project(ArtifactStreamKeys.metadataOnly, true)
                                 .fetch())) {
      for (ArtifactStream artifactStream : artifactStreams) {
        deleteArtifactsWithContents(retentionSize, artifactStream);
      }
    }
  }

  private void deleteArtifactsWithContents(int retentionSize, ArtifactStream artifactStream) {
    if (metadataOnlyBehindFlag(featureFlagService, artifactStream.getAccountId(), artifactStream.isMetadataOnly())
        || autoDownloaded.contains(artifactStream.getArtifactStreamType())) {
      return;
    }

    boolean artifactPerpetualTask =
        featureFlagService.isEnabled(FeatureName.ARTIFACT_PERPETUAL_TASK, artifactStream.getAccountId());
    if (!artifactPerpetualTask && ARTIFACTORY.name().equals(artifactStream.getArtifactStreamType())) {
      retentionSize *= 4;
    }

    // If it is Nexus Non Docker
    // TODO: ASR: update with accountId
    List<Artifact> toBeDeletedArtifacts = wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                                              .project(ArtifactKeys.artifactFiles, true)
                                              .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid())
                                              .field(ArtifactKeys.contentStatus)
                                              .hasAnyOf(singletonList(DOWNLOADED))
                                              .order(Sort.descending(CREATED_AT_KEY))
                                              .asList(new FindOptions().skip(retentionSize));
    if (isEmpty(toBeDeletedArtifacts)) {
      return;
    }

    toBeDeletedArtifacts =
        toBeDeletedArtifacts.stream().filter(artifact -> isNotEmpty(artifact.getArtifactFiles())).collect(toList());
    if (isEmpty(toBeDeletedArtifacts)) {
      return;
    }

    if (artifactPerpetualTask || NEXUS.name().equals(artifactStream.getArtifactStreamType())) {
      // NOTE: Do not delete artifacts from NEXUS artifact streams as we don't limit the count to
      // ARTIFACT_RETENTION_SIZE while fetching builds from NEXUS. So artifacts can get collected and then deleted and
      // collected again. This leads to duplicate collections for the same artifact and more importantly we can possibly
      // execute 'On New Artifact' triggers on older artifacts. As a workaround for NEXUS artifact streams, we simply
      // change the content status from DOWNLOADED to NOT_DOWNLOADED and delete the artifact files.
      //
      // NOTE: The repository format here is not docker as we have already filtered by artifactStream.isMetadataOnly and
      // checked that the artifact has files.
      List<String> toBeDeletedArtifactIds = toBeDeletedArtifacts.stream().map(Artifact::getUuid).collect(toList());
      markArtifactsNotDownloaded(toBeDeletedArtifactIds);
      deleteArtifactFiles(artifactStream.getUuid(), toBeDeletedArtifacts);
      return;
    }

    log.info("Deleting artifacts for artifactStreamId: [{}] of size: [{}]", artifactStream.getUuid(),
        toBeDeletedArtifacts.size());
    deleteArtifacts(artifactStream.getUuid(), toBeDeletedArtifacts);
  }

  private void markArtifactsNotDownloaded(List<String> toBeDeletedArtifactIds) {
    Query<Artifact> query =
        wingsPersistence.createQuery(Artifact.class, excludeAuthority).field(ID_KEY).in(toBeDeletedArtifactIds);
    UpdateOperations<Artifact> ops = wingsPersistence.createUpdateOperations(Artifact.class);
    setUnset(ops, ArtifactKeys.contentStatus, NOT_DOWNLOADED);
    setUnset(ops, ArtifactKeys.artifactFiles, null);
    wingsPersistence.update(query, ops);
  }

  private void deleteArtifacts(String artifactStreamId, List<Artifact> toBeDeletedArtifacts) {
    try {
      List<String> artifactFileIds = getArtifactFileIds(toBeDeletedArtifacts);
      if (isNotEmpty(artifactFileIds)) {
        Object[] artifactIds = toBeDeletedArtifacts.stream().map(Artifact::getUuid).toArray();
        deleteArtifacts(artifactIds, artifactFileIds);
      }
    } catch (Exception ex) {
      log.warn("Failed to delete artifacts for artifactStreamId: [{}] of size: [{}]", artifactStreamId,
          toBeDeletedArtifacts.size(), ex);
      return;
    }
    log.info("Successfully deleted artifacts for artifactStreamId: [{}] of size: [{}]", artifactStreamId,
        toBeDeletedArtifacts.size());
  }

  private void deleteArtifactFiles(String artifactStreamId, List<Artifact> toBeDeletedArtifacts) {
    try {
      deleteArtifactFiles(getArtifactFileIds(toBeDeletedArtifacts));
    } catch (Exception ex) {
      log.warn("Failed to delete artifacts for artifactStreamId: [{}] of size: [{}]", artifactStreamId,
          toBeDeletedArtifacts.size(), ex);
      return;
    }
    log.info("Successfully deleted artifact files for artifactStreamId: [{}] of size: [{}]", artifactStreamId,
        toBeDeletedArtifacts.size());
  }

  private void deleteArtifacts(Object[] artifactIds, List<String> artifactFileIds) {
    log.info("Deleting artifactIds of artifacts {}", artifactIds);

    final DBCollection dbCollection = wingsPersistence.getCollection(DEFAULT_STORE, "artifacts");
    Iterator<Object> artifactListIterator = Arrays.stream(artifactIds).iterator();
    Iterator<List<Object>> chunksOfArtifactIds = Iterators.partition(artifactListIterator, DELETE_BATCH_SIZE);
    while (chunksOfArtifactIds.hasNext()) {
      BulkWriteOperation bulkWriteOperation = dbCollection.initializeUnorderedBulkOperation();
      bulkWriteOperation
          .find(wingsPersistence.createQuery(Artifact.class)
                    .field(ArtifactKeys.uuid)
                    .in(chunksOfArtifactIds.next())
                    .getQueryObject())
          .remove();
      bulkWriteOperation.execute();
    }

    deleteArtifactFiles(artifactFileIds);
  }

  private void deleteArtifactFiles(List<String> artifactFileIds) {
    if (isEmpty(artifactFileIds)) {
      return;
    }
    for (String fileId : artifactFileIds) {
      fileService.deleteFile(fileId, ARTIFACTS);
    }
  }

  private List<String> getArtifactFileIds(List<Artifact> artifacts) {
    return artifacts.stream()
        .flatMap(artifact -> artifact.getArtifactFiles().stream())
        .filter(artifactFile -> artifactFile.getFileUuid() != null)
        .map(ArtifactFile::getFileUuid)
        .collect(Collectors.toList());
  }

  @Override
  public Query<Artifact> prepareArtifactWithMetadataQuery(ArtifactStream artifactStream, boolean hitSecondary) {
    // TODO: ASR: update with accountId
    Query<Artifact> artifactQuery =
        wingsPersistence.createQuery(Artifact.class, excludeAuthority)
            .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid())
            .field(ArtifactKeys.status)
            .hasAnyOf(asList(QUEUED, RUNNING, REJECTED, WAITING, READY, APPROVED, FAILED, ERROR))
            .disableValidation();

    if (AMI.name().equals(artifactStream.getArtifactStreamType())) {
      artifactQuery.project(ArtifactKeys.revision, true);
    } else {
      artifactQuery.project(ArtifactKeys.metadata, true);
      artifactQuery.project(ArtifactKeys.revision, true);
    }

    if (CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      return artifactQuery;
    }
    artifactQuery.filter(ArtifactKeys.artifactSourceName, artifactStream.getSourceName());
    if (artifactStream.getAccountId() != null && hitSecondary
        && featureFlagService.isEnabled(FeatureName.CDS_QUERY_OPTIMIZATION, artifactStream.getAccountId())) {
      artifactQuery.useReadPreference(ReadPreference.secondaryPreferred());
    }
    return artifactQuery;
  }

  @Override
  public Query<Artifact> prepareCleanupQuery(ArtifactStream artifactStream) {
    Query<Artifact> artifactQuery =
        wingsPersistence.createQuery(Artifact.class, excludeAuthority)
            .project(ArtifactKeys.artifactFiles, true)
            .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid())
            .field(ArtifactKeys.status)
            .hasAnyOf(asList(QUEUED, RUNNING, REJECTED, WAITING, READY, APPROVED, FAILED, ERROR))
            .disableValidation();

    if (AMI.name().equals(artifactStream.getArtifactStreamType())) {
      artifactQuery.project(ArtifactKeys.revision, true);
    } else {
      artifactQuery.project(ArtifactKeys.metadata, true);
      artifactQuery.project(ArtifactKeys.revision, true);
    }

    if (CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      return artifactQuery;
    }
    artifactQuery.filter(ArtifactKeys.artifactSourceName, artifactStream.getSourceName());
    return artifactQuery;
  }

  @Override
  public void deleteWhenArtifactSourceNameChanged(ArtifactStream artifactStream) {
    deleteArtifactsByQuery(wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                               .project(ArtifactKeys.artifactFiles, true)
                               .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid())
                               .filter(ArtifactKeys.artifactSourceName, artifactStream.getSourceName()));
  }

  @Override
  public void deleteByArtifactStreamId(String appId, String artifactStreamId) {
    deleteArtifactsByQuery(wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                               .project(ArtifactKeys.artifactFiles, true)
                               .filter(ArtifactKeys.artifactStreamId, artifactStreamId)
                               .filter(ArtifactKeys.appId, appId));
  }

  @Override
  public List<Artifact> listByIds(String accountId, Collection<String> artifactIds) {
    return wingsPersistence.createQuery(Artifact.class)
        .filter(ArtifactKeys.accountId, accountId)
        .field(ArtifactKeys.uuid)
        .in(artifactIds)
        .asList();
  }

  @Override
  public List<Artifact> listByAccountId(String accountId) {
    return wingsPersistence.createQuery(Artifact.class).filter(ArtifactKeys.accountId, accountId).asList();
  }

  @Override
  public List<Artifact> listByAppId(String appId) {
    // NOTE: appId is only used for finding accountId
    if (GLOBAL_APP_ID.equals(appId)) {
      return new ArrayList<>();
    }

    return listByAccountId(appService.getAccountIdByAppId(appId));
  }

  @Override
  public List<ArtifactFile> fetchArtifactFiles(String artifactId) {
    return wingsPersistence.createQuery(Artifact.class)
        .project(ArtifactKeys.artifactFiles, true)
        .filter(ArtifactKeys.uuid, artifactId)
        .get()
        .getArtifactFiles();
  }

  @Override
  public List<Artifact> listArtifactsByArtifactStreamId(String accountId, String artifactStreamId) {
    return wingsPersistence.createQuery(Artifact.class)
        .filter(ArtifactKeys.accountId, accountId)
        .filter(ArtifactKeys.artifactStreamId, artifactStreamId)
        .order(Sort.descending(CREATED_AT_KEY))
        .asList();
  }

  @Override
  public List<Artifact> listArtifactsByArtifactStreamId(String accountId, String artifactStreamId, String buildNo) {
    return wingsPersistence.createQuery(Artifact.class)
        .filter(ArtifactKeys.accountId, accountId)
        .filter(ArtifactKeys.artifactStreamId, artifactStreamId)
        .filter(ArtifactKeys.metadata_buildNo, buildNo)
        .order(Sort.descending(CREATED_AT_KEY))
        .asList();
  }

  public static boolean metadataOnlyBehindFlag(
      FeatureFlagService featureFlagService, String accountId, boolean metadataOnly) {
    if (featureFlagService.isEnabled(ARTIFACT_STREAM_METADATA_ONLY, accountId)) {
      return true;
    } else {
      return metadataOnly;
    }
  }
}
