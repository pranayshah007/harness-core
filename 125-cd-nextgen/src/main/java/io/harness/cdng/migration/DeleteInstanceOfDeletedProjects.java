/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.migration;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.entities.InfrastructureMapping;
import io.harness.entities.InfrastructureMapping.InfrastructureMappingNGKeys;
import io.harness.migration.NGMigration;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.ng.core.entities.Project;
import io.harness.ng.core.entities.Project.ProjectKeys;
import io.harness.remote.client.CGRestUtils;

import com.google.inject.Inject;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@OwnedBy(CDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class DeleteInstanceOfDeletedProjects implements NGMigration {
  private AccountClient accountClient;
  private MongoTemplate mongoTemplate;
  private CDMigrationUtils cdMigrationUtils;

  @Override
  public void migrate() {
    log.info("Migrating instances belonging to deleted projects");
    try {
      List<AccountDTO> accounts = CGRestUtils.getResponse(accountClient.getAllAccounts());
      for (AccountDTO account : accounts) {
        for (Project project : getDeletedProjectsForAccount(account.getIdentifier())) {
          for (InfrastructureMapping infrastructureMapping : getInfrastructureMappingsForProject(project)) {
            log.info("Deleting orphan entities for account {} belonging to deleted project {} with id {} and "
                    + "infrastructure mapping {}",
                project.getAccountIdentifier(), project.getIdentifier(), project.getId(),
                infrastructureMapping.getId());
            cdMigrationUtils.deleteOrphanInstance(infrastructureMapping);
            cdMigrationUtils.deleteRelatedOrphanEntities(infrastructureMapping);
          }
        }
      }
    } catch (Exception ex) {
      log.error("Unexpected error occurred while migrating instances for deleted project", ex);
    }
  }

  private List<Project> getDeletedProjectsForAccount(String accountIdentifier) {
    Query query = new Query(
        Criteria.where(ProjectKeys.deleted).is(true).and(ProjectKeys.accountIdentifier).is(accountIdentifier));
    return mongoTemplate.find(query, Project.class);
  }

  private List<InfrastructureMapping> getInfrastructureMappingsForProject(Project project) {
    Query infrastructureMappingQuery = new Query(new Criteria()
                                                     .and(InfrastructureMappingNGKeys.accountIdentifier)
                                                     .is(project.getAccountIdentifier())
                                                     .and(InfrastructureMappingNGKeys.orgIdentifier)
                                                     .is(project.getOrgIdentifier())
                                                     .and(InfrastructureMappingNGKeys.projectIdentifier)
                                                     .is(project.getIdentifier()));
    return mongoTemplate.find(infrastructureMappingQuery, InfrastructureMapping.class);
  }
}
