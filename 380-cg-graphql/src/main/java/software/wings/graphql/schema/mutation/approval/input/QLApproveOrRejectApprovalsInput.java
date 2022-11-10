/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.graphql.schema.mutation.approval.input;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.OwnedBy;

import software.wings.beans.ApprovalDetails.Action;
import software.wings.beans.NameValuePair;

import java.util.List;
import lombok.Value;

@Value
@OwnedBy(CDC)
public class QLApproveOrRejectApprovalsInput {
  String executionId;
  String approvalId;
  Action action;
  List<NameValuePair> variableInputs;
  String applicationId;
  String comments;
  String clientMutationId;
  String userEmail;
}
