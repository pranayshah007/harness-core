/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.envGroup;

import io.harness.annotation.HarnessRepo;
import io.harness.cdng.envGroup.beans.EnvironmentGroupEntity;
import io.harness.gitsync.persistance.GitSyncableHarnessRepo;

import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.transaction.annotation.Transactional;

@HarnessRepo
@GitSyncableHarnessRepo
@Transactional
public interface EnvironmentGroupRepository
    extends PagingAndSortingRepository<EnvironmentGroupEntity, String>, EnvironmentGroupRepositoryCustom {}
