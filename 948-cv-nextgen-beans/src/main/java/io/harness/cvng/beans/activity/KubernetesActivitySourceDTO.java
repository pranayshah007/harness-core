/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.activity;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.Set;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@SuperBuilder
@NoArgsConstructor
@JsonTypeName("KUBERNETES")
@OwnedBy(HarnessTeam.CV)
public class KubernetesActivitySourceDTO extends ActivitySourceDTO {
  @NotNull String connectorIdentifier;
  @NotNull @NotEmpty Set<KubernetesActivitySourceConfig> activitySourceConfigs;

  @Override
  public ActivitySourceType getType() {
    return ActivitySourceType.KUBERNETES;
  }

  @Override
  public boolean isEditable() {
    return true;
  }

  @Data
  @Builder
  @FieldNameConstants(innerTypeName = "KubernetesActivitySourceConfigKeys")
  public static class KubernetesActivitySourceConfig {
    @NotNull String serviceIdentifier;
    @NotNull String envIdentifier;
    @NotNull String namespace;
    @NotNull String workloadName;
    String namespaceRegex;
  }
}
