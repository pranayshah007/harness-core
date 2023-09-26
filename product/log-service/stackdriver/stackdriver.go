// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package stackdriver defines the struct for sending logs to stackdriver
package stackdriver

import (
	"cloud.google.com/go/logging"
	"context"
	"errors"
	"github.com/harness/harness-core/product/log-service/logger"
)

// Stackdriver defines the struct for sending logs to GCP stackdriver.
type Stackdriver struct {
	client *logging.Client
}

func New(ctx context.Context, projectId string) (*Stackdriver, error) {
	if len(projectId) == 0 {
		return nil, errors.New("stackdriver not configured, projectId is required")
	}

	client, err := logging.NewClient(ctx, "projects/"+projectId)
	if err != nil {
		return nil, err
	}

	client.OnError = func(err error) {
		logger.FromContext(ctx).WithError(err).Errorln("error flushing logs to stackdriver")
	}

	return &Stackdriver{client: client}, nil
}

func (s *Stackdriver) Write(key string, entry logging.Entry) {
	s.client.Logger(key).Log(entry)
}

func (s *Stackdriver) Ping(ctx context.Context) error {
	logger.FromContext(ctx).Infoln("Pinging stackdriver")
	return s.client.Ping(ctx)
}

func (s *Stackdriver) Close() error {
	return s.client.Close()
}

// Line represents a structured line in the stackdriver logs.
type Line struct {
	Labels   map[string]string      `json:"labels"`
	Payload  map[string]interface{} `json:"payload"`
	Severity int                    `json:"severity"`
}
