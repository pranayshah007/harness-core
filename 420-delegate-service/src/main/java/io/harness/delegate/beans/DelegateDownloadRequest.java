/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@OwnedBy(HarnessTeam.DEL)
public class DelegateDownloadRequest {
  @NotNull private final String name;
  private final String description;
  private final DelegateSize size;
  private final Set<String> tags;
  private final String tokenName;
  private final K8sPermissionType clusterPermissionType;
  private final String customClusterNamespace;

  public Set<String> getTags() {
    return isEmpty(tags) ? Collections.emptySet()
                         : tags.stream().filter(StringUtils::isNotBlank).map(String::trim).collect(Collectors.toSet());
  }
}
