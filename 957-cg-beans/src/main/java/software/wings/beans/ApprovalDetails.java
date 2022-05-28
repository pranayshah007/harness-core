/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.EmbeddedUser;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Created by sgurubelli on 6/7/17.
 */
@Data
@NoArgsConstructor
@OwnedBy(CDC)
public class ApprovalDetails {
  @NotEmpty private String approvalId;
  private EmbeddedUser approvedBy;
  private String comments;
  private Action action;
  private boolean approvalFromSlack;
  private boolean approvalFromGraphQL;
  private boolean approvalViaApiKey;
  private List<NameValuePair> variables;

  public enum Action {
    /**
     * Approve action
     */
    APPROVE,
    /** Reject Action */
    REJECT
  }
}
