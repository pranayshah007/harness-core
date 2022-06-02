/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cvng.beans.change;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = KubernetesChangeEventMetadata.class, name = "K8sCluster")
  , @JsonSubTypes.Type(value = HarnessCDEventMetadata.class, name = "HarnessCDNextGen"),
      @JsonSubTypes.Type(value = PagerDutyEventMetaData.class, name = "PagerDuty"),
      @JsonSubTypes.Type(value = HarnessCDCurrentGenEventMetadata.class, name = "HarnessCD")
})
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY, visible = true)
public abstract class ChangeEventMetadata {
  @JsonIgnore public abstract ChangeSourceType getType();
}
