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
import io.harness.spec.server.ssca.v1.model.ArtifactListingRequestBody;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponse;
import io.harness.spec.server.ssca.v1.model.ArtifactListingResponse.ActivityEnum;
import io.harness.spec.server.ssca.v1.model.SbomProcessRequestBody;
import io.harness.ssca.beans.EnvType;
import io.harness.ssca.beans.SbomDTO;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.entities.CdInstanceSummary;
import io.harness.ssca.entities.CdInstanceSummary.CdInstanceSummaryKeys;
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
                   .triggeredBy(entity.getLastDeployedByName()));
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

    List<String> artifactCorrelationIds =
        artifactEntities.stream().map(ArtifactEntity::getArtifactCorrelationId).collect(Collectors.toList());
    Criteria artifactDeploymentCriteria = Criteria.where(CdInstanceSummaryKeys.artifactCorrelationId)
                                              .in(artifactCorrelationIds)
                                              .and(CdInstanceSummaryKeys.accountIdentifier)
                                              .is(accountId)
                                              .and(CdInstanceSummaryKeys.orgIdentifier)
                                              .is(orgIdentifier)
                                              .and(CdInstanceSummaryKeys.projectIdentifier)
                                              .is(projectIdentifier);
    List<CdInstanceSummary> cdInstanceSummaries = cdInstanceSummaryRepo.findAll(artifactDeploymentCriteria);

    Map<String, List<CdInstanceSummary>> artifactDeploymentMap = cdInstanceSummaries.stream().collect(
        Collectors.groupingBy(cdInstanceSummary -> cdInstanceSummary.getArtifactCorrelationId()));

    List<ArtifactListingResponse> responses = new ArrayList<>();
    for (ArtifactEntity artifact : artifactEntities) {
      List<CdInstanceSummary> deploymentSummary = new ArrayList<>();
      EnforcementSummaryEntity enforcementSummary = EnforcementSummaryEntity.builder().build();

      if (artifactDeploymentMap.containsKey(artifact.getArtifactCorrelationId())) {
        deploymentSummary = artifactDeploymentMap.get(artifact.getArtifactCorrelationId());
      }

      if (enforcementSummaryEntityMap.containsKey(artifact.getOrchestrationId())) {
        enforcementSummary = enforcementSummaryEntityMap.get(artifact.getOrchestrationId());
      }

      responses.add(
          new ArtifactListingResponse()
              .artifactId(artifact.getArtifactId())
              .artifactName(artifact.getName())
              .tag(artifact.getTag())
              .componentsCount((int) artifact.getComponentsCount())
              .allowListViolationCount(enforcementSummary.getAllowListViolationCount())
              .denyListViolationCount(enforcementSummary.getDenyListViolationCount())
              .activity(Objects.isNull(deploymentSummary) ? ActivityEnum.GENERATED : ActivityEnum.DEPLOYED)
              .updatedAt(String.format("%d", artifact.getLastUpdatedAt()))
              .prodEnvCount((int) deploymentSummary.stream()
                                .filter(cdInstanceSummary -> cdInstanceSummary.getEnvType() == EnvType.Production)
                                .count())
              .nonProdEnvCount((int) deploymentSummary.stream()
                                   .filter(cdInstanceSummary -> cdInstanceSummary.getEnvType() == EnvType.PreProduction)
                                   .count()));
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
}
