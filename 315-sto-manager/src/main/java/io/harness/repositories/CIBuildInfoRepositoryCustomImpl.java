/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ci.beans.entities.CIBuild;

import com.google.inject.Inject;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class CIBuildInfoRepositoryCustomImpl implements CIBuildInfoRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Page<CIBuild> findAll(Criteria criteria, Pageable pageable) {
    Query query = new Query(criteria).with(pageable);
    List<CIBuild> projects = mongoTemplate.find(query, CIBuild.class);
    return PageableExecutionUtils.getPage(
        projects, pageable, () -> mongoTemplate.count(Query.of(query).limit(-1).skip(-1), CIBuild.class));
  }

  @Override
  public Optional<CIBuild> getBuildById(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, Long buildIdentifier) {
    Query query = new Query().addCriteria(new Criteria()
                                              .and(CIBuild.Build.accountIdentifier)
                                              .is(accountIdentifier)
                                              .and(CIBuild.Build.orgIdentifier)
                                              .is(orgIdentifier)
                                              .and(CIBuild.Build.projectIdentifier)
                                              .is(projectIdentifier)
                                              .and(CIBuild.Build.buildNumber)
                                              .is(buildIdentifier));
    return Optional.ofNullable(mongoTemplate.findOne(query, CIBuild.class));
  }

  @Override
  public Page<CIBuild> getBuilds(Criteria criteria, Pageable pageable) {
    return findAll(criteria, pageable);
  }
}
