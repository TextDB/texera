package edu.uci.ics.texera.workflow.operators.typecasting

import edu.uci.ics.texera.workflow.common.operators.map.MapOpExec
import edu.uci.ics.texera.workflow.common.tuple.Tuple
import edu.uci.ics.texera.workflow.common.tuple.schema.{AttributeTypeUtils, Schema}

class TypeCastingOpExec(val castToSchema: Schema) extends MapOpExec {
  this.setMapFunc(this.processTuple)
  def processTuple(tuple: Tuple): Tuple = AttributeTypeUtils.TupleCasting(tuple, castToSchema)
}
