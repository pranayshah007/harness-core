package io.harness.metrics;

import io.harness.metrics.service.api.MetricService;
import io.harness.metrics.service.api.MetricsPublisher;
import io.harness.mongo.metrics.HarnessConnectionPoolListener;
import io.harness.mongo.metrics.HarnessConnectionPoolStatistics;
import io.harness.mongo.metrics.MongoMetricsContext;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.connection.ServerId;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@Singleton
public class NextGenManagerMongoDBMetricsPublisher implements MetricsPublisher {
  private final HarnessConnectionPoolListener harnessConnectionPoolListener;
  private final MetricService metricService;
  private static final String METRIC_PREFIX = "nextgen_manager_mongodb_";
  private static final Pattern METRIC_NAME_RE = Pattern.compile("[^a-zA-Z0-9_]");
  private static final String WAIT_QUEUE_SIZE = "wait_queue_size";
  private static final String CONNECTION_POOL_SIZE = "connection_pool_size";
  private static final String CONNECTIONS_CHECKED_OUT = "connections_checked_out";
  private static final String CONNECTION_POOL_MAX_SIZE = "connection_pool_max_size";
  private static final String NAMESPACE = System.getenv("NAMESPACE");
  private static final String CONTAINER_NAME = System.getenv("CONTAINER_NAME");

  @Override
  public void recordMetrics() {
    ConcurrentMap<ServerId, HarnessConnectionPoolStatistics> map = harnessConnectionPoolListener.getStatistics();
    map.forEach((serverId, harnessConnectionPoolStatistics) -> {
      String serverAddress = sanitizeName(serverId.getAddress().toString());
      String clientDescription = sanitizeName(serverId.getClusterId().getDescription());
      try (MongoMetricsContext ignore =
               new MongoMetricsContext(NAMESPACE, CONTAINER_NAME, serverAddress, clientDescription)) {
        recordMetric(CONNECTION_POOL_MAX_SIZE, harnessConnectionPoolStatistics.getMaxSize());
        recordMetric(CONNECTION_POOL_SIZE, harnessConnectionPoolStatistics.getSize());
        recordMetric(CONNECTIONS_CHECKED_OUT, harnessConnectionPoolStatistics.getCheckedOutCount());
        recordMetric(WAIT_QUEUE_SIZE, harnessConnectionPoolStatistics.getWaitQueueSize());
      }
    });
  }

  private static String sanitizeName(String labelName) {
    String name = METRIC_NAME_RE.matcher(labelName).replaceAll("_");
    if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
      name = "_" + name;
    }
    return name;
  }

  private void recordMetric(String name, double value) {
    metricService.recordMetric(METRIC_PREFIX + name, value);
  }
}
