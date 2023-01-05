/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;

import io.harness.annotations.dev.OwnedBy;

import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.UtilityClass;

@FieldNameConstants(innerTypeName = "CompareEnvironmentAggregationInfoKeys")
@Data
@NoArgsConstructor
@OwnedBy(DX)
public class CompareEnvironmentAggregationInfo {
  @Id private ID _id;
  private String serviceId;
  private String serviceName;
  private String count;
  private List<ServiceInfoSummary> serviceInfoSummaries;

  @Data
  @NoArgsConstructor
  public static final class ID {
    private String serviceId;
    private String envId;
    private String lastArtifactBuildNum;
    private String lastWorkflowExecutionId;
    private String infraMappingId;
  }

  @UtilityClass
  public static final class CompareEnvironmentAggregationInfoKeys {
    public static final String serviceId = "serviceId";
    public static final String serviceName = "serviceName";
    public static final String count = "count";
    public static final String serviceInfoSummaries = "serviceInfoSummaries";
  }
}
