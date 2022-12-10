// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package utils

import "fmt"

// GetAllSubTopicsFromTopicKey key, all subtopics list is stored having actual stream redis key
func GetAllSubTopicsFromTopicKey(requestTopicName string) string {
	return fmt.Sprintf("hsqs:%s:subtopics", requestTopicName)
}

// GetSubTopicStreamQueueKey key is the actual redis stream key for a topic + subtopic
func GetSubTopicStreamQueueKey(topic, subTopic string) string {
	return fmt.Sprintf("hsqs:%s:%s:queue", topic, subTopic)
}

// GetTopicMetadataKey key, metadata for the topic is stored which is given in register topic
func GetTopicMetadataKey(topic string) string {
	return fmt.Sprintf("hsqs:%s:metadata", topic)
}

// GetConsumerGroupKeyForTopic key is the fixed consumerGroup name for a given topic
func GetConsumerGroupKeyForTopic(topic string) string {
	return fmt.Sprintf("hsqs:%s:consumerGroup", topic)
}

func GetAllBlockedSubTopicsFromTopicKey(topic string, subTopic string) string {
	return fmt.Sprintf("hsqs:%s:%s:blocked", topic, subTopic)
}
