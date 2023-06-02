/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.rule.OwnerRule.GARVIT;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.category.element.UnitTests;
import io.harness.execution.export.ExportExecutionsResourceService;
import io.harness.execution.export.ExportExecutionsUtils;
import io.harness.execution.export.request.ExportExecutionsRequest.Status;
import io.harness.execution.export.request.ExportExecutionsRequestSummary;
import io.harness.rule.Owner;

import software.wings.beans.WorkflowExecution;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.AbstractDataFetcherTestBase;
import software.wings.graphql.datafetcher.MutationContext;
import software.wings.graphql.schema.mutation.execution.export.QLExportExecutionsInput;
import software.wings.graphql.schema.mutation.execution.export.QLExportExecutionsPayload;
import software.wings.graphql.schema.mutation.execution.export.QLExportExecutionsStatus;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;

import com.google.inject.Inject;
import dev.morphia.query.CriteriaContainer;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import graphql.schema.DataFetchingEnvironment;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class ExportExecutionsDataFetcherTest extends AbstractDataFetcherTestBase {
  private static final String ACCOUNT_ID = "ACCOUNT_ID";

  @InjectMocks @Inject private ExportExecutionsDataFetcher exportExecutionsDataFetcher;
  @Mock private WingsPersistence mockWingsPersistence;
  @Mock private ExportExecutionsResourceService exportExecutionsResourceService;

  @Before
  public void setup() throws Exception {
    on(exportExecutionsDataFetcher).set("exportExecutionsResourceService", exportExecutionsResourceService);
  }

  @Test
  @Owner(developers = GARVIT)
  @Category(UnitTests.class)
  public void testExportExecutions() {
    final MutationContext mutationContext = getMutationContext();
    QLExportExecutionsInput input =
        QLExportExecutionsInput.builder()
            .clientMutationId("clientMutationId")
            .notifyOnlyTriggeringUser(false)
            .userGroupIds(asList("ug1", "ug2"))
            .filters(Collections.singletonList(
                QLExecutionFilter.builder()
                    .execution(
                        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"executionId"}).build())
                    .build()))
            .build();

    Query<WorkflowExecution> query = mock(Query.class);
    CriteriaContainer continer = mock(CriteriaContainer.class);
    when(query.or()).thenReturn(continer);
    when(query.and()).thenReturn(continer);
    when(mockWingsPersistence.createAuthorizedQuery(WorkflowExecution.class)).thenReturn(query);
    FieldEnd fieldEnd = mock(FieldEnd.class);
    when(query.field(anyString())).thenReturn(fieldEnd);
    when(fieldEnd.equal(any())).thenReturn(query);

    Instant now = Instant.now();
    Instant aDayAfter = now.plus(1, ChronoUnit.DAYS);
    doReturn(ExportExecutionsRequestSummary.builder()
                 .requestId("rid")
                 .status(Status.QUEUED)
                 .totalExecutions(1)
                 .triggeredAt(ExportExecutionsUtils.prepareZonedDateTime(now.toEpochMilli()))
                 .statusLink("sl")
                 .downloadLink("dl")
                 .expiresAt(ExportExecutionsUtils.prepareZonedDateTime(aDayAfter.toEpochMilli()))
                 .errorMessage("em")
                 .build())
        .when(exportExecutionsResourceService)
        .export(any(), any(), any());

    QLExportExecutionsPayload payload = exportExecutionsDataFetcher.mutateAndFetch(input, mutationContext);
    assertThat(payload).isNotNull();
    assertThat(payload.getClientMutationId()).isEqualTo("clientMutationId");
    assertThat(payload.getRequestId()).isEqualTo("rid");
    assertThat(payload.getStatus()).isEqualTo(QLExportExecutionsStatus.QUEUED);
    assertThat(payload.getTotalExecutions()).isEqualTo(1);
    assertThat(payload.getTriggeredAt()).isEqualTo(now.toEpochMilli());
    assertThat(payload.getStatusLink()).isEqualTo("sl");
    assertThat(payload.getDownloadLink()).isEqualTo("dl");
    assertThat(payload.getExpiresAt()).isEqualTo(aDayAfter.toEpochMilli());
    assertThat(payload.getErrorMessage()).isEqualTo("em");
  }

  private MutationContext getMutationContext() {
    return MutationContext.builder()
        .accountId(ACCOUNT_ID)
        .dataFetchingEnvironment(Mockito.mock(DataFetchingEnvironment.class))
        .build();
  }
}
