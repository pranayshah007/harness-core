/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.FeatureName.ARTIFACT_STREAM_METADATA_ONLY;
import static io.harness.beans.FeatureName.SPG_ALLOW_TEMPLATE_ON_NEXUS_ARTIFACT;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.PageResponse.PageResponseBuilder.aPageResponse;
import static io.harness.beans.SearchFilter.Operator.EQ;
import static io.harness.beans.SearchFilter.Operator.STARTS_WITH;
import static io.harness.data.structure.CollectionUtils.trimmedLowercaseSet;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;
import static io.harness.mongo.MongoUtils.setUnset;
import static io.harness.persistence.HQuery.excludeAuthority;
import static io.harness.validation.Validator.notNullCheck;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.artifact.ArtifactStreamType.ACR;
import static software.wings.beans.artifact.ArtifactStreamType.AMAZON_S3;
import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.ARTIFACTORY;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_ARTIFACTS;
import static software.wings.beans.artifact.ArtifactStreamType.AZURE_MACHINE_IMAGE;
import static software.wings.beans.artifact.ArtifactStreamType.BAMBOO;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.beans.artifact.ArtifactStreamType.DOCKER;
import static software.wings.beans.artifact.ArtifactStreamType.ECR;
import static software.wings.beans.artifact.ArtifactStreamType.GCR;
import static software.wings.beans.artifact.ArtifactStreamType.GCS;
import static software.wings.beans.artifact.ArtifactStreamType.JENKINS;
import static software.wings.beans.artifact.ArtifactStreamType.NEXUS;
import static software.wings.beans.artifact.ArtifactStreamType.SFTP;
import static software.wings.beans.artifact.ArtifactStreamType.SMB;
import static software.wings.common.TemplateConstants.LATEST_TAG;
import static software.wings.security.PermissionAttribute.PermissionType.ACCOUNT_MANAGEMENT;
import static software.wings.service.impl.artifact.ArtifactServiceImpl.metadataOnlyBehindFlag;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.annotations.dev.OwnedBy;
import io.harness.artifact.ArtifactCollectionPTaskClientParams.ArtifactCollectionPTaskClientParamsKeys;
import io.harness.artifact.ArtifactCollectionResponseHandler;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.Operator;
import io.harness.data.validator.EntityNameValidator;
import io.harness.event.handler.impl.EventPublishHelper;
import io.harness.exception.ExceptionUtils;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.ShellExecutionException;
import io.harness.exception.UnauthorizedUsageRestrictionsException;
import io.harness.ff.FeatureFlagService;
import io.harness.observer.RemoteObserverInformer;
import io.harness.observer.Subject;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.CreatedAtAware;
import io.harness.persistence.HIterator;
import io.harness.queue.QueuePublisher;
import io.harness.reflection.ReflectionUtils;
import io.harness.validation.Create;
import io.harness.validation.PersistenceValidator;
import io.harness.validation.SuppressValidation;
import io.harness.validation.Update;

import software.wings.beans.AccountEvent;
import software.wings.beans.AzureContainerRegistry;
import software.wings.beans.EntityType;
import software.wings.beans.Event.Type;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Variable;
import software.wings.beans.account.AccountEventType;
import software.wings.beans.artifact.AcrArtifactStream;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStream.ArtifactStreamKeys;
import software.wings.beans.artifact.ArtifactStreamCollectionStatus;
import software.wings.beans.artifact.ArtifactStreamSummary;
import software.wings.beans.artifact.ArtifactStreamType;
import software.wings.beans.artifact.ArtifactSummary;
import software.wings.beans.artifact.ArtifactoryArtifactStream;
import software.wings.beans.artifact.AzureArtifactsArtifactStream;
import software.wings.beans.artifact.CustomArtifactStream;
import software.wings.beans.artifact.NexusArtifactStream;
import software.wings.beans.config.ArtifactSourceable;
import software.wings.beans.config.NexusConfig;
import software.wings.beans.template.TemplateHelper;
import software.wings.beans.trigger.Trigger;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;
import software.wings.prune.PruneEntityListener;
import software.wings.prune.PruneEvent;
import software.wings.service.intfc.AlertService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.ArtifactService;
import software.wings.service.intfc.ArtifactStreamService;
import software.wings.service.intfc.ArtifactStreamServiceBindingService;
import software.wings.service.intfc.AzureResourceService;
import software.wings.service.intfc.BuildSourceService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.TriggerService;
import software.wings.service.intfc.UsageRestrictionsService;
import software.wings.service.intfc.artifact.ArtifactStreamServiceObserver;
import software.wings.service.intfc.ownership.OwnedByArtifactStream;
import software.wings.service.intfc.template.TemplateService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingValue;
import software.wings.settings.SettingVariableTypes;
import software.wings.utils.ArtifactType;
import software.wings.utils.RepositoryFormat;
import software.wings.utils.RepositoryType;
import software.wings.utils.Utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.annotations.Transient;
import dev.morphia.query.FindOptions;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import dev.morphia.query.UpdateOperations;
import dev.morphia.query.UpdateResults;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import javax.ws.rs.NotFoundException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;

@Singleton
@ValidateOnExecution
@Slf4j
@OwnedBy(CDC)
public class ArtifactStreamServiceImpl implements ArtifactStreamService {
  private static final Integer REFERENCED_ENTITIES_TO_SHOW = 10;
  public static final String ARTIFACT_STREAM_DEBUG_LOG = "ARTIFACT_STREAM_DEBUG_LOG ";

  // Restrict to docker only artifact streams.
  private static final List<String> dockerOnlyArtifactStreams = Collections.unmodifiableList(
      asList(ArtifactStreamType.DOCKER.name(), ECR.name(), GCR.name(), ACR.name(), CUSTOM.name()));
  private static final String EXCEPTION_OBSERVERS_OF_ARTIFACT_STREAM =
      "Encountered exception while informing the observers of Artifact Stream";

  @Inject private WingsPersistence wingsPersistence;
  @Inject private ExecutorService executorService;
  @Inject private QueuePublisher<PruneEvent> pruneQueue;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private BuildSourceService buildSourceService;
  @Inject private AppService appService;
  @Inject private SettingsService settingsService;
  @Inject private YamlPushService yamlPushService;
  @Inject @Transient private transient FeatureFlagService featureFlagService;
  @Inject private TemplateService templateService;
  @Inject private TemplateHelper templateHelper;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private transient AzureResourceService azureResourceService;

  // Do not delete as they are being used by Prune
  @Inject private ArtifactService artifactService;
  @Inject private AlertService alertService;
  @Inject private ArtifactStreamServiceBindingService artifactStreamServiceBindingService;
  @Inject private TriggerService triggerService;
  @Inject private UsageRestrictionsService usageRestrictionsService;
  @Inject private EventPublishHelper eventPublishHelper;
  @Inject
  @Getter(onMethod = @__(@SuppressValidation))
  private Subject<ArtifactStreamServiceObserver> subject = new Subject<>();
  @Inject private RemoteObserverInformer remoteObserverInformer;

  @Override
  public PageResponse<ArtifactStream> list(PageRequest<ArtifactStream> req) {
    return wingsPersistence.query(ArtifactStream.class, req);
  }

  @Override
  public PageResponse<ArtifactStream> list(
      PageRequest<ArtifactStream> req, String accountId, boolean withArtifactCount, String artifactSearchString) {
    return list(req, accountId, withArtifactCount, artifactSearchString, null, Integer.MAX_VALUE);
  }

  @Override
  public PageResponse<ArtifactStream> list(PageRequest<ArtifactStream> req, String accountId, boolean withArtifactCount,
      String artifactSearchString, ArtifactType artifactType, int maxArtifacts) {
    if (!withArtifactCount) {
      return list(req);
    }

    try {
      PageRequest<ArtifactStream> pageRequest = req.copy();
      int offset = pageRequest.getStart();
      int limit = pageRequest.getPageSize();

      pageRequest.setOffset("0");
      pageRequest.setLimit(String.valueOf(Integer.MAX_VALUE));
      addFilterToArtifactStreamPageRequest(artifactType, pageRequest);
      PageResponse<ArtifactStream> pageResponse = wingsPersistence.query(ArtifactStream.class, pageRequest);

      List<ArtifactStream> filteredArtifactStreams = pageResponse.getResponse();

      if (isNotEmpty(filteredArtifactStreams)) {
        filteredArtifactStreams = filterArtifactStreamsAndArtifactsWithCount(
            accountId, artifactSearchString, maxArtifacts, filteredArtifactStreams);
      }

      List<ArtifactStream> resp;
      if (isEmpty(filteredArtifactStreams)) {
        resp = Collections.emptyList();
      } else {
        int total = filteredArtifactStreams.size();
        if (total <= offset) {
          resp = Collections.emptyList();
        } else {
          int endIdx = Math.min(offset + limit, total);
          resp = filteredArtifactStreams.subList(offset, endIdx);
          resp.forEach(artifactStream -> {
            SettingAttribute settingAttribute = settingsService.get(artifactStream.getSettingId());
            if (settingAttribute != null) {
              artifactStream.setArtifactServerName(settingAttribute.getName());
            }
          });
        }
      }

      return aPageResponse()
          .withResponse(resp)
          .withTotal(filteredArtifactStreams.size())
          .withOffset(req.getOffset())
          .withLimit(req.getLimit())
          .build();
    } catch (Exception e) {
      throw new InvalidRequestException(ExceptionUtils.getMessage(e), e);
    }
  }

  private List<ArtifactStream> filterArtifactStreamsAndArtifactsWithCount(
      String accountId, String artifactSearchString, int maxArtifacts, List<ArtifactStream> filteredArtifactStreams) {
    List<ArtifactStream> newArtifactStreams = new ArrayList<>();
    for (ArtifactStream artifactStream : filteredArtifactStreams) {
      Query<Artifact> artifactQuery = wingsPersistence.createQuery(Artifact.class)
                                          .disableValidation()
                                          .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid())
                                          .filter(ArtifactKeys.accountId, accountId)
                                          .project(ArtifactKeys.artifactStreamId, true)
                                          .project(ArtifactKeys.uiDisplayName, true)
                                          .project(ArtifactKeys.metadata, true)
                                          .order(Sort.descending(CreatedAtAware.CREATED_AT_KEY));
      if (isNotEmpty(artifactSearchString)) {
        // NOTE: Might be inefficient for artifact streams having large number of artifacts.
        artifactQuery.or(artifactQuery.criteria(ArtifactKeys.uiDisplayName).containsIgnoreCase(artifactSearchString),
            artifactQuery.criteria(ArtifactKeys.metadata_buildNo).containsIgnoreCase(artifactSearchString));
      }

      long totalArtifactCount = artifactQuery.count();
      if (totalArtifactCount == 0L) {
        if (artifactStream.isArtifactStreamParameterized()) {
          artifactStream.setArtifactCount(totalArtifactCount);
          newArtifactStreams.add(artifactStream);
        }
        continue;
      }

      List<Artifact> artifacts = artifactQuery.asList(new FindOptions().limit(maxArtifacts));
      if (isEmpty(artifacts)) {
        continue;
      }

      List<ArtifactSummary> artifactSummaries =
          artifacts.stream().map(ArtifactStreamServiceImpl::prepareSummaryFromArtifact).collect(toList());
      artifactStream.setArtifactCount(totalArtifactCount);
      artifactStream.setArtifacts(artifactSummaries);
      newArtifactStreams.add(artifactStream);
    }
    return newArtifactStreams;
  }

  static void addFilterToArtifactStreamQuery(ArtifactType artifactType, Query<ArtifactStream> artifactStreamQuery) {
    if (artifactType == null) {
      return;
    }

    switch (artifactType) {
      case DOCKER: // Deployment type: K8S, ECS, Helm
        artifactStreamQuery.or(
            artifactStreamQuery.criteria(ArtifactStreamKeys.artifactStreamType).in(dockerOnlyArtifactStreams),
            artifactStreamQuery.criteria(ArtifactStreamKeys.repositoryFormat).equal(RepositoryFormat.docker.name()),
            artifactStreamQuery.criteria(ArtifactStreamKeys.repositoryType).equal(RepositoryType.docker.name()));
        break;
      case AWS_LAMBDA:
        artifactStreamQuery.criteria(ArtifactStreamKeys.artifactStreamType)
            .in(asList(AMAZON_S3.name(), CUSTOM.name())); // TODO: verify this
        break;
      case AMI:
        artifactStreamQuery.criteria(ArtifactStreamKeys.artifactStreamType).equal(AMI.name());
        break;
      case AZURE_MACHINE_IMAGE:
        artifactStreamQuery.criteria(ArtifactStreamKeys.artifactStreamType).equal(AZURE_MACHINE_IMAGE.name());
        break;
      case AZURE_WEBAPP:
        artifactStreamQuery.criteria(ArtifactStreamKeys.artifactStreamType)
            .in(asList(DOCKER.name(), ARTIFACTORY.name(), AZURE_ARTIFACTS.name()));
        break;
      case AWS_CODEDEPLOY: // Deployment Type: AWS_CODEDEPLOY,
      case PCF: // Deployment Type: PCF,
      case WAR: // Deployment type: ssh
      case JAR:
      case NUGET:
      case TAR:
      case RPM:
      case ZIP:
      case IIS: // Deployment type: WinRM
      case IIS_APP:
      case IIS_VirtualDirectory:
        artifactStreamQuery.and(
            artifactStreamQuery.criteria(ArtifactStreamKeys.artifactStreamType)
                .in(asList(JENKINS.name(), BAMBOO.name(), GCS.name(), NEXUS.name(), ARTIFACTORY.name(),
                    AMAZON_S3.name(), SMB.name(), AMI.name(), SFTP.name(), AZURE_ARTIFACTS.name(), CUSTOM.name())),
            artifactStreamQuery.criteria(ArtifactStreamKeys.repositoryFormat).notEqual(RepositoryFormat.docker.name()),
            artifactStreamQuery.criteria(ArtifactStreamKeys.repositoryType)
                .notEqual(RepositoryType.docker.name())); // TODO: verify this
        break;
      case OTHER:
        artifactStreamQuery.criteria(ArtifactStreamKeys.artifactStreamType)
            .in(asList(ArtifactStreamType.DOCKER.name(), ECR.name(), GCR.name(), ACR.name(), JENKINS.name(),
                BAMBOO.name(), GCS.name(), NEXUS.name(), ARTIFACTORY.name(), AMAZON_S3.name(), SMB.name(), AMI.name(),
                SFTP.name(), AZURE_ARTIFACTS.name(), CUSTOM.name()));
        break;
      default:
        throw new InvalidRequestException(format("Unsupported artifact type: [%s]", artifactType.name()));
    }
  }

  private static void addFilterToArtifactStreamPageRequest(
      ArtifactType artifactType, PageRequest<ArtifactStream> artifactStreamPageRequest) {
    if (artifactType == null) {
      return;
    }

    switch (artifactType) {
      case DOCKER: // Deployment type: K8S, ECS, Helm
        artifactStreamPageRequest.addFilter("", Operator.OR,
            createSearchFilter(ArtifactStreamKeys.artifactStreamType, Operator.IN, dockerOnlyArtifactStreams.toArray()),
            createSearchFilter(ArtifactStreamKeys.repositoryFormat, Operator.EQ, RepositoryFormat.docker.name()),
            createSearchFilter(ArtifactStreamKeys.repositoryType, Operator.EQ, RepositoryType.docker.name()));
        break;
      case AWS_LAMBDA:
        artifactStreamPageRequest.addFilter(
            ArtifactStreamKeys.artifactStreamType, Operator.IN, AMAZON_S3.name(), CUSTOM.name()); // TODO: verify this
        break;
      case AMI:
        artifactStreamPageRequest.addFilter(ArtifactStreamKeys.artifactStreamType, Operator.EQ, AMI.name());
        break;
      case AZURE_MACHINE_IMAGE:
        artifactStreamPageRequest.addFilter(
            ArtifactStreamKeys.artifactStreamType, Operator.EQ, AZURE_MACHINE_IMAGE.name());
        break;
      case AZURE_WEBAPP:
        artifactStreamPageRequest.addFilter(ArtifactStreamKeys.artifactStreamType, Operator.IN, DOCKER.name(),
            ARTIFACTORY.name(), AZURE_ARTIFACTS.name());
        break;
      case AWS_CODEDEPLOY: // Deployment Type: AWS_CODEDEPLOY,
      case PCF: // Deployment Type: PCF,
      case WAR: // Deployment type: ssh
      case JAR:
      case NUGET:
      case TAR:
      case RPM:
      case ZIP:
      case IIS: // Deployment type: WinRM
      case IIS_APP:
      case IIS_VirtualDirectory:
        artifactStreamPageRequest.addFilter("", Operator.AND,
            createSearchFilter(ArtifactStreamKeys.artifactStreamType, Operator.IN, JENKINS.name(), BAMBOO.name(),
                GCS.name(), NEXUS.name(), ARTIFACTORY.name(), AMAZON_S3.name(), SMB.name(), AMI.name(), SFTP.name(),
                AZURE_ARTIFACTS.name(), CUSTOM.name()),
            createSearchFilter(ArtifactStreamKeys.repositoryFormat, Operator.NOT_EQ, RepositoryFormat.docker.name()),
            createSearchFilter(
                ArtifactStreamKeys.repositoryType, Operator.NOT_EQ, RepositoryType.docker.name())); // TODO: verify this
        break;
      case OTHER:
        artifactStreamPageRequest.addFilter(ArtifactStreamKeys.artifactStreamType, Operator.IN,
            ArtifactStreamType.DOCKER.name(), ECR.name(), GCR.name(), ACR.name(), JENKINS.name(), BAMBOO.name(),
            GCS.name(), NEXUS.name(), ARTIFACTORY.name(), AMAZON_S3.name(), SMB.name(), AMI.name(), SFTP.name(),
            AZURE_ARTIFACTS.name(), CUSTOM.name());
        break;
      default:
        throw new InvalidRequestException(format("Unsupported artifact type: [%s]", artifactType.name()));
    }
  }

  private static SearchFilter createSearchFilter(String fieldName, Operator op, Object... fieldValues) {
    return SearchFilter.builder().fieldName(fieldName).op(op).fieldValues(fieldValues).build();
  }

  @Override
  public ArtifactStream get(String artifactStreamId) {
    ArtifactStream artifactStream = wingsPersistence.get(ArtifactStream.class, artifactStreamId);
    if (artifactStream != null) {
      artifactStream.setMaxAttempts(ArtifactCollectionResponseHandler.MAX_FAILED_ATTEMPTS);
    }
    return artifactStream;
  }

  @Override
  public ArtifactStream getArtifactStreamByName(String appId, String serviceId, String artifactStreamName) {
    return artifactStreamServiceBindingService.listArtifactStreams(appId, serviceId)
        .stream()
        .filter(artifactStream -> artifactStreamName.equals(artifactStream.getName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public ArtifactStream getArtifactStreamByName(String settingId, String artifactStreamName) {
    return wingsPersistence.createQuery(ArtifactStream.class)
        .filter(ArtifactStreamKeys.appId, GLOBAL_APP_ID)
        .filter(ArtifactStreamKeys.settingId, settingId)
        .filter(ArtifactStreamKeys.name, artifactStreamName)
        .get();
  }

  @Override
  @ValidationGroups(Create.class)
  public ArtifactStream create(ArtifactStream artifactStream) {
    return create(artifactStream, true);
  }

  @Override
  @ValidationGroups(Create.class)
  public ArtifactStream create(ArtifactStream artifactStream, boolean validate) {
    String accountId = getAccountIdForArtifactStream(artifactStream);
    artifactStream.setAccountId(accountId);

    if (GLOBAL_APP_ID.equals(artifactStream.fetchAppId())) {
      usageRestrictionsService.validateUsageRestrictionsOnEntitySave(artifactStream.getAccountId(), ACCOUNT_MANAGEMENT,
          settingsService.getUsageRestrictionsForSettingId(artifactStream.getSettingId()), false);
    }

    setServiceId(artifactStream);

    boolean originalMetadataOnly = artifactStream.isMetadataOnly();
    boolean isArtifactStreamMetadataFFEnabled = featureFlagService.isEnabled(ARTIFACT_STREAM_METADATA_ONLY, accountId);
    if (isArtifactStreamMetadataFFEnabled) {
      artifactStream.setMetadataOnly(true);
    }
    artifactStream.validateRequiredFields();
    if (isArtifactStreamMetadataFFEnabled) {
      artifactStream.setMetadataOnly(originalMetadataOnly);
    }

    validateIfNexus2AndDockerRepositoryType(artifactStream, accountId);

    // Set collectionEnabled status
    if (artifactStream.getCollectionEnabled() == null) {
      artifactStream.setCollectionEnabled(true);
    }

    // Set collection status initially to UNSTABLE.
    artifactStream.setCollectionStatus(ArtifactStreamCollectionStatus.UNSTABLE.name());

    // check if artifact stream is parameterized
    boolean streamParameterized = artifactStream.checkIfStreamParameterized();
    if (streamParameterized) {
      // parameterised artifact stream cannot have collection enabled true, forcing it to be false.
      artifactStream.setCollectionEnabled(false);
      // setting parameterised artifact stream status to be STOPPED since collection cannot be enabled for it.
      artifactStream.setCollectionStatus(ArtifactStreamCollectionStatus.STOPPED.name());
      // if nexus check if its not version 3
      validateIfNexus2AndParameterized(artifactStream, accountId);
      artifactStream.setArtifactStreamParameterized(true);
    }

    validateArtifactStream(artifactStream);
    if (validate && artifactStream.getTemplateUuid() == null && !streamParameterized) {
      validateArtifactSourceData(artifactStream);
    }

    artifactStream.setSourceName(artifactStream.generateSourceName());
    setAutoPopulatedName(artifactStream);
    if (!artifactStream.isAutoPopulate() && isEmpty(artifactStream.getName())) {
      throw new InvalidRequestException("Artifact source name is mandatory", USER);
    }

    if (artifactStream.getTemplateUuid() != null) {
      String version = artifactStream.getTemplateVersion() != null ? artifactStream.getTemplateVersion() : LATEST_TAG;
      ArtifactStream artifactStream1 = (ArtifactStream) templateService.constructEntityFromTemplate(
          artifactStream.getTemplateUuid(), version, EntityType.ARTIFACT_STREAM);
      if (artifactStream instanceof CustomArtifactStream) {
        ((CustomArtifactStream) artifactStream).setScripts(((CustomArtifactStream) artifactStream1).getScripts());
      }
      if (isEmpty(artifactStream.getTemplateVariables())) {
        artifactStream.setTemplateVariables(artifactStream1.getTemplateVariables());
      } else {
        if (validate && !streamParameterized) {
          validateArtifactSourceData(artifactStream);
        }
      }
    }

    addAcrHostNameIfNeeded(artifactStream);

    // Set metadata-only field for nexus and azure artifacts.
    setMetadataOnly(artifactStream);

    if (!metadataOnlyBehindFlag(featureFlagService, artifactStream.getAccountId(), artifactStream.isMetadataOnly())) {
      throw new InvalidRequestException("Artifact Stream's metadata-only property cannot be set to false", USER);
    }
    handleArtifactoryDockerSupportForPcf(artifactStream);
    // Add keywords.
    artifactStream.setKeywords(trimmedLowercaseSet(artifactStream.generateKeywords()));
    // Trim out whitespaces
    artifactStream.setName(artifactStream.getName().trim());

    String id = PersistenceValidator.duplicateCheck(
        () -> wingsPersistence.save(artifactStream), "name", artifactStream.getName());
    log.info(ARTIFACT_STREAM_DEBUG_LOG + "Artifact Stream {} created with id: {}", artifactStream.getName(), id);

    yamlPushService.pushYamlChangeSet(
        accountId, null, artifactStream, Type.CREATE, artifactStream.isSyncFromGit(), false);

    if (!artifactStream.isSample()) {
      eventPublishHelper.publishAccountEvent(accountId,
          AccountEvent.builder().accountEventType(AccountEventType.ARTIFACT_STREAM_ADDED).build(), true, true);
    }

    ArtifactStream newArtifactStream = get(id);
    if (newArtifactStream == null) {
      log.info(ARTIFACT_STREAM_DEBUG_LOG + "Artifact Stream Created with id {} is null", id);
    }
    createPerpetualTask(newArtifactStream);
    return newArtifactStream;
  }

  private void handleArtifactoryDockerSupportForPcf(ArtifactStream artifactStream) {
    if (artifactStream instanceof ArtifactoryArtifactStream) {
      ArtifactoryArtifactStream artifactoryArtifactStream = (ArtifactoryArtifactStream) artifactStream;
      if (artifactoryArtifactStream.isUseDockerFormat()) {
        artifactoryArtifactStream.setRepositoryType(RepositoryType.docker.name());
        artifactoryArtifactStream.setMetadataOnly(true);
      }
    }
  }

  private void validateIfNexus2AndParameterized(ArtifactStream artifactStream, String accountId) {
    if (artifactStream != null) {
      if (artifactStream.getArtifactStreamType().equals(NEXUS.name())) {
        if (Boolean.TRUE.equals(artifactStream.getCollectionEnabled())) {
          throw new InvalidRequestException(
              "Auto artifact collection cannot be enabled for parameterized artifact sources");
        }
        SettingValue settingValue = settingsService.getSettingValueById(accountId, artifactStream.getSettingId());
        if (settingValue instanceof NexusConfig
            && !featureFlagService.isEnabled(SPG_ALLOW_TEMPLATE_ON_NEXUS_ARTIFACT, accountId)) {
          NexusConfig nexusConfig = (NexusConfig) settingValue;
          boolean isNexusThree = nexusConfig.getVersion() == null || nexusConfig.getVersion().equalsIgnoreCase("3.x");
          if (isNexusThree) {
            throw new InvalidRequestException("Nexus 3.x artifact stream does not support parameterized fields");
          }
        }
      } else {
        throw new InvalidRequestException(
            format("%s artifact stream does not support parameterized fields", artifactStream.getArtifactStreamType()));
      }
    }
  }

  private void validateIfNexus2AndDockerRepositoryType(ArtifactStream artifactStream, String accountId) {
    if (artifactStream != null) {
      if (artifactStream.getArtifactStreamType().equals(NEXUS.name())) {
        SettingValue settingValue = settingsService.getSettingValueById(accountId, artifactStream.getSettingId());
        if (settingValue instanceof NexusConfig) {
          String nexusVersion = ((NexusConfig) settingValue).getVersion();
          boolean isNexusTwo = nexusVersion != null && nexusVersion.equalsIgnoreCase("2.x");
          if (isNexusTwo && artifactStream instanceof NexusArtifactStream) {
            String repositoryType = ((NexusArtifactStream) artifactStream).getRepositoryType();
            if (repositoryType != null && repositoryType.equals(RepositoryType.docker.name())) {
              throw new InvalidRequestException("Nexus 2.x does not support docker artifact type");
            }
          }
        }
      }
    }
  }

  private void setServiceId(ArtifactStream artifactStream) {
    // set serviceId equal to settingId for ConnectorDisconnectHandler level artifact streams
    String appId = artifactStream.fetchAppId();
    if (appId == null || appId.equals(GLOBAL_APP_ID)) {
      artifactStream.setServiceId(artifactStream.getSettingId());
    }
  }

  private void createPerpetualTask(ArtifactStream artifactStream) {
    if (artifactStream == null || Boolean.FALSE.equals(artifactStream.getCollectionEnabled())) {
      return;
    }

    try {
      subject.fireInform(ArtifactStreamServiceObserver::onSaved, artifactStream);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(ArtifactStreamServiceObserver.class, "onSaved", ArtifactStream.class),
          ArtifactStreamServiceImpl.class, artifactStream);
    } catch (Exception e) {
      log.error(EXCEPTION_OBSERVERS_OF_ARTIFACT_STREAM, e);
    }
  }

  private void resetPerpetualTask(ArtifactStream artifactStream) {
    if (artifactStream == null) {
      return;
    }

    try {
      subject.fireInform(ArtifactStreamServiceObserver::onUpdated, artifactStream);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(ArtifactStreamServiceObserver.class, "onUpdated", ArtifactStream.class),
          ArtifactStreamServiceImpl.class, artifactStream);
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Artifact Stream", e);
    }
  }

  private void deletePerpetualTask(ArtifactStream artifactStream) {
    if (artifactStream == null) {
      return;
    }

    try {
      subject.fireInform(ArtifactStreamServiceObserver::onDeleted, artifactStream);
      remoteObserverInformer.sendEvent(
          ReflectionUtils.getMethod(ArtifactStreamServiceObserver.class, "onDeleted", ArtifactStream.class),
          ArtifactStreamServiceImpl.class, artifactStream);
    } catch (Exception e) {
      log.error("Encountered exception while informing the observers of Artifact Stream", e);
    }
  }

  /**
   * This method gets the default name, checks if another entry exists with the same name, if exists, it parses and
   * extracts the revision and creates a name with the next revision.
   *
   * @param artifactStream
   */
  private void setAutoPopulatedName(ArtifactStream artifactStream) {
    if (artifactStream.isAutoPopulate()) {
      String name = EntityNameValidator.getMappedString(artifactStream.generateName());

      // TODO: ASR: IMP: update this
      // We need to check if the name exists in case of auto generate, if it exists, we need to add a suffix to the
      // name.
      PageRequest<ArtifactStream> pageRequest = aPageRequest()
                                                    .addFilter("appId", EQ, artifactStream.fetchAppId())
                                                    .addFilter("serviceId", EQ, artifactStream.getServiceId())
                                                    .addFilter("name", STARTS_WITH, name)
                                                    .build();
      PageResponse<ArtifactStream> response = wingsPersistence.query(ArtifactStream.class, pageRequest);

      // For the ConnectorDisconnectHandler level also, check if the name exists
      // TODO: ASR: uncomment when index added on setting_id + name
      //      pageRequest = aPageRequest()
      //                        .addFilter("settingId", EQ, artifactStream.getSettingId())
      //                        .addFilter("name", STARTS_WITH, escapedString)
      //                        .build();
      //      PageResponse<ArtifactStream> connectorResponse = wingsPersistence.query(ArtifactStream.class,
      //      pageRequest);
      // If an entry exists with the given default name
      //      if (isNotEmpty(response) || isNotEmpty(connectorResponse)) {
      if (isNotEmpty(response)) {
        name = Utils.getNameWithNextRevision(
            response.getResponse().stream().map(ArtifactStream::getName).collect(toList()), name);
      }
      artifactStream.setName(name);
    }
  }

  @Override
  @ValidationGroups(Update.class)
  public ArtifactStream update(ArtifactStream artifactStream) {
    return update(artifactStream, true);
  }

  @Override
  public ArtifactStream update(ArtifactStream artifactStream, boolean validate) {
    return update(artifactStream, validate, false);
  }

  @Override
  public ArtifactStream update(ArtifactStream artifactStream, boolean validate, boolean fromTemplate) {
    ArtifactStream existingArtifactStream = wingsPersistence.get(ArtifactStream.class, artifactStream.getUuid());
    if (existingArtifactStream == null) {
      throw new NotFoundException("Artifact stream with id " + artifactStream.getUuid() + " not found");
    }

    String accountId = getAccountIdForArtifactStream(artifactStream);
    artifactStream.setAccountId(accountId);

    boolean originalMetadataOnly = artifactStream.isMetadataOnly();
    boolean isArtifactStreamMetadataFFEnabled = featureFlagService.isEnabled(ARTIFACT_STREAM_METADATA_ONLY, accountId);
    if (isArtifactStreamMetadataFFEnabled) {
      artifactStream.setMetadataOnly(true);
    }
    artifactStream.validateRequiredFields();
    if (isArtifactStreamMetadataFFEnabled) {
      artifactStream.setMetadataOnly(originalMetadataOnly);
    }

    if (artifactStream.getArtifactStreamType() != null && existingArtifactStream.getArtifactStreamType() != null
        && !artifactStream.getArtifactStreamType().equals(existingArtifactStream.getArtifactStreamType())) {
      throw new InvalidRequestException("Artifact Stream type cannot be updated", USER);
    }

    if (artifactStream.getAccountId() != null && existingArtifactStream.getAccountId() != null
        && !artifactStream.getAccountId().equals(existingArtifactStream.getAccountId())) {
      throw new InvalidRequestException("Artifact Stream cannot be moved from one account to another", USER);
    } else {
      artifactStream.setAccountId(existingArtifactStream.getAccountId());
    }

    setMetadataOnly(artifactStream);

    if (!metadataOnlyBehindFlag(featureFlagService, artifactStream.getAccountId(), artifactStream.isMetadataOnly())
        && metadataOnlyBehindFlag(
            featureFlagService, existingArtifactStream.getAccountId(), existingArtifactStream.isMetadataOnly())) {
      throw new InvalidRequestException("Artifact Stream's metadata-only property cannot be changed to false", USER);
    }

    artifactStream.setSourceName(artifactStream.generateSourceName());

    // For artifactory.
    validateRepositoryType(artifactStream, existingArtifactStream);
    // For nexus.
    validateRepositoryFormat(artifactStream, existingArtifactStream);
    validateExtensionAndClassifier(artifactStream, existingArtifactStream);
    // For azure artifacts.
    validateProtocolType(artifactStream, existingArtifactStream);

    // for CUSTOM, update scripts and variables only if its not coming from template.
    // If coming from template, the update has already been handled in
    // software.wings.service.impl.template.ArtifactSourceTemplateProcessor.updateLinkedEntities()
    if (!fromTemplate) {
      populateCustomArtifactStreamFields(artifactStream, existingArtifactStream);
    }
    if (artifactStream.getCollectionEnabled() == null) {
      artifactStream.setCollectionEnabled(true);
    }

    // check if artifact stream is parameterized
    boolean streamParameterized = artifactStream.checkIfStreamParameterized();
    if (streamParameterized) {
      // parameterised artifact stream cannot have collection enabled true, forcing it to be false.
      artifactStream.setCollectionEnabled(false);
      validateIfNexus2AndParameterized(artifactStream, accountId);
    }
    artifactStream.setArtifactStreamParameterized(streamParameterized);

    validateArtifactStream(artifactStream);
    if (validate && !streamParameterized) {
      validateArtifactSourceData(artifactStream);
    }

    addAcrHostNameIfNeeded(artifactStream);

    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, artifactStream.getAccountId())) {
      resetArtifactCollectionStatusAndFailedAttempts(artifactStream, existingArtifactStream);
    }

    // Add keywords.
    artifactStream.setKeywords(trimmedLowercaseSet(artifactStream.generateKeywords()));

    ArtifactStream finalArtifactStream = PersistenceValidator.duplicateCheck(
        () -> wingsPersistence.saveAndGet(ArtifactStream.class, artifactStream), "name", artifactStream.getName());

    if (!existingArtifactStream.getSourceName().equals(finalArtifactStream.getSourceName())) {
      if (CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
        artifactService.updateArtifactSourceName(finalArtifactStream);
      }
      executorService.submit(() -> triggerService.updateByArtifactStream(artifactStream.getUuid()));
    }

    if (isEmpty(artifactStream.getName())) {
      throw new InvalidRequestException("Please provide valid artifact name", USER);
    }

    boolean isRename = !artifactStream.getName().equals(existingArtifactStream.getName());
    yamlPushService.pushYamlChangeSet(artifactStream.getAccountId(), existingArtifactStream, finalArtifactStream,
        Type.UPDATE, artifactStream.isSyncFromGit(), isRename);

    if (Boolean.FALSE.equals(artifactStream.getCollectionEnabled())) {
      deletePerpetualTask(artifactStream);
    } else {
      if (shouldDeleteArtifactsOnSourceChanged(existingArtifactStream, finalArtifactStream)
          || shouldDeleteArtifactsOnServerChanged(existingArtifactStream, finalArtifactStream)) {
        deleteArtifacts(accountId, finalArtifactStream);
      } else {
        resetPerpetualTask(finalArtifactStream);
      }
    }

    return finalArtifactStream;
  }

  private void resetArtifactCollectionStatusAndFailedAttempts(
      ArtifactStream artifactStream, ArtifactStream existingArtifactStream) {
    if (Boolean.FALSE.equals(artifactStream.getCollectionEnabled())) {
      return;
    }
    if (existingArtifactStream.artifactServerChanged(artifactStream)
        || existingArtifactStream.artifactSourceChanged(artifactStream)
        || existingArtifactStream.artifactCollectionEnabledFromDisabled(artifactStream)
        || artifactStream.getCollectionStatus() == null) {
      artifactStream.setCollectionStatus(ArtifactStreamCollectionStatus.UNSTABLE.name());
      artifactStream.setFailedCronAttempts(0);
    }
  }

  @Override
  public void deleteArtifacts(String accountId, ArtifactStream artifactStream) {
    // Mark the collection status as unstable (for non-custom) because the artifact source has changed. We will again
    // do a fresh artifact collection.
    if (!CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      updateCollectionStatus(accountId, artifactStream.getUuid(), ArtifactStreamCollectionStatus.UNSTABLE.name());
    }

    // TODO: This logic has to be moved to Prune event or Queue to ensure guaranteed execution
    executorService.submit(() -> {
      artifactService.deleteByArtifactStreamId(artifactStream.getAppId(), artifactStream.getUuid());
      // Perpetual task should only be reset after the artifacts are deleted. Otherwise, cache will become invalid.
      resetPerpetualTask(artifactStream);
    });
  }

  @Override
  public ArtifactStream fetchByArtifactSourceVariableValue(String appId, String variableValue) {
    Pattern pattern = Pattern.compile("(.+)\\((.+)\\)");
    Matcher matcher = pattern.matcher(variableValue);
    if (matcher.find()) {
      String serviceName = matcher.group(2);
      Service service = serviceResourceService.getServiceByName(appId, serviceName);
      notNullCheck("Service with name " + serviceName + " doesn't exist", service);
      String artifactSourceName = matcher.group(1).trim();
      return getArtifactStreamByName(appId, service.getUuid(), artifactSourceName);
    } else {
      throw new InvalidRequestException(
          "The Artifact Source variable should be of the format 'artifactSourceName (serviceName)'");
    }
  }

  @Override
  public boolean deletePerpetualTaskByArtifactStream(String accountId, String artifactStreamId) {
    log.info("Deleting perpetual task associated with artifact stream " + artifactStreamId);
    Query<PerpetualTaskRecord> query = wingsPersistence.createQuery(PerpetualTaskRecord.class)
                                           .field(PerpetualTaskRecordKeys.accountId)
                                           .equal(accountId)
                                           .field(PerpetualTaskRecordKeys.client_params + "."
                                               + ArtifactCollectionPTaskClientParamsKeys.artifactStreamId)
                                           .equal(artifactStreamId);
    return wingsPersistence.delete(query);
  }

  @Override
  public boolean updateLastIterationFields(String accountId, String uuid, boolean success) {
    Query<ArtifactStream> query = wingsPersistence.createQuery(ArtifactStream.class)
                                      .filter(ArtifactStreamKeys.accountId, accountId)
                                      .filter(ArtifactStreamKeys.uuid, uuid);
    long currentTime = System.currentTimeMillis();
    UpdateOperations<ArtifactStream> updateOperations = wingsPersistence.createUpdateOperations(ArtifactStream.class)
                                                            .set(ArtifactStreamKeys.lastIteration, currentTime);
    if (success) {
      updateOperations.set(ArtifactStreamKeys.lastSuccessfulIteration, currentTime);
    }
    UpdateResults update = wingsPersistence.update(query, updateOperations);
    return update.getUpdatedCount() == 1;
  }

  @Override
  public ArtifactStream resetStoppedArtifactCollection(String appId, String artifactStreamId) {
    Query<ArtifactStream> query =
        wingsPersistence.createQuery(ArtifactStream.class)
            .filter(ArtifactStreamKeys.appId, appId)
            .filter(ArtifactStreamKeys.uuid, artifactStreamId)
            .filter(ArtifactStreamKeys.collectionStatus, ArtifactStreamCollectionStatus.STOPPED);
    UpdateOperations<ArtifactStream> updateOperations =
        wingsPersistence.createUpdateOperations(ArtifactStream.class)
            .set(ArtifactStreamKeys.collectionStatus, ArtifactStreamCollectionStatus.UNSTABLE)
            .set(ArtifactStreamKeys.failedCronAttempts, 0);

    UpdateResults updateResults = wingsPersistence.update(query, updateOperations);
    if (updateResults.getUpdatedCount() == 0) {
      throw new InvalidRequestException(format(
          "No valid artifact source available to reset with id %s and artifact collection STOPPED", artifactStreamId));
    }
    alertService.deleteByArtifactStream(appId, artifactStreamId);
    ArtifactStream artifactStream = get(artifactStreamId);
    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, artifactStream.getAccountId())) {
      resetPerpetualTask(artifactStream);
    }
    return artifactStream;
  }

  @Override
  public boolean resetStoppedArtifactCollectionForAccount(String accountId) {
    Query<ArtifactStream> query =
        wingsPersistence.createQuery(ArtifactStream.class)
            .filter(ArtifactStreamKeys.accountId, accountId)
            .filter(ArtifactStreamKeys.collectionStatus, ArtifactStreamCollectionStatus.STOPPED);
    UpdateOperations<ArtifactStream> updateOperations =
        wingsPersistence.createUpdateOperations(ArtifactStream.class)
            .set(ArtifactStreamKeys.collectionStatus, ArtifactStreamCollectionStatus.UNSTABLE)
            .set(ArtifactStreamKeys.failedCronAttempts, 0);

    UpdateResults updateResults = wingsPersistence.update(query, updateOperations);
    if (updateResults.getUpdatedCount() == 0) {
      log.warn("No valid artifact source available to reset with accountId {}", accountId);
    }
    alertService.deleteArtifactStreamAlertForAccount(accountId);
    // not handlng PT cases since they are deprecated.
    return true;
  }

  @Override
  public boolean stopArtifactCollectionForAccount(String accountId) {
    Query<ArtifactStream> query =
        wingsPersistence.createQuery(ArtifactStream.class).filter(ArtifactStreamKeys.accountId, accountId);
    UpdateOperations<ArtifactStream> updateOperations =
        wingsPersistence.createUpdateOperations(ArtifactStream.class)
            .set(ArtifactStreamKeys.collectionStatus, ArtifactStreamCollectionStatus.STOPPED);

    UpdateResults updateResults = wingsPersistence.update(query, updateOperations);
    if (updateResults.getUpdatedCount() == 0) {
      log.warn("No valid artifact source available to stop collecting for accountId {}", accountId);
    }
    // not handlng PT cases since they are deprecated.
    return true;
  }

  @Override
  public void updateCollectionEnabled(ArtifactStream artifactStream, boolean collectionEnabled) {
    wingsPersistence.updateField(
        ArtifactStream.class, artifactStream.getUuid(), ArtifactStreamKeys.collectionEnabled, collectionEnabled);
    if (featureFlagService.isEnabled(FeatureName.ARTIFACT_COLLECTION_CONFIGURABLE, artifactStream.getAccountId())) {
      if (collectionEnabled && artifactStream.getPerpetualTaskId() != null) {
        createPerpetualTask(artifactStream);
      } else {
        deletePerpetualTask(artifactStream);
      }
    }
  }

  @Override
  public Map<String, String> getArtifactStreamNames(String appId, Set<String> artifactStreamIds) {
    if (isEmpty(artifactStreamIds)) {
      return Collections.emptyMap();
    }
    List<ArtifactStream> artifactStreams = wingsPersistence.createQuery(ArtifactStream.class)
                                               .field(ServiceKeys.appId)
                                               .equal(appId)
                                               .field(ServiceKeys.uuid)
                                               .in(artifactStreamIds)
                                               .project(ServiceKeys.name, true)
                                               .project(ServiceKeys.uuid, true)
                                               .asList();

    Map<String, String> artifactStreamIdToNameMap = new HashMap<>();
    artifactStreams.forEach(
        artifactStream -> artifactStreamIdToNameMap.put(artifactStream.getUuid(), artifactStream.getName()));
    return artifactStreamIdToNameMap;
  }

  private void populateCustomArtifactStreamFields(
      ArtifactStream artifactStream, ArtifactStream existingArtifactStream) {
    boolean versionChanged = false;
    List<Variable> oldTemplateVariables = existingArtifactStream.getTemplateVariables();
    if (artifactStream.getTemplateVersion() != null && existingArtifactStream.getTemplateVersion() != null
        && !artifactStream.getTemplateVersion().equals(existingArtifactStream.getTemplateVersion())) {
      versionChanged = true;
    }

    if (versionChanged || existingArtifactStream.getTemplateUuid() == null) {
      if (artifactStream.getTemplateUuid() != null) {
        String version = artifactStream.getTemplateVersion() != null ? artifactStream.getTemplateVersion() : LATEST_TAG;
        ArtifactStream artifactStreamFromTemplate = (ArtifactStream) templateService.constructEntityFromTemplate(
            artifactStream.getTemplateUuid(), version, EntityType.ARTIFACT_STREAM);
        notNullCheck("Template does not exist", artifactStreamFromTemplate, USER);
        // merge template variables and existing artifact stream values
        List<Variable> templateVariables = templateHelper.overrideVariables(
            artifactStreamFromTemplate.getTemplateVariables(), oldTemplateVariables, false);
        // Merge incoming template variables from payload with the template variables computed above
        // incoming template variable values get precedence over the computed values
        if (isNotEmpty(artifactStream.getTemplateVariables()) && isNotEmpty(templateVariables)) {
          for (Variable templateVariable : templateVariables) {
            for (Variable artifactStreamVariable : artifactStream.getTemplateVariables()) {
              if (templateVariable.getName().equals(artifactStreamVariable.getName())) {
                templateVariable.setValue(artifactStreamVariable.getValue());
              }
            }
          }
        }
        artifactStream.setTemplateVariables(templateVariables);
        if (artifactStream.getArtifactStreamType().equals(CUSTOM.name())) {
          ((CustomArtifactStream) artifactStream)
              .setScripts(((CustomArtifactStream) artifactStreamFromTemplate).getScripts());
        }
      }
    } else if (existingArtifactStream.getTemplateUuid() != null) {
      // through yaml flow: when only template variables are changed we update those but preserve the existing script
      artifactStream.setTemplateVariables(
          templateHelper.overrideVariables(artifactStream.getTemplateVariables(), oldTemplateVariables, false));
      if (artifactStream.getArtifactStreamType().equals(CUSTOM.name())) {
        ((CustomArtifactStream) artifactStream)
            .setScripts(((CustomArtifactStream) existingArtifactStream).getScripts());
      }
    }
  }

  private String getAccountIdForArtifactStream(ArtifactStream artifactStream) {
    String accountId = null;
    String appId = artifactStream.fetchAppId();
    if (appId == null || !appId.equals(GLOBAL_APP_ID)) {
      accountId = appService.getAccountIdByAppId(appId);
    } else {
      if (artifactStream.getSettingId() != null) {
        accountId = settingsService.fetchAccountIdBySettingId(artifactStream.getSettingId());
      }
    }
    return accountId;
  }

  private void validateRepositoryType(ArtifactStream artifactStream, ArtifactStream existingArtifactStream) {
    if (artifactStream != null && existingArtifactStream != null) {
      if (artifactStream.getArtifactStreamType().equals(ARTIFACTORY.name())) {
        if (!((ArtifactoryArtifactStream) artifactStream)
                 .getRepositoryType()
                 .equals(((ArtifactoryArtifactStream) existingArtifactStream).getRepositoryType())) {
          throw new InvalidRequestException("Repository Type cannot be updated", USER);
        }
      }
    }
  }

  private void validateRepositoryFormat(ArtifactStream artifactStream, ArtifactStream existingArtifactStream) {
    if (artifactStream != null && existingArtifactStream != null) {
      if (artifactStream.getArtifactStreamType().equals(NEXUS.name())) {
        NexusArtifactStream nexusArtifactStream = (NexusArtifactStream) artifactStream;
        if (nexusArtifactStream.getRepositoryFormat() == null
            || !nexusArtifactStream.getRepositoryFormat().equals(
                ((NexusArtifactStream) existingArtifactStream).getRepositoryFormat())) {
          throw new InvalidRequestException("Repository Format cannot be updated", USER);
        }
      }
    }
  }

  private void validateExtensionAndClassifier(ArtifactStream artifactStream, ArtifactStream existingArtifactStream) {
    if (artifactStream != null && existingArtifactStream != null) {
      if (artifactStream instanceof NexusArtifactStream
          && RepositoryFormat.maven.name().equals(((NexusArtifactStream) artifactStream).getRepositoryFormat())
          && !existingArtifactStream.artifactSourceChanged(artifactStream)) {
        String extension = ((NexusArtifactStream) artifactStream).getExtension();
        String existingExtension = ((NexusArtifactStream) existingArtifactStream).getExtension();
        if ((extension == null && existingExtension != null) || (extension != null && existingExtension == null)
            || (extension != null && !extension.equals(existingExtension))) {
          throw new InvalidRequestException("Extension cannot be updated", USER);
        }

        String classifier = ((NexusArtifactStream) artifactStream).getClassifier();
        String existingClassifier = ((NexusArtifactStream) existingArtifactStream).getClassifier();
        if ((classifier == null && existingClassifier != null) || (classifier != null && existingClassifier == null)
            || (classifier != null && !classifier.equals(existingClassifier))) {
          throw new InvalidRequestException("Classifier cannot be updated", USER);
        }
      }
    }
  }

  private void validateProtocolType(ArtifactStream artifactStream, ArtifactStream existingArtifactStream) {
    if (artifactStream != null && existingArtifactStream != null) {
      if (artifactStream.getArtifactStreamType().equals(AZURE_ARTIFACTS.name())) {
        if (!((AzureArtifactsArtifactStream) artifactStream)
                 .getProtocolType()
                 .equals(((AzureArtifactsArtifactStream) existingArtifactStream).getProtocolType())) {
          throw new InvalidRequestException("Protocol type cannot be updated", USER);
        }
      }
    }
  }

  // TODO: move this to individual ArtifactStream classes instead of handling here
  private void setMetadataOnly(ArtifactStream artifactStream) {
    if (artifactStream != null && artifactStream.getArtifactStreamType().equals(NEXUS.name())) {
      if (RepositoryFormat.docker.name().equals(((NexusArtifactStream) artifactStream).getRepositoryFormat())) {
        artifactStream.setMetadataOnly(true);
      }
    }
    if (artifactStream != null && AZURE_ARTIFACTS.name().equals(artifactStream.getArtifactStreamType())) {
      artifactStream.setMetadataOnly(true);
    }
  }

  private void addAcrHostNameIfNeeded(ArtifactStream artifactStream) {
    if (artifactStream instanceof AcrArtifactStream) {
      AcrArtifactStream acrArtifactStream = (AcrArtifactStream) artifactStream;
      AzureContainerRegistry registry =
          azureResourceService
              .listContainerRegistries(acrArtifactStream.getSettingId(), acrArtifactStream.getSubscriptionId())
              .stream()
              .filter(item -> item.getName().equals(acrArtifactStream.getRegistryName()))
              .findFirst()
              .get();
      acrArtifactStream.setRegistryHostName(registry.getLoginServer());
    }
  }

  private boolean shouldDeleteArtifactsOnSourceChanged(
      ArtifactStream oldArtifactStream, ArtifactStream updatedArtifactStream) {
    ArtifactStreamType artifactStreamType = ArtifactStreamType.valueOf(oldArtifactStream.getArtifactStreamType());
    switch (artifactStreamType) {
      case CUSTOM:
        return false;
      case AMI:
      case ARTIFACTORY:
      case AMAZON_S3:
      case NEXUS:
      case ECR:
      case DOCKER:
      case GCR:
      case ACR:
      case GCS:
      case SMB:
      case SFTP:
      case JENKINS:
      case BAMBOO:
      case AZURE_ARTIFACTS:
      case AZURE_MACHINE_IMAGE:
        return oldArtifactStream.artifactSourceChanged(updatedArtifactStream);

      default:
        throw new InvalidRequestException(
            "Artifact source changed check not covered for Artifact Stream Type [" + artifactStreamType + "]");
    }
  }

  private boolean shouldDeleteArtifactsOnServerChanged(
      ArtifactStream oldArtifactStream, ArtifactStream updatedArtifactStream) {
    ArtifactStreamType artifactStreamType = ArtifactStreamType.valueOf(oldArtifactStream.getArtifactStreamType());
    if (artifactStreamType != CUSTOM) {
      return oldArtifactStream.artifactServerChanged(updatedArtifactStream);
    }
    return false;
  }

  private void validateArtifactStream(ArtifactStream artifactStream) {
    String artifactStreamType = artifactStream.getArtifactStreamType();
    String settingId = artifactStream.getSettingId();
    if (isNotBlank(settingId) && isNotBlank(artifactStreamType)) {
      SettingAttribute settingAttribute = settingsService.get(settingId);
      notNullCheck("Artifact server was not found", settingAttribute, USER);
      SettingValue settingValue = settingAttribute.getValue();
      ArtifactStreamType type = ArtifactStreamType.valueOf(artifactStreamType);
      // Only check for AZURE_MACHINE_IMAGE as we are not sure if the mapping covers all cases
      if (!AZURE_MACHINE_IMAGE.equals(type)) {
        return;
      }
      if (ArtifactStreamType.supportedSettingVariableTypes.containsKey(type)
          && !ArtifactStreamType.supportedSettingVariableTypes.get(type).contains(
              SettingVariableTypes.valueOf(settingValue.getType()))) {
        throw new InvalidRequestException(String.format(
            "Invalid setting type %s for artifact stream type %s", settingValue.getType(), artifactStreamType));
      }
    }
    if (AZURE_MACHINE_IMAGE.name().equals(artifactStreamType)) {
      buildSourceService.validateAndInferArtifactSource(artifactStream);
    }
  }

  private void validateArtifactSourceData(ArtifactStream artifactStream) {
    String artifactStreamType = artifactStream.getArtifactStreamType();
    if (Lists.newArrayList(DOCKER, ECR, GCR, ACR, ARTIFACTORY, NEXUS, AZURE_ARTIFACTS)
            .stream()
            .anyMatch(type -> type.name().equals(artifactStreamType))) {
      if (artifactStream.shouldValidate()) {
        buildSourceService.validateArtifactSource(artifactStream.fetchAppId(), artifactStream.getSettingId(),
            artifactStream.fetchArtifactStreamAttributes(featureFlagService));
      }
    } else if (CUSTOM.name().equals(artifactStreamType) && artifactStream.shouldValidate()) {
      try {
        buildSourceService.validateArtifactSource(artifactStream);
      } catch (ShellExecutionException ex) {
        String message = "Unable to execute script since it contains errors " + ex.getMessage();
        if (ex.getCause() != null) {
          message = message + ex.getCause().getMessage();
        }
        log.warn(message, ex);
        throw new ShellExecutionException("Custom Artifact script execution failed with following error: "
            + ExceptionUtils.getMessage(ex) + ", Please verify the script.");
      }
    }
  }

  private void ensureArtifactStreamSafeToDelete(String appId, String artifactStreamId) {
    validateTriggerUsages(appId, artifactStreamId);
  }

  private void validateTriggerUsages(String appId, String artifactStreamId) {
    List<String> triggerNames;
    List<Trigger> triggers = triggerService.getTriggersHasArtifactStreamAction(appId, artifactStreamId);
    if (isEmpty(triggers)) {
      return;
    }
    triggerNames = triggers.stream().map(Trigger::getName).collect(toList());

    if (isNotEmpty(triggerNames)) {
      throw new InvalidRequestException(
          format("Artifact Stream associated as a trigger action to triggers [%s]", String.join(", ", triggerNames)),
          USER);
    }
  }

  @Override
  public boolean delete(String appId, String artifactStreamId) {
    return delete(appId, artifactStreamId, false, false);
  }

  @Override
  public boolean delete(String artifactStreamId, boolean syncFromGit) {
    log.info(ARTIFACT_STREAM_DEBUG_LOG + "Delete artifact stream {} from setting resource", artifactStreamId);
    ArtifactStream artifactStream = get(artifactStreamId);
    if (artifactStream == null) {
      return true;
    }

    if (!GLOBAL_APP_ID.equals(artifactStream.fetchAppId())) {
      // delete method used only for artifact streams defined at ConnectorDisconnectHandler level
      throw new NotFoundException("Artifact stream with id " + artifactStreamId + " not found");
    }

    // TODO: check if used in triggers
    String accountId = settingsService.fetchAccountIdBySettingId(artifactStream.getSettingId());
    if (GLOBAL_APP_ID.equals(artifactStream.fetchAppId())) {
      if (!usageRestrictionsService.userHasPermissionsToChangeEntity(accountId, ACCOUNT_MANAGEMENT,
              settingsService.getUsageRestrictionsForSettingId(artifactStream.getSettingId()), false)) {
        throw new UnauthorizedUsageRestrictionsException(USER);
      }
      ensureArtifactStreamSafeToDelete(GLOBAL_APP_ID, artifactStreamId);
    }

    artifactStream.setSyncFromGit(syncFromGit);
    boolean retVal = pruneArtifactStream(artifactStream);
    yamlPushService.pushYamlChangeSet(accountId, artifactStream, null, Type.DELETE, syncFromGit, false);
    return retVal;
  }

  private boolean delete(String appId, String artifactStreamId, boolean forceDelete, boolean syncFromGit) {
    ArtifactStream artifactStream = get(artifactStreamId);
    if (artifactStream == null) {
      return true;
    }

    return delete(appId, artifactStream, forceDelete, syncFromGit);
  }

  private boolean delete(String appId, ArtifactStream artifactStream, boolean forceDelete, boolean syncFromGit) {
    log.info(ARTIFACT_STREAM_DEBUG_LOG + "Deleting artifact stream {}", artifactStream.getUuid());
    String accountId = null;
    if (!GLOBAL_APP_ID.equals(appId)) {
      accountId = appService.getAccountIdByAppId(artifactStream.getAppId());
    } else {
      if (artifactStream.getSettingId() != null) {
        accountId = settingsService.fetchAccountIdBySettingId(artifactStream.getSettingId());
        if (!usageRestrictionsService.userHasPermissionsToChangeEntity(accountId, ACCOUNT_MANAGEMENT,
                settingsService.getUsageRestrictionsForSettingId(artifactStream.getSettingId()), false)) {
          throw new UnauthorizedUsageRestrictionsException(USER);
        }
      }
    }

    String artifactStreamId = artifactStream.getUuid();
    if (!forceDelete) {
      ensureArtifactStreamSafeToDelete(appId, artifactStreamId);
    }

    artifactStream.setSyncFromGit(syncFromGit);
    boolean retVal = pruneArtifactStream(artifactStream);
    yamlPushService.pushYamlChangeSet(accountId, artifactStream, null, Type.DELETE, syncFromGit, false);
    return retVal;
  }

  @Override
  public boolean pruneArtifactStream(ArtifactStream artifactStream) {
    boolean retVal =
        pruneArtifactStream(artifactStream.fetchAppId(), artifactStream.getUuid(), artifactStream.isSyncFromGit());
    deletePerpetualTask(artifactStream);
    return retVal;
  }

  private boolean pruneArtifactStream(String appId, String artifactStreamId, boolean gitSync) {
    log.info(ARTIFACT_STREAM_DEBUG_LOG + "Deleting Artifact Stream with id: {}", artifactStreamId);
    alertService.deleteByArtifactStream(appId, artifactStreamId);
    pruneQueue.send(new PruneEvent(ArtifactStream.class, appId, artifactStreamId, gitSync));
    boolean retVal = wingsPersistence.delete(ArtifactStream.class, appId, artifactStreamId);
    artifactStreamServiceBindingService.deleteByArtifactStream(artifactStreamId, gitSync);
    return retVal;
  }

  @Override
  public boolean attachPerpetualTaskId(ArtifactStream artifactStream, String perpetualTaskId) {
    // NOTE: Before using this method, ensure that perpetualTaskId does not exist for artifact stream, otherwise we'll
    // have an extra perpetual task running for this.
    Query<ArtifactStream> query = wingsPersistence.createQuery(ArtifactStream.class)
                                      .filter(ArtifactStreamKeys.accountId, artifactStream.getAccountId())
                                      .filter(ArtifactStreamKeys.uuid, artifactStream.getUuid())
                                      .field(ArtifactStreamKeys.perpetualTaskId)
                                      .doesNotExist();
    UpdateOperations<ArtifactStream> updateOperations = wingsPersistence.createUpdateOperations(ArtifactStream.class)
                                                            .set(ArtifactStreamKeys.perpetualTaskId, perpetualTaskId);
    UpdateResults updateResults = wingsPersistence.update(query, updateOperations);

    return updateResults.getUpdatedCount() == 1;
  }

  @Override
  public boolean detachPerpetualTaskId(String perpetualTaskId) {
    Query<ArtifactStream> query = wingsPersistence.createQuery(ArtifactStream.class, excludeAuthority)
                                      .filter(ArtifactStreamKeys.perpetualTaskId, perpetualTaskId);
    UpdateOperations<ArtifactStream> updateOperations =
        wingsPersistence.createUpdateOperations(ArtifactStream.class).unset(ArtifactStreamKeys.perpetualTaskId);
    UpdateResults updateResults = wingsPersistence.update(query, updateOperations);

    return updateResults.getUpdatedCount() >= 1;
  }

  @Override
  public void pruneDescendingEntities(@NotEmpty String appId, @NotEmpty String artifactStreamId) {
    List<OwnedByArtifactStream> services = software.wings.service.impl.ServiceClassLocator.descendingServices(
        this, ArtifactStreamServiceImpl.class, OwnedByArtifactStream.class);
    PruneEntityListener.pruneDescendingEntities(
        services, descending -> descending.pruneByArtifactStream(appId, artifactStreamId));
  }

  @Override
  public boolean artifactStreamsExistForService(String appId, String serviceId) {
    return isNotEmpty(artifactStreamServiceBindingService.listArtifactStreamIds(appId, serviceId));
  }

  @Override
  public List<ArtifactStream> getArtifactStreamsForService(String appId, String serviceId) {
    return artifactStreamServiceBindingService.listArtifactStreams(appId, serviceId);
  }

  @Override
  public List<ArtifactStream> getArtifactStreamsForService(String appId, String serviceId, List<String> projections) {
    Query<ArtifactStream> query = wingsPersistence.createQuery(ArtifactStream.class)
                                      .filter(ArtifactStreamKeys.appId, appId)
                                      .filter(ArtifactStreamKeys.serviceId, serviceId);

    if (isNotEmpty(projections)) {
      for (String projectionKey : projections) {
        query.project(projectionKey, true);
      }
    }

    return query.asList();
  }

  @Override
  public Map<String, String> fetchArtifactSourceProperties(String accountId, String artifactStreamId) {
    ArtifactStream artifactStream = wingsPersistence.get(ArtifactStream.class, artifactStreamId);
    Map<String, String> artifactSourceProperties = new HashMap<>();
    if (artifactStream == null) {
      log.warn("Failed to construct artifact source properties. ArtifactStream {} was deleted", artifactStreamId);
      return artifactSourceProperties;
    }
    SettingValue settingValue = settingsService.getSettingValueById(accountId, artifactStream.getSettingId());
    if (settingValue instanceof ArtifactSourceable) {
      artifactSourceProperties.putAll(((ArtifactSourceable) settingValue).fetchArtifactSourceProperties());
    }
    artifactSourceProperties.putAll(artifactStream.fetchArtifactSourceProperties());

    return artifactSourceProperties;
  }

  @Override
  public List<ArtifactStream> fetchArtifactStreamsForService(String appId, String serviceId) {
    return artifactStreamServiceBindingService.listArtifactStreams(appId, serviceId);
  }

  @Override
  public List<String> fetchArtifactStreamIdsForService(String appId, String serviceId) {
    return artifactStreamServiceBindingService.listArtifactStreamIds(appId, serviceId);
  }

  @Override
  public Map<String, String> getSupportedBuildSourceTypes(String appId, String serviceId) {
    Service service = serviceResourceService.get(appId, serviceId, false);
    // Observed NPE in logs due to invalid service id provided by the ui due to a stale screen.
    if (service == null) {
      throw new InvalidRequestException("Service does not exist", USER);
    }

    ImmutableMap.Builder<String, String> builder;
    switch (service.getArtifactType()) {
      case DOCKER:
        builder = new ImmutableMap.Builder<String, String>()
                      .put(DOCKER.name(), DOCKER.name())
                      .put(ECR.name(), ECR.name())
                      .put(ACR.name(), ACR.name())
                      .put(GCR.name(), GCR.name())
                      .put(ARTIFACTORY.name(), ARTIFACTORY.name())
                      .put(NEXUS.name(), NEXUS.name())
                      .put(CUSTOM.name(), CUSTOM.name());
        break;
      case AWS_LAMBDA:
        builder = new ImmutableMap.Builder<String, String>()
                      .put(AMAZON_S3.name(), AMAZON_S3.name())
                      .put(ARTIFACTORY.name(), ARTIFACTORY.name())
                      .put(NEXUS.name(), NEXUS.name())
                      .put(JENKINS.name(), JENKINS.name())
                      .put(CUSTOM.name(), CUSTOM.name());
        break;
      case AMI:
        builder =
            new ImmutableMap.Builder<String, String>().put(AMI.name(), AMI.name()).put(CUSTOM.name(), CUSTOM.name());
        break;
      case AZURE_MACHINE_IMAGE:
        builder =
            new ImmutableMap.Builder<String, String>().put(AZURE_MACHINE_IMAGE.name(), AZURE_MACHINE_IMAGE.name());
        break;
      case AZURE_WEBAPP:
        builder = new ImmutableMap.Builder<String, String>()
                      .put(DOCKER.name(), DOCKER.name())
                      .put(ARTIFACTORY.name(), ARTIFACTORY.name())
                      .put(AZURE_ARTIFACTS.name(), AZURE_ARTIFACTS.name());
        break;
      case OTHER:
        builder = new ImmutableMap.Builder<String, String>()
                      .put(DOCKER.name(), DOCKER.name())
                      .put(ECR.name(), ECR.name())
                      .put(ACR.name(), ACR.name())
                      .put(GCR.name(), GCR.name())
                      .put(ARTIFACTORY.name(), ARTIFACTORY.name())
                      .put(NEXUS.name(), NEXUS.name())
                      .put(JENKINS.name(), JENKINS.name())
                      .put(BAMBOO.name(), BAMBOO.name())
                      .put(GCS.name(), GCS.name())
                      .put(AMAZON_S3.name(), AMAZON_S3.name())
                      .put(AMI.name(), AMI.name())
                      .put(AZURE_ARTIFACTS.name(), AZURE_ARTIFACTS.name())
                      .put(SMB.name(), SMB.name())
                      .put(SFTP.name(), SFTP.name())
                      .put(CUSTOM.name(), CUSTOM.name());
        break;
      case PCF:
        builder = new ImmutableMap.Builder<String, String>()
                      .put(ARTIFACTORY.name(), ARTIFACTORY.name())
                      .put(NEXUS.name(), NEXUS.name())
                      .put(JENKINS.name(), JENKINS.name())
                      .put(BAMBOO.name(), BAMBOO.name())
                      .put(GCS.name(), GCS.name())
                      .put(AMAZON_S3.name(), AMAZON_S3.name())
                      .put(AZURE_ARTIFACTS.name(), AZURE_ARTIFACTS.name())
                      .put(SMB.name(), SMB.name())
                      .put(SFTP.name(), SFTP.name())
                      .put(DOCKER.name(), DOCKER.name())
                      .put(ECR.name(), ECR.name())
                      .put(GCR.name(), GCR.name())
                      .put(CUSTOM.name(), CUSTOM.name());
        break;
      default:
        builder = new ImmutableMap.Builder<String, String>()
                      .put(ARTIFACTORY.name(), ARTIFACTORY.name())
                      .put(NEXUS.name(), NEXUS.name())
                      .put(JENKINS.name(), JENKINS.name())
                      .put(BAMBOO.name(), BAMBOO.name())
                      .put(GCS.name(), GCS.name())
                      .put(AMAZON_S3.name(), AMAZON_S3.name())
                      .put(AMI.name(), AMI.name())
                      .put(AZURE_ARTIFACTS.name(), AZURE_ARTIFACTS.name())
                      .put(SMB.name(), SMB.name())
                      .put(SFTP.name(), SFTP.name())
                      .put(CUSTOM.name(), CUSTOM.name());
    }
    return builder.build();
  }

  @Override
  public void pruneByService(String appId, String serviceId) {
    // TODO: ASR: IMP: remove this after refactor - currently doesn't allow deleting if appId is GLOBAL_APP_ID
    if (appId == null || GLOBAL_APP_ID.equals(appId)) {
      return;
    }

    wingsPersistence.createQuery(ArtifactStream.class)
        .filter(ArtifactStreamKeys.appId, appId)
        .filter(ArtifactStreamKeys.serviceId, serviceId)
        .asList()
        .forEach(artifactStream -> {
          pruneArtifactStream(artifactStream);
          auditServiceHelper.reportDeleteForAuditing(appId, artifactStream);
        });
  }

  @Override
  public boolean updateFailedCronAttemptsAndLastIteration(
      String accountId, String artifactStreamId, int counter, boolean success) {
    Query<ArtifactStream> query = wingsPersistence.createQuery(ArtifactStream.class)
                                      .filter(ArtifactStreamKeys.accountId, accountId)
                                      .filter(ArtifactStreamKeys.uuid, artifactStreamId);
    UpdateOperations<ArtifactStream> updateOperations = wingsPersistence.createUpdateOperations(ArtifactStream.class)
                                                            .set(ArtifactStreamKeys.failedCronAttempts, counter);
    long currentTime = System.currentTimeMillis();
    updateOperations.set(ArtifactStreamKeys.lastIteration, currentTime);
    if (success) {
      updateOperations.set(ArtifactStreamKeys.lastSuccessfulIteration, currentTime);
    }

    UpdateResults update = wingsPersistence.update(query, updateOperations);
    return update.getUpdatedCount() == 1;
  }

  @Override
  public boolean updateCollectionStatus(String accountId, String artifactStreamId, String collectionStatus) {
    Query<ArtifactStream> query = wingsPersistence.createQuery(ArtifactStream.class)
                                      .filter(ArtifactStreamKeys.accountId, accountId)
                                      .filter(ArtifactStreamKeys.uuid, artifactStreamId);
    UpdateOperations<ArtifactStream> updateOperations = wingsPersistence.createUpdateOperations(ArtifactStream.class);
    setUnset(updateOperations, ArtifactStreamKeys.collectionStatus, collectionStatus);
    UpdateResults update = wingsPersistence.update(query, updateOperations);
    return update.getUpdatedCount() == 1;
  }

  @Override
  public List<ArtifactStream> listAllBySettingId(String settingId) {
    return wingsPersistence.createQuery(ArtifactStream.class, excludeAuthority)
        .filter(ArtifactStreamKeys.settingId, settingId)
        .asList();
  }

  @Override
  public List<ArtifactStream> listBySettingId(String settingId) {
    return wingsPersistence.createQuery(ArtifactStream.class, excludeAuthority)
        .filter(ArtifactStreamKeys.settingId, settingId)
        .asList(new FindOptions().limit(REFERENCED_ENTITIES_TO_SHOW));
  }

  @Override
  public List<ArtifactStream> listByIds(Collection<String> artifactStreamIds) {
    if (isEmpty(artifactStreamIds)) {
      return new ArrayList<>();
    }

    List<ArtifactStream> artifactStreams = wingsPersistence.createQuery(ArtifactStream.class, excludeAuthority)
                                               .field("_id")
                                               .in(artifactStreamIds)
                                               .asList();

    if (isNotEmpty(artifactStreams)) {
      List<ArtifactStream> orderedArtifactStreams = new ArrayList<>();
      Map<String, ArtifactStream> artifactStreamMap =
          artifactStreams.stream().collect(Collectors.toMap(ArtifactStream::getUuid, Function.identity()));
      for (String artifactStreamId : artifactStreamIds) {
        if (artifactStreamMap.containsKey(artifactStreamId)) {
          orderedArtifactStreams.add(artifactStreamMap.get(artifactStreamId));
        }
      }
      return orderedArtifactStreams;
    }
    return new ArrayList<>();
  }

  @Override
  public List<ArtifactStreamSummary> listArtifactStreamSummary(String appId) {
    Map<String, ArtifactStream> artifactStreamMap = getArtifactStreamMap(appId);
    List<ArtifactStreamSummary> artifactStreamSummaries = new ArrayList<>();
    try (HIterator<Service> serviceHIterator = new HIterator<>(wingsPersistence.createQuery(Service.class)
                                                                   .filter(ServiceKeys.appId, appId)
                                                                   .project(ServiceKeys.uuid, true)
                                                                   .project(ServiceKeys.name, true)
                                                                   .project(ServiceKeys.artifactStreamIds, true)
                                                                   .fetch())) {
      for (Service service : serviceHIterator) {
        List<String> artifactStreamIds = service.getArtifactStreamIds();
        if (isEmpty(artifactStreamIds)) {
          continue;
        }

        String serviceName = service.getName();
        artifactStreamSummaries.addAll(artifactStreamIds.stream()
                                           .filter(artifactStreamMap::containsKey)
                                           .map(artifactStreamId -> {
                                             ArtifactStream artifactStream = artifactStreamMap.get(artifactStreamId);
                                             return ArtifactStreamSummary.builder()
                                                 .artifactStreamId(artifactStreamId)
                                                 .settingId(artifactStream.getSettingId())
                                                 .displayName(artifactStream.getName() + " (" + serviceName + ")")
                                                 .build();
                                           })
                                           .collect(Collectors.toList()));
      }
    }

    return artifactStreamSummaries;
  }

  private Map<String, ArtifactStream> getArtifactStreamMap(String appId) {
    String accountId = appService.getAccountIdByAppId(appId);

    Map<String, ArtifactStream> artifactStreamMap = new HashMap<>();
    try (HIterator<ArtifactStream> artifactStreamHIterator =
             new HIterator<>(wingsPersistence.createQuery(ArtifactStream.class)
                                 .filter(ArtifactStreamKeys.accountId, accountId)
                                 .project(ArtifactStreamKeys.uuid, true)
                                 .project(ArtifactStreamKeys.name, true)
                                 .fetch())) {
      for (ArtifactStream artifactStream : artifactStreamHIterator) {
        artifactStreamMap.put(artifactStream.getUuid(), artifactStream);
      }
    }
    return artifactStreamMap;
  }

  @Override
  @ValidationGroups(Create.class)
  public ArtifactStream createWithBinding(String appId, ArtifactStream artifactStream, boolean validate) {
    if (GLOBAL_APP_ID.equals(artifactStream.fetchAppId())) {
      return create(artifactStream, validate);
    }

    String serviceId = artifactStream.getServiceId();
    notNullCheck("ArtifactStream.serviceId", serviceId);

    // TODO: ASR: IMP: hack to make yaml push work as yaml changes require binding info but the binding info is deleted
    // in parallel
    artifactStream.setService(serviceResourceService.getWithDetails(appId, serviceId));

    // NOTE: artifactStream and binding must be created atomically
    ArtifactStream savedArtifactStream = create(artifactStream, validate);
    try {
      artifactStreamServiceBindingService.createOld(appId, serviceId, savedArtifactStream.getUuid());
    } catch (Exception e) {
      log.error(ARTIFACT_STREAM_DEBUG_LOG + "Exception occurred: ", e);
      delete(appId, savedArtifactStream, false, false);
      throw e;
    }

    if (!artifactStream.isSample()) {
      eventPublishHelper.publishAccountEvent(savedArtifactStream.getAccountId(),
          AccountEvent.builder().accountEventType(AccountEventType.ARTIFACT_STREAM_ADDED).build(), true, true);
    }

    return savedArtifactStream;
  }

  @Override
  @ValidationGroups(Create.class)
  public boolean deleteWithBinding(String appId, String artifactStreamId, boolean forceDelete, boolean syncFromGit) {
    log.info(ARTIFACT_STREAM_DEBUG_LOG + "Delete artifact stream {} with binding", artifactStreamId);
    if (GLOBAL_APP_ID.equals(appId)) {
      return delete(artifactStreamId, syncFromGit);
    }

    ArtifactStream artifactStream = get(artifactStreamId);
    if (artifactStream == null) {
      return false;
    }

    String serviceId = artifactStream.getServiceId();
    notNullCheck("ArtifactStream.serviceId", serviceId);

    // TODO: ASR: IMP: hack to make yaml push work as yaml changes require binding info but the binding info is deleted
    // in parallel
    artifactStream.setService(serviceResourceService.getWithDetails(appId, serviceId));

    // NOTE: Binding is deleted internally by this method.
    return delete(appId, artifactStream, forceDelete, syncFromGit);
  }

  @Override
  public List<ArtifactStream> listBySettingId(String appId, String settingId) {
    return wingsPersistence.createQuery(ArtifactStream.class, excludeAuthority)
        .filter(ArtifactStreamKeys.appId, appId)
        .filter(ArtifactStreamKeys.settingId, settingId)
        .asList();
  }

  @Override
  public List<ArtifactStream> listByAppId(String appId) {
    if (GLOBAL_APP_ID.equals(appId)) {
      return new ArrayList<>();
    }

    Set<String> artifactStreamIds = new HashSet<>();
    serviceResourceService.findServicesByApp(appId).forEach(service -> {
      List<String> serviceArtifactStreamIds = artifactStreamServiceBindingService.listArtifactStreamIds(service);
      if (isNotEmpty(serviceArtifactStreamIds)) {
        artifactStreamIds.addAll(serviceArtifactStreamIds);
      }
    });

    return listByIds(artifactStreamIds);
  }

  @Override
  public List<String> getArtifactStreamParameters(String artifactStreamId) {
    ArtifactStream artifactStream = get(artifactStreamId);
    if (artifactStream == null) {
      throw new InvalidRequestException(format("Artifact stream with id [%s] not found", artifactStreamId));
    }
    if (!artifactStream.isArtifactStreamParameterized()) {
      throw new InvalidRequestException(format("Artifact stream with id [%s] not parameterized", artifactStreamId));
    }
    validateIfNexus2AndParameterized(artifactStream, artifactStream.getAccountId());
    return artifactStream.fetchArtifactStreamParameters();
  }

  public static ArtifactSummary prepareSummaryFromArtifact(Artifact artifact) {
    if (artifact == null) {
      return null;
    }

    return ArtifactSummary.builder()
        .uuid(artifact.getUuid())
        .uiDisplayName(artifact.getUiDisplayName())
        .buildNo(artifact.getBuildNo())
        .artifactStreamId(artifact.getArtifactStreamId())
        .build();
  }
}
