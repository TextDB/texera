package edu.uci.ics.amber.engine.architecture.controller

import akka.actor.{ActorContext, Address}
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.WorkerLayer
import edu.uci.ics.amber.engine.architecture.linksemantics.{AllToOne, FullRoundRobin, HashBasedShuffle, LinkStrategy, OneToOne}
import edu.uci.ics.amber.engine.architecture.messaginglayer.NetworkCommunicationActor.NetworkSenderActorRef
import edu.uci.ics.amber.engine.architecture.principal.OperatorState.Completed
import edu.uci.ics.amber.engine.architecture.principal.OperatorStatistics
import edu.uci.ics.amber.engine.common.{AmberUtils, Constants}
import edu.uci.ics.amber.engine.common.ambertag.{LayerTag, OperatorIdentifier}
import edu.uci.ics.amber.engine.common.ambertag.neo.VirtualIdentity.ActorVirtualIdentity
import edu.uci.ics.amber.engine.operators.{OpExecConfig, SinkOpExecConfig}

import scala.collection.mutable

class Workflow(
    operators: mutable.Map[OperatorIdentifier, OpExecConfig],
    outLinks: Map[OperatorIdentifier, Set[OperatorIdentifier]]
) {
  private val inLinks: Map[OperatorIdentifier, Set[OperatorIdentifier]] =
    AmberUtils.reverseMultimap(outLinks)

  private val startOperators: Iterable[OperatorIdentifier] = operators.keys.filter(!inLinks.contains(_))
  private val endOperators: Iterable[OperatorIdentifier] = operators.keys.filter(!outLinks.contains(_))

  private val workerToLayer = new mutable.HashMap[ActorVirtualIdentity, WorkerLayer]()
  private val layerToOperator = new mutable.HashMap[LayerTag, OpExecConfig]()
  private val operatorLinks = new mutable.HashMap[OperatorIdentifier, mutable.ArrayBuffer[LinkStrategy]]

  def getSources(operator: OperatorIdentifier): Set[OperatorIdentifier] = {
    var result = Set[OperatorIdentifier]()
    var current = Set[OperatorIdentifier](operator)
    while (current.nonEmpty) {
      var next = Set[OperatorIdentifier]()
      for (i <- current) {
        if (inLinks.contains(i) && inLinks(i).nonEmpty) {
          next ++= inLinks(i)
        } else {
          result += i
        }
        current = next
      }
    }
    result
  }

  def getWorkflowStatus:Map[String, OperatorStatistics] = {
    operators.map{
      op =>
        (op._1.operator,op._2.getOperatorStatistics)
    }.toMap
  }

  def getStartOperators:Iterable[OpExecConfig] = startOperators.map(operators(_))

  def getEndOperators:Iterable[OpExecConfig] = endOperators.map(operators(_))

  def getOperator(opID: OperatorIdentifier): OpExecConfig = operators(opID)

  def getOperator(workerID: ActorVirtualIdentity): OpExecConfig = layerToOperator(workerToLayer(workerID).tag)

  def getAllOperators:Iterable[OpExecConfig] = operators.values

  def getWorkerLayer(workerID:ActorVirtualIdentity): WorkerLayer = workerToLayer(workerID)

  def getAllWorkers:Iterable[ActorVirtualIdentity] = workerToLayer.keys

  def getAllLinks: Iterable[LinkStrategy] = operatorLinks.values.flatten ++ operators.values.flatMap(_.topology.links)

  def isCompleted:Boolean = operators.values.forall(op => op.getState == Completed)

  def buildOperator(allNodes:Array[Address], prev: Array[(OpExecConfig, WorkerLayer)], communicationActor: NetworkSenderActorRef, opID:OperatorIdentifier, ctx:ActorContext): Unit ={
    val operator = operators(opID) // This metadata gets updated at the end of this function
    if (operator.topology.links.isEmpty) {
      operator.topology.layers.foreach(x =>{
        x.build(prev, allNodes, communicationActor.ref, ctx, workerToLayer)
        layerToOperator(x.tag) = operator
      })
    } else {
      val operatorInLinks: Map[WorkerLayer, Set[WorkerLayer]] =
        operator.topology.links.groupBy(x => x.to).map(x => (x._1, x._2.map(_.from).toSet))
      var currentLayer: Iterable[WorkerLayer] =
        operator.topology.links
          .filter(x => operator.topology.links.forall(_.to != x.from))
          .map(_.from)
      currentLayer.foreach(x => {
        x.build(prev, allNodes, communicationActor.ref, ctx, workerToLayer)
        layerToOperator(x.tag) = operator
      })
      currentLayer = operatorInLinks.filter(x => x._2.forall(_.isBuilt)).keys
      while (currentLayer.nonEmpty) {
        currentLayer.foreach(x =>{
          x.build(operatorInLinks(x).map(y => (null, y)).toArray, allNodes, communicationActor.ref, ctx, workerToLayer)
          layerToOperator(x.tag) = operator
        })
        currentLayer = operatorInLinks.filter(x => !x._1.isBuilt && x._2.forall(_.isBuilt)).keys
      }
    }
  }

  def linkOperators(from: (OpExecConfig, WorkerLayer), to: (OpExecConfig, WorkerLayer)):LinkStrategy = {
    val sender = from._2
    val receiver = to._2
    val inputNum = to._1.getInputNum(from._1.tag)
    if (to._1.requiredShuffle) {
      new HashBasedShuffle(
        sender,
        receiver,
        Constants.defaultBatchSize,
        to._1.getShuffleHashFunction(sender.tag),
        inputNum
      )
    } else if (to._1.isInstanceOf[SinkOpExecConfig]) {
      new AllToOne(sender, receiver, Constants.defaultBatchSize, inputNum)
    } else if (sender.numWorkers == receiver.numWorkers) {
      new OneToOne(sender, receiver, Constants.defaultBatchSize, inputNum)
    } else {
      new FullRoundRobin(sender, receiver, Constants.defaultBatchSize, inputNum)
    }
  }



  def buildLinks(to: OperatorIdentifier):Unit ={
    if(!inLinks.contains(to)){
      return
    }
    for (from <- inLinks(to)) {
        val edge = linkOperators(
          (
            operators(from),
            operators(from).topology.layers.last
          ),
          (
            operators(to),
            operators(to).topology.layers.head
          )
        )
      if(operatorLinks.contains(from)){
        operatorLinks(from).append(edge)
      }else{
        operatorLinks(from) = mutable.ArrayBuffer[LinkStrategy](edge)
      }
    }
  }

  def build(allNodes:Array[Address], communicationActor: NetworkSenderActorRef, ctx:ActorContext): Unit ={
    val builtOperators = mutable.HashSet[OperatorIdentifier]()
    var frontier = startOperators
    while(frontier.nonEmpty){
      frontier.foreach{
        op =>
          val prev:Array[(OpExecConfig, WorkerLayer)] = if(inLinks.contains(op)){
            inLinks(op).map(x =>
              (
                operators(x),
                operators(x).topology.layers.last
              )).toArray
          }else{
            Array.empty
          }
          buildOperator(allNodes, prev, communicationActor, op, ctx)
          buildLinks(op)
          builtOperators.add(op)
      }
      frontier = inLinks.filter {
        case (op, inlinks) =>
          !builtOperators.contains(op) && inlinks.forall(builtOperators.contains)
      }.keys
    }
  }


}
