/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.approval;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.delegate.task.TaskParameters;
import io.harness.expression.Expression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@OwnedBy(CDC)
@NoArgsConstructor
@AllArgsConstructor
@TargetModule(HarnessModule._957_CG_BEANS)
@JsonIgnoreProperties(ignoreUnknown = true)
@FieldNameConstants(innerTypeName = "ShellScriptApprovalParamsKeys")
public class ShellScriptApprovalParams implements TaskParameters {
  @Getter @Setter @Expression(ALLOW_SECRETS) private String scriptString;

  /* Retry Interval in Milliseconds*/
  @Getter @Setter private Integer retryInterval;
  private List<String> delegateSelectors;

  public void setDelegateSelectors(List<String> delegateSelectors) {
    this.delegateSelectors = delegateSelectors;
  }

  public List<String> fetchDelegateSelectors() {
    return this.delegateSelectors;
  }
}
