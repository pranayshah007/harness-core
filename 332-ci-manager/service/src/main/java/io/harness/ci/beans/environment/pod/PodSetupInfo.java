/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.environment.pod;

import io.harness.annotation.RecasterAlias;
import io.harness.beans.environment.pod.container.ContainerDefinitionInfo;
import io.harness.delegate.beans.ci.pod.PVCParams;
import io.harness.delegate.beans.ci.pod.PodVolume;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.annotation.TypeAlias;

/**
 * Stores all details require to spawn pod
 */

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("podSetupInfo")
@RecasterAlias("io.harness.beans.environment.pod.PodSetupInfo")
public class PodSetupInfo {
  private PodSetupParams podSetupParams;
  private List<PVCParams> pvcParamsList;
  @NotNull private Map<String, String> volumeToMountPath;
  @NotEmpty private String name;
  @NotNull private Integer stageMemoryRequest;
  @NotNull private Integer stageCpuRequest;
  private List<String> serviceIdList;
  private List<Integer> serviceGrpcPortList;
  @NotEmpty private String workDirPath;
  private List<PodVolume> volumes;

  @Data
  @Builder
  public static final class PodSetupParams {
    private List<ContainerDefinitionInfo> containerDefinitionInfos;
  }
}
