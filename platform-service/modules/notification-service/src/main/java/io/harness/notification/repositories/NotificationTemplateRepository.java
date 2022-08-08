/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.notification.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.notification.Team;
import io.harness.notification.entities.NotificationTemplate;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface NotificationTemplateRepository extends PagingAndSortingRepository<NotificationTemplate, String> {
  List<NotificationTemplate> findByTeam(Team team);

  Optional<NotificationTemplate> findByIdentifierAndTeam(String identifier, Team team);

  Optional<NotificationTemplate> findByIdentifierAndTeamExists(String identifier, boolean teamExists);

  void deleteByTeam(Team team);
}
