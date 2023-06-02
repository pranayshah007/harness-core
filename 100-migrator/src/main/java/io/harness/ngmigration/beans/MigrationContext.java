/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.beans;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;

import java.util.Map;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MigrationContext {
  private Map<CgEntityId, CgEntityNode> entities;
  private Map<CgEntityId, NGYamlFile> migratedEntities;
  private Map<CgEntityId, Set<CgEntityId>> graph;
  private MigrationInputDTO inputDTO;
  private String accountId;
  private boolean templatizeStepParams;

  public static MigrationContext newInstance(String accountId, MigrationInputDTO inputDTO,
      Map<CgEntityId, CgEntityNode> entities, Map<CgEntityId, Set<CgEntityId>> graph,
      Map<CgEntityId, NGYamlFile> migratedEntities) {
    return MigrationContext.builder()
        .accountId(accountId)
        .inputDTO(inputDTO)
        .entities(entities)
        .graph(graph)
        .migratedEntities(migratedEntities)
        .templatizeStepParams(false)
        .build();
  }
}
