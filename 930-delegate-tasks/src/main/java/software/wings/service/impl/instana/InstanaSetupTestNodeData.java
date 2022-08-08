/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instana;

import software.wings.delegatetasks.DelegateStateType;
import software.wings.service.impl.analysis.SetupTestNodeData;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InstanaSetupTestNodeData extends SetupTestNodeData {
  private InstanaInfraParams infraParams;
  private InstanaApplicationParams applicationParams;
  private List<InstanaTagFilter> tagFilters;
  @Builder
  private InstanaSetupTestNodeData(String appId, String settingId, String instanceName, boolean isServiceLevel,
      Instance instanceElement, String hostExpression, String workflowId, long fromTime, long toTime, String guid,
      InstanaInfraParams infraParams, InstanaApplicationParams applicationParams, List<InstanaTagFilter> tagFilters) {
    super(appId, settingId, instanceName, isServiceLevel, instanceElement, hostExpression, workflowId, guid,
        DelegateStateType.INSTANA, fromTime, toTime);
    this.infraParams = infraParams;
    this.applicationParams = applicationParams;
    this.tagFilters = tagFilters;
  }

  public List<InstanaTagFilter> getTagFilters() {
    if (tagFilters == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(this.tagFilters);
  }
}
