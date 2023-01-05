/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.execution.export.ExportExecutionsResourceService;
import io.harness.execution.export.request.ExportExecutionsRequestSummary;
import io.harness.logging.AccountLogContext;
import io.harness.logging.AutoLogContext;

import software.wings.beans.WorkflowExecution;
import software.wings.graphql.datafetcher.BaseMutatorDataFetcher;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.execution.export.QLExportExecutionsInput;
import software.wings.graphql.schema.mutation.execution.export.QLExportExecutionsPayload;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;

@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ExportExecutionsDataFetcher
    extends BaseMutatorDataFetcher<QLExportExecutionsInput, QLExportExecutionsPayload> {
  @Inject private final ExportExecutionsResourceService exportExecutionsResourceService;
  @Inject private final ExecutionQueryHelper executionQueryHelper;

  @Inject
  public ExportExecutionsDataFetcher(
      ExportExecutionsResourceService exportExecutionsResourceService, ExecutionQueryHelper executionQueryHelper) {
    super(QLExportExecutionsInput.class, QLExportExecutionsPayload.class);
    this.exportExecutionsResourceService = exportExecutionsResourceService;
    this.executionQueryHelper = executionQueryHelper;
  }

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  protected QLExportExecutionsPayload mutateAndFetch(
      QLExportExecutionsInput triggerExecutionInput, MutationContext mutationContext) {
    String accountId = mutationContext.getAccountId();
    try (AutoLogContext ignore1 = new AccountLogContext(accountId, AutoLogContext.OverrideBehavior.OVERRIDE_ERROR)) {
      Query<WorkflowExecution> query = wingsPersistence.createAuthorizedQuery(WorkflowExecution.class);
      executionQueryHelper.setBaseQuery(triggerExecutionInput.getFilters(), query, accountId);
      ExportExecutionsRequestSummary summary = exportExecutionsResourceService.export(
          accountId, query, QLExportExecutionsInput.toUserParams(triggerExecutionInput));
      return QLExportExecutionsPayload.fromExportExecutionsRequestSummary(summary, triggerExecutionInput);
    }
  }
}
