package io.harness.ng.core.migration.timescale;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.migration.NGMigration;
import io.harness.timescaledb.DBUtils;
import io.harness.timescaledb.TimeScaleDBService;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@Slf4j
@Singleton
public class PopulateRollbackDurationToServiceInfraInfoTable implements NGMigration {
  @Inject TimeScaleDBService timeScaleDBService;
  @Inject MongoTemplate mongoTemplate;
  private static final int MAX_RETRY = 5;
  private static int MAX_NODES_BATCH_SIZE = 1000;
  public static final Set<String> fieldsForMigration =
      Sets.newHashSet(NodeExecution.NodeExecutionKeys.nodeId, NodeExecution.NodeExecutionKeys.name,
          NodeExecution.NodeExecutionKeys.startTs, NodeExecution.NodeExecutionKeys.endTs);
  private static final String update_statement = "UPDATE service_infra_info SET rollback_duration=? WHERE id=?";
  private static final String query_statement = "SELECT * FROM service_infra_info WHERE id=?";

  @Override
  public void migrate() {
    if (!timeScaleDBService.isValid()) {
      log.info("TimeScaleDB not found, not migrating deployment data to TimeScaleDB");
      return;
    }
    List<NodeExecution> nodeExecutionList = new LinkedList<>();
    long count = 0;
    int currentPage = 0;
    int totalPages = 0;
    try {
      do {
        Query query = query(where(NodeExecution.NodeExecutionKeys.name).is("(Rollback)"))
                          .with(PageRequest.of(currentPage, MAX_NODES_BATCH_SIZE));
        for (String fieldName : fieldsForMigration) {
          query.fields().include(fieldName);
        }
        validateNodeExecutionListQueryObject(query);
        validateQueryFieldsForNodeExecution(fieldsForMigration);
        Pageable pageable = PageRequest.of(currentPage, MAX_NODES_BATCH_SIZE);
        List<NodeExecution> nodeExecutions = mongoTemplate.find(query, NodeExecution.class);
        long nodesInPage = findCount(query);
        Page<NodeExecution> paginatedNodeExecutions =
            PageableExecutionUtils.getPage(nodeExecutions, pageable, () -> nodesInPage);
        ;
        if (paginatedNodeExecutions == null || paginatedNodeExecutions.getTotalElements() == 0) {
          break;
        }
        totalPages = paginatedNodeExecutions.getTotalPages();
        nodeExecutionList.addAll(new LinkedList<>(paginatedNodeExecutions.getContent()));
        currentPage++;
      } while (currentPage < totalPages);
      for (NodeExecution nodeExecutionEntity : nodeExecutionList) {
        updateRollbackDurationInTimeScaleDB(nodeExecutionEntity);
        count++;
        if (count % 100 == 0) {
          log.info("Completed migrating node execution [{}] records", count);
        }
      }
    } catch (Exception e) {
      log.warn("Failed to complete rollback duration migration", e);
    } finally {
      log.info("Completed updating [{}] records", count);
    }
  }

  private void updateRollbackDurationInTimeScaleDB(NodeExecution nodeExecution) {
    long startTime = System.currentTimeMillis();
    boolean successful = false;
    int retryCount = 0;
    int index = 0;
    String nodeId;

    Long rollbackStartTs = nodeExecution.getStartTs();
    Long rollbackEndTs = rollbackStartTs == null ? null : nodeExecution.getEndTs();
    Long rollbackDuration = rollbackEndTs == null ? null : rollbackEndTs - rollbackStartTs;
    if (rollbackDuration == null) {
      rollbackDuration = 0L;
    }

    if (nodeExecution.getNodeId().endsWith("_combinedRollback")) {
      index = nodeExecution.getNodeId().indexOf("_combinedRollback");
      nodeId = nodeExecution.getNodeId().substring(0, index);
    } else {
      return;
    }

    while (!successful && retryCount < MAX_RETRY) {
      ResultSet queryResult = null;
      try (Connection connection = timeScaleDBService.getDBConnection();
           PreparedStatement queryStatement = connection.prepareStatement(query_statement);
           PreparedStatement updateStatement = connection.prepareStatement(update_statement)) {
        queryStatement.setString(1, nodeId);
        queryResult = queryStatement.executeQuery();
        if (queryResult != null && queryResult.next()) {
          log.info("service_infra_info found:[{}],updating it", nodeId);
          updateDataInTimescaleDB(nodeId, updateStatement, rollbackDuration);
        }

        successful = true;
      } catch (SQLException e) {
        if (retryCount >= MAX_RETRY) {
          log.error("Failed to update service_infra_info,[{}]", nodeId, e);
        } else {
          log.info("Failed to update service_infra_info,[{}],retryCount=[{}]", nodeId, retryCount);
        }
        retryCount++;
      } catch (Exception e) {
        log.error("Failed to update service_infra_info,[{}]", nodeId, e);
        retryCount = MAX_RETRY + 1;
      } finally {
        DBUtils.close(queryResult);
        log.info("Total update time =[{}] for service_infra_info:[{}]", System.currentTimeMillis() - startTime, nodeId);
      }
    }
  }

  private void updateDataInTimescaleDB(String nodeId, PreparedStatement updateStatement, long rollbackDuration)
      throws SQLException {
    updateStatement.setLong(1, rollbackDuration);
    updateStatement.setString(2, nodeId);
    updateStatement.execute();
  }

  private void validateNodeExecutionListQueryObject(Query query) {
    if (query.getLimit() <= 0 || query.getLimit() > MAX_NODES_BATCH_SIZE) {
      throw new InvalidRequestException(
          "NodeExecution query should have limit within max batch size- " + MAX_NODES_BATCH_SIZE);
    }
    if (query.getFieldsObject().isEmpty()) {
      throw new InvalidRequestException("NodeExecution list query should have projection fields");
    }
  }

  private void validateQueryFieldsForNodeExecution(Set<String> fieldsToInclude) {
    if (EmptyPredicate.isEmpty(fieldsToInclude)) {
      throw new InvalidRequestException("Projection fields for NodeExecution in fetchNodeExecutions is required");
    }
  }

  public long findCount(Query query) {
    return mongoTemplate.count(Query.of(query).limit(-1).skip(-1), NodeExecution.class);
  }
}