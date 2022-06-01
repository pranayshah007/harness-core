/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.custom;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.filestore.entities.NGFile;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDP)
public interface FileStoreRepositoryCustom {
  /**
   * Execute aggregation query.
   *
   * @param aggregation aggregation query
   * @param classToFillResultIn cast result in class
   * @param <T> type
   * @return the aggregation results
   */
  <T> AggregationResults<T> aggregate(Aggregation aggregation, Class<T> classToFillResultIn);

  /**
   * List all NG files sorted by sort criteria
   *
   * @param criteria the query criteria
   * @param sortBy the sort by criteria
   * @return sorted list of NG files
   */
  List<NGFile> findAllAndSort(Criteria criteria, Sort sortBy);

  /**
   * List all NG files sorted by sort criteria
   *
   * @param criteria the query criteria
   * @param sortBy the sort by criteria
   * @param pageable the page request
   * @return sorted page of NG files
   */
  Page<NGFile> findAllAndSort(Criteria criteria, Sort sortBy, Pageable pageable);

  /**
   * List NG files by pages.
   *
   * @param criteria the query criteria
   * @param pageable the page request
   * @return page of NG files
   */
  Page<NGFile> findAll(Criteria criteria, Pageable pageable);
}
