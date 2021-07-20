package edu.uci.ics.texera.unittest.workflow.operators.visualization.scatterplot

import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{
  Attribute,
  AttributeType,
  OperatorSchemaInfo,
  Schema
}
import edu.uci.ics.texera.workflow.operators.visualization.scatterplot.{
  ScatterplotOpDesc,
  ScatterplotOpExec
}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

class ScatterplotVizOpExecSpec extends AnyFlatSpec with BeforeAndAfter {

  val tupleSchema: Schema = Schema
    .newBuilder()
    .add(new Attribute("field1", AttributeType.DOUBLE))
    .add(new Attribute("field2", AttributeType.INTEGER))
    .build()

  val desc: ScatterplotOpDesc = new ScatterplotOpDesc()
  val tuple: Tuple = Tuple
    .newBuilder(tupleSchema)
    .add(new Attribute("field1", AttributeType.DOUBLE), 73.142)
    .add(new Attribute("field2", AttributeType.INTEGER), 32)
    .build()
  val extratuple: Tuple = Tuple
    .newBuilder(tupleSchema)
    .add(new Attribute("field1", AttributeType.DOUBLE), 77.142)
    .add(new Attribute("field2", AttributeType.INTEGER), 31)
    .build()
  var scatterplotOpExec: ScatterplotOpExec = _

  before {
    desc.isGeometric = true
    desc.xColumn = "field1"
    desc.yColumn = "field2"
    val outputSchema: Schema = desc.getOutputSchema(Array(tupleSchema))
    val operatorSchemaInfo: OperatorSchemaInfo =
      OperatorSchemaInfo(Array(tupleSchema), outputSchema)
    scatterplotOpExec = new ScatterplotOpExec(desc, operatorSchemaInfo)
  }

  it should "process more tuples" in {
    val processedTuple: Tuple = scatterplotOpExec.processTuple(tuple)
    val processedAnotherTuple: Tuple = scatterplotOpExec.processTuple(extratuple)
    assert(processedTuple.getField("xColumn").asInstanceOf[Double] == 73.142)
    assert(processedTuple.getField("yColumn").asInstanceOf[Integer] == 32)
    assert(processedAnotherTuple.getField("xColumn").asInstanceOf[Double] == 77.142)
    assert(processedAnotherTuple.getField("yColumn").asInstanceOf[Integer] == 31)
  }

  it should "handle last tuple correctly" in {
    val outputTuples: List[Tuple] =
      scatterplotOpExec.processTexeraTuple(Right(InputExhausted()), null).toList
    assert(outputTuples.size == 0)
  }
}
