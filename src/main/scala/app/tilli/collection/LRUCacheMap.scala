package app.tilli.collection

import java.util.Collections.synchronizedMap

class LRUCacheMap[K, V](
  maxEntries: Int,
  initialCapacity: Int = 100,
  loadFactor: Float = 0.75f,
  accessOrder: Boolean = true,
) extends LRUCache[K, V] {

  private val map = synchronizedMap(new java.util.LinkedHashMap[K, V](initialCapacity, loadFactor, accessOrder) {
    override def removeEldestEntry(eldest: java.util.Map.Entry[K, V]): Boolean = size > maxEntries
  })

  override def getOption(key: K): Option[V] = Option(map.get(key)).filter(_ != null)

  override def containsKey(key: K): Boolean = map.containsKey(key)

  override def put(key: K, value: V): Option[V] = Option(map.put(key, value)).filter(_ != null)

  override def size: Int = map.size()

  override def remove(key: K): Option[V] = Option(map.remove(key)).filter(_ != null)
}