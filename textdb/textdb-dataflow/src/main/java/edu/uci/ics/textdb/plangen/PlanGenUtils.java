package edu.uci.ics.textdb.plangen;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import edu.uci.ics.textdb.api.common.FieldType;
import edu.uci.ics.textdb.api.dataflow.IOperator;
import edu.uci.ics.textdb.common.constants.OperatorConstants;
import edu.uci.ics.textdb.common.exception.PlanGenException;
import edu.uci.ics.textdb.plangen.operatorbuilder.DictionaryMatcherBuilder;
import edu.uci.ics.textdb.plangen.operatorbuilder.DictionarySourceBuilder;
import edu.uci.ics.textdb.plangen.operatorbuilder.FileSinkBuilder;
import edu.uci.ics.textdb.plangen.operatorbuilder.FuzzyTokenMatcherBuilder;
import edu.uci.ics.textdb.plangen.operatorbuilder.KeywordMatcherBuilder;
import edu.uci.ics.textdb.plangen.operatorbuilder.KeywordSourceBuilder;
import edu.uci.ics.textdb.plangen.operatorbuilder.NlpExtractorBuilder;
import edu.uci.ics.textdb.plangen.operatorbuilder.RegexMatcherBuilder;

/**
 * This class provides a set of helper functions that are commonly used in plan generation.
 * @author Zuozhi Wang
 *
 */
public class PlanGenUtils {
    
    @FunctionalInterface
    public interface OperatorBuilder {
        public IOperator buildOperator(Map<String, String> operatorProperties) throws Exception;
    }
    
    public static Map<String, OperatorBuilder> operatorBuilderMap = new HashMap<>();
    static {
        operatorBuilderMap.put("KeywordMatcher".toLowerCase(), KeywordMatcherBuilder::buildKeywordMatcher);
        operatorBuilderMap.put("DictionaryMatcher".toLowerCase(), DictionaryMatcherBuilder::buildOperator);
        operatorBuilderMap.put("RegexMatcher".toLowerCase(), RegexMatcherBuilder::buildRegexMatcher);
        operatorBuilderMap.put("NlpExtractor".toLowerCase(), NlpExtractorBuilder::buildOperator);
        operatorBuilderMap.put("FuzzyTokenMatcher".toLowerCase(), FuzzyTokenMatcherBuilder::buildOperator);
        operatorBuilderMap.put("KeywordSource".toLowerCase(), KeywordSourceBuilder::buildSourceOperator);
        operatorBuilderMap.put("DictionarySource".toLowerCase(), DictionarySourceBuilder::buildSourceOperator);
        operatorBuilderMap.put("FileSink".toLowerCase(), FileSinkBuilder::buildSink);
    }
    
    public static IOperator buildOperator(String operatorType, Map<String, String> operatorProperties) throws Exception {
        OperatorBuilder operatorBuilder = operatorBuilderMap.get(operatorType.toLowerCase());
        planGenAssert(operatorBuilder != null, "operatorType is not valid. It must be one of " + OperatorConstants.operatorList);
               
        return operatorBuilder.buildOperator(operatorProperties);
    }
    
    
    public static void planGenAssert(boolean assertBoolean, String errorMessage) throws PlanGenException {
        if (! assertBoolean) {
            throw new PlanGenException(errorMessage);
        }
    }
    
    /**
     * This function checks if a string is a valid operator (case insensitive).
     * 
     * @param operatorStr
     * @return true if the string is an operator
     */
    public static boolean isValidOperator(String operatorStr) {
        return OperatorConstants.operatorList.stream().anyMatch(str -> str.toLowerCase().equals(operatorStr.toLowerCase()));
    }

    /**
     * This function checks if a string is a valid attribute type (case insensitive).
     * 
     * @param attributeType
     * @return true if the string is an attribute type
     */
    public static boolean isValidAttributeType(String attributeType) {
        return Stream.of(FieldType.values()).anyMatch(type -> type.toString().toLowerCase().equals(attributeType.toLowerCase()));
    }
    
    /**
     * This function converts a attributeTypeString to FieldType (case insensitive). 
     * It returns null if string is not a valid type.
     * 
     * @param attributeTypeStr
     * @return FieldType, null if attributeTypeStr is not a valid type.
     */
    public static FieldType convertAttributeType(String attributeTypeStr) {
        return Stream.of(FieldType.values())
                .filter(typeStr -> typeStr.toString().toLowerCase().equals(attributeTypeStr.toLowerCase()))
                .findAny().orElse(null);
    }
    

}
