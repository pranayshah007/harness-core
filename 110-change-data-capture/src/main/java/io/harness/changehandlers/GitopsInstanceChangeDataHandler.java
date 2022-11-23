package io.harness.changehandlers;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.changestreamsframework.ChangeEvent;

import com.mongodb.DBObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OwnedBy(HarnessTeam.GITOPS)
public class GitopsInstanceChangeDataHandler extends AbstractChangeDataHandler {
  @Override
  public Map<String, String> getColumnValueMapping(ChangeEvent<?> changeEvent, String[] fields) {
    if (changeEvent == null) {
      return null;
    }
    Map<String, String> columnValueMapping = new HashMap<>();
    DBObject dbObject = changeEvent.getFullDocument();

    columnValueMapping.put("id", changeEvent.getUuid());

    if (dbObject == null) {
      return columnValueMapping;
    }

    DBObject instanceInfoObject = (DBObject) dbObject.get("instanceInfo");
    if (instanceInfoObject == null) {
      return null;
    }
    // add column in tsdb
    if (instanceInfoObject.get("clusterIdentifier") != null) {
      columnValueMapping.put("cluster_identifier", instanceInfoObject.get("clusterIdentifier").toString());
    } else {
      return null;
    }
    // add column in tsdb
    if (instanceInfoObject.get("agentIdentifier") != null) {
      columnValueMapping.put("agent_identifier", instanceInfoObject.get("agentIdentifier").toString());
    }

    if (dbObject.get("accountIdentifier") != null) {
      columnValueMapping.put("accountid", dbObject.get("accountIdentifier").toString());
    }
    if (dbObject.get("orgIdentifier") != null) {
      columnValueMapping.put("orgidentifier", dbObject.get("orgIdentifier").toString());
    }
    if (dbObject.get("projectIdentifier") != null) {
      columnValueMapping.put("projectidentifier", dbObject.get("projectIdentifier").toString());
    }
    if (dbObject.get("envIdentifier") != null) {
      columnValueMapping.put("env_id", dbObject.get("envIdentifier").toString());
    }
    if (dbObject.get("envName") != null) {
      columnValueMapping.put("env_name", dbObject.get("envName").toString());
    }
    if (dbObject.get("envType") != null) {
      columnValueMapping.put("env_type", dbObject.get("envType").toString());
    }
    DBObject primaryArtifactObject = (DBObject) dbObject.get("primaryArtifact");
    if (primaryArtifactObject != null && primaryArtifactObject.get("tag") != null) {
      columnValueMapping.put("tag", primaryArtifactObject.get("tag").toString());
    }

    if (dbObject.get("lastPipelineExecutionId") != null) {
      columnValueMapping.put("pipeline_execution_summary_cd_id", dbObject.get("lastPipelineExecutionId").toString());
    }
    columnValueMapping.put("service_status", "SUCCESS");
    if (dbObject.get("serviceIdentifier") != null) {
      columnValueMapping.put("service_id", dbObject.get("serviceIdentifier").toString());
    }
    if (dbObject.get("serviceName") != null) {
      columnValueMapping.put("service_name", dbObject.get("serviceName").toString());
    }
    columnValueMapping.put("deployment_type", "Kubernetes");
    if (dbObject.get("lastDeployedAt") != null) {
      columnValueMapping.put(
          "service_startts", String.valueOf(Long.parseLong(dbObject.get("lastDeployedAt").toString())));
    } else {
      // populating as this column is not null
      columnValueMapping.put("service_startts", "");
    }
    return columnValueMapping;
  }

  @Override
  public List<String> getPrimaryKeys() {
    return asList("id", "service_startts");
  }
}
