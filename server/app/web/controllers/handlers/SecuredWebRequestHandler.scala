package web.controllers.handlers

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.mohiva.play.silhouette.api.actions.UserAwareRequest
import com.mohiva.play.silhouette.api.{Env, HandlerResult, Silhouette}
import play.api.cache.SyncCacheApi
import play.api.libs.json.Json
import play.api.mvc._
import utils.auth.DefaultEnv
import web.models.{JobValue, MemberValue, UserNotAuthenticated}
import web.models.formats.AuthResponseFormats
import web.models.rbac.{AccessPolicy, MemberProfile, MemberRole, SubjectType}
import web.services.{JobService, MemberService}

import scala.concurrent.{ExecutionContext, Future}

trait SecuredWebRequestHandler extends AuthResponseFormats {

  def handleRequestWithOrg[E <: Env, B](request: UserAwareRequest[DefaultEnv, B], memberService: MemberService, userCache: SyncCacheApi)(
      handler: MemberValue => Future[Result]): Future[Result] = {
    request.identity match {
      case Some(m) =>
        val mv = memberService.getMemberValue(m.id.getOrElse(0), userCache)
        if (mv.orgIds.length > 0) {
          handler(mv)
        } else Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))

      case _ => {
        Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def handleMemberRequest[E <: Env, B](request: UserAwareRequest[DefaultEnv, B], memberService: MemberService)(
      handler: (Seq[MemberRole], MemberProfile) => Future[Result])(implicit ec: ExecutionContext): Future[Result] = {
    request.identity match {
      case Some(m) if m.id.isDefined =>
        for {
          roles   <- memberService.getMemberRoles(m.id.get)
          profile <- memberService.getMemberProfile(m.id.get)
          result <- profile match {
            case None =>
              Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path, message = "Profile not found"))))
            case Some(foundProfile) => handler(roles, foundProfile)
          }
        } yield result

      case _ => {
        Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def usingIdentity[E <: Env, B](memberId: Long, memberService: MemberService)(
      handler: (Seq[MemberRole], MemberProfile) => Future[Either[Result, Flow[Any, String, NotUsed]]])(
      implicit ec: ExecutionContext): Future[Either[Result, Flow[Any, String, NotUsed]]] = {

    for {
      roles   <- memberService.getMemberRoles(memberId)
      profile <- memberService.getMemberProfile(memberId)
      result <- profile match {
        case None =>
          Future.successful(Left(Results.Unauthorized(Json.toJson(UserNotAuthenticated("", message = "Profile not found")))))
        case Some(foundProfile) => handler(roles, foundProfile)
      }
    } yield result

  }

  def hasWorkspaceViewPermission(profile: MemberProfile, roles: Seq[MemberRole], orgId: Long, workspaceId: Long): Boolean = {
    val hasViewRole = roles
      .find(
        p =>
          p.subjectId == profile.workspaceId && p.subjectType == SubjectType.WORKSPACE && (p.policies
            .contains(AccessPolicy.WS_MANAGE) || p.policies
            .contains(AccessPolicy.WS_READ)))
      .isDefined
    hasViewRole && profile.orgId == orgId && profile.workspaceId == workspaceId
  }

  def hasWorkspaceManagePermission(profile: MemberProfile, roles: Seq[MemberRole], orgId: Long, workspaceId: Long): Boolean = {
    val hasViewRole = roles
      .find(
        p =>
          p.subjectId == profile.workspaceId && p.subjectType == SubjectType.WORKSPACE && (p.policies
            .intersect(Seq(AccessPolicy.WS_MANAGE))
            .nonEmpty))
      .isDefined
    hasViewRole && profile.orgId == orgId && profile.workspaceId == workspaceId
  }

  def hasOrgManagePermission(profile: MemberProfile, roles: Seq[MemberRole], orgId: Long, workspaceId: Long): Boolean = {
    val hasViewRole = roles
      .find(
        p =>
          p.subjectId == profile.orgId && p.subjectType == SubjectType.ORG && (p.policies
            .intersect(Seq(AccessPolicy.ORG_MANAGE))
            .nonEmpty))
      .isDefined
    hasViewRole && profile.orgId == orgId && profile.workspaceId == workspaceId
  }

  def handleWorkspaceViewRequest[E <: Env, B](
      request: UserAwareRequest[DefaultEnv, B],
      memberService: MemberService,
      orgId: Long,
      workspaceId: Long)(handler: (Seq[MemberRole], MemberProfile) => Future[Result])(implicit ec: ExecutionContext): Future[Result] = {
    request.identity match {
      case Some(m) if m.id.isDefined =>
        for {
          roles   <- memberService.getMemberRoles(m.id.get)
          profile <- memberService.getMemberProfile(m.id.get)
          result <- profile match {
            case Some(foundProfile) if hasWorkspaceViewPermission(foundProfile, roles, orgId, workspaceId) => handler(roles, foundProfile)
            case _ =>
              Future.successful(Results.Forbidden(Json.toJson(UserNotAuthenticated(request.path, message = "Forbidden for this request"))))

          }
        } yield result

      case _ => {
        Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def handleWorkspaceManageRequest[E <: Env, B](
      request: UserAwareRequest[DefaultEnv, B],
      memberService: MemberService,
      orgId: Long,
      workspaceId: Long)(handler: (Seq[MemberRole], MemberProfile) => Future[Result])(implicit ec: ExecutionContext): Future[Result] = {
    request.identity match {
      case Some(m) if m.id.isDefined =>
        for {
          roles   <- memberService.getMemberRoles(m.id.get)
          profile <- memberService.getMemberProfile(m.id.get)
          result <- profile match {
            case Some(foundProfile) if hasWorkspaceManagePermission(foundProfile, roles, orgId, workspaceId) => handler(roles, foundProfile)
            case _ =>
              Future.successful(Results.Forbidden(Json.toJson(UserNotAuthenticated(request.path, message = "Forbidden for this request"))))

          }
        } yield result

      case _ => {
        Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def handleRequestWithOrgAndJobs[E <: Env, B](
      request: UserAwareRequest[DefaultEnv, B],
      memberService: MemberService,
      userCache: SyncCacheApi,
      jobId: Long,
      jobCache: SyncCacheApi,
      jobService: JobService)(handler: (MemberValue, JobValue) => Future[Result]): Future[Result] = {
    request.identity match {
      case Some(m) =>
        val mv = memberService.getMemberValue(m.id.getOrElse(0), userCache)
        val jv = jobService.updateJobCache(jobId, jobCache)
        if (mv.orgIds.contains(1)) {
          handler(mv, jv)
        } else Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))

      case _ => {
        Future.successful(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path))))
      }
    }
  }

  def handleSparkClusterWebsocketRequest(silhouette: Silhouette[DefaultEnv],
                                  memberService: MemberService,
                                  userCache: SyncCacheApi,
                                  request: RequestHeader,
                                  )(processor: (Long, Long) => Source[String, Cancellable])(
                                   implicit ec: ExecutionContext): Future[Either[Result, Flow[Any, String, NotUsed]]] = {
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Results.Ok, Some(securedRequest.identity)))
      }
      .flatMap {
        case HandlerResult(r, Some(m)) =>
          usingIdentity(m.id.get, memberService) { (roles, profile) =>
            if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
              val in       = Sink.ignore
              val response = processor(profile.orgId, profile.workspaceId)

              Future.successful(Right(Flow.fromSinkAndSource(in, response)))
            } else {
              Future.successful(Left(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path)))))
            }
          }


        case HandlerResult(r, None) => Future.successful(Left(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path)))))
      }
      .recover {
        case e: Exception =>
          val result = Results.InternalServerError(e.getMessage)
          Left(result)
      }

  }

  def handleSparkWebsocketRequest(silhouette: Silhouette[DefaultEnv],
                                  memberService: MemberService,
                                  userCache: SyncCacheApi,
                                  request: RequestHeader,
                                  projectId: Long,
                                  runId: Long,
                                  attemptId: String)(processor: (Long, Long, String) => Source[String, Cancellable])(
      implicit ec: ExecutionContext): Future[Either[Result, Flow[Any, String, NotUsed]]] = {
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette
      .SecuredRequestHandler { securedRequest =>
        Future.successful(HandlerResult(Results.Ok, Some(securedRequest.identity)))
      }
      .flatMap {
        case HandlerResult(r, Some(m)) =>
          usingIdentity(m.id.get, memberService) { (roles, profile) =>
            if (hasWorkspaceViewPermission(profile, roles, profile.orgId, profile.workspaceId)) {
              val in       = Sink.ignore
              val response = processor(projectId, runId, attemptId)

              Future.successful(Right(Flow.fromSinkAndSource(in, response)))
            } else {
              Future.successful(Left(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path)))))
            }
          }


        case HandlerResult(r, None) => Future.successful(Left(Results.Unauthorized(Json.toJson(UserNotAuthenticated(request.path)))))
      }
      .recover {
        case e: Exception =>
          val result = Results.InternalServerError(e.getMessage)
          Left(result)
      }

  }

}
