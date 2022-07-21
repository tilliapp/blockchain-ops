package app.tilli.blockchain.dataprovider

import app.tilli.blockchain.codec.BlockchainClasses.DataProvider
import app.tilli.blockchain.codec.BlockchainConfig.dataProviderTilli

object TilliDataProvider
  extends DataProvider(
    dataProviderTilli.source,
    dataProviderTilli.provider,
    dataProviderTilli.name,
    dataProviderTilli.defaultPage,
  )