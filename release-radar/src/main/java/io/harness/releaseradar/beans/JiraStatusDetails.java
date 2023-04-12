package io.harness.releaseradar.beans;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Set;

@Data
@Builder
@ToString
public class JiraStatusDetails {
    Map<CommitDetails, Set<Pair<Environment, Service>>> commitDetailsListMap;
}
