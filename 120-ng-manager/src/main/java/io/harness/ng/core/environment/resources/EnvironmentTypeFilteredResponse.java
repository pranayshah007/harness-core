package io.harness.ng.core.environment.resources;

import io.harness.accesscontrol.acl.api.AccessControlDTO;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EnvironmentTypeFilteredResponse {
  List<AccessControlDTO> accessControlDTOList;
  boolean hasPreProdAccess;
  boolean hasProdAccess;
}
