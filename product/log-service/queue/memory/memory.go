// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package memory

import (
	"context"
	"sync"

	"github.com/harness/harness-core/product/log-service/entity"
	"github.com/harness/harness-core/product/log-service/queue"
)

var _ queue.Queue = (*Queuer)(nil)

type Queuer struct {
	sync.Mutex
	queue chan map[string]interface{}
}

func New() *Queuer {
	return &Queuer{
		queue: make(chan map[string]interface{}, 10),
	}
}

func (q *Queuer) Create(ctx context.Context, stream string, group string) error {
	return nil
}

func (q *Queuer) Produce(ctx context.Context, stream string, key string, zipKey string, m []string) error {
	q.Lock()
	defer q.Unlock()
	event := entity.EventQueue{key, zipKey, m}
	b, _ := event.MarshalBinary()
	q.queue <- map[string]interface{}{"prefix": string(b)}
	return nil
}

func (q *Queuer) Consume(ctx context.Context, stream string, cGroup string, cName string) (map[string]interface{}, error) {
	q.Lock()
	defer q.Unlock()
	m2 := <-q.queue
	return m2, nil
}
