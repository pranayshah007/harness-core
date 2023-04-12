package io.harness.releaseradar.beans;

import lombok.Builder;

@Builder
public class CommitDetailsRequest {
  String branch;
  int pageCount;
}