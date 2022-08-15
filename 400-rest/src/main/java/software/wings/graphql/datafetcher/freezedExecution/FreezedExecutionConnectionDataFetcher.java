package software.wings.graphql.datafetcher.freezedExecution;

import io.harness.beans.WorkflowType;
import io.harness.exception.UnexpectedException;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
import software.wings.beans.WorkflowExecution;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.datafetcher.execution.QLExecutionFilter;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLExecutionConnection;
import software.wings.graphql.schema.type.QLFreezedExecution;
import software.wings.graphql.schema.type.QLFreezedExecutionConnection;
import software.wings.graphql.schema.type.QLPipelineExecution;
import software.wings.graphql.schema.type.QLWorkflowExecution;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import java.util.List;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

public class FreezedExecutionConnectionDataFetcher extends AbstractConnectionV2DataFetcher<QLFreezedExecution, QLNoOpSortCriteria, QLFreezedExecutionConnection> {

	@Override
	@AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
	protected QLFreezedExecutionConnection fetchConnection(List<QLExecutionFilter> filters,
			QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
		Query<WorkflowExecution> query = populateFilters(wingsPersistence, filters, WorkflowExecution.class, true)
				.order(Sort.descending(WorkflowExecution.WorkflowExecutionKeys.createdAt));

		Boolean includeIndirectExecutions =
				(Boolean) pageQueryParameters.getDataFetchingEnvironment().getArguments().get(INDIRECT_EXECUTION_FIELD);

		boolean includePipelineId = false;
		if (includeIndirectExecutions != null) {
			includePipelineId = includeIndirectExecutions;
		}

		/**
		 * If we are querying the membegit rExecutions, then we need to explicitly mark this boolean so we do not include
		 * the does not exist in the query
		 */

		if (isNotEmpty(filters)) {
			for (QLExecutionFilter filter : filters) {
				includePipelineId = includePipelineId || (filter.getPipelineExecutionId() != null);
			}
		}
		if (!includePipelineId) {
			query.field(WorkflowExecution.WorkflowExecutionKeys.pipelineExecutionId).doesNotExist();
		}

		QLExecutionConnection.QLExecutionConnectionBuilder connectionBuilder = QLExecutionConnection.builder();
		connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, execution -> {
			if (execution.getWorkflowType() == WorkflowType.PIPELINE) {
				final QLPipelineExecution.QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
				pipelineExecutionController.populatePipelineExecution(execution, builder);
				connectionBuilder.node(builder.build());
			} else if (execution.getWorkflowType() == WorkflowType.ORCHESTRATION) {
				final QLWorkflowExecution.QLWorkflowExecutionBuilder builder1 = QLWorkflowExecution.builder();
				workflowExecutionController.populateWorkflowExecution(execution, builder1);
				connectionBuilder.node(builder1.build());
			} else {
				String errorMgs = "Unsupported execution type: " + execution.getWorkflowType();
				log.error(errorMgs);
				throw new UnexpectedException(errorMgs);
			}
		}));

		return connectionBuilder.build();
	}
}
