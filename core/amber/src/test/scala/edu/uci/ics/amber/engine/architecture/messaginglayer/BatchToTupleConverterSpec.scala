package edu.uci.ics.amber.engine.architecture.messaginglayer

import edu.uci.ics.amber.engine.architecture.worker.neo.WorkerInternalQueue
import com.softwaremill.macwire.wire
import edu.uci.ics.amber.engine.architecture.worker.neo.WorkerInternalQueue.{
  EndMarker,
  EndOfAllMarker,
  InputTuple,
  SenderChangeMarker
}
import edu.uci.ics.amber.engine.common.ambermessage.WorkerMessage.{DataFrame, EndOfUpstream}
import edu.uci.ics.amber.engine.common.ambertag.neo.VirtualIdentity.NamedActorVirtualIdentity
import edu.uci.ics.amber.engine.common.tuple.ITuple
import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec

class BatchToTupleConverterSpec extends AnyFlatSpec with MockFactory {
  private val mockInternalQueue = mock[WorkerInternalQueue]
  private val fakeID = NamedActorVirtualIdentity("testReceiver")

  "tuple producer" should "break batch into tuples and output" in {
    val batchToTupleConverter = wire[BatchToTupleConverter]
    val inputBatch = DataFrame(Array.fill(4)(ITuple(1, 2, 3, 5, "9.8", 7.6)))
    inSequence {
      (mockInternalQueue.appendElement _).expects(SenderChangeMarker(0))
      inputBatch.frame.foreach { i =>
        (mockInternalQueue.appendElement _).expects(InputTuple(i))
      }
      (mockInternalQueue.appendElement _).expects(EndMarker())
      (mockInternalQueue.appendElement _).expects(EndOfAllMarker())
    }
    batchToTupleConverter.registerInput(fakeID, 0)
    batchToTupleConverter.processDataEvents(fakeID, Iterable(inputBatch))
    batchToTupleConverter.processDataEvents(fakeID, Iterable(EndOfUpstream()))
  }

  "tuple producer" should "be aware of upstream change" in {
    val batchToTupleConverter = wire[BatchToTupleConverter]
    val inputBatchFromUpstream1 = DataFrame(Array.fill(4)(ITuple(1, 2, 3, 5, "9.8", 7.6)))
    val inputBatchFromUpstream2 = DataFrame(Array.fill(4)(ITuple(2, 3, 4, 5, "6.7", 8.9)))
    inSequence {
      (mockInternalQueue.appendElement _).expects(SenderChangeMarker(0))
      inputBatchFromUpstream1.frame.foreach { i =>
        (mockInternalQueue.appendElement _).expects(InputTuple(i))
      }
      (mockInternalQueue.appendElement _).expects(SenderChangeMarker(1))
      inputBatchFromUpstream2.frame.foreach { i =>
        (mockInternalQueue.appendElement _).expects(InputTuple(i))
      }
      (mockInternalQueue.appendElement _).expects(EndMarker())
      (mockInternalQueue.appendElement _).expects(SenderChangeMarker(0))
      (mockInternalQueue.appendElement _).expects(EndMarker())
      (mockInternalQueue.appendElement _).expects(EndOfAllMarker())
    }
    val first = NamedActorVirtualIdentity("first upstream")
    val second = NamedActorVirtualIdentity("second upstream")
    batchToTupleConverter.registerInput(first, 0)
    batchToTupleConverter.registerInput(second, 1)
    batchToTupleConverter.processDataEvents(first, Iterable(inputBatchFromUpstream1))
    batchToTupleConverter.processDataEvents(
      second,
      Iterable(inputBatchFromUpstream2, EndOfUpstream())
    )
    batchToTupleConverter.processDataEvents(first, Iterable(EndOfUpstream()))

  }

}
