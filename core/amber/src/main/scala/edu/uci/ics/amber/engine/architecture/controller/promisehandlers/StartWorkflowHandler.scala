package edu.uci.ics.amber.engine.architecture.controller.promisehandlers

import com.twitter.util.Future
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.QueryWorkerStatisticsHandler.QueryWorkerStatistics
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.StartWorkflowHandler.StartWorkflow
import edu.uci.ics.amber.engine.architecture.controller.{
  Controller,
  ControllerAsyncRPCHandlerInitializer,
  ControllerState
}
import edu.uci.ics.amber.engine.architecture.worker.promisehandlers.StartHandler.StartWorker
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.ControlInvocation
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCServer.{CommandCompleted, ControlCommand}
import edu.uci.ics.amber.engine.common.statetransition.WorkerStateManager.Running

import scala.concurrent.duration.{DurationInt, FiniteDuration, MILLISECONDS}

object StartWorkflowHandler {
  final case class StartWorkflow() extends ControlCommand[CommandCompleted]
}

trait StartWorkflowHandler {
  this: ControllerAsyncRPCHandlerInitializer =>

  registerHandler { (msg: StartWorkflow, sender) =>
    Future
      .collect(workflow.getSourceLayers.flatMap {
        case layer if layer.canStart => layer.workers.keys.map(send(StartWorker(), _))
      }.toSeq)
      .map { ret =>
        println("workflow started")
        actorContext.parent ! ControllerState.Running // for testing
        //TODO: change it to operators which are actually started!!!
//        workflow.getStartOperators.foreach { op =>
//          op.setAllWorkerState(Running)
//        }
        enableStatusUpdate()
        CommandCompleted()
      }
  }
}
