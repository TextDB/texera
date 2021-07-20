package edu.uci.ics.texera.workflow.operators.typecasting;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;
import edu.uci.ics.texera.workflow.common.metadata.annotations.AutofillAttributeName;
import edu.uci.ics.texera.workflow.common.tuple.schema.AttributeType;

public class TypeCastingUnit {
    @JsonProperty(required = true)
    @JsonSchemaTitle("Attribute")
    @JsonPropertyDescription("Attribute for type casting")
    @AutofillAttributeName
    public String attribute;

    @JsonProperty(required = true)
    @JsonSchemaTitle("Cast type")
    @JsonPropertyDescription("Result type after type casting")
    public AttributeType resultType;
}
