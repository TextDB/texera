package edu.uci.ics.texera.workflow.operators.pythonUDF;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.kjetland.jackson.jsonSchema.annotations.JsonSchemaTitle;
import edu.uci.ics.amber.engine.common.Constants;
import edu.uci.ics.amber.engine.operators.OpExecConfig;
import edu.uci.ics.texera.workflow.common.metadata.InputPort;
import edu.uci.ics.texera.workflow.common.metadata.OperatorGroupConstants;
import edu.uci.ics.texera.workflow.common.metadata.OperatorInfo;
import edu.uci.ics.texera.workflow.common.metadata.OutputPort;
import edu.uci.ics.texera.workflow.common.operators.OperatorDescriptor;
import edu.uci.ics.texera.workflow.common.tuple.schema.Attribute;
import edu.uci.ics.texera.workflow.common.tuple.schema.AttributeType;
import edu.uci.ics.texera.workflow.common.tuple.schema.Schema;
import scala.collection.JavaConverters;

import java.util.List;

import static edu.uci.ics.texera.workflow.operators.pythonUDF.PythonUDFType.SupervisedTraining;
import static edu.uci.ics.texera.workflow.operators.pythonUDF.PythonUDFType.UnsupervisedTraining;
import static java.util.Collections.singletonList;
import static scala.collection.JavaConverters.asScalaBuffer;


public class PythonUDFOpDesc extends OperatorDescriptor {

    @JsonProperty()
    @JsonSchemaTitle("Python script")
    @JsonPropertyDescription("input your code here")
    public String pythonScriptText;

    @JsonProperty()
    @JsonSchemaTitle("Python script file")
    @JsonPropertyDescription("name of the UDF script file")
    public String pythonScriptFile;

    @JsonProperty()
    @JsonSchemaTitle("Input column(s)")
    @JsonPropertyDescription("name of the input column(s) that the UDF will use, if any")
    public List<String> inputColumns;

    @JsonProperty(required = true)
    @JsonSchemaTitle("pythonUDFType")
    public PythonUDFType pythonUDFType;

    @JsonProperty()
    @JsonSchemaTitle("Extra output column(s)")
    @JsonPropertyDescription("name of the newly added output columns that the UDF will produce, if any")
    public List<Attribute> outputColumns;

    @JsonProperty()
    @JsonSchemaTitle("Arguments")
    @JsonPropertyDescription("arguments to be passed, if any")
    public List<String> arguments;


    @JsonProperty()
    @JsonSchemaTitle("Outer file(s)")
    @JsonPropertyDescription("name(s) of outer file(s) to be used, if any")
    public List<String> outerFiles;


    @JsonProperty(required = true, defaultValue = "100")
    @JsonSchemaTitle("Batch size")
    @JsonPropertyDescription("size of every batch of tuples to pass to python")
    public int batchSize;


    @Override
    public OpExecConfig operatorExecutor() {
        return new PythonUDFOpExecConfig(this.operatorIdentifier(),
                // changed it to 1 because training with python needs all data in one node.
                PythonUDFType.supportsParallel.contains(pythonUDFType) ? Constants.defaultNumWorkers() : 1,
                pythonScriptText,
                pythonScriptFile,
                JavaConverters.asScalaIteratorConverter(this.inputColumns.iterator()).asScala().toBuffer(),
                JavaConverters.asScalaIteratorConverter(this.outputColumns.iterator()).asScala().toBuffer(),
                JavaConverters.asScalaIteratorConverter(this.arguments.iterator()).asScala().toBuffer(),
                JavaConverters.asScalaIteratorConverter(this.outerFiles.iterator()).asScala().toBuffer(),
                batchSize);
    }

    @Override
    public OperatorInfo operatorInfo() {
        return new OperatorInfo(
                "Python UDF",
                "User-defined function operator in Python script",
                OperatorGroupConstants.UDF_GROUP(),
                asScalaBuffer(singletonList(new InputPort("", false))).toList(),
                asScalaBuffer(singletonList(new OutputPort(""))).toList());
    }

    @Override
    public Schema getOutputSchema(Schema[] schemas) {
        Schema inputSchema = schemas[0];

        // check if inputColumns are presented in inputSchema.
        if (inputColumns != null) {
            for (String s : inputColumns) {
                if (!inputSchema.containsAttribute(s)) throw new RuntimeException("No such column:" + s + ".");
            }
        }

        Schema.Builder outputSchemaBuilder = Schema.newBuilder();
        if (pythonUDFType == SupervisedTraining) {
            outputSchemaBuilder.add("class", AttributeType.STRING);
            outputSchemaBuilder.add("precision", AttributeType.STRING);
            outputSchemaBuilder.add("recall", AttributeType.STRING);
            outputSchemaBuilder.add("f1-score", AttributeType.STRING);
            outputSchemaBuilder.add("support", AttributeType.STRING);
        } else if (pythonUDFType == UnsupervisedTraining) {
            outputSchemaBuilder.add("output", AttributeType.STRING);
        } else {
            // for pythonUDFType with map and filter, keep the same schema from input
            outputSchemaBuilder.add(inputSchema);
        }

        // for any pythonUDFType, it can add custom output columns (attributes).
        if (outputColumns != null) {
            for (Attribute a : outputColumns) {
                if (inputSchema.containsAttribute(a.getName())) throw new RuntimeException("Column name " + a.getName()
                        + " already exists!");
            }
            outputSchemaBuilder.add(outputColumns).build();
        }
        return outputSchemaBuilder.build();
    }
}
