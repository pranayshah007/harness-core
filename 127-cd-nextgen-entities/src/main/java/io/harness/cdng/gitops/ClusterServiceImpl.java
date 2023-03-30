/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.exception.WingsException.USER;

import static com.google.common.base.Preconditions.checkArgument;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.NGResourceFilterConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.ScopeLevel;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.cdng.gitops.entity.Cluster.ClusterKeys;
import io.harness.cdng.gitops.service.ClusterService;
import io.harness.exception.DuplicateFieldException;
import io.harness.exception.UnexpectedException;
import io.harness.ng.DuplicateKeyExceptionParser;
import io.harness.ng.core.utils.CoreCriteriaUtils;
import io.harness.repositories.gitops.spring.ClusterRepository;
import io.harness.utils.IdentifierRefHelper;
import io.harness.utils.PageUtils;
import io.harness.utils.ScopeWiseIds;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
  public Optional<Cluster> get(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String clusterRef) {
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
  public long bulkCreate(List<Cluster> entities) {
    try {
      return clusterRepository.bulkCreate(entities);
    } catch (Exception ex) {
      String clusters = entities.stream().map(Cluster::getClusterRef).collect(Collectors.joining(","));
      log.info("Encountered exception while saving the Cluster entity records of [{}], with exception", clusters, ex);
      throw new UnexpectedException("Encountered exception while saving the Cluster entity records.");
    }
  }

  @Override
  public long bulkDelete(
      List<Cluster> entities, String accountId, String orgIdentifier, String projectIdentifier, String envRef) {
    List<String> clusterRefs = entities.stream().map(c -> c.getClusterRef()).collect(Collectors.toList());
    Criteria criteria = getClusterEqualityCriteria(accountId, orgIdentifier, projectIdentifier, envRef, clusterRefs);
    return clusterRepository.bulkDelete(criteria);
  }

  @Override
  public boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier,
      String clusterRef, ScopeLevel scopeLevel) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project identifier must be present");
    checkArgument(isNotEmpty(clusterRef), "cluster identifier must be present");

    Criteria criteria = getClusterEqualityCriteria(
        accountId, orgIdentifier, projectIdentifier, envIdentifier, getScopedClusterRef(scopeLevel, clusterRef));
    DeleteResult delete = clusterRepository.delete(criteria);
    return delete.wasAcknowledged() && delete.getDeletedCount() == 1;
  }

  @Override
  public DeleteResult deleteFromAllEnv(
      String accountId, String orgIdentifier, String projectIdentifier, String clusterRef) {
    final ScopeLevel scope = ScopeLevel.of(accountId, orgIdentifier, projectIdentifier);

    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(clusterRef), "cluster identifier must be present");

    Criteria criteria = getClusterEqualityCriteriaForAllEnv(
        accountId, orgIdentifier, projectIdentifier, getScopedClusterRef(scope, clusterRef));
    return clusterRepository.delete(criteria);
  }

  @Override
  public long deleteAllFromEnvAndReturnCount(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project identifier must be present");
    checkArgument(isNotEmpty(envIdentifier), "environment identifier must be present");

    Criteria criteria = getClusterEqCriteriaForAllClusters(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    DeleteResult delete = clusterRepository.delete(criteria);
    return delete.getDeletedCount();
  }

  @Override
  public boolean deleteAllFromEnv(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project identifier must be present");
    checkArgument(isNotEmpty(envIdentifier), "environment identifier must be present");

    Criteria criteria = getClusterEqCriteriaForAllClusters(accountId, orgIdentifier, projectIdentifier, envIdentifier);
    DeleteResult delete = clusterRepository.delete(criteria);
    return delete.wasAcknowledged() && delete.getDeletedCount() > 0;
  }

  @Override
  public boolean deleteAllFromProj(String accountId, String orgIdentifier, String projectIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");
    checkArgument(isNotEmpty(projectIdentifier), "project identifier must be present");

    return deleteAllInternal(accountId, orgIdentifier, projectIdentifier);
  }

  @Override
  public boolean deleteAllFromOrg(String accountId, String orgIdentifier) {
    checkArgument(isNotEmpty(accountId), "accountId must be present");
    checkArgument(isNotEmpty(orgIdentifier), "org identifier must be present");

    return deleteAllInternal(accountId, orgIdentifier, null);
  }

  private boolean deleteAllInternal(String accountId, String orgIdentifier, String projectIdentifier) {
    Criteria criteria = getClusterEqCriteriaForAllClusters(accountId, orgIdentifier, projectIdentifier);
    DeleteResult delete = clusterRepository.delete(criteria);
    return delete.wasAcknowledged();
  }

  public Page<Cluster> list(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envRef, String searchTerm, Collection<String> clusterRefs, List<String> sort) {
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

  @Override
  public List<Cluster> listAcrossEnv(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, Collection<String> envRefs) {
    List<Cluster> entities = new ArrayList<>();
    ScopeWiseIds scopeWiseIds =
        IdentifierRefHelper.getScopeWiseIds(accountIdentifier, orgIdentifier, projectIdentifier, envRefs);
    entities.addAll(getAllClusters(
        page, size, accountIdentifier, orgIdentifier, projectIdentifier, scopeWiseIds.getProjectScopedIds()));
    entities.addAll(getAllClusters(page, size, accountIdentifier, orgIdentifier, null, scopeWiseIds.getOrgScopedIds()));
    entities.addAll(getAllClusters(page, size, accountIdentifier, null, null, scopeWiseIds.getAccountScopedIds()));

    return entities;
  }

  private List<Cluster> getAllClusters(int page, int size, String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> envIds) {
    PageRequest pageRequest = PageRequest.of(page, size);

    if (isNotEmpty(envIds)) {
      Criteria criteria =
          getClusterEqualityCriteriaAcrossEnvs(accountIdentifier, orgIdentifier, projectIdentifier, envIds);
      Page<Cluster> clustersPageResponse = clusterRepository.find(criteria, pageRequest);
      if (isNotEmpty(clustersPageResponse.getContent())) {
        return clustersPageResponse.getContent();
      }
    }
    return new ArrayList<>();
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

  private Criteria getClusterEqualityCriteriaAcrossEnvs(
      String accountId, String orgId, String projectId, Collection<String> envRefs) {
    return where(ClusterKeys.accountId)
        .is(accountId)
        .and(ClusterKeys.orgIdentifier)
        .is(orgId)
        .and(ClusterKeys.projectIdentifier)
        .is(projectId)
        .and(ClusterKeys.envRef)
        .in(envRefs);
  }

  private Criteria getClusterEqualityCriteriaForAllEnv(
      String accountId, String orgId, String projectId, String identifier) {
    Criteria criteria = where(ClusterKeys.accountId).is(accountId);
    if (isNotEmpty(orgId)) {
      criteria = criteria.and(ClusterKeys.orgIdentifier).is(orgId);
    }
    if (isNotEmpty(projectId)) {
      criteria = criteria.and(ClusterKeys.projectIdentifier).is(projectId);
    }
    return criteria.and(ClusterKeys.clusterRef).is(identifier);
  }

  private Criteria getClusterEqCriteriaForAllClusters(String accountId, String orgId, String projectId) {
    return where(ClusterKeys.accountId)
        .is(accountId)
        .and(ClusterKeys.orgIdentifier)
        .is(orgId)
        .and(ClusterKeys.projectIdentifier)
        .is(projectId);
  }

  private Criteria getClusterEqCriteriaForAllClusters(
      String accountId, String orgId, String projectId, String envIdentifier) {
    return where(ClusterKeys.accountId)
        .is(accountId)
        .and(ClusterKeys.orgIdentifier)
        .is(orgId)
        .and(ClusterKeys.projectIdentifier)
        .is(projectId)
        .and(ClusterKeys.envRef)
        .is(envIdentifier);
  }

  private Criteria getClusterEqualityCriteria(
      String accountId, String orgId, String projectId, String envRefs, Collection<String> clusterRefs) {
    return where(ClusterKeys.accountId)
        .is(accountId)
        .and(ClusterKeys.orgIdentifier)
        .is(orgId)
        .and(ClusterKeys.projectIdentifier)
        .is(projectId)
        .and(ClusterKeys.envRef)
        .is(envRefs)
        .and(ClusterKeys.clusterRef)
        .in(clusterRefs);
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

  private String getScopedClusterRef(ScopeLevel scopeLevel, String ref) {
    return scopeLevel != null && scopeLevel != ScopeLevel.PROJECT
        ? String.format("%s.%s", scopeLevel.toString().toLowerCase(), ref)
        : ref;
  }
}
