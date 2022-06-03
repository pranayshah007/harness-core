package io.harness.repositories.gitops.spring;

import io.harness.annotation.HarnessRepo;
import io.harness.cdng.gitops.entity.Cluster;
import io.harness.repositories.gitops.custom.ClusterRepositoryCustom;

import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface ClusterRepository extends PagingAndSortingRepository<Cluster, String>, ClusterRepositoryCustom {}
