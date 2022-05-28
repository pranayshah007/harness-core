/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.cdng.gitops.ClusterServiceConstants.CLUSTER_DOES_NOT_EXIST;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.gitops.entity.Cluster.ClusterKeys;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.DuplicateKeyExceptionParser;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.repositories.gitops.spring.ClusterRepository;
import io.harness.utils.PageUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(GITOPS)
@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class ClusterServiceImpl implements ClusterService {
  private final ClusterRepository clusterRepository;

  @Override
  public Optional<Cluster> get(String orgIdentifier, String projectIdentifier, String accountId, String envIdentifier,
      String clusterRef, boolean deleted) {
    Criteria criteria =
        getClusterEqualityCriteria(accountId, orgIdentifier, projectIdentifier, envIdentifier, clusterRef);
    return Optional.ofNullable(clusterRepository.findOne(criteria));
  }

  @Override
  public Cluster create(Cluster cluster) {
    try {
      return clusterRepository.create(cluster);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(
          getDuplicateExistsErrorMessage(cluster.getAccountId(), cluster.getOrgIdentifier(),
              cluster.getProjectIdentifier(), cluster.getClusterRef()),
          USER, ex);
    }
  }

  @Override
  public Cluster update(Cluster cluster) {
    Criteria criteria = getClusterEqualityCriteria(cluster);
    Cluster updated = clusterRepository.update(criteria, cluster);
    if (updated == null) {
      throw new InvalidRequestException(String.format(
          CLUSTER_DOES_NOT_EXIST, cluster.getClusterRef(), cluster.getProjectIdentifier(), cluster.getOrgIdentifier()));
    }
    return updated;
  }

  @Override
  public Page<Cluster> bulkCreate(String accountId, List<Cluster> entities) {
    try {
      List<Cluster> savedEntities = (List<Cluster>) clusterRepository.saveAll(entities);
      return new PageImpl<>(savedEntities);
    } catch (DuplicateKeyException ex) {
      throw new DuplicateFieldException(getDuplicateExistsErrorMessage(accountId, ex.getMessage()), USER, ex);
    } catch (Exception ex) {
      String clusters = entities.stream().map(Cluster::getClusterRef).collect(Collectors.joining(","));
      log.info("Encountered exception while saving the Cluster entity records of [{}], with exception", clusters, ex);
      throw new UnexpectedException("Encountered exception while saving the Cluster entity records.");
    }
  }

  @Override
  public boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String clusterRef) {
    Criteria criteria =
        getClusterEqualityCriteria(accountId, orgIdentifier, projectIdentifier, envIdentifier, clusterRef);
    DeleteResult delete = clusterRepository.delete(criteria);
    return delete.wasAcknowledged() && delete.getDeletedCount() == 1;
  }

  @Override
  public DeleteResult deleteFromAllEnv(
      String accountId, String orgIdentifier, String projectIdentifier, String clusterRef) {
    Criteria criteria = getClusterEqualityCriteria(accountId, orgIdentifier, projectIdentifier, clusterRef);
    return clusterRepository.delete(criteria);
  }

  public Page<Cluster> list(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envRef, String searchTerm, List<String> clusterRefs, List<String> sort) {
    Criteria criteria =
        createCriteriaForGetList(accountIdentifier, orgIdentifier, projectIdentifier, envRef, searchTerm);
    Pageable pageRequest;
    if (isNotEmpty(clusterRefs)) {
      criteria.and(ClusterKeys.clusterRef).in(clusterRefs);
    }
    if (isEmpty(sort)) {
      pageRequest = org.springframework.data.domain.PageRequest.of(
          page, size, Sort.by(Sort.Direction.DESC, ClusterKeys.createdAt));
    } else {
      pageRequest = PageUtils.getPageRequest(page, size, sort);
    }

    return clusterRepository.find(criteria, pageRequest);
  }

  private Criteria getClusterEqualityCriteria(@Valid Cluster cluster) {
    return getClusterEqualityCriteria(cluster.getAccountId(), cluster.getOrgIdentifier(),
        cluster.getProjectIdentifier(), cluster.getEnvRef(), cluster.getClusterRef());
  }

  private Criteria getClusterEqualityCriteria(
      String accountId, String orgId, String projectId, String envIdentifier, String identifier) {
    return where(ClusterKeys.accountId)
        .is(accountId)
        .and(ClusterKeys.orgIdentifier)
        .is(orgId)
        .and(ClusterKeys.projectIdentifier)
        .is(projectId)
        .and(ClusterKeys.clusterRef)
        .is(identifier)
        .and(ClusterKeys.envRef)
        .is(envIdentifier);
  }

  private Criteria getClusterEqualityCriteria(String accountId, String orgId, String projectId, String identifier) {
    return where(ClusterKeys.accountId)
        .is(accountId)
        .and(ClusterKeys.orgIdentifier)
        .is(orgId)
        .and(ClusterKeys.projectIdentifier)
        .is(projectId)
        .and(ClusterKeys.clusterRef)
        .is(identifier);
  }

  private String getDuplicateExistsErrorMessage(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String clusterRef) {
    if (isEmpty(orgIdentifier)) {
      return String.format(
          ClusterServiceConstants.DUP_KEY_EXP_FORMAT_STRING_FOR_ACCOUNT, clusterRef, accountIdentifier);
    } else if (isEmpty(projectIdentifier)) {
      return String.format(
          ClusterServiceConstants.DUP_KEY_EXP_FORMAT_STRING_FOR_ORG, clusterRef, orgIdentifier, accountIdentifier);
    }
    return String.format(ClusterServiceConstants.DUP_KEY_EXP_FORMAT_STRING_FOR_PROJECT, clusterRef, projectIdentifier,
        orgIdentifier, accountIdentifier);
  }

  String getDuplicateExistsErrorMessage(String accountId, String exceptionString) {
    String errorMessageToBeReturned;
    try {
      JSONObject jsonObjectOfDuplicateKey = DuplicateKeyExceptionParser.getDuplicateKey(exceptionString);
      if (jsonObjectOfDuplicateKey != null) {
        String orgIdentifier = jsonObjectOfDuplicateKey.getString("orgIdentifier");
        String projectIdentifier = jsonObjectOfDuplicateKey.getString("projectIdentifier");
        String clusterRef = jsonObjectOfDuplicateKey.getString("clusterRef");
        errorMessageToBeReturned =
            getDuplicateExistsErrorMessage(accountId, orgIdentifier, projectIdentifier, clusterRef);
      } else {
        errorMessageToBeReturned = "A Duplicate cluster already exists";
      }
    } catch (Exception ex) {
      errorMessageToBeReturned = "A Duplicate cluster already exists";
    }
    return errorMessageToBeReturned;
  }

  public Criteria createCriteriaForGetList(
      String accountId, String orgIdentifier, String projectIdentifier, String envRef, String searchTerm) {
    Criteria criteria = CoreCriteriaUtils.createCriteriaForGetList(accountId, orgIdentifier, projectIdentifier);
    criteria.and(ClusterKeys.envRef).is(envRef);
    if (isNotEmpty(searchTerm)) {
      Criteria searchCriteria = new Criteria().orOperator(
          where(ClusterKeys.clusterRef).regex(searchTerm, NGResourceFilterConstants.CASE_INSENSITIVE_MONGO_OPTIONS));
      criteria.andOperator(searchCriteria);
    }
    return criteria;
  }
}
