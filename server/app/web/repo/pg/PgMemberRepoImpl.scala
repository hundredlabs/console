package web.repo.pg

import java.time.{LocalDate, Period, ZonedDateTime}
import java.util.UUID

import com.mohiva.play.silhouette.api.LoginInfo
import javax.inject.Inject
import web.models.rbac.{AccessPolicy, AccessRoles, MemberProfile, MemberRole, SubjectType, Theme}
import web.models.{AccessRequest, AlpahRequest, AlphaRequests, AlphaSignedUpUser, BetaRequestListResponse, BetaSignedUpUser, JobWithUsage, Member, MemberValue, OrgDetail, OrgUsagePlan, OrgWithKeys, WorkspaceViewResponse}
import web.repo.MemberRepository
import web.services.{APISecretsGenerator, SecretStore}
import web.utils.DateUtil

import scala.concurrent.{ExecutionContext, Future, blocking}
import scalikejdbc._

import scala.util.{Failure, Success, Try}

class PgMemberRepoImpl @Inject()(blockingEC: ExecutionContext, apiKeyGenerator: APISecretsGenerator) extends MemberRepository {

  //implicit val session = AutoSession
  implicit val ec = blockingEC

  override def getEmailByCode(code: String): Future[Option[String]] = Future {
    blocking {
      DB autoCommit { implicit session =>
        sql"select email from approved_requests where activation_code = ${code} and activation_status = false"
          .map(rs => rs.string("email"))
          .single()
          .apply()

      }
    }
  }

  override def updateApproveStatus(email: String): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""update alpha_requests set code_sent = true where email = ${email}"""
          .update()
          .apply() == 1

      }
    }
  }

  override def approveRequest(email: String): Future[Option[String]] = Future {
    blocking {
      DB localTx { implicit session =>
        //check if any such requests exists
        val reqEmail =
          sql"select email from alpha_requests where email = ${email} AND code_sent = false".map(_.string("email")).single().apply()
        reqEmail map { _ =>
          val code = UUID
            .randomUUID()
            .toString
          sql"""insert into approved_requests
                (email, activation_code, activation_status, dt_approved)
                 values (${email}, ${code},false, ${ZonedDateTime
            .now()} )"""
            .update()
            .apply()

          code
        }

        //create an entry in approved_request table

      }
    }
  }

  override def listAlphaRequests(pageNum: Int, pageSize: Int): Future[AlphaRequests] = Future {
    val starting = if (pageNum > 0) {
      (pageNum * pageSize)
    } else 0
    blocking {
      DB localTx { implicit session =>
        val totalCount = sql"select count(*) as total_requests from alpha_requests where code_sent = false"
          .map(rs => rs.long("total_requests"))
          .single()
          .apply()
          .getOrElse(0L)
        val requests = sql"select * from alpha_requests where code_sent = false limit ${pageSize} offset ${starting}"
          .map(
            r =>
              AlphaSignedUpUser(
                reqId = r.long("req_id"),
                email = r.string("email"),
                dtRequested = DateUtil.timeElapsed(r.zonedDateTime("dt_requested"), None)
            ))
          .list()
          .apply()
        AlphaRequests(totalCount, requests)
      }
    }
  }

  override def getOrgDetail(orgId: Long): Future[Option[OrgDetail]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""select name, slug_id, thumbnail_img FROM orgs WHERE id = $orgId"""
          .map(rs => OrgDetail(rs.string("name"), rs.string("slug_id"), rs.stringOpt("thumbnail_img")))
          .single()
          .apply()

      }
    }
  }

  override def updateOrg(id: Long, detail: OrgDetail): Future[Boolean] = Future {
    blocking {
      DB localTx { implicit session =>
        sql""" update orgs SET name = ${detail.name},
              slug_id = ${detail.slugId},
              thumbnail_img = ${detail.thumbnailImg} WHERE id = $id"""
          .update()
          .apply() > 0

      }
    }
  }

  override def getJobUsageByDate(orgId: Long, startDate: LocalDate, endDate: LocalDate): Future[Seq[JobWithUsage]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT t.job_name, t.id, t.workspace, SUM(t.live_time)/3600 as total_time FROM (
             |SELECT j.name as job_name, w.name as workspace, j.id, est.dep_run_id, est.executor_id, extract(epoch from MAX(est.ts) - MIN(est.ts)) as live_time from jobs as j INNER JOIN
             |deployment_config as dc ON j.id = dc.job_id
             |INNER JOIN deployment_history as dh ON dc.id = dh.deployment_id
             |INNER JOIN events_spark_task_distribution as est ON dh.id = est.dep_run_id
             |  INNER JOIN workspaces as w ON j.workspace_id = w.id
             |WHERE w.org_id = ${orgId} AND dh.dt_started BETWEEN ${startDate} AND ${endDate}
             |GROUP BY est.dep_run_id,j.name,j.id, workspace, est.executor_id ) as t GROUP BY t.job_name,t.workspace, t.id""".stripMargin
          .map { r =>
            JobWithUsage(r.string("job_name"), r.long("id"), r.string("workspace"), r.double("total_time"))
          }
          .list()
          .apply()
      }
    }
  }

  override def newAccessRequest(request: AccessRequest): Future[Long] = Future {
    blocking {
      DB autoCommit { implicit session =>
        val optGuestId = sql"select req_id from access_requests where email = ${request.email}"
          .map(rs => rs.long("req_id"))
          .single()
          .apply()
        optGuestId.getOrElse {
          sql"""insert into access_requests
                (name, email, server_region, activation_code, activation_status, dt_requested)
                 values (${request.name}, ${request.email}, ${request.serverRegion},${UUID
            .randomUUID()
            .toString},false, ${ZonedDateTime
            .now()} )""".updateAndReturnGeneratedKey
            .apply()
        }
      }
    }
  }

  override def newAlphaRequest(request: AlpahRequest): Future[Long] = Future {
    blocking {
      DB autoCommit { implicit session =>
        val optGuestId = sql"select req_id from alpha_requests where email = ${request.email.trim}"
          .map(rs => rs.long("req_id"))
          .single()
          .apply()
        optGuestId.getOrElse {
          sql"""insert into alpha_requests
                (email, dt_requested)
                 values (${request.email.trim}, ${ZonedDateTime
            .now()} )""".updateAndReturnGeneratedKey
            .apply()
        }
      }
    }
  }

  override def notifyMember(memberId: Long): Future[Boolean] = Future {
    blocking {
      DB autoCommit { implicit session =>
        sql"""SELECT member_id FROM feature_notification_requests WHERE member_id = ${memberId}"""
          .map(_.long("member_id"))
          .single().apply() match {
          case None => sql"""insert into feature_notification_requests
                (member_id, dt_requested)
                 values (${memberId}, ${ZonedDateTime
            .now()} )""".updateAndReturnGeneratedKey
            .apply() > 0
          case Some(_) => true
        }


      }
    }
  }

  override def createOrg(name: String, ownerId: Long,plan: OrgUsagePlan, orgSlug: String, thumbnailImg: Option[String]): Future[Either[Throwable, Long]] =
    Future {
      blocking {
        DB localTx { implicit session =>
          val orgId =
            sql"""INSERT INTO orgs(name, owner, slug_id, thumbnail_img, dt_created)
             VALUES(${name}, ${ownerId}, ${orgSlug}, ${thumbnailImg}, ${DateUtil.now})"""
              .updateAndReturnGeneratedKey()
              .apply()

          //Create the default plan
          sql"""INSERT INTO usage_plans(name, max_local_clusters_count, max_remote_cluster_count, max_remote_cluster_size,
               max_jobs_count, max_workspace_count, org_id) VALUES(${plan.name}, ${plan.maxLocalClusters}, ${plan.maxRemoteClusters},
               ${plan.maxRemoteClusterSize}, ${plan.maxJobsCount}, ${plan.maxWorkspaceCount}, ${orgId})"""
            .updateAndReturnGeneratedKey()
            .apply()

          //Create all the roles defined by system
          val adminPolicies = AccessRoles.ROLE_ORG_ADMIN.map(_.name).mkString(AccessRoles.policySeparator)
          val roleId = sql"""INSERT INTO roles(name, access_policies, manager_id)
              VALUES(${AccessRoles.ORG_ADMIN}, $adminPolicies,
              ${orgId})"""
            .updateAndReturnGeneratedKey()
            .apply()

          sql"""INSERT INTO member_roles(member_id, role_id, subject_id, subject_type)
             VALUES(${ownerId}, ${roleId}, ${orgId}, ${SubjectType.ORG.toString})"""
            .update()
            .apply()

          sql"""INSERT INTO member_profile(member_id, current_org_id, web_theme, desktop_theme)
             VALUES(${ownerId}, ${orgId}, ${Theme.LIGHT.toString}, ${Theme.LIGHT.toString})"""
            .update()
            .apply()

          Right(orgId)
        }
      }
    }.recover {
      case e: Exception =>
        if (e.getMessage.contains("unique constraint")) {
          Left(new RuntimeException("This organisation id is already reserved, try a different one."))
        } else {
          Left(new RuntimeException("Internal processing error. Contact support@gigahex.com"))
        }
    }

  override def getMemberProfile(id: Long): Future[Option[MemberProfile]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT current_org_id, current_workspace_id, web_theme, orgs.thumbnail_img, workspaces.name as ws_name,
               orgs.name as org_name, orgs.slug_id as org_slug_id, desktop_theme FROM member_profile
             INNER JOIN orgs ON member_profile.current_org_id = orgs.id
             INNER JOIN workspaces ON member_profile.current_workspace_id = workspaces.id
             WHERE member_id = ${id}"""
          .map(r =>
            MemberProfile(
              r.long("current_org_id"),
              r.string("org_name"),
              r.string("org_slug_id"),
              r.long("current_workspace_id"),
              r.string("ws_name"),
              Theme.withName(r.string("web_theme")),
              Theme.withName(r.string("desktop_theme")),
              r.stringOpt("thumbnail_img")
          ))
          .single()
          .apply()
      }
    }
  }

  override def getDesktopToken(memberId: Long): Future[Option[String]] = Future {
    blocking {
      DB readOnly { implicit session =>
          sql"""SELECT login_token FROM user_login_info WHERE member_id = $memberId"""
          .map(_.string("login_token"))
          .single().apply()
      }
    }
  }


  override def findByToken(token: String): Future[Option[Member]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT m.id, m.name, m.email, m.member_type, m.activated, m.receive_updates, m.dt_joined, li.provider_id, li.provider_key
               FROM user_login_info as ulf INNER JOIN
               login_info as li ON ulf.login_info_id = li.id
               INNER JOIN members as m ON ulf.member_id = m.id
               WHERE login_token = $token"""
          .map{ rs =>
            new Member(
              rs.string("name"),
              rs.string("email"),
              rs.string("member_type"),
              rs.boolean("receive_updates"),
              rs.boolean("activated"),
              LoginInfo(rs.string("provider_id"), rs.string("provider_key")),
              rs.zonedDateTime("dt_joined"),
              rs.longOpt("id")
            )
          }.single().apply()
      }
    }
  }

  override def getMemberRoles(memberId: Long): Future[Seq[MemberRole]] = Future {
    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT name, access_policies, subject_id, subject_type, roles.id FROM roles INNER JOIN member_roles
              ON roles.id = member_roles.role_id WHERE member_id = ${memberId}"""
          .map(
            r =>
              MemberRole(r.long("subject_id"),
                         SubjectType.withName(r.string("subject_type")),
                         AccessPolicy.parseRoles(r.string("access_policies"))))
          .list()
          .apply()
      }
    }
  }

  /**
    * Finds a user by its login info.
    *
    * @param loginInfo The login info of the user to find.
    * @return The found user or None if no user for the given login info could be found.
    */
  override def find(loginInfo: LoginInfo): Future[Option[Member]] = {
    Future {
      blocking {
        DB autoCommit { implicit session =>
          val id = sql"select id from login_info where provider_id = ${loginInfo.providerID} AND provider_key = ${loginInfo.providerKey}"
            .map(_.long("id"))
            .single()
            .apply()
          id.flatMap { i =>
              sql"""SELECT member_id FROM user_login_info WHERE login_info_id = ${i}"""
                .map(_.long("member_id"))
                .single()
                .apply()
            }
            .flatMap { memberId =>
              sql"select * from members where id = ${memberId}"
                .map(rs =>
                  new Member(
                    rs.string("name"),
                    rs.string("email"),
                    rs.string("member_type"),
                    rs.boolean("receive_updates"),
                    rs.boolean("activated"),
                    loginInfo,
                    rs.zonedDateTime("dt_joined"),
                    rs.longOpt("id")
                ))
                .single()
                .apply()
            }
        }
      }
    }
  }

  override def findByEmail(email: String): Future[Option[Member]] = Future {
    blocking {
      DB autoCommit { implicit session =>
        sql"select * from members where email = ${email}".map(rs => Member(rs)).single().apply()
      }
    }
  }

  /**
    * Finds a user by its user ID.
    *
    * @param memberId The ID of the user to find.
    * @return The found user or None if no user for the given ID could be found.
    */
  override def find(memberId: Long): Future[Option[Member]] =
    Future {
      blocking {
        DB autoCommit { implicit session =>
          sql"select * from members where id = ${memberId}".map(rs => Member(rs)).single().apply()
        }
      }
    }

  override def updateName(name: String, memId: Long): Future[Boolean] = Future {
    blocking {
      DB autoCommit { implicit session =>
        sql"update members set name = ${name} where id = ${memId}".update().apply() == 1
      }
    }
  }

  override def setActivated(member: Member): Future[Boolean] = Future {
    blocking {
      DB autoCommit { implicit session =>
        sql"update members set activated = true where id = ${member.id.getOrElse(0)}".update().apply() == 1
      }
    }
  }

  /**
    * Saves a user.
    *
    * @param member The user to save.
    * @return The saved user.
    */
  override def save(member: Member, secretStore: SecretStore): Future[Long] =
    Future {
      blocking {
        DB localTx { implicit session =>
          //update the activation status
          sql"update approved_requests set activation_status = true where email = ${member.email}".update().apply()

          val memberId =
            sql"""INSERT INTO members (name, email, member_type, receive_updates, activated, dt_joined)
                  VALUES (${member.name}, ${member.email}, ${member.memberType}, ${member.receiveUpdates}, ${member.activated}, ${member.dtJoined} )""".updateAndReturnGeneratedKey
              .apply()

          val memberAPIKey = apiKeyGenerator.genKey(20, memberId)

          val encryptedSecretKey = secretStore.encrypt(memberAPIKey.secret)
          val decryptedSecretKey = Try(secretStore.decrypt(encryptedSecretKey)) match {
            case Failure(exception) =>
              exception.printStackTrace()
              throw exception
            case Success(value) => value
          }

          sql"""insert into member_api_keys(name, api_key,encrypted_api_secret_key, member_id, created, last_used) values
                 ('default',  ${memberAPIKey.key},${encryptedSecretKey}, $memberId, ${member.dtJoined}, ${member.dtJoined})""".updateAndReturnGeneratedKey
            .apply()

          memberId
        }
      }
    }

  override def getMemberInfo(id: Long): Option[MemberValue] = DB localTx { implicit session =>
    sql"""SELECT email, o.id as org_id from members m INNER JOIN orgs o ON m.id = o.owner WHERE m.id = ${id}"""
      .map { r =>
        val email = r.string("email")
        val orgId = r.long("org_id")

        MemberValue(id, email, Seq(orgId))
      }
      .single()
      .apply()
  }

  override def retriveOrg(key: String): Future[Option[OrgWithKeys]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""SELECT id, name, api_key, secret_key, api_key_validity_days, dt_created FROM orgs WHERE api_key = ${key}"""
          .map { r =>
            val orgId          = r.long("id")
            val name           = r.string("name")
            val apiKey         = r.string("api_key")
            val secret         = r.string("secret_key")
            val dtCreated      = r.dateTime("dt_created")
            val validityInDays = r.int("api_key_validity_days")
            val diff           = dtCreated.plus(Period.ofDays(validityInDays)).toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli
            val timeLeft = if (diff > 0) {
              DateUtil.formatIntervalMillisInDays(diff)
            } else "0 days"

            OrgWithKeys(orgId, name, apiKey, secret, timeLeft)
          }
          .single()
          .apply()
      }
    }
  }

  override def listOrgsWithKeys(memberId: Long): Future[Seq[OrgWithKeys]] = Future {
    blocking {
      DB localTx { implicit session =>
        sql"""SELECT id, name, api_key, secret_key, api_key_validity_days, dt_created FROM orgs WHERE owner = ${memberId}"""
          .map { r =>
            val orgId          = r.long("id")
            val name           = r.string("name")
            val apiKey         = r.string("api_key")
            val secret         = r.string("secret_key")
            val dtCreated      = r.dateTime("dt_created")
            val validityInDays = r.int("api_key_validity_days")
            val diff           = dtCreated.plus(Period.ofDays(validityInDays)).toInstant.toEpochMilli - ZonedDateTime.now().toInstant.toEpochMilli
            val timeLeft = if (diff > 0) {
              DateUtil.formatIntervalMillisInDays(diff)
            } else "0 days"

            OrgWithKeys(orgId, name, apiKey, secret, timeLeft)
          }
          .list()
          .apply()
      }
    }
  }

  override def listApprovedRequests(pageNum: Int, pageSize: Int): Future[BetaRequestListResponse] = Future {
    val starting = if (pageNum > 0) {
      (pageNum * pageSize)
    } else 0
    blocking {
      DB localTx { implicit session =>
        val totalCount = sql"select count(*) as total_requests from approved_requests"
          .map(rs => rs.long("total_requests"))
          .single()
          .apply()
          .getOrElse(0L)
        val requests = sql"select * from approved_requests limit ${pageSize} offset ${starting}"
          .map(r => BetaSignedUpUser(r))
          .list()
          .apply()
        BetaRequestListResponse(totalCount, requests)
      }
    }
  }

  override def listWorkspaces(orgId: Long): Future[Seq[WorkspaceViewResponse]] = Future {

    blocking {
      DB readOnly { implicit session =>
        sql"""SELECT id, name, dt_created  FROM workspaces as w
              WHERE w.org_id = ${orgId} """
          .map { r =>
            WorkspaceViewResponse(r.long("id"), r.string("name"), DateUtil.timeElapsed(r.dateTime("dt_created"), None) + " ago")
          }
          .list()
          .apply()
      }
    }
  }
}
