// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package redis

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/harness/harness-core/queue-service/hsqs/store"
)

const (
	topic    = "PIPELINE"
	producer = "PMS"
	consumer = "PMS"
)

var sema chan struct{}

func BenchmarkEndToEndSimpleEnqueueWithGroupRegistration(b *testing.B) {
	const count = 100
	const queueCount = 100
	redisStore := NewRedisStore("localhost:6379")
	parent, parentCancel := context.WithTimeout(context.Background(), 100*time.Second)
	defer parentCancel()
	//enqueue and register streams with consumer groups
	for k := 0; k < queueCount; k++ {
		for j := 0; j < count; j++ {
			ctx, _ := context.WithTimeout(parent, 100*time.Millisecond)
			if _, err := redisStore.Enqueue(ctx, store.EnqueueRequest{
				Topic:        topic,
				SubTopic:     "A" + strconv.Itoa(k),
				Payload:      "Payload",
				ProducerName: producer,
			}); err != nil {
				b.Fatalf("could not enqueue a task: %v", err.Error())
			}
		}
	}

	if parent.Err() != nil {
		b.Fatalf("could not complete the benchmark task in time: %v", parent.Err().Error())
	}
}

func BenchmarkDequeueAllTheMessages(b *testing.B) {
	processed := make(chan string, 10000)
	done := make(chan bool)
	redisStore := NewRedisStore("localhost:6379")
	sema = make(chan struct{}, 10)
	parent, _ := context.WithTimeout(context.Background(), 100*time.Second)

	for i := 0; i < b.N; i++ {
		select {
		case <-done:
			{
				// execution has been completed, complete the context and break the loop
				parent.Done()
				break
			}
		default:
			go func() {
				b.StartTimer()
				sema <- struct{}{}
				ctx, _ := context.WithCancel(parent)
				deqRequest := store.DequeueRequest{
					Topic:           topic,
					BatchSize:       100,
					ConsumerName:    consumer + strconv.Itoa(i%10),
					MaxWaitDuration: 10 * time.Millisecond,
				}
				dequeResponse, err := redisStore.Dequeue(ctx, deqRequest)
				if err != nil {
					b.Errorf("could not dequeue tasks inside: %v", err.Error())
				}
				fmt.Printf("length is %d", len(processed))
				if len(processed) == 10000 {
					done <- true
					return
				} else if len(dequeResponse) != 0 {
					for _, m := range dequeResponse {
						redisStore.Ack(ctx, store.AckRequest{
							ItemID:       m.ItemID,
							Topic:        deqRequest.Topic,
							SubTopic:     strings.Split(m.QueueKey, ":")[2],
							ConsumerName: deqRequest.ConsumerName,
						})
						if len(processed) == 10000 {
							done <- true
						} else {
							processed <- m.ItemID
						}

					}
				}
				<-sema
				return
			}()
		}
	}

	if parent.Err() != nil {
		b.Fatalf("could not complete the benchmark task in time: %v", parent.Err().Error())
	}
}
