// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package mongodb

import (
	"context"
	"fmt"
	"strings"

	"github.com/harness/harness-core/product/log-service/db"

	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
	"go.mongodb.org/mongo-driver/mongo/readpref"
	"go.mongodb.org/mongo-driver/tag"
	"go.mongodb.org/mongo-driver/x/mongo/driver/connstring"

	"github.com/sirupsen/logrus"
)

type MongoDb struct {
	Client   *mongo.Client
	Database *mongo.Database

	SecondaryClient   *mongo.Client
	SecondaryDatabase *mongo.Database
}

func New(username, password, connStr string, enableSecondary bool) (*MongoDb, error) {
	dbName, _, _, err := getMongoDBServerInfo(connStr)
	if err != nil {
		return nil, err
	}

	logrus.Infof("Trying to connect to MongoDB")
	primaryClient, err := getMongoClient(username, password, connStr, true)
	if err != nil {
		return nil, err
	}
	logrus.Infof("Successfully pinged MongoDB Server")

	var secondaryClient *mongo.Client
	if enableSecondary {
		secondaryClient, err = getMongoClient(username, password, connStr, false)
		if err != nil {
			return nil, err
		}
		logrus.Infof("Successfully pinged MongoDB Analytics Server")
	}
	tidb := &MongoDb{
		Client:            primaryClient,
		Database:          primaryClient.Database(dbName),
		SecondaryClient:   secondaryClient,
		SecondaryDatabase: secondaryClient.Database(dbName),
	}
	return tidb, nil
}

func getMongoClient(username, password, connStr string, primary bool) (*mongo.Client, error) {
	ctx := context.Background()
	opts := options.Client().ApplyURI(connStr)
	if len(username) > 0 {
		credential := options.Credential{
			Username: username,
			Password: password,
		}
		opts = opts.SetAuth(credential)
	}

	rf := readpref.Primary()
	if !primary {
		// Tags to connect to Analytics node
		tagMap := map[string]string{"nodeType": "ANALYTICS"}
		tagSet := tag.NewTagSetFromMap(tagMap)
		tagOpts := readpref.WithTagSets(tagSet)

		// Set read preference as secondary
		rf = readpref.SecondaryPreferred(tagOpts)
		opts = opts.SetReadPreference(rf)
	}

	// Connect to MongoDB
	client, err := mongo.NewClient(opts)
	if err != nil {
		return nil, err
	}
	if err = client.Connect(ctx); err != nil {
		return nil, err
	}
	// Ping mongo server to see if it's accessible.
	err = client.Ping(ctx, rf)
	if err != nil {
		return nil, err
	}
	return client, nil
}

func getMongoDBServerInfo(connStr string) (dbName, host, port string, err error) {
	// Get DB name, host and port from connection string. Expected URI: "mongodb://localhost:27017/testDb?params..."
	mongoDBInfo, err := connstring.Parse(connStr)
	if err != nil {
		return dbName, host, port, err
	}

	dbName = mongoDBInfo.Database
	if dbName == "" {
		return dbName, host, port, fmt.Errorf("dbName cannot be empty")
	}

	if len(mongoDBInfo.Hosts) > 0 {
		hostAndPort := mongoDBInfo.Hosts[0]
		hostAndPortSplit := strings.Split(hostAndPort, ":")
		if len(hostAndPortSplit) == 2 {
			host = hostAndPortSplit[0]
			port = hostAndPortSplit[1]
		}
	}

	return dbName, host, port, nil
}

func (mdb *MongoDb) Insert(ctx context.Context, collection string, document interface{}) error {
	_, err := mdb.Database.Collection(collection).InsertOne(ctx, document)
	return err
}

func (mdb *MongoDb) FindOne(ctx context.Context, collection string, filter interface{},
	out interface{}) error {
	err := mdb.Database.Collection(collection).FindOne(ctx, filter).Decode(out)
	if err == mongo.ErrNoDocuments {
		return db.ErrNotFound
	}
	return err
}

func (mdb *MongoDb) ReplaceOne(ctx context.Context, collection string, filter interface{},
	document interface{}) error {
	_, err := mdb.Database.Collection(collection).ReplaceOne(ctx, filter, document)
	return err
}
