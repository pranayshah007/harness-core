package io.harness.releaseradar.entities;

import io.harness.releaseradar.beans.Environment;
import io.harness.releaseradar.beans.Service;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
public class CommitDetailsMetadata {
    String sha;
    Date timestamp;
    Environment environment;
    Service service;
}
