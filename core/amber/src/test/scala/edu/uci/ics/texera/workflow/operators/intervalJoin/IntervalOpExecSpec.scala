package edu.uci.ics.texera.workflow.operators.intervalJoin

import java.sql.Timestamp

import edu.uci.ics.amber.engine.common.InputExhausted
import edu.uci.ics.amber.engine.common.virtualidentity.{LayerIdentity, LinkIdentity}
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{
  Attribute,
  AttributeType,
  OperatorSchemaInfo,
  Schema
}
import org.scalatest.BeforeAndAfter
import org.scalatest.flatspec.AnyFlatSpec

import scala.collection.mutable.ArrayBuffer
import scala.util.Random.{nextInt, nextLong}

class IntervalOpExecSpec extends AnyFlatSpec with BeforeAndAfter {
  val left: LinkIdentity = linkID()
  val right: LinkIdentity = linkID()

  var opDesc: IntervalJoinOpDesc = _
  var counter: Int = 0

  def linkID(): LinkIdentity = LinkIdentity(Option(layerID()), Option(layerID()))

  def layerID(): LayerIdentity = {
    counter += 1
    LayerIdentity("" + counter, "" + counter, "" + counter)
  }

  def newTuple[T](name: String, n: Int = 1, i: T, attributeType: AttributeType): Tuple = {
    Tuple
      .newBuilder(schema(name, attributeType, n))
      .add(new Attribute(name, attributeType), i)
      .add(new Attribute(name + "_" + 1, attributeType), i)
      .build()
  }
  def intergerTuple(name: String, n: Int = 1, i: Int): Tuple = {
    Tuple
      .newBuilder(schema(name, AttributeType.INTEGER, n))
      .add(new Attribute(name, AttributeType.INTEGER), i)
      .add(new Attribute(name + "_" + 1, AttributeType.INTEGER), i)
      .build()
  }
  def doubleTuple(name: String, n: Int = 1, i: Double): Tuple = {
    Tuple
      .newBuilder(schema(name, AttributeType.DOUBLE, n))
      .add(new Attribute(name, AttributeType.DOUBLE), i)
      .add(new Attribute(name + "_" + 1, AttributeType.DOUBLE), i)
      .build()
  }

  def schema(name: String, attributeType: AttributeType, n: Int = 1): Schema = {
    Schema
      .newBuilder()
      .add(
        new Attribute(name, attributeType),
        new Attribute(name + "_" + n, attributeType)
      )
      .build()
  }

  def longTuple(name: String, n: Int = 1, i: Long): Tuple = {
    Tuple
      .newBuilder(schema(name, AttributeType.LONG, n))
      .add(new Attribute(name, AttributeType.LONG), i)
      .add(new Attribute(name + "_" + 1, AttributeType.LONG), i)
      .build()
  }

  def timeStampTuple(name: String, n: Int = 1, i: Timestamp): Tuple = {
    Tuple
      .newBuilder(schema(name, AttributeType.TIMESTAMP, n))
      .add(new Attribute(name, AttributeType.TIMESTAMP), i)
      .add(new Attribute(name + "_" + 1, AttributeType.TIMESTAMP), i)
      .build()
  }

  def bruteForceJoin[T](
      leftInput: Array[T],
      rightInput: Array[T],
      includeLeftBound: Boolean,
      includeRightBound: Boolean,
      constant: Long,
      dataType: AttributeType,
      timeIntervalType: TimeIntervalType = TimeIntervalType.DAY
  ): Int = {
    var resultSize: Int = 0
    for (k <- leftInput.indices) {
      for (i <- rightInput.indices) {
        dataType match {
          case AttributeType.INTEGER => {
            if (
              compare(
                leftInput(k).asInstanceOf[Int].toLong,
                rightInput(i).asInstanceOf[Int].toLong,
                rightInput(i).asInstanceOf[Int].toLong + constant,
                includeLeftBound,
                includeRightBound
              )
            ) {
              resultSize += 1
            }
          }
          case AttributeType.LONG => {
            if (
              compare(
                leftInput(k).asInstanceOf[Long],
                rightInput(i).asInstanceOf[Long],
                rightInput(i).asInstanceOf[Long] + constant,
                includeLeftBound,
                includeRightBound
              )
            ) {
              resultSize += 1
            }
          }
          case AttributeType.TIMESTAMP => {
            val leftBoundValue: Long = rightInput(i).asInstanceOf[Timestamp].getTime
            val rightBoundValue: Long =
              timeIntervalType match {
                case TimeIntervalType.YEAR =>
                  Timestamp
                    .valueOf(
                      rightInput(i).asInstanceOf[Timestamp].toLocalDateTime.plusYears(constant)
                    )
                    .getTime
                case TimeIntervalType.MONTH =>
                  Timestamp
                    .valueOf(
                      rightInput(i).asInstanceOf[Timestamp].toLocalDateTime.plusMonths(constant)
                    )
                    .getTime
                case TimeIntervalType.DAY =>
                  Timestamp
                    .valueOf(
                      rightInput(i).asInstanceOf[Timestamp].toLocalDateTime.plusDays(constant)
                    )
                    .getTime
                case TimeIntervalType.HOUR =>
                  Timestamp
                    .valueOf(
                      rightInput(i).asInstanceOf[Timestamp].toLocalDateTime.plusHours(constant)
                    )
                    .getTime
                case TimeIntervalType.MINUTE =>
                  Timestamp
                    .valueOf(
                      rightInput(i).asInstanceOf[Timestamp].toLocalDateTime.plusMinutes(constant)
                    )
                    .getTime
                case TimeIntervalType.SECOND =>
                  Timestamp
                    .valueOf(
                      rightInput(i).asInstanceOf[Timestamp].toLocalDateTime.plusSeconds(constant)
                    )
                    .getTime
              }
            if (
              compare(
                leftInput(k).asInstanceOf[Timestamp].getTime,
                leftBoundValue,
                rightBoundValue,
                includeLeftBound,
                includeRightBound
              )
            ) {
              resultSize += 1
            }

          }
        }
      }
    }
    resultSize
  }

  def compare(
      input1: Long,
      leftBound: Long,
      rightBound: Long,
      includeLeftBound: Boolean,
      includeRightBound: Boolean
  ): Boolean = {
    if (includeLeftBound && includeRightBound) {
      input1 >= leftBound && input1 <= rightBound
    } else if (includeLeftBound && !includeRightBound) {
      input1 >= leftBound && input1 < rightBound
    } else if (!includeLeftBound && includeRightBound) {
      input1 > leftBound && input1 <= rightBound
    } else {
      input1 > leftBound && input1 < rightBound
    }
  }

  def testJoin[T](
      leftKey: String,
      rightKey: String,
      includeLeftBound: Boolean,
      includeRightBound: Boolean,
      dataType: AttributeType,
      timeIntervalType: TimeIntervalType,
      intervalConstant: Long,
      leftInput: Array[T],
      rightInput: Array[T]
  ): Unit = {
    val inputSchemas =
      Array(schema(leftKey, dataType), schema(rightKey, dataType))
    opDesc = new IntervalJoinOpDesc(
      left,
      leftKey,
      rightKey,
      inputSchemas,
      intervalConstant,
      includeLeftBound,
      includeRightBound,
      timeIntervalType
    )
    val outputSchema = opDesc.getOutputSchema(inputSchemas)
    val opExec = new IntervalJoinOpExec(
      OperatorSchemaInfo(inputSchemas, outputSchema),
      opDesc
    )
    opExec.open()
    counter = 0
    var leftIndex: Int = 0
    var rightIndex: Int = 0
    var leftOrder = Stream.continually(nextInt(10)).take(leftInput.size).toList
    var rightOrder = Stream.continually(nextInt(10)).take(rightInput.size).toList
    var outputTuples: ArrayBuffer[Tuple] = new ArrayBuffer[Tuple]

    while (leftIndex < leftOrder.size || rightIndex < rightOrder.size) {
      if (
        leftIndex < leftOrder.size && (rightIndex >= rightOrder.size || leftOrder(
          leftIndex
        ) < rightOrder(rightIndex))
      ) {
        var result = opExec
          .processTexeraTuple(Left(newTuple[T](leftKey, 1, leftInput(leftIndex), dataType)), left)
          .toBuffer
        outputTuples.appendAll(
          result
        )
        leftIndex += 1
      } else if (rightIndex < rightOrder.size) {
        var result = opExec
          .processTexeraTuple(Left(newTuple(rightKey, 1, rightInput(rightIndex), dataType)), right)
          .toBuffer
        outputTuples.appendAll(
          result
        )
        rightIndex += 1
      }
    }
    val bruteForceResult: Int = bruteForceJoin(
      leftInput,
      rightInput,
      includeLeftBound,
      includeRightBound,
      intervalConstant,
      dataType
    )
    assert(outputTuples.size == bruteForceResult)
    assert(opExec.processTexeraTuple(Right(InputExhausted()), left).isEmpty)
    assert(opExec.processTexeraTuple(Right(InputExhausted()), right).isEmpty)
    if (outputTuples.nonEmpty)
      assert(outputTuples.head.getSchema.getAttributeNames.size() == 4)
    opExec.close()
  }
  it should "random order test" in {

    var pointList: Array[Long] = (1L to 10L).toArray[Long]
    var rangeList: Array[Long] = Array(1L, 5L, 8L)
    testJoin[Long](
      "point",
      "range",
      true,
      true,
      AttributeType.LONG,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }
  it should "work with Integer value int [] interval, simple test" in {
    var pointList: Array[Int] = (1 to 10).toArray[Int]
    var rangeList: Array[Int] = Array(1, 5, 8)
    testJoin[Int](
      "point",
      "range",
      true,
      true,
      AttributeType.INTEGER,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }

  it should "work with Integer value int [] interval, same key" in {

    var pointList: Array[Int] = (1 to 10).toArray[Int]
    var rangeList: Array[Int] = Array(1, 5, 8)
    testJoin[Int](
      "same",
      "same",
      true,
      true,
      AttributeType.INTEGER,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }

  it should "work with Integer value int [) interval" in {

    var pointList: Array[Int] = (1 to 10).toArray[Int]
    var rangeList: Array[Int] = Array(1, 5, 8)
    testJoin[Int](
      "point",
      "range",
      true,
      false,
      AttributeType.INTEGER,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }

  it should "work with Integer value int (] interval" in {

    var pointList: Array[Int] = (1 to 10).toArray[Int]
    var rangeList: Array[Int] = Array(1, 5, 8)
    testJoin[Int](
      "point",
      "range",
      false,
      true,
      AttributeType.INTEGER,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }
  it should "work with Integer value int () interval" in {

    var pointList: Array[Int] = (1 to 10).toArray[Int]
    var rangeList: Array[Int] = Array(1, 5, 8)
    testJoin[Int](
      "point",
      "range",
      false,
      false,
      AttributeType.INTEGER,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }
  it should "work with Timestamp value int [] interval" in {
    var pointList: Array[Timestamp] = (1L to 10L).map(i => { new Timestamp(i) }).toArray[Timestamp]
    var rangeList: Array[Timestamp] = Array(1, 5, 8).map(i => {
      new Timestamp(i)
    })
    testJoin[Timestamp](
      "point",
      "range",
      false,
      true,
      AttributeType.TIMESTAMP,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }

  it should "work with Double value int [] interval" in {
    val inputSchemas =
      Array(schema("point", AttributeType.DOUBLE), schema("range", AttributeType.DOUBLE))

    var opDesc = new IntervalJoinOpDesc(
      left,
      "point_1",
      "range_1",
      inputSchemas,
      3,
      true,
      true,
      TimeIntervalType.DAY
    )
    val outputSchema = opDesc.getOutputSchema(inputSchemas)
    var opExec = new IntervalJoinOpExec(
      OperatorSchemaInfo(inputSchemas, outputSchema),
      opDesc
    )

    opExec.open()
    counter = 0
    var pointList: Array[Double] = Array(1.1, 2.1, 3.1, 4.1, 5.1, 6.1, 7.1, 8.1, 9.1, 10.1)
    pointList.foreach(i => {
      assert(
        opExec.processTexeraTuple(Left(doubleTuple("point", 1, i)), left).isEmpty
      )
    })
    assert(opExec.processTexeraTuple(Right(InputExhausted()), left).isEmpty)
    var rangeList: Array[Double] = Array(1.1, 5.1, 8.1)
    val outputTuples = rangeList
      .map(i => opExec.processTexeraTuple(Left(doubleTuple("range", 1, i)), right))
      .foldLeft(Iterator[Tuple]())(_ ++ _)
      .toList
    assert(outputTuples.size == 11)
    assert(outputTuples.head.getSchema.getAttributeNames.size() == 4)
    opExec.close()
  }

  it should "work with Long value int [] interval" in {

    var pointList: Array[Long] = (1L to 10L).toArray
    var rangeList: Array[Long] = Array(1L, 5L, 8L).toArray
    testJoin[Long](
      "point",
      "range",
      true,
      true,
      AttributeType.LONG,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }

  it should "work with basic two input streams with left empty table" in {

    var pointList: Array[Long] = Array()
    var rangeList: Array[Long] = Array(1L, 5L, 8L).toArray
    testJoin[Long](
      "point",
      "range",
      true,
      true,
      AttributeType.LONG,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }

  it should "work with basic two input streams with right empty table" in {
    var pointList: Array[Long] = (1L to 10L).toArray
    var rangeList: Array[Long] = Array().toArray
    testJoin[Long](
      "point",
      "range",
      true,
      true,
      AttributeType.LONG,
      TimeIntervalType.DAY,
      3,
      pointList,
      rangeList
    )
  }

  it should "test larger dataset(1k)" in {
    var pointList: Array[Long] = Stream.continually(nextLong()).take(1000).toArray
    var rangeList: Array[Long] = Stream.continually(nextLong()).take(1000).toArray
    testJoin[Long](
      "point",
      "range",
      true,
      true,
      AttributeType.LONG,
      TimeIntervalType.DAY,
      nextInt(1000).toLong,
      pointList,
      rangeList
    )
  }

}
