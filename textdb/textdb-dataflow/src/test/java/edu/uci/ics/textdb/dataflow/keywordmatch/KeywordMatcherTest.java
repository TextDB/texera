package edu.uci.ics.textdb.dataflow.keywordmatch;

import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import edu.uci.ics.textdb.api.common.IPredicate;
import edu.uci.ics.textdb.api.common.ITuple;
import edu.uci.ics.textdb.api.dataflow.ISourceOperator;
import edu.uci.ics.textdb.api.storage.IDataReader;
import edu.uci.ics.textdb.api.storage.IDataStore;
import edu.uci.ics.textdb.api.storage.IDataWriter;
import edu.uci.ics.textdb.common.constants.LuceneConstants;
import edu.uci.ics.textdb.common.constants.TestConstants;
import edu.uci.ics.textdb.dataflow.common.KeywordPredicate;
import edu.uci.ics.textdb.dataflow.source.ScanBasedSourceOperator;
import edu.uci.ics.textdb.dataflow.utils.TestUtils;
import edu.uci.ics.textdb.storage.LuceneDataStore;
import edu.uci.ics.textdb.storage.reader.LuceneDataReader;
import edu.uci.ics.textdb.storage.writer.LuceneDataWriter;

/**
 * Created by akshay on 4/17/16.
 */
public class KeywordMatcherTest {
    
    private KeywordMatcher keywordMatcher;
    private IDataWriter dataWriter;
    private IDataReader dataReader;
    private IDataStore dataStore;
    
    @Before
    public void setUp() throws Exception{
        dataStore = new LuceneDataStore(LuceneConstants.INDEX_DIR, TestConstants.SAMPLE_SCHEMA_PEOPLE);
        dataWriter = new LuceneDataWriter(dataStore);
        dataWriter.clearData();
        dataWriter.writeData(TestConstants.getSamplePeopleTuples());
        dataReader = new LuceneDataReader(dataStore,LuceneConstants.SCAN_QUERY, 
            TestConstants.SAMPLE_SCHEMA_PEOPLE.get(0).getFieldName());
    }
    
    @After
    public void cleanUp() throws Exception{
        dataWriter.clearData();
    }
    
    @Test
    public void testGetNextTuple() throws Exception{
        String keyword = "chris"; //matches chris and christian
        String fieldName = TestConstants.FIRST_NAME;
        IPredicate predicate = new KeywordPredicate(keyword, fieldName);
        ISourceOperator sourceOperator = new ScanBasedSourceOperator(dataReader);
        List<ITuple> tuples = TestConstants.getSamplePeopleTuples();
        
        keywordMatcher = new KeywordMatcher(predicate, sourceOperator);
        keywordMatcher.open();
        ITuple nextTuple = null;
        int numTuples = 0;
        while((nextTuple = keywordMatcher.getNextTuple()) != null){
            boolean contains = TestUtils.contains(tuples, nextTuple);
            Assert.assertTrue(contains);
            numTuples ++;
        }
        Assert.assertEquals(2, numTuples);
        keywordMatcher.close();
    }

}