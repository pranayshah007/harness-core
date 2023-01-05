/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance;

import software.wings.beans.instance.dashboard.EntitySummary;

import dev.morphia.annotations.Id;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public final class ServiceInstanceCount {
  @Id private String serviceId;
  private long count;
  private List<EnvType> envTypeList;
  private EntitySummary appInfo;
  private EntitySummary serviceInfo;

  @Data
  @NoArgsConstructor
  public static final class EnvType {
    private String type;
  }
}
