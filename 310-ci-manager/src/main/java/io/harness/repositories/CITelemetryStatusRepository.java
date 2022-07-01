package io.harness.repositories;

import io.harness.annotation.HarnessRepo;
import io.harness.ci.beans.entities.CITelemetrySentStatus;

import org.springframework.data.repository.PagingAndSortingRepository;

@HarnessRepo
public interface CITelemetryStatusRepository
    extends PagingAndSortingRepository<CITelemetrySentStatus, String>, CITelemetryStatusRepositoryCustom {}
