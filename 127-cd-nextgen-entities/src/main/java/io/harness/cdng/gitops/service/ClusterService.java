/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.gitops.service;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.entity.Cluster;

import com.mongodb.client.result.DeleteResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;

@OwnedBy(GITOPS)
public interface ClusterService {
  /**
   * @param accountId the account id
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param envIdentifier identifier of the environment the gitops cluster is linked to
   * @param clusterRef identifier of the actual gitops cluster that exists in harness gitops
   * @return requested Cluster
   */
  Optional<Cluster> get(@NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier,
      String envIdentifier, @NotEmpty String clusterRef);

  /**
   * @param Cluster cluster entity to be created
   * @return Cluster entity created
   */
  Cluster create(@NotNull Cluster Cluster);

  /**
   * @param accountId  the account id
   * @param entities cluster entities to be bulk created
   * @return  clusters created
   */
  @NotNull Page<Cluster> bulkCreate(@NotEmpty String accountId, @NotNull List<Cluster> entities);

  /**
   * @param accountId  the account id
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param envIdentifier identifier of the environment the gitops cluster is linked to
   * @param clusterRef identifier of the actual gitops cluster that exists in harness gitops
   * @return boolean to indicate if deletion was successful
   */
  boolean delete(@NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier,
      @NotEmpty String envIdentifier, @NotEmpty String clusterRef);

  /**
   * Deletes a cluster from all the environments its linked into
   * @param accountId the account id
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param clusterRef identifier of the actual gitops cluster that exists in harness gitops
   * @return DeleteResult
   */
  @NotNull
  DeleteResult deleteFromAllEnv(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String clusterRef);

  /**
   * Deletes all clusters linked to a particular harness environment.
   * @param accountId  the account id
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param envIdentifier identifier of the environment the gitops cluster is linked to
   * @return boolean to indicate if deletion was successful
   */
  @NotNull
  boolean deleteAllFromEnv(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String envIdentifier);

  /**
   * Deletes all clusters linked to a particular harness project.
   * @param accountId  the account id
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @return boolean to indicate if deletion was successful
   */
  @NotNull
  boolean deleteAllFromProj(
      @NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier);

  /**
   * @param page page index starting from 0
   * @param size number of items to fetch in 1 page
   * @param accountIdentifier  the account id
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param envRef identifier of the environment the gitops cluster is linked to
   * @param searchTerm search term to be used to match against cluster identifiers
   * @param clusterRefs identifier of the actual gitops cluster that exists in harness gitops
   * @return Page of clusters
   */
  @NotNull
  Page<Cluster> list(int page, int size, @NotEmpty String accountIdentifier, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String envRef, String searchTerm, Collection<String> clusterRefs,
      List<String> sort);

  /**
   *
   * @param page page index starting from 0
   * @param size  number of items to fetch in 1 page
   * @param orgIdentifier the organization identifier
   * @param projectIdentifier the project identifier
   * @param envRefs identifiers of the environments for which to fetch clusters
   * @return Page of clusters
   */
  @NotNull
  Page<Cluster> listAcrossEnv(int page, int size, @NotEmpty String accountIdentifier, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, Collection<String> envRefs);
}
