package edu.uci.ics.amber.engine.architecture.messaginglayer

import edu.uci.ics.amber.engine.common.ambertag.neo.Identifier

import scala.collection.mutable
import scala.reflect.ClassTag

object OrderingEnforcer {
  def reorderMessage[V: ClassTag](
      seqMap: mutable.AnyRefMap[Identifier, OrderingEnforcer[V]],
      sender: Identifier,
      seq: Long,
      command: V
  ): Option[Iterable[V]] = {
    val entry = seqMap.getOrElseUpdate(sender, new OrderingEnforcer[V]())
    if (entry.isDuplicated(seq)) {
      None
    } else if (entry.isAhead(seq)) {
      entry.stash(seq, command)
      None
    } else {
      Some(entry.enforceFIFO(seq, command))
    }
  }
}

/* The abstracted FIFO/exactly-once logic */
class OrderingEnforcer[T: ClassTag] {

  var current = 0L
  val ofoMap = new mutable.LongMap[T]

  def isDuplicated(sequenceNumber: Long): Boolean =
    sequenceNumber < current || ofoMap.contains(sequenceNumber)

  def isAhead(sequenceNumber: Long): Boolean = sequenceNumber > current

  def stash(sequenceNumber: Long, data: T): Unit = {
    ofoMap(sequenceNumber) = data
  }

  def enforceFIFO(sequenceNumber: Long, data: T): Array[T] = {
    val res = mutable.ArrayBuffer[T](data)
    current += 1
    while (ofoMap.contains(current)) {
      res.append(ofoMap(current))
      ofoMap.remove(current)
      current += 1
    }
    res.toArray
  }
}
