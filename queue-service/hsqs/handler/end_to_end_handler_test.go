// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"bytes"
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/golang-jwt/jwt"
	jwt4 "github.com/golang-jwt/jwt/v4"
	"github.com/harness/harness-core/queue-service/hsqs/config"
	"github.com/harness/harness-core/queue-service/hsqs/store"
	"github.com/stretchr/testify/assert"
)

type harnessClaims struct {
	Type     string `json:"type"`
	Name     string `json:"name"`
	Email    string `json:"email"`
	Username string `json:"username"`
	jwt4.RegisteredClaims
}

const (
	url      = "http://localhost:9091"
	topic    = "PMS"
	producer = "PIPELINE"
	consumer = "PIPELINE"
)

// Test to Insert data into a single Queue and SubTopic, and dequeued afterwards from the same queue
func Test_EnqueueThenDequeueSingleTopicSingleQueue(t *testing.T) {

	for i := 0; i < 10; i++ {
		enqueueRequest := store.EnqueueRequest{
			Topic:        topic,
			SubTopic:     "ACCOUNT1",
			Payload:      "PAYLOAD1",
			ProducerName: producer,
		}

		requestBody, _ := json.Marshal(enqueueRequest)
		req, _ := http.NewRequest("POST", url+"/v1/queue", bytes.NewBuffer(requestBody))
		resp, err := call(req)
		if err != nil {
			panic(err)
		}
		assert.Equal(t, "200 OK", resp.Status)

	}

	// dequeue request
	dequeueRequest := store.DequeueRequest{
		Topic:           topic,
		BatchSize:       10,
		ConsumerName:    producer,
		MaxWaitDuration: 10,
	}

	requestBody, _ := json.Marshal(dequeueRequest)
	req, _ := http.NewRequest("POST", url+"/v1/dequeue", bytes.NewBuffer(requestBody))
	resp, err := call(req)
	if err != nil {
		panic(err)
	}
	assert.Equal(t, "200 OK", resp.Status)

	buf := &bytes.Buffer{}
	buf.ReadFrom(resp.Body)

	data := buf.Bytes()
	var dequeResponses []store.DequeueResponse

	json.Unmarshal(data, &dequeResponses)
	assert.Equal(t, 10, len(dequeResponses))
}

// Test to Insert data into a Multiple Topics, and dequeued afterwards from the respective queues
func Test_EnqueueThenDequeueMultipleTopicMultipleQueue(t *testing.T) {

	for i := 0; i < 10; i++ {
		enqueueRequest := store.EnqueueRequest{
			Topic:        "PMS" + strconv.Itoa(i),
			SubTopic:     "ACCOUNT",
			Payload:      "PAYLOAD1",
			ProducerName: producer,
		}

		requestBody, _ := json.Marshal(enqueueRequest)
		req, _ := http.NewRequest("POST", url+"/v1/queue", bytes.NewBuffer(requestBody))
		resp, err := call(req)
		if err != nil {
			panic(err)
		}
		assert.Equal(t, "200 OK", resp.Status)

	}

	// dequeue request
	for i := 0; i < 10; i++ {

		dequeueRequest := store.DequeueRequest{
			Topic:           "PMS" + strconv.Itoa(i),
			BatchSize:       1,
			ConsumerName:    consumer,
			MaxWaitDuration: 10,
		}

		requestBody, _ := json.Marshal(dequeueRequest)
		req, _ := http.NewRequest("POST", url+"/v1/dequeue", bytes.NewBuffer(requestBody))
		resp, err := call(req)
		if err != nil {
			panic(err)
		}
		assert.Equal(t, "200 OK", resp.Status)

		buf := &bytes.Buffer{}
		buf.ReadFrom(resp.Body)

		data := buf.Bytes()
		var dequeResponses []store.DequeueResponse

		json.Unmarshal(data, &dequeResponses)
		assert.Equal(t, 1, len(dequeResponses))
	}
}

// Test to Insert data into a Topic, and fetching more data via dequeue than available for that particular topic
func Test_EnqueueThenDequeueMoreMessagesThanAvailable(t *testing.T) {

	for i := 0; i < 5; i++ {
		enqueueRequest := store.EnqueueRequest{
			Topic:        topic,
			SubTopic:     "ACCOUNT1",
			Payload:      "PAYLOAD1",
			ProducerName: producer,
		}

		requestBody, _ := json.Marshal(enqueueRequest)
		req, _ := http.NewRequest("POST", url+"/v1/queue", bytes.NewBuffer(requestBody))
		resp, err := call(req)
		if err != nil {
			panic(err)
		}
		assert.Equal(t, "200 OK", resp.Status)

	}

	// dequeue request
	dequeueRequest := store.DequeueRequest{
		Topic:           topic,
		BatchSize:       10,
		ConsumerName:    consumer,
		MaxWaitDuration: 10,
	}

	requestBody, _ := json.Marshal(dequeueRequest)
	req, _ := http.NewRequest("POST", url+"/v1/dequeue", bytes.NewBuffer(requestBody))
	resp, err := call(req)
	if err != nil {
		panic(err)
	}
	assert.Equal(t, "200 OK", resp.Status)

	buf := &bytes.Buffer{}
	buf.ReadFrom(resp.Body)

	data := buf.Bytes()
	var dequeResponses []store.DequeueResponse

	json.Unmarshal(data, &dequeResponses)
	// as only 5 messages are present in queue, it would return only 5 messages when batch size is 10 messages
	assert.Equal(t, 5, len(dequeResponses))
}

// Test to Insert data into a Topic, and fetching same data via dequeue after acknowledging few of the messages
func Test_EnqueueThenMultipleDequeueWithPartialAcknowledgement(t *testing.T) {
	for i := 0; i < 10; i++ {
		enqueueRequest := store.EnqueueRequest{
			Topic:        topic,
			SubTopic:     "ACCOUNT1",
			Payload:      "PAYLOAD1",
			ProducerName: producer,
		}

		requestBody, _ := json.Marshal(enqueueRequest)
		req, _ := http.NewRequest("POST", url+"/v1/queue", bytes.NewBuffer(requestBody))
		resp, err := call(req)
		if err != nil {
			panic(err)
		}
		assert.Equal(t, "200 OK", resp.Status)

	}

	// dequeue request
	dequeueRequest := store.DequeueRequest{
		Topic:           topic,
		BatchSize:       10,
		ConsumerName:    producer,
		MaxWaitDuration: 10,
	}

	requestBody, _ := json.Marshal(dequeueRequest)
	req, _ := http.NewRequest("POST", url+"/v1/dequeue", bytes.NewBuffer(requestBody))
	resp, err := call(req)
	if err != nil {
		panic(err)
	}
	assert.Equal(t, "200 OK", resp.Status)

	buf := &bytes.Buffer{}
	buf.ReadFrom(resp.Body)

	data := buf.Bytes()
	var dequeResponses []store.DequeueResponse

	json.Unmarshal(data, &dequeResponses)
	assert.Equal(t, 10, len(dequeResponses))

	// end dequeue Request

	// Ack partial messages
	for i := 0; i < 5; i++ {
		ackRequest := store.AckRequest{
			ItemID:       dequeResponses[i].ItemID,
			Topic:        topic,
			SubTopic:     strings.Split(dequeResponses[i].QueueKey, ":")[2],
			ConsumerName: consumer,
		}

		requestBody, _ = json.Marshal(ackRequest)
		req, _ = http.NewRequest("POST", url+"/v1/ack", bytes.NewBuffer(requestBody))
		resp, err = call(req)
		if err != nil {
			panic(err)
		}
		assert.Equal(t, "200 OK", resp.Status)
	}

	// dequeue request again
	dequeueRequest = store.DequeueRequest{
		Topic:           topic,
		BatchSize:       10,
		ConsumerName:    producer,
		MaxWaitDuration: 10,
	}

	requestBody, _ = json.Marshal(dequeueRequest)
	req, _ = http.NewRequest("POST", url+"/v1/dequeue", bytes.NewBuffer(requestBody))
	resp, err = call(req)
	if err != nil {
		panic(err)
	}
	assert.Equal(t, "200 OK", resp.Status)

	buf = &bytes.Buffer{}
	buf.ReadFrom(resp.Body)

	data = buf.Bytes()

	json.Unmarshal(data, &dequeResponses)
	assert.Equal(t, 5, len(dequeResponses))
}

// helper method to generate Jwt Token
func GenerateJWTToken(jwtSecret string) (string, error) {

	// Valid from an hour ago
	issuedTime := jwt4.NewNumericDate(time.Now().Add(-time.Hour))

	// Expires in an hour from now
	expiryTime := jwt4.NewNumericDate(time.Now().Add(time.Hour))

	harnessClaims := harnessClaims{
		Type: "SERVICE",
		Name: "HSQS",
	}

	harnessClaims.Issuer = "Harness Inc"
	harnessClaims.IssuedAt = issuedTime
	harnessClaims.NotBefore = issuedTime
	harnessClaims.ExpiresAt = expiryTime

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, harnessClaims)
	signedJwt, err := token.SignedString([]byte(jwtSecret))
	if err != nil {
		return "", err
	}
	return signedJwt, nil
}

func call(req *http.Request) (*http.Response, error) {
	c, _ := config.Load()
	token, _ := GenerateJWTToken(c.Secret)
	client := &http.Client{}
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set("Content-Type", "application/json")
	return client.Do(req)
}
