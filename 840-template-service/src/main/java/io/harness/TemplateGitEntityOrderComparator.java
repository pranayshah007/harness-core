/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.ng.core.entitydetail.EntityDetailProtoToRestMapper.mapEventToRestEntityType;

import io.harness.annotations.dev.OwnedBy;
import io.harness.eventsframework.schemas.entity.EntityDetailProtoDTO;
import io.harness.gitsync.common.GitSyncEntityOrderComparatorInMsvc;

import com.google.common.collect.Lists;
import java.util.Comparator;
import java.util.List;

@OwnedBy(CDC)
public class TemplateGitEntityOrderComparator implements GitSyncEntityOrderComparatorInMsvc {
  public static final List<EntityType> sortOrder = Lists.newArrayList(EntityType.TEMPLATE);

  @Override
  public Comparator<EntityDetailProtoDTO> comparator() {
    return new Comparator<EntityDetailProtoDTO>() {
      @Override
      public int compare(final EntityDetailProtoDTO o1, final EntityDetailProtoDTO o2) {
        EntityType entityType1 = mapEventToRestEntityType(o1.getType());
        EntityType entityType2 = mapEventToRestEntityType(o2.getType());
        return Integer.compare(sortOrder.indexOf(entityType1), sortOrder.indexOf(entityType2));
      }
    };
  }
}
