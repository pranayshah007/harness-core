/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.activityhistory;

import io.harness.ng.core.activityhistory.entity.NGActivity;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

public interface NGActivityCustomRepository {
  Page<NGActivity> findAll(Criteria criteria, Pageable pageable);
  <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn);
  List<String> findDistinctEntityTypes(Criteria criteria);
}
