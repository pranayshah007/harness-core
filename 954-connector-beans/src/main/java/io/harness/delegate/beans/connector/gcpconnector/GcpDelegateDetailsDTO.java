/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.gcpconnector;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModel;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName(GcpConstants.INHERIT_FROM_DELEGATE)
@ApiModel("GcpDelegateDetails")
@Schema(name = "GcpDelegateDetails", description = "This contains GCP ConnectorDisconnectHandler delegate details")
public class GcpDelegateDetailsDTO implements GcpCredentialSpecDTO {
  @NotNull @Size(min = 1) Set<String> delegateSelectors;
}
