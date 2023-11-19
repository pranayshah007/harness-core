// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package metric

import (
	"github.com/prometheus/client_golang/prometheus"
)

type Metrics struct {
	PutCount            *prometheus.CounterVec
	GetCount            *prometheus.CounterVec
	StreamAPIGetLatency prometheus.Gauge
	StreamAPIPutLatency prometheus.Gauge
	BlobAPILatency      prometheus.Gauge
}

var (
	// defining prometheus metric parameters directly in the package
	PutCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "log_service_stream_api_put_count",
			Help: "Total number of put requests to stream api",
		},
		[]string{"operation"},
	)

	GetCount = prometheus.NewCounterVec(
		prometheus.CounterOpts{
			Name: "log_service_stream_api_get_count",
			Help: "Total number of get requests to stream api",
		},
		[]string{"operation"},
	)

	StreamAPIGetLatency = prometheus.NewGauge(
		prometheus.GaugeOpts{
			Name: "log_service_stream_api_get_latency",
			Help: "Latency distribution of stream api requests",
		},
	)

	StreamAPIPutLatency = prometheus.NewGauge(
		prometheus.GaugeOpts{
			Name: "log_service_stream_api_put_latency",
			Help: "Latency distribution of stream api requests",
		},
	)

	BlobAPILatency = prometheus.NewGauge(
		prometheus.GaugeOpts{
			Name: "log_service_blob_api_latency",
			Help: "Latency for blob api for get, put, and delete requests",
		},
	)
)

func RegisterMetrics() *Metrics {
	putCount := PutCount
	getCount := GetCount
	streamAPIGetLatency := StreamAPIGetLatency
	streamAPIPutLatency := StreamAPIPutLatency
	blobAPILatency := BlobAPILatency

	prometheus.MustRegister(putCount, getCount, streamAPIGetLatency, streamAPIPutLatency, blobAPILatency)

	return &Metrics{
		PutCount:            putCount,
		GetCount:            getCount,
		StreamAPIGetLatency: streamAPIGetLatency,
		StreamAPIPutLatency: streamAPIPutLatency,
		BlobAPILatency:      blobAPILatency,
	}
}
