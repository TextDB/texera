package edu.uci.ics.texera.workflow.operators.visualization.scatterplot

import edu.uci.ics.amber.engine.architecture.breakpoint.globalbreakpoint.GlobalBreakpoint
import edu.uci.ics.amber.engine.architecture.deploysemantics.deploymentfilter.{ForceLocal}
import edu.uci.ics.amber.engine.architecture.deploysemantics.deploystrategy.{RandomDeployment}
import edu.uci.ics.amber.engine.architecture.deploysemantics.layer.WorkerLayer
import edu.uci.ics.amber.engine.common.virtualidentity.util.makeLayer
import edu.uci.ics.amber.engine.common.virtualidentity.{
  ActorVirtualIdentity,
  LayerIdentity,
  OperatorIdentity
}
import edu.uci.ics.amber.engine.operators.OpExecConfig
import edu.uci.ics.texera.workflow.common.tuple.schema.OperatorSchemaInfo

class ScatterplotOpExecConfig(
    tag: OperatorIdentity,
    opDesc: ScatterplotOpDesc,
    schemaInfo: OperatorSchemaInfo
) extends OpExecConfig(tag) {

  override lazy val topology = new Topology(
    Array(
      new WorkerLayer(
        makeLayer(tag, "main"),
        _ => new ScatterplotOpExec(opDesc, schemaInfo),
        1,
        ForceLocal(),
        RandomDeployment()
      )
    ),
    Array()
  )

  override def assignBreakpoint(
      breakpoint: GlobalBreakpoint[_]
  ): Array[ActorVirtualIdentity] = {
    topology.layers(0).identifiers
  }

}
