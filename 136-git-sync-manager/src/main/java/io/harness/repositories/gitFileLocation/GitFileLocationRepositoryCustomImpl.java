/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.gitFileLocation;

import static io.harness.annotations.dev.HarnessTeam.DX;

import static java.util.Arrays.asList;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.EntityType;
import io.harness.NGCommonEntityConstants;
import io.harness.annotations.dev.OwnedBy;
import io.harness.gitsync.common.beans.GitFileLocation;
import io.harness.gitsync.common.beans.GitFileLocation.GitFileLocationKeys;
import io.harness.gitsync.common.dtos.GitSyncEntityListDTO;
import io.harness.gitsync.common.dtos.GitSyncEntityListDTO.GitSyncEntityListDTOKeys;
import io.harness.gitsync.common.dtos.GitSyncRepoFilesDTO;
import io.harness.gitsync.common.dtos.GitSyncRepoFilesDTO.GitSyncConfigFilesDTOKeys;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.client.result.DeleteResult;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.Fields;
import org.springframework.data.mongodb.core.aggregation.GroupOperation;
import org.springframework.data.mongodb.core.aggregation.MatchOperation;
import org.springframework.data.mongodb.core.aggregation.ProjectionOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(DX)
public class GitFileLocationRepositoryCustomImpl implements GitFileLocationRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public List<String> getDistinctEntityName(Criteria criteria, String field) {
    Query query = query(criteria);
    return mongoTemplate.findDistinct(query, field, GitFileLocation.class, String.class);
  }

  @Override
  public Page<GitFileLocation> getGitFileLocation(Criteria criteria, Pageable pageable) {
    Query query = query(criteria).with(pageable);
    final List<GitFileLocation> gitFileLocationList = mongoTemplate.find(query, GitFileLocation.class);
    return PageableExecutionUtils.getPage(gitFileLocationList, pageable,
        () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), GitFileLocation.class));
  }

  @Override
  public List<GitSyncRepoFilesDTO>
  getByProjectIdAndOrganizationIdAndAccountIdAndGitSyncConfigIdentifierListAndEntityTypeList(String projectIdentifier,
      String orgIdentifier, String accountIdentifier, List<String> gitSyncConfigIdentifierList,
      List<EntityType> entityTypeList, String searchTerm, int size) {
    List<String> entityTypeListString = new ArrayList<>();
    entityTypeList.forEach(entityType -> entityTypeListString.add(entityType.name()));
    MatchOperation matchOperation = getMatchOperation(projectIdentifier, orgIdentifier, accountIdentifier,
        gitSyncConfigIdentifierList, entityTypeListString, searchTerm);
    GroupOperation firstGroupOperation =
        Aggregation.group(GitFileLocationKeys.gitSyncConfigId, GitFileLocationKeys.entityType)
            .count()
            .as("entityCount")
            .push(Aggregation.ROOT)
            .as("entities");
    GroupOperation secondGroupOperation =
        Aggregation.group("$_id." + GitFileLocationKeys.gitSyncConfigId)
            .push(new BasicDBObject(GitSyncEntityListDTOKeys.entityType, "$_id." + GitFileLocationKeys.entityType)
                      .append(GitSyncEntityListDTOKeys.gitSyncEntities,
                          new BasicDBObject("$slice", asList("$entities", size)))
                      .append(GitSyncEntityListDTOKeys.count, "$entityCount"))
            .as("entityTypes");
    ProjectionOperation projectionOperation =
        Aggregation.project(Fields.from(Fields.field(GitSyncConfigFilesDTOKeys.gitSyncConfigIdentifier, "_id"),
            Fields.field(GitSyncConfigFilesDTOKeys.gitSyncEntityLists, "entityTypes")));
    Aggregation aggregation =
        Aggregation.newAggregation(matchOperation, firstGroupOperation, secondGroupOperation, projectionOperation);
    return mongoTemplate.aggregate(aggregation, GitFileLocation.class, GitSyncRepoFilesDTO.class).getMappedResults();
  }

  @Override
  public List<GitSyncEntityListDTO>
  getByProjectIdAndOrganizationIdAndAccountIdAndGitSyncConfigIdentifierAndEntityTypeListAndBranch(
      String projectIdentifier, String orgIdentifier, String accountIdentifier, String gitSyncConfigIdentifier,
      String branch, List<EntityType> entityTypeList, String searchTerm, int size) {
    List<String> entityTypeListString = new ArrayList<>();
    entityTypeList.forEach(entityType -> entityTypeListString.add(entityType.name()));
    MatchOperation matchOperation = getMatchOperation(projectIdentifier, orgIdentifier, accountIdentifier,
        gitSyncConfigIdentifier, branch, entityTypeListString, searchTerm);
    GroupOperation groupOperation = Aggregation.group(NGCommonEntityConstants.ENTITY_TYPE)
                                        .count()
                                        .as("count")
                                        .push(Aggregation.ROOT)
                                        .as("gitSyncEntities");
    ProjectionOperation projectionOperation = Aggregation.project()
                                                  .andExpression("gitSyncEntities")
                                                  .slice(size)
                                                  .as(GitSyncEntityListDTOKeys.gitSyncEntities)
                                                  .andExpression("_id")
                                                  .as(GitSyncEntityListDTOKeys.entityType)
                                                  .andExpression("count")
                                                  .as(GitSyncEntityListDTOKeys.count);
    return mongoTemplate
        .aggregate(Aggregation.newAggregation(matchOperation, groupOperation, projectionOperation),
            GitFileLocation.class, GitSyncEntityListDTO.class)
        .getMappedResults();
  }

  @Override
  public GitFileLocation update(Query query, Update update) {
    return mongoTemplate.findAndModify(
        query, update, new FindAndModifyOptions().returnNew(true), GitFileLocation.class);
  }

  @Override
  public DeleteResult deleteAll(Query query) {
    return mongoTemplate.remove(query, GitFileLocation.class);
  }

  @Override
  public GitFileLocation findOne(Criteria criteria) {
    return mongoTemplate.findOne(query(criteria), GitFileLocation.class);
  }

  private MatchOperation getMatchOperation(String projectIdentifier, String orgIdentifier, String accountIdentifier,
      String gitSyncConfigIdentifier, String branch, List<String> entityTypeList, String searchTerm) {
    Criteria criteria = Criteria.where(GitFileLocationKeys.entityType)
                            .in(entityTypeList)
                            .and(GitFileLocationKeys.projectId)
                            .is(projectIdentifier)
                            .and(GitFileLocationKeys.organizationId)
                            .is(orgIdentifier)
                            .and(GitFileLocationKeys.accountId)
                            .is(accountIdentifier)
                            .and(GitFileLocationKeys.gitSyncConfigId)
                            .is(gitSyncConfigIdentifier)
                            .and(GitFileLocationKeys.branch)
                            .is(branch)
                            .orOperator(Criteria.where(GitFileLocationKeys.entityIdentifier).regex(searchTerm, "i"),
                                Criteria.where(GitFileLocationKeys.entityGitPath).regex(searchTerm, "i"));
    return match(criteria);
  }

  private MatchOperation getMatchOperation(String projectIdentifier, String orgIdentifier, String accountIdentifier,
      List<String> gitSyncConfigIdentifierList, List<String> entityTypeList, String searchTerm) {
    Criteria criteria = Criteria.where(GitFileLocationKeys.entityType)
                            .in(entityTypeList)
                            .and(GitFileLocationKeys.projectId)
                            .is(projectIdentifier)
                            .and(GitFileLocationKeys.organizationId)
                            .is(orgIdentifier)
                            .and(GitFileLocationKeys.accountId)
                            .is(accountIdentifier)
                            .and(GitFileLocationKeys.gitSyncConfigId)
                            .in(gitSyncConfigIdentifierList)
                            .and(GitFileLocationKeys.isDefault)
                            .is(true)
                            .orOperator(Criteria.where(GitFileLocationKeys.entityIdentifier).regex(searchTerm, "i"),
                                Criteria.where(GitFileLocationKeys.entityGitPath).regex(searchTerm, "i"));
    return match(criteria);
  }
}
