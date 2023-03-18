// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package metrics

import (
	"context"

	"github.com/prometheus/client_golang/prometheus"
)

type MetricsHandler struct {
	enqueueCounter        *prometheus.CounterVec
	enqueueFailureCounter *prometheus.CounterVec
	dequeueCounter        *prometheus.CounterVec
	dequeueFailureCounter *prometheus.CounterVec
	ackCounter            *prometheus.CounterVec
	ackFailureCounter     *prometheus.CounterVec
}

func InitMetrics() *MetricsHandler {

	enqueueCounter := prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "enqueue_requests_per_module_success",
			Help: "The total number of successful enqueue requests per topic and subtopic.",
		},
		[]string{"topic", "subTopic"},
	)
	enqueueFailureCounter := prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "enqueue_requests_per_module_failure",
			Help: "The total number of failed enqueue requests per topic and subtopic.",
		},
		[]string{"topic", "subTopic"},
	)
	dequeueCounter := prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "dequeue_requests_per_module_success",
			Help: "The total number of successful dequeue requests per topic and subtopic.",
		},
		[]string{"topic"},
	)
	dequeueFailureCounter := prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "dequeue_requests_per_module_failure",
			Help: "The total number of failed dequeue requests per topic and subtopic.",
		},
		[]string{"topic"},
	)
	ackCounter := prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "ack_requests_per_module_success",
			Help: "The total number of successful ack requests per topic and subtopic.",
		},
		[]string{"topic", "subTopic"},
	)
	ackFailureCounter := prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "ack_requests_per_module_failure",
			Help: "The total number of failed ack requests per topic and subtopic.",
		},
		[]string{"topic", "subTopic"},
	)
	prometheus.MustRegister(enqueueCounter, enqueueFailureCounter, dequeueCounter, dequeueFailureCounter, ackCounter, ackFailureCounter)
	return &MetricsHandler{enqueueCounter: enqueueCounter, enqueueFailureCounter: enqueueFailureCounter, dequeueCounter: dequeueCounter, dequeueFailureCounter: dequeueFailureCounter, ackCounter: ackCounter, ackFailureCounter: ackFailureCounter}

}

// Method to orchestrate Custom Metrics , cna be refactored
func (m *MetricsHandler) CountMetric(ctx context.Context, success bool, operationType string, labelValues ...string) {

	if operationType == "queue" && success {
		m.enqueueCounter.WithLabelValues(labelValues[0], labelValues[1]).Inc()
	} else if operationType == "queue" && !success {
		m.enqueueFailureCounter.WithLabelValues(labelValues[0], labelValues[1]).Inc()
	} else if operationType == "dequeue" && success {
		m.dequeueCounter.WithLabelValues(labelValues[0]).Inc()
	} else if operationType == "dequeue" && !success {
		m.dequeueFailureCounter.WithLabelValues(labelValues[0]).Inc()
	} else if operationType == "ack" && success {
		m.ackCounter.WithLabelValues(labelValues[0], labelValues[1]).Inc()
	} else if operationType == "ack" && !success {
		m.ackFailureCounter.WithLabelValues(labelValues[0], labelValues[1]).Inc()
	}
}
