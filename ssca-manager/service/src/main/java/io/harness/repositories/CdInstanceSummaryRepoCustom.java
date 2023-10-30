/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.ssca.entities.CdInstanceSummary;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface CdInstanceSummaryRepoCustom {
  CdInstanceSummary findOne(Criteria criteria);
  List<CdInstanceSummary> findAll(Criteria criteria);
  Page<CdInstanceSummary> findAll(Criteria criteria, Pageable pageable);
}
