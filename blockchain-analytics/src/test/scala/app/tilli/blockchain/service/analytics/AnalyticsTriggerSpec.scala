package app.tilli.blockchain.service.analytics

import app.tilli.BaseSpec
import app.tilli.blockchain.service.analytics.AnalyticsTriggerSpec.debeziumEvent

class AnalyticsTriggerSpec extends BaseSpec {

  "AnalyticsTriger" must {

    "read transaction even from debezium" in {
      val Right(json) = io.circe.parser.parse(debeziumEvent)
      AnalyticsTrigger.getAddressFromDebeziumTransactionEvent(json) mustBe Right(List("0x62ac2dbbd306610ff8652b9e0d1a310b6c6afa0f", "0xc20a4a6fd0dce286afad550d2266965caf8cee5e"))
    }

  }

}


object AnalyticsTriggerSpec {

  val debeziumEvent =
    """{
      |  "schema" : {
      |    "type" : "struct",
      |    "fields" : [ {
      |      "type" : "string",
      |      "optional" : true,
      |      "name" : "io.debezium.data.Json",
      |      "version" : 1,
      |      "field" : "after"
      |    }, {
      |      "type" : "string",
      |      "optional" : true,
      |      "name" : "io.debezium.data.Json",
      |      "version" : 1,
      |      "field" : "patch"
      |    }, {
      |      "type" : "string",
      |      "optional" : true,
      |      "name" : "io.debezium.data.Json",
      |      "version" : 1,
      |      "field" : "filter"
      |    }, {
      |      "type" : "struct",
      |      "fields" : [ {
      |        "type" : "array",
      |        "items" : {
      |          "type" : "string",
      |          "optional" : false
      |        },
      |        "optional" : true,
      |        "field" : "removedFields"
      |      }, {
      |        "type" : "string",
      |        "optional" : true,
      |        "name" : "io.debezium.data.Json",
      |        "version" : 1,
      |        "field" : "updatedFields"
      |      }, {
      |        "type" : "array",
      |        "items" : {
      |          "type" : "struct",
      |          "fields" : [ {
      |            "type" : "string",
      |            "optional" : false,
      |            "field" : "field"
      |          }, {
      |            "type" : "int32",
      |            "optional" : false,
      |            "field" : "size"
      |          } ],
      |          "optional" : false,
      |          "name" : "io.debezium.connector.mongodb.changestream.truncatedarray"
      |        },
      |        "optional" : true,
      |        "field" : "truncatedArrays"
      |      } ],
      |      "optional" : true,
      |      "name" : "io.debezium.connector.mongodb.changestream.updatedescription",
      |      "field" : "updateDescription"
      |    }, {
      |      "type" : "struct",
      |      "fields" : [ {
      |        "type" : "string",
      |        "optional" : false,
      |        "field" : "version"
      |      }, {
      |        "type" : "string",
      |        "optional" : false,
      |        "field" : "connector"
      |      }, {
      |        "type" : "string",
      |        "optional" : false,
      |        "field" : "name"
      |      }, {
      |        "type" : "int64",
      |        "optional" : false,
      |        "field" : "ts_ms"
      |      }, {
      |        "type" : "string",
      |        "optional" : true,
      |        "name" : "io.debezium.data.Enum",
      |        "version" : 1,
      |        "parameters" : {
      |          "allowed" : "true,last,false,incremental"
      |        },
      |        "default" : "false",
      |        "field" : "snapshot"
      |      }, {
      |        "type" : "string",
      |        "optional" : false,
      |        "field" : "db"
      |      }, {
      |        "type" : "string",
      |        "optional" : true,
      |        "field" : "sequence"
      |      }, {
      |        "type" : "string",
      |        "optional" : false,
      |        "field" : "rs"
      |      }, {
      |        "type" : "string",
      |        "optional" : false,
      |        "field" : "collection"
      |      }, {
      |        "type" : "int32",
      |        "optional" : false,
      |        "field" : "ord"
      |      }, {
      |        "type" : "int64",
      |        "optional" : true,
      |        "field" : "h"
      |      }, {
      |        "type" : "int64",
      |        "optional" : true,
      |        "field" : "tord"
      |      }, {
      |        "type" : "string",
      |        "optional" : true,
      |        "field" : "stxnid"
      |      }, {
      |        "type" : "string",
      |        "optional" : true,
      |        "field" : "lsid"
      |      }, {
      |        "type" : "int64",
      |        "optional" : true,
      |        "field" : "txnNumber"
      |      } ],
      |      "optional" : false,
      |      "name" : "io.debezium.connector.mongo.Source",
      |      "field" : "source"
      |    }, {
      |      "type" : "string",
      |      "optional" : true,
      |      "field" : "op"
      |    }, {
      |      "type" : "int64",
      |      "optional" : true,
      |      "field" : "ts_ms"
      |    }, {
      |      "type" : "struct",
      |      "fields" : [ {
      |        "type" : "string",
      |        "optional" : false,
      |        "field" : "id"
      |      }, {
      |        "type" : "int64",
      |        "optional" : false,
      |        "field" : "total_order"
      |      }, {
      |        "type" : "int64",
      |        "optional" : false,
      |        "field" : "data_collection_order"
      |      } ],
      |      "optional" : true,
      |      "field" : "transaction"
      |    } ],
      |    "optional" : false,
      |    "name" : "tilli_mongodb.tilli.transaction.Envelope"
      |  },
      |  "payload" : {
      |    "after" : "{\"_id\": {\"$oid\": \"62dc8cbe7720e84521694af0\"},\"key\": \"0x27a1c6fcdb26011e903998f90e6eeb45fd9f654c0e2a499b1685cfc31ba13980-92-2235\",\"data\": {\"transactionHash\": \"0x27a1c6fcdb26011e903998f90e6eeb45fd9f654c0e2a499b1685cfc31ba13980\",\"transactionOffset\": 92,\"chain\": \"ethereum\",\"paymentTokenSymbol\": \"eth\",\"paymentTokenDecimals\": 18,\"totalPrice\": \"0\",\"quantity\": 1,\"transactionTime\": {\"$date\": 1653908194000},\"eventType\": \"transfer\",\"logOffset\": 2235,\"fromAddress\": \"0x62ac2dbbd306610ff8652b9e0d1a310b6c6afa0f\",\"toAddress\": \"0xc20a4a6fd0dce286afad550d2266965caf8cee5e\",\"assetContractAddress\": \"0xea4d123d17b7ab200533388521ae005bfefd8e26\",\"assetContractName\": \"Dusk Topia\",\"assetContractSymbol\": \"DT\",\"tokenType\": null,\"tokenId\": \"2155\",\"createdAt\": null},\"createdAt\": {\"$date\": 1658621118515}}",
      |    "patch" : null,
      |    "filter" : null,
      |    "updateDescription" : null,
      |    "source" : {
      |      "version" : "1.9.5.Final",
      |      "connector" : "mongodb",
      |      "name" : "tilli-mongodb",
      |      "ts_ms" : 1658621118000,
      |      "snapshot" : "false",
      |      "db" : "tilli",
      |      "sequence" : null,
      |      "rs" : "atlas-pnu3kw-shard-0",
      |      "collection" : "transaction",
      |      "ord" : 48,
      |      "h" : null,
      |      "tord" : null,
      |      "stxnid" : null,
      |      "lsid" : null,
      |      "txnNumber" : null
      |    },
      |    "op" : "c",
      |    "ts_ms" : 1658621118536,
      |    "transaction" : null
      |  }
      |}""".stripMargin

}