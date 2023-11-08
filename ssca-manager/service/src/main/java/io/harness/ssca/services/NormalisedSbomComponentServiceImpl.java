/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.services;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.exception.InvalidRequestException;
import io.harness.repositories.SBOMComponentRepo;
import io.harness.spec.server.ssca.v1.model.Artifact;
import io.harness.spec.server.ssca.v1.model.ArtifactComponentViewRequestBody;
import io.harness.spec.server.ssca.v1.model.ComponentFilter;
import io.harness.spec.server.ssca.v1.model.LicenseFilter;
import io.harness.spec.server.ssca.v1.model.NormalizedSbomComponentDTO;
import io.harness.ssca.entities.ArtifactEntity;
import io.harness.ssca.entities.ArtifactEntity.ArtifactEntityKeys;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;
import io.harness.ssca.transformers.NormalisedSbomComponentTransformer;
import io.harness.utils.ApiUtils;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

public class NormalisedSbomComponentServiceImpl implements NormalisedSbomComponentService {
  @Inject SBOMComponentRepo sbomComponentRepo;

  @Inject ArtifactService artifactService;
  Map<ComponentFilter.FieldNameEnum, String> componentFilterToFieldNameMap =
      Map.of(ComponentFilter.FieldNameEnum.COMPONENTNAME, NormalizedSBOMEntityKeys.packageName,
          ComponentFilter.FieldNameEnum.COMPONENTVERSION, NormalizedSBOMEntityKeys.packageVersion);
  @Override
  public Response listNormalizedSbomComponent(
      String orgIdentifier, String projectIdentifier, Integer page, Integer limit, Artifact body, String accountId) {
    Pageable pageRequest = PageRequest.of(page, limit);
    String artifactId = artifactService.generateArtifactId(body.getRegistryUrl(), body.getName());
    ArtifactEntity artifact =
        artifactService
            .getArtifact(accountId, orgIdentifier, projectIdentifier, artifactId,
                Sort.by(ArtifactEntityKeys.createdOn).descending())
            .orElseThrow(()
                             -> new NotFoundException(
                                 String.format("Artifact with image name [%s] and registry Url [%s] is not found",
                                     body.getName(), body.getRegistryUrl())));
    Page<NormalizedSBOMComponentEntity> entities =
        sbomComponentRepo.findByAccountIdAndOrgIdentifierAndProjectIdentifierAndOrchestrationId(
            accountId, orgIdentifier, projectIdentifier, artifact.getOrchestrationId(), pageRequest);
    Page<NormalizedSbomComponentDTO> result = entities.map(entity -> NormalisedSbomComponentTransformer.toDTO(entity));
    ResponseBuilder responseBuilder = Response.ok();
    ResponseBuilder responseBuilderWithLinks =
        ApiUtils.addLinksHeader(responseBuilder, entities.getTotalElements(), page, limit);
    return responseBuilderWithLinks.entity(result.getContent()).build();
  }

  public Criteria getLicenseCriteria(LicenseFilter licenseFilter) {
    Criteria criteria = new Criteria();
    switch (licenseFilter.getOperator()) {
      case EQUALS:
        return criteria.and(NormalizedSBOMEntityKeys.packageLicense).is(licenseFilter.getValue());
      case CONTAINS:
        return criteria.and(NormalizedSBOMEntityKeys.packageLicense).regex(licenseFilter.getValue());
      case STARTSWITH:
        return criteria.and(NormalizedSBOMEntityKeys.packageLicense)
            .regex(Pattern.compile("^".concat(licenseFilter.getValue())));
      default:
        throw new InvalidRequestException("Invalid component filter operator");
    }
  }

  public Criteria getComponentCriteria(List<ComponentFilter> componentFilter) {
    Criteria componentCriteria = new Criteria();
    Criteria[] componentFilterCriteria =
        componentFilter.stream()
            .map(filter -> {
              if (isEmpty(String.valueOf(filter.getFieldName()))) {
                throw new InvalidRequestException("fieldName cannot be null");
              }
              String fieldName = componentFilterToFieldNameMap.get(filter.getFieldName());
              switch (filter.getOperator()) {
                case EQUALS:
                  return Criteria.where(fieldName).is(filter.getValue());
                case CONTAINS:
                  return Criteria.where(fieldName).regex(filter.getValue());
                case STARTSWITH:
                  return Criteria.where(fieldName).regex(Pattern.compile("^".concat(filter.getValue())));
                default:
                  throw new InvalidRequestException("Invalid component filter operator");
              }
            })
            .toArray(Criteria[] ::new);
    if (isNotEmpty(componentFilterCriteria)) {
      componentCriteria.orOperator(componentFilterCriteria);
    }
    return componentCriteria;
  }
  public Page<NormalizedSBOMComponentEntity> getNormalizedSbomComponents(String accountId, String orgIdentifier,
      String projectIdentifier, ArtifactEntity artifact, ArtifactComponentViewRequestBody filterBody,
      Pageable pageable) {
    Criteria criteria = Criteria.where(NormalizedSBOMEntityKeys.accountId)
                            .is(accountId)
                            .and(NormalizedSBOMEntityKeys.orgIdentifier)
                            .is(orgIdentifier)
                            .and(NormalizedSBOMEntityKeys.projectIdentifier)
                            .is(projectIdentifier)
                            .and(NormalizedSBOMEntityKeys.orchestrationId)
                            .is(artifact.getOrchestrationId());

    if (Objects.nonNull(filterBody) && Objects.nonNull(filterBody.getPackageManager())) {
      Pattern pattern = Pattern.compile("[.]*" + filterBody.getPackageManager() + "[.]*");
      criteria.and(NormalizedSBOMEntityKeys.packageManager).regex(pattern);
    }
    if (Objects.nonNull(filterBody) && Objects.nonNull(filterBody.getPackageSupplier())) {
      Pattern pattern = Pattern.compile("[.]*" + filterBody.getPackageSupplier() + "[.]*");
      criteria.and(NormalizedSBOMEntityKeys.packageOriginatorName).regex(pattern);
    }

    if (Objects.nonNull(filterBody) && Objects.nonNull(filterBody.getLicenseFilter())) {
      criteria.andOperator(getLicenseCriteria(filterBody.getLicenseFilter()));
    }

    if (Objects.nonNull(filterBody) && Objects.nonNull(filterBody.getComponentFilter())) {
      criteria.andOperator(getComponentCriteria(filterBody.getComponentFilter()));
    }

    return sbomComponentRepo.findAll(criteria, pageable);
  }

  public List<String> getOrchestrationIds(
      String accountId, String orgIdentifier, String projectIdentifier, LicenseFilter licenseFilter) {
    if (Objects.nonNull(licenseFilter)) {
      Criteria criteria = Criteria.where(NormalizedSBOMEntityKeys.accountId)
                              .is(accountId)
                              .and(NormalizedSBOMEntityKeys.orgIdentifier)
                              .is(orgIdentifier)
                              .and(NormalizedSBOMEntityKeys.projectIdentifier)
                              .is(projectIdentifier);

      criteria.andOperator(getLicenseCriteria(licenseFilter));
      return sbomComponentRepo.findAll(criteria, Pageable.unpaged())
          .map(NormalizedSBOMComponentEntity::getOrchestrationId)
          .toList();
    }
    return new ArrayList<>();
  }

  public List<String> getOrchestrationIds(
      String accountId, String orgIdentifier, String projectIdentifier, List<ComponentFilter> componentFilter) {
    if (isNotEmpty(componentFilter)) {
      Criteria criteria = Criteria.where(NormalizedSBOMEntityKeys.accountId)
                              .is(accountId)
                              .and(NormalizedSBOMEntityKeys.orgIdentifier)
                              .is(orgIdentifier)
                              .and(NormalizedSBOMEntityKeys.projectIdentifier)
                              .is(projectIdentifier);

      criteria.andOperator(getComponentCriteria(componentFilter));
      return sbomComponentRepo.findAll(criteria, Pageable.unpaged())
          .map(NormalizedSBOMComponentEntity::getOrchestrationId)
          .toList();
    }
    return new ArrayList<>();
  }
}
