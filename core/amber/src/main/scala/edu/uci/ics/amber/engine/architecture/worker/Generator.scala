package edu.uci.ics.amber.engine.architecture.worker

import java.util.concurrent.Executors

import edu.uci.ics.amber.engine.architecture.breakpoint.FaultedTuple
import edu.uci.ics.amber.engine.architecture.breakpoint.localbreakpoint.{
  ExceptionBreakpoint,
  LocalBreakpoint
}
import edu.uci.ics.amber.engine.common.amberexception.BreakpointException
import edu.uci.ics.amber.engine.common.{AdvancedMessageSending, ElidableStatement, IOperatorExecutor, ISourceOperatorExecutor, InputExhausted, ThreadState}
import edu.uci.ics.amber.engine.common.ambermessage.WorkerMessage._
import edu.uci.ics.amber.engine.common.ambermessage.StateMessage._
import edu.uci.ics.amber.engine.common.ambermessage.ControlMessage._
import edu.uci.ics.amber.engine.common.ambertag.{LayerTag, WorkerTag}
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.faulttolerance.recovery.RecoveryPacket
import akka.actor.{ActorLogging, Props, Stash}
import akka.event.LoggingAdapter
import akka.util.Timeout
import com.softwaremill.macwire.wire
import edu.uci.ics.amber.engine.architecture.worker.neo.{BatchInput, DataProcessor, PauseControl, TupleInput, TupleOutput}

import scala.annotation.elidable
import scala.annotation.elidable.INFO

object Generator {
  def props(producer: ISourceOperatorExecutor, tag: WorkerTag): Props =
    Props(new Generator(producer, tag))
}

class Generator(var operator: IOperatorExecutor, val tag: WorkerTag)
    extends WorkerBase
    with ActorLogging
    with Stash{

  //insert a SPECIAL case for generator
  batchInput.inputMap(LayerTag("","","")) = 0

  @elidable(INFO) var generateTime = 0L
  @elidable(INFO) var generateStart = 0L

  override def onReset(value: Any, recoveryInformation: Seq[(Long, Long)]): Unit = {
    super.onReset(value, recoveryInformation)
    operator = value.asInstanceOf[ISourceOperatorExecutor]
    operator.open()
    dataProcessor.resetBreakpoints()
    tupleOutput.resetOutput()
    context.become(ready)
    self ! Start
  }

  override def onResuming(): Unit = {
    super.onResuming()
    pauseControl.resume(PauseControl.User)
  }

  override def onCompleted(): Unit = {
    super.onCompleted()
    ElidableStatement.info {
      val (_, outputCount) = dataProcessor.collectStatistics()
      log.info(
        "completed its job. total: {} ms, generating: {} ms, generated {} tuples",
        (System.nanoTime() - startTime) / 1000000,
        generateTime / 1000000,
        outputCount
      )
    }
  }

  override def onPaused(): Unit = {
    val (inputCount, outputCount) = dataProcessor.collectStatistics()
    log.info(s"paused at $outputCount , 0")
    context.parent ! ReportCurrentProcessingTuple(self.path, dataProcessor.getCurrentInputTuple)
    context.parent ! RecoveryPacket(tag, outputCount, 0)
    context.parent ! ReportState(WorkerState.Paused)
  }

  override def onResumeTuple(faultedTuple: FaultedTuple): Unit = {
    var i = 0
    while (i < tupleOutput.output.length) {
      tupleOutput.output(i).accept(faultedTuple.tuple)
      i += 1
    }
  }

  override def getInputRowCount(): Long = {
    0 // source operator should not have input rows
  }

  override def getOutputRowCount(): Long = {
    val (_, outputCount) = dataProcessor.collectStatistics()
    outputCount
  }

  override def onModifyTuple(faultedTuple: FaultedTuple): Unit = {
    userFixedTuple = faultedTuple.tuple
  }

  override def onPausing(): Unit = {
    super.onPausing()
    pauseControl.pause(PauseControl.User)
    onPaused()
    context.become(paused)
    unstashAll()
  }

  override def onInitialization(recoveryInformation: Seq[(Long, Long)]): Unit = {
    super.onInitialization(recoveryInformation)
    operator.open()
  }

  override def onStart(): Unit = {
    super.onStart()
    batchInput.addBatch((LayerTag("","",""),null))
    context.become(running)
    unstashAll()
  }

}
