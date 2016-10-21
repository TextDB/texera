package edu.uci.ics.textdb.web.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.uci.ics.textdb.web.request.QueryPlanRequest;
import edu.uci.ics.textdb.web.request.beans.*;
import edu.uci.ics.textdb.web.response.SampleResponse;

import javax.json.JsonObject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;

/**
 * This class will be the resource class for accepting a query plan request and executing the
 * query plan to get the query response
 * Created by kishorenarendran on 10/17/16.
 */
@Path("/queryplan")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class QueryPlanResource {

    @POST
    @Path("/execute")
    public Response executeQueryPlan(QueryPlanRequest queryPlanRequest) throws Exception {
        // Aggregating all the operator properties in the query plan input
        HashMap<String, HashMap<String, String>> operatorProperties = QueryPlanResourceUtils.aggregateOperatorProperties(queryPlanRequest.getOperators());

        if(operatorProperties != null) {
            // Temporary sample response when the operator properties aggregation works correctly
            SampleResponse sampleResponse = new SampleResponse(0, "Successful");
            ObjectMapper objectMapper = new ObjectMapper();
            return Response.status(200)
                    .entity(objectMapper.writeValueAsString(sampleResponse))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        else {
            SampleResponse sampleResponse = new SampleResponse(1, "Unsuccessful");
            return Response.status(400)
                    .build();
        }
    }
}
