/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.audit.repositories.streaming;

import io.harness.audit.entities.streaming.StreamingDestination;
import io.harness.spec.server.audit.v1.model.StatusWiseCount;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface StreamingDestinationRepositoryCustom {
  Page<StreamingDestination> findAll(Criteria criteria, Pageable pageable);

  Page<String> findAllStreamingDestinationIdentifiers(Criteria criteria, Pageable pageable);

  boolean deleteByCriteria(Criteria criteria);

  List<StatusWiseCount> countByStatus(Criteria criteria);
}
