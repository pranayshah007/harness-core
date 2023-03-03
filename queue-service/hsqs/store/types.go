// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

// Package store package created to store all data types used by hsqs
package store

import (
	"fmt"
	"time"
)

// Request and Response Objects for HSQS API's

// RegisterTopicMetadata Request object for Registering Topic Metadata
type RegisterTopicMetadata struct {
	Topic      string
	MaxRetries int
	// time in nanoseconds
	// swagger:strfmt maxProcessingTime
	MaxProcessingTime      time.Duration `json:"maxProcessingTime"`
	MaxUnProcessedMessages int
}

// EnqueueRequest Request object for Enqueuing messages
type EnqueueRequest struct {
	Topic        string `json:"topic" validate:"required"`
	SubTopic     string `json:"subTopic" validate:"required"`
	Payload      string `json:"payload" validate:"required"`
	ProducerName string `json:"producerName" validate:"required"`
}

// EnqueueResponse Response object for Enqueuing messages
type EnqueueResponse struct {
	// ItemID is the identifier of the task in the Queue
	ItemID string `json:"itemId" validate:"required"`
}

// EnqueueErrorResponse Error Message object for Enqueuing messages
type EnqueueErrorResponse struct {
	ErrorMessage string `json:"errorMessage"`
}

func (e *EnqueueErrorResponse) Error() string {
	return fmt.Sprintf("EnqueueErrorResponse: message - %s",
		e.ErrorMessage)
}

// DequeueRequest Request object for Dequeuing messages
type DequeueRequest struct {
	Topic           string        `json:"topic" validate:"required"`
	BatchSize       int           `json:"batchSize" validate:"required"`
	ConsumerName    string        `json:"consumerName" validate:"required"`
	MaxWaitDuration time.Duration `json:"maxWaitDuration" validate:"required"`
}

// DequeueResponse Response object for Dequeuing messages
type DequeueResponse struct {
	ItemID       string              `json:"itemId"`
	Timestamp    int64               `json:"timeStamp"`
	Payload      string              `json:"payload"`
	QueueKey     string              `json:"queueKey"`
	ItemMetadata DequeueItemMetadata `json:"metadata"`
}

// DequeueItemMetadata DequeuingItem metadata request
type DequeueItemMetadata struct {
	CurrentRetryCount int     `json:"currentRetryCount"`
	MaxProcessingTime float64 `json:"maxProcessingTime"`
}

// DequeueErrorResponse Error Response object for Dequeue Request response
type DequeueErrorResponse struct {
	ErrorMessage string `json:"errorMessage"`
}

func (e *DequeueErrorResponse) Error() string {
	return fmt.Sprintf("DequeueErrorResponse: message - %s",
		e.ErrorMessage)
}

// AckRequest Request object for Acknowledging a message
type AckRequest struct {
	ItemID       string `json:"itemId" validate:"required"`
	Topic        string `json:"topic" validate:"required"`
	SubTopic     string `json:"subTopic" validate:"required"`
	ConsumerName string `json:"consumerName" validate:"required"`
}

// AckResponse Response object for Acknowledging a message
type AckResponse struct {
	ItemID string `json:"itemId"`
}

// AckErrorResponse Error Response object for Acknowledging a message
type AckErrorResponse struct {
	ErrorMessage string `json:"errorMessage"`
}

func (e *AckErrorResponse) Error() string {
	return fmt.Sprintf("AckErrorResponse: message - %s",
		e.ErrorMessage)
}

type UnAckType int

const (
	UnAckTopic UnAckType = iota + 1
	UnAckItem
)

func (u UnAckType) String() string {
	return []string{"UnAckTopic", "UnAckItem"}[u]
}

// UnAckRequest Request object for UnAck a message
// swagger:model UnAckRequest
type UnAckRequest struct {
	ItemID   string `json:"itemId"`
	Topic    string `json:"topic" validate:"required"`
	SubTopic string `json:"subTopic" validate:"required"`
	// Retry topic + subtopic after RetryAfterTimeDuration nanoseconds
	RetryAfterTimeDuration time.Duration `json:"retryTimeAfterDuration"`
	Type                   UnAckType     `json:"type"`
}

// UnAckResponse Response object for UnAck a message
type UnAckResponse struct {
	ItemID   string    `json:"itemId"`
	Topic    string    `json:"topic"`
	SubTopic string    `json:"subTopic"`
	Type     UnAckType `json:"type"`
}

// UnAckErrorResponse Response object for UnAck a message
type UnAckErrorResponse struct {
	ErrorMessage string `json:"errorMessage"`
}

func (e *UnAckErrorResponse) Error() string {
	return fmt.Sprintf("UnAckErrorResponse: message - %s",
		e.ErrorMessage)
}

// ValidateDequeueRequest helper method to Validate Dequeue Request
func ValidateDequeueRequest(request *DequeueRequest) error {
	if len(request.Topic) == 0 {
		return fmt.Errorf("DequeueRequest Topic is Missing")
	}
	if len(request.ConsumerName) == 0 {
		return fmt.Errorf("DequeueRequest ConsumerName is Missing")
	}
	if request.BatchSize <= 0 {
		return fmt.Errorf("DequeueRequest BatchSize should be greater than 0")
	}
	if request.MaxWaitDuration <= 0 {
		return fmt.Errorf("DequeueRequest MaxWaitDuration should be greater than 0")
	}
	return nil
}

// ValidateEnqueueRequest helper method to Validate Enqueue Request
func ValidateEnqueueRequest(request *EnqueueRequest) error {
	if len(request.Topic) == 0 {
		return fmt.Errorf("EnqueueRequest Topic is missing")
	}

	if len(request.SubTopic) == 0 {
		return fmt.Errorf("EnqueueRequest SubTopic is missing")
	}

	if len(request.ProducerName) == 0 {
		return fmt.Errorf("EnqueueRequest ProducerName is missing")
	}

	if len(request.Payload) == 0 {
		return fmt.Errorf("EnqueueRequest Payload is missing")
	}
	return nil

}

// ValidateAckRequest helper method to Validate Enqueue Request
func ValidateAckRequest(request *AckRequest) error {
	if len(request.Topic) == 0 {
		return fmt.Errorf("AckRequest Topic is missing")
	}

	if len(request.SubTopic) == 0 {
		return fmt.Errorf("AckRequest SubTopic is missing")
	}

	if len(request.ConsumerName) == 0 {
		return fmt.Errorf("AckRequest ConsumerName is missing")
	}

	if len(request.ItemID) == 0 {
		return fmt.Errorf("AckRequest ItemID is missing")
	}
	return nil

}

// ValidateAckRequest helper method to Validate Enqueue Request
func ValidateUnAckRequest(request *UnAckRequest) error {
	if len(request.Topic) == 0 {
		return fmt.Errorf("UnAckRequest Topic is missing")
	}

	if len(request.SubTopic) == 0 {
		return fmt.Errorf("UnAckRequest SubTopic is missing")
	}

	return nil

}
