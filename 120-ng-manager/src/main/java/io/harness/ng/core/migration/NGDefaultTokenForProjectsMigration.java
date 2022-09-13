/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.entities.Project;
import io.harness.remote.client.NGRestUtils;
import io.harness.repositories.core.spring.ProjectRepository;

import com.google.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@Slf4j
@OwnedBy(HarnessTeam.DEL)
public class NGDefaultTokenForProjectsMigration implements NGMigration {
  private final ProjectRepository projectRepository;
  private final AccountClient accountClient;

  @Inject
  public NGDefaultTokenForProjectsMigration(ProjectRepository projectRepository, AccountClient accountClient) {
    this.projectRepository = projectRepository;
    this.accountClient = accountClient;
  }

  @Override
  public void migrate() {
    try {
      log.info("[NGDefaultTokenForProjectsMigration] Generating default NG Tokens for projects");
      Iterable<Project> projects =
          projectRepository.findAll(Criteria.where("deleted").is(Boolean.FALSE), Pageable.unpaged());
      Map<String, List<String>> projectsWithActiveDefaultDelegateToken =
          StreamSupport.stream(projects.spliterator(), false)
              .map(project -> project.getAccountIdentifier())
              .filter(Objects::nonNull)
              .distinct()
              .collect(Collectors.toMap(Function.identity(),
                  accountId
                  -> NGRestUtils.getCgResponse(accountClient.getProjectsWithActiveDefaultDelegateToken(accountId))));
      for (Project project : projects) {
        if (projectsWithActiveDefaultDelegateToken.get(project.getAccountIdentifier()) != null
            && projectsWithActiveDefaultDelegateToken.get(project.getAccountIdentifier())
                   .stream()
                   .anyMatch(i -> i.equals(project.getOrgIdentifier() + "/" + project.getIdentifier()))) {
          continue;
        }
        try {
          NGRestUtils.getCgResponse(accountClient.upsertDefaultToken(
              project.getAccountIdentifier(), project.getOrgIdentifier(), project.getIdentifier(), true));
        } catch (Exception e) {
          log.error(
              "[NGDefaultTokenForProjectsMigration] Failed to create default Delegate Token for account {}, organization {}, and project {}",
              project.getAccountIdentifier(), project.getOrgIdentifier(), project.getIdentifier());
        }
      }
    } catch (Exception e) {
      log.error(
          "[NGDefaultTokenForProjectsMigration] Migration for generating default Delegate NG Token for projects failed with error {}. Ignoring the error.",
          e);
    }
  }
}
