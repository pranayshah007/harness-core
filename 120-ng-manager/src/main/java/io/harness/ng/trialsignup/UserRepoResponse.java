package io.harness.ng.trialsignup;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserRepoResponse {
  String namespace;
  String name;
}
