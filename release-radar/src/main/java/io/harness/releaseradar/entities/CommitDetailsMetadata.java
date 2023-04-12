package io.harness.releaseradar.entities;

import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class CommitDetailsMetadata {
    String sha;
    Date timestamp;
}
