/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.migration.tasks;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.entities.Project;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.springframework.data.mongodb.core.MongoTemplate;

@OwnedBy(HarnessTeam.GTM)
@Slf4j
public class ProjectModulesMigration implements NGMigration {
  @Inject MongoTemplate mongoTemplate;
  @Inject HPersistence persistence;
  @Override
  public void migrate() {
    log.info("Creating the projects query for migration");
    Query<Project> projectsQuery = persistence.createQuery(Project.class);

    try (HIterator<Project> records = new HIterator<>(projectsQuery.fetch())) {
      while (records.hasNext()) {
        Project project = records.next();

        log.info("Enabling all modules for the project accountId={} projectId={}", project.getAccountIdentifier(),
            project.getId());
        // All projects should have all modules enabled
        project.setModules(ModuleType.getModules());

        try {
          mongoTemplate.save(project);
        } catch (Exception e) {
          log.info("Failed to save the migrated project accountId={} projectId={}", project.getAccountIdentifier(),
              project.getId(), e);
        }

        log.info("Successfully enabled all modules for the project accountId={} projectId={}",
            project.getAccountIdentifier(), project.getId());
      }
    }
  }
}
