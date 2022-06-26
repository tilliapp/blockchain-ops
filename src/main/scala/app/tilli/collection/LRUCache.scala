package app.tilli.collection

trait LRUCache[K, V] {

  /**
   * Get the Value if it exists.
   * Usage of this method will count as a "hit" that increments the access count for the key
   */
  def getOption(key: K): Option[V]

  /**
   * Check if the Key exists.
   * Usage of this method will not count as a "hit" and will therefore not increment the cache access counts
   */
  def containsKey(key: K): Boolean

  def put(key: K, value: V): Option[V]

  def size: Int

  def remove(key: K): Option[V]

}