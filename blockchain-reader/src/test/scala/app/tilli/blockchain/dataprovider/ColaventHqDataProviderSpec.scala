package app.tilli.blockchain.dataprovider

import app.tilli.BaseSpec
import app.tilli.blockchain.dataprovider.ColaventHqDataProviderSpec.expectedResult
import app.tilli.serializer.KeyConverter

class ColaventHqDataProviderSpec extends BaseSpec {

  "ColaventHqDataProvider" must {

    "get next page" in {
      val Right(json) = io.circe.parser.parse(ColaventHqDataProviderSpec.apiResult)
      val next = ColaventHqDataProvider.getNextPageFromResult(json)
      next mustBe Some(1)
    }

    "decode result" in {
      val Right(json) = io.circe.parser.parse(ColaventHqDataProviderSpec.apiResult)
      val Right(expectedResultJson) = io.circe.parser.parse(expectedResult)

      val result = ColaventHqDataProvider.getTransactionEventsFromResult(json)

      result mustBe List(expectedResultJson)
    }

    "decode hex to int" in {
      val hexString = "0x00000000000000000000000000000000000000000000000000000000000004e8"
      val Right(result) = ColaventHqDataProvider.toIntegerStringFromHexString(hexString)
      result mustBe "1256"
    }

    "handle bad string" in {
      ColaventHqDataProvider.toIntegerStringFromHexString("xyz") mustBe a[Left[Throwable, Int]]
      ColaventHqDataProvider.toIntegerStringFromHexString(null) mustBe a[Left[Throwable, Int]]
    }

    "decode very large hex into int" in {
      val hexString = "0x8c7afa16a87e8f7d7dba934325ffa4086aeb5228e09dbf0ac74660e8f61e5c0c"
      val Right(result) = ColaventHqDataProvider.toIntegerStringFromHexString(hexString)
      result mustBe "63541080191010310552469611647640848047372637692655634840450549577336141798412"
    }

  }

}

object ColaventHqDataProviderSpec {

  val apiResult = KeyConverter.sc2cc(
    """
      |{
      |  "data": {
      |    "address": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |    "updated_at": "2022-06-18T22:47:52.145508105Z",
      |    "next_update_at": "2022-06-18T22:52:52.145508475Z",
      |    "quote_currency": "USD",
      |    "chain_id": 1,
      |    "items": [
      |      {
      |        "block_signed_at": "2022-06-09T15:37:45Z",
      |        "block_height": 14933373,
      |        "tx_hash": "0x139de7f5924f71869e44812048ac514d4c55c64d395d18619404911f434a10ae",
      |        "tx_offset": 39,
      |        "successful": true,
      |        "from_address": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |        "from_address_label": null,
      |        "to_address": "0x7f268357a8c2552623316e2562d90e642bb538e5",
      |        "to_address_label": "Wyvern Exchange Contract (-)",
      |        "value": "190000000000000000",
      |        "value_quote": 341.21170410156253,
      |        "gas_offered": 336829,
      |        "gas_spent": 244229,
      |        "gas_price": 67601952068,
      |        "fees_paid": "16510357151615572",
      |        "gas_quote": 29.650142626464042,
      |        "gas_quote_rate": 1795.85107421875,
      |        "log_events": [
      |          {
      |            "block_signed_at": "2022-06-09T15:37:45Z",
      |            "block_height": 14933373,
      |            "tx_offset": 39,
      |            "log_offset": 56,
      |            "tx_hash": "0x139de7f5924f71869e44812048ac514d4c55c64d395d18619404911f434a10ae",
      |            "raw_log_topics": [
      |              "0xc4109843e0b7d514e4c093114b863f8e7d8d9a458c372cd51bfe526b588006c9",
      |              "0x000000000000000000000000cfb098c1d44eb12f93f9aaece5d6054e2a2240ab",
      |              "0x000000000000000000000000becb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |              "0x0000000000000000000000000000000000000000000000000000000000000000"
      |            ],
      |            "sender_contract_decimals": 0,
      |            "sender_name": "Wyvern Exchange Contract",
      |            "sender_contract_ticker_symbol": null,
      |            "sender_address": "0x7f268357a8c2552623316e2562d90e642bb538e5",
      |            "sender_address_label": "Wyvern Exchange Contract (-)",
      |            "sender_logo_url": "https://logos.covalenthq.com/tokens/1/0x7f268357a8c2552623316e2562d90e642bb538e5.png",
      |            "raw_log_data": "0x000000000000000000000000000000000000000000000000000000000000000037975ab6fea9d75320c8f78607815dec2555de17c8b0a6d8acd6b8ae5add807b00000000000000000000000000000000000000000000000002a303fe4b530000",
      |            "decoded": {
      |              "name": "OrdersMatched",
      |              "signature": "OrdersMatched(bytes32 buyHash, bytes32 sellHash, indexed address maker, indexed address taker, uint256 price, indexed bytes32 metadata)",
      |              "params": [
      |                {
      |                  "name": "buyHash",
      |                  "type": "bytes32",
      |                  "indexed": false,
      |                  "decoded": true,
      |                  "value": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
      |                },
      |                {
      |                  "name": "sellHash",
      |                  "type": "bytes32",
      |                  "indexed": false,
      |                  "decoded": true,
      |                  "value": "N5datv6p11MgyPeGB4Fd7CVV3hfIsKbYrNa4rlrdgHs="
      |                },
      |                {
      |                  "name": "maker",
      |                  "type": "address",
      |                  "indexed": true,
      |                  "decoded": true,
      |                  "value": "0xcfb098c1d44eb12f93f9aaece5d6054e2a2240ab"
      |                },
      |                {
      |                  "name": "taker",
      |                  "type": "address",
      |                  "indexed": true,
      |                  "decoded": true,
      |                  "value": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e"
      |                },
      |                {
      |                  "name": "price",
      |                  "type": "uint256",
      |                  "indexed": false,
      |                  "decoded": true,
      |                  "value": "190000000000000000"
      |                },
      |                {
      |                  "name": "metadata",
      |                  "type": "bytes32",
      |                  "indexed": true,
      |                  "decoded": true,
      |                  "value": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
      |                }
      |              ]
      |            }
      |          },
      |          {
      |            "block_signed_at": "2022-06-09T15:37:45Z",
      |            "block_height": 14933373,
      |            "tx_offset": 39,
      |            "log_offset": 55,
      |            "tx_hash": "0x139de7f5924f71869e44812048ac514d4c55c64d395d18619404911f434a10ae",
      |            "raw_log_topics": [
      |              "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
      |              "0x000000000000000000000000cfb098c1d44eb12f93f9aaece5d6054e2a2240ab",
      |              "0x000000000000000000000000becb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |              "0x00000000000000000000000000000000000000000000000000000000000004e8"
      |            ],
      |            "sender_contract_decimals": 0,
      |            "sender_name": "Philosophical Foxes V2",
      |            "sender_contract_ticker_symbol": "FOX",
      |            "sender_address": "0x55256178afe74082c4f9afef7e40fec949c1b499",
      |            "sender_address_label": null,
      |            "sender_logo_url": "https://logos.covalenthq.com/tokens/1/0x55256178afe74082c4f9afef7e40fec949c1b499.png",
      |            "raw_log_data": null,
      |            "decoded": {
      |              "name": "Transfer",
      |              "signature": "Transfer(indexed address from, indexed address to, uint256 value)",
      |              "params": [
      |                {
      |                  "name": "from",
      |                  "type": "address",
      |                  "indexed": true,
      |                  "decoded": true,
      |                  "value": "0xcfb098c1d44eb12f93f9aaece5d6054e2a2240ab"
      |                },
      |                {
      |                  "name": "to",
      |                  "type": "address",
      |                  "indexed": true,
      |                  "decoded": true,
      |                  "value": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e"
      |                },
      |                {
      |                  "name": "value",
      |                  "type": "uint256",
      |                  "indexed": false,
      |                  "decoded": false,
      |                  "value": null
      |                }
      |              ]
      |            }
      |          },
      |          {
      |            "block_signed_at": "2022-06-09T15:37:45Z",
      |            "block_height": 14933373,
      |            "tx_offset": 39,
      |            "log_offset": 54,
      |            "tx_hash": "0x139de7f5924f71869e44812048ac514d4c55c64d395d18619404911f434a10ae",
      |            "raw_log_topics": [
      |              "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925",
      |              "0x000000000000000000000000cfb098c1d44eb12f93f9aaece5d6054e2a2240ab",
      |              "0x0000000000000000000000000000000000000000000000000000000000000000",
      |              "0x00000000000000000000000000000000000000000000000000000000000004e8"
      |            ],
      |            "sender_contract_decimals": 0,
      |            "sender_name": "Philosophical Foxes V2",
      |            "sender_contract_ticker_symbol": "FOX",
      |            "sender_address": "0x55256178afe74082c4f9afef7e40fec949c1b499",
      |            "sender_address_label": null,
      |            "sender_logo_url": "https://logos.covalenthq.com/tokens/1/0x55256178afe74082c4f9afef7e40fec949c1b499.png",
      |            "raw_log_data": null,
      |            "decoded": {
      |              "name": "Approval",
      |              "signature": "Approval(indexed address owner, indexed address spender, uint256 value)",
      |              "params": [
      |                {
      |                  "name": "owner",
      |                  "type": "address",
      |                  "indexed": true,
      |                  "decoded": true,
      |                  "value": "0xcfb098c1d44eb12f93f9aaece5d6054e2a2240ab"
      |                },
      |                {
      |                  "name": "spender",
      |                  "type": "address",
      |                  "indexed": true,
      |                  "decoded": true,
      |                  "value": "0x0000000000000000000000000000000000000000"
      |                },
      |                {
      |                  "name": "value",
      |                  "type": "uint256",
      |                  "indexed": false,
      |                  "decoded": false,
      |                  "value": null
      |                }
      |              ]
      |            }
      |          }
      |        ]
      |      }
      |    ],
      |    "pagination": {
      |      "has_more": true,
      |      "page_number": 0,
      |      "page_size": 100,
      |      "total_count": null
      |    }
      |  },
      |  "error": false,
      |  "error_message": null,
      |  "error_code": null
      |}
      |""".stripMargin)

  val expectedResult =
    """{
      |  "transactionHash" : "0x139de7f5924f71869e44812048ac514d4c55c64d395d18619404911f434a10ae",
      |  "transactionOffset" : 39,
      |  "chain" : "ethereum",
      |  "paymentTokenSymbol" : "eth",
      |  "paymentTokenDecimals" : 18,
      |  "totalPrice" : "190000000000000000",
      |  "quantity" : 1,
      |  "transactionTime" : 1654789065000,
      |  "blockHeight" : 14933373,
      |  "eventType" : "transfer",
      |  "logOffset" : 55,
      |  "fromAddress" : "0xcfb098c1d44eb12f93f9aaece5d6054e2a2240ab",
      |  "toAddress" : "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |  "assetContractAddress" : "0x55256178afe74082c4f9afef7e40fec949c1b499",
      |  "assetContractName" : "Philosophical Foxes V2",
      |  "assetContractSymbol" : "FOX",
      |  "tokenType" : null,
      |  "tokenId" : "1256"
      |}""".stripMargin
}