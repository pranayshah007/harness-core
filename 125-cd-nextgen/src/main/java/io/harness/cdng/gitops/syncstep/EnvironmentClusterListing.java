/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.gitops.syncstep;

import java.util.Set;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EnvironmentClusterListing {
  Set<String> environmentIds;
  Set<String> clusterIds;
}
