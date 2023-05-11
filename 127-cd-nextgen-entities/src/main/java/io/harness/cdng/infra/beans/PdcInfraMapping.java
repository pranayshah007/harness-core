/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra.beans;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.host.HostFilter;

import com.fasterxml.jackson.annotation.JsonTypeName;
import dev.morphia.annotations.Id;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("PdcInfraMapping")
@JsonTypeName("PdcInfraMapping")
@OwnedBy(CDP)
@RecasterAlias("io.harness.cdng.infra.beans.PdcInfraMapping")
public class PdcInfraMapping implements InfraMapping {
  @Id private String uuid;
  private String accountId;
  private String credentialsRef;
  private List<String> hosts;
  private String connectorRef;
  private HostFilter hostFilter;
  private String hostObjectArray;
  private Map<String, String> hostAttributes;
}
