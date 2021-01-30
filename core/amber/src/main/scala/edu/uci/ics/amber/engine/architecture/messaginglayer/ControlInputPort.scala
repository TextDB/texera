package edu.uci.ics.amber.engine.architecture.messaginglayer

import com.typesafe.scalalogging.LazyLogging
import edu.uci.ics.amber.engine.architecture.messaginglayer.ControlInputPort.WorkflowControlMessage
import edu.uci.ics.amber.engine.architecture.worker.WorkerStatistics
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.QueryStatisticsHandler.QueryStatistics
import edu.uci.ics.amber.engine.common.WorkflowLogger
import edu.uci.ics.amber.engine.common.ambermessage.{ControlPayload, WorkflowMessage}
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.{ControlInvocation, ReturnPayload}
import edu.uci.ics.amber.engine.common.rpc.{AsyncRPCClient, AsyncRPCServer}
import edu.uci.ics.amber.engine.common.virtualidentity.{ActorVirtualIdentity, VirtualIdentity}
import edu.uci.ics.amber.error.WorkflowRuntimeError

import scala.collection.mutable

object ControlInputPort {
  final case class WorkflowControlMessage(
      from: VirtualIdentity,
      sequenceNumber: Long,
      payload: ControlPayload
  ) extends WorkflowMessage
}

class ControlInputPort(asyncRPCClient: AsyncRPCClient, asyncRPCServer: AsyncRPCServer) {

  protected val logger: WorkflowLogger = WorkflowLogger("ControlInputPort")

  private val idToOrderingEnforcers =
    new mutable.AnyRefMap[VirtualIdentity, OrderingEnforcer[ControlPayload]]()

  def handleControlMessage(msg: WorkflowControlMessage): Unit = {
    OrderingEnforcer.reorderMessage(
      idToOrderingEnforcers,
      msg.from,
      msg.sequenceNumber,
      msg.payload
    ) match {
      case Some(iterable) =>
        iterable.foreach {
          case call: ControlInvocation =>
            assert(msg.from.isInstanceOf[ActorVirtualIdentity])
            logControlInvocation(call, msg.from)
            asyncRPCServer.receive(call, msg.from.asInstanceOf[ActorVirtualIdentity])
          case ret: ReturnPayload =>
            logControlReply(ret, msg.from)
            asyncRPCClient.fulfillPromise(ret)
          case other =>
            logger.logError(
              WorkflowRuntimeError(
                s"unhandled control message: $other",
                "ControlInputPort",
                Map.empty
              )
            )
        }
      case None =>
        // discard duplicate
        logger.logInfo(s"receive duplicated: ${msg.payload}")
    }
  }

  def logControlInvocation(call: ControlInvocation, sender:VirtualIdentity): Unit ={
    if(call.command.isInstanceOf[QueryStatistics]){
      // ignore statistics control messages
      return
    }
    logger.logInfo(
      s"receive command: ${call.command} from ${sender.toString} (controlID: ${call.commandID})"
    )
  }

  def logControlReply(ret:ReturnPayload, sender:VirtualIdentity): Unit ={
    if(ret.returnValue.isInstanceOf[WorkerStatistics]){
      // ignore statistics control messages
      return
    }
    if (ret.returnValue != null) {
      logger.logInfo(
        s"receive reply: ${ret.returnValue.getClass.getSimpleName} from ${sender.toString} (controlID: ${ret.originalCommandID})"
      )
    } else {
      logger.logInfo(s"receive reply: null from ${sender.toString} (controlID: ${ret.originalCommandID})")
    }
  }


}
