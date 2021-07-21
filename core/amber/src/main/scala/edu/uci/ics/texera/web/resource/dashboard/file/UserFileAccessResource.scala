package edu.uci.ics.texera.web.resource.dashboard.file

import edu.uci.ics.texera.web.SqlServer
import edu.uci.ics.texera.web.model.jooq.generated.Tables.{FILE, USER_FILE_ACCESS}
import edu.uci.ics.texera.web.model.jooq.generated.tables.daos.{FileDao, UserDao, UserFileAccessDao}
import edu.uci.ics.texera.web.model.jooq.generated.tables.pojos.UserFileAccess
import edu.uci.ics.texera.web.resource.auth.UserResource
import io.dropwizard.jersey.sessions.Session
import org.jooq.DSLContext
import org.jooq.types.UInteger

import javax.servlet.http.HttpSession
import javax.ws.rs._
import javax.ws.rs.core.{MediaType, Response}
import scala.collection.JavaConverters._

object FileAccessUtils {
  final private val fileDao = new FileDao(SqlServer.createDSLContext.configuration)
  final private val userDao = new UserDao(SqlServer.createDSLContext().configuration)

  def hasAccessTo(uid: UInteger, fid: UInteger): Boolean = {
    return SqlServer
      .createDSLContext()
      .fetchExists(
        SqlServer.createDSLContext
          .selectFrom(USER_FILE_ACCESS)
          .where(USER_FILE_ACCESS.UID.eq(uid).and(USER_FILE_ACCESS.FID.eq(fid)))
      )
  }

  def getFileId(ownername: String, filename: String): UInteger = {
    val uid = userDao.fetchByName(ownername).get(0).getUid
    val file = SqlServer
      .createDSLContext()
      .select(FILE.FID)
      .from(FILE)
      .where(FILE.UID.eq(uid).and(FILE.NAME.eq(filename)))
      .fetch()
    return file.getValue(0, 0).asInstanceOf[UInteger]
  }

  def getUidOfUser(username: String): UInteger = {
    userDao.fetchByName(username).get(0).getUid
  }
}

case class FileAccess(username: String, fileAccess: String)

@Path("/user-file-access")
@Consumes(Array(MediaType.APPLICATION_JSON))
@Produces(Array(MediaType.APPLICATION_JSON))
class UserFileAccessResource {
  final private val fileDao = new FileDao(SqlServer.createDSLContext.configuration)
  final private val userFileAccessDao = new UserFileAccessDao(
    SqlServer.createDSLContext.configuration
  )
  final private val userDao = new UserDao(SqlServer.createDSLContext().configuration)
  private var context: DSLContext = SqlServer.createDSLContext

  @GET
  @Path("list/{fid}")
  def getAllSharedFileAccess(
      @PathParam("fid") fid: UInteger,
      @Session session: HttpSession
  ): Response = {
    UserResource.getUser(session) match {
      case Some(user) =>
        val fileAccess = SqlServer
          .createDSLContext()
          .select(USER_FILE_ACCESS.UID, USER_FILE_ACCESS.READ_ACCESS, USER_FILE_ACCESS.WRITE_ACCESS)
          .from(USER_FILE_ACCESS)
          .where(USER_FILE_ACCESS.FID.eq(fid))
          .fetch()
        Response
          .ok(
            fileAccess
              .getValues(0)
              .asScala
              .toList
              .zipWithIndex
              .map({
                case (uid, index) =>
                  val uname = userDao.fetchOneByUid(uid.asInstanceOf[UInteger]).getName
                  if (fileAccess.getValue(index, 2) == true) {
                    FileAccess(uname, "Write")
                  } else {
                    FileAccess(uname, "Read")
                  }
              })
          )
          .build()
      case None =>
        Response.status(Response.Status.UNAUTHORIZED).entity("Please Login").build()
    }
  }

  @GET
  @Path("hasAccess/{uid}/{fid}")
  def hasAccessTo(
      @PathParam("uid") uid: UInteger,
      @PathParam("fid") fid: UInteger,
      @Session session: HttpSession
  ): Response = {
    UserResource.getUser(session) match {
      case Some(user) =>
        val existence = SqlServer
          .createDSLContext()
          .fetchExists(
            SqlServer.createDSLContext
              .selectFrom(USER_FILE_ACCESS)
              .where(USER_FILE_ACCESS.UID.eq(uid).and(USER_FILE_ACCESS.FID.eq(fid)))
          )
        if (existence) {
          Response.ok().entity().build()
        } else {
          Response.status(Response.Status.BAD_REQUEST).entity("user has no access to file").build()
        }
      case None => Response.status(Response.Status.UNAUTHORIZED).entity("please login").build()
    }
  }

  @POST
  @Path("grant/{fid}/{username}/{accessType}")
  def shareFileTo(
      @PathParam("username") username: String,
      @PathParam("fid") fid: UInteger,
      @PathParam("accessType") accessType: String,
      @Session session: HttpSession
  ): Response = {
    UserResource.getUser(session) match {
      case Some(user) =>
        val uid: UInteger =
          try {
            userDao.fetchByName(username).get(0).getUid
          } catch {
            case _: NullPointerException =>
              return Response
                .status(Response.Status.BAD_REQUEST)
                .entity("Target User Does Not Exist")
                .build()
          }

        if (FileAccessUtils.hasAccessTo(uid, fid)) {
          if (accessType == "read") {
            userFileAccessDao.update(new UserFileAccess(uid, fid, true, false))
          } else {
            userFileAccessDao.update(new UserFileAccess(uid, fid, true, true))
          }
          Response.ok().build()
        } else {
          if (accessType == "read") {
            userFileAccessDao.insert(new UserFileAccess(uid, fid, true, false))
          } else {
            userFileAccessDao.insert(new UserFileAccess(uid, fid, true, true))
          }
          Response.ok().build()
        }
    }
  }

  @POST
  @Path("/revoke/{fid}/{username}")
  def revokeAccess(
      @PathParam("fid") fid: UInteger,
      @PathParam("username") username: String,
      @Session session: HttpSession
  ): Response = {
    UserResource.getUser(session) match {
      case Some(user) =>
        val uid: UInteger =
          try {
            userDao.fetchByName(username).get(0).getUid
          } catch {
            case _: NullPointerException =>
              return Response
                .status(Response.Status.BAD_REQUEST)
                .entity("Target User Does Not Exist")
                .build()
          }
        SqlServer
          .createDSLContext()
          .deleteFrom(USER_FILE_ACCESS)
          .where(USER_FILE_ACCESS.UID.eq(uid).and(USER_FILE_ACCESS.FID.eq(fid)))
          .execute()
        Response.ok().build()
      case None =>
        Response.status(Response.Status.UNAUTHORIZED).entity("please login").build()
    }
  }
}
