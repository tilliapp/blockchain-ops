package app.tilli.blockchain.dataprovider

import app.tilli.BaseSpec
import app.tilli.blockchain.dataprovider.OpenSeaApiDataProviderSpec._
import app.tilli.serializer.KeyConverter

import java.time.Instant

class OpenSeaApiDataProviderSpec extends BaseSpec {

  "OpenSeaApi" must {

    "decode events" in {
      val Right(json) = io.circe.parser.parse(OpenSeaApiDataProviderSpec.apiResult)
      val results = OpenSeaApiDataProvider.assetContractEventsFromResult(json)

      val Right(expectedResultJson) = io.circe.parser.parse(expectedResult)
      val Right(expectedResult2Json) = io.circe.parser.parse(expectedResult2)

      results mustBe List(
        expectedResultJson,
        expectedResult2Json
      )
    }

    "decode asset contract with missing fields" in {
      val time = Instant.now
      val Right(json) = io.circe.parser.parse(mfersAssetContract)
      val jsonConvertedKeys = KeyConverter.snakeCaseToCamelCaseJson(json)
      val processed = OpenSeaApiDataProvider.decodeAssetContract(jsonConvertedKeys, time)
      val Right(expected) = io.circe.parser.parse(mfersAssetContractExpected(time))
      processed mustBe expected
    }
  }
}


object OpenSeaApiDataProviderSpec {

  val mfersAssetContract =
    """{
      |    "collection": {
      |        "banner_image_url": "https://lh3.googleusercontent.com/vqvdty0tk-RKv8dPkKnAiLxFxhRfMdt-K7iwp1RJh13yBKPS3iUmqAY29xdJlbjXk_AqXu81FfC6AWg79UV0SQqifBxEXp2rdhox9CQ=s2500",
      |        "chat_url": null,
      |        "created_date": "2021-11-30T04:55:51.097880",
      |        "default_to_fiat": false,
      |        "description": "mfers are generated entirely from hand drawings by sartoshi. this project is in the public domain; feel free to use mfers any way you want.\n\nunofficial mfers discord is [here](https://t.co/k18FPgnBy7)\n\nbackstory on mfers is [here](https://mirror.xyz/sartoshi.eth/QukjtL1076-1SEoNJuqyc-x4Ut2v8_TocKkszo-S_nU)",
      |        "dev_buyer_fee_basis_points": "0",
      |        "dev_seller_fee_basis_points": "500",
      |        "discord_url": null,
      |        "display_data": {
      |            "card_display_style": "cover"
      |        },
      |        "external_url": null,
      |        "featured": false,
      |        "featured_image_url": "https://lh3.googleusercontent.com/J2iIgy5_gmA8IS6sXGKGZeFVZwhldQylk7w7fLepTE9S7ICPCn_dlo8kypX8Ju0N6wvLVOKsbP_7bNGd8cpKmWhFQmqMXOC8q2sOdqw=s300",
      |        "hidden": false,
      |        "safelist_request_status": "verified",
      |        "image_url": "https://lh3.googleusercontent.com/J2iIgy5_gmA8IS6sXGKGZeFVZwhldQylk7w7fLepTE9S7ICPCn_dlo8kypX8Ju0N6wvLVOKsbP_7bNGd8cpKmWhFQmqMXOC8q2sOdqw=s120",
      |        "is_subject_to_whitelist": false,
      |        "large_image_url": "https://lh3.googleusercontent.com/J2iIgy5_gmA8IS6sXGKGZeFVZwhldQylk7w7fLepTE9S7ICPCn_dlo8kypX8Ju0N6wvLVOKsbP_7bNGd8cpKmWhFQmqMXOC8q2sOdqw=s300",
      |        "medium_username": null,
      |        "name": "mfers",
      |        "only_proxied_transfers": false,
      |        "opensea_buyer_fee_basis_points": "0",
      |        "opensea_seller_fee_basis_points": "250",
      |        "payout_address": "0x2c47540d6f4589a974e651f13a27dd9a62f30b89",
      |        "require_email": false,
      |        "short_description": null,
      |        "slug": "mfers",
      |        "telegram_url": null,
      |        "twitter_username": null,
      |        "instagram_username": null,
      |        "wiki_url": null,
      |        "is_nsfw": false
      |    },
      |    "address": "0x79fcdef22feed20eddacbb2587640e45491b757f",
      |    "asset_contract_type": "non-fungible",
      |    "created_date": "2021-11-29T22:21:12.644606",
      |    "name": "mfer",
      |    "nft_version": "3.0",
      |    "opensea_version": null,
      |    "owner": 240860552,
      |    "schema_name": "ERC721",
      |    "symbol": "MFER",
      |    "total_supply": "0",
      |    "description": "mfers are generated entirely from hand drawings by sartoshi. this project is in the public domain; feel free to use mfers any way you want.\n\nunofficial mfers discord is [here](https://t.co/k18FPgnBy7)\n\nbackstory on mfers is [here](https://mirror.xyz/sartoshi.eth/QukjtL1076-1SEoNJuqyc-x4Ut2v8_TocKkszo-S_nU)",
      |    "external_link": null,
      |    "image_url": "https://lh3.googleusercontent.com/J2iIgy5_gmA8IS6sXGKGZeFVZwhldQylk7w7fLepTE9S7ICPCn_dlo8kypX8Ju0N6wvLVOKsbP_7bNGd8cpKmWhFQmqMXOC8q2sOdqw=s120",
      |    "default_to_fiat": false,
      |    "dev_buyer_fee_basis_points": 0,
      |    "dev_seller_fee_basis_points": 500,
      |    "only_proxied_transfers": false,
      |    "opensea_buyer_fee_basis_points": 0,
      |    "opensea_seller_fee_basis_points": 250,
      |    "buyer_fee_basis_points": 0,
      |    "seller_fee_basis_points": 750,
      |    "payout_address": "0x2c47540d6f4589a974e651f13a27dd9a62f30b89"
      |}""".stripMargin

  def mfersAssetContractExpected(time:Instant) =
    s"""{
      |  "address" : "0x79fcdef22feed20eddacbb2587640e45491b757f",
      |  "openSeaSlug" : "mfers",
      |  "url" : null,
      |  "name" : "mfers",
      |  "created" : "2021-11-29T22:21:12.644606",
      |  "type" : "non-fungible",
      |  "schema" : "ERC721",
      |  "symbol" : "MFER",
      |  "sourced" : "${time.toString}"
      |}""".stripMargin

  val transfer =
    """
      |{
      |    "approved_account": null,
      |    "asset": {
      |        "animation_original_url": null,
      |        "animation_url": null,
      |        "asset_contract": {
      |            "address": "0x55256178afe74082c4f9afef7e40fec949c1b499",
      |            "asset_contract_type": "non-fungible",
      |            "buyer_fee_basis_points": 0,
      |            "created_date": "2022-01-31T15:34:32.113222",
      |            "default_to_fiat": false,
      |            "description": "#Philosophical Foxes are pixels with inner lives 💭🦊\r\n\r\nThey're much more than a profile picture - they're NFTs that think, wonder, hide, and feel. They're real characters, just like those in the pages of iconic books or in our favorite shows. \r\n\r\n💡 *How do we do that?* 💡 By giving Foxes rich traits that go far beyond the physical. Every one of our NFTs has a thought, philosophy, virtues and baggage attached to them. Some even have secrets! 🤫 🔐\r\n\r\n💭 *What are the thought bubbles?* 💭 These are \"solo thoughts.\" They have philosophies, virtues, baggage, and secrets of their own. By collecting them, you add more dimension to your Fox. We also ave some super secret plans about how else solo thoughts will be used. But we can't spoil the fun just yet 😉 🦊 🤫\r\n\r\nClick into each Fox or thought to explore their unique characteristics!",
      |            "dev_buyer_fee_basis_points": 0,
      |            "dev_seller_fee_basis_points": 500,
      |            "external_link": "http://www.philosophicalfoxes.com",
      |            "image_url": "https://lh3.googleusercontent.com/8JzLJbmg9U8XBigqC6YIzCNAbpEz9DNuknml0P9K1IeyrFf5K9JNNd083WAlUjpRS16OsyVjOFRY3vwLdUCOKvKxGWJsmy9USrWHwg=s120",
      |            "name": "Philosophical Foxes V2",
      |            "nft_version": "3.0",
      |            "only_proxied_transfers": false,
      |            "opensea_buyer_fee_basis_points": 0,
      |            "opensea_seller_fee_basis_points": 250,
      |            "opensea_version": null,
      |            "owner": 93579176,
      |            "payout_address": "0x68bb9fdd68c692def11f1351c73ee1af798540d4",
      |            "schema_name": "ERC721",
      |            "seller_fee_basis_points": 750,
      |            "symbol": "FOX",
      |            "total_supply": "0"
      |        },
      |        "background_color": null,
      |        "collection": {
      |            "banner_image_url": "https://lh3.googleusercontent.com/GdKsN7qTeH8Br-cyKL0QU0ybPYf1xQ_FNodnPgz-bWxlWFdxIxxU4Z9vXfkxQr6_v7rPUQNEuqe5HtJYS4kGIiUIzbIw1TQ7Zax8sFg=s2500",
      |            "chat_url": null,
      |            "created_date": "2021-10-11T13:06:51.272783",
      |            "default_to_fiat": false,
      |            "description": "#Philosophical Foxes are pixels with inner lives 💭🦊\r\n\r\nThey're much more than a profile picture - they're NFTs that think, wonder, hide, and feel. They're real characters, just like those in the pages of iconic books or in our favorite shows. \r\n\r\n💡 *How do we do that?* 💡 By giving Foxes rich traits that go far beyond the physical. Every one of our NFTs has a thought, philosophy, virtues and baggage attached to them. Some even have secrets! 🤫 🔐\r\n\r\n💭 *What are the thought bubbles?* 💭 These are \"solo thoughts.\" They have philosophies, virtues, baggage, and secrets of their own. By collecting them, you add more dimension to your Fox. We also ave some super secret plans about how else solo thoughts will be used. But we can't spoil the fun just yet 😉 🦊 🤫\r\n\r\nClick into each Fox or thought to explore their unique characteristics!",
      |            "dev_buyer_fee_basis_points": "0",
      |            "dev_seller_fee_basis_points": "500",
      |            "discord_url": "https://discord.gg/philosophical-foxes",
      |            "display_data": {
      |                "card_display_style": "cover"
      |            },
      |            "external_url": "http://www.philosophicalfoxes.com",
      |            "featured": false,
      |            "featured_image_url": "https://lh3.googleusercontent.com/NpBvIAbx5fik_unzcVX2drAqCrZVtaoumoWumo_ZOysOJ5Dq_riizqJjTZ_cTxbc3VAbvZyXpc4kOVDKtkqpAoCHlsawFrmet_DL0j8=s300",
      |            "hidden": false,
      |            "image_url": "https://lh3.googleusercontent.com/8JzLJbmg9U8XBigqC6YIzCNAbpEz9DNuknml0P9K1IeyrFf5K9JNNd083WAlUjpRS16OsyVjOFRY3vwLdUCOKvKxGWJsmy9USrWHwg=s120",
      |            "instagram_username": null,
      |            "is_nsfw": false,
      |            "is_subject_to_whitelist": false,
      |            "large_image_url": "https://lh3.googleusercontent.com/NpBvIAbx5fik_unzcVX2drAqCrZVtaoumoWumo_ZOysOJ5Dq_riizqJjTZ_cTxbc3VAbvZyXpc4kOVDKtkqpAoCHlsawFrmet_DL0j8=s300",
      |            "medium_username": null,
      |            "name": "Philosophical Foxes",
      |            "only_proxied_transfers": false,
      |            "opensea_buyer_fee_basis_points": "0",
      |            "opensea_seller_fee_basis_points": "250",
      |            "payout_address": "0x68bb9fdd68c692def11f1351c73ee1af798540d4",
      |            "require_email": false,
      |            "safelist_request_status": "verified",
      |            "short_description": null,
      |            "slug": "philosophicalfoxes",
      |            "telegram_url": null,
      |            "twitter_username": "FoxesNFT",
      |            "wiki_url": null
      |        },
      |        "decimals": 0,
      |        "description": "Fox #1256",
      |        "external_link": null,
      |        "id": 258784686,
      |        "image_original_url": "ipfs://QmRRHxPfqjRvswqquE4SsB8gaA2zWRwgaUK8w5MkBmx1Xj/1256.png",
      |        "image_preview_url": "https://lh3.googleusercontent.com/xNh55bF1RPs6KD7Acx8f9sG-CL5BspmPb80NHjny5vbTE1I5Dgd9FMmGH5GP6MRMLxm-Gb_xi9nsGQuI0-ruZVzhyPSIDbzAg8pp=s250",
      |        "image_thumbnail_url": "https://lh3.googleusercontent.com/xNh55bF1RPs6KD7Acx8f9sG-CL5BspmPb80NHjny5vbTE1I5Dgd9FMmGH5GP6MRMLxm-Gb_xi9nsGQuI0-ruZVzhyPSIDbzAg8pp=s128",
      |        "image_url": "https://lh3.googleusercontent.com/xNh55bF1RPs6KD7Acx8f9sG-CL5BspmPb80NHjny5vbTE1I5Dgd9FMmGH5GP6MRMLxm-Gb_xi9nsGQuI0-ruZVzhyPSIDbzAg8pp",
      |        "is_nsfw": false,
      |        "name": "Don't move.",
      |        "num_sales": 2,
      |        "owner": {
      |            "address": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |            "config": "",
      |            "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/25.png",
      |            "user": {
      |                "username": null
      |            }
      |        },
      |        "permalink": "https://opensea.io/assets/ethereum/0x55256178afe74082c4f9afef7e40fec949c1b499/1256",
      |        "token_id": "1256",
      |        "token_metadata": "https://opensea.mypinata.cloud/ipfs/QmTM2banPba5x8scA9V6n7KgCbqjjAuarHDXuSboPPWHGh/metadata/1256.json"
      |    },
      |    "asset_bundle": null,
      |    "auction_type": null,
      |    "bid_amount": null,
      |    "collection_slug": "philosophicalfoxes",
      |    "contract_address": "0x55256178afe74082c4f9afef7e40fec949c1b499",
      |    "created_date": "2022-06-09T15:37:50.781297",
      |    "custom_event_name": null,
      |    "dev_fee_payment_event": null,
      |    "dev_seller_fee_basis_points": null,
      |    "duration": null,
      |    "ending_price": null,
      |    "event_timestamp": "2022-06-09T15:37:45",
      |    "event_type": "transfer",
      |    "from_account": {
      |        "address": "0xcfb098c1d44eb12f93f9aaece5d6054e2a2240ab",
      |        "config": "",
      |        "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/2.png",
      |        "user": {
      |            "username": null
      |        }
      |    },
      |    "id": 6771178380,
      |    "is_private": null,
      |    "listing_time": null,
      |    "owner_account": null,
      |    "payment_token": null,
      |    "quantity": "1",
      |    "seller": null,
      |    "starting_price": null,
      |    "to_account": {
      |        "address": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |        "config": "",
      |        "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/25.png",
      |        "user": {
      |            "username": null
      |        }
      |    },
      |    "total_price": null,
      |    "transaction": {
      |        "block_hash": "0x2d7dce11ae752f83f47b8a3706fc35b56e2b03a0e121478e216d2bc81d9b2dfe",
      |        "block_number": "14933373",
      |        "from_account": {
      |            "address": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |            "config": "",
      |            "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/25.png",
      |            "user": {
      |                "username": null
      |            }
      |        },
      |        "id": 391848436,
      |        "timestamp": "2022-06-09T15:37:45",
      |        "to_account": {
      |            "address": "0x7f268357a8c2552623316e2562d90e642bb538e5",
      |            "config": "",
      |            "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/28.png",
      |            "user": null
      |        },
      |        "transaction_hash": "0x139de7f5924f71869e44812048ac514d4c55c64d395d18619404911f434a10ae",
      |        "transaction_index": "39"
      |    },
      |    "winner_account": null
      |}""".stripMargin

  val sale =
    """
      |{
      |    "approved_account": null,
      |    "asset": {
      |        "animation_original_url": null,
      |        "animation_url": null,
      |        "asset_contract": {
      |            "address": "0x55256178afe74082c4f9afef7e40fec949c1b499",
      |            "asset_contract_type": "non-fungible",
      |            "buyer_fee_basis_points": 0,
      |            "created_date": "2022-01-31T15:34:32.113222",
      |            "default_to_fiat": false,
      |            "description": "#Philosophical Foxes are pixels with inner lives 💭🦊\r\n\r\nThey're much more than a profile picture - they're NFTs that think, wonder, hide, and feel. They're real characters, just like those in the pages of iconic books or in our favorite shows. \r\n\r\n💡 *How do we do that?* 💡 By giving Foxes rich traits that go far beyond the physical. Every one of our NFTs has a thought, philosophy, virtues and baggage attached to them. Some even have secrets! 🤫 🔐\r\n\r\n💭 *What are the thought bubbles?* 💭 These are \"solo thoughts.\" They have philosophies, virtues, baggage, and secrets of their own. By collecting them, you add more dimension to your Fox. We also ave some super secret plans about how else solo thoughts will be used. But we can't spoil the fun just yet 😉 🦊 🤫\r\n\r\nClick into each Fox or thought to explore their unique characteristics!",
      |            "dev_buyer_fee_basis_points": 0,
      |            "dev_seller_fee_basis_points": 500,
      |            "external_link": "http://www.philosophicalfoxes.com",
      |            "image_url": "https://lh3.googleusercontent.com/8JzLJbmg9U8XBigqC6YIzCNAbpEz9DNuknml0P9K1IeyrFf5K9JNNd083WAlUjpRS16OsyVjOFRY3vwLdUCOKvKxGWJsmy9USrWHwg=s120",
      |            "name": "Philosophical Foxes V2",
      |            "nft_version": "3.0",
      |            "only_proxied_transfers": false,
      |            "opensea_buyer_fee_basis_points": 0,
      |            "opensea_seller_fee_basis_points": 250,
      |            "opensea_version": null,
      |            "owner": 93579176,
      |            "payout_address": "0x68bb9fdd68c692def11f1351c73ee1af798540d4",
      |            "schema_name": "ERC721",
      |            "seller_fee_basis_points": 750,
      |            "symbol": "FOX",
      |            "total_supply": "0"
      |        },
      |        "background_color": null,
      |        "collection": {
      |            "banner_image_url": "https://lh3.googleusercontent.com/GdKsN7qTeH8Br-cyKL0QU0ybPYf1xQ_FNodnPgz-bWxlWFdxIxxU4Z9vXfkxQr6_v7rPUQNEuqe5HtJYS4kGIiUIzbIw1TQ7Zax8sFg=s2500",
      |            "chat_url": null,
      |            "created_date": "2021-10-11T13:06:51.272783",
      |            "default_to_fiat": false,
      |            "description": "#Philosophical Foxes are pixels with inner lives 💭🦊\r\n\r\nThey're much more than a profile picture - they're NFTs that think, wonder, hide, and feel. They're real characters, just like those in the pages of iconic books or in our favorite shows. \r\n\r\n💡 *How do we do that?* 💡 By giving Foxes rich traits that go far beyond the physical. Every one of our NFTs has a thought, philosophy, virtues and baggage attached to them. Some even have secrets! 🤫 🔐\r\n\r\n💭 *What are the thought bubbles?* 💭 These are \"solo thoughts.\" They have philosophies, virtues, baggage, and secrets of their own. By collecting them, you add more dimension to your Fox. We also ave some super secret plans about how else solo thoughts will be used. But we can't spoil the fun just yet 😉 🦊 🤫\r\n\r\nClick into each Fox or thought to explore their unique characteristics!",
      |            "dev_buyer_fee_basis_points": "0",
      |            "dev_seller_fee_basis_points": "500",
      |            "discord_url": "https://discord.gg/philosophical-foxes",
      |            "display_data": {
      |                "card_display_style": "cover"
      |            },
      |            "external_url": "http://www.philosophicalfoxes.com",
      |            "featured": false,
      |            "featured_image_url": "https://lh3.googleusercontent.com/NpBvIAbx5fik_unzcVX2drAqCrZVtaoumoWumo_ZOysOJ5Dq_riizqJjTZ_cTxbc3VAbvZyXpc4kOVDKtkqpAoCHlsawFrmet_DL0j8=s300",
      |            "hidden": false,
      |            "image_url": "https://lh3.googleusercontent.com/8JzLJbmg9U8XBigqC6YIzCNAbpEz9DNuknml0P9K1IeyrFf5K9JNNd083WAlUjpRS16OsyVjOFRY3vwLdUCOKvKxGWJsmy9USrWHwg=s120",
      |            "instagram_username": null,
      |            "is_nsfw": false,
      |            "is_subject_to_whitelist": false,
      |            "large_image_url": "https://lh3.googleusercontent.com/NpBvIAbx5fik_unzcVX2drAqCrZVtaoumoWumo_ZOysOJ5Dq_riizqJjTZ_cTxbc3VAbvZyXpc4kOVDKtkqpAoCHlsawFrmet_DL0j8=s300",
      |            "medium_username": null,
      |            "name": "Philosophical Foxes",
      |            "only_proxied_transfers": false,
      |            "opensea_buyer_fee_basis_points": "0",
      |            "opensea_seller_fee_basis_points": "250",
      |            "payout_address": "0x68bb9fdd68c692def11f1351c73ee1af798540d4",
      |            "require_email": false,
      |            "safelist_request_status": "verified",
      |            "short_description": null,
      |            "slug": "philosophicalfoxes",
      |            "telegram_url": null,
      |            "twitter_username": "FoxesNFT",
      |            "wiki_url": null
      |        },
      |        "decimals": 0,
      |        "description": "Fox #1256",
      |        "external_link": null,
      |        "id": 258784686,
      |        "image_original_url": "ipfs://QmRRHxPfqjRvswqquE4SsB8gaA2zWRwgaUK8w5MkBmx1Xj/1256.png",
      |        "image_preview_url": "https://lh3.googleusercontent.com/xNh55bF1RPs6KD7Acx8f9sG-CL5BspmPb80NHjny5vbTE1I5Dgd9FMmGH5GP6MRMLxm-Gb_xi9nsGQuI0-ruZVzhyPSIDbzAg8pp=s250",
      |        "image_thumbnail_url": "https://lh3.googleusercontent.com/xNh55bF1RPs6KD7Acx8f9sG-CL5BspmPb80NHjny5vbTE1I5Dgd9FMmGH5GP6MRMLxm-Gb_xi9nsGQuI0-ruZVzhyPSIDbzAg8pp=s128",
      |        "image_url": "https://lh3.googleusercontent.com/xNh55bF1RPs6KD7Acx8f9sG-CL5BspmPb80NHjny5vbTE1I5Dgd9FMmGH5GP6MRMLxm-Gb_xi9nsGQuI0-ruZVzhyPSIDbzAg8pp",
      |        "is_nsfw": false,
      |        "name": "Don't move.",
      |        "num_sales": 2,
      |        "owner": {
      |            "address": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |            "config": "",
      |            "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/25.png",
      |            "user": {
      |                "username": null
      |            }
      |        },
      |        "permalink": "https://opensea.io/assets/ethereum/0x55256178afe74082c4f9afef7e40fec949c1b499/1256",
      |        "token_id": "1256",
      |        "token_metadata": "https://opensea.mypinata.cloud/ipfs/QmTM2banPba5x8scA9V6n7KgCbqjjAuarHDXuSboPPWHGh/metadata/1256.json"
      |    },
      |    "asset_bundle": null,
      |    "auction_type": null,
      |    "bid_amount": null,
      |    "collection_slug": "philosophicalfoxes",
      |    "contract_address": "0x7f268357a8c2552623316e2562d90e642bb538e5",
      |    "created_date": "2022-06-09T15:37:50.898279",
      |    "custom_event_name": null,
      |    "dev_fee_payment_event": {
      |        "asset": null,
      |        "asset_bundle": null,
      |        "auction_type": null,
      |        "created_date": "2022-06-17T17:22:57.132798",
      |        "event_timestamp": "2022-06-17T17:22:41",
      |        "event_type": "payout",
      |        "payment_token": {
      |            "address": "0x0000000000000000000000000000000000000000",
      |            "decimals": 18,
      |            "eth_price": "1.000000000000000",
      |            "image_url": "https://openseauserdata.com/files/6f8e2979d428180222796ff4a33ab929.svg",
      |            "name": "Ether",
      |            "symbol": "ETH",
      |            "usd_price": "1097.799999999999955000"
      |        },
      |        "quantity": null,
      |        "total_price": null,
      |        "transaction": {
      |            "block_hash": "0x56a94154c89693c6981da49ca16de1ec42c56195c7919395da4c120d1ec5810f",
      |            "block_number": "14980379",
      |            "from_account": null,
      |            "id": 399758648,
      |            "timestamp": null,
      |            "to_account": null,
      |            "transaction_hash": "0x00518c8ee31af1b51fb83182ecb062ffcde5ed4f7931b39eb21e7a88c10d3cbd",
      |            "transaction_index": "19"
      |        }
      |    },
      |    "dev_seller_fee_basis_points": 500,
      |    "duration": null,
      |    "ending_price": null,
      |    "event_timestamp": "2022-06-09T15:37:45",
      |    "event_type": "successful",
      |    "from_account": null,
      |    "id": 6771178459,
      |    "is_private": false,
      |    "listing_time": "2022-06-08T12:52:24",
      |    "owner_account": null,
      |    "payment_token": {
      |        "address": "0x0000000000000000000000000000000000000000",
      |        "decimals": 18,
      |        "eth_price": "1.000000000000000",
      |        "image_url": "https://openseauserdata.com/files/6f8e2979d428180222796ff4a33ab929.svg",
      |        "name": "Ether",
      |        "symbol": "ETH",
      |        "usd_price": "1097.799999999999955000"
      |    },
      |    "quantity": "1",
      |    "seller": {
      |        "address": "0xcfb098c1d44eb12f93f9aaece5d6054e2a2240ab",
      |        "config": "",
      |        "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/2.png",
      |        "user": {
      |            "username": null
      |        }
      |    },
      |    "starting_price": null,
      |    "to_account": null,
      |    "total_price": "190000000000000000",
      |    "transaction": {
      |        "block_hash": "0x2d7dce11ae752f83f47b8a3706fc35b56e2b03a0e121478e216d2bc81d9b2dfe",
      |        "block_number": "14933373",
      |        "from_account": {
      |            "address": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |            "config": "",
      |            "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/25.png",
      |            "user": {
      |                "username": null
      |            }
      |        },
      |        "id": 391848436,
      |        "timestamp": "2022-06-09T15:37:45",
      |        "to_account": {
      |            "address": "0x7f268357a8c2552623316e2562d90e642bb538e5",
      |            "config": "",
      |            "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/28.png",
      |            "user": null
      |        },
      |        "transaction_hash": "0x139de7f5924f71869e44812048ac514d4c55c64d395d18619404911f434a10ae",
      |        "transaction_index": "39"
      |    },
      |    "winner_account": {
      |        "address": "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |        "config": "",
      |        "profile_img_url": "https://storage.googleapis.com/opensea-static/opensea-profile/25.png",
      |        "user": {
      |            "username": null
      |        }
      |    }
      |}
      |""".stripMargin

  val apiResult = {
    KeyConverter.sc2cc(
      s"""
         |{
         |    "next": "LWV2ZW50X3RpbWVzdGFtcD0yMDIxLTA5LTI4KzIwJTNBMzAlM0ExNC41MjMzMzgmLXBrPTExNTE1MzA4MDc=",
         |    "previous": null,
         |    "asset_events": [
         |      $transfer,
         |      $sale
         |    ]
         |}
         |""".stripMargin
    )
  }

  val expectedResult =
    """{
      |  "transactionHash" : "0x139de7f5924f71869e44812048ac514d4c55c64d395d18619404911f434a10ae",
      |  "eventType" : "transfer",
      |  "chain" : "ethereum",
      |  "fromAddress" : "0xcfb098c1d44eb12f93f9aaece5d6054e2a2240ab",
      |  "toAddress" : "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |  "assetContractAddress" : "0x55256178afe74082c4f9afef7e40fec949c1b499",
      |  "assetContractName" : "Philosophical Foxes V2",
      |  "assetContractSymbol" : "FOX",
      |  "tokenType" : "ERC721",
      |  "tokenId" : "1256",
      |  "quantity" : 1,
      |  "paymentTokenSymbol" : null,
      |  "paymentTokenDecimals" : null,
      |  "totalPrice" : null,
      |  "transactionTime" : "2022-06-09T15:37:45Z"
      |}""".stripMargin

  val expectedResult2 =
    """{
      |  "transactionHash" : "0x139de7f5924f71869e44812048ac514d4c55c64d395d18619404911f434a10ae",
      |  "eventType" : "sale",
      |  "chain" : "ethereum",
      |  "fromAddress" : "0xcfb098c1d44eb12f93f9aaece5d6054e2a2240ab",
      |  "toAddress" : "0xbecb05b9335fc0c53aeab1c09733cdf9a0cde85e",
      |  "assetContractAddress" : "0x55256178afe74082c4f9afef7e40fec949c1b499",
      |  "assetContractName" : "Philosophical Foxes V2",
      |  "assetContractSymbol" : "FOX",
      |  "tokenType" : "ERC721",
      |  "tokenId" : "1256",
      |  "quantity" : 1,
      |  "paymentTokenSymbol" : "ETH",
      |  "paymentTokenDecimals" : 18,
      |  "totalPrice" : "190000000000000000",
      |  "transactionTime" : "2022-06-09T15:37:45Z"
      |}""".stripMargin

}