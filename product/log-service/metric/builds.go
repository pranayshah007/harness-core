// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package metric

import (
	"github.com/prometheus/client_golang/prometheus"
)

// function to return prometheus counter metric
func CreatePrometheusCounter(name, help, operation string) *prometheus.CounterVec {
	return prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: name,
			Help: help,
		},
		[]string{"operation"},
	)
}

var (
	// defining prometheus metric parameters
	PutCount = CreatePrometheusCounter(
		"log_service_stream_api_put_count",
		"Total number of put requests to stream api",
		"put",
	)

	GetCount = CreatePrometheusCounter(
		"log_service_stream_api_get_count",
		"Total number of get requests to stream api",
		"get",
	)
)

func RegisterMetrics() {
	prometheus.MustRegister(PutCount)
	prometheus.MustRegister(GetCount)
}
