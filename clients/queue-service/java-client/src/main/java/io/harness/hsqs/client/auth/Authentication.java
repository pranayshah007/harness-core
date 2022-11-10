/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.hsqs.client.auth;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.hsqs.client.Pair;

import java.util.List;
import java.util.Map;

@OwnedBy(PIPELINE)
public interface Authentication {
  /**
   * Apply authentication settings to header and query params.
   *
   * @param queryParams  List of query parameters
   * @param headerParams Map of header parameters
   */
  void applyToParams(List<Pair> queryParams, Map<String, String> headerParams);
}
