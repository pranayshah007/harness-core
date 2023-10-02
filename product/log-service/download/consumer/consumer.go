// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package consumer

import (
	"context"
	"strconv"
	"time"

	"github.com/harness/harness-core/product/log-service/cache"
	"github.com/harness/harness-core/product/log-service/config"
	"github.com/harness/harness-core/product/log-service/download"
	"github.com/harness/harness-core/product/log-service/logger"
	"github.com/harness/harness-core/product/log-service/queue"
	"github.com/harness/harness-core/product/log-service/store"
)

const (
	CONSUMER_PREFIX string = "consumer:"
)

type ConsumerPool struct {
	workerLimit   int
	StreamName    string
	ConsumerGroup string
}

func NewWorkerPool(maxWorker int, consumerGroup, streamName string) download.Worker {
	wp := &ConsumerPool{
		workerLimit:   maxWorker,
		ConsumerGroup: consumerGroup,
		StreamName:    streamName,
	}

	return wp
}

func (wp *ConsumerPool) Execute(fn func(ctx context.Context, wID string, q queue.Queue, c cache.Cache, s store.Store, cfg config.Config), q queue.Queue, c cache.Cache, s store.Store, cfg config.Config) {
	ctx := context.Background()
	logEntry := logger.FromContext(ctx).
		WithField("stream name", wp.StreamName).
		WithField("consumer group", wp.ConsumerGroup)

	for i := 1; i <= wp.workerLimit; i++ {
		wID := CONSUMER_PREFIX + strconv.Itoa(i)
		logEntry.
			WithField("time", time.Now().Format(time.RFC3339)).
			Infoln("Worker %s has been started", wID)

		go fn(ctx, wID, q, c, s, cfg)
	}
}
