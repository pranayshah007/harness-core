package handlers

import (
	"idp-service/configs"
	"idp-service/models"
	"idp-service/vo"
	"log"
	"net/http"
	"time"

	"github.com/go-playground/validator/v10"
	"github.com/labstack/echo/v4"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
	"go.mongodb.org/mongo-driver/mongo/options"
	"golang.org/x/net/context"

)

var appConfigCollection *mongo.Collection = configs.GetCollection(configs.DB,"harness", "appConfigs")
var validate = validator.New()

func AddAppConfigs(c echo.Context) error {
    ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    var appConfig models.AppConfig
    defer cancel()


    //validate the request body
    if err := c.Bind(&appConfig); err != nil {
        return c.JSON(http.StatusBadRequest, vo.AppConfigResponse{Status: http.StatusBadRequest, Message: "error", Data: &echo.Map{"data": err.Error()}})
    }

    //use the validator library to validate required fields
    if validationErr := validate.Struct(&appConfig); validationErr != nil {
        return c.JSON(http.StatusBadRequest, vo.AppConfigResponse{Status: http.StatusBadRequest, Message: "error", Data: &echo.Map{"data": validationErr.Error()}})
    }

    newAppConfig := models.AppConfig{
		AccountId: appConfig.AccountId,
		Content: appConfig.Content,
    }


    result, err := appConfigCollection.InsertOne(ctx, newAppConfig)
	// result, err := appConfigCollection.update(ctx, bson.M{"accountid": newAppConfig.accountId}, bson.M{"$set": newAppConfig})
    if err != nil {
        return c.JSON(http.StatusInternalServerError, vo.AppConfigResponse{Status: http.StatusInternalServerError, Message: "error", Data: &echo.Map{"data": err.Error()}})
    }
	log.Printf("Data pushed in DB successfully...")
	log.Printf("Result : ",result)
    return c.JSON(http.StatusCreated, vo.AppConfigResponse{Status: http.StatusCreated, Message: "success", Data: &echo.Map{"data": result}})
}


func GetAppConfigFromAccountID(c echo.Context) error {
    ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    // userId := c.Param("userId")
	accountId := c.Param("accountId")

    var appConfig models.AppConfig
    defer cancel()

    err := appConfigCollection.FindOne(ctx, bson.M{"accountid": accountId}).Decode(&appConfig)

    if err != nil {
        return c.JSON(http.StatusInternalServerError, vo.AppConfigResponse{Status: http.StatusInternalServerError, Message: "error", Data: &echo.Map{"data": err.Error()}})
    }

    return c.JSON(http.StatusOK, vo.AppConfigResponse{Status: http.StatusOK, Message: "success", Data: &echo.Map{"data": appConfig}})
}


func UpdateAppConfigYaml(c echo.Context) error {
    ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    accountId := c.Param("accountId")
    var appConfig models.AppConfig
    defer cancel()

    //validate the request body
    if err := c.Bind(&appConfig); err != nil {
        return c.JSON(http.StatusBadRequest, vo.AppConfigResponse{Status: http.StatusBadRequest, Message: "error", Data: &echo.Map{"data": err.Error()}})
    }

    //use the validator library to validate required fields
    if validationErr := validate.Struct(&appConfig); validationErr != nil {
        return c.JSON(http.StatusBadRequest, vo.AppConfigResponse{Status: http.StatusBadRequest, Message: "error", Data: &echo.Map{"data": validationErr.Error()}})
    }

	currentTime := time.Now()

	sha := GetSHAOfFile("wings-software", "harness-idp-test", "app-config.yaml", accountId, "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")
	log.Print("Sha of file to be updated : ",sha,"\n")
	UpdateFileInGithubRepo("wings-software", "harness-idp-test", "app-config.yaml", accountId, "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","test-commit-"+currentTime.String(), sha, "Devesh Mishra", "devesh.mishra@harness.io", appConfig.Content)

    update := bson.M{"accountid": accountId, "content": appConfig.Content}

    result, err := appConfigCollection.UpdateOne(ctx, bson.M{"accountid": accountId}, bson.M{"$set": update})

    if err != nil {
        return c.JSON(http.StatusInternalServerError, vo.AppConfigResponse{Status: http.StatusInternalServerError, Message: "error", Data: &echo.Map{"data": err.Error()}})
    }

    //get updated user details
    var updatedConfig models.AppConfig
    if result.MatchedCount == 1 {
        err := appConfigCollection.FindOne(ctx, bson.M{"accountid": accountId}).Decode(&updatedConfig)

        if err != nil {
            return c.JSON(http.StatusInternalServerError, vo.AppConfigResponse{Status: http.StatusInternalServerError, Message: "error", Data: &echo.Map{"data": err.Error()}})
        }
    }

    return c.JSON(http.StatusOK, vo.AppConfigResponse{Status: http.StatusOK, Message: "success", Data: &echo.Map{"data": updatedConfig}})
}


func DeleteAUser(c echo.Context) error {
    ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
    accountId := c.Param("accountId")
    defer cancel()


    result, err := appConfigCollection.DeleteOne(ctx, bson.M{"accountid": accountId})

    if err != nil {
        return c.JSON(http.StatusInternalServerError, vo.AppConfigResponse{Status: http.StatusInternalServerError, Message: "error", Data: &echo.Map{"data": err.Error()}})
    }

    if result.DeletedCount < 1 {
        return c.JSON(http.StatusNotFound, vo.AppConfigResponse{Status: http.StatusNotFound, Message: "error", Data: &echo.Map{"data": "User with specified ID not found!"}})
    }

    return c.JSON(http.StatusOK, vo.AppConfigResponse{Status: http.StatusOK, Message: "success", Data: &echo.Map{"data": "User successfully deleted!"}})
}


func CreateIndex( collection *mongo.Collection){
	ctx,_:= context.WithTimeout(context.Background(), 10*time.Second)
    indexName, err := collection.Indexes().CreateOne(ctx, mongo.IndexModel{
        Keys: bson.M{"accountid": 1},
		Options: options.Index().SetUnique(true),
    })
    if err != nil {
        log.Fatal(err)
    }
    log.Print(indexName)
}
