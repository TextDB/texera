package edu.uci.ics.texera.workflow.operators.typecasting

import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{Attribute, AttributeType, Schema}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

class TypeCastingOpExecSpec extends AnyFlatSpec with BeforeAndAfter {
  val tupleSchema: Schema = Schema
    .newBuilder()
    .add(new Attribute("field1", AttributeType.STRING))
    .add(new Attribute("field2", AttributeType.INTEGER))
    .add(new Attribute("field3", AttributeType.BOOLEAN))
    .add(new Attribute("field4", AttributeType.LONG))
    .build()

  val castToSchema: Schema = Schema
    .newBuilder()
    .add(new Attribute("field1", AttributeType.STRING))
    .add(new Attribute("field2", AttributeType.STRING))
    .add(new Attribute("field3", AttributeType.STRING))
    .add(new Attribute("field4", AttributeType.LONG))
    .build()

  val tuple: Tuple = Tuple
    .newBuilder(tupleSchema)
    .add(new Attribute("field1", AttributeType.STRING), "hello")
    .add(new Attribute("field2", AttributeType.INTEGER), 1)
    .add(
      new Attribute("field3", AttributeType.BOOLEAN),
      true
    )
    .add(
      new Attribute("field4", AttributeType.LONG),
      3L
    )
    .build()

  it should "open" in {
    val typeCastingOpExec = new TypeCastingOpExec(castToSchema)
    typeCastingOpExec.open()

  }

  it should "process Tuple" in {

    val typeCastingOpExec = new TypeCastingOpExec(castToSchema)

    typeCastingOpExec.open()

    val processedTuple = typeCastingOpExec.processTexeraTuple(Left(tuple), null).next()
    assert(processedTuple.length() == 4)
    assert(processedTuple.getField("field1").asInstanceOf[String] == "hello")
    assert(processedTuple.getField("field2").asInstanceOf[String] == "1")
    assert(processedTuple.getField("field3").asInstanceOf[String] == "true")
    assert(processedTuple.getField("field4").asInstanceOf[Long] == 3L)
    assert(processedTuple.get(0) == "hello")
    assert(processedTuple.get(1) == "1")
    assert(processedTuple.get(2) == "true")
    assert(processedTuple.get(3) == 3L)
  }
}
