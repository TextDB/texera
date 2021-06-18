package edu.uci.ics.texera.web.resource

import akka.actor.{ActorRef, PoisonPill}
import edu.uci.ics.amber.engine.architecture.controller.{Controller, ControllerEventListener}
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.PauseHandler.PauseWorkflow
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.ResumeHandler.ResumeWorkflow
import edu.uci.ics.amber.engine.architecture.controller.promisehandlers.StartWorkflowHandler.StartWorkflow
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient
import edu.uci.ics.amber.engine.common.rpc.AsyncRPCClient.ControlInvocation
import edu.uci.ics.amber.engine.common.tuple.ITuple
import edu.uci.ics.amber.engine.common.virtualidentity.WorkflowIdentity
import edu.uci.ics.texera.web.{ServletAwareConfigurator, TexeraWebApplication}
import edu.uci.ics.texera.web.model.event._
import edu.uci.ics.texera.web.model.request._
import edu.uci.ics.texera.web.resource.WorkflowWebsocketResource._
import edu.uci.ics.texera.web.resource.auth.UserResource
import edu.uci.ics.texera.workflow.common.{Utils, WorkflowContext}
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.workflow.{WorkflowCompiler, WorkflowInfo}

import java.util.concurrent.atomic.AtomicInteger
import javax.servlet.http.HttpSession
import javax.websocket.{EndpointConfig, _}
import javax.websocket.server.ServerEndpoint
import scala.collection.mutable

object WorkflowWebsocketResource {
  // TODO should reorganize this resource.

  val nextJobID = new AtomicInteger(0)

  // Map[sessionId, (Session, HttpSession)]
  val sessionMap = new mutable.HashMap[String, (Session, HttpSession)]

  // Map[sessionId, (WorkflowCompiler, ActorRef)]
  val sessionJobs = new mutable.HashMap[String, (WorkflowCompiler, ActorRef)]

  // Map[sessionId, Map[operatorId, List[ITuple]]]
  val sessionResults = new mutable.HashMap[String, Map[String, List[ITuple]]]

  // Map[sessionId, Map[downloadType, googleSheetLink]
  val sessionDownloadCache = new mutable.HashMap[String, mutable.HashMap[String, String]]

  /**
    * Calculate which page in frontend need to be re-fetched
    * @param beforeList data before status update event (i.e. unmodified sessionResults)
    * @param afterList data after status update event
    * @return list of indices of modified pages starting from 1
    */
  def getDirtyPageIndices(beforeList: List[ITuple], afterList: List[ITuple]): List[Int] = {
    val pageSize = 10

    var currentIndex = 1
    var currentIndexPageCount = 0
    val dirtyPageIndices = new mutable.HashSet[Int]()
    for ((before, after) <- beforeList.zipAll(afterList, null, null)) {
      if (before == null || after == null || !before.equals(after)) {
        dirtyPageIndices.add(currentIndex)
      }
      currentIndexPageCount += 1
      if (currentIndexPageCount == pageSize) {
        currentIndexPageCount = 0
        currentIndex += 1
      }
    }

    dirtyPageIndices.toList
  }

}

@ServerEndpoint(
  value = "/wsapi/workflow-websocket",
  configurator = classOf[ServletAwareConfigurator]
)
class WorkflowWebsocketResource {

  final val objectMapper = Utils.objectMapper

  @OnOpen
  def myOnOpen(session: Session, config: EndpointConfig): Unit = {
    WorkflowWebsocketResource.sessionMap.update(
      session.getId,
      (session, config.getUserProperties.get("httpSession").asInstanceOf[HttpSession])
    )
    println("connection open")
  }

  @OnMessage
  def myOnMsg(session: Session, message: String): Unit = {
    val request = objectMapper.readValue(message, classOf[TexeraWebSocketRequest])
    try {
      request match {
        case helloWorld: HelloWorldRequest =>
          send(session, HelloWorldResponse("hello from texera web server"))
        case heartbeat: HeartBeatRequest =>
          send(session, HeartBeatResponse())
        case execute: ExecuteWorkflowRequest =>
          println(execute)
          executeWorkflow(session, execute)
        case newLogic: ModifyLogicRequest =>
          println(newLogic)
          modifyLogic(session, newLogic)
        case pause: PauseWorkflowRequest =>
          pauseWorkflow(session)
        case resume: ResumeWorkflowRequest =>
          resumeWorkflow(session)
        case kill: KillWorkflowRequest =>
          killWorkflow(session)
        case skipTupleMsg: SkipTupleRequest =>
          skipTuple(session, skipTupleMsg)
        case breakpoint: AddBreakpointRequest =>
          addBreakpoint(session, breakpoint)
        case paginationRequest: ResultPaginationRequest =>
          resultPagination(session, paginationRequest)
        case resultDownloadRequest: ResultDownloadRequest =>
          downloadResult(session, resultDownloadRequest)
      }
    } catch {
      case e: Throwable =>
        send(
          session,
          WorkflowErrorEvent(generalErrors =
            Map("exception" -> (e.getMessage + "\n" + e.getStackTrace.mkString("\n")))
          )
        )
        throw e
    }

  }

  def resultPagination(session: Session, request: ResultPaginationRequest): Unit = {
    val paginatedResultEvent = PaginatedResultEvent(
      sessionResults
        .getOrElse(session.getId, Map.empty[String, List[ITuple]])
        .map {
          case (operatorID, table) =>
            (
              operatorID,
              table
                .slice(
                  request.pageSize * (request.pageIndex - 1),
                  request.pageSize * request.pageIndex
                )
                .map(tuple => tuple.asInstanceOf[Tuple].asKeyValuePairJson())
            )
        }
        .map {
          case (operatorID, objNodes) =>
            PaginatedOperatorResult(
              operatorID,
              objNodes,
              sessionResults
                .getOrElse(session.getId, Map.empty[String, List[ITuple]])
                .getOrElse(operatorID, List.empty[ITuple])
                .size
            )
        }
        .toList
    )

    send(session, paginatedResultEvent)
  }

  def addBreakpoint(session: Session, addBreakpoint: AddBreakpointRequest): Unit = {
    val compiler = WorkflowWebsocketResource.sessionJobs(session.getId)._1
    val controller = WorkflowWebsocketResource.sessionJobs(session.getId)._2
    compiler.addBreakpoint(controller, addBreakpoint.operatorID, addBreakpoint.breakpoint)
  }

  def skipTuple(session: Session, tupleReq: SkipTupleRequest): Unit = {
//    val actorPath = tupleReq.actorPath
//    val faultedTuple = tupleReq.faultedTuple
//    val controller = WorkflowWebsocketResource.sessionJobs(session.getId)._2
//    controller ! SkipTupleGivenWorkerRef(actorPath, faultedTuple.toFaultedTuple())
    throw new RuntimeException("skipping tuple is temporarily disabled")
  }

  def modifyLogic(session: Session, newLogic: ModifyLogicRequest): Unit = {
//    val texeraOperator = newLogic.operator
//    val (compiler, controller) = WorkflowWebsocketResource.sessionJobs(session.getId)
//    compiler.initOperator(texeraOperator)
//    controller ! ModifyLogic(texeraOperator.operatorExecutor)
    throw new RuntimeException("modify logic is temporarily disabled")
  }

  def pauseWorkflow(session: Session): Unit = {
    val controller = WorkflowWebsocketResource.sessionJobs(session.getId)._2
    controller ! ControlInvocation(AsyncRPCClient.IgnoreReply, PauseWorkflow())
    // workflow paused event will be send after workflow is actually paused
    // the callback function will handle sending the paused event to frontend
  }

  def resumeWorkflow(session: Session): Unit = {
    val controller = WorkflowWebsocketResource.sessionJobs(session.getId)._2
    controller ! ControlInvocation(AsyncRPCClient.IgnoreReply, ResumeWorkflow())
    send(session, WorkflowResumedEvent())
  }

  def send(session: Session, event: TexeraWebSocketEvent): Unit = {
    session.getAsyncRemote.sendText(objectMapper.writeValueAsString(event))
  }

  def executeWorkflow(session: Session, request: ExecuteWorkflowRequest): Unit = {
    val context = new WorkflowContext
    val jobID = Integer.toString(WorkflowWebsocketResource.nextJobID.incrementAndGet)
    context.jobID = jobID
    context.userID = UserResource
      .getUser(sessionMap(session.getId)._2)
      .map(u => u.getUid)

    val texeraWorkflowCompiler = new WorkflowCompiler(
      WorkflowInfo(request.operators, request.links, request.breakpoints),
      context
    )

//    texeraWorkflowCompiler.init()
    val violations = texeraWorkflowCompiler.validate
    if (violations.nonEmpty) {
      send(session, WorkflowErrorEvent(violations))
      return
    }

    val workflow = texeraWorkflowCompiler.amberWorkflow
    val workflowTag = WorkflowIdentity(jobID)

    val eventListener = ControllerEventListener(
      workflowCompletedListener = completed => {
        sessionResults.remove(session.getId)
        sessionDownloadCache.remove(session.getId)
        sessionResults.update(session.getId, completed.result)
        send(
          session,
          WorkflowCompletedEvent.apply(completed, texeraWorkflowCompiler)
        )
        WorkflowWebsocketResource.sessionJobs.remove(session.getId)
      },
      workflowStatusUpdateListener = statusUpdate => {
        val sinkOpDirtyPageIndices = statusUpdate.operatorStatistics
          .filter(e => e._2.aggregatedOutputResults.isDefined)
          .map(e => {
            val beforeList =
              sessionResults.getOrElse(session.getId, Map.empty).getOrElse(e._1, List.empty)
            val afterList = e._2.aggregatedOutputResults.get
            val dirtyPageIndices = getDirtyPageIndices(beforeList, afterList)
            (e._1, dirtyPageIndices)
          })

        sessionResults.update(
          session.getId,
          statusUpdate.operatorStatistics
            .filter(e => e._2.aggregatedOutputResults.isDefined)
            .map(e => (e._1, e._2.aggregatedOutputResults.get))
        )
        send(
          session,
          WebWorkflowStatusUpdateEvent.apply(
            statusUpdate,
            sinkOpDirtyPageIndices,
            texeraWorkflowCompiler
          )
        )
      },
      modifyLogicCompletedListener = _ => {
        send(session, ModifyLogicCompletedEvent())
      },
      breakpointTriggeredListener = breakpointTriggered => {
        send(session, BreakpointTriggeredEvent.apply(breakpointTriggered))
      },
      workflowPausedListener = _ => {
        send(session, WorkflowPausedEvent())
      },
      skipTupleResponseListener = _ => {
        send(session, SkipTupleResponseEvent())
      },
      reportCurrentTuplesListener = report => {
//        send(session, OperatorCurrentTuplesUpdateEvent.apply(report))
      },
      recoveryStartedListener = _ => {
        send(session, RecoveryStartedEvent())
      },
      workflowExecutionErrorListener = errorOccurred => {
        send(session, WorkflowExecutionErrorEvent(errorOccurred.error.convertToMap()))
      }
    )

    val controllerActorRef = TexeraWebApplication.actorSystem.actorOf(
      Controller.props(workflowTag, workflow, eventListener, 100)
    )
    texeraWorkflowCompiler.initializeBreakpoint(controllerActorRef)
    controllerActorRef ! ControlInvocation(AsyncRPCClient.IgnoreReply, StartWorkflow())

    WorkflowWebsocketResource.sessionJobs(session.getId) =
      (texeraWorkflowCompiler, controllerActorRef)

    send(session, WorkflowStartedEvent())

  }

  def downloadResult(session: Session, request: ResultDownloadRequest): Unit = {
    val resultDownloadResponse = ResultDownloadResource.apply(session.getId, request)
    send(session, resultDownloadResponse)
  }

  def killWorkflow(session: Session): Unit = {
    WorkflowWebsocketResource.sessionJobs(session.getId)._2 ! PoisonPill
    println("workflow killed")
  }

  @OnClose
  def myOnClose(session: Session, cr: CloseReason): Unit = {
    if (WorkflowWebsocketResource.sessionJobs.contains(session.getId)) {
      println(s"session ${session.getId} disconnected, kill its controller actor")
      this.killWorkflow(session)
    }

    sessionResults.remove(session.getId)
    sessionJobs.remove(session.getId)
    sessionMap.remove(session.getId)
    sessionDownloadCache.remove(session.getId)
  }

  def removeBreakpoint(session: Session, removeBreakpoint: RemoveBreakpointRequest): Unit = {
    throw new UnsupportedOperationException();
  }

}
