/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.k8Connector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonTypeName(KubernetesConfigConstants.INHERIT_FROM_DELEGATE)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(name = "KubernetesDelegateDetails", description = "This contains kubernetes ConnectorDisconnectHandler delegate details")
public class KubernetesDelegateDetailsDTO implements KubernetesCredentialSpecDTO {
  //  @NotNull @Size(min = 1) Set<String> delegateSelectors;
  // delete this
}
