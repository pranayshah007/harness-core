package io.harness.batch.processing.config;

import io.harness.batch.processing.ccm.BatchJobType;
import io.harness.batch.processing.svcmetrics.BatchJobExecutionListener;
import io.harness.batch.processing.tasklet.RecommendationServicenowStatusTasklet;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RecommendationServicenowStatusConfig {
  @Autowired private BatchJobExecutionListener batchJobExecutionListener;

  @Bean
  public Tasklet recommendationServicenowStatusTasklet() {
    return new RecommendationServicenowStatusTasklet();
  }

  @Bean
  @Autowired
  @Qualifier(value = "recommendationServicenowStatusJob")
  public Job recommendationServicenowStatusJob(
      JobBuilderFactory jobBuilderFactory, Step recommendationServicenowStatusStep) {
    return jobBuilderFactory.get(BatchJobType.RECOMMENDATION_SERVICENOW_STATUS.name())
        .incrementer(new RunIdIncrementer())
        .listener(batchJobExecutionListener)
        .start(recommendationServicenowStatusStep)
        .build();
  }

  @Bean
  public Step recommendationServicenowStatusStep(StepBuilderFactory stepBuilderFactory) {
    return stepBuilderFactory.get("recommendationServicenowStatusStep")
        .tasklet(recommendationServicenowStatusTasklet())
        .build();
  }
}
