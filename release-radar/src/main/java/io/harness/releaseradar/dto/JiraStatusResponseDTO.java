package io.harness.releaseradar.dto;

import io.harness.releaseradar.beans.CommitDetails;
import io.harness.releaseradar.beans.Environment;
import io.harness.releaseradar.beans.Service;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Set;

@Data
@Builder
public class JiraStatusResponseDTO {
    Map<CommitDetails, Set<Pair<Environment, Service>>> commitDetailsListMap;
}
