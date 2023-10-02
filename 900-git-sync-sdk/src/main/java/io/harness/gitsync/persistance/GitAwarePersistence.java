/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.gitsync.persistance;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;
import io.harness.git.model.ChangeType;
import io.harness.gitsync.beans.YamlDTO;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(DX)
public interface GitAwarePersistence {
  <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, String yaml, ChangeType changeType, Class<B> entityClass, Supplier functor);

  @Deprecated
  <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, String yaml, ChangeType changeType, Class<B> entityClass);

  /**
   * Count returns count with limit -1 and skip -1
   **/
  <B extends GitSyncableEntity, Y extends YamlDTO> Long count(@NotNull Criteria criteria, String projectIdentifier,
      String orgIdentifier, String accountId, Class<B> entityClass);

  <B extends GitSyncableEntity, Y extends YamlDTO> Optional<B> findOne(@NotNull Criteria criteria,
      String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass);

  <B extends GitSyncableEntity, Y extends YamlDTO> Optional<B> findOne(
      Criteria criteria, String repo, String branch, Class<B> entityClass);

  <B extends GitSyncableEntity, Y extends YamlDTO> List<B> find(@NotNull Criteria criteria, Pageable pageable,
      String projectIdentifier, String orgIdentifier, String accountId, Class<B> entityClass, boolean useCollation);

  <B extends GitSyncableEntity, Y extends YamlDTO> boolean exists(@NotNull Criteria criteria, String projectIdentifier,
      String orgIdentifier, String accountId, Class<B> entityClass);

  <B extends GitSyncableEntity, Y extends YamlDTO> B save(
      B objectToSave, ChangeType changeType, Class<B> entityClass, Supplier functor);

  @Deprecated
  <B extends GitSyncableEntity, Y extends YamlDTO> B save(B objectToSave, ChangeType changeType, Class<B> entityClass);

  <B extends GitSyncableEntity> void delete(
      B objectToRemove, String yaml, ChangeType changeType, Class<B> entityClass, Supplier functor);

  <B extends GitSyncableEntity, Y extends YamlDTO> void delete(
      B objectToRemove, ChangeType changeType, Class<B> entityClass);

  // added as a stop gap fix for PMS.
  Criteria getCriteriaWithGitSync(String projectIdentifier, String orgIdentifier, String accountId, Class entityClass);

  Criteria makeCriteriaGitAware(
      String accountId, String orgIdentifier, String projectIdentifier, Class entityClass, Criteria criteria);
}
