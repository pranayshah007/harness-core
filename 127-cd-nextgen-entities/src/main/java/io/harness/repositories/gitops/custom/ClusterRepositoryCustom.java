package io.harness.repositories.gitops.custom;

import static io.harness.annotations.dev.HarnessTeam.GITOPS;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.gitops.entity.Cluster;

import com.mongodb.client.result.DeleteResult;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(GITOPS)
public interface ClusterRepositoryCustom {
  Page<Cluster> find(@NotNull Criteria criteria, @NotNull Pageable pageable);
  Cluster create(@NotNull Cluster cluster);
  Cluster update(@NotNull Criteria criteria, @NotNull Cluster cluster);
  DeleteResult delete(@NotNull Criteria criteria);
  Cluster findOne(@NotNull Criteria criteria);
}
