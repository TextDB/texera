package edu.uci.ics.texera.web.resource.dashboard

import edu.uci.ics.texera.web.SqlServer
import edu.uci.ics.texera.web.model.jooq.generated.Tables.{
  WORKFLOW,
  WORKFLOW_OF_USER,
  WORKFLOW_USER_ACCESS
}
import edu.uci.ics.texera.web.model.jooq.generated.tables.daos.{
  WorkflowDao,
  WorkflowOfUserDao,
  WorkflowUserAccessDao
}
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.{
  User,
  Workflow,
  WorkflowOfUser,
  WorkflowUserAccess
}
import edu.uci.ics.texera.web.resource.auth.UserResource
import io.dropwizard.jersey.sessions.Session
import org.jooq.types.UInteger

import java.util
import javax.servlet.http.HttpSession
import javax.ws.rs._
import javax.ws.rs.core.{MediaType, Response}

/**
  * This file handles various request related to saved-workflows.
  * It sends mysql queries to the MysqlDB regarding the UserWorkflow Table
  * The details of UserWorkflowTable can be found in /core/scripts/sql/texera_ddl.sql
  */
@Path("/workflow")
@Produces(Array(MediaType.APPLICATION_JSON))
class WorkflowResource {
  final private val workflowDao = new WorkflowDao(SqlServer.createDSLContext.configuration)
  final private val workflowOfUserDao = new WorkflowOfUserDao(
    SqlServer.createDSLContext.configuration
  )
  final private val workflowUserAccessDao = new WorkflowUserAccessDao(
    SqlServer.createDSLContext().configuration()
  )

  /**
    * This method returns the current in-session user's workflow list based on all workflows he/she has access to
    *
    * @param session HttpSession
    * @return Workflow[]
    */
  @GET
  @Path("/list")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def retrieveWorkflowsBySessionUser(@Session session: HttpSession): util.List[Workflow] = {
    UserResource.getUser(session) match {
      case Some(user) =>
        SqlServer.createDSLContext
          .select()
          .from(WORKFLOW)
          .join(WORKFLOW_USER_ACCESS)
          .on(WORKFLOW_USER_ACCESS.WID.eq(WORKFLOW.WID))
          .where(WORKFLOW_USER_ACCESS.UID.eq(user.getUid))
          .fetchInto(classOf[Workflow])

      case None => new util.ArrayList()
    }
  }

  /**
    * This method handles the client request to get a specific workflow to be displayed in canvas
    * at current design, it only takes the workflowID and searches within the database for the matching workflow
    * for future design, it should also take userID as an parameter.
    *
    * @param wid     workflow id, which serves as the primary key in the UserWorkflow database
    * @param session HttpSession
    * @return a json string representing an savedWorkflow
    */
  @GET
  @Path("/{wid}")
  @Produces(Array(MediaType.APPLICATION_JSON))
  def retrieveWorkflow(@PathParam("wid") wid: UInteger, @Session session: HttpSession): Response = {
    UserResource.getUser(session) match {
      case Some(user) =>
        if (
          WorkflowAccessResource.hasNoWorkflowAccess(wid, user.getUid) ||
          WorkflowAccessResource.hasNoWorkflowAccessRecord(wid, user.getUid)
        ) {
          Response.status(Response.Status.UNAUTHORIZED).build()
        } else {
          Response.ok(workflowDao.fetchOneByWid(wid)).build()
        }
      case None =>
        Response.status(Response.Status.UNAUTHORIZED).build()
    }
  }

  /**
    * This method persists the workflow into database
    *
    * @param session  HttpSession
    * @param workflow , a workflow
    * @return Workflow, which contains the generated wid if not provided//
    *         TODO: divide into two endpoints -> one for new-workflow and one for updating existing workflow
    */
  @POST
  @Path("/persist")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def persistWorkflow(@Session session: HttpSession, workflow: Workflow): Response = {
    UserResource.getUser(session) match {
      case Some(user) =>
        if (workflowOfUserExists(workflow.getWid, user.getUid)) {
          // current user reading
          workflowDao.update(workflow)
        } else {
          if (WorkflowAccessResource.hasNoWorkflowAccessRecord(workflow.getWid, user.getUid)) {
            // not owner and not access record --> new record
            insertWorkflow(workflow, user)

          } else if (WorkflowAccessResource.hasWriteAccess(workflow.getWid, user.getUid)) {
            // not owner but has write access
            workflowDao.update(workflow)
          } else {
            // not owner and no write access -> rejected
            Response.status(Response.Status.UNAUTHORIZED).build()
          }
        }
        Response.ok(workflowDao.fetchOneByWid(workflow.getWid)).build()
      case None =>
        Response.status(Response.Status.UNAUTHORIZED).build()
    }
  }

  private def workflowOfUserExists(wid: UInteger, uid: UInteger): Boolean = {
    workflowOfUserDao.existsById(
      SqlServer.createDSLContext
        .newRecord(WORKFLOW_OF_USER.UID, WORKFLOW_OF_USER.WID)
        .values(uid, wid)
    )
  }

  /**
    * This method creates and insert a new workflow to database
    *
    * @param session  HttpSession
    * @param workflow , a workflow
    * @return Workflow, which contains the generated wid if not provided
    */
  @POST
  @Path("/create")
  @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  def createWorkflow(@Session session: HttpSession, workflow: Workflow): Response = {
    UserResource.getUser(session) match {
      case Some(user) =>
        if (workflow.getWid != null) {
          Response.status(Response.Status.BAD_REQUEST).build()
        } else {
          insertWorkflow(workflow, user)
          Response.ok(workflowDao.fetchOneByWid(workflow.getWid)).build()
        }
      case None =>
        Response.status(Response.Status.UNAUTHORIZED).build()
    }
  }

  private def insertWorkflow(workflow: Workflow, user: User): Unit = {
    workflowDao.insert(workflow)
    workflowOfUserDao.insert(new WorkflowOfUser(user.getUid, workflow.getWid))
    workflowUserAccessDao.insert(
      new WorkflowUserAccess(
        user.getUid,
        workflow.getWid,
        true, // readPrivilege
        true // writePrivilege
      )
    )
  }

  /**
    * This method deletes the workflow from database
    *
    * @param session HttpSession
    * @return Response, deleted - 200, not deleted - 304 // TODO: change the error code
    */
  @DELETE
  @Path("/{wid}")
  def deleteWorkflow(@PathParam("wid") wid: UInteger, @Session session: HttpSession): Response = {
    UserResource.getUser(session) match {
      case Some(user) =>
        if (workflowOfUserExists(wid, user.getUid)) {
          workflowDao.deleteById(wid)
          Response.ok().build()
        } else {
          Response.notModified().build()
        }
      case None =>
        Response.status(Response.Status.UNAUTHORIZED).build()
    }
  }

}
