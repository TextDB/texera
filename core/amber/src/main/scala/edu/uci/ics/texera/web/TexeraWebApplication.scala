package edu.uci.ics.texera.web

import akka.actor.ActorSystem
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.github.dirkraft.dropwizard.fileassets.FileAssetsBundle
import edu.uci.ics.amber.engine.common.AmberUtils
import edu.uci.ics.texera.web.resource.{UserDictionaryResource, _}
import edu.uci.ics.texera.web.resource.auth.UserResource
import edu.uci.ics.texera.web.resource.dashboard.file.{UserFileAccessResource, UserFileResource}
import edu.uci.ics.texera.web.resource.dashboard.{WorkflowAccessResource, WorkflowResource}
import edu.uci.ics.texera.workflow.common.Utils
import io.dropwizard.setup.{Bootstrap, Environment}
import io.dropwizard.websockets.WebsocketBundle
import org.eclipse.jetty.server.session.SessionHandler
import org.eclipse.jetty.servlet.ErrorPageErrorHandler
import org.eclipse.jetty.websocket.server.WebSocketUpgradeFilter
import org.glassfish.jersey.media.multipart.MultiPartFeature

import java.time.Duration

object TexeraWebApplication {

  var actorSystem: ActorSystem = _

  def main(args: Array[String]): Unit = {
    // start actor system master node
    actorSystem = AmberUtils.startActorMaster(true)

    // start web server
    val server = "server"
    val serverConfig =
      Utils.amberHomePath.resolve("../conf").resolve("web-config.yml").toString
    new TexeraWebApplication().run(server, serverConfig)
  }
}

class TexeraWebApplication extends io.dropwizard.Application[TexeraWebConfiguration] {

  override def initialize(bootstrap: Bootstrap[TexeraWebConfiguration]): Unit = {
    // serve static frontend GUI files
    bootstrap.addBundle(new FileAssetsBundle("../new-gui/dist", "/", "index.html"))
    // add websocket bundle
    bootstrap.addBundle(new WebsocketBundle(classOf[WorkflowWebsocketResource]))
    // register scala module to dropwizard default object mapper
    bootstrap.getObjectMapper.registerModule(DefaultScalaModule)
  }

  override def run(configuration: TexeraWebConfiguration, environment: Environment): Unit = {
    // serve backend at /api
    environment.jersey.setUrlPattern("/api/*")

    // redirect all 404 to index page, according to Angular routing requirements
    val eph = new ErrorPageErrorHandler
    eph.addErrorPage(404, "/")
    environment.getApplicationContext.setErrorHandler(eph)

    val webSocketUpgradeFilter =
      WebSocketUpgradeFilter.configureContext(environment.getApplicationContext)
    webSocketUpgradeFilter.getFactory.getPolicy.setIdleTimeout(Duration.ofHours(1).toMillis)
    environment.getApplicationContext.setAttribute(
      classOf[WebSocketUpgradeFilter].getName,
      webSocketUpgradeFilter
    )

    // add HTTPSessionInitializer to create HTTPSession if not presented in Websocket handshake
    environment.getApplicationContext.addEventListener(new HTTPSessionInitializer)

    // register SessionHandler
    environment.jersey.register(classOf[SessionHandler])
    environment.servlets.setSessionHandler(new SessionHandler)

    // register MultiPartFeature
    environment.jersey.register(classOf[MultiPartFeature])

    environment.jersey().register(classOf[SystemMetadataResource])
    //    environment.jersey().register(classOf[MockKillWorkerResource])
    environment.jersey().register(classOf[SchemaPropagationResource])
    environment.jersey().register(classOf[UserResource])
    environment.jersey().register(classOf[UserDictionaryResource])
    environment.jersey().register(classOf[UserFileAccessResource])
    environment.jersey().register(classOf[UserFileResource])
    environment.jersey().register(classOf[WorkflowAccessResource])
    environment.jersey().register(classOf[WorkflowResource])

  }

}
