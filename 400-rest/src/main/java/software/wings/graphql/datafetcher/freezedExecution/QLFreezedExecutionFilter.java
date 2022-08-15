package software.wings.graphql.datafetcher.freezedExecution;

import software.wings.graphql.schema.type.aggregation.EntityFilter;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;

public class QLFreezedExecutionFilter implements EntityFilter {
	private QLIdFilter rejectedByFreezeWindowId;
	private QLIdFilter application;
	private QLIdFilter service;
	private QLIdFilter environment;
}
