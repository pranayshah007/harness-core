package io.harness.releaseradar.services;


import io.harness.releaseradar.beans.CommitDetails;
import io.harness.releaseradar.beans.CommitDetailsRequest;

import java.io.IOException;
import java.util.List;

public interface GitService {
    List<CommitDetails> getCommitList(CommitDetailsRequest commitDetailsRequest) throws IOException;
}