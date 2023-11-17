// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package metric

import (
	"github.com/prometheus/client_golang/prometheus"
)

type Metrics struct{
    CreatePrometheusCounter *prometheus.CounterVec
    CreatePrometheusGauge   prometheus.Gauge
}

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

func CreatePrometheusGauge(name, help string) prometheus.Gauge {
	return prometheus.NewGauge(
		prometheus.GaugeOpts{
			Name: name,
			Help: help,
		},
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
	StreamAPIGetLatency = CreatePrometheusGauge(
		"log_service_stream_api_get_latency",
		"Latency distribution of stream api requests",
	)
	StreamAPIPutLatency = CreatePrometheusGauge(
		"log_service_stream_api_put_latency",
		"Latency distribution of stream api requests",
	)
	BlobAPILatency = CreatePrometheusGauge(
		"log_service_blob_api_latency",
		"Latency for blob api for get, put, and delete requests",
	)
)

func RegisterMetrics() {
	prometheus.MustRegister(PutCount)
	prometheus.MustRegister(GetCount)
	prometheus.MustRegister(StreamAPIGetLatency)
	prometheus.MustRegister(StreamAPIPutLatency)
	prometheus.MustRegister(BlobAPILatency)
}
