/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.count;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.skip;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.unwind;

import io.harness.network.Http;
import io.harness.repositories.ArtifactRepository;
import io.harness.repositories.CdInstanceSummaryRepo;
import io.harness.repositories.EnforcementSummaryRepo;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewResponse.AttestedStatusEnum;
import io.harness.spec.server.ssca.v1.model.ArtifactDeploymentViewResponse.TypeEnum;
import io.harness.spec.server.ssca.v1.model.ArtifactDetailResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactListingRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponse.ActivityEnum;
import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.ssca.beans.EnforcementSummaryDBO.EnforcementSummaryDBOKeys;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.SbomDTO;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.entities.EnforcementSummaryEntity;
import io.harness.ssca.entities.EnforcementSummaryEntity.EnforcementSummaryEntityKeys;
import io.harness.ssca.utils.SBOMUtils;

import com.google.inject.Inject;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.UriBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.CountOperation;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.LimitOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.aggregation.SkipOperation;
import org.springframework.data.mongodb.core.aggregation.SortOperation;
import org.springframework.data.mongodb.core.aggregation.UnwindOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
public class ArtifactServiceImpl implements ArtifactService {
  @Inject ArtifactRepository artifactRepository;
  @Inject EnforcementSummaryRepo enforcementSummaryRepo;
  @Inject NormalisedSbomComponentService normalisedSbomComponentService;
  @Inject CdInstanceSummaryRepo cdInstanceSummaryRepo;
  @Inject CdInstanceSummaryService cdInstanceSummaryService;

  private final String GCP_REGISTRY_HOST = "grc.io";

  @Override
  public ArtifactEntity getArtifactFromSbomPayload(
      String accountId, String orgIdentifier, String projectIdentifier, SbomProcessRequestBody body, SbomDTO sbomDTO) {
    String artifactId = generateArtifactId(body.getArtifact().getRegistryUrl(), body.getArtifact().getName());
    return ArtifactEntity.builder()
        .id(UUID.randomUUID().toString())
        .artifactId(artifactId)
        .orchestrationId(body.getSbomMetadata().getStepExecutionId())
        .pipelineExecutionId(body.getSbomMetadata().getPipelineExecutionId())
        .artifactCorrelationId(getCDImagePath(
            body.getArtifact().getRegistryUrl(), body.getArtifact().getName(), body.getArtifact().getTag()))
        .name(body.getArtifact().getName())
        .orgId(orgIdentifier)
        .projectId(projectIdentifier)
        .accountId(accountId)
        .sbomName(body.getSbomProcess().getName())
        .type(body.getArtifact().getType())
        .url(body.getArtifact().getRegistryUrl())
        .pipelineId(body.getSbomMetadata().getPipelineIdentifier())
        .stageId(body.getSbomMetadata().getStageIdentifier())
        .tag(body.getArtifact().getTag())
        .isAttested(body.getAttestation().isIsAttested())
        .attestedFileUrl(body.getAttestation().getUrl())
        .stepId(body.getSbomMetadata().getStepIdentifier())
        .sequenceId(body.getSbomMetadata().getSequenceId())
        .createdOn(Instant.now())
        .sbom(ArtifactEntity.Sbom.builder()
                  .tool(body.getSbomMetadata().getTool())
                  .toolVersion("2.0")
                  .sbomFormat(body.getSbomProcess().getFormat())
                  .sbomVersion(SBOMUtils.getSbomVersion(sbomDTO))
                  .build())
        .build();
  }

  @Override
  public Optional<ArtifactEntity> getArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String orchestrationId) {
    return artifactRepository.findByAccountIdAndOrgIdAndProjectIdAndOrchestrationId(
        accountId, orgIdentifier, projectIdentifier, orchestrationId);
  }

  @Override
  public Optional<ArtifactEntity> getArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, Sort sort) {
    return artifactRepository.findFirstByAccountIdAndOrgIdAndProjectIdAndArtifactIdLike(
        accountId, orgIdentifier, projectIdentifier, artifactId, sort);
  }

  @Override
  public ArtifactEntity getArtifactByCorrelationId(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactCorrelationId) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.artifactCorrelationId)
                            .is(artifactCorrelationId)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);
    return artifactRepository.findOne(criteria);
  }

  @Override
  public ArtifactEntity getLatestArtifact(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String tag) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.artifactId)
                            .is(artifactId)
                            .and(ArtifactEntityKeys.tag)
                            .is(tag)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);
    return artifactRepository.findOne(criteria);
  }

  @Override
  public ArtifactDetailResponse getArtifactDetails(
      String accountId, String orgIdentifier, String projectIdentifier, String artifactId, String tag) {
    ArtifactEntity artifact = getLatestArtifact(accountId, orgIdentifier, projectIdentifier, artifactId, tag);
    if (Objects.isNull(artifact)) {
      throw new NotFoundException(
          String.format("Artifact with artifactId [%s] and tag [%s] is not found", artifactId, tag));
    }
    return new ArtifactDetailResponse()
        .artifactId(artifact.getArtifactId())
        .artifactName(artifact.getName())
        .tag(artifact.getTag())
        .componentsCount(artifact.getComponentsCount().intValue())
        .updated(String.format("%d", artifact.getLastUpdatedAt()))
        .prodEnvCount(artifact.getProdEnvCount().intValue())
        .nonProdEnvCount(artifact.getNonProdEnvCount().intValue())
        .buildPipelineId(artifact.getPipelineId())
        .buildPipelineExecutionId(artifact.getPipelineExecutionId());
  }

  @Override
  public String generateArtifactId(String registryUrl, String name) {
    return UUID.nameUUIDFromBytes((registryUrl + ":" + name).getBytes()).toString();
  }

  @Override
  @Transactional
  public void saveArtifactAndInvalidateOldArtifact(ArtifactEntity artifact) {
    artifactRepository.invalidateOldArtifact(artifact);
    artifact.setLastUpdatedAt(artifact.getCreatedOn().toEpochMilli());
    artifactRepository.save(artifact);
  }

  @Override
  public Page<ArtifactListingResponse> listLatestArtifacts(
      String accountId, String orgIdentifier, String projectIdentifier, Pageable pageable) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);

    MatchOperation matchOperation = match(criteria);
    SortOperation sortOperation =
        sort(Sort.by(Direction.DESC, ArtifactEntityKeys.lastUpdatedAt).and(pageable.getSort()));
    GroupOperation groupByArtifactId = group(ArtifactEntityKeys.artifactId).first("$$ROOT").as("document");
    SkipOperation skipOperation = skip(pageable.getOffset());
    LimitOperation limitOperation = limit(pageable.getPageSize());
    ProjectionOperation projectionOperation = new ProjectionOperation().andExclude("_id");

    Aggregation aggregation = Aggregation.newAggregation(
        matchOperation, sortOperation, groupByArtifactId, projectionOperation, skipOperation, limitOperation);
    List<ArtifactEntity> artifactEntities = artifactRepository.findAll(aggregation);
    // Aggregation Query: { "aggregate" : "__collection__", "pipeline" : [{ "$match" : { "accountId" :
    // "kmpySmUISimoRrJL6NL73w", "orgId" : "default", "projectId" : "LocalPipeline", "invalid" : false}}, { "$sort" : {
    // "lastUpdatedAt" : -1, "sort" : 1}}, { "$group" : { "_id" : "$artifactId", "document" : { "$first" : "$$ROOT"}}},
    // { "$project" : { "_id" : 0}}, { "$skip" : skip}, { "$limit" : limit}]}
    List<ArtifactListingResponse> artifactListingResponses =
        getArtifactListingResponses(accountId, orgIdentifier, projectIdentifier, artifactEntities);

    CountOperation countOperation = count().as("count");
    aggregation = Aggregation.newAggregation(matchOperation, groupByArtifactId, countOperation);
    long total = artifactRepository.getCount(aggregation);

    return new PageImpl<>(artifactListingResponses, pageable, total);
  }

  @Override
  public Page<ArtifactListingResponse> listArtifacts(String accountId, String orgIdentifier, String projectIdentifier,
      ArtifactListingRequestBody body, Pageable pageable) {
    Criteria criteria = Criteria.where(ArtifactEntityKeys.accountId)
                            .is(accountId)
                            .and(ArtifactEntityKeys.orgId)
                            .is(orgIdentifier)
                            .and(ArtifactEntityKeys.projectId)
                            .is(projectIdentifier)
                            .and(ArtifactEntityKeys.invalid)
                            .is(false);

    criteria.andOperator(getPolicyFilterCriteria(body), getDeploymentFilterCriteria(body));

    Page<ArtifactEntity> artifactEntities = artifactRepository.findAll(criteria, pageable);

    List<ArtifactListingResponse> artifactListingResponses =
        getArtifactListingResponses(accountId, orgIdentifier, projectIdentifier, artifactEntities.toList());

    return new PageImpl<>(artifactListingResponses, pageable, artifactEntities.getTotalElements());
  }

  @Override
  public Page<ArtifactComponentViewResponse> getArtifactComponentView(String accountId, String orgIdentifier,
      String projectIdentifier, String artifactId, String tag, ArtifactComponentViewRequestBody filterBody,
      Pageable pageable) {
    ArtifactEntity artifact = getLatestArtifact(accountId, orgIdentifier, projectIdentifier, artifactId, tag);
    if (Objects.isNull(artifact)) {
      throw new NotFoundException(String.format("No Artifact Found with {id: %s}", artifactId));
    }

    return normalisedSbomComponentService
        .getNormalizedSbomComponents(accountId, orgIdentifier, projectIdentifier, artifact, filterBody, pageable)
        .map(entity
            -> new ArtifactComponentViewResponse()
                   .name(entity.getPackageName())
                   .license(String.join(", ", entity.getPackageLicense()))
                   .packageManager(entity.getPackageManager())
                   .supplier(entity.getPackageOriginatorName())
                   .purl(entity.getPurl())
                   .version(entity.getPackageVersion()));
  }

  @Override
  public Page<ArtifactDeploymentViewResponse> getArtifactDeploymentView(String accountId, String orgIdentifier,
      String projectIdentifier, String artifactId, String tag, ArtifactDeploymentViewRequestBody filterBody,
      Pageable pageable) {
    ArtifactEntity artifact = getLatestArtifact(accountId, orgIdentifier, projectIdentifier, artifactId, tag);
    if (Objects.isNull(artifact)) {
      throw new NotFoundException(String.format("No Artifact Found with {id: %s}", artifactId));
    }

    return cdInstanceSummaryService
        .getCdInstanceSummaries(accountId, orgIdentifier, projectIdentifier, artifact, filterBody, pageable)
        .map(entity
            -> new ArtifactDeploymentViewResponse()
                   .id(entity.getEnvIdentifier())
                   .name(entity.getEnvName())
                   .type(entity.getEnvType() == EnvType.Production ? TypeEnum.PROD : TypeEnum.NONPROD)
                   .attestedStatus(artifact.isAttested() ? AttestedStatusEnum.PASS : AttestedStatusEnum.FAIL)
                   .pipelineId(entity.getLastPipelineExecutionName())
                   .pipelineExecutionId(entity.getLastPipelineExecutionId())
                   .triggeredById(entity.getLastDeployedById())
                   .triggeredBy(entity.getLastDeployedByName())
                   .triggeredAt(entity.getLastDeployedAt().toString()));
  }

  @Override
  public void updateArtifactEnvCount(ArtifactEntity artifact, EnvType envType, long count) {
    if (envType == EnvType.Production) {
      long envCount = Long.max(artifact.getProdEnvCount() + count, 0);
      artifact.setProdEnvCount(envCount);
    } else {
      long envCount = Long.max(artifact.getNonProdEnvCount() + count, 0);
      artifact.setNonProdEnvCount(envCount);
    }
    artifact.setLastUpdatedAt(Instant.now().toEpochMilli());
    artifactRepository.save(artifact);
  }

  private List<ArtifactListingResponse> getArtifactListingResponses(
      String accountId, String orgIdentifier, String projectIdentifier, List<ArtifactEntity> artifactEntities) {
    List<String> orchestrationIds =
        artifactEntities.stream().map(ArtifactEntity::getOrchestrationId).collect(Collectors.toList());

    Criteria criteria = Criteria.where(EnforcementSummaryEntityKeys.orchestrationId).in(orchestrationIds);
    MatchOperation matchOperation = match(criteria);
    SortOperation sortOperation = sort(Sort.by(Direction.DESC, EnforcementSummaryEntityKeys.createdAt));
    GroupOperation groupByOrchestrationId =
        group(EnforcementSummaryEntityKeys.orchestrationId).first("$$ROOT").as("document");
    Aggregation aggregation = newAggregation(matchOperation, sortOperation, groupByOrchestrationId);

    // Aggregate Query: { "aggregate" : "__collection__", "pipeline" : [{ "$match" : { "orchestrationId" : { "$in" :
    // ["unique123", "unique12", "unique12"]}}}, { "$sort" : { "createdAt" : -1}}, { "$group" : { "_id" :
    // "$orchestrationId", "document" : { "$first" : "$$ROOT"}}}]}
    List<EnforcementSummaryEntity> enforcementSummaryEntities = enforcementSummaryRepo.findAll(aggregation);
    Map<String, EnforcementSummaryEntity> enforcementSummaryEntityMap = enforcementSummaryEntities.stream().collect(
        Collectors.toMap(entity -> entity.getOrchestrationId(), Function.identity()));

    List<ArtifactListingResponse> responses = new ArrayList<>();
    for (ArtifactEntity artifact : artifactEntities) {
      EnforcementSummaryEntity enforcementSummary = EnforcementSummaryEntity.builder().build();

      if (enforcementSummaryEntityMap.containsKey(artifact.getOrchestrationId())) {
        enforcementSummary = enforcementSummaryEntityMap.get(artifact.getOrchestrationId());
      }

      responses.add(
          new ArtifactListingResponse()
              .artifactId(artifact.getArtifactId())
              .artifactName(artifact.getName())
              .tag(artifact.getTag())
              .componentsCount(artifact.getComponentsCount().intValue())
              .allowListViolationCount(enforcementSummary.getAllowListViolationCount())
              .denyListViolationCount(enforcementSummary.getDenyListViolationCount())
              .enforcementId(enforcementSummary.getEnforcementId())
              .activity(artifact.getProdEnvCount() + artifact.getNonProdEnvCount() == 0 ? ActivityEnum.GENERATED
                                                                                        : ActivityEnum.DEPLOYED)
              .updatedAt(String.format("%d", artifact.getLastUpdatedAt()))
              .prodEnvCount(artifact.getProdEnvCount().intValue())
              .nonProdEnvCount(artifact.getNonProdEnvCount().intValue()));
    }
    return responses;
  }

  private String getCDImagePath(String url, String image, String tag) {
    URI uri = UriBuilder.fromUri(url).build();
    String registryUrl = UriBuilder.fromUri(url).path(uri.getPath().endsWith("/") ? "" : "/").build().toString();
    String domainName = Http.getDomainWithPort(registryUrl);
    if (domainName.contains(GCP_REGISTRY_HOST)) {
      return image + ":" + tag;
    }
    return domainName + "/" + image + ":" + tag;
  }

  private Criteria getPolicyFilterCriteria(ArtifactListingRequestBody body) {
    Criteria criteria = new Criteria();
    if (Objects.isNull(body) || Objects.isNull(body.getPolicyViolation())) {
      return criteria;
    }
    Criteria enforcementCriteria = new Criteria();
    switch (body.getPolicyViolation()) {
      case DENY:
        enforcementCriteria = Criteria
                                  .where(EnforcementSummaryDBOKeys.document + "."
                                      + EnforcementSummaryEntityKeys.denyListViolationCount.toLowerCase())
                                  .ne(0);
        break;
      case ALLOW:
        enforcementCriteria = Criteria
                                  .where(EnforcementSummaryDBOKeys.document + "."
                                      + EnforcementSummaryEntityKeys.allowListViolationCount.toLowerCase())
                                  .ne(0);
        break;
      default:
        log.error("Unknown Policy Violation Type");
    }

    MatchOperation matchOperation = match(enforcementCriteria);
    SortOperation sortOperation = sort(Sort.by(Direction.DESC, EnforcementSummaryEntityKeys.createdAt));
    GroupOperation groupByOrchestrationId =
        group(EnforcementSummaryEntityKeys.orchestrationId).first("$$ROOT").as(EnforcementSummaryDBOKeys.document);
    UnwindOperation unwindOperation = unwind(EnforcementSummaryDBOKeys.document);
    Aggregation aggregation = newAggregation(sortOperation, groupByOrchestrationId, unwindOperation, matchOperation);

    // { "aggregate" : "__collection__", "pipeline" : [{ "$sort" : { "createdAt" : -1}}, { "$group" : { "_id" :
    // "$orchestrationId", "document" : { "$first" : "$$ROOT"}}}, { "$unwind" : "$document"}, { "$match" : {
    // "document.denylistviolationcount" : { "$ne" : 0}}}]}
    List<EnforcementSummaryEntity> enforcementSummaryEntities = enforcementSummaryRepo.findAll(aggregation);
    criteria.and(ArtifactEntityKeys.orchestrationId)
        .in(enforcementSummaryEntities.stream()
                .map(EnforcementSummaryEntity::getOrchestrationId)
                .collect(Collectors.toSet()));
    return criteria;
  }

  private Criteria getDeploymentFilterCriteria(ArtifactListingRequestBody body) {
    if (Objects.isNull(body) || Objects.isNull(body.getEnvironmentType())) {
      return new Criteria();
    }
    switch (body.getEnvironmentType()) {
      case NONPROD:
        return Criteria.where(ArtifactEntityKeys.nonProdEnvCount).gt(0);
      case PROD:
        return Criteria.where(ArtifactEntityKeys.prodEnvCount).gt(0);
      case ALL:
        return Criteria.where(ArtifactEntityKeys.nonProdEnvCount).gt(0).and(ArtifactEntityKeys.prodEnvCount).gt(0);
      case NONE:
        return Criteria.where(ArtifactEntityKeys.nonProdEnvCount).is(0).and(ArtifactEntityKeys.prodEnvCount).is(0);
      default:
        log.error("Unknown Policy Environment Type");
    }
    return new Criteria();
  }
}
