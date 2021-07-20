package edu.uci.ics.texera.workflow.common.tuple.schema
import com.google.common.base.Preconditions
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.AttributeType._

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.time.Instant
import scala.util.Try
import scala.util.control.Exception.allCatch

object AttributeTypeUtils extends Serializable {

  /**
    * this loop check whether the current attribute in the array is the attribute for casting,
    * if it is, change it to result type
    * if it's not, remain the same type
    * we need this loop to keep the order the same as the original
    * @param schema schema of data
    * @param attribute selected attribute
    * @param resultType casting type
    * @return schema of data
    */
  def SchemaCasting(
      schema: Schema,
      attribute: String,
      resultType: AttributeType
  ): Schema = {
    // need a builder to maintain the order of original schema
    val builder = Schema.newBuilder
    val attributes: List[Attribute] = schema.getAttributesScala
    // change the schema when meet selected attribute else remain the same
    for (i <- attributes.indices) {
      if (attributes.apply(i).getName.equals(attribute)) {
        resultType match {
          case STRING | INTEGER | DOUBLE | LONG | BOOLEAN | TIMESTAMP =>
            builder.add(attribute, resultType)
          case ANY | _ =>
            builder.add(attribute, attributes.apply(i).getType)
        }
      } else {
        builder.add(attributes.apply(i).getName, attributes.apply(i).getType)
      }
    }
    builder.build()
  }

  /**
    * Returns a new tuple that has the values casted to the given new schema.
    * @param tuple The tuple to be processed
    * @param schema The new Schema to be casted into, must have same matching attribute names with the Tuple
    * @return a new casted Tuple
    */
  def TupleCasting(
      tuple: Tuple,
      schema: Schema
  ): Tuple = {
    Preconditions.checkArgument(tuple.getSchema.getAttributes.size() == schema.getAttributes.size())

    val builder = Tuple.newBuilder(schema)
    schema.getAttributesScala.map(attr =>
      builder.add(attr, parseField(tuple.getField(attr.getName), attr.getType))
    )
    builder.build()
  }

  /**
    * parse Fields to corresponding Java objects base on the given Schema AttributeTypes
    * @param attributeTypes Schema AttributeTypeList
    * @param fields fields value
    * @return parsedFields in the target AttributeTypes
    */
  @throws[AttributeTypeException]
  def parseFields(
      fields: Array[Object],
      attributeTypes: Array[AttributeType]
  ): Array[Object] = {
    fields.indices.map(i => parseField(fields.apply(i), attributeTypes.apply(i))).toArray
  }

  /**
    * parse Field to a corresponding Java object base on the given Schema AttributeType
    * @param field fields value
    * @param attributeType target AttributeType
    *
    * @return parsedField in the target AttributeType
    */
  @throws[AttributeTypeException]
  def parseField(
      field: Object,
      attributeType: AttributeType
  ): Object = {
    if (field == null) return null
    attributeType match {
      case INTEGER   => parseInteger(field)
      case LONG      => parseLong(field)
      case DOUBLE    => parseDouble(field)
      case BOOLEAN   => parseBoolean(field)
      case TIMESTAMP => parseTimestamp(field)
      case STRING    => field.toString
      case ANY | _   => field
    }
  }

  @throws[AttributeTypeException]
  def parseInteger(fieldValue: Object): Integer = {
    fieldValue match {
      case str: String                => str.trim.toInt
      case int: Integer               => int
      case long: java.lang.Long       => long.toInt
      case double: java.lang.Double   => double.toInt
      case boolean: java.lang.Boolean => if (boolean) 1 else 0
      // Timestamp is considered to be illegal here.
      case _ =>
        throw new AttributeTypeException(
          s"not able to parse type ${fieldValue.getClass} to Integer: ${fieldValue.toString}"
        )
    }
  }

  @throws[AttributeTypeException]
  def parseLong(fieldValue: Object): java.lang.Long = {
    fieldValue match {
      case str: String                => str.trim.toLong
      case int: Integer               => int.toLong
      case long: java.lang.Long       => long
      case double: java.lang.Double   => double.toLong
      case boolean: java.lang.Boolean => if (boolean) 1L else 0L
      case timestamp: Timestamp       => timestamp.toInstant.toEpochMilli
      case _ =>
        throw new AttributeTypeException(
          s"not able to parse type ${fieldValue.getClass} to Long: ${fieldValue.toString}"
        )
    }
  }

  @throws[AttributeTypeException]
  def parseTimestamp(fieldValue: Object): Timestamp = {
    val parseError = new AttributeTypeException(
      s"not able to parse type ${fieldValue.getClass} to Timestamp: ${fieldValue.toString}"
    )
    val datetimeISOFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd")
    fieldValue match {
      case str: String =>
        (
          // support ISO format with UTC {@code 2007-12-03T10:15:30.00Z}
          Try(new Timestamp(Instant.parse(str.trim).toEpochMilli))
            orElse
              // support {@code yyyy-[m]m-[d]d hh:mm:ss[.f...]}
              Try(Timestamp.valueOf(str.trim))
            orElse
              // support ISO format with timezone {@code 2007-12-03T10:15:30.00.000Z}
              Try(new Timestamp(datetimeISOFormat.parse(fieldValue.toString.trim).getTime))
            orElse
              // support date format with timezone {@code 2007-12-03}
              Try(new Timestamp(dateFormat.parse(fieldValue.toString.trim).getTime))
        ).getOrElse(throw parseError)

      case long: java.lang.Long => new Timestamp(long)

      case timestamp: Timestamp => timestamp
      // Integer, Double and Boolean are considered to be illegal here.
      case _ =>
        throw parseError
    }
  }

  @throws[AttributeTypeException]
  def parseDouble(fieldValue: Object): java.lang.Double = {
    fieldValue match {
      case str: String                => str.trim.toDouble
      case int: Integer               => int.toDouble
      case long: java.lang.Long       => long.toDouble
      case double: java.lang.Double   => double
      case boolean: java.lang.Boolean => if (boolean) 1 else 0
      // Timestamp is considered to be illegal here.
      case _ =>
        throw new AttributeTypeException(
          s"not able to parse type ${fieldValue.getClass} to Double: ${fieldValue.toString}"
        )
    }
  }

  @throws[AttributeTypeException]
  def parseBoolean(fieldValue: Object): java.lang.Boolean = {
    val parseError = new AttributeTypeException(
      s"not able to parse type ${fieldValue.getClass} to Boolean: ${fieldValue.toString}"
    )
    fieldValue match {
      case str: String =>
        (Try(str.trim.toBoolean) orElse Try(str.trim.toInt == 1))
          .getOrElse(throw parseError)
      case int: Integer               => int != 0
      case long: java.lang.Long       => long != 0
      case double: java.lang.Double   => double != 0
      case boolean: java.lang.Boolean => boolean
      // Timestamp is considered to be illegal here.
      case _ =>
        throw parseError
    }
  }

  /**
    * Infers field types of a given row of data. The given attributeTypes will be updated
    * through each iteration of row inference, to contain the most accurate inference.
    * @param attributeTypes AttributeTypes that being passed to each iteration.
    * @param fields data fields to be parsed
    * @return
    */
  def inferRow(
      attributeTypes: Array[AttributeType],
      fields: Array[Object]
  ): Unit = {
    for (i <- fields.indices) {
      attributeTypes.update(i, inferField(attributeTypes.apply(i), fields.apply(i)))
    }
  }

  /**
    * Infers field types of a given row of data.
    * @param fieldsIterator iterator of field arrays to be parsed.
    *                       each field array should have exact same order and length.
    * @return AttributeType array
    */
  def inferSchemaFromRows(fieldsIterator: Iterator[Array[Object]]): Array[AttributeType] = {
    var attributeTypes: Array[AttributeType] = Array()

    for (fields <- fieldsIterator) {
      if (attributeTypes.isEmpty) {
        attributeTypes = Array.fill[AttributeType](fields.length)(INTEGER)
      }
      inferRow(attributeTypes, fields)
    }
    attributeTypes
  }

  /**
    * infer filed type with only data field
    * @param fieldValue data field to be parsed, original as String field
    * @return inferred AttributeType
    */
  def inferField(fieldValue: Object): AttributeType = {
    tryParseInteger(fieldValue)
  }

  private def tryParseInteger(fieldValue: Object): AttributeType = {
    if (fieldValue == null)
      return INTEGER
    allCatch opt parseInteger(fieldValue) match {
      case Some(_) => INTEGER
      case None    => tryParseLong(fieldValue)
    }
  }

  private def tryParseLong(fieldValue: Object): AttributeType = {
    if (fieldValue == null)
      return LONG
    allCatch opt parseLong(fieldValue) match {
      case Some(_) => LONG
      case None    => tryParseTimestamp(fieldValue)
    }
  }

  private def tryParseTimestamp(fieldValue: Object): AttributeType = {
    if (fieldValue == null)
      return TIMESTAMP
    allCatch opt parseTimestamp(fieldValue) match {
      case Some(_) => TIMESTAMP
      case None    => tryParseDouble(fieldValue)
    }
  }

  private def tryParseDouble(fieldValue: Object): AttributeType = {
    if (fieldValue == null)
      return DOUBLE
    allCatch opt parseDouble(fieldValue) match {
      case Some(_) => DOUBLE
      case None    => tryParseBoolean(fieldValue)
    }
  }

  private def tryParseBoolean(fieldValue: Object): AttributeType = {
    if (fieldValue == null)
      return BOOLEAN
    allCatch opt parseBoolean(fieldValue) match {
      case Some(_) => BOOLEAN
      case None    => tryParseString()
    }
  }

  private def tryParseString(): AttributeType = {
    STRING
  }

  /**
    * InferField when get both typeSofar and tuple string
    * @param attributeType typeSofar
    * @param fieldValue data field to be parsed, original as String field
    * @return inferred AttributeType
    */
  def inferField(attributeType: AttributeType, fieldValue: Object): AttributeType = {
    attributeType match {
      case STRING    => tryParseString()
      case BOOLEAN   => tryParseBoolean(fieldValue)
      case DOUBLE    => tryParseDouble(fieldValue)
      case LONG      => tryParseLong(fieldValue)
      case INTEGER   => tryParseInteger(fieldValue)
      case TIMESTAMP => tryParseTimestamp(fieldValue)
      case _         => tryParseString()
    }
  }

  class AttributeTypeException(msg: String) extends IllegalArgumentException(msg) {}
}
