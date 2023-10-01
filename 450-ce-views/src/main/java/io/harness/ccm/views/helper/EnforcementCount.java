/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

@Data
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "This object will contain the complete definition of a Cloud Cost Enforcement Count")

public final class EnforcementCount {
  @Schema(description = "account id") String accountId;
  @Schema(description = "rules ids and list of enforcement") Map<String, List<LinkedEnforcements>> ruleIds;
  @Schema(description = "rules pack ids and list of enforcement") Map<String, List<LinkedEnforcements>> ruleSetIds;

  public EnforcementCount toDTO() {
    return EnforcementCount.builder()
        .accountId(getAccountId())
        .ruleIds(getRuleIds())
        .ruleSetIds(getRuleSetIds())
        .build();
  }
}
