package app.tilli.blockchain.asset

import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}

class ApiRateLimiterFunction extends RichAsyncFunction[String, String]() {

  /** The database specific client that can issue concurrent requests with callbacks */
  private transient DatabaseClient client;

  @Override
  public void open(Configuration parameters) throws Exception {
    client = new DatabaseClient(host, post, credentials);
  }

  @Override
  public void close() throws Exception {
    client.close();
  }

  override def asyncInvoke(input: String, resultFuture: ResultFuture[String]): Unit = ???

}
